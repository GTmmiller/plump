#include <gtest/gtest.h>
#include <gmock/gmock.h>

#include "plump_mock.grpc.pb.h"

#include "plump_server.h"

#include <string>
#include <memory>
#include <iostream>
#include <algorithm>
#include <thread>         // std::this_thread::sleep_for
#include <chrono>         // std::chrono::seconds

using plump::MockPlumpStub;
using plump::Plump;

using namespace testing;


// TODO: You're going to have to figure out how to do some threading here to
// Not run the client until the server has started. We'll see if that's ok or 
// If an entire other process will need to be created for the server

// It wouldn't kill you to try to diagnose the segfault too

// So just an update: running the server and client on the same process is a no-go,
// but it should be possible to use a stub for client tests and to manually do server
// tests without the rpc part. Just do a regular function call. It seems like it'll be easy, right?
// Lol, I had to change how my client worked to accept stubs other than a NewStub using a real channel
// Good step on the way to making a new

CreateDestroyReply ServerCreateLock(Plump::Service *service, std::string lock_name) {
  ServerContext ctx;
  CreateDestroyRequest req;
  CreateDestroyReply res;

  req.set_lock_name(lock_name);

  Status s = service->CreateLock(&ctx, &req, &res);
  EXPECT_TRUE(s.ok());
  return res;
}

CreateDestroyReply ServerDestroyLock(Plump::Service *service, std::string lock_name) {
  ServerContext ctx;
  CreateDestroyRequest req;
  CreateDestroyReply res;

  req.set_lock_name(lock_name);

  Status s = service->DestroyLock(&ctx, &req, &res);
  EXPECT_TRUE(s.ok());
  return res;
}

SequencerReply ServerGetSequencer(Plump::Service *service, std::string lock_name) {
  ServerContext ctx;
  SequencerRequest req;
  SequencerReply res;

  req.set_lock_name(lock_name);
  Status s = service->GetSequencer(&ctx, &req, &res);
  EXPECT_TRUE(s.ok());
  return res;
}

LockReply ServerGetLock(Plump::Service *service, const Sequencer& sequencer) {
  ServerContext ctx;
  LockRequest req;
  LockReply res;

  req.mutable_sequencer()->set_lock_name(sequencer.lock_name());
  req.mutable_sequencer()->set_sequence_number(sequencer.sequence_number());
  req.mutable_sequencer()->set_key(sequencer.key());
  req.mutable_sequencer()->set_expiration(sequencer.expiration());

  Status s = service->GetLock(&ctx, &req, &res);
  // Ok isn't always the expected outcome
  EXPECT_TRUE(s.ok());
  return res;
}

KeepAliveReply ServerLockKeepAlive(Plump::Service *service, const Sequencer& sequencer) {
  ServerContext ctx;
  KeepAliveRequest req;
  KeepAliveReply reply;

  req.mutable_sequencer()->set_lock_name(sequencer.lock_name());
  req.mutable_sequencer()->set_sequence_number(sequencer.sequence_number());
  req.mutable_sequencer()->set_key(sequencer.key());
  req.mutable_sequencer()->set_expiration(sequencer.expiration());

  Status s = service->LockKeepAlive(&ctx, &req, &reply);
  EXPECT_TRUE(s.ok());
  return reply;
}

ListReply ServerListLocks(Plump::Service *service) {
  ServerContext ctx;
  ListRequest req;
  ListReply res;

  Status s = service->ListLocks(&ctx, &req, &res);
  EXPECT_TRUE(s.ok());
  return res;
}


TEST(PlumpServerTests, CreateLockTest) {
  // TODO: Make some more of these with empty strings, strings too long and stuff like that
  std::string lock_name("test");
  PlumpServiceImpl service;

  CreateDestroyReply res = ServerCreateLock(&service, lock_name);
  EXPECT_EQ(res.success(), true);
  EXPECT_EQ(res.message(), "Lock: " + lock_name + " successfully created");
}

TEST(PlumpServerTests, ListLocksTest) {
  std::string lock_name_one("test");
  std::string lock_name_two("test_two");
  PlumpServiceImpl service;
  ServerCreateLock(&service, lock_name_one);
  ServerCreateLock(&service, lock_name_two);

  ListReply lres = ServerListLocks(&service);
  EXPECT_EQ(lres.lock_names().size(), 2);
  EXPECT_TRUE(std::find(
    lres.lock_names().begin(), 
    lres.lock_names().end(),
    lock_name_one) != lres.lock_names().end()
  );
  EXPECT_TRUE(std::find(
    lres.lock_names().begin(), 
    lres.lock_names().end(),
    lock_name_two) != lres.lock_names().end()
  );
}

TEST(PlumpServer_DestroyLockTests, DestroyLockTest) {
  std::string lock_name("test");
  PlumpServiceImpl service;
  ServerCreateLock(&service, lock_name);
  
  ListReply lres = ServerListLocks(&service);
  EXPECT_EQ(lres.lock_names().size(), 1);
  
  CreateDestroyReply res = ServerDestroyLock(&service, lock_name);
  EXPECT_TRUE(res.success());
  EXPECT_EQ(res.message(), "Lock: " + lock_name + " has been destroyed");
  
  lres = ServerListLocks(&service);
  EXPECT_EQ(lres.lock_names().size(), 0);
}

TEST(PlumpServer_DestroyLockTests, NoLockTest) {
  PlumpServiceImpl service;
  std::string unknown_lock("unknown");
  CreateDestroyReply res = ServerDestroyLock(&service, unknown_lock);
  EXPECT_FALSE(res.success());
  EXPECT_EQ(res.message(), "Lock: " + unknown_lock + " does not exist");
}

TEST(PlumpServer_GetSequencerTests, GetSequencerTest) {
  PlumpServiceImpl service;
  std::string lock_name("john");
  ServerCreateLock(&service, lock_name);
  SequencerReply res = ServerGetSequencer(&service, lock_name);
  EXPECT_EQ(res.sequencer().sequence_number(), 0);
  EXPECT_GT(res.sequencer().key().size(), 11);
  EXPECT_LT(res.sequencer().key().size(), 17);
  EXPECT_GT(res.sequencer().expiration(), time(0));
  EXPECT_EQ(res.sequencer().lock_name(), lock_name);
}

TEST(PlumpServer_GetLockTests, GetLockTest) {
  PlumpServiceImpl service;
  std::string lock_name("lock");
  ServerCreateLock(&service, lock_name);
  SequencerReply seqReply = ServerGetSequencer(&service, lock_name);
  LockReply lockReply = ServerGetLock(&service, seqReply.sequencer());
  EXPECT_TRUE(lockReply.success());
}

TEST(PlumpServer_GetLockTests, GetLockNotHead) {
  PlumpServiceImpl service;
  std::string lock_name("lock");
  ServerCreateLock(&service, lock_name);
  ServerGetSequencer(&service, lock_name);
  SequencerReply seqReply = ServerGetSequencer(&service, lock_name);
  LockReply lockReply = ServerGetLock(&service, seqReply.sequencer());
  EXPECT_FALSE(lockReply.success());
}

TEST(PlumpServer_GetKeepAliveTests, LockKeepAliveTest) {
  PlumpServiceImpl service;
  std::string lock_name("lock");
  ServerCreateLock(&service, lock_name);
  SequencerReply seqReply = ServerGetSequencer(&service, lock_name);
  LockReply lockReply = ServerGetLock(&service, seqReply.sequencer());
  // This ain't great, but I'll stub it later with chrono (maybe)
  std::this_thread::sleep_for(std::chrono::milliseconds(100));
  KeepAliveReply kaReply = ServerLockKeepAlive(&service, lockReply.updated_sequencer());
  EXPECT_GT(kaReply.updated_sequencer().expiration(), lockReply.updated_sequencer().expiration());
}

