package com.example.rummypulse.data;

/**
 * Applies only identity fields to a player in an already-current game snapshot.
 */
public final class PlayerMappingPatch {

    private PlayerMappingPatch() {
    }

    public static GameData apply(
            GameData latestGameData,
            int playerIndex,
            String playerName,
            String userId) {
        if (latestGameData == null || latestGameData.getPlayers() == null
                || playerIndex < 0 || playerIndex >= latestGameData.getPlayers().size()) {
            throw new IllegalArgumentException("The player list changed.");
        }
        if (playerName == null || playerName.trim().isEmpty()
                || userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("Player mapping is incomplete.");
        }
        Player player = latestGameData.getPlayers().get(playerIndex);
        player.setName(playerName);
        player.setUserId(userId);
        return latestGameData;
    }
}
