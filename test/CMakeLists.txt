include(FetchContent)

message(STATUS "Using googletest via add_subdirectory (FetchContent).")
FetchContent_Declare(
  googletest
  GIT_REPOSITORY https://github.com/google/googletest.git
  GIT_TAG        release-1.10.0
)

FetchContent_GetProperties(googletest)
if(NOT googletest_POPULATED)
  FetchContent_Populate(googletest)
  add_subdirectory(${googletest_SOURCE_DIR} ${googletest_BINARY_DIR})
endif()

# set up dependencies for testing
include(GoogleTest)

add_executable(plump_unittest plump_unittest.cc)
target_link_libraries(
  plump_unittest
  gtest
  gmock
  gtest_main
)

gtest_discover_tests(plump_unittest)