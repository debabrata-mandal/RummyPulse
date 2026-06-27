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
    private final double grossAmount;
    private final double gstPaid;
    private final double netAmount;

    public GamePlayerEntry(String entryId, String gameId, String gameName, String playerName,
                           @Nullable String userId, int playerScore,
                           double grossAmount, double gstPaid, double netAmount) {
        this.entryId = entryId;
        this.gameId = gameId;
        this.gameName = gameName;
        this.playerName = playerName;
        this.userId = userId;
        this.playerScore = playerScore;
        this.grossAmount = grossAmount;
        this.gstPaid = gstPaid;
        this.netAmount = netAmount;
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

    public double getGrossAmount() {
        return grossAmount;
    }

    public double getGstPaid() {
        return gstPaid;
    }

    public double getNetAmount() {
        return netAmount;
    }
}
