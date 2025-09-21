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
    private String gameStatus;

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
        this.gameStatus = calculateCurrentRound();
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

    public void setGameStatus(String gameStatus) {
        this.gameStatus = gameStatus;
    }

    // Helper methods
    public int getTotalScore() {
        if (players == null || players.isEmpty()) return 0;
        
        // Calculate total score as sum of all individual player scores
        // This represents the total points scored across all players in all rounds
        // Note: -1 values are treated as 0 (not counted in total)
        int totalScore = 0;
        for (Player player : players) {
            if (player.getScores() != null) {
                for (Integer score : player.getScores()) {
                    if (score != null && score > 0) { // Only count positive scores, -1 treated as 0
                        totalScore += score;
                    }
                }
            }
        }
        return totalScore;
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
        // Return stored status if available, otherwise calculate it
        if (gameStatus != null && !gameStatus.isEmpty()) {
            System.out.println("Using stored gameStatus: " + gameStatus);
            return gameStatus;
        }
        
        if (players == null || players.isEmpty()) {
            System.out.println("No players, returning Not Started");
            return "Not Started";
        }
        
        // Calculate current round based on completed entries (similar to web app logic)
        String calculatedStatus = calculateCurrentRound();
        System.out.println("Calculated gameStatus: " + calculatedStatus + " for " + players.size() + " players");
        return calculatedStatus;
    }
    
    private String calculateCurrentRound() {
        if (players == null || players.isEmpty()) {
            return "Not Started";
        }
        
        int numPlayers = players.size();
        System.out.println("Calculating round for " + numPlayers + " players");
        
        // Check rounds 1-10 (exactly like web app logic)
        for (int round = 1; round <= 10; round++) {
            int completedPlayers = 0;
            
            // Check if all players have entered scores for this round
            for (Player player : players) {
                if (player.getScores() != null && player.getScores().size() >= round) {
                    // Check if this player has a valid score for this round (not null, not -1)
                    Integer score = player.getScores().get(round - 1);
                    if (score != null && score != -1) {
                        completedPlayers++;
                    }
                }
            }
            
            System.out.println("Round " + round + ": " + completedPlayers + "/" + numPlayers + " players completed");
            
            // If not all players completed this round, this is the current round
            if (completedPlayers < numPlayers) {
                System.out.println("Returning R" + round);
                return "R" + round;
            }
        }
        
        // All rounds completed by all players
        System.out.println("All rounds completed, returning Completed");
        return "Completed";
    }
}
