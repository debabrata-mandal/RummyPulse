package com.example.rummypulse.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Date;

public class SafePlayPolicyTest {

    @Test
    public void currentVersionWithTimestampIsAccepted() {
        AppUser user = acceptedUser(SafePlayPolicy.CURRENT_VERSION);

        assertTrue(SafePlayPolicy.hasCurrentAcceptance(user));
    }

    @Test
    public void futureVersionRemainsAccepted() {
        AppUser user = acceptedUser(SafePlayPolicy.CURRENT_VERSION + 1);

        assertTrue(SafePlayPolicy.hasCurrentAcceptance(user));
    }

    @Test
    public void olderVersionRequiresNewAcceptance() {
        AppUser user = acceptedUser(SafePlayPolicy.CURRENT_VERSION - 1);

        assertFalse(SafePlayPolicy.hasCurrentAcceptance(user));
    }

    @Test
    public void missingSafePlayTimestampIsRejected() {
        AppUser user = acceptedUser(SafePlayPolicy.CURRENT_VERSION);
        user.setSafePlayAcceptedAt(null);

        assertFalse(SafePlayPolicy.hasCurrentAcceptance(user));
    }

    @Test
    public void absentProfileIsRejected() {
        assertFalse(SafePlayPolicy.hasCurrentAcceptance(null));
    }

    private static AppUser acceptedUser(int version) {
        AppUser user = new AppUser();
        user.setSafePlayPolicyVersion(version);
        user.setSafePlayAcceptedAt(new Date(1_000L));
        return user;
    }
}
