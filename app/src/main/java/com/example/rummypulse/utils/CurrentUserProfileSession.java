package com.example.rummypulse.utils;

import android.content.Context;

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
    private static String profileUid;
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
        profileUid = appUser.getUserId();
        photoUrl = appUser.getPhotoUrl();
        displayName = appUser.getDisplayName();
        profileName = appUser.getProfileName();
        notifyChanged();
    }

    /** Restores only the public game name, scoped to the verified Firebase UID. */
    public static void restoreCachedName(Context context, String uid) {
        if (uid == null || uid.isEmpty()) return;
        if (!uid.equals(profileUid)) clear();
        profileUid = uid;
        String cached = context.getApplicationContext().getSharedPreferences(
                "verified_profile_names", Context.MODE_PRIVATE).getString(uid, null);
        if (cached == null || cached.trim().isEmpty()) return;
        if (cached.equals(profileName)) return;
        profileName = cached;
        displayName = cached;
        notifyChanged();
    }

    public static void cachePublicName(Context context, @Nullable AppUser appUser) {
        if (appUser == null || appUser.getUserId() == null) return;
        cachePublicName(context, appUser.getUserId(), appUser.getProfileName());
    }

    public static void cachePublicName(Context context, String uid, String name) {
        if (uid == null || uid.isEmpty()) return;
        if (name == null || name.trim().isEmpty()) return;
        context.getApplicationContext().getSharedPreferences(
                "verified_profile_names", Context.MODE_PRIVATE).edit()
                .putString(uid, name).apply();
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
        profileUid = null;
        photoUrl = null;
        displayName = null;
        profileName = null;
        notifyChanged();
    }

    public static boolean belongsTo(String uid) {
        return uid != null && uid.equals(profileUid);
    }

    private static void notifyChanged() {
        changes.postValue(changeVersion.incrementAndGet());
    }
}
