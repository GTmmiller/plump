package com.wiligsi.plump.client;

import static com.wiligsi.plump.common.PlumpOuterClass.*;

import com.wiligsi.plump.common.PlumpGrpc;
import io.grpc.Channel;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class PlumpClient {

  private static final Logger LOG = Logger.getLogger(PlumpClient.class.getName());

  private final PlumpGrpc.PlumpBlockingStub plumpBlockingStub;

  public PlumpClient(Channel channel) {
    plumpBlockingStub = PlumpGrpc.newBlockingStub(channel);
  }

  public String createLock(String name) throws StatusRuntimeException {
    LOG.info("Creating lock with name: " + name);
    CreateLockRequest request = CreateLockRequest.newBuilder().setLockName(name).build();
    CreateLockResponse response = plumpBlockingStub.createLock(request);
    LOG.info(String.format("Created new lock with name: %s", name));
    return response.getDestroyKey();
  }

  public void destroyLock(String name, String destroyKey) throws StatusRuntimeException {
    LOG.info("Destroying lock with name: " + name);
    DestroyLockRequest request = DestroyLockRequest.newBuilder()
        .setLockName(name)
        .setDestroyKey(destroyKey)
        .build();
    plumpBlockingStub.destroyLock(request);
    LOG.info(String.format("Destroyed lock with name: %s", name));
  }

  public List<String> listLocks() {
    ListRequest request = ListRequest.newBuilder().build();
    return plumpBlockingStub.listLocks(request).getLockNamesList();
  }

  public Sequencer acquireSequencer(String lockName) {
    SequencerRequest request = SequencerRequest.newBuilder()
        .setLockName(lockName)
        .build();
    return plumpBlockingStub.acquireSequencer(request).getSequencer();
  }
}
