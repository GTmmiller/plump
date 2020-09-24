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
