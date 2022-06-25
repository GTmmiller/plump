package com.wiligsi.plump.cli;

import com.wiligsi.plump.client.PlumpClient;
import com.wiligsi.plump.common.PlumpOuterClass.LockResponse;
import com.wiligsi.plump.common.PlumpOuterClass.Sequencer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Mixin;
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

  @Command(name = "acquire", description = "Acquire a sequencer from a lock")
  void acquireSequencer(
      @Parameters(index = "0", paramLabel = "<lockName>", description = "Name of the locks to acquire a sequencer from")
      String lockName
  ) throws IOException {
    Sequencer sequencer = client.acquireSequencer(lockName);
    state.setLockSequencer(serverUrl, lockName, sequencer);
    System.out.printf(
        "Acquired sequencer %s for lock %s%n",
        sequencer.getSequenceNumber(),
        sequencer.getLockName());

    saveStateToFile();
  }

  @Command(name = "lock", description = "Acquire a lock using a sequencer")
  int acquireLock(
      @Mixin SequencerOptions options
  ) throws IOException {
    final Sequencer lockSequencer;

    final Optional<Sequencer> manualSequencer = options.createManualSequencer();
    if(manualSequencer.isPresent()) {
      lockSequencer = manualSequencer.get();
    } else {
      Optional<Sequencer> stateSequencer = state.getLockSequencer(serverUrl, options.lockName);

      if (stateSequencer.isPresent()) {
        lockSequencer = stateSequencer.get();
      } else {
        System.err.printf("No sequencer recorded for lock '%s' on server at '%s'%n",
            options.lockName, serverUrl);
        System.err.println("Try using the -m option to manually insert sequencer details");
        return -11;
      }
    }

    System.out.printf(
        "Attempting to acquire lock '%s' on server at '%s' with the following sequencer:%n '%s'%n",
        options.lockName, serverUrl, lockSequencer
    );

    LockResponse lockResponse = client.acquireLock(lockSequencer);

    // Success report
    System.out.printf(
        "Lock '%s' on server at '%s' ",
        options.lockName, serverUrl
    );
    if (lockResponse.getSuccess()) {
      System.out.println("was successfully acquired!");
    } else {
      System.out.println("is locked by another user.");
    }

    // Sequencer update from keepalive
    if (options.manual) {
      System.out.printf("Your sequencer was renewed, here is your new sequencer:%n %s%n", lockResponse.getUpdatedSequencer());
    } else {
      state.setLockSequencer(serverUrl, options.lockName, lockResponse.getUpdatedSequencer());
      saveStateToFile();
    }
    return 0;
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
