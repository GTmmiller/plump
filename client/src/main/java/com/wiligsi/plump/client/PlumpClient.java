package com.wiligsi.plump.client;

import static com.wiligsi.plump.common.PlumpOuterClass.CreateLockRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.CreateLockResponse;
import static com.wiligsi.plump.common.PlumpOuterClass.DestroyLockRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.KeepAliveRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.KeepAliveResponse;
import static com.wiligsi.plump.common.PlumpOuterClass.ListRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.LockRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.LockResponse;
import static com.wiligsi.plump.common.PlumpOuterClass.NextSequencerRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.ReleaseRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.ReleaseResponse;
import static com.wiligsi.plump.common.PlumpOuterClass.Sequencer;
import static com.wiligsi.plump.common.PlumpOuterClass.SequencerRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.WhoHasRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.WhoHasResponse;

import com.wiligsi.plump.common.PlumpGrpc;
import io.grpc.Channel;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * This client takes the boilerplate out of interacting with the plump server by creating methods
 * that wrap the process of managing the requests/response with the server.
 *
 * @author Steven Miller
 */
public class PlumpClient {

  private static final Logger LOG = Logger.getLogger(PlumpClient.class.getName());

  private final PlumpGrpc.PlumpBlockingStub plumpBlockingStub;

  /**
   * Create a new client instance given a channel for a Plump server.
   *
   * @param channel - the channel the client uses to send requests
   */
  public PlumpClient(Channel channel) {
    plumpBlockingStub = PlumpGrpc.newBlockingStub(channel);
  }

  /**
   * Create a new lock on the Plump server.
   *
   * @param name - the name of the new lock. It should be a unique name.
   * @return The destroy key for the newly created lock.
   */
  public String createLock(String name) {
    LOG.info("Creating lock with name: " + name);
    CreateLockRequest request = CreateLockRequest.newBuilder().setLockName(name).build();
    CreateLockResponse response = plumpBlockingStub.createLock(request);
    LOG.info(String.format("Created new lock with name: %s", name));
    return response.getDestroyKey();
  }

  /**
   * Destroy a lock on the Plump server with a given destroy key.
   *
   * @param name       - the name of the lock being destroyed.
   * @param destroyKey - the destroy key for the lock.
   */
  public void destroyLock(String name, String destroyKey) {
    LOG.info("Destroying lock with name: " + name);
    DestroyLockRequest request = DestroyLockRequest.newBuilder()
        .setLockName(name)
        .setDestroyKey(destroyKey)
        .build();
    plumpBlockingStub.destroyLock(request);
    LOG.info(String.format("Destroyed lock with name: %s", name));
  }

  /**
   * List out the available locks on the server.
   *
   * @return - a list of lock names.
   */
  public List<String> listLocks() {
    ListRequest request = ListRequest.newBuilder().build();
    return plumpBlockingStub.listLocks(request).getLockNamesList();
  }

  /**
   * Acquire a Sequencer from a lock.
   *
   * @param lockName - the lock to acquire a Sequencer from.
   * @return - the requested Sequencer
   */
  public Sequencer acquireSequencer(String lockName) {
    SequencerRequest request = SequencerRequest.newBuilder()
        .setLockName(lockName)
        .build();
    return plumpBlockingStub.acquireSequencer(request).getSequencer();
  }

  /**
   * Attempt to acquire a Lock with a given Sequencer.
   *
   * @param lockSequencer - the Sequencer used to acquire the Lock.
   * @return the result of the acquisition request.
   */
  public LockResponse acquireLock(Sequencer lockSequencer) {
    return plumpBlockingStub.acquireLock(
        LockRequest.newBuilder()
            .setSequencer(lockSequencer)
            .build()
    );
  }

  /**
   * Continually try to acquire a lock until the server errors, or you get the lock.
   * @param lockSequencer - the Sequencer used to acquire the Lock.
   * @return the Sequencer used to acquire the Lock.
   */
  public Sequencer awaitLock(Sequencer lockSequencer) throws InterruptedException {
    Sequencer updatedSequencer = lockSequencer;
    boolean locked = false;
    while (!locked) {
      LOG.info("Attempting to lock");
      LockResponse response = acquireLock(updatedSequencer);

      updatedSequencer = response.getUpdatedSequencer();
      locked = response.getSuccess();
      if (!locked) {
        Thread.sleep(2);
      }
    }

    return updatedSequencer;
  }

  /**
   * Release a Lock you have acquired with the Sequencer used to acquire the Lock.
   *
   * @param unlockSequencer - the sequencer used to acquire the lock.
   * @return the result of the release request.
   */
  public ReleaseResponse releaseLock(Sequencer unlockSequencer) {
    return plumpBlockingStub.releaseLock(
        ReleaseRequest.newBuilder()
            .setSequencer(unlockSequencer)
            .build()
    );
  }

  /**
   * Renew a passed in Sequencer. It works for any Sequencer that has not timed out and has not
   * already been used to acquire a Lock.
   *
   * @param sequencer - the sequencer to keep alive.
   * @return an updated version of the passed in sequencer.
   */
  public KeepAliveResponse keepAlive(Sequencer sequencer) {
    return plumpBlockingStub.keepAlive(
        KeepAliveRequest.newBuilder()
            .setSequencer(sequencer)
            .build()
    );
  }

  /**
   * Gets the next available sequence number for the requested Lock.
   *
   * @param lockName - the name of the lock to query.
   * @return the Lock's next available sequence number.
   */
  public int getNextSequencer(String lockName) {
    return plumpBlockingStub.nextSequencer(
        NextSequencerRequest.newBuilder()
            .setLockName(lockName)
            .build()
    ).getSequenceNumber();
  }

  /**
   * Lets you know if a Lock is acquired, and if so, by which sequence number.
   *
   * @param lockName - the name of the lock to query.
   * @return An optional containing the sequence number that acquired the lock or an empty sequencer
   *        if the lock is not acquired.
   */
  public Optional<Integer> whoHasLock(String lockName) {
    final WhoHasResponse response = plumpBlockingStub.whoHasLock(
        WhoHasRequest.newBuilder()
            .setLockName(lockName)
            .build()
    );

    if (response.getLocked()) {
      return Optional.of(response.getSequenceNumber());
    } else {
      return Optional.empty();
    }
  }

}
