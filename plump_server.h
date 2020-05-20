#ifndef PLUMP_SERVER_H
#define PLUMP_SERVER_H

#include <memory>
#include <string>
#include <vector>

#include <grpcpp/grpcpp.h>

#include "plump.grpc.pb.h"

using grpc::Server;
using grpc::Status;
using grpc::ServerContext;

using plump::CreateDestroyRequest;
using plump::CreateDestroyReply;
using plump::ListRequest;
using plump::ListReply;
using plump::Plump;



class PlumpServiceImpl final : public Plump::Service {
  public:
    Status CreateLock(ServerContext* context, const CreateDestroyRequest* request, CreateDestroyReply* reply) override;
    Status ListLocks(ServerContext* context, const ListRequest* request, ListReply* reply) override;

  private:
    std::vector<std::string> locks_;
};


void RunServer();
#endif