package com.wiligsi.plump.server;

import com.wiligsi.plump.PlumpOuterClass.Sequencer;

public class InvalidSequencerException extends Exception {
    public InvalidSequencerException(Sequencer invalid) {
        super(
                String.format("Provided sequencer for lock '%s' is invalid", invalid.getLockName())
        );
    }
}
