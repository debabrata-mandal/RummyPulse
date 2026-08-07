package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GameAuthEditorIdentityTest {
    @Test
    public void activeEditorTakesPriority() {
        GameAuth auth = authWithCreator();
        auth.setLastEditorUserId("last-id");
        auth.setLastEditorName("Last Editor");
        auth.setActiveEditorUserId("active-id");
        auth.setActiveEditorName("Active Editor");

        assertEquals("active-id", auth.getDisplayEditorUserId());
        assertEquals("Active Editor", auth.getDisplayEditorName());
    }

    @Test
    public void transferGapUsesLastEditor() {
        GameAuth auth = authWithCreator();
        auth.setLastEditorUserId("last-id");
        auth.setLastEditorName("Last Editor");

        assertEquals("last-id", auth.getDisplayEditorUserId());
        assertEquals("Last Editor", auth.getDisplayEditorName());
    }

    @Test
    public void legacyGameFallsBackToCreator() {
        GameAuth auth = authWithCreator();

        assertEquals("creator-id", auth.getDisplayEditorUserId());
        assertEquals("Creator", auth.getDisplayEditorName());
    }

    private static GameAuth authWithCreator() {
        GameAuth auth = new GameAuth();
        auth.setCreatorUserId("creator-id");
        auth.setCreatorName("Creator");
        return auth;
    }
}
