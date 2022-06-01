package com.wiligsi.plump.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * This is the class that takes the PlumpImpl class and runs it. Theoretically, this could be used
 * to run alternate implementations of the Plump server.
 *
 * @author Steven Miller
 */
public class PlumpServer {

  static final int DEFAULT_PORT = 50051;
  private static final Logger LOG = Logger.getLogger(PlumpServer.class.getName());

  private Server server;
  private final int port;

  /**
   * Constructs a new server on the default port.
   */
  public PlumpServer() {
    this(DEFAULT_PORT);
  }

  /**
   * Constructs a new server given a port number.
   *
   * @param port - the port number to run the server on
   */
  public PlumpServer(int port) {
    this.port = port;
  }

  /**
   * Start the server.
   *
   * @throws IOException              if the server fails to run
   * @throws NoSuchAlgorithmException if the server's digest algorithm is not available
   */
  public void start() throws IOException, NoSuchAlgorithmException {

    server = ServerBuilder.forPort(port)
        .addService(new PlumpImpl())
        .build()
        .start();
    LOG.info("Server started, listening on port: " + port);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.err.println("shuting down gRPC with the JVM");
      try {
        PlumpServer.this.stop();
      } catch (InterruptedException exception) {
        System.err.println("Error shutting down grpc");
        exception.printStackTrace(System.err);
      }

      System.err.println("gRPC server shut down");
    }));
  }

  private void stop() throws InterruptedException {
    if (server != null) {
      server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  /**
   * Shutdown the server safely.
   *
   * @throws InterruptedException if the shutdown is interrupted
   */
  public void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }
}
