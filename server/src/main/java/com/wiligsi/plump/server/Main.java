package com.wiligsi.plump.server;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/**
 * The entry point for the Plump Lock Service Server. This will run a server until it crashes or is
 * interrupted. It can be passed a port number if the default port is unacceptable.
 *
 * @author Steven Miller
 */
public class Main {

  private static final String USAGE = "usage: ./server.java {port} Default port is "
      + PlumpServer.DEFAULT_PORT;

  /**
   * The entry point for running the server. A port number can be passed in to change the port if
   * desired.
   *
   * @param args - the command line arguments passed to the server. Expects 0-1 arguments.
   * @throws IOException              if there's an issue starting the server
   * @throws InterruptedException     if the server is sent an interrupt signal
   * @throws NoSuchAlgorithmException if the PlumpServer's hashing algorithm is not present
   */
  public static void main(String[] args)
      throws IOException, InterruptedException, NoSuchAlgorithmException {
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
