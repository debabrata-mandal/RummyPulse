package com.example.rummypulse.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Filters the app-user directory for screens that should not offer hidden test accounts.
 */
public final class AppUserDirectoryFilter {

    private AppUserDirectoryFilter() {
    }

    /** Returns only users that may be linked to a player in game edit. */
    public static List<AppUser> forPlayerMapping(List<AppUser> users) {
        List<AppUser> filtered = new ArrayList<>();
        if (users == null) {
            return filtered;
        }
        for (AppUser user : users) {
            if (user != null && !user.isHidden()) {
                filtered.add(user);
            }
        }
        return filtered;
    }
}
