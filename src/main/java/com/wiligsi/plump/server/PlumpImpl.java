package com.wiligsi.plump.server;

import com.wiligsi.plump.PlumpGrpc;
import com.wiligsi.plump.PlumpOuterClass;
import io.grpc.stub.StreamObserver;

public class PlumpImpl extends PlumpGrpc.PlumpImplBase {


    @Override
    public void createLock(PlumpOuterClass.CreateLockRequest request, StreamObserver<PlumpOuterClass.CreateLockReply> responseObserver) {
        super.createLock(request, responseObserver);
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
