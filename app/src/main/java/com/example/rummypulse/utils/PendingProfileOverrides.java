package com.example.rummypulse.utils;

import androidx.annotation.Nullable;

import com.example.rummypulse.data.AppUserRepository;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

/**
 * Holds Google profile hints from the most recent explicit sign-in until the first app-user sync
 * consumes them.
 */
public final class PendingProfileOverrides {

    private static AppUserRepository.ProfileOverrides pendingOverrides;
    private static boolean forceProfileVersionRefresh;

    private PendingProfileOverrides() {
    }

    public static void setFromGoogleAccount(@Nullable GoogleSignInAccount account) {
        if (account == null) {
            clear();
            return;
        }
        String photoUrl = account.getPhotoUrl() != null
                ? account.getPhotoUrl().toString()
                : null;
        pendingOverrides = new AppUserRepository.ProfileOverrides(
                account.getDisplayName(),
                photoUrl);
        forceProfileVersionRefresh = true;
    }

    public static void set(@Nullable AppUserRepository.ProfileOverrides overrides) {
        pendingOverrides = overrides;
        forceProfileVersionRefresh = overrides != null;
    }

    @Nullable
    public static AppUserRepository.ProfileOverrides consumeOverrides() {
        AppUserRepository.ProfileOverrides overrides = pendingOverrides;
        pendingOverrides = null;
        return overrides;
    }

    public static boolean consumeForceProfileVersionRefresh() {
        boolean force = forceProfileVersionRefresh;
        forceProfileVersionRefresh = false;
        return force;
    }

    public static void clear() {
        pendingOverrides = null;
        forceProfileVersionRefresh = false;
    }
}
