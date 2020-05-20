#ifndef PLUMP_CLIENT_H
#define PLUMP_CLIENT_H

#include <memory>
#include <grpcpp/grpcpp.h>
#include "plump.grpc.pb.h"

using plump::Plump;

using grpc::Channel;

class PlumpClient {
  public:
    PlumpClient(std::shared_ptr<Channel> channel);
    std::string CreateLock(const std::string& lock_name);
    std::string ListLocks();
  private:
    std::unique_ptr<Plump::Stub> stub_;
};


#endif