# Copyright 2018 gRPC authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# cmake build file for C++ helloworld example.
# Assumes protobuf and gRPC have been installed using cmake.
# See cmake_externalproject/CMakeLists.txt for all-in-one cmake build
# that automatically builds all the dependencies before building helloworld.

cmake_minimum_required(VERSION 3.11)

set(CMAKE_CXX_STANDARD 11)

project(HelloWorld C CXX)

if(NOT MSVC)
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
else()
  add_definitions(-D_WIN32_WINNT=0x600)
endif()

find_package(Threads REQUIRED)
find_package(OpenSSL REQUIRED)

# Another way is to use CMake's FetchContent module to clone gRPC at
# configure time. This makes gRPC's source code available to your project,
# similar to a git submodule.
message(STATUS "Using gRPC via add_subdirectory (FetchContent).")
include(FetchContent)
FetchContent_Declare(
  grpc
  GIT_REPOSITORY https://github.com/grpc/grpc.git
  # when using gRPC, you will actually set this to an existing tag, such as
  # v1.25.0, v1.26.0 etc..
  # For the purpose of testing, we override the tag used to the commit
  # that's currently under test.
  GIT_TAG        v1.28.1)
FetchContent_MakeAvailable(grpc)

# Since FetchContent uses add_subdirectory under the hood, we can use
# the grpc targets directly from this build.
set(_PROTOBUF_LIBPROTOBUF libprotobuf)
set(_REFLECTION grpc++_reflection)
set(_PROTOBUF_PROTOC $<TARGET_FILE:protoc>)
set(_GRPC_GRPCPP grpc++)
if(CMAKE_CROSSCOMPILING)
  find_program(_GRPC_CPP_PLUGIN_EXECUTABLE grpc_cpp_plugin)
else()
  set(_GRPC_CPP_PLUGIN_EXECUTABLE $<TARGET_FILE:grpc_cpp_plugin>)
endif()


# Proto file
get_filename_component(plump_proto "protos/plump.proto" ABSOLUTE)
get_filename_component(plump_proto_path "${plump_proto}" PATH)

# Generated sources
set(plump_proto_srcs "${CMAKE_CURRENT_BINARY_DIR}/plump.pb.cc")
set(plump_proto_hdrs "${CMAKE_CURRENT_BINARY_DIR}/plump.pb.h")
set(plump_grpc_srcs "${CMAKE_CURRENT_BINARY_DIR}/plump.grpc.pb.cc")
set(plump_grpc_hdrs "${CMAKE_CURRENT_BINARY_DIR}/plump.grpc.pb.h")
add_custom_command(
      OUTPUT "${plump_proto_srcs}" "${plump_proto_hdrs}" "${plump_grpc_srcs}" "${plump_grpc_hdrs}"
      COMMAND ${_PROTOBUF_PROTOC}
      ARGS --grpc_out "${CMAKE_CURRENT_BINARY_DIR}"
        --cpp_out "${CMAKE_CURRENT_BINARY_DIR}"
        -I "${plump_proto_path}"
        --grpc_out=generate_mock_code=true:.
        --plugin=protoc-gen-grpc="${_GRPC_CPP_PLUGIN_EXECUTABLE}"
        "${plump_proto}"
      DEPENDS "${plump_proto}")

# Include generated *.pb.h files
include_directories("${CMAKE_CURRENT_BINARY_DIR}")

# make the two libraries
add_library(plump_server_lib plump_server.h plump_server.cc ${plump_proto_srcs} ${plump_grpc_srcs})
target_link_libraries(plump_server_lib
  ${_REFLECTION}
  ${_GRPC_GRPCPP}
  ${_PROTOBUF_LIBPROTOBUF}
  OpenSSL::SSL)

add_library(plump_client_lib plump_client.h plump_client.cc ${plump_proto_srcs} ${plump_grpc_srcs})
target_link_libraries(plump_client_lib
  ${_REFLECTION}
  ${_GRPC_GRPCPP}
  ${_PROTOBUF_LIBPROTOBUF})

# Targets plump client|server
#foreach(_target
#  plump_client plump_server)
#  add_executable(${_target} "${_target}.cc" "${_target}.h"
#    ${plump_proto_srcs}
#    ${plump_grpc_srcs})
#  target_link_libraries(${_target}
#    ${_REFLECTION}
#    ${_GRPC_GRPCPP}
#    ${_PROTOBUF_LIBPROTOBUF})
#endforeach()

add_executable(plump_client client_main.cc
  ${plump_proto_srcs}
  ${plump_grpc_srcs})
target_link_libraries(plump_client
  ${_REFLECTION}
  ${_GRPC_GRPCPP}
  ${_PROTOBUF_LIBPROTOBUF}
  plump_client_lib)

add_executable(plump_server server_main.cc
  ${plump_proto_srcs}
  ${plump_grpc_srcs})
target_link_libraries(plump_server
  ${_REFLECTION}
  ${_GRPC_GRPCPP}
  ${_PROTOBUF_LIBPROTOBUF}
  plump_server_lib)

include_directories(.)

enable_testing()
add_subdirectory(test)