package com.example.rummypulse.data;

import com.google.firebase.Timestamp;
import java.util.Collections;
import java.util.List;

public class ApprovedGameData {
    private Integer schemaVersion;
    private String gameId;
    private int numPlayers;
    private double gamePointFactor;
    private double boardAdjustmentPercent;
    /** Authoritative schema-v3 player results, carrying the account behind each row. */
    private List<ApprovedPlayer> players;
    private Timestamp approvedAt;
    private String version;
    private double boardPoints;
    private String gameStatus;
    private String creationDateTime;

    public ApprovedGameData() {
        // Default constructor required for Firestore
    }

    public ApprovedGameData(String gameId, int numPlayers, double gamePointFactor, double boardAdjustmentPercent,
                           List<ApprovedPlayer> players, Timestamp approvedAt, String version,
                           double boardPoints, String gameStatus, String creationDateTime) {
        this.players = players;
        this.schemaVersion = GameDataSchema.CURRENT_VERSION;
        this.gameId = gameId;
        this.numPlayers = numPlayers;
        this.gamePointFactor = gamePointFactor;
        this.boardAdjustmentPercent = boardAdjustmentPercent;
        this.approvedAt = approvedAt;
        this.version = version;
        this.boardPoints = boardPoints;
        this.gameStatus = gameStatus;
        this.creationDateTime = creationDateTime;
    }

    // Getters and setters
    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

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

    public double getGamePointFactor() {
        return gamePointFactor;
    }

    public void setGamePointFactor(double gamePointFactor) {
        this.gamePointFactor = gamePointFactor;
    }

    public double getBoardAdjustmentPercent() {
        return boardAdjustmentPercent;
    }

    public void setBoardAdjustmentPercent(double boardAdjustmentPercent) {
        this.boardAdjustmentPercent = boardAdjustmentPercent;
    }

    public List<ApprovedPlayer> getPlayers() {
        return players;
    }

    public void setPlayers(List<ApprovedPlayer> players) {
        this.players = players;
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


    public double getBoardPoints() {
        return boardPoints;
    }

    public void setBoardPoints(double boardPoints) {
        this.boardPoints = boardPoints;
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
        for (ApprovedPlayer player : players == null ? Collections.<ApprovedPlayer>emptyList() : players) {
            if (player != null && player.getScore() > 0) {
                total += player.getScore();
            }
        }
        return total;
    }

    public double getBoardPointsAsDouble() {
        return boardPoints;
    }
}
