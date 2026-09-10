package com.example.rummypulse.ui.join;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.GameDataSchema;
import com.example.rummypulse.data.Player;

/**
 * View-scoped state for one editable player name.
 *
 * <p>The controller deliberately resolves the player again at commit time so a reordered or
 * regenerated card can never rename whichever player happens to occupy the same list position.
 */
public final class PlayerNameEditController {
    public interface RenameSink {
        void enqueue(String playerId, String normalizedName);
    }

    private final String stablePlayerId;
    private int bindingDepth;
    private boolean userEditPending;

    public PlayerNameEditController(String stablePlayerId) {
        this.stablePlayerId = stablePlayerId;
    }

    public String getPlayerId() {
        return stablePlayerId;
    }

    /** Runs a text binding without treating its watcher callback as a user edit. */
    public void bind(Runnable bindingAction) {
        bindingDepth++;
        try {
            bindingAction.run();
        } finally {
            bindingDepth--;
            userEditPending = false;
        }
    }

    /** Called by the view's watcher; persistence still waits for focus loss or IME Done. */
    public void onTextChanged() {
        if (bindingDepth == 0) {
            userEditPending = true;
        }
    }

    /**
     * Commits a genuine user edit against the latest canonical player state.
     *
     * @return {@code true} only when a rename was sent to the sink
     */
    public boolean commit(GameData latestGameData, CharSequence enteredText, RenameSink sink) {
        if (bindingDepth != 0 || !userEditPending) {
            return false;
        }
        userEditPending = false;

        Player latestPlayer = GameDataSchema.findPlayer(latestGameData, stablePlayerId);
        if (latestPlayer == null || clean(latestPlayer.getUserId()) != null) {
            return false;
        }
        String enteredName = clean(enteredText == null ? null : enteredText.toString());
        String canonicalName = clean(latestPlayer.getName());
        if (enteredName == null || enteredName.equals(canonicalName)) {
            return false;
        }
        sink.enqueue(stablePlayerId, enteredName);
        return true;
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
