package com.example.rummypulse.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Date;

public class AppUserSyncPolicyTest {

    private static final long NOW = 2_000_000_000_000L;

    @Test
    public void unchangedRecentUserRequiresNoWrite() {
        AppUser stored = userWithLastLogin(NOW - 60_000L);

        AppUserSyncPolicy.SyncPlan plan = AppUserSyncPolicy.plan(
                stored, "Google", "user@example.com", "User", "photo", NOW);

        assertFalse(plan.hasUpdates());
    }

    @Test
    public void staleLastLoginRequiresWrite() {
        AppUser stored = userWithLastLogin(
                NOW - AppUserSyncPolicy.LAST_LOGIN_WRITE_INTERVAL_MS);

        AppUserSyncPolicy.SyncPlan plan = AppUserSyncPolicy.plan(
                stored, "Google", "user@example.com", "User", "photo", NOW);

        assertTrue(plan.updateLastLoginAt);
        assertTrue(plan.hasUpdates());
    }

    @Test
    public void missingLastLoginRequiresWrite() {
        AppUser stored = userWithLastLogin(null);

        AppUserSyncPolicy.SyncPlan plan = AppUserSyncPolicy.plan(
                stored, "Google", "user@example.com", "User", "photo", NOW);

        assertTrue(plan.updateLastLoginAt);
    }

    @Test
    public void changedProfileFieldsAreDetectedIndividually() {
        AppUser stored = userWithLastLogin(NOW - 60_000L);

        AppUserSyncPolicy.SyncPlan plan = AppUserSyncPolicy.plan(
                stored, "Microsoft", "new@example.com", "New Name", "new-photo", NOW);

        assertTrue(plan.updateProvider);
        assertTrue(plan.updateEmail);
        assertTrue(plan.updateDisplayName);
        assertTrue(plan.updatePhotoUrl);
        assertTrue(plan.updateProfileVersion);
        assertFalse(plan.updateLastLoginAt);
    }

    @Test
    public void staleCachedFirebaseMetadataCannotOverwriteStoredProviderProfile() {
        AppUser stored = userWithLastLogin(NOW - 60_000L);

        AppUserSyncPolicy.SyncPlan plan = AppUserSyncPolicy.plan(
                stored,
                "Google",
                "user@example.com",
                "Old cached name",
                "old-cached-photo",
                NOW,
                false,
                false);

        assertFalse(plan.updateDisplayName);
        assertFalse(plan.updatePhotoUrl);
        assertFalse(plan.updateProfileVersion);
        assertFalse(plan.hasUpdates());
    }

    @Test
    public void explicitProviderProfileCanReplaceStoredProfile() {
        AppUser stored = userWithLastLogin(NOW - 60_000L);

        AppUserSyncPolicy.SyncPlan plan = AppUserSyncPolicy.plan(
                stored,
                "Google",
                "user@example.com",
                "New provider name",
                "new-provider-photo",
                NOW,
                true,
                true);

        assertTrue(plan.updateDisplayName);
        assertTrue(plan.updatePhotoUrl);
        assertTrue(plan.updateProfileVersion);
        assertTrue(plan.hasUpdates());
    }

    @Test
    public void forceProfileVersionRefreshRequiresWriteEvenWhenFieldsMatch() {
        AppUser stored = userWithLastLogin(NOW - 60_000L);

        AppUserSyncPolicy.SyncPlan plan = AppUserSyncPolicy.plan(
                stored, "Google", "user@example.com", "User", "photo", NOW, true);

        assertTrue(plan.updateProfileVersion);
        assertFalse(plan.updateDisplayName);
        assertFalse(plan.updatePhotoUrl);
        assertFalse(plan.updateLastLoginAt);
        assertTrue(plan.hasUpdates());
    }

    @Test
    public void futureServerTimestampDoesNotCauseWriteLoop() {
        AppUser stored = userWithLastLogin(NOW + 60_000L);

        AppUserSyncPolicy.SyncPlan plan = AppUserSyncPolicy.plan(
                stored, "Google", "user@example.com", "User", "photo", NOW);

        assertFalse(plan.updateLastLoginAt);
        assertFalse(plan.hasUpdates());
    }

    private static AppUser userWithLastLogin(Long timestamp) {
        AppUser user = new AppUser(
                "uid",
                "Google",
                UserRole.REGULAR_USER,
                "user@example.com",
                "User",
                "photo");
        user.setLastLoginAt(timestamp != null ? new Date(timestamp) : null);
        return user;
    }
}
