package com.wiligsi.plump.server;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LockName {

    private final String displayName;
    private final String internalName;
    private static final Pattern LOCK_NAME_REGEX = Pattern.compile("^[a-zA-Z]\\p{Alnum}{3,11}$");

    public LockName(String displayName) throws IllegalArgumentException {
        Matcher lockNameMatcher = LOCK_NAME_REGEX.matcher(displayName);
        if(!lockNameMatcher.matches()) {
            throw new IllegalArgumentException(
                    String.format(
                            "LockName '%s' is invalid. Names should be a series of 4-12 alphanumeric characters",
                            displayName
                    )
            );
        }

        this.displayName = displayName;
        internalName = this.displayName.toLowerCase();
    }

    public String getDisplayName() {
        return displayName;
    }

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
                "LockName{displayName=%s, internalName=%s}",
                displayName,
                internalName
        );
    }
}
