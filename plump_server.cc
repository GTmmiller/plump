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

bool SeqComp_(const Sequencer& i, const Sequencer& j) {
  return i.sequence_number() < j.sequence_number();
}

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

// Will return true if the sequencers are valid and equal
bool validateSequencer_(const Sequencer& requestSeq, const Sequencer& databaseSeq) {
  bool lock_name = requestSeq.lock_name() == databaseSeq.lock_name();
  bool sequence_number = requestSeq.sequence_number() == databaseSeq.sequence_number();
  bool key = HashString_(requestSeq.key()) == databaseSeq.key();
  bool expiration = requestSeq.expiration() == databaseSeq.expiration();

  return lock_name && sequence_number && key && expiration;
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
  reply->mutable_sequencer()->set_lock_name(request->lock_name());
  // since we know the lock is present, let's set and increment the sequencer
  reply->mutable_sequencer()->set_sequence_number(GetNextSequencer_(request->lock_name()));
  // Add an expiration. default is 5 minutes from now.
  // TODO: This should be some kind of configuration thing
  reply->mutable_sequencer()->set_expiration(time(0) + 60 * 5);
  std::cout << "hork" << std::endl;
  Sequencer hash_sequencer(reply->sequencer());

  // To make the sequencer secure, we want to hand out a character sequence with it  
  // make the sequence a random number of characters between 12 - 16
  int length = sequence_length_(generator_);
  std::string sequencer_key;
  
  for(int i = 0; i < length; i++) {
    sequencer_key += valid_chars_[char_distribution_(generator_)];
  }
  
  // Hash the key for the sequencer object, send the key in the reply
  reply->mutable_sequencer()->set_key(sequencer_key);
  hash_sequencer.set_key(HashString_(sequencer_key));
  std::cout << reply->sequencer().lock_name() << std::endl;

  SaveSequencerHash_(hash_sequencer);

  return Status::OK;
}

Status PlumpServiceImpl::GetLock(ServerContext* context, const LockRequest* request, LockReply* reply) {
  // Check if the lock exists, if not then send an error
  Sequencer requestSequencer = request->sequencer();
  if (!LockExists_(requestSequencer.lock_name())) {
    return Status(StatusCode::NOT_FOUND, "Lock: " + requestSequencer.lock_name() + " does not exist.");
  }

  // Establish the effective time for the GetLock Request
  time_t eff_time = time(0);
  // Pull a reference to the list of sequencers for the lock
  std::list<Sequencer> sequencer_list = lock_sequencers_[requestSequencer.lock_name()];

  bool lockReserved = lock_reservations_[requestSequencer.lock_name()];
  bool headExpired = sequencer_list.front().expiration() < eff_time;
  
  
  
  // If the head is expired then prune to the next valid head or clear the list
  if (headExpired) {
    // Prune to the next valid head or clear the list
    while (sequencer_list.front().expiration() < eff_time && sequencer_list.size() != 0) {
      sequencer_list.pop_front();
    }

    // unlock the lock
    lock_reservations_[requestSequencer.lock_name()] = false;
    lockReserved = false;
    
    // If the list is now size zero then return an error message
    if (sequencer_list.size() == 0) {
      return Status(StatusCode::NOT_FOUND, "There are no available sequencers for lock: " + requestSequencer.lock_name());
    }
  }

  Sequencer headSequencer = sequencer_list.front();
  bool headMatch = validateSequencer_(requestSequencer, headSequencer);

  // Now we can try to lock the lock 
  if (!lockReserved && headMatch) {
    // If there's a head match and the lock isn't reserved then you can get the lock
    // Lock it
    lock_reservations_[requestSequencer.lock_name()] = true;

    // Send the revised sequencer
    uint32_t newExpiration = time(0) + 60 * 5;
    headSequencer.set_expiration(newExpiration);

    reply->mutable_updated_sequencer()->set_lock_name(requestSequencer.lock_name());
    reply->mutable_updated_sequencer()->set_sequence_number(requestSequencer.sequence_number());
    reply->mutable_updated_sequencer()->set_key(requestSequencer.key());
    reply->mutable_updated_sequencer()->set_expiration(newExpiration);

    reply->set_success(true);
    reply->set_keep_alive_interval(60 * 5);
    return Status::OK;
  } else if (lockReserved && headMatch) {
    // If the head is matched and the lock is reserved then send an error message
    return Status(StatusCode::ALREADY_EXISTS, "You have already locked the lock: " + requestSequencer.lock_name());
  } else {
    // If there isn't a head match then the expiration should update if it's findable
    // lock is reserved but the head doesn't match
    // lock isn't reserved and the head doesn't match
    auto seqPtr = std::lower_bound(sequencer_list.begin(), sequencer_list.end(), requestSequencer, SeqComp_);
    
    if (seqPtr == sequencer_list.end()) {
      // The sequencer isn't in the list, return an error message
      return Status(StatusCode::NOT_FOUND, "Your sequencer for lock: " + requestSequencer.lock_name() + " has timed out or does not exist");
    } else if(validateSequencer_(requestSequencer, *seqPtr)) {
      // if the sequencer is valid then we can update it
      uint32_t newExpiration = time(0) + 60 * 5;
      seqPtr->set_expiration(newExpiration);

      reply->mutable_updated_sequencer()->set_lock_name(requestSequencer.lock_name());
      reply->mutable_updated_sequencer()->set_sequence_number(requestSequencer.sequence_number());
      // Think about returning the key
      reply->mutable_updated_sequencer()->set_key(requestSequencer.key());
      reply->mutable_updated_sequencer()->set_expiration(newExpiration);

      reply->set_success(false);
      reply->set_keep_alive_interval(60 * 5);
      return Status::OK;
    } else {
      // invalid sequencer, but don't let them know that we know
      // The sequencer isn't in the list, return an error message
      return Status(StatusCode::NOT_FOUND, "Your sequencer for lock: " + requestSequencer.lock_name() + " has timed out or does not exist");
    }
  }
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
  lock_sequencers_[seq.lock_name()].push_back(seq);
}

std::set<std::string> PlumpServiceImpl::ListLockNames_() {
  std::set<std::string> lock_names;
  for(auto start = lock_next_seq_.begin(); start != lock_next_seq_.end(); start++) {
    lock_names.insert(start->first);
  }
  return lock_names;
}
  




