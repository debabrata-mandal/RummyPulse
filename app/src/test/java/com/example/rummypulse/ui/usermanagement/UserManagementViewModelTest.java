package com.example.rummypulse.ui.usermanagement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.UserRole;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UserManagementViewModelTest {

    private static AppUser admin(String displayName) {
        return new AppUser(
                "id-admin-" + displayName,
                "google",
                UserRole.ADMIN_USER,
                "admin@test.com",
                displayName);
    }

    private static AppUser regular(String displayName) {
        return new AppUser(
                "id-regular-" + displayName,
                "google",
                UserRole.REGULAR_USER,
                "user@test.com",
                displayName);
    }

    @Test
    public void emptyList_remainsEmpty() {
        List<AppUser> list = new ArrayList<>();

        UserManagementViewModel.sortUsers(list);

        assertEquals(0, list.size());
    }

    @Test
    public void singleElement_remainsUnchanged() {
        List<AppUser> list = new ArrayList<>(Arrays.asList(regular("Alice")));

        UserManagementViewModel.sortUsers(list);

        assertEquals(1, list.size());
        assertEquals("Alice", list.get(0).getDisplayName());
    }

    @Test
    public void adminsBeforeRegularUsers() {
        List<AppUser> list = new ArrayList<>(Arrays.asList(
                regular("Zara"),
                admin("Bob")));

        UserManagementViewModel.sortUsers(list);

        assertEquals(UserRole.ADMIN_USER, list.get(0).getRole());
        assertEquals("Bob", list.get(0).getDisplayName());
        assertEquals(UserRole.REGULAR_USER, list.get(1).getRole());
        assertEquals("Zara", list.get(1).getDisplayName());
    }

    @Test
    public void mixedRoles_adminsFirstThenRegularAlphabetically() {
        List<AppUser> list = new ArrayList<>(Arrays.asList(
                regular("Charlie"),
                admin("Alice"),
                regular("Bob"),
                admin("Zara")));

        UserManagementViewModel.sortUsers(list);

        assertEquals(UserRole.ADMIN_USER, list.get(0).getRole());
        assertEquals("Alice", list.get(0).getDisplayName());
        assertEquals(UserRole.ADMIN_USER, list.get(1).getRole());
        assertEquals("Zara", list.get(1).getDisplayName());
        assertEquals(UserRole.REGULAR_USER, list.get(2).getRole());
        assertEquals("Bob", list.get(2).getDisplayName());
        assertEquals(UserRole.REGULAR_USER, list.get(3).getRole());
        assertEquals("Charlie", list.get(3).getDisplayName());
    }

    @Test
    public void allAdmins_sortedAlphabetically() {
        List<AppUser> list = new ArrayList<>(Arrays.asList(
                admin("Zara"),
                admin("Alice"),
                admin("Bob")));

        UserManagementViewModel.sortUsers(list);

        assertEquals("Alice", list.get(0).getDisplayName());
        assertEquals("Bob", list.get(1).getDisplayName());
        assertEquals("Zara", list.get(2).getDisplayName());
    }

    @Test
    public void allRegularUsers_sortedAlphabetically() {
        List<AppUser> list = new ArrayList<>(Arrays.asList(
                regular("Zara"),
                regular("Alice"),
                regular("Bob")));

        UserManagementViewModel.sortUsers(list);

        assertEquals("Alice", list.get(0).getDisplayName());
        assertEquals("Bob", list.get(1).getDisplayName());
        assertEquals("Zara", list.get(2).getDisplayName());
    }

    @Test
    public void alphabeticalSort_isCaseInsensitive() {
        List<AppUser> list = new ArrayList<>(Arrays.asList(
                regular("zara"),
                regular("ALICE"),
                regular("Bob")));

        UserManagementViewModel.sortUsers(list);

        assertEquals("ALICE", list.get(0).getDisplayName());
        assertEquals("Bob", list.get(1).getDisplayName());
        assertEquals("zara", list.get(2).getDisplayName());
    }

    @Test
    public void nullDisplayName_treatedAsEmptyString_sortedFirst() {
        List<AppUser> list = new ArrayList<>(Arrays.asList(
                regular("Bob"),
                regular(null),
                admin(null),
                admin("Alice")));

        UserManagementViewModel.sortUsers(list);

        // Admins first: null (treated as "") sorts before "Alice"
        assertEquals(UserRole.ADMIN_USER, list.get(0).getRole());
        assertNull(list.get(0).getDisplayName());
        assertEquals(UserRole.ADMIN_USER, list.get(1).getRole());
        assertEquals("Alice", list.get(1).getDisplayName());
        // Regular users: null (treated as "") sorts before "Bob"
        assertEquals(UserRole.REGULAR_USER, list.get(2).getRole());
        assertNull(list.get(2).getDisplayName());
        assertEquals(UserRole.REGULAR_USER, list.get(3).getRole());
        assertEquals("Bob", list.get(3).getDisplayName());
    }
}
