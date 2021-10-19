package com.wiligsi.plump.server;

import com.wiligsi.plump.PlumpGrpc;
import com.wiligsi.plump.PlumpOuterClass;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

public class PlumpImpl extends PlumpGrpc.PlumpImplBase {

    private static final Logger LOG = Logger.getLogger(PlumpImpl.class.getName());
    final ConcurrentMap<LockName, Lock> locks;

    public PlumpImpl() {
        super();
        locks = new ConcurrentHashMap<>();
    }

    @Override
    public void createLock(PlumpOuterClass.CreateLockRequest request, StreamObserver<PlumpOuterClass.CreateLockReply> responseObserver) {
        try {
            final String newLockName = request.getLockName();
            // We can catch this later, but LockNames are small and Locks are much heavier
            ensureLockDoesNotExist(newLockName);

            final Lock newLock = buildLock(newLockName);
            Lock oldLock = locks.putIfAbsent(newLock.getName(), newLock);
            if (oldLock != null) {
                throw asLockAlreadyExistsException(newLockName);
            }
            responseObserver.onNext(PlumpOuterClass.CreateLockReply.newBuilder().build());
            responseObserver.onCompleted();
        } catch (StatusException statusException) {
            responseObserver.onError(statusException);
        }
    }

    @Override
    public void destroyLock(PlumpOuterClass.DestroyLockRequest request, StreamObserver<PlumpOuterClass.DestroyLockReply> responseObserver) {
        // TODO: make a key on creation/deletion so that only someone with the key can delete the lock
        try {
            final LockName destroyLockName = buildLockName(request.getLockName());
            ensureLockAlreadyExists(destroyLockName);
            locks.remove(destroyLockName);
            responseObserver.onNext(PlumpOuterClass.DestroyLockReply.newBuilder().build());
            responseObserver.onCompleted();
        } catch (StatusException validationException) {
            responseObserver.onError(validationException);
        }
    }

    @Override
    public void acquireSequencer(PlumpOuterClass.SequencerRequest request, StreamObserver<PlumpOuterClass.SequencerReply> responseObserver) {
        final LockName requestLockName;
        try {
            requestLockName = new LockName(request.getLockName());
            ensureLockAlreadyExists(requestLockName);
        } catch (StatusException validationException) {
            responseObserver.onError(validationException);
            return;
        }

        final PlumpOuterClass.Sequencer responseSequencer;
        responseSequencer = locks.get(requestLockName).createSequencer();

        responseObserver.onNext(
                PlumpOuterClass.SequencerReply.newBuilder()
                        .setSequencer(responseSequencer)
                        .build()
        );
        responseObserver.onCompleted();
    }

    @Override
    public void acquireLock(PlumpOuterClass.LockRequest request, StreamObserver<PlumpOuterClass.LockReply> responseObserver) {
        final PlumpOuterClass.Sequencer requestSequencer = request.getSequencer();
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
        final PlumpOuterClass.Sequencer keepAliveSequencer;
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
                PlumpOuterClass.LockReply.newBuilder()
                        .setUpdatedSequencer(keepAliveSequencer)
                        .setSuccess(success)
                        .setKeepAliveInterval(Duration.ofMinutes(2).toMillis())
                        .build()
        );
        responseObserver.onCompleted();
    }

    @Override
    public void keepAlive(PlumpOuterClass.KeepAliveRequest request, StreamObserver<PlumpOuterClass.KeepAliveReply> responseObserver) {
        final PlumpOuterClass.Sequencer requestSequencer = request.getSequencer();
        final LockName requestLockName;
        try {
            requestLockName = buildLockName(requestSequencer.getLockName());
            ensureLockAlreadyExists(requestLockName);
            Lock keepAliveLock = locks.get(requestLockName);
            PlumpOuterClass.Sequencer newSequencer =  keepAliveLock.keepAlive(requestSequencer);
            responseObserver.onNext(
                    PlumpOuterClass.KeepAliveReply.newBuilder()
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
    public void releaseLock(PlumpOuterClass.ReleaseRequest request, StreamObserver<PlumpOuterClass.ReleaseReply> responseObserver) {
        try{
            PlumpOuterClass.Sequencer releaseSequencer = request.getSequencer();
            LockName lockName = buildLockName(releaseSequencer.getLockName());
            Lock releaseLock = locks.get(lockName);
            boolean success = releaseLock.release(releaseSequencer);
            PlumpOuterClass.ReleaseReply.Builder replyBase = PlumpOuterClass.ReleaseReply.newBuilder()
                    .setKeepAliveInterval(Duration.ofMinutes(2).toMillis())
                    .setSuccess(success);

            if (success) {
                responseObserver.onNext(
                        replyBase.build()
                );
            } else {
                PlumpOuterClass.Sequencer updatedSequencer = releaseLock.keepAlive(releaseSequencer);
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
    public void whoHasLock(PlumpOuterClass.WhoHasRequest request, StreamObserver<PlumpOuterClass.WhoHasReply> responseObserver) {
        super.whoHasLock(request, responseObserver);
    }

    @Override
    public void nextSequencer(PlumpOuterClass.NextSequencerRequest request, StreamObserver<PlumpOuterClass.NextSequencerReply> responseObserver) {
        super.nextSequencer(request, responseObserver);
    }

    @Override
    public void listLocks(PlumpOuterClass.ListRequest request, StreamObserver<PlumpOuterClass.ListReply> responseObserver) {
        super.listLocks(request, responseObserver);
    }

    protected void ensureLockAlreadyExists(LockName lockName) throws StatusException {
        if (!locks.containsKey(lockName)) {
            throw Status.NOT_FOUND
                    .withDescription(
                            String.format(
                                    "Lock named '%s' does not exist",
                                    lockName.getDisplayName()
                            )
                    )
                    .asException();
        }
    }

    protected void ensureLockDoesNotExist(String requestLockName) throws StatusException {
        final LockName lockName = buildLockName(requestLockName);
        if (locks.containsKey(lockName)) {
            throw asLockAlreadyExistsException(requestLockName);
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
}
