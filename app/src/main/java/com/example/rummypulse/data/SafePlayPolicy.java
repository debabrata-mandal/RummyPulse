package com.example.rummypulse.data;

/** Versioned acceptance policy for the non-monetary Game Points experience. */
public final class SafePlayPolicy {

    public static final int CURRENT_VERSION = 1;

    private SafePlayPolicy() {
    }

    public static boolean hasCurrentAcceptance(AppUser user) {
        return user != null
                && user.getSafePlayPolicyVersion() != null
                && user.getSafePlayPolicyVersion() >= CURRENT_VERSION
                && user.getSafePlayAcceptedAt() != null;
    }
}
