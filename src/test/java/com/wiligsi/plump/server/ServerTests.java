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
import static com.wiligsi.plump.server.matcher.PlumpAssertions.*;

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

        assertThat(testSequencer.getExpiration()).isGreaterThan(effectiveTime.toEpochMilli());
    }

    // Should not lock when lock doesn't exist
    @Test
    public void itShouldNotLockWhenLockDoesNotExist() {
        final String fakeLockName = "fakeLock";

        StatusRuntimeException throwable = catchThrowableOfType(
                () -> {
                    acquireLock(
                            fakeLockName,
                            Sequencer.newBuilder()
                                    .setLockName("fakeLock")
                                    .build()
                    );
                },
                StatusRuntimeException.class
        );
        assertThat(throwable).isLockNameNotFoundExceptionFor(fakeLockName);
    }

    // should lock when sequencer is on top
    @Test
    public void itShouldLockWhenSequencerIsOnTop() {
        createTestLock();
        Sequencer sequencer = acquireTestLockSequencer();
        LockReply lockReply = acquireTestLock(sequencer);

        assertThat(lockReply).hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("success", true);
        assertThat(lockReply.getUpdatedSequencer())
                .hasFieldOrPropertyWithValue("lockName", sequencer.getLockName())
                .hasFieldOrPropertyWithValue("sequenceNumber", sequencer.getSequenceNumber());
        assertThat(lockReply.getUpdatedSequencer().getKey()).isNotEqualTo(sequencer.getKey());
        assertThat(lockReply.getUpdatedSequencer().getExpiration()).isGreaterThan(sequencer.getExpiration());
    }

    // should not lock when sequencer is not head
    @Test
    public void itShouldNotLockWhenSequencerIsNotHead() {
        createTestLock();

        Sequencer firstSequencer = acquireTestLockSequencer();
        Sequencer secondSequencer = acquireTestLockSequencer();

        LockReply failureReply = acquireTestLock(secondSequencer);

        // TODO: make a matcher for sequencers
        assertThat(failureReply).hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("success", false);
        // TODO: assert that getUpdatedSequencer() isUpdatedFrom(sequencer)
        assertThat(failureReply.getUpdatedSequencer())
                .hasFieldOrPropertyWithValue("lockName", secondSequencer.getLockName())
                .hasFieldOrPropertyWithValue("sequenceNumber", secondSequencer.getSequenceNumber());
        assertThat(failureReply.getUpdatedSequencer().getKey()).isNotEqualTo(secondSequencer.getKey());
        assertThat(failureReply.getUpdatedSequencer().getExpiration()).isGreaterThan(secondSequencer.getExpiration());
    }

    // sequencer from another lock shouldn't work
    @Test
    public void itShouldNotAcceptSequencerFromAnotherLock() {
        final String otherLockName = "otherLock";
        createTestLock();
        createLock(otherLockName);

        Sequencer otherLockSequencer = acquireSequencer(otherLockName);

        StatusRuntimeException throwable = catchThrowableOfType(
                () -> {
                    LockReply lockReply = acquireTestLock(otherLockSequencer);
                },
                StatusRuntimeException.class
        );

        assertThat(throwable).isInvalidSequencerExceptionFor(TEST_LOCK_NAME);
    }

    // old sequencer should not work to lock -- requires standalone keepalive endpoint
     @Test
     public void itShouldNotAllowOldSequencerWhenUpdated() {
        createTestLock();
        Sequencer sequencer = acquireTestLockSequencer();
        acquireTestLock(sequencer);

        StatusRuntimeException throwable = catchThrowableOfType(
                () -> {
                    acquireTestLock(sequencer);
                },
                StatusRuntimeException.class
        );

        assertThat(throwable).isInvalidSequencerExceptionFor(TEST_LOCK_NAME);
     }

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

    private LockReply acquireLock(String lockName, Sequencer sequencer) {
        return plumpBlockingStub.acquireLock(
                LockRequest.newBuilder()
                        .setLockName(lockName)
                        .setSequencer(sequencer)
                        .build()
        );
    }

    private LockReply acquireTestLock(Sequencer sequencer) {
        return acquireLock(TEST_LOCK_NAME, sequencer);
    }
}
