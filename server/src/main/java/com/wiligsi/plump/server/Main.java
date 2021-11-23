package com.wiligsi.plump.server;

import client.PlumpClient;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class Main {

  private static final String USAGE = "usage: ./Plump [server {port} |client host:port clientMethod ]";

  public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException {
    if (args.length == 0) {
      System.out.println("Expected at least 1 argument but received " + args.length + " " + USAGE);
      System.exit(-1);
    }

    final String commandArg = args[0].trim().toLowerCase(Locale.ROOT);

    switch (commandArg) {
      case "server":
        final PlumpServer server;
        if (args.length >= 2) {
          final int portArg = Integer.parseInt(args[1]);
          server = new PlumpServer(portArg);
        } else {
          server = new PlumpServer();
        }

        server.start();
        server.blockUntilShutdown();
        break;
      case "client":
        ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:50051")
            .usePlaintext()
            .build();
        try {
          PlumpClient client = new PlumpClient(channel);
          client.createLock("NuLock");
        } finally {
          channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        break;
      default:
        System.out.printf("Unknown argument %s. Expected 'server' or 'client'%n", commandArg);
        System.exit(-3);
        break;
    }
  }
}
