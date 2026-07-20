package com.example.rummypulse.ui.join;

import com.example.rummypulse.data.GameAuth;

final class GameEditAccessPolicy {

    private GameEditAccessPolicy() {
    }

    static boolean canSaveGameData(Boolean editAccessGranted,
                                   long activeEditGeneration,
                                   GameAuth auth,
                                   String currentUserId) {
        if (editAccessGranted == null || !editAccessGranted) {
            return false;
        }
        if (activeEditGeneration <= 0 || auth == null || currentUserId == null) {
            return false;
        }
        String activeEditor = auth.getActiveEditorUserId();
        return currentUserId.equals(activeEditor)
                && activeEditGeneration == auth.getPinGenerationOrDefault();
    }
}
