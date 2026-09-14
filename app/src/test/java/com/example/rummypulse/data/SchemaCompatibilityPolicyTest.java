package com.example.rummypulse.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SchemaCompatibilityPolicyTest {

    @Test
    public void currentSchema_canOpen() {
        assertTrue(SchemaCompatibilityPolicy.canOpen(
                (long) GameDataSchema.CURRENT_VERSION));
    }

    @Test
    public void missingOlderAndFutureSchemas_areBlocked() {
        assertFalse(SchemaCompatibilityPolicy.canOpen(null));
        assertFalse(SchemaCompatibilityPolicy.canOpen(2L));
        assertFalse(SchemaCompatibilityPolicy.canOpen(4L));
    }
}
