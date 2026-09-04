package com.example.rummypulse.data;

/**
 * One player's result inside an archived game.
 *
 * <p>Approval used to keep only a {@code name -> score} map, which lost the account behind each row
 * and silently merged two players sharing a name. Keeping identity and score together fixes both:
 * statistics can be credited to the right account without guessing, and same-named players stay
 * separate.
 *
 * <p>{@link #userId} is null for manual players who were never linked to an account. The name is
 * retained regardless, because it is what the archive displays.
 */
public class ApprovedPlayer {

    private String name;
    private String userId;
    private int score;

    public ApprovedPlayer() {
        // Default constructor required for Firestore
    }

    public ApprovedPlayer(String name, String userId, int score) {
        this.name = name;
        this.userId = userId;
        this.score = score;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }
}
