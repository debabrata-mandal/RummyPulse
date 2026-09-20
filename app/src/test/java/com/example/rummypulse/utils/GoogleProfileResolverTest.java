package com.example.rummypulse.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.example.rummypulse.data.AppUserRepository;

import org.junit.Test;

public class GoogleProfileResolverTest {

    @Test
    public void freshUserInfoReplacesStaleSignInProfile() {
        AppUserRepository.ProfileOverrides stale =
                new AppUserRepository.ProfileOverrides("Debabrata Device", "old-photo");

        AppUserRepository.ProfileOverrides resolved = GoogleProfileResolver.mergeUserInfo(
                stale,
                "google-user-id",
                "{\"sub\":\"google-user-id\","
                        + "\"name\":\"Debabrata Android\","
                        + "\"picture\":\"new-photo\"}");

        assertEquals("Debabrata Android", resolved.displayName);
        assertEquals("new-photo", resolved.photoUrl);
    }

    @Test
    public void mismatchedUserInfoCannotReplaceSelectedAccountProfile() {
        AppUserRepository.ProfileOverrides fallback =
                new AppUserRepository.ProfileOverrides("Selected User", "selected-photo");

        AppUserRepository.ProfileOverrides resolved = GoogleProfileResolver.mergeUserInfo(
                fallback,
                "selected-account-id",
                "{\"sub\":\"different-account-id\","
                        + "\"name\":\"Different User\","
                        + "\"picture\":\"different-photo\"}");

        assertSame(fallback, resolved);
    }
}
