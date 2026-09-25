package com.example.rummypulse.utils;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.rummypulse.data.AppUser;

import java.util.concurrent.atomic.AtomicLong;

/** Stores the latest synced profile fields for the signed-in user. */
public final class CurrentUserProfileSession {

    private static final AtomicLong changeVersion = new AtomicLong();
    private static final MutableLiveData<Long> changes = new MutableLiveData<>(0L);
    private static long profileVersion;
    @Nullable
    private static String photoUrl;
    @Nullable
    private static String displayName;
    @Nullable
    private static String profileName;

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
        profileName = appUser.getProfileName();
        notifyChanged();
    }

    public static void applyOverrides(
            @Nullable String overrideDisplayName,
            @Nullable String overridePhotoUrl) {
        if (overridePhotoUrl != null) {
            photoUrl = overridePhotoUrl;
            notifyChanged();
        }
    }

    public static LiveData<Long> getChanges() {
        return changes;
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

    @Nullable
    public static String getProfileName() {
        return profileName;
    }

    public static void applyPublicProfile(@Nullable String newProfileName, String newDisplayName) {
        profileName = newProfileName;
        displayName = newDisplayName;
        profileVersion = System.currentTimeMillis();
        notifyChanged();
    }

    public static void clear() {
        profileVersion = 0L;
        photoUrl = null;
        displayName = null;
        profileName = null;
        notifyChanged();
    }

    private static void notifyChanged() {
        changes.postValue(changeVersion.incrementAndGet());
    }
}
