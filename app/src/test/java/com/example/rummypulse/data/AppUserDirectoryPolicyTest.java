package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AppUserDirectoryPolicyTest {

    private static final long NOW = 1_800_000_000_000L;

    private static AppUser user(String id, long profileVersion) {
        AppUser user = new AppUser(id, "Google", UserRole.REGULAR_USER, null, id);
        user.setProfileVersion(profileVersion);
        return user;
    }

    @Test
    public void deltaSinceVersion_looksBackFromNewestVersion() {
        long since = AppUserDirectoryPolicy.deltaSinceVersion(Arrays.asList(
                user("a", NOW - 5_000_000L), user("b", NOW), user("c", 0L)));

        assertEquals(NOW - DeviceCachePolicy.DELTA_LOOKBACK_MS, since);
    }

    @Test
    public void deltaSinceVersion_neverNegative() {
        assertEquals(0L, AppUserDirectoryPolicy.deltaSinceVersion(
                Collections.singletonList(user("a", 1_000L))));
        assertEquals(0L, AppUserDirectoryPolicy.deltaSinceVersion(null));
    }

    @Test
    public void changedUsers_keepsNewAndReversionedUsersOnly() {
        List<AppUser> changed = AppUserDirectoryPolicy.changedUsers(
                Arrays.asList(user("same", 10L), user("renamed", 10L)),
                Arrays.asList(user("same", 10L), user("renamed", 20L), user("new", 5L)));

        assertEquals(2, changed.size());
        assertEquals("renamed", changed.get(0).getUserId());
        assertEquals("new", changed.get(1).getUserId());
    }

    @Test
    public void changedUsers_handlesNulls() {
        assertTrue(AppUserDirectoryPolicy.changedUsers(null, null).isEmpty());
        assertEquals(1, AppUserDirectoryPolicy.changedUsers(
                null, Collections.singletonList(user("a", 1L))).size());
    }
}
