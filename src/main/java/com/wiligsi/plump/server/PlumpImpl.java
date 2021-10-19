package com.wiligsi.plump.server;

import com.wiligsi.plump.PlumpGrpc;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import static com.wiligsi.plump.PlumpOuterClass.*;

public class PlumpImpl extends PlumpGrpc.PlumpImplBase {

    private static final Logger LOG = Logger.getLogger(PlumpImpl.class.getName());
    final ConcurrentMap<LockName, Lock> locks;

    public PlumpImpl() {
        super();
        locks = new ConcurrentHashMap<>();
    }

    @Override
    public void createLock(CreateLockRequest request, StreamObserver<CreateLockReply> responseObserver) {
        try {
            final String newLockName = request.getLockName();
            // We can catch this later, but LockNames are small and Locks are much heavier
            ensureLockDoesNotExist(buildLockName(newLockName));

            final Lock newLock = buildLock(newLockName);
            Lock oldLock = locks.putIfAbsent(newLock.getName(), newLock);
            if (oldLock != null) {
                throw asLockAlreadyExistsException(newLockName);
            }
            responseObserver.onNext(CreateLockReply.newBuilder().build());
            responseObserver.onCompleted();
        } catch (StatusException statusException) {
            responseObserver.onError(statusException);
        }
    }

    @Override
    public void destroyLock(DestroyLockRequest request, StreamObserver<DestroyLockReply> responseObserver) {
        // TODO: make a key on creation/deletion so that only someone with the key can delete the lock
        try {
            final LockName destroyLockName = buildLockName(request.getLockName());
            ensureLockAlreadyExists(destroyLockName);
            locks.remove(destroyLockName);
            responseObserver.onNext(DestroyLockReply.newBuilder().build());
            responseObserver.onCompleted();
        } catch (StatusException validationException) {
            responseObserver.onError(validationException);
        }
    }

    @Override
    public void acquireSequencer(SequencerRequest request, StreamObserver<SequencerReply> responseObserver) {
        try {
            final LockName requestLockName = buildLockName(request.getLockName());
            final Lock requestLock = safeGetLock(requestLockName);
            final Sequencer responseSequencer = requestLock.createSequencer();
            responseObserver.onNext(
                    SequencerReply.newBuilder()
                            .setSequencer(responseSequencer)
                            .build()
            );
            responseObserver.onCompleted();
        } catch (StatusException validationException) {
            responseObserver.onError(validationException);
        }
    }

    @Override
    public void acquireLock(LockRequest request, StreamObserver<LockReply> responseObserver) {
        final Sequencer requestSequencer = request.getSequencer();
        final LockName requestLockName;
        try {
            requestLockName = new LockName(requestSequencer.getLockName());
            ensureLockAlreadyExists(requestLockName);
        } catch (StatusException validationException) {
            responseObserver.onError(validationException);
            return;
        }
        // TODO: Malformed lock name consideration
        // TODO: Break common stuff out into more methods
        // TODO: Use typed methods to send errors and stuff

        final Lock acquireLock = locks.get(requestLockName);
        final boolean success;
        final Sequencer keepAliveSequencer;
        try {
            success = acquireLock.acquire(requestSequencer);
            keepAliveSequencer = acquireLock.keepAlive(requestSequencer);
        } catch (InvalidSequencerException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .withCause(exception)
                            .asException()
            );
            return;
        }

        responseObserver.onNext(
                LockReply.newBuilder()
                        .setUpdatedSequencer(keepAliveSequencer)
                        .setSuccess(success)
                        .setKeepAliveInterval(Duration.ofMinutes(2).toMillis())
                        .build()
        );
        responseObserver.onCompleted();
    }

    @Override
    public void keepAlive(KeepAliveRequest request, StreamObserver<KeepAliveReply> responseObserver) {
        final Sequencer requestSequencer = request.getSequencer();
        final LockName requestLockName;
        try {
            requestLockName = buildLockName(requestSequencer.getLockName());
            ensureLockAlreadyExists(requestLockName);
            Lock keepAliveLock = locks.get(requestLockName);
            Sequencer newSequencer =  keepAliveLock.keepAlive(requestSequencer);
            responseObserver.onNext(
                    KeepAliveReply.newBuilder()
                            .setUpdatedSequencer(newSequencer)
                            .setKeepAliveInterval(Duration.ofMinutes(2).toMillis())
                            .build()
            );
            responseObserver.onCompleted();
        } catch (StatusException validationException) {
            responseObserver.onError(validationException);
        } catch (InvalidSequencerException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .withCause(exception)
                            .asException()
            );
        }

        // TODO: or, combine all of the try/catch blocks and catch everything

    }

    @Override
    public void releaseLock(ReleaseRequest request, StreamObserver<ReleaseReply> responseObserver) {
        try{
            Sequencer releaseSequencer = request.getSequencer();
            LockName lockName = buildLockName(releaseSequencer.getLockName());
            Lock releaseLock = locks.get(lockName);
            boolean success = releaseLock.release(releaseSequencer);
            ReleaseReply.Builder replyBase = ReleaseReply.newBuilder()
                    .setKeepAliveInterval(Duration.ofMinutes(2).toMillis())
                    .setSuccess(success);

            if (success) {
                responseObserver.onNext(
                        replyBase.build()
                );
            } else {
                Sequencer updatedSequencer = releaseLock.keepAlive(releaseSequencer);
                responseObserver.onNext(
                        replyBase.setUpdatedSequencer(updatedSequencer)
                                .build()
                );
            }
            responseObserver.onCompleted();
        } catch (StatusException exception) {
            responseObserver.onError(exception);
        } catch (InvalidSequencerException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .withCause(exception)
                            .asException()
            );
        }
    }

    @Override
    public void whoHasLock(WhoHasRequest request, StreamObserver<WhoHasReply> responseObserver) {
        super.whoHasLock(request, responseObserver);
    }

    @Override
    public void nextSequencer(NextSequencerRequest request, StreamObserver<NextSequencerReply> responseObserver) {
        super.nextSequencer(request, responseObserver);
    }

    @Override
    public void listLocks(ListRequest request, StreamObserver<ListReply> responseObserver) {
        super.listLocks(request, responseObserver);
    }

    protected void ensureLockAlreadyExists(LockName lockName) throws StatusException {
        if (!locks.containsKey(lockName)) {
            throw asLockDoesNotExistException(lockName);
        }
    }

    protected void ensureLockDoesNotExist(LockName lockName) throws StatusException {
        if (locks.containsKey(lockName)) {
            throw asLockAlreadyExistsException(lockName.getDisplayName());
        }
    }

    protected LockName buildLockName(String lockName) throws StatusException {
        try {
            return new LockName(lockName);
        } catch (IllegalArgumentException argumentException) {
            throw asStatusException(Status.INVALID_ARGUMENT, argumentException);
        }
    }

    protected Lock buildLock(String lockName) throws StatusException {
        try {
            return new Lock(lockName);
        } catch (IllegalArgumentException argumentException) {
            throw asStatusException(Status.INVALID_ARGUMENT, argumentException);
        } catch (NoSuchAlgorithmException algorithmException) {
            throw asStatusException(Status.INTERNAL, algorithmException);
        }
    }

    protected StatusException asStatusException(Status status, Throwable exception) {
        return status.withDescription(exception.getMessage())
                .withCause(exception)
                .asException();
    }

    protected StatusException asLockAlreadyExistsException(String lockName) {
        return Status.ALREADY_EXISTS
                .withDescription(
                        String.format(
                                "Lock named '%s' already exists",
                                lockName
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

    protected Lock safeGetLock(LockName lockName) throws StatusException {
        Lock getLock =  locks.get(lockName);
        if (getLock == null) {
            throw asLockDoesNotExistException(lockName);
        }
        return getLock;
    }
}
