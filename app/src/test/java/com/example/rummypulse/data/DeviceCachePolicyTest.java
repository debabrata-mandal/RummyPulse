package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceCachePolicyTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void canSeedFromDeviceCache_withinIntervalAfterFullReload() {
        assertTrue(DeviceCachePolicy.canSeedFromDeviceCache(NOW - 60_000L, NOW));
    }

    @Test
    public void canSeedFromDeviceCache_notAtOrPastInterval() {
        assertFalse(DeviceCachePolicy.canSeedFromDeviceCache(
                NOW - DeviceCachePolicy.FULL_RELOAD_INTERVAL_MS, NOW));
    }

    @Test
    public void canSeedFromDeviceCache_notWithoutFullReload() {
        assertFalse(DeviceCachePolicy.canSeedFromDeviceCache(0L, NOW));
    }

    @Test
    public void canSeedFromDeviceCache_notWhenClockMovedBackwards() {
        assertFalse(DeviceCachePolicy.canSeedFromDeviceCache(NOW + 60_000L, NOW));
    }

    @Test
    public void deltaSince_looksBackFromNewestMarker() {
        assertEquals(NOW - DeviceCachePolicy.DELTA_LOOKBACK_MS, DeviceCachePolicy.deltaSince(NOW));
    }

    @Test
    public void deltaSince_neverNegative() {
        assertEquals(0L, DeviceCachePolicy.deltaSince(1_000L));
        assertEquals(0L, DeviceCachePolicy.deltaSince(0L));
    }
}
