package com.example.rummypulse.data;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/**
 * System-wide defaults stored at {@code gameDefaults_v2/config} in Firestore.
 */
public class GameDefaults {

    public static final double FALLBACK_DEFAULT_GAME_POINT_FACTOR = 0.15;
    public static final double FALLBACK_DEFAULT_BOARD_ADJUSTMENT_PERCENT = 25.0;
    public static final long FALLBACK_MID_GAME_INCREMENT = 2L;
    public static final boolean FALLBACK_SHOW_LIVE_GAME_POINTS = true;
    public static final boolean FALLBACK_SHOW_DASHBOARD_APPROVAL_COUNTS = true;
    public static final boolean FALLBACK_SHOW_DASHBOARD_LEADERBOARD = true;
    public static final boolean FALLBACK_SHOW_DASHBOARD_LEADERBOARD_GAME_POINTS = true;

    private Integer schemaVersion;
    private Double defaultGamePointFactor;
    private Double defaultBoardAdjustmentPercent;
    private Long defaultMidGameNewPlayerScoreIncrement;
    private Boolean showLiveGamePoints;
    private Boolean showDashboardApprovalCounts;
    private Boolean showDashboardLeaderboard;
    private Boolean showDashboardLeaderboardGamePoints;
    private Timestamp updatedAt;
    private String updatedByUserId;
    private String updatedByUserName;

    public GameDefaults() {
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public double getDefaultGamePointFactor() {
        return defaultGamePointFactor != null ? defaultGamePointFactor : FALLBACK_DEFAULT_GAME_POINT_FACTOR;
    }

    public void setDefaultGamePointFactor(Double defaultGamePointFactor) {
        this.defaultGamePointFactor = defaultGamePointFactor;
    }

    public double getDefaultBoardAdjustmentPercent() {
        return defaultBoardAdjustmentPercent != null ? defaultBoardAdjustmentPercent : FALLBACK_DEFAULT_BOARD_ADJUSTMENT_PERCENT;
    }

    public void setDefaultBoardAdjustmentPercent(Double defaultBoardAdjustmentPercent) {
        this.defaultBoardAdjustmentPercent = defaultBoardAdjustmentPercent;
    }

    public long getDefaultMidGameNewPlayerScoreIncrement() {
        return defaultMidGameNewPlayerScoreIncrement != null
                ? defaultMidGameNewPlayerScoreIncrement
                : FALLBACK_MID_GAME_INCREMENT;
    }

    public void setDefaultMidGameNewPlayerScoreIncrement(Long defaultMidGameNewPlayerScoreIncrement) {
        this.defaultMidGameNewPlayerScoreIncrement = defaultMidGameNewPlayerScoreIncrement;
    }

    /** When true, standings show live Game Points; otherwise they appear only after the game ends. */
    public boolean isShowLiveGamePoints() {
        return showLiveGamePoints == null || showLiveGamePoints;
    }

    public void setShowLiveGamePoints(Boolean showLiveGamePoints) {
        this.showLiveGamePoints = showLiveGamePoints;
    }

    /** When true, dashboard game cards show pending/approved/rejected view request counts to managers. */
    public boolean isShowDashboardApprovalCounts() {
        return showDashboardApprovalCounts == null || showDashboardApprovalCounts;
    }

    public void setShowDashboardApprovalCounts(Boolean showDashboardApprovalCounts) {
        this.showDashboardApprovalCounts = showDashboardApprovalCounts;
    }

    /** When true, the dashboard shows the top/bottom performer leaderboard. */
    public boolean isShowDashboardLeaderboard() {
        return showDashboardLeaderboard == null || showDashboardLeaderboard;
    }

    public void setShowDashboardLeaderboard(Boolean showDashboardLeaderboard) {
        this.showDashboardLeaderboard = showDashboardLeaderboard;
    }

    /**
     * When true, leaderboard rows show each player's final Game Points. When false the ranking is
     * still by final Game Points, but the figures stay hidden.
     */
    public boolean isShowDashboardLeaderboardGamePoints() {
        return showDashboardLeaderboardGamePoints == null || showDashboardLeaderboardGamePoints;
    }

    public void setShowDashboardLeaderboardGamePoints(Boolean showDashboardLeaderboardGamePoints) {
        this.showDashboardLeaderboardGamePoints = showDashboardLeaderboardGamePoints;
    }

    /**
     * Whether ranked final Game Points may be shown anywhere, on the dashboard donut or the player
     * ranking screen.
     *
     * <p>Both switches must be on. The Game Points switch is disabled in the admin UI while the
     * leaderboard switch is off, so it can be left stranded at {@code true}; honouring it alone
     * would show figures an admin believes are switched off.
     */
    @Exclude
    public boolean isLeaderboardGamePointsVisible() {
        return isShowDashboardLeaderboard() && isShowDashboardLeaderboardGamePoints();
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
            g.setSchemaVersion(GameDataSchema.CURRENT_VERSION);
            g.setDefaultGamePointFactor(FALLBACK_DEFAULT_GAME_POINT_FACTOR);
            g.setDefaultBoardAdjustmentPercent(FALLBACK_DEFAULT_BOARD_ADJUSTMENT_PERCENT);
            g.setDefaultMidGameNewPlayerScoreIncrement(FALLBACK_MID_GAME_INCREMENT);
            g.setShowLiveGamePoints(FALLBACK_SHOW_LIVE_GAME_POINTS);
            g.setShowDashboardApprovalCounts(FALLBACK_SHOW_DASHBOARD_APPROVAL_COUNTS);
            g.setShowDashboardLeaderboard(FALLBACK_SHOW_DASHBOARD_LEADERBOARD);
            g.setShowDashboardLeaderboardGamePoints(FALLBACK_SHOW_DASHBOARD_LEADERBOARD_GAME_POINTS);
            return g;
        }
        g.setSchemaVersion(fromDb.schemaVersion);
        g.setDefaultGamePointFactor(fromDb.defaultGamePointFactor != null && fromDb.defaultGamePointFactor > 0
                ? fromDb.defaultGamePointFactor : FALLBACK_DEFAULT_GAME_POINT_FACTOR);
        g.setDefaultBoardAdjustmentPercent(fromDb.defaultBoardAdjustmentPercent != null
                ? clampBoardAdjustment(fromDb.defaultBoardAdjustmentPercent) : FALLBACK_DEFAULT_BOARD_ADJUSTMENT_PERCENT);
        long inc = fromDb.defaultMidGameNewPlayerScoreIncrement != null
                ? fromDb.defaultMidGameNewPlayerScoreIncrement : FALLBACK_MID_GAME_INCREMENT;
        g.setDefaultMidGameNewPlayerScoreIncrement(Math.max(0L, inc));
        g.setShowLiveGamePoints(fromDb.showLiveGamePoints != null
                ? fromDb.showLiveGamePoints
                : FALLBACK_SHOW_LIVE_GAME_POINTS);
        g.setShowDashboardApprovalCounts(fromDb.showDashboardApprovalCounts != null
                ? fromDb.showDashboardApprovalCounts
                : FALLBACK_SHOW_DASHBOARD_APPROVAL_COUNTS);
        g.setShowDashboardLeaderboard(fromDb.showDashboardLeaderboard != null
                ? fromDb.showDashboardLeaderboard
                : FALLBACK_SHOW_DASHBOARD_LEADERBOARD);
        g.setShowDashboardLeaderboardGamePoints(fromDb.showDashboardLeaderboardGamePoints != null
                ? fromDb.showDashboardLeaderboardGamePoints
                : FALLBACK_SHOW_DASHBOARD_LEADERBOARD_GAME_POINTS);
        g.setUpdatedAt(fromDb.updatedAt);
        g.setUpdatedByUserId(fromDb.updatedByUserId);
        g.setUpdatedByUserName(fromDb.updatedByUserName);
        return g;
    }

    private static double clampBoardAdjustment(double v) {
        if (v < 0) {
            return 0;
        }
        if (v > 100) {
            return 100;
        }
        return v;
    }
}
