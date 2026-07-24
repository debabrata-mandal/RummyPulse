package com.example.rummypulse.data;

/**
 * Pure submission policy for game creation.
 */
public final class GameCreationPolicy {

    public static final long SLOW_NETWORK_NOTICE_MS = 15_000L;

    private GameCreationPolicy() {}

    public static boolean canStart(boolean validatedNetwork, boolean attemptInProgress) {
        return validatedNetwork && !attemptInProgress;
    }
}
