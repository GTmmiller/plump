package com.wiligsi.plump.cli;

import static com.wiligsi.plump.common.PlumpOuterClass.Sequencer;

import com.wiligsi.plump.cli.CliSerialize.CliStateData;
import com.wiligsi.plump.cli.CliSerialize.CliStateData.HostStateData;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This class is a singleton object that represents locally stored state for the cli.
 *
 * <p>
 * This object tracks the deletion keys and information for sequencers and locks. It serializes to
 * files via protocol buffer classes.
 * </p>
 *
 * @author Steven Miller
 */
public class CliStateSingleton {

  private final Map<String, HostState> hostStateMap;

  public CliStateSingleton() {
    hostStateMap = new HashMap<>();
  }

  private CliStateSingleton(CliStateData cliStateData) {
    hostStateMap = cliStateData.getHostDataMap()
        .entrySet()
        .stream()
        .collect(
            Collectors.toMap(
                Entry::getKey,
                e -> new HostState(e.getValue())
            )
        );
  }

  public static CliStateSingleton parseFrom(InputStream cliStateDataStream) throws IOException {
    CliStateData cliStateDate = CliStateData.parseFrom(cliStateDataStream);
    return new CliStateSingleton(cliStateDate);
  }

  public void writeTo(OutputStream cliStateFileStream) throws IOException {
    CliStateData writeData = this.toCliStateData();
    writeData.writeTo(cliStateFileStream);
  }

  public Optional<String> getDeleteToken(String url, String lockName) {
    return getHostState(url).getDeleteToken(lockName);
  }

  public void setDeleteToken(String url, String lockName, String deleteToken) {
    getHostState(url).setDeleteToken(lockName, deleteToken);
  }

  public Optional<String> removeDeleteToken(String url, String lockName) {
    return getHostState(url).removeDeleteToken(lockName);
  }

  public Optional<Sequencer> getLockSequencer(String url, String lockName) {
    return getHostState(url).getLockSequencer(lockName);
  }

  public void setLockSequencer(String url, String lockName, Sequencer sequencer) {
    getHostState(url).setLockSequencer(lockName, sequencer);
  }

  public Optional<Sequencer> removeLockSequencer(String url, String lockName) {
    return getHostState(url).removeLockSequencer(lockName);
  }

  private CliStateData toCliStateData() {
    return CliStateData.newBuilder()
        .putAllHostData(
            hostStateMap.entrySet()
                .stream()
                .collect(
                    Collectors.toUnmodifiableMap(
                        Entry::getKey,
                        e -> e.getValue().toHostStateData()
                    ))
        )
        .build();
  }

  private HostState getHostState(String url) {
    hostStateMap.putIfAbsent(url, new HostState(url));
    return hostStateMap.get(url);
  }

  private static class HostState {

    private final String url;
    private final Map<String, String> lockDeleteTokens;
    private final Map<String, Sequencer> lockSequencers;

    public HostState(String url) {
      this.url = url;
      lockDeleteTokens = new HashMap<>();
      lockSequencers = new HashMap<>();
    }

    private HostState(HostStateData hostStateData) {
      url = hostStateData.getUrl();
      lockDeleteTokens = new HashMap<>(hostStateData.getLockDeleteTokensMap());
      lockSequencers = new HashMap<>(hostStateData.getLockSequencersMap());
    }

    public Optional<String> getDeleteToken(String lockName) {
      return Optional.ofNullable(lockDeleteTokens.get(lockName));
    }

    public void setDeleteToken(String lockName, String deleteToken) {
      lockDeleteTokens.put(lockName, deleteToken);
    }

    public Optional<String> removeDeleteToken(String lockName) {
      return Optional.ofNullable(lockDeleteTokens.remove(lockName));
    }

    public Optional<Sequencer> getLockSequencer(String lockName) {
      return Optional.ofNullable(lockSequencers.get(lockName));
    }

    public void setLockSequencer(String lockName, Sequencer sequencer) {
      lockSequencers.put(lockName, sequencer);
    }

    public Optional<Sequencer> removeLockSequencer(String lockName) {
      return Optional.ofNullable(lockSequencers.remove(lockName));
    }

    public String getUrl() {
      return url;
    }

    public HostStateData toHostStateData() {
      return HostStateData.newBuilder()
          .setUrl(url)
          .putAllLockDeleteTokens(lockDeleteTokens)
          .putAllLockSequencers(lockSequencers)
          .build();
    }
  }
}
