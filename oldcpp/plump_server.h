#ifndef PLUMP_SERVER_H
#define PLUMP_SERVER_H

#include <memory>
#include <string>
#include <map>
#include <list>
#include <ctime>

#include <grpcpp/grpcpp.h>

#include "plump.grpc.pb.h"

using grpc::Server;
using grpc::Status;
using grpc::ServerContext;

using plump::CreateDestroyRequest;
using plump::CreateDestroyReply;
using plump::SequencerRequest;
using plump::SequencerReply;
using plump::ListRequest;
using plump::ListReply;
using plump::LockRequest;
using plump::LockReply;
using plump::Sequencer;
using plump::KeepAliveRequest;
using plump::KeepAliveReply;

using plump::Plump;

class PlumpServiceImpl final : public Plump::Service {
  public:
    Status CreateLock(ServerContext* context, const CreateDestroyRequest* request, CreateDestroyReply* reply) override;
    Status DestroyLock(ServerContext* context, const CreateDestroyRequest* request, CreateDestroyReply* reply) override;
    Status ListLocks(ServerContext* context, const ListRequest* request, ListReply* reply) override;
    Status GetSequencer(ServerContext* context, const SequencerRequest* request, SequencerReply* reply) override;
    Status GetLock(ServerContext* context, const LockRequest* request, LockReply* reply) override;
    Status LockKeepAlive(ServerContext* context, const KeepAliveRequest* request, KeepAliveReply* reply) override;
    Status ReleaseLock(ServerContext* context, const ReleaseRequest* request, ReleaseReply* reply) override;

  private:
    std::map<std::string, uint32_t> lock_next_seq_;
    std::map<std::string, std::list<Sequencer>> lock_sequencers_;
    std::map<std::string, bool> lock_reservations_;

    bool LockExists_(const std::string& lock_name);
    void AddLock_(const std::string& lock_name);
    void DestroyLock_(const std::string& lock_name);
    uint32_t GetNextSequencer_(const std::string& lock_name);
    void SaveSequencerHash_(const Sequencer& seq);
    std::set<std::string> ListLockNames_();
};



void RunServer();
#endif