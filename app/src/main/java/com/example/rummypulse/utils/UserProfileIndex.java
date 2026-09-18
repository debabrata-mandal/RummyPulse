package com.example.rummypulse.utils;

import androidx.annotation.Nullable;

import com.example.rummypulse.data.AppUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds lookup maps from the shared app-user directory. */
public final class UserProfileIndex {

    private UserProfileIndex() {
    }

    public static Map<String, String> photoUrlsByUserId(@Nullable List<AppUser> users) {
        Map<String, String> byUserId = new HashMap<>();
        if (users == null) {
            return byUserId;
        }
        for (AppUser user : users) {
            if (user == null || user.getUserId() == null) {
                continue;
            }
            String photoUrl = user.getPhotoUrl();
            if (photoUrl != null && !photoUrl.trim().isEmpty()) {
                byUserId.put(user.getUserId(), photoUrl.trim());
            }
        }
        return byUserId;
    }

    public static Map<String, Long> profileVersionsByUserId(@Nullable List<AppUser> users) {
        Map<String, Long> byUserId = new HashMap<>();
        if (users == null) {
            return byUserId;
        }
        for (AppUser user : users) {
            if (user == null || user.getUserId() == null) {
                continue;
            }
            byUserId.put(user.getUserId(), user.getProfileVersion());
        }
        return byUserId;
    }

    public static long profileVersionForUserId(
            @Nullable String userId,
            @Nullable Map<String, Long> profileVersionByUserId) {
        if (userId == null || profileVersionByUserId == null) {
            return 0L;
        }
        Long version = profileVersionByUserId.get(userId);
        return version != null ? version : 0L;
    }
}
