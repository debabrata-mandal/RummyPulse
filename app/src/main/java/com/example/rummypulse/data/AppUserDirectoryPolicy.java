package com.example.rummypulse.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure policy for loading the user directory from Firestore's on-device cache plus a
 * {@code profileVersion} change-only query instead of reading every user on each cold start.
 */
public final class AppUserDirectoryPolicy {

    private AppUserDirectoryPolicy() {}

    /** Lower bound, exclusive, for the change-only query over {@code users}. */
    public static long deltaSinceVersion(List<AppUser> users) {
        long maxVersion = 0L;
        if (users != null) {
            for (AppUser user : users) {
                if (user != null) {
                    maxVersion = Math.max(maxVersion, user.getProfileVersion());
                }
            }
        }
        return DeviceCachePolicy.deltaSince(maxVersion);
    }

    /** Returns the {@code updates} that are new users or carry a different profileVersion. */
    public static List<AppUser> changedUsers(List<AppUser> current, List<AppUser> updates) {
        Map<String, Long> versionsByUserId = new HashMap<>();
        if (current != null) {
            for (AppUser user : current) {
                if (user != null && user.getUserId() != null) {
                    versionsByUserId.put(user.getUserId(), user.getProfileVersion());
                }
            }
        }
        List<AppUser> changed = new ArrayList<>();
        if (updates == null) {
            return changed;
        }
        for (AppUser user : updates) {
            if (user == null || user.getUserId() == null) {
                continue;
            }
            Long knownVersion = versionsByUserId.get(user.getUserId());
            if (knownVersion == null || knownVersion != user.getProfileVersion()) {
                changed.add(user);
            }
        }
        return changed;
    }
}
