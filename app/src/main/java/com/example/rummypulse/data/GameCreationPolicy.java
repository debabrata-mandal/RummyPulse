package com.example.rummypulse.data;

/**
 * Pure submission policy for game creation.
 */
public final class GameCreationPolicy {

    public static final long SLOW_NETWORK_NOTICE_MS = 15_000L;
    public static final String INITIALIZATION_PENDING = "pending";
    public static final String INITIALIZATION_READY = "ready";

    private GameCreationPolicy() {}

    public static boolean canStart(boolean validatedNetwork, boolean attemptInProgress) {
        return validatedNetwork && !attemptInProgress;
    }

    /** Existing games without this newer field remain visible for backward compatibility. */
    public static boolean isReady(String initializationStatus) {
        return !INITIALIZATION_PENDING.equals(initializationStatus);
    }
}
