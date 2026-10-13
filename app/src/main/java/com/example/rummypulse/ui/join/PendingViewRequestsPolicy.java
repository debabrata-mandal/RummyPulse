package com.example.rummypulse.ui.join;

import com.example.rummypulse.data.GameAuth;

/**
 * Decides who may manage a game's view requests and when the pending-requests listener must be
 * replaced. Every restart re-runs both Firestore queries, so unchanged inputs keep the listener.
 */
final class PendingViewRequestsPolicy {

    enum Access {
        NONE,
        ADMIN,
        ACTIVE_EDITOR
    }

    private PendingViewRequestsPolicy() {
    }

    static Access accessFor(boolean isAppAdmin,
                            Boolean editAccessGranted,
                            GameAuth auth,
                            String currentUserId) {
        if (isAppAdmin) {
            return Access.ADMIN;
        }
        if (!Boolean.TRUE.equals(editAccessGranted) || auth == null || currentUserId == null) {
            return Access.NONE;
        }
        return currentUserId.equals(auth.getActiveEditorUserId())
                ? Access.ACTIVE_EDITOR
                : Access.NONE;
    }

    static boolean shouldRestartListener(String listeningGameId,
                                         Access listeningAccess,
                                         String requestedGameId,
                                         Access requestedAccess) {
        if (requestedGameId == null || requestedAccess == Access.NONE) {
            return false;
        }
        return !requestedGameId.equals(listeningGameId) || requestedAccess != listeningAccess;
    }
}
