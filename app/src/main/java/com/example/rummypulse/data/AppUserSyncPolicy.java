package com.example.rummypulse.data;

import java.util.Date;
import java.util.Objects;

/**
 * Pure policy for deciding whether an existing appUser document needs a profile/login write.
 */
public final class AppUserSyncPolicy {

    public static final long LAST_LOGIN_WRITE_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private AppUserSyncPolicy() {}

    public static SyncPlan plan(
            AppUser stored,
            String provider,
            String email,
            String displayName,
            String photoUrl,
            long nowMillis) {
        return new SyncPlan(
                !Objects.equals(stored.getProvider(), provider),
                !Objects.equals(stored.getEmail(), email),
                !Objects.equals(stored.getDisplayName(), displayName),
                !Objects.equals(stored.getPhotoUrl(), photoUrl),
                shouldUpdateLastLogin(stored.getLastLoginAt(), nowMillis));
    }

    public static boolean shouldUpdateLastLogin(Date lastLoginAt, long nowMillis) {
        if (lastLoginAt == null) {
            return true;
        }
        long age = nowMillis - lastLoginAt.getTime();
        return age >= LAST_LOGIN_WRITE_INTERVAL_MS;
    }

    public static final class SyncPlan {
        public final boolean updateProvider;
        public final boolean updateEmail;
        public final boolean updateDisplayName;
        public final boolean updatePhotoUrl;
        public final boolean updateLastLoginAt;

        private SyncPlan(
                boolean updateProvider,
                boolean updateEmail,
                boolean updateDisplayName,
                boolean updatePhotoUrl,
                boolean updateLastLoginAt) {
            this.updateProvider = updateProvider;
            this.updateEmail = updateEmail;
            this.updateDisplayName = updateDisplayName;
            this.updatePhotoUrl = updatePhotoUrl;
            this.updateLastLoginAt = updateLastLoginAt;
        }

        public boolean hasUpdates() {
            return updateProvider
                    || updateEmail
                    || updateDisplayName
                    || updatePhotoUrl
                    || updateLastLoginAt;
        }
    }
}
