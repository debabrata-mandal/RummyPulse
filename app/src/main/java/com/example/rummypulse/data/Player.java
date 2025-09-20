package com.example.rummypulse.data;

import java.util.List;

public class Player {
    private String name;
    private List<Integer> scores;
    private Integer randomNumber;

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

    // Helper method to calculate total score
    public int getTotalScore() {
        if (scores == null) return 0;
        return scores.stream().mapToInt(Integer::intValue).sum();
    }
}
