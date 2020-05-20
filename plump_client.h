#ifndef PLUMP_CLIENT_H
#define PLUMP_CLIENT_H

#include <memory>
#include <grpcpp/grpcpp.h>

using grpc::Channel;

class PlumpClient {
  public:
    PlumpClient(std::shared_ptr<Channel> channel);
    std::string CreateLock(const std::string& lock_name);
    std::string ListLocks();
};


#endif