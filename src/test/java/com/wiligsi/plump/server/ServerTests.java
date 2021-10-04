package com.wiligsi.plump.server;


import com.wiligsi.plump.PlumpGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
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

public class ServerTests {
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
        // Todo: It might be nice to just have a default create test lock method along with a create lock method that does the boilerplate
        assertThatCode(
                () -> {
                    CreateLockReply reply = plumpBlockingStub.createLock(
                            CreateLockRequest.newBuilder()
                                    .setLockName("TestLock")
                                    .build()
                    );
                }
        ).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DamnLongLockName23232323232323", "sho", "$ymb)l*"})
    public void itShouldRejectMalformedLockNames(String lockName) {
        assertThatThrownBy(
                () -> {
                    CreateLockReply reply = plumpBlockingStub.createLock(
                            CreateLockRequest.newBuilder()
                                    .setLockName(lockName)
                                    .build());
                }
                ).isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("Names should be a series of 4-12 alphanumeric characters")
                .hasFieldOrProperty("status")
                .extracting("status")
                .hasFieldOrPropertyWithValue("code", Status.INVALID_ARGUMENT.getCode());

    }

    // Reject a duplicate lock
    @ParameterizedTest
    @ValueSource(strings = {"lockName", "LoCKNAme", "lockname"})
    public void itShouldRejectDuplicateLockNames(String duplicateName) {
        CreateLockReply reply = plumpBlockingStub.createLock(
                CreateLockRequest.newBuilder()
                        .setLockName("lockName")
                        .build());

        assertThatThrownBy(
                () -> {
                    CreateLockReply duplicateReply = plumpBlockingStub.createLock(
                            CreateLockRequest.newBuilder()
                                    .setLockName(duplicateName)
                                    .build());
                }
        ).isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("Lock named")
                .hasMessageContaining(duplicateName)
                .hasMessageContaining("already exists")
                .hasFieldOrProperty("status")
                .extracting("status")
                .hasFieldOrPropertyWithValue("code", Status.ALREADY_EXISTS.getCode());
    }

    @Test
    public void itShouldNotDeleteANonExistentLock() {
        final String nonExistentName = "fakeLock";
        assertThatThrownBy(
                () -> {
                    DestroyLockReply destroyLockReply = plumpBlockingStub.destroyLock(
                            DestroyLockRequest.newBuilder().setLockName(nonExistentName).build()
                    );
                }
        ).hasMessageContaining("Lock named")
        .hasMessageContaining(nonExistentName)
        .hasMessageContaining("does not exist")
        .hasFieldOrProperty("status")
        .extracting("status")
        .hasFieldOrPropertyWithValue("code", Status.NOT_FOUND.getCode());
    }

    @Test
    public void itShouldBeAbleToDeleteALock() {
        final String lockName = "lockName";
        CreateLockReply lockCreate = plumpBlockingStub.createLock(
          CreateLockRequest.newBuilder()
            .setLockName(lockName)
            .build()
        );

        assertThatCode(
                () -> {
                    DestroyLockReply lockDestroy = plumpBlockingStub.destroyLock(
                            DestroyLockRequest.newBuilder()
                                .setLockName(lockName)
                                .build()
                    );
                }
        ).doesNotThrowAnyException();
    }

    @Test
    public void itShouldNotBeAbleToGetSequencerFromNonExistentLock() {
        final String fakeLockName = "fakeLock";
        // TODO: Can you make malformed requests?
        assertThatThrownBy(
                () -> {
                    // Todo: is there anything we can do to verify the replies? I don't like the warnings all over
                    SequencerReply sequencerReply = plumpBlockingStub.getSequencer(
                            SequencerRequest.newBuilder()
                                    .setLockName(fakeLockName)
                                    .build()
                    );
                }
                // TODO: Common pattern lock name does not exist should be removed to matcher
        ).hasMessageContaining("Lock named")
        .hasMessageContaining(fakeLockName)
        .hasMessageContaining("does not exist")
        .hasFieldOrProperty("status")
        .extracting("status")
        .hasFieldOrPropertyWithValue("code", Status.NOT_FOUND.getCode());
    }

    @Test
    public void itShouldBeAbleToGetSequencerFromLock() {
        final String lockName = "testLock";
        final Instant effectiveTime = Instant.now();
        CreateLockReply reply = plumpBlockingStub.createLock(
                CreateLockRequest
                        .newBuilder()
                        .setLockName(lockName)
                        .build()
        );

        SequencerReply sequencerReply = plumpBlockingStub.getSequencer(
                SequencerRequest.newBuilder()
                        .setLockName(lockName)
                        .build()
        );

        assertThat(sequencerReply.getSequencer()).isNotNull()
                .hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("lockName", lockName)
                .hasFieldOrPropertyWithValue("sequenceNumber", 0);

        assertThat(sequencerReply.getSequencer().getExpiration()).isGreaterThan(effectiveTime.toEpochMilli());
    }



}
