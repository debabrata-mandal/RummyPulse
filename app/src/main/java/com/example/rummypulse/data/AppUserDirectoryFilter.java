package com.example.rummypulse.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filters the app-user directory for screens that should only offer completed, visible profiles.
 */
public final class AppUserDirectoryFilter {

    private AppUserDirectoryFilter() {
    }

    /** Returns only visible users that have completed their public profile-name setup. */
    public static List<AppUser> forPlayerMapping(List<AppUser> users) {
        List<AppUser> filtered = new ArrayList<>();
        if (users == null) {
            return filtered;
        }
        for (AppUser user : users) {
            if (user != null && !user.isHidden() && hasProfileName(user)) {
                filtered.add(user);
            }
        }
        return filtered;
    }

    /** Merges already-read directory rows without requiring another database request. */
    static List<AppUser> mergeByUserId(List<AppUser> current, List<AppUser> updates) {
        List<AppUser> merged = current == null ? new ArrayList<>() : new ArrayList<>(current);
        Map<String, Integer> indexesByUserId = new HashMap<>();
        for (int index = 0; index < merged.size(); index++) {
            AppUser user = merged.get(index);
            if (user != null && user.getUserId() != null) {
                indexesByUserId.put(user.getUserId(), index);
            }
        }
        if (updates == null) {
            return merged;
        }
        for (AppUser user : updates) {
            if (user == null || user.getUserId() == null) {
                continue;
            }
            Integer existingIndex = indexesByUserId.get(user.getUserId());
            if (existingIndex == null) {
                indexesByUserId.put(user.getUserId(), merged.size());
                merged.add(user);
            } else {
                merged.set(existingIndex, user);
            }
        }
        return merged;
    }

    private static boolean hasProfileName(AppUser user) {
        return user.getProfileName() != null && !user.getProfileName().trim().isEmpty();
    }
}
