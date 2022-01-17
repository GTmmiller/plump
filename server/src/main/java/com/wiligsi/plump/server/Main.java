package com.wiligsi.plump.server;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class Main {

  private static final String USAGE = "usage: ./server.java {port} Default port is " + PlumpServer.DEFAULT_PORT;

  public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException {
    if (args.length > 1) {
      System.out.println("Expected at most 1 argument, but received " + args.length + " arguments");
      System.out.println(USAGE);
      System.exit(-1);
    }

    final PlumpServer server;
    if (args.length == 1) {
      final int serverPort = Integer.parseInt(args[0]);
      server = new PlumpServer(serverPort);
    } else {
      server = new PlumpServer();
    }

    server.start();
    server.blockUntilShutdown();
  }
}
