package com.wiligsi.plump.server;


import com.wiligsi.plump.PlumpGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.wiligsi.plump.PlumpOuterClass.*;
import static org.assertj.core.api.Assertions.*;
import static com.wiligsi.plump.server.assertion.PlumpAssertions.*;

public class ServerTests {
    private static final String TEST_LOCK_NAME = "testLock";

    private Server plumpServer;
    private ManagedChannel plumpChannel;
    private PlumpGrpc.PlumpBlockingStub plumpBlockingStub;

    @BeforeEach
    public void createInProcessServerClient() throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        plumpServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new PlumpImpl())
                .build();
        plumpServer.start();

        plumpChannel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        plumpBlockingStub = PlumpGrpc.newBlockingStub(
                plumpChannel
        );

    }

    @AfterEach
    public void terminateInProcessServerClient() throws InterruptedException {
        plumpChannel.shutdown();
        plumpServer.shutdown();

        try {
            assert plumpChannel.awaitTermination(5, TimeUnit.SECONDS) : "Channel could not be shutdown in reasonable time";
            assert plumpServer.awaitTermination(5, TimeUnit.SECONDS) : "Server could not be shutdown in reasonable time";
        } finally {
            plumpChannel.shutdownNow();
            plumpServer.shutdownNow();
        }
    }

    @Test
    public void itShouldBePossibleToCreateALock() {
        assertThatCode(
                this::createTestLock
        ).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DamnLongLockName23232323232323", "sho", "$ymb)l*"})
    public void itShouldRejectMalformedLockNames(String lockName) {
        StatusRuntimeException throwable = catchThrowableOfType(
                () -> createLock(lockName),
                StatusRuntimeException.class
        );
        // Todo: Should I test the part of the message that has the lock name?
        assertThat(throwable).isInvalidLockNameException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"testLock", "TeSTLOck", "testlock"})
    public void itShouldRejectDuplicateLockNames(String duplicateName) {
        createTestLock();

        StatusRuntimeException throwable = catchThrowableOfType(
                () -> createLock(duplicateName),
                StatusRuntimeException.class
        );

        assertThat(throwable).isLockNameAlreadyExistsExceptionFor(duplicateName);
    }

    @Test
    public void itShouldNotDeleteANonExistentLock() {
        final String nonExistentName = "fakeLock";

        StatusRuntimeException throwable = catchThrowableOfType(
                () -> destroyLock(nonExistentName),
                StatusRuntimeException.class
        );

        assertThat(throwable).isLockNameNotFoundExceptionFor(nonExistentName);
    }

    @Test
    public void itShouldBeAbleToDeleteALock() {
        createTestLock();

        assertThatCode(
                this::destroyTestLock
        ).doesNotThrowAnyException();
    }

    @Test
    public void itShouldNotBeAbleToGetSequencerFromNonExistentLock() {
        final String fakeLockName = "fakeLock";
        // TODO: Can you make malformed requests?
        StatusRuntimeException throwable = catchThrowableOfType(
                () -> {
                    Sequencer fakeSequencer = acquireSequencer(fakeLockName);
                    assertThat(fakeSequencer)
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("lockName", fakeLockName);
                },
                StatusRuntimeException.class
        );

        assertThat(throwable).isLockNameNotFoundExceptionFor(fakeLockName);
    }

    @Test
    public void itShouldBeAbleToGetSequencerFromLock() {
        final Instant effectiveTime = Instant.now();
        createTestLock();

        final Sequencer testSequencer = acquireTestLockSequencer();

        assertThat(testSequencer)
                .hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("lockName", TEST_LOCK_NAME)
                .hasFieldOrPropertyWithValue("sequenceNumber", 0);

        assertThat(testSequencer.getExpiration()).isGreaterThanOrEqualTo(effectiveTime.toEpochMilli());
    }

    // Should not lock when lock doesn't exist
    @Test
    public void itShouldNotLockWhenLockDoesNotExist() {
        final String fakeLockName = "fakeLock";

        StatusRuntimeException throwable = catchThrowableOfType(
                () -> acquireLock(
                        Sequencer.newBuilder()
                                .setLockName("fakeLock")
                                .build()
                ),
                StatusRuntimeException.class
        );
        assertThat(throwable).isLockNameNotFoundExceptionFor(fakeLockName);
        // TODO: provide protection against partial sequencers
    }

    // should lock when sequencer is on top
    @Test
    public void itShouldLockWhenSequencerIsOnTop() {
        createTestLock();
        Sequencer sequencer = acquireTestLockSequencer();
        LockReply lockReply = acquireLock(sequencer);

        assertThat(lockReply).hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("success", true);
        assertThat(lockReply.getUpdatedSequencer())
                .hasFieldOrPropertyWithValue("lockName", sequencer.getLockName())
                .hasFieldOrPropertyWithValue("sequenceNumber", sequencer.getSequenceNumber());
        assertThat(lockReply.getUpdatedSequencer().getKey()).isNotEqualTo(sequencer.getKey());
        assertThat(lockReply.getUpdatedSequencer().getExpiration()).isGreaterThanOrEqualTo(sequencer.getExpiration());
    }

    // should not lock when sequencer is not head
    @Test
    public void itShouldNotLockWhenSequencerIsNotHead() {
        createTestLock();

        Sequencer firstSequencer = acquireTestLockSequencer();
        Sequencer secondSequencer = acquireTestLockSequencer();

        LockReply failureReply = acquireLock(secondSequencer);

        assertThat(failureReply).hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("success", false);
        assertThat(failureReply.getUpdatedSequencer()).isUpdatedFrom(secondSequencer);
    }

     @Test
     public void itShouldNotAllowOldSequencerWhenUpdated() {
        createTestLock();
        Sequencer sequencer = acquireTestLockSequencer();
        acquireLock(sequencer);

        StatusRuntimeException throwable = catchThrowableOfType(
                () -> acquireLock(sequencer),
                StatusRuntimeException.class
        );

        assertThat(throwable).isInvalidSequencerExceptionFor(TEST_LOCK_NAME);
     }

     // It should keep alive a non-expired sequencer
    @Test
    public void itShouldKeepAliveSequencer() {
        createTestLock();
        Sequencer testLockSequencer = acquireTestLockSequencer();
        Sequencer keepAliveSequencer = keepAliveSequencer(testLockSequencer);

        assertThat(keepAliveSequencer).isUpdatedFrom(testLockSequencer);
    }
    // It Should not keep alive a non-expired sequencer
    // It should not keep alive an invalid sequencer
    @Test public void itShouldNotKeepAliveInvalidSequencer() {
        createTestLock();
        Sequencer testLockSequencer = acquireTestLockSequencer();
        Sequencer badSequencer = Sequencer.newBuilder(testLockSequencer)
                .setKey("badKey")
                .build();

        StatusRuntimeException throwable = catchThrowableOfType(
                () -> keepAliveSequencer(badSequencer),
                StatusRuntimeException.class
        );

        assertThat(throwable).isInvalidSequencerExceptionFor(TEST_LOCK_NAME);
    }

    // can lock and unlock a lock
    @Test
    public void itShouldBeAbleToReleaseALock() {
        createTestLock();
        Sequencer sequencer = acquireTestLockSequencer();
        LockReply reply = acquireLock(sequencer);
        Sequencer lockSequencer = reply.getUpdatedSequencer();
        ReleaseReply releaseReply = releaseLock(lockSequencer);

        assertThat(lockSequencer).isUpdatedFrom(sequencer);
        assertThat(releaseReply)
                .hasFieldOrPropertyWithValue("success", true);
    }

    // lock can't be unlocked by someone else
    @Test
    public void itShouldNotBeAbleToReleaseLockWithOtherSequencer() {
        createTestLock();
        Sequencer lockSequencer = acquireTestLockSequencer();
        Sequencer badUnlockSequencer = acquireTestLockSequencer();

        acquireLock(lockSequencer);
        ReleaseReply releaseReply = releaseLock(badUnlockSequencer);

        assertThat(releaseReply).hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("success", false);
        assertThat(releaseReply.getUpdatedSequencer()).isUpdatedFrom(badUnlockSequencer);
    }

    // invalid sequencer can't unlock
    @Test
    public void itShouldNotBeReleasedByAnInvalidSequencer() {
        createTestLock();
        Sequencer sequencer = acquireTestLockSequencer();
        acquireLock(sequencer);

        StatusRuntimeException throwable = catchThrowableOfType(
                () -> releaseLock(sequencer),
                StatusRuntimeException.class
        );

        assertThat(throwable).isInvalidSequencerExceptionFor(TEST_LOCK_NAME);
    }


    // Timeout unlocks? -> lock implementation
    // TODO: Consider mocks for the timeout implementations to pass in a clock spy object

     // Helper Methods

    private void createLock(String lockName) {
        CreateLockReply createLockReply = plumpBlockingStub.createLock(
                CreateLockRequest
                        .newBuilder()
                        .setLockName(lockName)
                        .build()
        );
        assertThat(createLockReply.isInitialized()).isTrue();
    }

    private void createTestLock() {
        createLock(TEST_LOCK_NAME);
    }

    private void destroyLock(String lockName) {
        DestroyLockReply destroyLockReply = plumpBlockingStub.destroyLock(
                DestroyLockRequest.newBuilder()
                        .setLockName(lockName)
                        .build()
        );
        assertThat(destroyLockReply.isInitialized()).isTrue();
    }

    private void destroyTestLock() {
        destroyLock(TEST_LOCK_NAME);
    }

    private Sequencer acquireSequencer(String lockName) {
        SequencerReply sequencerReply = plumpBlockingStub.acquireSequencer(
                SequencerRequest.newBuilder()
                        .setLockName(lockName)
                        .build()
        );
        return sequencerReply.getSequencer();
    }

    private Sequencer acquireTestLockSequencer() {
        return acquireSequencer(TEST_LOCK_NAME);
    }

    private LockReply acquireLock(Sequencer sequencer) {
        return plumpBlockingStub.acquireLock(
                LockRequest.newBuilder()
                        .setSequencer(sequencer)
                        .build()
        );
    }

    // TODO: Actually implement a keep alive interval. Would be a bigger thing with a config object probably.
    // TODO: make some actual timeout ones? I guess it's fine since we did it for the lock itself
    private Sequencer keepAliveSequencer(Sequencer sequencer) {
        return plumpBlockingStub.keepAlive(
                KeepAliveRequest.newBuilder()
                        .setSequencer(sequencer)
                        .build()
        ).getUpdatedSequencer();
    }

    private ReleaseReply releaseLock(Sequencer sequencer) {
        return plumpBlockingStub.releaseLock(
                ReleaseRequest.newBuilder()
                        .setSequencer(sequencer)
                        .build()
        );
    }
}
