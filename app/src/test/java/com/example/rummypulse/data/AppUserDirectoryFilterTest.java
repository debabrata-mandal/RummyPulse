package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.rummypulse.data.UserRole;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AppUserDirectoryFilterTest {

    private static AppUser user(String id, boolean hidden) {
        AppUser user = new AppUser(id, "Google", UserRole.REGULAR_USER, id + "@test.com", id);
        user.setProfileName(id);
        user.setHidden(hidden);
        return user;
    }

    @Test
    public void forPlayerMapping_omitsHiddenUsers() {
        List<AppUser> filtered = AppUserDirectoryFilter.forPlayerMapping(Arrays.asList(
                user("visible", false),
                user("hidden", true)));

        assertEquals(1, filtered.size());
        assertEquals("visible", filtered.get(0).getUserId());
    }

    @Test
    public void forPlayerMapping_omitsUsersWithoutProfileNames() {
        AppUser incomplete = user("incomplete", false);
        incomplete.setProfileName(null);

        List<AppUser> filtered = AppUserDirectoryFilter.forPlayerMapping(Arrays.asList(
                user("complete", false), incomplete));

        assertEquals(1, filtered.size());
        assertEquals("complete", filtered.get(0).getUserId());
    }

    @Test
    public void forPlayerMapping_handlesNullAndEmpty() {
        assertTrue(AppUserDirectoryFilter.forPlayerMapping(null).isEmpty());
        assertTrue(AppUserDirectoryFilter.forPlayerMapping(Arrays.asList()).isEmpty());
    }

    @Test
    public void mergeByUserId_addsNewlyReadUsersWithoutAnotherFetch() {
        List<AppUser> merged = AppUserDirectoryFilter.mergeByUserId(
                Arrays.asList(user("existing", false)),
                Arrays.asList(user("new-user", false)));

        assertEquals(2, merged.size());
        assertEquals("new-user", merged.get(1).getUserId());
    }

    @Test
    public void mergeByUserId_replacesExistingUserWithoutDuplicates() {
        AppUser updated = user("existing", false);
        updated.setProfileName("Updated Name");

        List<AppUser> merged = AppUserDirectoryFilter.mergeByUserId(
                Arrays.asList(user("existing", false)), Arrays.asList(updated));

        assertEquals(1, merged.size());
        assertEquals("Updated Name", merged.get(0).getProfileName());
    }
}
