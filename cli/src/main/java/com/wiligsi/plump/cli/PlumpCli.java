package com.wiligsi.plump.cli;

import com.wiligsi.plump.client.PlumpClient;
import com.wiligsi.plump.common.PlumpOuterClass.CreateLockResponse;
import com.wiligsi.plump.common.PlumpOuterClass.DestroyLockResponse;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "plumpc", version = "plump 1.0",
description = "provides", subcommands={HelpCommand.class}, subcommandsRepeatable = true,
synopsisSubcommandLabel = "COMMAND")
public class PlumpCli {
  private static final String STATE_FILE_NAME = ".plumpc.buf";

  private final CliStateSingleton state;
  private final File stateFile;
  private final ManagedChannel channel;
  private final PlumpClient client;

  @Option(names = {"-u", "--url"}, description = "The URL of the plump server to interact with.")
  private String serverUrl = "localhost:50051";

  @Option(names = {"-s", "--state-file"}, description = "The state file to create or load from.")
  private Optional<String> stateFileLocation = Optional.empty();

  public PlumpCli() throws IOException {
    // Read the state file if it exists, otherwise create a new state object
    if (stateFileLocation.isPresent()) {
      stateFile = Path.of(stateFileLocation.get()).toFile();
    } else {
      stateFile = Path.of(System.getProperty("user.home"), STATE_FILE_NAME).toFile();
    }

    if(stateFile.isFile()) {
      try (FileInputStream dotFileIn = new FileInputStream(stateFile)) {
        state = CliStateSingleton.parseFrom(dotFileIn);
      }
    } else {
      state = new CliStateSingleton();
    }

    this.channel = ManagedChannelBuilder
        .forTarget(serverUrl)
        .usePlaintext()
        .build();

    client = new PlumpClient(channel);
  }

  @Command(name = "version", description = "Displays the version of plumpc")
  void version() {
    System.out.println("plumpc version 1.0");
  }

  @Command(name = "create", description = "Creates a new lock on a Plump server")
  void createLock(
      @Parameters(index = "0", paramLabel="<lockName>", description="new lock name") String lockName
  ) throws InterruptedException, IOException {
      String destroyKey = client.createLock(lockName);
      state.setDeleteToken(serverUrl, lockName, destroyKey);
       saveStateToFile();
  }

  @Command(name = "destroy", description = "Destroy a lock from a Plump server")
  int destroyLock(
      @Parameters(index = "0", paramLabel="<lockName>", description="lock to delete")
          String lockName,
      @Option(names={"-t", "--token"}, description="A passed in delete token for the lock")
          String paramDeleteToken
  ) {
    final String deleteToken;
    if(paramDeleteToken != null) {
      deleteToken = paramDeleteToken;
    } else {
      Optional<String> stateDeleteToken = state.getDeleteToken(serverUrl, lockName);
      if (stateDeleteToken.isEmpty()) {
        System.err.printf("No delete token recorded for lock '%s' on server at '%s'%n",
            lockName, serverUrl);
        return -10;
      }
      deleteToken = stateDeleteToken.get();
    }

    client.destroyLock(lockName, deleteToken);
    return 0;
  }

  @Command(name = "list", description = "List the locks on the server")
  void listLocks() {
    client.listLocks().forEach(System.out::println);
  }

  public void shutdownChannel() throws InterruptedException {
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  private void saveStateToFile() throws IOException {
    try (FileOutputStream stateOutputStream = new FileOutputStream(stateFile)) {
      state.writeTo(stateOutputStream);
    }
  }


  public static void main(String[] args) throws InterruptedException, IOException {
    Optional<PlumpCli> cli = Optional.empty();
    int exitCode = -99;
    try {
      cli = Optional.of(new PlumpCli());
      exitCode = new CommandLine(cli.get()).execute(args);
    } finally {
      if (cli.isPresent()) {
        cli.get().shutdownChannel();
      }
      System.exit(exitCode);
    }
  }
}
