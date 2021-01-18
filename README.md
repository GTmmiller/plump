# plump

A lightweight lock service based on [google's chubby lock service](https://research.google/pubs/pub27897/). `plump` is designed to provide mutiple backends for a simple lock service allowing users to create, delete, and hold locks.

*This project is a work in progress, check the projects page to see how it's progressing*

## Getting Started

`plump` uses `[cmake](https://cmake.org/) version 3.11 and up` for retrieving dependencies and building.

1. Make a build directory

```mkdir build```

2. Change to the build directory and call `cmake`

```cd build```

```cmake ../```

3. Run make in the build directory

```make```

4. Run make tests to make sure everything is working properly

```make test```

5. `plump_server` and `plump_client` should now be built and usable.

## Creating a `plump` backend

To be determined, right now it would be done by implementing `ILockContainer.h`, but it's not complete as of yet.

Install the protocol buffer compiler protoc: https://developers.google.com/protocol-buffers/docs/downloads

Make sure protoc is on the PATH. Linux and OSX users should use a package manager to install protoc for the easiest experience.

Install the Go plugins for the protocol compiler (protoc)

export GO111MODULE=on
go get google.golang.org/protobuf/cmd/protoc-gen-go \
         google.golang.org/grpc/cmd/protoc-gen-go-grpc

Update your PATH so that the protoc compiler can find the plugins:

$ export PATH="$PATH:$(go env GOPATH)/bin"

protoc --go_out=. --go_opt=paths=source_relative \
    --go-grpc_out=. --go-grpc_opt=paths=source_relative \
    helloworld/helloworld.proto

is there a pre-build thing I can do? A go build system? Oh well

protoc --go_out=. --go_opt=paths=source_relative --go-grpc_out=. --go-grpc_opt=paths=source_relative .\protos\plump.proto

