#include <gtest/gtest.h>
#include <gmock/gmock.h>

#include "plump_mock.grpc.pb.h"

#include "plump_client.h"
#include "plump_server.h"

#include <string>
#include <memory>
#include <iostream>

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




TEST(PlumpServerTests, CreateLockTest) {
  // TODO: Make some more of these with empty strings, strings too long and stuff like that
  std::string lock_name("test");
  ServerContext ctx;
  CreateDestroyRequest req;
  req.set_lock_name(lock_name);
  CreateDestroyReply res;
  
  PlumpServiceImpl service;
  Status s = service.CreateLock(&ctx, &req, &res);
  EXPECT_TRUE(s.ok());
  EXPECT_EQ(res.success(), true);
  EXPECT_EQ(res.message(), "Lock: " + lock_name + " successfully created");
}

TEST(PlumpClientTests, CreateLockTest) {
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