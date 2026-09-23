package com.example.rummypulse.data;

import java.util.ArrayList;
import java.util.List;

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

    private static boolean hasProfileName(AppUser user) {
        return user.getProfileName() != null && !user.getProfileName().trim().isEmpty();
    }
}
