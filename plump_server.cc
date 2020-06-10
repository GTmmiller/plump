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

Status PlumpServiceImpl::CreateLock(ServerContext* context, const CreateDestroyRequest* request,
                  CreateDestroyReply* reply) {
  // Better unit test needed
  const std::string lock_name(request->lock_name());
  if (locks_.count(lock_name) == 0) {
    locks_[lock_name] = 0;
    lock_sequencers_[lock_name] = std::map<uint32_t, std::string>();
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
  if (locks_.count(request->lock_name())) {
    locks_.erase(request->lock_name());
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
  // TODO: Better error types
  
  // First, we check if the lock exists, if it doesn't we send an error
  if (!locks_.count(request->lock_name())) {
    return Status(StatusCode::NOT_FOUND, "Lock: " + request->lock_name() + " does not exist.");
  }

  // since we know the lock is present, let's set and increment the sequencer
  reply->set_sequencer(locks_[request->lock_name()]);
  locks_[request->lock_name()] += 1;

  // To make the sequencer secure, we want to hand out a character sequence with it  
  // make the sequence a random number of characters between 12 - 16
  int length = sequence_length_(generator_);
  std::string sequencer_key;
  
  for(int i = 0; i < length; i++) {
    sequencer_key += valid_chars_[char_distribution_(generator_)];
  }
  
  // Save the hash, send the original password
  reply->set_key(sequencer_key);

  // https://stackoverflow.com/questions/2262386/generate-sha256-with-openssl-and-c
  // Hashing the password using open ssl
  EVP_MD_CTX *md_context = EVP_MD_CTX_new();
  EVP_DigestInit_ex(md_context, EVP_sha256(), NULL);
  // Note, this wouldn't work with unicode per-se, requires a byte count
  EVP_DigestUpdate(md_context, sequencer_key.c_str(), sequencer_key.length());
  
  unsigned char sequencer_hash[EVP_MAX_MD_SIZE];
  unsigned int sequencer_hash_length = 0;
  
  EVP_DigestFinal_ex(md_context, sequencer_hash, &sequencer_hash_length);
  std::stringstream stream;
  for(unsigned int i = 0; i < sequencer_hash_length; i++) {
    stream << std::hex << std::setw(2) << std::setfill('0') << (int)sequencer_hash[i];
  }

  lock_sequencers_[request->lock_name()][reply->sequencer()] = stream.str();
  EVP_MD_CTX_free(md_context);  
  
  return Status::OK;
}

Status PlumpServiceImpl::ListLocks(ServerContext* context, const ListRequest* request, ListReply* reply) {
  for(auto start = locks_.begin(); start != locks_.end(); start++) {
    reply->add_lock_names(start->first);
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


