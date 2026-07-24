package com.example.rummypulse.data;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory score entry for one round. Scores are copied into a new {@link GameData}
 * instance only after every required player has been reviewed.
 */
public final class RoundScoreDraft {
    private final int round1Based;
    private final boolean correctionMode;
    private final int[] scores;
    private final boolean[] reviewed;

    private RoundScoreDraft(int round1Based, int playerCount, boolean correctionMode) {
        if (round1Based < 1 || round1Based > 10) {
            throw new IllegalArgumentException("Round must be between 1 and 10.");
        }
        if (playerCount <= 0) {
            throw new IllegalArgumentException("A round requires at least one player.");
        }
        this.round1Based = round1Based;
        this.correctionMode = correctionMode;
        this.scores = new int[playerCount];
        this.reviewed = new boolean[playerCount];
        java.util.Arrays.fill(scores, -1);
    }

    public static RoundScoreDraft start(GameData gameData, int round1Based,
            boolean correctionMode) {
        if (gameData == null || gameData.getPlayers() == null
                || gameData.getPlayers().isEmpty()) {
            throw new IllegalArgumentException("Game data must contain players.");
        }
        RoundScoreDraft draft = new RoundScoreDraft(round1Based,
                gameData.getPlayers().size(), correctionMode);
        for (int i = 0; i < gameData.getPlayers().size(); i++) {
            Player player = gameData.getPlayers().get(i);
            Integer existing = existingScore(player, round1Based);
            if (existing != null && existing >= 0) {
                draft.scores[i] = existing;
                // Existing scores satisfy a normal partially-entered round. Corrections
                // intentionally require every player to be reviewed.
                draft.reviewed[i] = !correctionMode;
            }
        }
        return draft;
    }

    public int getRound1Based() {
        return round1Based;
    }

    public int getPlayerCount() {
        return scores.length;
    }

    public boolean isCorrectionMode() {
        return correctionMode;
    }

    public int getScore(int playerIndex) {
        requirePlayerIndex(playerIndex);
        return scores[playerIndex];
    }

    public void recordScore(int playerIndex, int score) {
        requirePlayerIndex(playerIndex);
        if (score < 0) {
            throw new IllegalArgumentException("Score cannot be negative.");
        }
        scores[playerIndex] = score;
        reviewed[playerIndex] = true;
    }

    public int findNextUnreviewed(int startIndex) {
        for (int i = Math.max(0, startIndex); i < reviewed.length; i++) {
            if (!reviewed[i]) {
                return i;
            }
        }
        return -1;
    }

    public boolean isComplete() {
        return findNextUnreviewed(0) < 0;
    }

    public String serialize() {
        StringBuilder encoded = new StringBuilder("1|")
                .append(round1Based).append('|')
                .append(correctionMode ? '1' : '0').append('|');
        for (int i = 0; i < scores.length; i++) {
            if (i > 0) {
                encoded.append(',');
            }
            encoded.append(scores[i]);
        }
        encoded.append('|');
        for (int i = 0; i < reviewed.length; i++) {
            if (i > 0) {
                encoded.append(',');
            }
            encoded.append(reviewed[i] ? '1' : '0');
        }
        return encoded.toString();
    }

    public static RoundScoreDraft deserialize(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Draft is missing.");
        }
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 5 || !"1".equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported draft format.");
        }
        try {
            int round = Integer.parseInt(parts[1]);
            boolean correction = "1".equals(parts[2]);
            String[] savedScores = parts[3].split(",", -1);
            String[] savedReviewed = parts[4].split(",", -1);
            if (savedScores.length == 0 || savedScores.length != savedReviewed.length) {
                throw new IllegalArgumentException("Draft player data is invalid.");
            }
            RoundScoreDraft draft =
                    new RoundScoreDraft(round, savedScores.length, correction);
            for (int i = 0; i < savedScores.length; i++) {
                draft.scores[i] = Integer.parseInt(savedScores[i]);
                draft.reviewed[i] = "1".equals(savedReviewed[i]);
                if (draft.scores[i] < -1
                        || (draft.reviewed[i] && draft.scores[i] < 0)) {
                    throw new IllegalArgumentException("Draft score is invalid.");
                }
            }
            return draft;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Draft contains invalid numbers.", error);
        }
    }

    public GameData applyToCopy(GameData source) {
        if (!isComplete()) {
            throw new IllegalStateException("All player scores must be reviewed first.");
        }
        if (source == null || source.getPlayers() == null
                || source.getPlayers().size() != scores.length) {
            throw new IllegalArgumentException("Player list changed while entering scores.");
        }

        List<Player> copiedPlayers = new ArrayList<>(scores.length);
        for (int i = 0; i < scores.length; i++) {
            Player original = source.getPlayers().get(i);
            Player copy = copyPlayer(original);
            List<Integer> copiedScores = copy.getScores();
            while (copiedScores.size() < 10) {
                copiedScores.add(-1);
            }
            copiedScores.set(round1Based - 1, scores[i]);
            copiedPlayers.add(copy);
        }

        GameData copy = new GameData();
        copy.setNumPlayers(copiedPlayers.size());
        copy.setPointValue(source.getPointValue());
        copy.setGstPercent(source.getGstPercent());
        copy.setPlayers(copiedPlayers);
        copy.setLastUpdated(source.getLastUpdated());
        copy.setVersion(source.getVersion());
        copy.setGameStatus(source.getGameStatus());
        copy.setMidGameJoinActiveRound(source.getMidGameJoinActiveRound());
        copy.setMidGameJoinBackfillScore(source.getMidGameJoinBackfillScore());
        return copy;
    }

    private static Integer existingScore(Player player, int round1Based) {
        if (player == null || player.getScores() == null
                || player.getScores().size() < round1Based) {
            return null;
        }
        return player.getScores().get(round1Based - 1);
    }

    private static Player copyPlayer(Player original) {
        Player copy = new Player();
        copy.setName(original.getName());
        copy.setScores(original.getScores() == null
                ? new ArrayList<>()
                : new ArrayList<>(original.getScores()));
        copy.setRandomNumber(original.getRandomNumber());
        copy.setUserId(original.getUserId());
        copy.setIsCreator(original.getIsCreator());
        return copy;
    }

    private void requirePlayerIndex(int playerIndex) {
        if (playerIndex < 0 || playerIndex >= scores.length) {
            throw new IndexOutOfBoundsException("Invalid player index.");
        }
    }
}
