package com.example.rummypulse.data;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ApprovedGameData {
    private String gameId;
    private int numPlayers;
    private double pointValue;
    private double gstPercent;
    /**
     * Authoritative player results, carrying the account behind each row.
     *
     * <p>Null on games archived before this field existed; those fall back to {@link #playerScores}.
     */
    private List<ApprovedPlayer> players;
    /**
     * @deprecated Legacy name-keyed map from before {@link #players} existed. No longer written on
     *     new approvals; kept only so {@link #resolvePlayers()} can read older archived games.
     */
    @Deprecated
    private Map<String, Integer> playerScores;
    private Timestamp approvedAt;
    private String version;
    private String gstAmount;
    private String gameStatus;
    private String creationDateTime;

    public ApprovedGameData() {
        // Default constructor required for Firestore
    }

    public ApprovedGameData(String gameId, int numPlayers, double pointValue, double gstPercent,
                           Map<String, Integer> playerScores, Timestamp approvedAt, String version,
                           String gstAmount, String gameStatus, String creationDateTime) {
        this(gameId, numPlayers, pointValue, gstPercent, null, playerScores, approvedAt, version,
                gstAmount, gameStatus, creationDateTime);
    }

    public ApprovedGameData(String gameId, int numPlayers, double pointValue, double gstPercent,
                           List<ApprovedPlayer> players, Timestamp approvedAt, String version,
                           String gstAmount, String gameStatus, String creationDateTime) {
        this(gameId, numPlayers, pointValue, gstPercent, players, null, approvedAt, version,
                gstAmount, gameStatus, creationDateTime);
    }

    public ApprovedGameData(String gameId, int numPlayers, double pointValue, double gstPercent,
                           List<ApprovedPlayer> players, Map<String, Integer> playerScores,
                           Timestamp approvedAt, String version,
                           String gstAmount, String gameStatus, String creationDateTime) {
        this.players = players;
        this.gameId = gameId;
        this.numPlayers = numPlayers;
        this.pointValue = pointValue;
        this.gstPercent = gstPercent;
        this.playerScores = playerScores;
        this.approvedAt = approvedAt;
        this.version = version;
        this.gstAmount = gstAmount;
        this.gameStatus = gameStatus;
        this.creationDateTime = creationDateTime;
    }

    // Getters and setters
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public int getNumPlayers() {
        return numPlayers;
    }

    public void setNumPlayers(int numPlayers) {
        this.numPlayers = numPlayers;
    }

    public double getPointValue() {
        return pointValue;
    }

    public void setPointValue(double pointValue) {
        this.pointValue = pointValue;
    }

    public double getGstPercent() {
        return gstPercent;
    }

    public void setGstPercent(double gstPercent) {
        this.gstPercent = gstPercent;
    }

    public List<ApprovedPlayer> getPlayers() {
        return players;
    }

    public void setPlayers(List<ApprovedPlayer> players) {
        this.players = players;
    }

    /** @deprecated Use {@link #resolvePlayers()}. */
    @Deprecated
    public Map<String, Integer> getPlayerScores() {
        return playerScores;
    }

    /** @deprecated Use {@link #setPlayers(List)}. */
    @Deprecated
    public void setPlayerScores(Map<String, Integer> playerScores) {
        this.playerScores = playerScores;
    }

    /**
     * Player results for this game, preferring {@link #players} and falling back to the legacy
     * {@link #playerScores} map for games archived before identity was retained. Rows recovered
     * from the fallback carry no {@code userId}.
     */
    public List<ApprovedPlayer> resolvePlayers() {
        if (players != null && !players.isEmpty()) {
            return players;
        }
        List<ApprovedPlayer> recovered = new ArrayList<>();
        if (playerScores != null) {
            for (Map.Entry<String, Integer> entry : playerScores.entrySet()) {
                recovered.add(new ApprovedPlayer(
                        entry.getKey(),
                        null,
                        entry.getValue() == null ? 0 : entry.getValue()));
            }
        }
        return recovered;
    }

    public Timestamp getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Timestamp approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }


    public String getGstAmount() {
        return gstAmount;
    }

    public void setGstAmount(String gstAmount) {
        this.gstAmount = gstAmount;
    }

    public String getGameStatus() {
        return gameStatus;
    }

    public void setGameStatus(String gameStatus) {
        this.gameStatus = gameStatus;
    }

    public String getCreationDateTime() {
        return creationDateTime;
    }

    public void setCreationDateTime(String creationDateTime) {
        this.creationDateTime = creationDateTime;
    }

    // Helper methods
    public int getTotalGameScore() {
        int total = 0;
        for (ApprovedPlayer player : resolvePlayers()) {
            if (player != null && player.getScore() > 0) {
                total += player.getScore();
            }
        }
        return total;
    }

    public double getGstAmountAsDouble() {
        try {
            return Double.parseDouble(gstAmount);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
