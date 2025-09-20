package com.example.rummypulse.data;

import com.google.firebase.Timestamp;
import java.util.List;

public class GameData {
    private int numPlayers;
    private double pointValue;
    private double gstPercent;
    private List<Player> players;
    private Timestamp lastUpdated;
    private String version;

    public GameData() {
        // Default constructor required for Firestore
    }

    public GameData(int numPlayers, double pointValue, double gstPercent, List<Player> players, Timestamp lastUpdated, String version) {
        this.numPlayers = numPlayers;
        this.pointValue = pointValue;
        this.gstPercent = gstPercent;
        this.players = players;
        this.lastUpdated = lastUpdated;
        this.version = version;
    }

    // Getters and setters
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

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public Timestamp getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Timestamp lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    // Helper methods
    public int getTotalScore() {
        if (players == null) return 0;
        return players.stream().mapToInt(Player::getTotalScore).sum();
    }

    public double getGstAmount() {
        if (players == null || players.isEmpty()) return 0.0;
        
        // Calculate total of all scores
        int totalAllScores = getTotalScore();
        
        // Calculate GST only for winning players (those with positive gross amounts)
        double totalGstCollected = 0.0;
        
        for (Player player : players) {
            int playerScore = player.getTotalScore();
            // Formula: (Total of all scores - Player's score × Number of players) × Point value
            double grossAmount = (totalAllScores - playerScore * numPlayers) * pointValue;
            
            // GST is only paid by winners (those with positive gross amount)
            if (grossAmount > 0) {
                double gstPaid = (grossAmount * gstPercent) / 100.0;
                totalGstCollected += gstPaid;
            }
        }
        
        return totalGstCollected;
    }

    public String getGameStatus() {
        // Determine game status based on some logic
        // For now, we'll consider it "Active" if there are players with scores
        if (players == null || players.isEmpty()) {
            return "Not Started";
        }
        
        boolean hasScores = players.stream().anyMatch(player -> 
            player.getScores() != null && !player.getScores().isEmpty());
        
        return hasScores ? "Active" : "Completed";
    }
}
