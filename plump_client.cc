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
#include <memory>
#include <string>

#include <grpcpp/grpcpp.h>
#include "plump.grpc.pb.h"
#include "plump_client.h"

using grpc::Channel;
using grpc::ClientContext;
using grpc::Status;

using plump::CreateDestroyRequest;
using plump::CreateDestroyReply;
using plump::ListRequest;
using plump::ListReply;
using plump::Plump;

PlumpClient::PlumpClient(std::unique_ptr<Plump::StubInterface> stub) : stub_(std::move(stub)) {}

std::string PlumpClient::CreateLock(const std::string& lock_name) {
  // Prime request
  CreateDestroyRequest request;
  request.set_lock_name(lock_name);

  // Create reply and context
  CreateDestroyReply reply;
  ClientContext context;

  // Perform RPC
  Status status = stub_->CreateLock(&context, request, &reply);

  // Act upon its status.
  if (status.ok() && reply.success()) {
    return reply.message();
  } else {
    throw status;
  }
}

/**
 * Returns true if the lock is destroyed and false if the lock was not destroyed
 * a lock won't be destroyed if it was never in the service in the first place
 * or if more than one client tried to delete at once/
 */
bool PlumpClient::DestroyLock(const std::string& lock_name) {
  CreateDestroyRequest request;
  request.set_lock_name(lock_name);

  // Create reply and context
  CreateDestroyReply reply;
  ClientContext context;

  Status status = stub_->DestroyLock(&context, request, &reply);
  if (status.ok()) {
    return reply.success();
  } else {
    throw status;
  }
}

std::vector<std::string> PlumpClient::ListLocks() {
  // Create reply and context
  ListRequest request;
  ListReply reply;
  ClientContext context;

  // Perform RPC
  Status status = stub_->ListLocks(&context, request, &reply);

  // Act upon its status.
  if (status.ok()) {
    // Copy the lock names to a vector and return
    std::vector<std::string> list;
    for (auto start = reply.lock_names().begin(); start <
      reply.lock_names().end(); start++) {
      list.push_back(*start);
    }
    return list;
  } else {
    // Should throw an error
    throw status;
  }
}
