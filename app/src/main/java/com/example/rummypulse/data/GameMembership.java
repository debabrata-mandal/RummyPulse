package com.example.rummypulse.data;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Denormalized participation index stored on {@code games_v2}.
 *
 * <p>The dashboard needs to answer "which games am I in?" without reading {@code gameData_v2},
 * which most users cannot read anyway. Mirroring the members onto the game document lets the
 * dashboard use a single {@code whereArrayContains} query instead of loading every game in the
 * system and filtering client-side.
 */
public final class GameMembership {

    /** Field name on {@code games_v2}. */
    public static final String FIELD = "memberUserIds";

    private GameMembership() {
    }

    /**
     * Everyone entitled to see the game on their dashboard: linked players plus the creator and the
     * current editor, who may not hold a player row of their own.
     */
    public static List<String> resolve(
            GameData gameData, String creatorUserId, String activeEditorUserId) {
        Set<String> members = new LinkedHashSet<>();
        if (!TextUtils.isEmpty(creatorUserId)) {
            members.add(creatorUserId);
        }
        if (!TextUtils.isEmpty(activeEditorUserId)) {
            members.add(activeEditorUserId);
        }
        if (gameData != null && gameData.getPlayers() != null) {
            for (Player player : gameData.getPlayers()) {
                if (player != null && !TextUtils.isEmpty(player.getUserId())) {
                    members.add(player.getUserId());
                }
            }
        }
        return new ArrayList<>(members);
    }

    /** True when {@code stored} already matches {@code expected}, ignoring order. */
    public static boolean matches(Collection<String> stored, Collection<String> expected) {
        if (stored == null) {
            return expected == null || expected.isEmpty();
        }
        return new LinkedHashSet<>(stored).equals(new LinkedHashSet<>(expected));
    }
}
