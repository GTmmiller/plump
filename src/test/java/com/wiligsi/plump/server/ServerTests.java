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

        assertThat(throwable).isLockNameAlreadyExistsException(duplicateName);
    }

    @Test
    public void itShouldNotDeleteANonExistentLock() {
        final String nonExistentName = "fakeLock";

        StatusRuntimeException throwable = catchThrowableOfType(
                () -> destroyLock(nonExistentName),
                StatusRuntimeException.class
        );

        assertThat(throwable).isLockNameNotFoundException(nonExistentName);
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
                    Sequencer fakeSequencer = getSequencer(fakeLockName);
                    assertThat(fakeSequencer)
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("lockName", fakeLockName);
                },
                StatusRuntimeException.class
        );

        assertThat(throwable).isLockNameNotFoundException(fakeLockName);
    }

    @Test
    public void itShouldBeAbleToGetSequencerFromLock() {
        final Instant effectiveTime = Instant.now();
        createTestLock();

        final Sequencer testSequencer = getTestLockSequencer();

        assertThat(testSequencer)
                .hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("lockName", TEST_LOCK_NAME)
                .hasFieldOrPropertyWithValue("sequenceNumber", 0);

        assertThat(testSequencer.getExpiration()).isGreaterThan(effectiveTime.toEpochMilli());
    }

    // should not be able to lock non-existent lock

    // should lock when sequencer is on top

    // should not lock when sequencer is not head

    // sequencer from another lock shouldn't work

    // locking should keepalive on success

    // locking should keepalive on failure

    // old sequencer should not work to lock -- requires standalone keepalive endpoint

    // TODO: is it worth it to test the trickier clock-based lock scenarios here? I have a test in locks and I feel it wouldn't be worth it

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

    private Sequencer getSequencer(String lockName) {
        SequencerReply sequencerReply = plumpBlockingStub.acquireSequencer(
                SequencerRequest.newBuilder()
                        .setLockName(lockName)
                        .build()
        );
        return sequencerReply.getSequencer();
    }

    private Sequencer getTestLockSequencer() {
        return getSequencer(TEST_LOCK_NAME);
    }
}
