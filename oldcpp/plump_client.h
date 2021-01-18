#ifndef PLUMP_CLIENT_H
#define PLUMP_CLIENT_H

#include <memory>
#include <vector>
#include <utility>

#include <grpcpp/grpcpp.h>
#include "plump.grpc.pb.h"

using plump::Plump;
using plump::Sequencer;

using grpc::Channel;

class PlumpClient {
  public:
    explicit PlumpClient(std::unique_ptr<Plump::StubInterface> stub);
    std::string CreateLock(const std::string& lock_name);
    bool DestroyLock(const std::string& lock_name);
    Sequencer GetSequencer(const std::string& lock_name);
    std::vector<std::string> ListLocks();
  private:
    std::unique_ptr<Plump::StubInterface> stub_;
};

#endif