#include <string>
#include "plump_client.h"

int main(int argc, char** argv) {
  // Instantiate the client. It requires a channel, out of which the actual RPCs
  // are created. This channel models a connection to an endpoint specified by
  // the argument "--target=" which is the only expected argument.
  // We indicate that the channel isn't authenticated (use of
  // InsecureChannelCredentials()).
  std::string target_str;
  std::string arg_str("--target");
  if (argc > 1) {
    std::string arg_val = argv[1];
    size_t start_pos = arg_val.find(arg_str);
    if (start_pos != std::string::npos) {
      start_pos += arg_str.size();
      if (arg_val[start_pos] == '=') {
        target_str = arg_val.substr(start_pos + 1);
      } else {
        std::cout << "The only correct argument syntax is --target=" << std::endl;
        return 0;
      }
    } else {
      std::cout << "The only acceptable argument is --target=" << std::endl;
      return 0;
    }
  } else {
    target_str = "localhost:50051";
  }

  // Create a stub using a channel with insecure credentials
  std::unique_ptr<Plump::StubInterface> stub = Plump::NewStub(grpc::CreateChannel(
      target_str, grpc::InsecureChannelCredentials()));
  
  PlumpClient plump(std::move(stub));
  std::string lock_name("database");
  std::string reply = plump.CreateLock(lock_name);
  std::cout << reply << std::endl;

  reply = plump.ListLocks();

  return 0;
}