#include <gtest/gtest.h>
#include <gmock/gmock.h>

#include "plump_mock.grpc.pb.h"

#include "plump_client.h"
#include "plump_server.h"

#include <string>
#include <memory>
#include <iostream>
#include <algorithm>

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
  EXPECT_EQ(res.sequencer().sequencer(), 0);
  EXPECT_GT(res.sequencer().key().size(), 11);
  EXPECT_LT(res.sequencer().key().size(), 17);
  EXPECT_GT(res.sequencer().expiration(), time(0));
  EXPECT_EQ(res.sequencer().lock_name(), lock_name);
}


TEST(PlumpClient_CreateLockTests, CreateNormalLockTest) {
  std::string lock_name("test");
  auto stub = std::unique_ptr<MockPlumpStub>(new MockPlumpStub);
  CreateDestroyReply res;
  res.set_success(true);
  res.set_message("Lock: " + lock_name + " successfully created");
  EXPECT_CALL(*stub, CreateLock(_,_,_))
    .Times(AtLeast(1))
    .WillOnce(DoAll(SetArgPointee<2>(res), Return(Status::OK)));
  PlumpClient client(std::move(stub));
  std::string msg = client.CreateLock(lock_name);
  EXPECT_EQ(msg, "Lock: " + lock_name + " successfully created");
}

TEST(PlumpClient_CreateLockTests, CreateLockExceptionTest) {
  std::string target_str("localhost:60555");
  std::unique_ptr<Plump::StubInterface> stub = Plump::NewStub(grpc::CreateChannel(
      target_str, grpc::InsecureChannelCredentials()));
  
  PlumpClient plump(std::move(stub));
  EXPECT_THROW(plump.CreateLock("failure"), Status);
}

TEST(PlumpClient_DestroyLock, DestroyLockTest) {
  std::string valid_lock("valid");
  auto stub = std::unique_ptr<MockPlumpStub>(new MockPlumpStub);
  CreateDestroyReply res;
  res.set_success(true);
  res.set_message("Lock: " + valid_lock + " has been destroyed");
  EXPECT_CALL(*stub, DestroyLock(_,_,_))
    .Times(AtLeast(1))
    .WillOnce(DoAll(SetArgPointee<2>(res), Return(Status::OK)));
  PlumpClient client(std::move(stub));
  EXPECT_TRUE(client.DestroyLock(valid_lock));
} 

TEST(PlumpClient_GetSequencer, GetSequencerTest) {
  std::string lock_name("lock");
  std::string key("test_key");
  auto stub = std::unique_ptr<MockPlumpStub>(new MockPlumpStub);
  SequencerReply res;
  Sequencer* seq = new Sequencer();
  seq->set_lock_name(lock_name);
  seq->set_sequencer(0);
  seq->set_key(key);
  seq->set_expiration(time(0));
  res.set_allocated_sequencer(seq);

  EXPECT_CALL(*stub, GetSequencer(_,_,_))
    .Times(AtLeast(1))
    .WillOnce(DoAll(SetArgPointee<2>(res), Return(Status::OK)));
  PlumpClient client(std::move(stub));
  Sequencer sequencer = client.GetSequencer(lock_name);
  EXPECT_EQ(sequencer.lock_name(), lock_name);
  EXPECT_EQ(sequencer.key(), key);
  EXPECT_EQ(sequencer.sequencer(), 0);
}

TEST(PlumpClient_ListLocksTests, ListLocksTest) {
  std::string lock_name_one("lock_name_one");
  std::string lock_name_two("lock_name_two");
  auto stub = std::unique_ptr<MockPlumpStub>(new MockPlumpStub);
  ListReply res;
  res.add_lock_names(lock_name_one);
  res.add_lock_names(lock_name_two);
  EXPECT_CALL(*stub, ListLocks(_,_,_))
    .Times(AtLeast(1))
    .WillOnce(DoAll(SetArgPointee<2>(res), Return(Status::OK)));
  PlumpClient client(std::move(stub));
  std::vector<std::string> locks = client.ListLocks();
  EXPECT_EQ(locks.size(), 2);
  EXPECT_TRUE(std::find(
    locks.begin(), 
    locks.end(),
    lock_name_one) != locks.end()
  );
  EXPECT_TRUE(std::find(
    locks.begin(), 
    locks.end(),
    lock_name_two) != locks.end()
  );
}