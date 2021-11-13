package com.wiligsi.plump.server;

import static com.wiligsi.plump.PlumpOuterClass.*;

import com.wiligsi.plump.PlumpGrpc;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PlumpImpl extends PlumpGrpc.PlumpImplBase {

  private static final Logger LOG = Logger.getLogger(PlumpImpl.class.getName());
  private static final String DEFAULT_DIGEST_ALGORITHM = "SHA3-256";
  final ConcurrentMap<LockName, Lock> locks;
  final ConcurrentMap<String, LockName> destroyKeyHashMap;
  final SecureRandom secureRandom;
  final MessageDigest digest;

  public PlumpImpl() throws NoSuchAlgorithmException {
    super();
    locks = new ConcurrentHashMap<>();
    destroyKeyHashMap = new ConcurrentHashMap<>();
    secureRandom = SecureRandom.getInstanceStrong();
    digest = MessageDigest.getInstance(DEFAULT_DIGEST_ALGORITHM);
  }

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

  @Override
  public void listLocks(ListRequest request, StreamObserver<ListResponse> responseObserver) {
    // TODO: it would be easy to order since we're using stream operators already
    final Set<String> lockNames = locks.keySet().stream()
        .map(LockName::getDisplayName)
        .collect(Collectors.toUnmodifiableSet());

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
                "Lock named '%s' already exists",
                lockName.getDisplayName()
            )
        ).asException();
  }

  protected StatusException asLockDoesNotExistException(LockName lockName) {
    return Status.NOT_FOUND
        .withDescription(
            String.format(
                "Lock named '%s' does not exist",
                lockName.getDisplayName()
            )
        )
        .asException();
  }
}
