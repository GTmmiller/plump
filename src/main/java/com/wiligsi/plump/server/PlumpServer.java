package com.wiligsi.plump.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class PlumpServer {

  private static final Logger LOG = Logger.getLogger(PlumpServer.class.getName());
  private Server server;

  public void start() throws IOException, NoSuchAlgorithmException {
    final int port = 50051;

    server = ServerBuilder.forPort(port)
        .addService(new PlumpImpl())
        .build()
        .start();
    LOG.info("Server started, listening on port: " + port);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.err.println("shuting down gRPC with the JVM");
        try {
          PlumpServer.this.stop();
        } catch (InterruptedException exception) {
          System.err.println("Error shutting down grpc");
          exception.printStackTrace(System.err);
        }

        System.err.println("gRPC server shut down");
      }
    });
  }

  private void stop() throws InterruptedException {
    if (server != null) {
      server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  public void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }


}
