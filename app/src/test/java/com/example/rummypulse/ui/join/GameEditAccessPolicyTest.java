package com.example.rummypulse.ui.join;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.rummypulse.data.GameAuth;

import org.junit.Test;

public class GameEditAccessPolicyTest {

    @Test
    public void viewerCannotSaveEvenWhenGameGenerationDefaultsToOne() {
        GameAuth auth = auth("editor-uid", null);

        boolean canSave = GameEditAccessPolicy.canSaveGameData(
                false,
                1L,
                auth,
                "viewer-uid");

        assertFalse(canSave);
    }

    @Test
    public void missingActiveEditGenerationCannotSave() {
        GameAuth auth = auth("editor-uid", 1L);

        boolean canSave = GameEditAccessPolicy.canSaveGameData(
                true,
                0L,
                auth,
                "editor-uid");

        assertFalse(canSave);
    }

    @Test
    public void staleGenerationCannotSave() {
        GameAuth auth = auth("editor-uid", 2L);

        boolean canSave = GameEditAccessPolicy.canSaveGameData(
                true,
                1L,
                auth,
                "editor-uid");

        assertFalse(canSave);
    }

    @Test
    public void wrongActiveEditorCannotSave() {
        GameAuth auth = auth("other-editor", 1L);

        boolean canSave = GameEditAccessPolicy.canSaveGameData(
                true,
                1L,
                auth,
                "editor-uid");

        assertFalse(canSave);
    }

    @Test
    public void activeEditorWithCurrentGenerationCanSave() {
        GameAuth auth = auth("editor-uid", 3L);

        boolean canSave = GameEditAccessPolicy.canSaveGameData(
                true,
                3L,
                auth,
                "editor-uid");

        assertTrue(canSave);
    }

    private static GameAuth auth(String activeEditorUserId, Long pinGeneration) {
        GameAuth auth = new GameAuth();
        auth.setActiveEditorUserId(activeEditorUserId);
        auth.setPinGeneration(pinGeneration);
        return auth;
    }
}
