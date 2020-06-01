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
#include <algorithm>

#include <grpcpp/grpcpp.h>
#include <grpcpp/health_check_service_interface.h>
#include <grpcpp/ext/proto_server_reflection_plugin.h>

#include "plump_server.h"

using grpc::ServerBuilder;

Status PlumpServiceImpl::CreateLock(ServerContext* context, const CreateDestroyRequest* request,
                  CreateDestroyReply* reply) {
  reply->set_success(true);
  reply->set_message("Lock: " + request->lock_name() + " successfully created");
  locks_.insert(request->lock_name());
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

Status PlumpServiceImpl::ListLocks(ServerContext* context, const ListRequest* request, ListReply* reply) {
  for(auto start = locks_.begin(); start != locks_.end(); start++) {
    reply->add_lock_names(*start);
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


