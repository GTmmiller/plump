package com.wiligsi.plump.server.lock;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * This class captures the requirements around names for the Lock class and handles display names,
 * internal names, and rejecting names that aren't suitable for locks.
 *
 * <p>LockNames are equal if the internal names are the same. So if you create a Lock with the name
 * "LOCK", it will be considered the same as a Lock with the name "Lock".</p>
 *
 * @author Steven Miller
 */
public class LockName {

  private final String displayName;
  private final String internalName;
  private static final Pattern LOCK_NAME_REGEX = Pattern.compile("^[a-zA-Z]\\p{Alnum}{3,11}$");

  /**
   * Creates a new LockName given a string display name.
   *
   * @param displayName - the display name of a new lock
   * @throws IllegalArgumentException if the name doesn't conform to the LOCK_NAME_REGEX
   */
  public LockName(String displayName) throws IllegalArgumentException {
    Matcher lockNameMatcher = LOCK_NAME_REGEX.matcher(displayName);
    if (!lockNameMatcher.matches()) {
      throw new IllegalArgumentException(
          String.format(
              "com.wiligsi.plump.server.lock.LockName '%s' is invalid. Names should be a series of 4-12"
                  + " alphanumeric characters",
              displayName
          )
      );
    }

    this.displayName = displayName;
    internalName = this.displayName.toLowerCase();
  }

  /**
   * Returns the display name of the LockName.
   *
   * @return the display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Returns the internal name, an all lowercase version of the displayName.
   *
   * @return the internal name of the LockName object
   */
  public String getInternalName() {
    return internalName;
  }

  @Override
  public int hashCode() {
    return internalName.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }

    if (obj == this) {
      return true;
    }

    if (obj.getClass() != getClass()) {
      return false;
    }

    return obj.hashCode() == hashCode();
  }

  @Override
  public String toString() {
    return String.format(
        "com.wiligsi.plump.server.lock.LockName{displayName=%s, internalName=%s}",
        displayName,
        internalName
    );
  }
}
