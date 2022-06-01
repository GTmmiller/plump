package com.wiligsi.plump.server;

import static com.wiligsi.plump.common.PlumpOuterClass.CreateLockRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.CreateLockResponse;
import static com.wiligsi.plump.common.PlumpOuterClass.DestroyLockRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.DestroyLockResponse;
import static com.wiligsi.plump.common.PlumpOuterClass.KeepAliveRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.KeepAliveResponse;
import static com.wiligsi.plump.common.PlumpOuterClass.ListRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.ListResponse;
import static com.wiligsi.plump.common.PlumpOuterClass.LockRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.LockResponse;
import static com.wiligsi.plump.common.PlumpOuterClass.NextSequencerRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.NextSequencerResponse;
import static com.wiligsi.plump.common.PlumpOuterClass.ReleaseRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.ReleaseResponse;
import static com.wiligsi.plump.common.PlumpOuterClass.Sequencer;
import static com.wiligsi.plump.common.PlumpOuterClass.SequencerRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.SequencerResponse;
import static com.wiligsi.plump.common.PlumpOuterClass.WhoHasRequest;
import static com.wiligsi.plump.common.PlumpOuterClass.WhoHasResponse;

import com.wiligsi.plump.common.PlumpGrpc;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This is the implementation of the Plump server itself that inherits from the Grpc stub
 * interface.
 *
 * <p>
 * The idea here is to have a map of Locks that are created, destroyed, and acted upon by the
 * server. Most of the complicated logic behind Sequencers is managed by the Lock class itself.
 * ConcurrentMap classes are used to make multi-threaded logic easier to reason about.
 * </p>
 *
 * @author Steven Miller
 */
public class PlumpImpl extends PlumpGrpc.PlumpImplBase {

  private static final Logger LOG = Logger.getLogger(PlumpImpl.class.getName());
  private static final String DEFAULT_DIGEST_ALGORITHM = "SHA3-256";
  final ConcurrentMap<LockName, Lock> locks;
  final ConcurrentMap<String, LockName> destroyKeyHashMap;
  final SecureRandom secureRandom;
  final MessageDigest digest;

  /**
   * Create a new PlumpImpl instance.
   *
   * @throws NoSuchAlgorithmException if the DEFAULT_DIGEST_ALGORITHM isn't available
   */
  public PlumpImpl() throws NoSuchAlgorithmException {
    super();
    locks = new ConcurrentHashMap<>();
    destroyKeyHashMap = new ConcurrentHashMap<>();
    secureRandom = SecureRandom.getInstanceStrong();
    digest = MessageDigest.getInstance(DEFAULT_DIGEST_ALGORITHM);
  }

  /**
   * Creates a new Lock given a string name. It will return errors if the lock name is invalid.
   *
   * @param request          - the request to create a new lock. Contains a single string name
   * @param responseObserver - the response stream used to send the response
   */
  @Override
  public void createLock(CreateLockRequest request,
      StreamObserver<CreateLockResponse> responseObserver) {
    try {
      final LockName newLockName = buildLockName(request.getLockName());
      if (locks.containsKey(newLockName)) {
        throw asLockAlreadyExistsException(newLockName);
      }

      final Lock newLock = buildLock(newLockName);
      Lock oldLock = locks.putIfAbsent(newLock.getName(), newLock);
      if (oldLock != null) {
        throw asLockAlreadyExistsException(newLockName);
      }

      final String destroyKey = KeyUtil.generateRandomKey(secureRandom);
      destroyKeyHashMap.put(KeyUtil.hashKey(destroyKey, digest), newLock.getName());

      responseObserver.onNext(
          CreateLockResponse.newBuilder()
              .setDestroyKey(destroyKey)
              .build());
      responseObserver.onCompleted();
    } catch (StatusException statusException) {
      responseObserver.onError(statusException);
    }
  }

  /**
   * Destroys a lock given a name and a 'destroy' token. Succeeds if the token and the lock name are
   * valid and correspond to each other.
   *
   * @param request          - the request to destroy the lock containing the token and the lock
   *                         name
   * @param responseObserver - the response stream used to respond to the client
   */
  @Override
  public void destroyLock(DestroyLockRequest request,
      StreamObserver<DestroyLockResponse> responseObserver) {
    try {
      final LockName destroyLockName = buildLockName(request.getLockName());
      ensureLockExists(destroyLockName);
      final String destroyKey = request.getDestroyKey();
      verifyDestroyKey(destroyKey, destroyLockName);
      locks.remove(destroyLockName);
      destroyKeyHashMap.remove(destroyKey);
      responseObserver.onNext(DestroyLockResponse.newBuilder().build());
      responseObserver.onCompleted();
    } catch (StatusException validationException) {
      responseObserver.onError(validationException);
    }
  }

  /**
   * Acquires a sequencer from a given lock.
   *
   * @param request          - the request to acquire a sequencer that contains the lock name
   * @param responseObserver - the response stream used to respond to the client
   */
  @Override
  public void acquireSequencer(SequencerRequest request,
      StreamObserver<SequencerResponse> responseObserver) {
    try {
      final LockName requestLockName = buildLockName(request.getLockName());
      ensureLockExists(requestLockName);
      final Lock requestLock = safeGetLock(requestLockName);
      final Sequencer responseSequencer = requestLock.createSequencer();
      responseObserver.onNext(
          SequencerResponse.newBuilder()
              .setSequencer(responseSequencer)
              .build()
      );
      responseObserver.onCompleted();
    } catch (StatusException validationException) {
      responseObserver.onError(validationException);
    }
  }

  /**
   * Attempt to acquire a lock using a sequencer. Can be successful or unsuccessful depending on the
   * outcome of the lock attempt.
   *
   * @param request          - a locking request containing a Sequencer
   * @param responseObserver - the response stream used to respond to the client
   */
  @Override
  public void acquireLock(LockRequest request, StreamObserver<LockResponse> responseObserver) {
    try {
      ensureHasSequencer(request.hasSequencer());
      final Sequencer requestSequencer = request.getSequencer();
      final LockName requestLockName = buildLockName(requestSequencer.getLockName());
      ensureLockExists(requestLockName);
      final Lock acquireLock = safeGetLock(requestLockName);
      final Sequencer keepAliveSequencer = acquireLock.keepAlive(requestSequencer);
      final boolean success = acquireLock.acquire(keepAliveSequencer);

      responseObserver.onNext(
          LockResponse.newBuilder()
              .setUpdatedSequencer(keepAliveSequencer)
              .setSuccess(success)
              .setKeepAliveInterval(acquireLock.getKeepAliveInterval().toMillis())
              .build()
      );
      responseObserver.onCompleted();
    } catch (StatusException validationException) {
      responseObserver.onError(validationException);
    } catch (InvalidSequencerException sequencerException) {
      responseObserver.onError(
          asStatusException(Status.INVALID_ARGUMENT, sequencerException)
      );
    }
  }

  /**
   * Renew a passed in Sequencer to extend its expiration time.
   *
   * @param request          - the sequencer that should be extended
   * @param responseObserver - the response stream used to respond to the client
   */
  @Override
  public void keepAlive(KeepAliveRequest request,
      StreamObserver<KeepAliveResponse> responseObserver) {
    try {
      ensureHasSequencer(request.hasSequencer());
      final Sequencer requestSequencer = request.getSequencer();
      final LockName requestLockName = buildLockName(requestSequencer.getLockName());
      ensureLockExists(requestLockName);

      final Lock keepAliveLock = safeGetLock(requestLockName);
      Sequencer newSequencer = keepAliveLock.keepAlive(requestSequencer);
      responseObserver.onNext(
          KeepAliveResponse.newBuilder()
              .setUpdatedSequencer(newSequencer)
              .setKeepAliveInterval(keepAliveLock.getKeepAliveInterval().toMillis())
              .build()
      );
      responseObserver.onCompleted();
    } catch (StatusException validationException) {
      responseObserver.onError(validationException);
    } catch (InvalidSequencerException sequencerException) {
      responseObserver.onError(
          asStatusException(Status.INVALID_ARGUMENT, sequencerException)
      );
    }
  }

  /**
   * Attempt to release a Lock given a Sequencer.
   *
   * @param request          - a request to release a Lock containing a sequencer
   * @param responseObserver - the response stream used to respond to the client
   */
  @Override
  public void releaseLock(ReleaseRequest request,
      StreamObserver<ReleaseResponse> responseObserver) {
    try {
      ensureHasSequencer(request.hasSequencer());
      Sequencer releaseSequencer = request.getSequencer();
      LockName lockName = buildLockName(releaseSequencer.getLockName());
      ensureLockExists(lockName);

      Lock releaseLock = safeGetLock(lockName);
      boolean success = releaseLock.release(releaseSequencer);
      ReleaseResponse.Builder responseBase = ReleaseResponse.newBuilder()
          .setKeepAliveInterval(releaseLock.getKeepAliveInterval().toMillis())
          .setSuccess(success);

      if (success) {
        responseObserver.onNext(
            responseBase.build()
        );
      } else {
        Sequencer updatedSequencer = releaseLock.keepAlive(releaseSequencer);
        responseObserver.onNext(
            responseBase.setUpdatedSequencer(updatedSequencer).build()
        );
      }
      responseObserver.onCompleted();
    } catch (StatusException exception) {
      responseObserver.onError(exception);
    } catch (InvalidSequencerException sequencerException) {
      responseObserver.onError(
          asStatusException(Status.INVALID_ARGUMENT, sequencerException)
      );
    }
  }

  /**
   * Request the status of a Lock including the LockStatus and the sequence number of the Sequencer
   * that has acquired the lock.
   *
   * @param request          - a request for the lock's status
   * @param responseObserver - the response stream used to respond to the client
   */
  @Override
  public void whoHasLock(WhoHasRequest request, StreamObserver<WhoHasResponse> responseObserver) {
    try {
      final LockName requestName = buildLockName(request.getLockName());
      ensureLockExists(requestName);
      final Lock requestLock = safeGetLock(requestName);
      final LockState state = requestLock.getState();
      final Optional<Integer> headSequencerNumber = requestLock.getHeadSequencerNumber();

      final WhoHasResponse whoHasResponse;
      if (state == LockState.LOCKED && headSequencerNumber.isPresent()) {
        whoHasResponse = WhoHasResponse.newBuilder()
            .setLocked(true)
            .setSequenceNumber(headSequencerNumber.get())
            .build();
      } else {
        whoHasResponse = WhoHasResponse.newBuilder()
            .setLocked(false)
            .build();
      }
      responseObserver.onNext(whoHasResponse);
      responseObserver.onCompleted();
    } catch (StatusException statusException) {
      responseObserver.onError(statusException);
    }
  }

  /**
   * Request what the next sequence number will be for a given Lock.
   *
   * @param request          - a request including the lock to query
   * @param responseObserver - the response stream used to respond to the client
   */
  @Override
  public void nextSequencer(NextSequencerRequest request,
      StreamObserver<NextSequencerResponse> responseObserver) {
    try {
      final LockName requestName = buildLockName(request.getLockName());
      ensureLockExists(requestName);
      final Lock requestLock = safeGetLock(requestName);
      final int nextSequenceNumber = requestLock.getNextSequenceNumber();

      responseObserver.onNext(
          NextSequencerResponse.newBuilder()
              .setSequenceNumber(nextSequenceNumber)
              .build()
      );
      responseObserver.onCompleted();
    } catch (StatusException statusException) {
      responseObserver.onError(statusException);
    }
  }

  /**
   * Get an alphabetical list of locks that are stored on the server.
   *
   * @param request          - a list lock request
   * @param responseObserver - the response stream used to respond to the client
   */
  @Override
  public void listLocks(ListRequest request, StreamObserver<ListResponse> responseObserver) {
    final List<String> lockNames = locks.keySet().stream()
        .map(LockName::getDisplayName)
        .sorted()
        .collect(Collectors.toUnmodifiableList());

    final ListResponse response = ListResponse.newBuilder()
        .addAllLockNames(lockNames)
        .build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  protected void ensureLockExists(LockName lockName) throws StatusException {
    if (!locks.containsKey(lockName)) {
      throw asLockDoesNotExistException(lockName);
    }
  }

  protected void ensureHasSequencer(boolean requestHasSequencer) throws StatusException {
    if (!requestHasSequencer) {
      throw Status.INVALID_ARGUMENT
          .withDescription(
              "Passed sequencer is null"
          )
          .asException();
    }
  }

  protected LockName buildLockName(String lockName) throws StatusException {
    try {
      return new LockName(lockName);
    } catch (IllegalArgumentException argumentException) {
      throw asStatusException(Status.INVALID_ARGUMENT, argumentException);
    }
  }

  protected Lock buildLock(LockName lockName) throws StatusException {
    try {
      return new Lock(lockName);
    } catch (IllegalArgumentException argumentException) {
      throw asStatusException(Status.INVALID_ARGUMENT, argumentException);
    } catch (NoSuchAlgorithmException algorithmException) {
      throw asStatusException(Status.INTERNAL, algorithmException);
    }
  }

  protected Lock safeGetLock(LockName lockName) throws StatusException {
    Lock getLock = locks.get(lockName);
    if (getLock == null) {
      throw asLockDoesNotExistException(lockName);
    }
    return getLock;
  }

  protected void verifyDestroyKey(String destroyKey, LockName lockName) throws StatusException {
    final String keyHash = KeyUtil.hashKey(destroyKey, digest);
    final LockName expectedKeyName = destroyKeyHashMap.get(keyHash);
    if (!lockName.equals(expectedKeyName)) {
      throw Status.INVALID_ARGUMENT
          .withDescription(
              String.format(
                  "Cannot destroy lock '%s'. Destroy key is invalid.",
                  lockName.getDisplayName()
              )
          )
          .asException();
    }

  }

  protected StatusException asStatusException(Status status, Throwable exception) {
    return status.withDescription(exception.getMessage())
        .withCause(exception)
        .asException();
  }

  protected StatusException asLockAlreadyExistsException(LockName lockName) {
    return Status.ALREADY_EXISTS
        .withDescription(
            String.format(
                "com.wiligsi.plump.server.Lock named '%s' already exists",
                lockName.getDisplayName()
            )
        ).asException();
  }

  protected StatusException asLockDoesNotExistException(LockName lockName) {
    return Status.NOT_FOUND
        .withDescription(
            String.format(
                "com.wiligsi.plump.server.Lock named '%s' does not exist",
                lockName.getDisplayName()
            )
        )
        .asException();
  }
}
