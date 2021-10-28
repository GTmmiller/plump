package com.wiligsi.plump.server;

import com.wiligsi.plump.PlumpGrpc;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
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
            final LockName newLockName = buildLockName(request.getLockName());
            if (locks.containsKey(newLockName)) {
                throw asLockAlreadyExistsException(newLockName);
            }

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
            ensureLockExists(destroyLockName);
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
            ensureLockExists(requestLockName);
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
        try {
            final Sequencer requestSequencer = request.getSequencer();
            final LockName requestLockName = buildLockName(requestSequencer.getLockName());
            ensureLockExists(requestLockName);
            final Lock acquireLock = safeGetLock(requestLockName);
            final Sequencer keepAliveSequencer = acquireLock.keepAlive(requestSequencer);
            final boolean success = acquireLock.acquire(keepAliveSequencer);

            responseObserver.onNext(
                    LockReply.newBuilder()
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
    public void keepAlive(KeepAliveRequest request, StreamObserver<KeepAliveReply> responseObserver) {
        try {
            final Sequencer requestSequencer = request.getSequencer();
            final LockName requestLockName = buildLockName(requestSequencer.getLockName());
            ensureLockExists(requestLockName);

            final Lock keepAliveLock = safeGetLock(requestLockName);
            Sequencer newSequencer =  keepAliveLock.keepAlive(requestSequencer);
            responseObserver.onNext(
                    KeepAliveReply.newBuilder()
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
    public void releaseLock(ReleaseRequest request, StreamObserver<ReleaseReply> responseObserver) {
        try{
            Sequencer releaseSequencer = request.getSequencer();
            LockName lockName = buildLockName(releaseSequencer.getLockName());
            ensureLockExists(lockName);

            Lock releaseLock = safeGetLock(lockName);
            boolean success = releaseLock.release(releaseSequencer);
            ReleaseReply.Builder replyBase = ReleaseReply.newBuilder()
                    .setKeepAliveInterval(releaseLock.getKeepAliveInterval().toMillis())
                    .setSuccess(success);

            if (success) {
                responseObserver.onNext(
                        replyBase.build()
                );
            } else {
                Sequencer updatedSequencer = releaseLock.keepAlive(releaseSequencer);
                responseObserver.onNext(
                        replyBase.setUpdatedSequencer(updatedSequencer).build()
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
    public void whoHasLock(WhoHasRequest request, StreamObserver<WhoHasReply> responseObserver) {
        try {
            final LockName requestName = buildLockName(request.getLockName());
            ensureLockExists(requestName);
            final Lock requestLock = safeGetLock(requestName);
            final LockState state = requestLock.getState();
            final Optional<Integer> headSequencerNumber = requestLock.getHeadSequencerNumber();

            final WhoHasReply whoHasReply;
            if (state == LockState.LOCKED && headSequencerNumber.isPresent()) {
                whoHasReply = WhoHasReply.newBuilder()
                        .setLocked(true)
                        .setSequenceNumber(headSequencerNumber.get())
                        .build();
            } else {
                whoHasReply = WhoHasReply.newBuilder()
                        .setLocked(false)
                        .build();
            }
            responseObserver.onNext(whoHasReply);
            responseObserver.onCompleted();
        } catch (StatusException statusException) {
            responseObserver.onError(statusException);
        }
    }

    @Override
    public void nextSequencer(NextSequencerRequest request, StreamObserver<NextSequencerReply> responseObserver) {
        try {
            final LockName requestName = buildLockName(request.getLockName());
            ensureLockExists(requestName);
            final Lock requestLock = safeGetLock(requestName);
            final int nextSequenceNumber = requestLock.getNextSequenceNumber();

            responseObserver.onNext(
                    NextSequencerReply.newBuilder()
                            .setSequenceNumber(nextSequenceNumber)
                            .build()
            );
            responseObserver.onCompleted();
        } catch (StatusException statusException) {
            responseObserver.onError(statusException);
        }

        super.nextSequencer(request, responseObserver);
    }

    @Override
    public void listLocks(ListRequest request, StreamObserver<ListReply> responseObserver) {
        super.listLocks(request, responseObserver);
    }

    protected void ensureLockExists(LockName lockName) throws StatusException {
        if (!locks.containsKey(lockName)) {
            throw asLockDoesNotExistException(lockName);
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
        Lock getLock =  locks.get(lockName);
        if (getLock == null) {
            throw asLockDoesNotExistException(lockName);
        }
        return getLock;
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
