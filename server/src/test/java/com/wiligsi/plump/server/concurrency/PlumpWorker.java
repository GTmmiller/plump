package com.wiligsi.plump.server.concurrency;

import com.wiligsi.plump.common.PlumpGrpc.PlumpBlockingStub;
import com.wiligsi.plump.common.PlumpOuterClass.LockRequest;
import com.wiligsi.plump.common.PlumpOuterClass.LockResponse;
import com.wiligsi.plump.common.PlumpOuterClass.ReleaseRequest;
import com.wiligsi.plump.common.PlumpOuterClass.ReleaseResponse;
import com.wiligsi.plump.common.PlumpOuterClass.Sequencer;
import com.wiligsi.plump.common.PlumpOuterClass.SequencerRequest;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public class PlumpWorker implements Runnable {

  public static String COUNT = "";

  private static final Logger LOG = Logger.getLogger(PlumpWorker.class.getName());

  private final CountDownLatch startSignal;
  private final CountDownLatch endSignal;
  private final PlumpBlockingStub plumpBlockingStub;
  private final String lockName;

  public PlumpWorker(
      CountDownLatch startSignal,
      CountDownLatch endSignal,
      PlumpBlockingStub plumpBlockingStub,
      String lockName
  ) {
    this.startSignal = startSignal;
    this.endSignal = endSignal;
    this.plumpBlockingStub = plumpBlockingStub;
    this.lockName = lockName;
  }

  public void run() {
    try {
      LOG.info("Waiting to start");
      startSignal.await();

      Sequencer sequencer = plumpBlockingStub.acquireSequencer(
          SequencerRequest.newBuilder().setLockName(lockName).build()
      ).getSequencer();

      LOG.info("Acquired sequencer: " + sequencer);

      boolean locked = false;
      while (!locked) {
        LOG.info("Attempting to lock");
        LockResponse response = plumpBlockingStub.acquireLock(
            LockRequest.newBuilder().setSequencer(sequencer).build()
        );

        sequencer = response.getUpdatedSequencer();
        locked = response.getSuccess();
        if (!locked) {
          Thread.sleep(2);
        }
      }

      LOG.info("Adding star to COUNT string");
      COUNT = COUNT + "*";

      while (locked) {
        LOG.info("Attempting Unlock");
        ReleaseResponse response = plumpBlockingStub.releaseLock(
            ReleaseRequest.newBuilder().setSequencer(sequencer).build()
        );

        if (response.getSuccess()) {
          locked = false;
        } else {
          sequencer = response.getUpdatedSequencer();
          Thread.sleep(2);
        }
      }

      LOG.info("Unlocked");
      endSignal.countDown();
    } catch (InterruptedException ex) {
      LOG.severe(ex.getLocalizedMessage());
      LOG.severe("Worker was interrupted");
    }
  }
}
