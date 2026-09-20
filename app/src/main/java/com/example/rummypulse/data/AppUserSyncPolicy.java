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
        return plan(stored, provider, email, displayName, photoUrl, nowMillis, false);
    }

    public static SyncPlan plan(
            AppUser stored,
            String provider,
            String email,
            String displayName,
            String photoUrl,
            long nowMillis,
            boolean forceProfileVersionRefresh) {
        return plan(
                stored,
                provider,
                email,
                displayName,
                photoUrl,
                nowMillis,
                forceProfileVersionRefresh,
                true);
    }

    public static SyncPlan plan(
            AppUser stored,
            String provider,
            String email,
            String displayName,
            String photoUrl,
            long nowMillis,
            boolean forceProfileVersionRefresh,
            boolean providerProfileAuthoritative) {
        boolean updateProvider = !Objects.equals(stored.getProvider(), provider);
        boolean updateEmail = !Objects.equals(stored.getEmail(), email);
        boolean updateDisplayName = shouldUpdateProfileField(
                stored.getDisplayName(), displayName, providerProfileAuthoritative);
        boolean updatePhotoUrl = shouldUpdateProfileField(
                stored.getPhotoUrl(), photoUrl, providerProfileAuthoritative);
        boolean profileFieldsChanged = updateProvider
                || updateEmail
                || updateDisplayName
                || updatePhotoUrl;
        boolean updateProfileVersion = profileFieldsChanged || forceProfileVersionRefresh;
        return new SyncPlan(
                updateProvider,
                updateEmail,
                updateDisplayName,
                updatePhotoUrl,
                updateProfileVersion,
                shouldUpdateLastLogin(stored.getLastLoginAt(), nowMillis));
    }

    private static boolean shouldUpdateProfileField(
            String storedValue,
            String incomingValue,
            boolean providerProfileAuthoritative) {
        if (Objects.equals(storedValue, incomingValue)) {
            return false;
        }
        return providerProfileAuthoritative || isNullOrEmpty(storedValue);
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
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
        public final boolean updateProfileVersion;
        public final boolean updateLastLoginAt;

        private SyncPlan(
                boolean updateProvider,
                boolean updateEmail,
                boolean updateDisplayName,
                boolean updatePhotoUrl,
                boolean updateProfileVersion,
                boolean updateLastLoginAt) {
            this.updateProvider = updateProvider;
            this.updateEmail = updateEmail;
            this.updateDisplayName = updateDisplayName;
            this.updatePhotoUrl = updatePhotoUrl;
            this.updateProfileVersion = updateProfileVersion;
            this.updateLastLoginAt = updateLastLoginAt;
        }

        public boolean hasUpdates() {
            return updateProvider
                    || updateEmail
                    || updateDisplayName
                    || updatePhotoUrl
                    || updateProfileVersion
                    || updateLastLoginAt;
        }
    }
}
