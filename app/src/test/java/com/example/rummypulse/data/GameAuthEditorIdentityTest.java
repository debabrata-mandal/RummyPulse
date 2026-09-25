package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GameAuthEditorIdentityTest {
    @Test
    public void activeEditorTakesPriority() {
        GameAuth auth = authWithCreator();
        auth.setLastEditorUserId("last-id");
        auth.setActiveEditorUserId("active-id");

        assertEquals("active-id", auth.getDisplayEditorUserId());
    }

    @Test
    public void transferGapUsesLastEditor() {
        GameAuth auth = authWithCreator();
        auth.setLastEditorUserId("last-id");

        assertEquals("last-id", auth.getDisplayEditorUserId());
    }

    @Test
    public void legacyGameFallsBackToCreator() {
        GameAuth auth = authWithCreator();

        assertEquals("creator-id", auth.getDisplayEditorUserId());
    }

    private static GameAuth authWithCreator() {
        GameAuth auth = new GameAuth();
        auth.setCreatorUserId("creator-id");
        return auth;
    }
}
