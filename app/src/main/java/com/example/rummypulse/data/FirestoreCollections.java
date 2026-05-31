package com.example.rummypulse.data;

/**
 * Single source of truth for Firestore top-level collection names.
 */
public final class FirestoreCollections {

    public static final String APP_USER = "appUser_v2";
    public static final String GAMES = "games_v2";
    public static final String GAME_DATA = "gameData_v2";
    public static final String APPROVED_GAMES = "approvedGames_v2";
    public static final String APPROVED_GAMES_REPORT = "approvedGamesReport_v2";
    public static final String GAME_DEFAULTS = "gameDefaults_v2";

    private FirestoreCollections() {
    }
}
