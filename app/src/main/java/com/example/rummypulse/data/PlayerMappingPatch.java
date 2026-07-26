package com.example.rummypulse.data;

/**
 * Applies only identity fields to a player in an already-current game snapshot.
 */
public final class PlayerMappingPatch {

    private PlayerMappingPatch() {
    }

    public static int findPlayerIndex(
            GameData latestGameData,
            int previousIndex,
            String previousName,
            Integer randomNumber,
            String previousUserId) {
        if (latestGameData == null || latestGameData.getPlayers() == null) {
            return -1;
        }
        if (previousUserId != null && !previousUserId.trim().isEmpty()) {
            for (int i = 0; i < latestGameData.getPlayers().size(); i++) {
                if (previousUserId.equals(latestGameData.getPlayers().get(i).getUserId())) {
                    return i;
                }
            }
        }
        if (randomNumber != null) {
            for (int i = 0; i < latestGameData.getPlayers().size(); i++) {
                if (randomNumber.equals(
                        latestGameData.getPlayers().get(i).getRandomNumber())) {
                    return i;
                }
            }
        }
        if (previousIndex >= 0 && previousIndex < latestGameData.getPlayers().size()
                && previousName != null
                && previousName.equals(
                        latestGameData.getPlayers().get(previousIndex).getName())) {
            return previousIndex;
        }
        int nameMatch = -1;
        for (int i = 0; i < latestGameData.getPlayers().size(); i++) {
            if (previousName != null
                    && previousName.equals(latestGameData.getPlayers().get(i).getName())) {
                if (nameMatch >= 0) {
                    return -1;
                }
                nameMatch = i;
            }
        }
        return nameMatch;
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

    public static GameData clear(GameData latestGameData, int playerIndex) {
        if (latestGameData == null || latestGameData.getPlayers() == null
                || playerIndex < 0 || playerIndex >= latestGameData.getPlayers().size()) {
            throw new IllegalArgumentException("The player list changed.");
        }
        latestGameData.getPlayers().get(playerIndex).setUserId(null);
        return latestGameData;
    }
}
