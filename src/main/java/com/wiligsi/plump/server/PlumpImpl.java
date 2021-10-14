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
        final LockName createName;
        try {
            createName = validateLockName(request.getLockName());
            ensureLockDoesNotExist(createName);
        } catch (StatusException validationException) {
            responseObserver.onError(validationException);
            return;
        }

        // ToDo: Better exception variable names

        final Lock createLock;
        try {
           createLock = new Lock(createName.getDisplayName());
        } catch (NoSuchAlgorithmException algorithmException) {
            responseObserver.onError(algorithmException);
            return;
        }

        locks.put(createName, createLock);
        responseObserver.onNext(PlumpOuterClass.CreateLockReply.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void destroyLock(PlumpOuterClass.DestroyLockRequest request, StreamObserver<PlumpOuterClass.DestroyLockReply> responseObserver) {
        // TODO: make a key on creation/deletion so that only someone with the key can delete the lock
        final LockName destroyLockName;
        try {
            destroyLockName = validateLockName(request.getLockName());
            ensureLockAlreadyExists(destroyLockName);
        } catch (StatusException validationException) {
            responseObserver.onError(validationException);
            return;
        }

        locks.remove(destroyLockName);
        responseObserver.onNext(PlumpOuterClass.DestroyLockReply.newBuilder().build());
        responseObserver.onCompleted();
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
        try {
            responseSequencer = locks.get(requestLockName).createSequencer();
        } catch (NoSuchAlgorithmException algorithmException) {
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(algorithmException.getMessage())
                            .withCause(algorithmException.getCause())
                            .asException()
            );
            return;
        }

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
        } catch (NoSuchAlgorithmException exception) {
            responseObserver.onError(
                    Status.INTERNAL
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
            requestLockName = validateLockName(requestSequencer.getLockName());
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
        } catch (NoSuchAlgorithmException exception) {
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(exception.getMessage())
                            .withCause(exception)
                            .asException()
            );
        }

        // TODO: or, combine all of the try/catch blocks and catch everything

    }

    @Override
    public void releaseLock(PlumpOuterClass.ReleaseRequest request, StreamObserver<PlumpOuterClass.ReleaseReply> responseObserver) {
        super.releaseLock(request, responseObserver);
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

    protected LockName validateLockName(String lockName) throws StatusException {
        try {
            return new LockName(lockName);
        } catch (IllegalArgumentException exception) {
            throw Status.INVALID_ARGUMENT
                            .withDescription(exception.getLocalizedMessage())
                            .withCause(exception)
                            .asException();
        }
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

    protected void ensureLockDoesNotExist(LockName lockName) throws StatusException {
        if (locks.containsKey(lockName)) {
            throw Status.ALREADY_EXISTS
                    .withDescription(
                            String.format(
                                    "Lock named '%s' already exists",
                                    lockName.getDisplayName()
                            )
                    ).asException();
        }
    }
}
