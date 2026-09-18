package com.example.rummypulse.utils;

import androidx.annotation.Nullable;

import com.example.rummypulse.data.AppUserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Reloads Firebase Auth profile metadata before syncing appUser_v2. */
public final class ProfileSyncHelper {

    private ProfileSyncHelper() {
    }

    public static void reloadAndSync(
            FirebaseUser user,
            String provider,
            @Nullable AppUserRepository.AppUserCallback callback) {
        reloadAndSync(user, provider, null, false, callback);
    }

    public static void reloadAndSync(
            FirebaseUser user,
            String provider,
            @Nullable AppUserRepository.ProfileOverrides overrides,
            boolean forceProfileVersionRefresh,
            @Nullable AppUserRepository.AppUserCallback callback) {
        if (user == null) {
            if (callback != null) {
                callback.onFailure(new IllegalArgumentException("FirebaseUser cannot be null"));
            }
            return;
        }
        user.reload().addOnCompleteListener(task -> {
            FirebaseUser freshUser = FirebaseAuth.getInstance().getCurrentUser();
            if (freshUser == null) {
                if (callback != null) {
                    callback.onFailure(new IllegalStateException("Signed-in user missing after reload"));
                }
                return;
            }
            new AppUserRepository().createOrUpdateUser(
                    freshUser,
                    provider,
                    overrides,
                    forceProfileVersionRefresh,
                    callback);
        });
    }
}
