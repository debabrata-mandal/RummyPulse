package com.example.rummypulse.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.UserRole;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class CurrentUserProfileSessionTest {

    @Rule
    public final InstantTaskExecutorRule instantTaskExecutorRule =
            new InstantTaskExecutorRule();

    @After
    public void tearDown() {
        CurrentUserProfileSession.clear();
    }

    @Test
    public void update_notifiesObserversAfterRestoredProfileArrives() {
        AtomicInteger notifications = new AtomicInteger();
        Observer<Long> observer = ignored -> notifications.incrementAndGet();
        CurrentUserProfileSession.getChanges().observeForever(observer);
        int initialNotifications = notifications.get();

        AppUser user = new AppUser(
                "uid-1", "Google", UserRole.REGULAR_USER, null, "CardKing");
        user.setProfileName("CardKing");
        CurrentUserProfileSession.update(user);

        assertEquals("CardKing", CurrentUserProfileSession.getDisplayName());
        assertTrue(notifications.get() > initialNotifications);
        CurrentUserProfileSession.getChanges().removeObserver(observer);
    }
}
