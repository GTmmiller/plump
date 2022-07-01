package com.wiligsi.plump.experiment;

import com.wiligsi.plump.client.PlumpClient;
import com.wiligsi.plump.common.PlumpOuterClass.LockResponse;
import com.wiligsi.plump.common.PlumpOuterClass.Sequencer;

public class Philosopher {
  // TODO: Could be config values
  public static final int MAX_FOOD = 100;
  public static final int MIN_FOOD = 0;

  private PhilosopherState state;
  private int food;
  private int turn;

  private int turnDiedOn;
  private int turnsSpentThinking;
  private int turnsSpentHungry;

  private Sequencer lockSequencer = null;

  private final PlumpClient client;
  private final String lockName;

  public Philosopher(PlumpClient client, String lockName) {
    state = PhilosopherState.THINKING;
    food = MAX_FOOD;
    turnDiedOn = -1;
    this.client = client;
    this.lockName = lockName;
  }

  // Should probably use barriers to signal this
  public void takeTurn() {
    turn += 1;

    handleUpkeep();
    // If Thinking -> record a turn spent thinking

    if (state == PhilosopherState.THINKING) {
      actThinking();
    } else if (state == PhilosopherState.HUNGRY) {
      actHungry();
    } else {
      // if dead then do nothing
    }

    updateState();
  }

  public PhilosopherState getState() {
    return state;
  }

  public int getFood() {
    return food;
  }

  public int getTurn() {
    return turn;
  }

  public int getTurnDiedOn() {
    return turnDiedOn;
  }

  public int getTurnsSpentHungry() {
    return turnsSpentHungry;
  }

  public int getTurnsSpentThinking() {
    return turnsSpentThinking;
  }

  private void handleUpkeep() {
    // Upkeep - remove 1 food
    if (state != PhilosopherState.DEAD) {
      food -= 1;
    }
  }

  private void actThinking() {
    // Hmmmmmmmmmmm
    turnsSpentThinking += 1;
  }

  private void actHungry() {
    turnsSpentHungry += 1;

    // If Hungry, record a turn spent hungry and try to get the food lock
    // Attempt to get the food lock
    if (lockSequencer == null) {
      lockSequencer = client.acquireSequencer(lockName);
    }

    LockResponse response = client.acquireLock(lockSequencer);
    if (response.getSuccess()) {
      lockSequencer = null;
      // Eating
      // If the food lock is successful then add 100 -> ceiling Max to food. Mark sequencer as not in use
      food = Math.min(MAX_FOOD, food + 100);
    } else {
      // Didn't get to eat
      // If unsuccessful then update the sequencer and end turn
      lockSequencer = response.getUpdatedSequencer();
    }



    // TODO: Maybe encapsulate them as objects like a left and right as locking objects or something
  }

  private void updateState() {
    // If food lte 0 then change state to dead and record the turn they died on
    if (state != PhilosopherState.DEAD && food <= 0) {
      state = PhilosopherState.DEAD;
      turnDiedOn = turn;
    } else if (state == PhilosopherState.THINKING && food <= 25) {
      // If thinking and food is gt 0 but lte 25 then state to hungry
      state = PhilosopherState.HUNGRY;
    } else if (state == PhilosopherState.HUNGRY && food > 25) {
      // If Hungry and food is > 25 then state is Thinking
      state = PhilosopherState.THINKING;
    } else {
      // No state change
    }
  }




}
