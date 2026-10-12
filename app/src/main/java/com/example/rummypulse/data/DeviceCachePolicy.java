package com.example.rummypulse.data;

/**
 * Pure policy for collections loaded from Firestore's on-device cache plus a change-only query
 * ({@code appUser_v2} by {@code profileVersion}, {@code playerStats_v2} by {@code updatedAt}).
 */
public final class DeviceCachePolicy {

    /** A full reload is still needed periodically; change-only queries cannot see deletions. */
    public static final long FULL_RELOAD_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    /**
     * Change markers are millisecond clocks written by different servers or admin devices, so
     * writes can commit slightly out of order. Re-reading recent changes keeps a late commit from
     * being skipped.
     */
    public static final long DELTA_LOOKBACK_MS = 10L * 60L * 1000L;

    private DeviceCachePolicy() {}

    /**
     * The on-device cache only holds the whole collection after a full reload on this install, and
     * a reload time later than {@code nowMillis} means the device clock moved backwards.
     */
    public static boolean canSeedFromDeviceCache(long lastFullReloadAtMillis, long nowMillis) {
        return lastFullReloadAtMillis > 0L
                && nowMillis >= lastFullReloadAtMillis
                && nowMillis - lastFullReloadAtMillis < FULL_RELOAD_INTERVAL_MS;
    }

    /** Exclusive lower bound for the change-only query, given the newest cached marker. */
    public static long deltaSince(long newestCachedMarker) {
        return Math.max(0L, newestCachedMarker - DELTA_LOOKBACK_MS);
    }
}
