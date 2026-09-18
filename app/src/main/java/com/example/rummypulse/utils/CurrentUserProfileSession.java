package com.example.rummypulse.utils;

import androidx.annotation.Nullable;

import com.example.rummypulse.data.AppUser;

/** Stores the latest synced profile fields for the signed-in user. */
public final class CurrentUserProfileSession {

    private static long profileVersion;
    @Nullable
    private static String photoUrl;
    @Nullable
    private static String displayName;

    private CurrentUserProfileSession() {
    }

    public static void update(@Nullable AppUser appUser) {
        if (appUser == null) {
            clear();
            return;
        }
        profileVersion = appUser.getProfileVersion();
        photoUrl = appUser.getPhotoUrl();
        displayName = appUser.getDisplayName();
    }

    public static void applyOverrides(
            @Nullable String overrideDisplayName,
            @Nullable String overridePhotoUrl) {
        if (overrideDisplayName != null) {
            displayName = overrideDisplayName;
        }
        if (overridePhotoUrl != null) {
            photoUrl = overridePhotoUrl;
        }
    }

    public static long getProfileVersion() {
        return profileVersion;
    }

    @Nullable
    public static String getPhotoUrl() {
        return photoUrl;
    }

    @Nullable
    public static String getDisplayName() {
        return displayName;
    }

    public static void clear() {
        profileVersion = 0L;
        photoUrl = null;
        displayName = null;
    }
}
