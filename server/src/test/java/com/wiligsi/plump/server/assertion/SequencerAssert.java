package com.wiligsi.plump.server.assertion;

import static com.wiligsi.plump.common.PlumpOuterClass.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.Assertions;

public class SequencerAssert extends AbstractObjectAssert<SequencerAssert, Sequencer> {

  public SequencerAssert(Sequencer sequencer) {
    super(sequencer, SequencerAssert.class);
  }

  public SequencerAssert isUpdatedFrom(Sequencer original) {
    hasFieldOrPropertyWithValue("lockName", original.getLockName());
    hasFieldOrPropertyWithValue("sequenceNumber", original.getSequenceNumber());
    extracting("key").isNotEqualTo(original.getKey());
    Assertions.assertThat(actual.getExpiration()).isGreaterThanOrEqualTo(original.getExpiration());

    return this;
  }
}
