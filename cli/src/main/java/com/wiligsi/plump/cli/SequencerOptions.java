package com.wiligsi.plump.cli;

import com.wiligsi.plump.common.PlumpOuterClass.Sequencer;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(footer = "Mixin class for commands that handle sequencers")
public class SequencerOptions {
  @Parameters(index = "0", paramLabel = "<lockName>", description = "Name of the lock to acquire")
  String lockName;

  @Option(names = {"-m", "--manual"}, description = "Add this flag to manually enter a sequencer")
  boolean manual;

  @Option(names = {"-s", "--sequencer-number"}, description = "The sequence number of the sequencer")
  int sequenceNumber;

  @Option(names = {"-k", "--key"}, description = "The delete key for the sequencer")
  String key;

  @Option(names = {"-e", "--expiration"}, description = "The expiration timestamp for the sequencer")
  long expiration;

  public Optional<Sequencer> createManualSequencer() {
    if (manual) {
      return Optional.of(
          Sequencer.newBuilder()
          .setLockName(lockName)
          .setSequenceNumber(sequenceNumber)
          .setKey(key)
          .setExpiration(expiration)
          .build()
      );
    } else {
      return Optional.empty();
    }
  }
}
