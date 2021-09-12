package com.wiligsi.plump;

import com.google.common.base.Preconditions;
import com.wiligsi.plump.client.PlumpClient;
import com.wiligsi.plump.server.PlumpServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final String USAGE = "usage: ./Plump [server|client clientMethod]";

    public static void main(String[] args) throws IOException, InterruptedException {
        Preconditions.checkArgument(
                args.length > 0,
                "Expected at least 1 argument but received " + args.length + " " + USAGE
        );
        Preconditions.checkArgument(
                args.length < 4,
                "Expected at most 2 arguments but received " + args.length + " " + USAGE
        );

        final String commandArg = args[0].trim();
        Preconditions.checkArgument(
                commandArg.equalsIgnoreCase("server") ||
                        commandArg.equalsIgnoreCase("client"),
                String.format("Unknown argument %s. Expected 'server' or 'client'", commandArg)
        );

        if (commandArg.equalsIgnoreCase("server")) {
            final PlumpServer server = new PlumpServer();
            server.start();
            server.blockUntilShutdown();
        } else if (commandArg.equalsIgnoreCase("client")){
            ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:50051")
                    .usePlaintext()
                    .build();
            try {
                PlumpClient client = new PlumpClient(channel);
                client.createLock("NuLock");
            } finally {
                channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        } else {
            System.out.println("Error, unknown argument " + commandArg);
        }
    }
}
