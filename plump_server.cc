/*
 *
 * Copyright 2015 gRPC authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */



#include <iostream>
#include <iomanip>
#include <sstream>
#include <memory>
#include <algorithm>
#include <random>
#include <mutex>


#include <grpcpp/grpcpp.h>
#include <grpcpp/health_check_service_interface.h>
#include <grpcpp/ext/proto_server_reflection_plugin.h>

#include <openssl/evp.h>

#include "plump_server.h"

using grpc::ServerBuilder;
using grpc::StatusCode;

// https://inversepalindrome.com/blog/how-to-create-a-random-string-in-cpp
// source for random string generation

const std::string valid_chars_ = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890-=;:?!@#$%^&*()_+~,.<>[]{}";

std::random_device engine_;
std::mt19937 generator_(engine_());
std::uniform_int_distribution<int> sequence_length_(12,16);
std::uniform_int_distribution<int> char_distribution_(0, valid_chars_.size() - 1);
std::mutex locks_mutex_;

// helper functions
std::string HashString_(const std::string& str) {
  // https://stackoverflow.com/questions/2262386/generate-sha256-with-openssl-and-c
  // Hashing the password using open ssl
  EVP_MD_CTX *md_context = EVP_MD_CTX_new();
  EVP_DigestInit_ex(md_context, EVP_sha256(), NULL);
  // Note, this wouldn't work with unicode per-se, requires a byte count
  EVP_DigestUpdate(md_context, str.c_str(), str.length());
  
  unsigned char str_hash[EVP_MAX_MD_SIZE];
  unsigned int str_hash_length = 0;
  
  EVP_DigestFinal_ex(md_context, str_hash, &str_hash_length);
  std::stringstream stream;
  for(unsigned int i = 0; i < str_hash_length; i++) {
    stream << std::hex << std::setw(2) << std::setfill('0') << (int)str_hash[i];
  }

  EVP_MD_CTX_free(md_context); 

  return stream.str();
}

// Public
Status PlumpServiceImpl::CreateLock(ServerContext* context, const CreateDestroyRequest* request,
                  CreateDestroyReply* reply) {
  // Better unit test needed
  const std::string lock_name(request->lock_name());
  if (!LockExists_(lock_name)) {
    AddLock_(lock_name);
    reply->set_success(true);
    reply->set_message("Lock: " + lock_name + " successfully created");
  } else {
    reply->set_success(false);
    reply->set_message("Lock: " + lock_name + " already exists");
  }
  return Status::OK;
}

Status PlumpServiceImpl::DestroyLock(ServerContext* context, const CreateDestroyRequest* request,
                  CreateDestroyReply* reply) {
  // Check if this lock exists, if it does then destroy it
  if (LockExists_(request->lock_name())) {
    DestroyLock_(request->lock_name());
    
    reply->set_success(true);
    reply->set_message("Lock: " + request->lock_name() + " has been destroyed");
  } else {
    reply->set_success(false);
    reply->set_message("Lock: " + request->lock_name() + " does not exist");
  }
  return Status::OK;
}

Status PlumpServiceImpl::GetSequencer(ServerContext* context, const SequencerRequest* request, 
                    SequencerReply* reply) { 
  // TODO: Better error types and tests for them
  
  // First, we check if the lock exists, if it doesn't we send an error
  if (!LockExists_(request->lock_name())) {
    return Status(StatusCode::NOT_FOUND, "Lock: " + request->lock_name() + " does not exist.");
  }
  Sequencer new_sequencer;
  new_sequencer.lock_name = request->lock_name();
  // since we know the lock is present, let's set and increment the sequencer
  new_sequencer.seq_num = GetNextSequencer_(request->lock_name());
  // Add an expiration. default is 5 minutes from now.
  // TODO: This should be some kind of configuration thing
  new_sequencer.expiration = time(0) + 60 * 5;

  // To make the sequencer secure, we want to hand out a character sequence with it  
  // make the sequence a random number of characters between 12 - 16
  int length = sequence_length_(generator_);
  std::string sequencer_key;
  
  for(int i = 0; i < length; i++) {
    sequencer_key += valid_chars_[char_distribution_(generator_)];
  }
  // Hash the key for the sequencer object, send the key in the reply
  new_sequencer.key_hash = HashString_(sequencer_key);


  reply->set_sequencer(new_sequencer.seq_num);
  reply->set_key(sequencer_key);

  SaveSequencerHash_(new_sequencer);

  return Status::OK;
}

Status PlumpServiceImpl::GetLock(ServerContext* context, const LockRequest* request, LockReply* reply) {
  // Check if the lock exists, if not then send an error
  if (!LockExists_(request->lock_name())) {
    return Status(StatusCode::NOT_FOUND, "Lock: " + request->lock_name() + " does not exist.");
  }

  // Check if the sequencer is the next one up
  // WARNING: we are not doing living/dead ones right now
  // assuming that the sequencer will always be used properly

  // if the sequencer is 0 then none have been issued and an error status should be sent

  return Status::OK;
}

Status PlumpServiceImpl::ListLocks(ServerContext* context, const ListRequest* request, ListReply* reply) {
  std::set<std::string> lock_names = ListLockNames_();
  for(auto name_ptr = lock_names.begin(); name_ptr != lock_names.end(); name_ptr++) {
    reply->add_lock_names(*name_ptr);
  }
  return Status::OK;
}

void RunServer() {
  std::string server_address("0.0.0.0:50051");
  PlumpServiceImpl service;

  grpc::EnableDefaultHealthCheckService(true);
  grpc::reflection::InitProtoReflectionServerBuilderPlugin();
  ServerBuilder builder;
  // Listen on the given address without any authentication mechanism.
  builder.AddListeningPort(server_address, grpc::InsecureServerCredentials());
  // Register "service" as the instance through which we'll communicate with
  // clients. In this case it corresponds to an *synchronous* service.
  builder.RegisterService(&service);
  // Finally assemble the server.
  std::unique_ptr<Server> server(builder.BuildAndStart());
  std::cout << "Server listening on " << server_address << std::endl;

  // Wait for the server to shutdown. Note that some other thread must be
  // responsible for shutting down the server for this call to ever return.
  server->Wait();
}

// private
bool PlumpServiceImpl::LockExists_(const std::string& lock_name) {
  return lock_next_seq_.count(lock_name);
}

void PlumpServiceImpl::AddLock_(const std::string& lock_name) {  
  lock_next_seq_[lock_name] = 0;
  lock_sequencers_[lock_name] = std::list<Sequencer>();
  lock_reservations_[lock_name] = false;
}

void PlumpServiceImpl::DestroyLock_(const std::string& lock_name) {
  lock_next_seq_.erase(lock_name);
  lock_sequencers_.erase(lock_name);
  lock_reservations_.erase(lock_name);
}

uint32_t PlumpServiceImpl::GetNextSequencer_(const std::string& lock_name) {
  uint32_t next_seq = lock_next_seq_[lock_name];
  lock_next_seq_[lock_name] += 1;
  return next_seq;
}

void PlumpServiceImpl::SaveSequencerHash_(const Sequencer& seq) {
  lock_sequencers_[seq.lock_name].push_back(seq);
}

std::set<std::string> PlumpServiceImpl::ListLockNames_() {
  std::set<std::string> lock_names;
  for(auto start = lock_next_seq_.begin(); start != lock_next_seq_.end(); start++) {
    lock_names.insert(start->first);
  }
  return lock_names;
}
  




