package com.wiligsi.plump.client;

import com.wiligsi.plump.PlumpGrpc;
import io.grpc.Channel;
import io.grpc.StatusRuntimeException;

import java.util.logging.Logger;

import static com.wiligsi.plump.PlumpOuterClass.*;

public class PlumpClient {

  private static final Logger LOG = Logger.getLogger(PlumpClient.class.getName());

  private final PlumpGrpc.PlumpBlockingStub plumpBlockingStub;

  public PlumpClient(Channel channel) {
    plumpBlockingStub = PlumpGrpc.newBlockingStub(channel);
  }

  public void createLock(String name) {
    LOG.info("Creating lock with name: " + name);
    CreateLockRequest request = CreateLockRequest.newBuilder().setLockName(name).build();
    CreateLockResponse response;

    try {
      response = plumpBlockingStub.createLock(request);
    } catch (StatusRuntimeException exception) {
      LOG.warning("RPC failed with status: " + exception.getStatus());
      return;
    }
    LOG.info(String.format("Created new lock with name: %s", name));
  }

  public void deleteLock(String name) {
    LOG.info("Deleting lock with name: " + name);
    DestroyLockRequest request = DestroyLockRequest.newBuilder().setLockName(name).build();
    DestroyLockResponse response;

    try {
      response = plumpBlockingStub.destroyLock(request);
    } catch (StatusRuntimeException exception) {
      LOG.warning("RPC failed with status: " + exception.getStatus());
      return;
    }
    LOG.info(String.format("Deleted lock with name: %s", name));
  }
}
