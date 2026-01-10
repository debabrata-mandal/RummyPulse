package com.example.rummypulse.data;

import java.util.List;

public class Player {
    private String name;
    private List<Integer> scores;
    private Integer randomNumber;
    private String userId; // User ID of the player (null for manually added players)
    private Boolean isCreator; // True if this player is the game creator

    public Player() {
        // Default constructor required for Firestore
    }

    public Player(String name, List<Integer> scores, Integer randomNumber) {
        this.name = name;
        this.scores = scores;
        this.randomNumber = randomNumber;
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Integer> getScores() {
        return scores;
    }

    public void setScores(List<Integer> scores) {
        this.scores = scores;
    }

    public Integer getRandomNumber() {
        return randomNumber;
    }

    public void setRandomNumber(Integer randomNumber) {
        this.randomNumber = randomNumber;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Boolean getIsCreator() {
        return isCreator;
    }

    public void setIsCreator(Boolean isCreator) {
        this.isCreator = isCreator;
    }

    // Helper method to calculate total score
    public int getTotalScore() {
        if (scores == null) return 0;
        return scores.stream()
                .mapToInt(score -> score != null && score > 0 ? score : 0)
                .sum();
    }
}
