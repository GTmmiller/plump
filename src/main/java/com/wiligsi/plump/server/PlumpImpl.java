package com.wiligsi.plump.server;

import com.wiligsi.plump.PlumpGrpc;
import com.wiligsi.plump.PlumpOuterClass;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;

import java.security.NoSuchAlgorithmException;
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
        } catch (StatusException exception) {
            responseObserver.onError(exception);
            return;
        }

        // ToDo: Better exception variable names

        final Lock createLock;
        try {
           createLock = new Lock(createName.getDisplayName());
        } catch (NoSuchAlgorithmException exception) {
            responseObserver.onError(exception);
            return;
        }

        locks.put(createName, createLock);
        responseObserver.onNext(PlumpOuterClass.CreateLockReply.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void destroyLock(PlumpOuterClass.DestroyLockRequest request, StreamObserver<PlumpOuterClass.DestroyLockReply> responseObserver) {
        // TODO: make a key on creation/deletion so that only someone with the key can delete the lock
        // validate the lock name
        // if the lock doesn't exist then throw an error
        // if the lock does exist then delete it and return successfully
        final LockName destroyLockName;
        try {
            destroyLockName = validateLockName(request.getLockName());
            ensureLockAlreadyExists(destroyLockName);
        } catch (StatusException exception) {
            responseObserver.onError(exception);
            return;
        }

        locks.remove(destroyLockName);
        responseObserver.onNext(PlumpOuterClass.DestroyLockReply.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void getSequencer(PlumpOuterClass.SequencerRequest request, StreamObserver<PlumpOuterClass.SequencerReply> responseObserver) {
        super.getSequencer(request, responseObserver);
    }

    @Override
    public void getLock(PlumpOuterClass.LockRequest request, StreamObserver<PlumpOuterClass.LockReply> responseObserver) {
        super.getLock(request, responseObserver);
    }

    @Override
    public void lockKeepAlive(PlumpOuterClass.KeepAliveRequest request, StreamObserver<PlumpOuterClass.KeepAliveReply> responseObserver) {
        super.lockKeepAlive(request, responseObserver);
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
