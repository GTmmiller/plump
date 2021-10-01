package com.wiligsi.plump.server;

import com.wiligsi.plump.PlumpGrpc;
import com.wiligsi.plump.PlumpOuterClass;
import io.grpc.Status;
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
            createName = new LockName(request.getLockName());
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getLocalizedMessage())
                            .withCause(exception)
                            .asException());
            return;
        }

        if (locks.containsKey(createName)) {
            responseObserver.onError(
                    Status.ALREADY_EXISTS
                            .withDescription(
                                    String.format(
                                    "Lock named '%s' already exists",
                                    createName.getDisplayName()
                                    )
                            ).asException()
            );
            return;
        }

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
        super.destroyLock(request, responseObserver);
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
}
