#include <gtest/gtest.h>
#include "plump_client.h"
#include "plump_server.h"

#include <string>
#include <memory>
#include <iostream>

// TODO: You're going to have to figure out how to do some threading here to
// Not run the client until the server has started. We'll see if that's ok or 
// If an entire other process will need to be created for the server

// It wouldn't kill you to try to diagnose the segfault too

// So just an update: running the server and client on the same process is a no-go,
// but it should be possible to use a stub for client tests and to manually do server
// tests without the rpc part. Just do a regular function call. It seems like it'll be easy, right?


TEST(PlumpServerTests, CreateLockTest) {
  // TODO: Make some more of these with empty strings, strings too long and stuff like that
  std::string lock_name("test");
  ServerContext ctx;
  CreateDestroyRequest req;
  req.set_lock_name(lock_name);
  CreateDestroyReply res;
  
  PlumpServiceImpl service;
  service.CreateLock(&ctx, &req, &res);
  EXPECT_EQ(res.success(), true);
  EXPECT_EQ(res.message(), "Lock: " + lock_name + " successfully created");
  //EXPECT_EQ(service.locks_[0], lock_name);
}

TEST(PlumpClientTests, CreateLockTest) {

}