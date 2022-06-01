package com.wiligsi.plump.client;

import java.util.function.Consumer;

public class PlumpCommand {

  private final String commandName;
  private final Consumer<String[]> command;
  private final int minParams;
  private final int maxParams;

  public PlumpCommand(String commandName, Consumer<String[]> command, int minParams, int maxParams) {
    this.commandName = commandName;
    this.command = command;
    this.minParams = minParams;
    this.maxParams = maxParams;
  }

  public void execute(String[] args) {
    if (args.length < minParams) {
      throw new IllegalArgumentException(
          String.format(
              "Not enough parameters passed for command '%s'. Expected at least: %d Received: %d",
              commandName,
              minParams,
              args.length
          )
      );
    }

    // TODO: consider a max value or codify it or something 
    if (args.length > maxParams) {
      throw new IllegalArgumentException(
          String.format(
              "Too many parameters passed for command '%s'. Expected at most: %d Received: %d",
              commandName,
              maxParams,
              args.length
          )
      );
    }

    // TODO: could return a result string/message

  }


}
