package com.wiligsi.plump.client;

import com.wiligsi.plump.common.PlumpOuterClass.CreateLockResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class Main {

  private static final String USAGE = "usage: ./client.jar host:port clientMethod [methodParams]";

  private static final String DEFAULT_TARGET = "localhost:50051";

  private static final List<String> CLIENT_METHODS = List.of("createLock", "deleteLock");

  public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException {
    final Queue<String> argQueue = new LinkedList<>(List.of(args));
    if (argQueue.size() == 0) {
      System.out.println("Expected at least 1 argument but received " + args.length + " " + USAGE);
      System.exit(-1);
    }

    String clientTarget = DEFAULT_TARGET;
    if (argQueue.peek().contains(":")) {
      clientTarget = argQueue.poll();
    }

    ManagedChannel channel = ManagedChannelBuilder.forTarget(clientTarget)
        .usePlaintext()
        .build();
    try {
      final String clientMethod = argQueue.poll();
      if (clientMethod == null) {
        System.out.println("No client method requested");
        System.exit(-2);
      }
      PlumpClient client = new PlumpClient(channel);

      switch (clientMethod) {
        case "createLock":
          checkMethodArgs(clientMethod, 1, argQueue.size());
          final String createName = argQueue.poll();
          CreateLockResponse createResponse = client.createLock(createName);
          System.out.println("Successfully created lock '" + createName + "'");
          System.out.println("The destroy key for your lock is '" + createResponse.getDestroyKey() + "'");
          System.out.println("Keep this key secret and safe");
          break;
        case "destroyLock":
          checkMethodArgs(clientMethod, 2, argQueue.size());
          final String destroyName = argQueue.poll();
          final String destroyKey = argQueue.poll();
          client.destroyLock(destroyName, destroyKey);
          System.out.println("Successfully destroyed lock '" + destroyName + "'");
          break;
        default:
          System.out.println("Unrecognized client method '" + clientMethod + "'");
          System.exit(-3);
          break;
      }
    } catch (StatusRuntimeException exception) {
      System.err.println("RPC calling failed with status: " + exception.getStatus());
    } finally {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private static void checkMethodArgs(String commandName, int requiredArguments, int actualArguments) {
    if(actualArguments != requiredArguments) {
      System.out.println("Incorrect number of arguments for '" + commandName + "'");
      System.out.println("Expected: " + requiredArguments + " but received: " + actualArguments);
      System.exit(-4);
    }
  }
}
