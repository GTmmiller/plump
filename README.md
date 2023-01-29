# Plump

![Plump logo](plump_logo.png)

## What is Plump?

Plump is a basic lock service that uses sequencers to maintain the order in which clients can access a lock.This idea was outlined in the [Google Whitepaper about Chubby](https://www.google.com/url?sa=t&rct=j&q=&esrc=s&source=web&cd=&ved=2ahUKEwiYtc2m_ez8AhXREFkFHXG8Dd8QFnoECAwQAQ&url=https%3A%2F%2Fresearch.google.com%2Farchive%2Fchubby-osdi06.pdf&usg=AOvVaw1OIHckC-w_kgQKUF1ml1R9) Plump is intended to be a simple to use, easy to understand, and easy to extend.

## How to use Plump?

The easiest way to get started with Plump is to download the latest full release. The full release contains the executable `server.jar`, the `client.jar` library, and the Plump `cli.jar`.

To start, simply run the server with the following command.

`java -jar server.jar`

Optionally, you can pass the `-p` option with a different port number to start the server on a different port.

`java -jar server.jar -p 50500`

While the server is running you can interact with it using `cli.jar`. `cli.jar` has multiple subcommands that can be detailed by calling the `help` command.

`java -jar cli.jar help`

From here you can run a few simple commands to make sure your lock server is working. Note that if you set the port of the server to something besides the default you will have to set the url flag `-u` on the cli to target the server.

`java -jar cli.jar create testLock`

`java -jar cli.jar list`

If you receive the name of the lock you just created back, then congratulations! Your plump server is ready to be used.

## How plump works

1. First, locks must be created on the server to start.

2. Each lock begins its life in the Unlocked state.

2. Sequencers act as claims on the lock and are first come first serve. You can't lock the lock without a sequencer. 

3. Sequencers have a set expiration time that can be extended by sending the sequencer to the keepAlive endpoint on the server or by attempting to lock the lock. Every time you send a sequencer to the server you receive a sequencer back with the same sequence number, but a different expiration time and confirmation code.

3. Only the client with the lowest number valid sequencer can claim the lock. Other sequencer holders will have to wait for the head sequencer holder to lock and finish with the lock, revoke their sequencer, or time out.

4. Once a client locks the lock, they keep it indefinitely as long as they keep their sequencer current by sending it to the `keepAlive` endpoint. A client loses the lock when they explicitly unlock it or when their sequencer holding the lock expires.

For more information about sequencers and Plump, check out the javadoc.

### Notes for client programmers

When building a client that works with Plump it's good to keep the following things in mind:

- Its best to revoke your outstanding sequencers if your client application is closing or if it errors in any way. They will eventually time out, but it's courteous to other users to revoke sequencers you know you're not going to need.

- Feel free to use utility methods like `whoHas` and `next` to evaluate how long you should wait before trying to get the lock again. Don't forget to wake up your client to keep the sequencers from timing out in the mean time.

- Try not to hold a lock for too long. If you need to do a lot of work try to do it in batches that take less time to prevent starvation.

- The Plump CLI (`cli.jar`) is an excellent example for a simple client implementation.

## Writing a lock implementation

`PlumpLock.java` is a memory-only implementation of a first in first out lock server that uses sequencers. A user can create a different lock type by implementing the `Lock.java` interface with a new class.

### Slim

`Slim` is provided as an example of an alternative lock service built using the lock interface. You can run your server in Slim mode by passing the lock type flag with slim. 

`java -jar server.jar -l slim`

Slim lets any valid Sequencer lock the lock as long as it's in the Unlocked state. This takes away the first come first served order from Plump and replaces it with more traditional lock competition.

### Using your custom lock implementation

Currently, in order to use your custom lock implementation you would have to add it to the source code and add a reference to it in the `Main.java` for the `server` project. In the future it would be great to load a class file from the command line.

## Building plump

If you want to build plump from source simply clone this repository and run the [Maven](https://maven.apache.org/) command:

`mvn clean install`

This will build the project, compile all the grpc and protocol buffer files and build everything. From then on, all you need to do is

`mvn package`

to build the entire project again or 

`mvn verify`

if you just want to check if tests pass.

Feel free to contribute if you see any typos or if you find any bugs!
