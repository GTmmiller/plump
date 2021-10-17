package com.wiligsi.plump.server.assertion;

import org.assertj.core.api.AbstractObjectAssert;

import static com.wiligsi.plump.PlumpOuterClass.*;
import static org.assertj.core.api.Assertions.assertThat;

public class SequencerAssert extends AbstractObjectAssert<SequencerAssert, Sequencer> {
    public SequencerAssert(Sequencer sequencer) {
        super(sequencer, SequencerAssert.class);
    }

    public SequencerAssert isUpdatedFrom(Sequencer original){
        hasFieldOrPropertyWithValue("lockName", original.getLockName());
        hasFieldOrPropertyWithValue("sequenceNumber", original.getSequenceNumber());
        extracting("key").isNotEqualTo(original.getKey());
        assertThat(actual.getExpiration()).isGreaterThanOrEqualTo(original.getExpiration());

        return this;
    }
}
