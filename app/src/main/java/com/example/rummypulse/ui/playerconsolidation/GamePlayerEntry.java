package com.example.rummypulse.ui.playerconsolidation;

import androidx.annotation.Nullable;

public class GamePlayerEntry {

    private final String entryId;
    private final String gameId;
    private final String gameName;
    private final String playerName;
    @Nullable
    private final String userId;
    private final int playerScore;
    private final double baseGamePoints;
    private final double boardAdjustmentPoints;
    private final double finalGamePoints;

    public GamePlayerEntry(String entryId, String gameId, String gameName, String playerName,
                           @Nullable String userId, int playerScore,
                           double baseGamePoints, double boardAdjustmentPoints, double finalGamePoints) {
        this.entryId = entryId;
        this.gameId = gameId;
        this.gameName = gameName;
        this.playerName = playerName;
        this.userId = userId;
        this.playerScore = playerScore;
        this.baseGamePoints = baseGamePoints;
        this.boardAdjustmentPoints = boardAdjustmentPoints;
        this.finalGamePoints = finalGamePoints;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getGameId() {
        return gameId;
    }

    public String getGameName() {
        return gameName;
    }

    public String getPlayerName() {
        return playerName;
    }

    @Nullable
    public String getUserId() {
        return userId;
    }

    public int getPlayerScore() {
        return playerScore;
    }

    public double getBaseGamePoints() {
        return baseGamePoints;
    }

    public double getBoardAdjustmentPoints() {
        return boardAdjustmentPoints;
    }

    public double getFinalGamePoints() {
        return finalGamePoints;
    }
}
