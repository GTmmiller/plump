package com.wiligsi.plump.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;

@Command(name = "plumpc", version = "plump 1.0",
description = "provides", subcommands={HelpCommand.class}, subcommandsRepeatable = true,
synopsisSubcommandLabel = "COMMAND")
public class PlumpCli {

  // TODO: have this be parsed at the beginning of any sub-command
  // 17.0 will have this info

  // TODO: Add a simple version command
  @Option(names = {"-u", "--url"}, description = "The URL of the plump server to interact with.")
  private final String serverUrl = "localhost:50051";

  public static void main(String[] args) {
    int exitCode = new CommandLine(new PlumpCli()).execute(args);
    System.exit(exitCode);
  }
}
