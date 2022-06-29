package com.wiligsi.plump.server;

import com.wiligsi.plump.server.lock.Lock;
import com.wiligsi.plump.server.lock.LockName;
import com.wiligsi.plump.server.lock.PlumpLock;
import com.wiligsi.plump.server.lock.SlimLock;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The entry point for the Plump Lock Service Server. This will run a server until it crashes or is
 * interrupted. It can be passed a port number if the default port is unacceptable.
 *
 * @author Steven Miller
 */

@Command(name = "plump", version = "plump 1.0",
    description = "runs a plump server locally given a port and optionally a lock type",
    mixinStandardHelpOptions = true,
    synopsisSubcommandLabel = "COMMAND")
public class Main implements Callable<Integer> {

  private static final Map<String, Function<LockName, Lock>> CLI_LOCKS = Map.of(
      "plump", PlumpLock::new,
      "slim", SlimLock::new
  );

  @Option(names = {"-p", "--port"}, description = "The port number the server should run on" )
  protected int serverPort = PlumpServer.DEFAULT_PORT;

  @Option(names = {"-l", "--lock-type"}, description = "The Lock type to use with the server")
  protected String lockType = "plump";

  @Override
  public Integer call() throws IOException, NoSuchAlgorithmException, InterruptedException {
    final Optional<Function<LockName, Lock>> lockCreator = Optional.ofNullable(
        CLI_LOCKS.get(lockType.toLowerCase(Locale.ROOT))
    );

    if (lockCreator.isEmpty()) {
      System.err.printf("Lock type '%s' is not available.%n"
          + "The following lock types are available: '%s'%n",
          lockType, CLI_LOCKS.keySet()
      );
      return -1;
    }

    System.out.printf(
        "Starting server on port '%d' using lock type '%s'%n",
        serverPort, lockType.toLowerCase(Locale.ROOT)
    );
    final PlumpServer server = new PlumpServer(serverPort, lockCreator.get());

    server.start();
    server.blockUntilShutdown();

    return 0;
  }

  /**
   * The entry point for running the server. A port number can be passed in to change the port if
   * desired. The lock type can also be changed as well.
   *
   * @param args - the command line arguments passed to the server. Expects 0-1 arguments.
   */
  public static void main(String[] args) {
    final int code = new CommandLine(new Main()).execute(args);
    System.exit(code);
  }
}
