#include <gtest/gtest.h>
#include <gmock/gmock.h>

#include "plump_mock.grpc.pb.h"
#include "plump_client.h"

#include <string>
#include <memory>
#include <iostream>
#include <algorithm>

using plump::MockPlumpStub;
using plump::Plump;
using plump::CreateDestroyReply;
using plump::SequencerReply;
using plump::ListReply;

using grpc::Status;

using namespace testing;

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
  seq->set_sequence_number(0);
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
  EXPECT_EQ(sequencer.sequence_number(), 0);
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