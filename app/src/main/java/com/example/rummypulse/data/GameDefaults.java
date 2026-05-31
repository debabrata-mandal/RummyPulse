package com.example.rummypulse.data;

import com.google.firebase.Timestamp;

/**
 * System-wide defaults stored at {@code gameDefaults_v2/config} in Firestore.
 */
public class GameDefaults {

    public static final double FALLBACK_DEFAULT_POINT_VALUE = 0.15;
    public static final double FALLBACK_DEFAULT_GST_PERCENT = 25.0;
    public static final long FALLBACK_MID_GAME_INCREMENT = 2L;
    public static final boolean FALLBACK_DISPLAY_INTERMEDIATE_CALCULATION = true;

    private Double defaultPointValue;
    private Double defaultGstPercent;
    private Long defaultMidGameNewPlayerScoreIncrement;
    private Boolean displayIntermediateCalculation;
    private Timestamp updatedAt;
    private String updatedByUserId;
    private String updatedByUserName;

    public GameDefaults() {
    }

    public double getDefaultPointValue() {
        return defaultPointValue != null ? defaultPointValue : FALLBACK_DEFAULT_POINT_VALUE;
    }

    public void setDefaultPointValue(Double defaultPointValue) {
        this.defaultPointValue = defaultPointValue;
    }

    public double getDefaultGstPercent() {
        return defaultGstPercent != null ? defaultGstPercent : FALLBACK_DEFAULT_GST_PERCENT;
    }

    public void setDefaultGstPercent(Double defaultGstPercent) {
        this.defaultGstPercent = defaultGstPercent;
    }

    public long getDefaultMidGameNewPlayerScoreIncrement() {
        return defaultMidGameNewPlayerScoreIncrement != null
                ? defaultMidGameNewPlayerScoreIncrement
                : FALLBACK_MID_GAME_INCREMENT;
    }

    public void setDefaultMidGameNewPlayerScoreIncrement(Long defaultMidGameNewPlayerScoreIncrement) {
        this.defaultMidGameNewPlayerScoreIncrement = defaultMidGameNewPlayerScoreIncrement;
    }

    /** When true, standings show net amounts while rounds are in progress; when false, amounts appear only after the game is complete. */
    public boolean isDisplayIntermediateCalculation() {
        return displayIntermediateCalculation == null || displayIntermediateCalculation;
    }

    public void setDisplayIntermediateCalculation(Boolean displayIntermediateCalculation) {
        this.displayIntermediateCalculation = displayIntermediateCalculation;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(String updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }

    public String getUpdatedByUserName() {
        return updatedByUserName;
    }

    public void setUpdatedByUserName(String updatedByUserName) {
        this.updatedByUserName = updatedByUserName;
    }

    /** Resolved values for UI and game logic; never null numeric fields. */
    public static GameDefaults resolvedFromFirestoreBean(GameDefaults fromDb) {
        GameDefaults g = new GameDefaults();
        if (fromDb == null) {
            g.setDefaultPointValue(FALLBACK_DEFAULT_POINT_VALUE);
            g.setDefaultGstPercent(FALLBACK_DEFAULT_GST_PERCENT);
            g.setDefaultMidGameNewPlayerScoreIncrement(FALLBACK_MID_GAME_INCREMENT);
            g.setDisplayIntermediateCalculation(FALLBACK_DISPLAY_INTERMEDIATE_CALCULATION);
            return g;
        }
        g.setDefaultPointValue(fromDb.defaultPointValue != null && fromDb.defaultPointValue > 0
                ? fromDb.defaultPointValue : FALLBACK_DEFAULT_POINT_VALUE);
        g.setDefaultGstPercent(fromDb.defaultGstPercent != null
                ? clampGst(fromDb.defaultGstPercent) : FALLBACK_DEFAULT_GST_PERCENT);
        long inc = fromDb.defaultMidGameNewPlayerScoreIncrement != null
                ? fromDb.defaultMidGameNewPlayerScoreIncrement : FALLBACK_MID_GAME_INCREMENT;
        g.setDefaultMidGameNewPlayerScoreIncrement(Math.max(0L, inc));
        g.setDisplayIntermediateCalculation(fromDb.displayIntermediateCalculation != null
                ? fromDb.displayIntermediateCalculation
                : FALLBACK_DISPLAY_INTERMEDIATE_CALCULATION);
        g.setUpdatedAt(fromDb.updatedAt);
        g.setUpdatedByUserId(fromDb.updatedByUserId);
        g.setUpdatedByUserName(fromDb.updatedByUserName);
        return g;
    }

    private static double clampGst(double v) {
        if (v < 0) {
            return 0;
        }
        if (v > 100) {
            return 100;
        }
        return v;
    }
}
