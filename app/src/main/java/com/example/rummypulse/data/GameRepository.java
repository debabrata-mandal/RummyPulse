package com.example.rummypulse.data;

import android.content.Context;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class GameRepository {
    private static GameRepository dashboardInstance;

    /** Shared instance used by Dashboard and Join game so in-memory rows stay in sync. */
    public static synchronized GameRepository getDashboardInstance() {
        if (dashboardInstance == null) {
            dashboardInstance = new GameRepository();
        }
        return dashboardInstance;
    }

    private FirebaseFirestore db;
    private final GameViewApprovalRepository viewApprovalRepository;
    private MutableLiveData<List<GameItem>> gameItemsLiveData;
    private MutableLiveData<String> errorLiveData;
    private MutableLiveData<Double> totalApprovedGstLiveData;
    private MutableLiveData<Integer> approvedGamesCountLiveData;
    private MutableLiveData<List<MonthlyPointValueReport>> reportsSummariesLiveData;
    
    // Firestore listeners for real-time updates (used by Dashboard)
    private com.google.firebase.firestore.ListenerRegistration gamesListener;
    private com.google.firebase.firestore.ListenerRegistration approvedGamesListener;
    private java.util.Map<String, com.google.firebase.firestore.ListenerRegistration> gameDataListeners = new java.util.HashMap<>();
    
    // Store game items and game IDs order for maintaining consistency
    private java.util.Map<String, GameItem> gameItemsMap = new java.util.HashMap<>();
    private List<String> gameIdsOrder = new ArrayList<>();
    private java.util.Map<String, String> myViewApprovalStatusByGame = new java.util.HashMap<>();
    private com.google.firebase.firestore.ListenerRegistration myViewApprovalsListener;
    /** When JoinGame saves locally, remote refresh must not overwrite until Firestore catches up. */
    private final Map<String, Long> localDashboardUpdatedAtMs = new HashMap<>();
    
    // Track seen games
    private Set<String> seenGameIds = new HashSet<>();
    private Context appContext;

    public GameRepository() {
        db = FirebaseFirestore.getInstance();
        viewApprovalRepository = new GameViewApprovalRepository();
        gameItemsLiveData = new MutableLiveData<>();
        errorLiveData = new MutableLiveData<>();
        totalApprovedGstLiveData = new MutableLiveData<>();
        approvedGamesCountLiveData = new MutableLiveData<>();
        reportsSummariesLiveData = new MutableLiveData<>();
    }
    
    /**
     * Set application context
     */
    public void setContext(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public LiveData<List<GameItem>> getGameItems() {
        return gameItemsLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<Double> getTotalApprovedGst() {
        return totalApprovedGstLiveData;
    }

    public LiveData<Integer> getApprovedGamesCount() {
        return approvedGamesCountLiveData;
    }

    public LiveData<List<MonthlyPointValueReport>> getReportsSummaries() {
        return reportsSummariesLiveData;
    }

    /**
     * Load all games with real-time listener (for Dashboard)
     * This method sets up a real-time listener that automatically updates when data changes
     */
    public void loadAllGamesWithRealtimeListener() {
        System.out.println("GameRepository: Setting up real-time listener for games collection");
        startMyViewApprovalsListener();

        // Remove existing listener if any
        if (gamesListener != null) {
            gamesListener.remove();
        }
        
        // Set up real-time listener for games collection
        gamesListener = db.collection(FirestoreCollections.GAMES)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        System.out.println("GameRepository: Error listening to games collection: " + error.getMessage());
                        errorLiveData.setValue("Failed to load games: " + error.getMessage());
                        return;
                    }
                    
                    if (querySnapshot != null) {
                        System.out.println("GameRepository: Real-time update received, found " + querySnapshot.size() + " documents");

                        if (querySnapshot.isEmpty()) {
                            System.out.println("GameRepository: No games found in database");
                            clearGameDataListenersAndResetGameListState();
                            return;
                        }

                        System.out.println("GameRepository: Found " + querySnapshot.size() + " games, seeding dashboard rows");
                        loadGameDataForIdsWithListeners(querySnapshot);
                    }
                });
    }
    
    /**
     * Load all games with one-time fetch (for Review screen - manual refresh only)
     * This method fetches data once and does not set up real-time listeners.
     * Uses {@link Source#SERVER} first so local persistence cannot keep a deleted last game in the list.
     */
    public void loadAllGames() {
        System.out.println("GameRepository: Fetching games collection (manual refresh, prefer server)");
        com.google.firebase.firestore.Query gamesQuery = db.collection(FirestoreCollections.GAMES)
                .orderBy("createdAt", Query.Direction.ASCENDING);
        gamesQuery.get(Source.SERVER)
                .addOnSuccessListener(this::applyGamesQuerySnapshotForReview)
                .addOnFailureListener(error -> {
                    System.out.println("GameRepository: Server games fetch failed, falling back to default: " + error.getMessage());
                    gamesQuery.get()
                            .addOnSuccessListener(this::applyGamesQuerySnapshotForReview)
                            .addOnFailureListener(e2 -> {
                                System.out.println("GameRepository: Error fetching games collection: " + e2.getMessage());
                                errorLiveData.setValue("Failed to load games: " + e2.getMessage());
                            });
                });
    }

    private void applyGamesQuerySnapshotForReview(QuerySnapshot querySnapshot) {
        if (querySnapshot == null) {
            return;
        }
        System.out.println("GameRepository: Fetched " + querySnapshot.size() + " documents");
        List<String> gameIds = new ArrayList<>();
        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            if (GameCreationPolicy.isReady(
                    document.getString("initializationStatus"))) {
                gameIds.add(document.getId());
            }
        }
        if (gameIds.isEmpty()) {
            System.out.println("GameRepository: No games found in database");
            resetGameListStateForEmptyQuery();
            return;
        }
        System.out.println("GameRepository: Found " + gameIds.size() + " game IDs, loading game data");
        loadGameDataForIds(gameIds);
    }
    
    /**
     * Remove listeners when repository is no longer needed
     */
    public void removeListeners() {
        if (gamesListener != null) {
            gamesListener.remove();
            gamesListener = null;
        }
        if (approvedGamesListener != null) {
            approvedGamesListener.remove();
            approvedGamesListener = null;
        }
        if (myViewApprovalsListener != null) {
            myViewApprovalsListener.remove();
            myViewApprovalsListener = null;
        }
        // Remove all gameData listeners
        for (com.google.firebase.firestore.ListenerRegistration listener : gameDataListeners.values()) {
            listener.remove();
        }
        gameDataListeners.clear();
    }

    /**
     * Clears in-memory game list state when the games collection has no documents (one-shot query / Review).
     * Prevents stale in-flight {@link #fetchGameData} callbacks from repopulating the UI from old {@link #gameIdsOrder}.
     */
    private void resetGameListStateForEmptyQuery() {
        gameItemsMap.clear();
        gameIdsOrder.clear();
        gameItemsLiveData.setValue(new ArrayList<>());
    }

    /**
     * Removes per-game listeners and clears state when the realtime games snapshot is empty (Dashboard).
     */
    private void clearGameDataListenersAndResetGameListState() {
        for (com.google.firebase.firestore.ListenerRegistration listener : gameDataListeners.values()) {
            listener.remove();
        }
        gameDataListeners.clear();
        gameItemsMap.clear();
        gameIdsOrder.clear();
        gameItemsLiveData.setValue(new ArrayList<>());
    }

    /**
     * Load game data with real-time listeners (for Dashboard).
     * Pre-approval behavior: attach a {@code gameData_v2} listener per game; placeholders only on read errors.
     */
    private void loadGameDataForIdsWithListeners(QuerySnapshot gamesSnapshot) {
        List<String> gameIds = new ArrayList<>();
        for (DocumentSnapshot document : gamesSnapshot.getDocuments()) {
            if (GameCreationPolicy.isReady(
                    document.getString("initializationStatus"))) {
                gameIds.add(document.getId());
            }
        }

        gameIdsOrder = new ArrayList<>(gameIds);

        java.util.Set<String> currentGameIds = new java.util.HashSet<>(gameIds);
        java.util.Iterator<java.util.Map.Entry<String, com.google.firebase.firestore.ListenerRegistration>> iterator =
            gameDataListeners.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<String, com.google.firebase.firestore.ListenerRegistration> entry = iterator.next();
            if (!currentGameIds.contains(entry.getKey())) {
                entry.getValue().remove();
                iterator.remove();
                gameItemsMap.remove(entry.getKey());
                System.out.println("Removed listener and data for gameData: " + entry.getKey());
            }
        }

        for (String gameId : gameIds) {
            setupGameDataListener(gameId);
        }
        for (DocumentSnapshot document : gamesSnapshot.getDocuments()) {
            if (GameCreationPolicy.isReady(
                    document.getString("initializationStatus"))) {
                applyViewApprovalCountsFromGameSnapshot(
                        document, gameItemsMap.get(document.getId()));
            }
        }

        updateGameItemsList();
    }

    private boolean hasGameDataAccess(@Nullable GameAuth auth, @NonNull String gameId) {
        if (GameViewApprovalRepository.canBypassViewGate(auth)) {
            return true;
        }
        String status = myViewApprovalStatusByGame.get(gameId);
        return "approved".equalsIgnoreCase(status);
    }

    /**
     * Builds a dashboard card from {@code games_v2} when {@code gameData_v2} is not readable.
     */
    private void ensurePlaceholderForRestrictedUser(DocumentSnapshot authDocument) {
        if (authDocument == null || !authDocument.exists()) {
            return;
        }
        String gameId = authDocument.getId();
        if (!gameIdsOrder.contains(gameId)) {
            return;
        }

        GameAuth auth = authDocument.toObject(GameAuth.class);
        if (auth == null) {
            return;
        }

        GameItem existing = gameItemsMap.get(gameId);
        if (existing != null) {
            applyViewApprovalCountsFromGameSnapshot(authDocument, existing);
            if (existing.getPlayers() != null || wasRecentlyUpdatedLocally(gameId)) {
                updateGameItemsList();
                return;
            }
            if (!authDocument.getMetadata().isFromCache()) {
                applyDashboardSummaryFromAuth(existing, auth);
                updateGameItemsList();
            }
            return;
        }

        putDashboardPlaceholder(gameId, auth, null);
        applyViewApprovalCountsFromGameSnapshot(authDocument, gameItemsMap.get(gameId));

        String creatorUserId = auth.getCreatorUserId();
        if (creatorUserId != null && !creatorUserId.isEmpty()) {
            db.collection(FirestoreCollections.APP_USER)
                    .document(creatorUserId)
                    .get()
                    .addOnSuccessListener(userSnapshot -> {
                        if (!gameIdsOrder.contains(gameId)) {
                            return;
                        }
                        GameItem current = gameItemsMap.get(gameId);
                        if (current == null || current.getPlayers() != null) {
                            return;
                        }
                        String creatorPhotoUrl = userSnapshot.exists()
                                ? userSnapshot.getString("photoUrl") : null;
                        if (creatorPhotoUrl != null) {
                            current.setCreatorPhotoUrl(creatorPhotoUrl);
                            applyViewApprovalCountsFromGameSnapshot(authDocument, current);
                            updateGameItemsList();
                        }
                    });
        }
    }

    private void putDashboardPlaceholder(String gameId, GameAuth auth, String creatorPhotoUrl) {
        if (!gameIdsOrder.contains(gameId)) {
            return;
        }
        GameItem item = convertToPlaceholderGameItem(gameId, auth, creatorPhotoUrl);
        gameItemsMap.put(gameId, item);
        updateGameItemsList();
    }

    private GameItem convertToPlaceholderGameItem(String gameId, GameAuth auth, String creatorPhotoUrl) {
        String pin = auth.getPin() != null ? auth.getPin() : "";
        String creatorName = auth.getCreatorName();
        String creatorUserId = auth.getCreatorUserId();
        com.google.firebase.Timestamp createdAt = auth.getCreatedAt();
        String gameDisplayName = gameDisplayNameFromAuth(auth);
        String unknown = "—";

        String pointValueStr = auth.getDashboardPointValue() != null
                ? String.format("%.2f", auth.getDashboardPointValue()) : unknown;
        String playersStr = auth.getDashboardNumPlayers() != null
                ? String.valueOf(auth.getDashboardNumPlayers()) : unknown;
        String gstStr = auth.getDashboardGstPercent() != null
                ? String.format("%.0f", auth.getDashboardGstPercent()) : unknown;
        String gameStatus = dashboardStatusForDisplay(auth.getDashboardGameStatus());

        GameItem gameItem = new GameItem(
                gameId,
                pin,
                unknown,
                pointValueStr,
                formatTimestamp(createdAt),
                gameStatus,
                playersStr,
                gstStr,
                unknown,
                creatorName
        );
        gameItem.setCreatorPhotoUrl(creatorPhotoUrl);
        gameItem.setCreatorUserId(creatorUserId);
        gameItem.setGameDisplayName(gameDisplayName != null ? gameDisplayName : "");
        return gameItem;
    }

    /**
     * Loads {@code gameData_v2} for dashboard row refresh (creator/editor/admin).
     * Prefers server data so offline cache cannot revert round/player counts.
     */
    private void refreshDashboardRowFromGameData(String gameId) {
        if (gameId == null || gameId.isEmpty()) {
            return;
        }
        com.google.firebase.firestore.DocumentReference ref =
                db.collection(FirestoreCollections.GAME_DATA).document(gameId);
        ref.get(Source.SERVER)
                .addOnSuccessListener(snapshot -> applyGameDataRefreshSnapshot(gameId, snapshot))
                .addOnFailureListener(e -> ref.get()
                        .addOnSuccessListener(snapshot -> applyGameDataRefreshSnapshot(gameId, snapshot))
                        .addOnFailureListener(e2 ->
                                System.out.println("Dashboard row refresh failed for " + gameId + ": "
                                        + e2.getMessage())));
    }

    private void applyGameDataRefreshSnapshot(String gameId, DocumentSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return;
        }
        if (!gameIdsOrder.contains(gameId)) {
            gameIdsOrder.add(0, gameId);
        }
        GameDataWrapper wrapper = snapshot.toObject(GameDataWrapper.class);
        if (wrapper == null || wrapper.getData() == null) {
            return;
        }
        if (!shouldApplyRemoteDashboardGameData(gameId, wrapper.getData(), snapshot)) {
            System.out.println("Skipping stale dashboard server refresh for " + gameId);
            return;
        }
        upgradeDashboardItemFromGameData(gameId, wrapper.getData(), wrapper.getLastUpdated());
    }

    private void upgradeDashboardItemFromGameData(String gameId, GameData gameData,
                                                  com.google.firebase.Timestamp fallbackCreatedAt) {
        db.collection(FirestoreCollections.GAMES)
                .document(gameId)
                .get()
                .addOnSuccessListener(authSnapshot -> {
                    if (!gameIdsOrder.contains(gameId)) {
                        return;
                    }
                    GameAuth gameAuth = authSnapshot.toObject(GameAuth.class);
                    String pin = gameAuth != null ? gameAuth.getPin() : "0000";
                    String creatorName = gameAuth != null ? gameAuth.getCreatorName() : null;
                    String creatorUserId = gameAuth != null ? gameAuth.getCreatorUserId() : null;
                    String gameDisplayName = gameDisplayNameFromAuth(gameAuth);
                    com.google.firebase.Timestamp createdAt = gameAuth != null && gameAuth.getCreatedAt() != null
                            ? gameAuth.getCreatedAt() : fallbackCreatedAt;

                    Runnable publish = () -> {
                        GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt,
                                creatorName, null, creatorUserId, gameDisplayName);
                        if (gameItem != null) {
                            applyViewApprovalCountsFromGameSnapshot(authSnapshot, gameItem);
                            gameItemsMap.put(gameId, gameItem);
                            updateGameItemsList();
                        }
                    };

                    if (creatorUserId != null && !creatorUserId.isEmpty()) {
                        db.collection(FirestoreCollections.APP_USER)
                                .document(creatorUserId)
                                .get()
                                .addOnSuccessListener(userSnapshot -> {
                                    String creatorPhotoUrl = userSnapshot.exists()
                                            ? userSnapshot.getString("photoUrl") : null;
                                    GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt,
                                            creatorName, creatorPhotoUrl, creatorUserId, gameDisplayName);
                                    if (gameItem != null) {
                                        applyViewApprovalCountsFromGameSnapshot(authSnapshot, gameItem);
                                        gameItemsMap.put(gameId, gameItem);
                                        updateGameItemsList();
                                    }
                                })
                                .addOnFailureListener(e -> publish.run());
                    } else {
                        publish.run();
                    }
                });
    }

    private void applyDashboardSummaryFromAuth(@Nullable GameItem item, @Nullable GameAuth auth) {
        if (item == null || auth == null) {
            return;
        }
        if (auth.getDashboardPointValue() != null) {
            item.setPointValue(String.format(Locale.US, "%.2f", auth.getDashboardPointValue()));
        }
        if (auth.getDashboardNumPlayers() != null) {
            item.setNumberOfPlayers(String.valueOf(auth.getDashboardNumPlayers()));
        }
        if (auth.getDashboardGstPercent() != null) {
            item.setGstPercentage(String.format(Locale.US, "%.0f", auth.getDashboardGstPercent()));
        }
        if (auth.getDashboardGameStatus() != null && !auth.getDashboardGameStatus().trim().isEmpty()) {
            item.setGameStatus(dashboardStatusForDisplay(auth.getDashboardGameStatus()));
        }
        String gameDisplayName = gameDisplayNameFromAuth(auth);
        if (gameDisplayName != null && !gameDisplayName.isEmpty()) {
            item.setGameDisplayName(gameDisplayName);
        }
    }

    /**
     * Keeps {@code games_v2} dashboard summary in sync for users who cannot read {@code gameData_v2}.
     */
    /** Pushes the current in-memory dashboard rows to LiveData observers immediately. */
    public void forcePublishDashboard() {
        updateGameItemsList();
    }

    public void syncDashboardSummaryForGame(@NonNull String gameId, @NonNull GameData gameData) {
        syncDashboardSummaryOnGameDoc(gameId, gameData);
    }

    /**
     * Immediately updates the dashboard card in memory (round badge, stats) after a score save.
     */
    public void updateLocalDashboardFromGameData(@NonNull String gameId, @NonNull GameData gameData) {
        if (gameData == null || gameId.isEmpty()) {
            return;
        }
        int playerCount = resolvePlayerCount(gameData);
        String status = gameData.getGameStatus();
        String pointValueStr = String.format(Locale.US, "%.2f", gameData.getPointValue());
        String gstStr = String.format(Locale.US, "%.0f", gameData.getGstPercent());
        String totalScoreStr = String.valueOf(gameData.getTotalScore());

        GameItem item = gameItemsMap.get(gameId);
        if (item == null) {
            if (!gameIdsOrder.contains(gameId)) {
                gameIdsOrder.add(0, gameId);
            }
            item = new GameItem(
                    gameId,
                    "",
                    totalScoreStr,
                    pointValueStr,
                    "",
                    status != null ? status : "R1",
                    String.valueOf(playerCount),
                    gstStr,
                    "—",
                    gameData.getPlayers()
            );
            gameItemsMap.put(gameId, item);
        } else {
            GameItem updated = new GameItem(
                    gameId,
                    item.getGamePin() != null ? item.getGamePin() : "",
                    totalScoreStr,
                    pointValueStr,
                    item.getCreationDateTime() != null ? item.getCreationDateTime() : "",
                    status != null ? status : item.getGameStatus(),
                    String.valueOf(playerCount),
                    gstStr,
                    item.getGstAmount() != null ? item.getGstAmount() : "—",
                    item.getCreatorName(),
                    gameData.getPlayers()
            );
            updated.setCreatorPhotoUrl(item.getCreatorPhotoUrl());
            updated.setCreatorUserId(item.getCreatorUserId());
            updated.setGameDisplayName(item.getGameDisplayName());
            updated.setMyViewAccessStatus(item.getMyViewAccessStatus());
            copyViewApprovalCounts(item, updated);
            gameItemsMap.put(gameId, updated);
        }
        markLocalDashboardUpdated(gameId);
        updateGameItemsList();
    }

    /** Refreshes dashboard rows from {@code gameData_v2} when the user can read it (e.g. after Join). */
    public void refreshCreatorDashboardRows() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        for (String gameId : new ArrayList<>(gameIdsOrder)) {
            Long localAt = localDashboardUpdatedAtMs.get(gameId);
            if (localAt != null && now - localAt < 30_000) {
                continue;
            }
            if (gameDataListeners.containsKey(gameId)) {
                refreshDashboardRowFromGameData(gameId);
            }
        }
    }

    private void markLocalDashboardUpdated(@NonNull String gameId) {
        localDashboardUpdatedAtMs.put(gameId, android.os.SystemClock.elapsedRealtime());
    }

    private boolean wasRecentlyUpdatedLocally(@NonNull String gameId) {
        Long localAt = localDashboardUpdatedAtMs.get(gameId);
        return localAt != null
                && android.os.SystemClock.elapsedRealtime() - localAt < 60_000;
    }

    /**
     * Prevents a stale Firestore snapshot (often still R1 / 2 players) from undoing a fresh local save
     * when the user leaves JoinGame before the async write finishes.
     */
    private boolean shouldApplyRemoteDashboardGameData(@NonNull String gameId,
                                                       @NonNull GameData remote,
                                                       @Nullable DocumentSnapshot snapshot) {
        GameItem local = gameItemsMap.get(gameId);
        if (local == null || local.getPlayers() == null || local.getPlayers().isEmpty()) {
            if (wasRecentlyUpdatedLocally(gameId)) {
                return false;
            }
            return true;
        }

        int localPlayers = local.getPlayers().size();
        int remotePlayers = resolvePlayerCount(remote);
        if (remotePlayers < localPlayers) {
            return false;
        }

        Long localAt = localDashboardUpdatedAtMs.get(gameId);
        if (localAt == null) {
            return true;
        }

        long ageMs = android.os.SystemClock.elapsedRealtime() - localAt;
        if (ageMs > 60_000) {
            return true;
        }

        int localRound = parseRoundNumber(local.getGameStatus());
        int remoteRound = parseRoundNumber(remote.getGameStatus());
        if (localRound > 0 && remoteRound > 0 && remoteRound < localRound) {
            return false;
        }

        if (snapshot != null && snapshot.getMetadata().isFromCache() && ageMs < 15_000) {
            if (remotePlayers <= localPlayers && localRound > 0
                    && (remoteRound <= 0 || remoteRound <= localRound)) {
                return false;
            }
        }

        return true;
    }

    private static int parseRoundNumber(@Nullable String status) {
        if (status != null && status.startsWith("R")) {
            try {
                return Integer.parseInt(status.substring(1));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return -1;
    }

    private void syncDashboardSummaryOnGameDoc(String gameId, GameData gameData) {
        if (gameId == null || gameData == null) {
            return;
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("dashboardPointValue", gameData.getPointValue());
        summary.put("dashboardNumPlayers", resolvePlayerCount(gameData));
        summary.put("dashboardGstPercent", gameData.getGstPercent());
        String status = gameData.getGameStatus();
        summary.put("dashboardGameStatus",
                status != null && !status.trim().isEmpty() ? status.trim() : "R1");
        db.collection(FirestoreCollections.GAMES)
                .document(gameId)
                .update(summary)
                .addOnFailureListener(e ->
                        System.out.println("Failed to sync dashboard summary for " + gameId + ": "
                                + e.getMessage()));
    }

    /**
     * Load game data with one-time fetch (for Review screen)
     */
    private void loadGameDataForIds(List<String> gameIds) {
        // Update the game IDs order
        gameIdsOrder = new ArrayList<>(gameIds);

        // Clear previous game items
        gameItemsMap.clear();
        // Push empty list immediately so the UI does not keep the previous LiveData value when all fetches resolve to "missing".
        updateGameItemsList();

        // Fetch game data for each game
        for (String gameId : gameIds) {
            fetchGameData(gameId);
        }
    }

    /** Drops a row when server no longer has gameData / games for this id (e.g. deleted while cache still listed it). */
    private void removeStaleGameRowForReview(String gameId) {
        if (gameId == null) {
            return;
        }
        gameItemsMap.remove(gameId);
        updateGameItemsList();
    }

    private void fetchGameData(String gameId) {
        System.out.println("Fetching game data for: " + gameId);
        com.google.firebase.firestore.DocumentReference dataRef = db.collection(FirestoreCollections.GAME_DATA).document(gameId);
        dataRef.get(Source.SERVER)
                .addOnSuccessListener(snapshot -> onGameDataSnapshotForReviewFetch(gameId, snapshot))
                .addOnFailureListener(error -> {
                    System.out.println("GameData server fetch failed for " + gameId + ", fallback: " + error.getMessage());
                    dataRef.get()
                            .addOnSuccessListener(snapshot -> onGameDataSnapshotForReviewFetch(gameId, snapshot))
                            .addOnFailureListener(error2 ->
                                    System.out.println("Error fetching gameData for " + gameId + ": " + error2.getMessage()));
                });
    }

    private void onGameDataSnapshotForReviewFetch(String gameId, DocumentSnapshot documentSnapshot) {
        if (!gameIdsOrder.contains(gameId)) {
            return;
        }
        if (documentSnapshot == null || !documentSnapshot.exists()) {
            System.out.println("GameData document doesn't exist for " + gameId);
            removeStaleGameRowForReview(gameId);
            return;
        }
        try {
            GameDataWrapper gameDataWrapper = documentSnapshot.toObject(GameDataWrapper.class);
            if (gameDataWrapper == null || gameDataWrapper.getData() == null) {
                removeStaleGameRowForReview(gameId);
                return;
            }
            GameData gameData = gameDataWrapper.getData();
            attachReviewGameAuthFetch(gameId, gameDataWrapper, gameData);
        } catch (Exception e) {
            System.out.println("Error deserializing game data for " + gameId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void attachReviewGameAuthFetch(String gameId, GameDataWrapper gameDataWrapper, GameData gameData) {
        com.google.firebase.firestore.DocumentReference authRef = db.collection(FirestoreCollections.GAMES).document(gameId);
        authRef.get(Source.SERVER)
                .addOnSuccessListener(authSnapshot -> onAuthSnapshotForReviewFetch(gameId, gameDataWrapper, gameData, authSnapshot))
                .addOnFailureListener(e -> authRef.get()
                        .addOnSuccessListener(authSnapshot -> onAuthSnapshotForReviewFetch(gameId, gameDataWrapper, gameData, authSnapshot))
                        .addOnFailureListener(e2 ->
                                System.out.println("Error fetching games auth for " + gameId + ": " + e2.getMessage())));
    }

    private void onAuthSnapshotForReviewFetch(String gameId, GameDataWrapper gameDataWrapper, GameData gameData,
                                                DocumentSnapshot authSnapshot) {
        if (!gameIdsOrder.contains(gameId)) {
            return;
        }
        if (authSnapshot == null || !authSnapshot.exists()) {
            removeStaleGameRowForReview(gameId);
            return;
        }
        GameAuth gameAuth = authSnapshot.toObject(GameAuth.class);
        String pin = gameAuth != null ? gameAuth.getPin() : "0000";
        String creatorName = gameAuth != null ? gameAuth.getCreatorName() : null;
        String creatorUserId = gameAuth != null ? gameAuth.getCreatorUserId() : null;
        String gameDisplayName = gameDisplayNameFromAuth(gameAuth);

        com.google.firebase.Timestamp createdAt = gameAuth != null ? gameAuth.getCreatedAt() : gameDataWrapper.getLastUpdated();

        if (creatorUserId != null && !creatorUserId.isEmpty()) {
            db.collection(FirestoreCollections.APP_USER)
                    .document(creatorUserId)
                    .get()
                    .addOnSuccessListener(userSnapshot -> {
                        if (!gameIdsOrder.contains(gameId)) {
                            return;
                        }
                        String creatorPhotoUrl = null;
                        if (userSnapshot.exists()) {
                            creatorPhotoUrl = userSnapshot.getString("photoUrl");
                        }
                        GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName, creatorPhotoUrl, creatorUserId, gameDisplayName);
                        putGameItemIfStillInLoad(gameId, gameItem);
                    })
                    .addOnFailureListener(e -> {
                        if (!gameIdsOrder.contains(gameId)) {
                            return;
                        }
                        GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName, null, creatorUserId, gameDisplayName);
                        putGameItemIfStillInLoad(gameId, gameItem);
                    });
        } else {
            GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName, null, null, gameDisplayName);
            putGameItemIfStillInLoad(gameId, gameItem);
        }
    }
    
    /**
     * Setup real-time listener for a specific game (for Dashboard)
     */
    private void setupGameDataListener(String gameId) {
        // If listener already exists, don't create duplicate
        if (gameDataListeners.containsKey(gameId)) {
            System.out.println("Listener already exists for gameData: " + gameId);
            return;
        }
        
        System.out.println("Setting up real-time listener for gameData: " + gameId);
        
        com.google.firebase.firestore.ListenerRegistration listener = db.collection(FirestoreCollections.GAME_DATA)
                .document(gameId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        System.out.println("Error listening to gameData for " + gameId + ": " + error.getMessage());
                        db.collection(FirestoreCollections.GAMES)
                                .document(gameId)
                                .get()
                                .addOnSuccessListener(authSnapshot -> {
                                    if (hasGameDataAccess(authSnapshot.toObject(GameAuth.class), gameId)) {
                                        refreshDashboardRowFromGameData(gameId);
                                    } else {
                                        ensurePlaceholderForRestrictedUser(authSnapshot);
                                    }
                                });
                        return;
                    }
                    
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        try {
                            GameDataWrapper gameDataWrapper = documentSnapshot.toObject(GameDataWrapper.class);
                            if (gameDataWrapper != null && gameDataWrapper.getData() != null) {
                                GameData gameData = gameDataWrapper.getData();
                                if (!shouldApplyRemoteDashboardGameData(gameId, gameData, documentSnapshot)) {
                                    System.out.println("Skipping stale dashboard listener snapshot for " + gameId);
                                    return;
                                }
                                // Also get the auth data for PIN
                                db.collection(FirestoreCollections.GAMES)
                                        .document(gameId)
                                        .get()
                                        .addOnSuccessListener(authSnapshot -> {
                                            GameAuth gameAuth = authSnapshot.toObject(GameAuth.class);
                                            String pin = gameAuth != null ? gameAuth.getPin() : "0000";
                                            String creatorName = gameAuth != null ? gameAuth.getCreatorName() : null;
                                            String creatorUserId = gameAuth != null ? gameAuth.getCreatorUserId() : null;
                                            String gameDisplayName = gameDisplayNameFromAuth(gameAuth);
                                            
                                            // Use createdAt from games collection instead of lastUpdated from gameData collection
                                            com.google.firebase.Timestamp createdAt = gameAuth != null ? gameAuth.getCreatedAt() : gameDataWrapper.getLastUpdated();
                                            
                                            // Fetch creator's photo URL from appUser collection
                                            if (creatorUserId != null && !creatorUserId.isEmpty()) {
                                                db.collection(FirestoreCollections.APP_USER)
                                                        .document(creatorUserId)
                                                        .get()
                                                        .addOnSuccessListener(userSnapshot -> {
                                                            String creatorPhotoUrl = null;
                                                            if (userSnapshot.exists()) {
                                                                creatorPhotoUrl = userSnapshot.getString("photoUrl");
                                                            }
                                                            GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName, creatorPhotoUrl, creatorUserId, gameDisplayName);
                                                            
                                                            if (gameItem != null && shouldApplyRemoteDashboardGameData(gameId, gameData, documentSnapshot)) {
                                                                applyViewApprovalCountsFromGameSnapshot(authSnapshot, gameItem);
                                                                gameItemsMap.put(gameId, gameItem);
                                                                checkAndNotifyNewGame(gameItem);
                                                                updateGameItemsList();
                                                            }
                                                        })
                                                        .addOnFailureListener(e -> {
                                                            // If fetching photo fails, create game item without photo
                                                            GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName, null, creatorUserId, gameDisplayName);
                                                            if (gameItem != null && shouldApplyRemoteDashboardGameData(gameId, gameData, documentSnapshot)) {
                                                                applyViewApprovalCountsFromGameSnapshot(authSnapshot, gameItem);
                                                                gameItemsMap.put(gameId, gameItem);
                                                                checkAndNotifyNewGame(gameItem);
                                                                updateGameItemsList();
                                                            }
                                                        });
                                            } else {
                                                // No creator user ID, create game item without photo
                                                GameItem gameItem = convertToGameItem(gameId, pin, gameData, createdAt, creatorName, null, null, gameDisplayName);
                                                if (gameItem != null && shouldApplyRemoteDashboardGameData(gameId, gameData, documentSnapshot)) {
                                                    applyViewApprovalCountsFromGameSnapshot(authSnapshot, gameItem);
                                                    gameItemsMap.put(gameId, gameItem);
                                                    checkAndNotifyNewGame(gameItem);
                                                    updateGameItemsList();
                                                }
                                            }
                                        });
                            }
                        } catch (Exception e) {
                            System.out.println("Error deserializing game data for " + gameId + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("GameData document doesn't exist yet for " + gameId);
                    }
                });
        
        gameDataListeners.put(gameId, listener);
    }

    /** Ignores late one-shot fetch callbacks after {@link #gameIdsOrder} no longer includes the id. */
    private void putGameItemIfStillInLoad(String gameId, GameItem gameItem) {
        if (gameItem == null || !gameIdsOrder.contains(gameId)) {
            return;
        }
        gameItemsMap.put(gameId, gameItem);
        updateGameItemsList();
    }

    private void applyViewApprovalCountsFromGameSnapshot(@Nullable DocumentSnapshot snapshot,
                                                         @Nullable GameItem item) {
        if (snapshot == null || item == null) {
            return;
        }
        if (!canShowViewApprovalCounts(snapshot)) {
            clearViewApprovalCounts(item);
            return;
        }

        int requested = 0;
        int approved = 0;
        int rejected = 0;
        String creatorUserId = snapshot.getString("creatorUserId");
        Object raw = snapshot.get(GameViewApprovalRepository.PENDING_VIEW_REQUESTS_FIELD);
        if (raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> requestsByUser = (Map<String, Object>) raw;
            for (Map.Entry<String, Object> entry : requestsByUser.entrySet()) {
                if (creatorUserId != null && creatorUserId.equals(entry.getKey())) {
                    continue;
                }
                if (!(entry.getValue() instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> requestData = (Map<String, Object>) entry.getValue();
                Object statusObj = requestData.get("status");
                GameViewApprovalStatus status = GameViewApprovalStatus.fromFirestore(
                        statusObj != null ? String.valueOf(statusObj) : null);
                switch (status) {
                    case APPROVED:
                        approved++;
                        break;
                    case REJECTED:
                        rejected++;
                        break;
                    case REQUESTED:
                    default:
                        requested++;
                        break;
                }
            }
        }
        item.setPendingViewRequestCount(requested);
        item.setApprovedViewRequestCount(approved);
        item.setRejectedViewRequestCount(rejected);
    }

    private boolean canShowViewApprovalCounts(@NonNull DocumentSnapshot snapshot) {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return false;
        }
        if (AppUserRoleSession.getInstance().peekRole() == AppUserRoleSession.Role.ADMIN) {
            return true;
        }
        String uid = user.getUid();
        return uid.equals(snapshot.getString("creatorUserId"))
                || uid.equals(snapshot.getString("activeEditorUserId"));
    }

    private static void clearViewApprovalCounts(@NonNull GameItem item) {
        item.setPendingViewRequestCount(0);
        item.setApprovedViewRequestCount(0);
        item.setRejectedViewRequestCount(0);
    }

    private static void copyViewApprovalCounts(@NonNull GameItem from, @NonNull GameItem to) {
        to.setPendingViewRequestCount(from.getPendingViewRequestCount());
        to.setApprovedViewRequestCount(from.getApprovedViewRequestCount());
        to.setRejectedViewRequestCount(from.getRejectedViewRequestCount());
    }
    
    private void updateGameItemsList() {
        applyMyViewAccessStatusToGameItems();
        List<GameItem> gameItems = new ArrayList<>();
        // Maintain the order from gameIdsOrder
        for (String gameId : gameIdsOrder) {
            GameItem item = gameItemsMap.get(gameId);
            if (item != null) {
                gameItems.add(item);
            }
        }
        System.out.println("Updating game items list with " + gameItems.size() + " games");
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            gameItemsLiveData.setValue(gameItems);
        } else {
            gameItemsLiveData.postValue(gameItems);
        }
    }

    private void startMyViewApprovalsListener() {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            myViewApprovalStatusByGame.clear();
            return;
        }
        if (myViewApprovalsListener != null) {
            myViewApprovalsListener.remove();
        }
        myViewApprovalsListener = viewApprovalRepository.listenMyViewApprovals(
                user.getUid(),
                statusByGameId -> {
                    Map<String, String> previous = myViewApprovalStatusByGame;
                    myViewApprovalStatusByGame = statusByGameId != null
                            ? new HashMap<>(statusByGameId) : new HashMap<>();
                    if (statusByGameId != null) {
                        for (Map.Entry<String, String> entry : statusByGameId.entrySet()) {
                            String gameId = entry.getKey();
                            if (!"approved".equalsIgnoreCase(entry.getValue())) {
                                continue;
                            }
                            String prior = previous != null ? previous.get(gameId) : null;
                            if ("approved".equalsIgnoreCase(prior)) {
                                continue;
                            }
                            setupGameDataListener(gameId);
                            refreshDashboardRowFromGameData(gameId);
                        }
                    }
                    applyMyViewAccessStatusToGameItems();
                    updateGameItemsListWithoutReapply();
                });
    }

    private void applyMyViewAccessStatusToGameItems() {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        boolean isAdmin = AppUserRoleSession.getInstance().peekRole() == AppUserRoleSession.Role.ADMIN;
        String uid = user != null ? user.getUid() : null;
        for (GameItem item : gameItemsMap.values()) {
            if (item == null) {
                continue;
            }
            if (isAdmin || (uid != null && uid.equals(item.getCreatorUserId()))) {
                item.setMyViewAccessStatus(null);
            } else {
                item.setMyViewAccessStatus(myViewApprovalStatusByGame.get(item.getGameId()));
            }
        }
    }

    private void updateGameItemsListWithoutReapply() {
        List<GameItem> gameItems = new ArrayList<>();
        for (String gameId : gameIdsOrder) {
            GameItem item = gameItemsMap.get(gameId);
            if (item != null) {
                gameItems.add(item);
            }
        }
        gameItemsLiveData.postValue(gameItems);
    }

    private static String dashboardStatusForDisplay(@Nullable String status) {
        if (status == null || status.trim().isEmpty()
                || "In Progress".equalsIgnoreCase(status.trim())) {
            return "R1";
        }
        return status.trim();
    }

    private static int resolvePlayerCount(@NonNull GameData gameData) {
        if (gameData.getPlayers() != null && !gameData.getPlayers().isEmpty()) {
            return gameData.getPlayers().size();
        }
        return Math.max(gameData.getNumPlayers(), 0);
    }

    private static String gameDisplayNameFromAuth(GameAuth auth) {
        if (auth == null || auth.getDisplayName() == null) {
            return "";
        }
        return auth.getDisplayName();
    }

    private GameItem convertToGameItem(String gameId, String pin, GameData gameData, com.google.firebase.Timestamp createdAt, String creatorName, String creatorPhotoUrl, String creatorUserId, String gameDisplayName) {
        // Calculate total score
        int totalScore = gameData.getTotalScore();
        
        // Format point value (convert from double to string with 2 decimal places)
        String pointValueStr = String.format("%.2f", gameData.getPointValue());
        
        // Format GST percentage
        String gstPercentageStr = String.format("%.0f", gameData.getGstPercent());
        
        // Calculate GST amount
        double gstAmount = gameData.getGstAmount();
        String gstAmountStr = String.format("%.0f", gstAmount);
        
        // Format creation date (from timestamp)
        String creationDateTime = formatTimestamp(createdAt);
        
        // Get game status (no need to filter "Approved" status since approved games are deleted from games collection)
        String gameStatus = gameData.getGameStatus();
        
        // Get number of players
        String numberOfPlayers = String.valueOf(resolvePlayerCount(gameData));

        GameItem gameItem = new GameItem(
                gameId,
                pin,
                String.valueOf(totalScore),
                pointValueStr,
                creationDateTime,
                gameStatus,
                numberOfPlayers,
                gstPercentageStr,
                gstAmountStr,
                creatorName,
                gameData.getPlayers()
        );
        
        // Set creator photo URL and user ID
        gameItem.setCreatorPhotoUrl(creatorPhotoUrl);
        gameItem.setCreatorUserId(creatorUserId);
        gameItem.setGameDisplayName(gameDisplayName != null ? gameDisplayName : "");
        
        return gameItem;
    }

    private String formatTimestamp(com.google.firebase.Timestamp timestamp) {
        if (timestamp == null) {
            return "Unknown";
        }
        
        java.util.Date date = timestamp.toDate();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }
    
    /**
     * Check if a game is new
     */
    private void checkAndNotifyNewGame(GameItem game) {
        // Skip if no context available or game is completed
        if (appContext == null || game.isCompleted() || "Completed".equals(game.getGameStatus())) {
            return;
        }
        
        // Check if this is a new game we haven't seen before
        if (!seenGameIds.contains(game.getGameId())) {
            android.util.Log.d("GameRepository", "New game detected in repository: " + game.getGameId() + 
                " created by: " + game.getCreatorName() + " (ID: " + game.getCreatorUserId() + ")");
            
            // Mark as seen
            seenGameIds.add(game.getGameId());
            
            android.util.Log.d("GameRepository", "New game detected: " + game.getGameId());
        }
    }
    
    /**
     * Parse point value from string to double
     */
    private double parsePointValue(String pointValueStr) {
        if (pointValueStr == null || pointValueStr.isEmpty()) {
            return 0.0;
        }
        try {
            // Remove currency symbol if present
            String cleaned = pointValueStr.replace("₹", "").trim();
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public void deleteGame(String gameId) {
        viewApprovalRepository.deleteAllForGame(gameId);
        // Delete from both collections
        db.collection(FirestoreCollections.GAMES).document(gameId).delete()
                .addOnSuccessListener(aVoid -> {
                    db.collection(FirestoreCollections.GAME_DATA).document(gameId).delete()
                            .addOnSuccessListener(aVoid1 -> {
                                // Reload the list after deletion
                                loadAllGames();
                            })
                            .addOnFailureListener(e -> {
                                errorLiveData.setValue("Failed to delete game data: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    errorLiveData.setValue("Failed to delete game: " + e.getMessage());
                });
    }

    public void updateGameStatus(String gameId, String newStatus) {
        // Update the game status in the original gameData collection
        db.collection(FirestoreCollections.GAME_DATA)
                .document(gameId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        GameDataWrapper gameDataWrapper = documentSnapshot.toObject(GameDataWrapper.class);
                        if (gameDataWrapper != null && gameDataWrapper.getData() != null) {
                            GameData gameData = gameDataWrapper.getData();
                            gameData.setGameStatus(newStatus);
                            
                            // Update the wrapper with new timestamp
                            gameDataWrapper.setLastUpdated(com.google.firebase.Timestamp.now());
                            gameDataWrapper.setData(gameData);
                            preserveEditGeneration(documentSnapshot, gameDataWrapper);
                            
                            // Save back to Firestore
                            db.collection(FirestoreCollections.GAME_DATA)
                                    .document(gameId)
                                    .set(gameDataWrapper)
                                    .addOnSuccessListener(aVoid -> {
                                        // Reload the games list to reflect the change
                                        loadAllGames();
                                    })
                                    .addOnFailureListener(e -> {
                                        errorLiveData.setValue("Failed to update game status: " + e.getMessage());
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    errorLiveData.setValue("Failed to update game status: " + e.getMessage());
                });
    }

    /**
     * Update point value and contribution (GST) percent for a game. Contribution amount in the UI
     * is derived on reload via {@link GameData#getGstAmount()}.
     *
     * @param onSuccess optional; runs on the main thread after Firestore write succeeds (before list refresh completes).
     */
    public void updateGameEconomics(String gameId, double pointValue, double gstPercent, Runnable onSuccess) {
        if (gameId == null || gameId.isEmpty()) {
            errorLiveData.setValue("Cannot update game: missing game id");
            return;
        }
        db.collection(FirestoreCollections.GAME_DATA)
                .document(gameId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        errorLiveData.setValue("Game data not found");
                        return;
                    }
                    GameDataWrapper gameDataWrapper = documentSnapshot.toObject(GameDataWrapper.class);
                    if (gameDataWrapper == null || gameDataWrapper.getData() == null) {
                        errorLiveData.setValue("Failed to load game data for update");
                        return;
                    }
                    GameData gameData = gameDataWrapper.getData();
                    gameData.setPointValue(pointValue);
                    gameData.setGstPercent(gstPercent);
                    gameDataWrapper.setLastUpdated(com.google.firebase.Timestamp.now());
                    gameDataWrapper.setData(gameData);
                    preserveEditGeneration(documentSnapshot, gameDataWrapper);

                    db.collection(FirestoreCollections.GAME_DATA)
                            .document(gameId)
                            .set(gameDataWrapper)
                            .addOnSuccessListener(aVoid -> {
                                syncDashboardSummaryOnGameDoc(gameId, gameData);
                                loadAllGames();
                                if (onSuccess != null) {
                                    onSuccess.run();
                                }
                            })
                            .addOnFailureListener(e ->
                                    errorLiveData.setValue("Failed to update game: " + e.getMessage()));
                })
                .addOnFailureListener(e ->
                        errorLiveData.setValue("Failed to load game data: " + e.getMessage()));
    }

    public void approveGame(GameItem gameItem) {
        approveGame(gameItem, null);
    }

    /**
     * @param onAfterFullSuccess optional; runs after game is written to approvedGames and removed from games/gameData
     */
    public void approveGame(GameItem gameItem, Runnable onAfterFullSuccess) {
        if (gameItem == null || !gameItem.isCompleted()) {
            errorLiveData.setValue("Game must be completed before approval.");
            return;
        }
        List<GameItem> single = new ArrayList<>();
        single.add(gameItem);
        approveGamesAtomically(single, onAfterFullSuccess);
    }

    /**
     * Approves every completed game in one all-or-nothing Firestore transaction.
     */
    public void approveAllCompletedGames(List<GameItem> games, Runnable onAllComplete) {
        if (games == null || games.isEmpty()) {
            errorLiveData.setValue("No completed games to approve.");
            return;
        }
        List<GameItem> completed = new ArrayList<>();
        for (GameItem g : games) {
            if (g != null && g.isCompleted()) {
                completed.add(g);
            }
        }
        if (completed.isEmpty()) {
            errorLiveData.setValue("No completed games to approve.");
            return;
        }
        approveGamesAtomically(completed, onAllComplete);
    }

    private void approveGamesAtomically(List<GameItem> requested, Runnable onSuccess) {
        LinkedHashMap<String, GameItem> uniqueById = new LinkedHashMap<>();
        for (GameItem game : requested) {
            if (game == null || game.getGameId() == null || game.getGameId().trim().isEmpty()) {
                errorLiveData.setValue("Approval validation failed: a selected game has no id.");
                return;
            }
            uniqueById.put(game.getGameId(), game);
        }
        List<GameItem> games = new ArrayList<>(uniqueById.values());
        try {
            ApprovalBatchValidator.validateSelectionCount(games.size());
        } catch (IllegalArgumentException error) {
            errorLiveData.setValue(error.getMessage());
            return;
        }

        List<String> gameIds = new ArrayList<>(uniqueById.keySet());
        long preparationStartedAt = android.os.SystemClock.elapsedRealtime();
        viewApprovalRepository.loadDocumentReferencesForGames(
                gameIds,
                new GameViewApprovalRepository.DocumentReferencesCallback() {
                    @Override
                    public void onSuccess(@NonNull List<DocumentReference> cleanupReferences) {
                        try {
                            ApprovalBatchValidator.validateWriteCount(
                                    games.size(), cleanupReferences.size());
                        } catch (IllegalArgumentException error) {
                            errorLiveData.setValue(error.getMessage());
                            return;
                        }
                        runApprovalTransaction(
                                games,
                                gameIds,
                                cleanupReferences,
                                preparationStartedAt,
                                onSuccess);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        errorLiveData.setValue(message);
                    }
                });
    }

    private void runApprovalTransaction(
            List<GameItem> games,
            List<String> gameIds,
            List<DocumentReference> cleanupReferences,
            long startedAt,
            Runnable onSuccess) {
        db.runTransaction(transaction -> {
                    List<DocumentSnapshot> gameDataSnapshots = new ArrayList<>();
                    for (String gameId : gameIds) {
                        gameDataSnapshots.add(transaction.get(
                                db.collection(FirestoreCollections.GAME_DATA)
                                        .document(gameId)));
                    }

                    List<ApprovedGameData> approvedGames = new ArrayList<>();
                    for (int index = 0; index < games.size(); index++) {
                        GameItem gameItem = games.get(index);
                        DocumentSnapshot snapshot = gameDataSnapshots.get(index);
                        if (!snapshot.exists()) {
                            throw new IllegalStateException(
                                    "Validation failed for game " + gameItem.getGameId()
                                            + ": gameData_v2 document is missing.");
                        }
                        GameDataWrapper wrapper = snapshot.toObject(GameDataWrapper.class);
                        GameData gameData = ApprovalBatchValidator.validateGameData(
                                gameItem.getGameId(), wrapper);
                        approvedGames.add(buildApprovedGame(gameItem, wrapper, gameData));
                    }

                    for (int index = 0; index < games.size(); index++) {
                        String gameId = gameIds.get(index);
                        transaction.set(
                                db.collection(FirestoreCollections.APPROVED_GAMES)
                                        .document(gameId),
                                approvedGames.get(index));
                        transaction.delete(
                                db.collection(FirestoreCollections.GAMES).document(gameId));
                        transaction.delete(
                                db.collection(FirestoreCollections.GAME_DATA).document(gameId));
                    }
                    for (DocumentReference cleanupReference : cleanupReferences) {
                        transaction.delete(cleanupReference);
                    }
                    return games.size();
                })
                .addOnSuccessListener(approvedCount -> {
                    long elapsed = android.os.SystemClock.elapsedRealtime() - startedAt;
                    android.util.Log.d("GameRepository",
                            "Atomic approval committed: games=" + approvedCount
                                    + " reads=" + approvedCount
                                    + " writes=" + (approvedCount * 3
                                    + cleanupReferences.size())
                                    + " elapsedMs=" + elapsed);
                    loadAllGames();
                    loadApprovedGames();
                    viewApprovalRepository.cleanupAfterGamesRemoved(gameIds);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(error -> {
                    String detail = error.getMessage() != null
                            ? error.getMessage()
                            : error.getClass().getSimpleName();
                    errorLiveData.setValue("Atomic approval failed; no games were changed. "
                            + detail);
                });
    }

    private ApprovedGameData buildApprovedGame(
            GameItem gameItem,
            GameDataWrapper wrapper,
            GameData gameData) {
        Map<String, Integer> playerScores = new HashMap<>();
        if (gameData.getPlayers() != null) {
            for (Player player : gameData.getPlayers()) {
                playerScores.put(player.getName(), player.getTotalScore());
            }
        }
        String gstAmount = gameItem.getGstAmount();
        if (gstAmount == null || gstAmount.trim().isEmpty()) {
            gstAmount = Double.toString(gameData.getGstAmount());
        }
        return new ApprovedGameData(
                gameItem.getGameId(),
                gameData.getNumPlayers(),
                gameData.getPointValue(),
                gameData.getGstPercent(),
                playerScores,
                com.google.firebase.Timestamp.now(),
                wrapper.getVersion(),
                gstAmount,
                "Completed",
                gameItem.getCreationDateTime());
    }

    private void updateGameStatusInOriginal(String gameId, String newStatus) {
        // Update the game status in the original gameData collection
        db.collection(FirestoreCollections.GAME_DATA)
                .document(gameId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        GameDataWrapper gameDataWrapper = documentSnapshot.toObject(GameDataWrapper.class);
                        if (gameDataWrapper != null && gameDataWrapper.getData() != null) {
                            GameData gameData = gameDataWrapper.getData();
                            gameData.setGameStatus(newStatus);
                            
                            // Update the wrapper with new timestamp
                            gameDataWrapper.setLastUpdated(com.google.firebase.Timestamp.now());
                            gameDataWrapper.setData(gameData);
                            preserveEditGeneration(documentSnapshot, gameDataWrapper);
                            
                            // Save back to Firestore
                            db.collection(FirestoreCollections.GAME_DATA)
                                    .document(gameId)
                                    .set(gameDataWrapper)
                                    .addOnSuccessListener(aVoid -> {
                                        // Reload the games list to reflect the change
                                        loadAllGames();
                                    })
                                    .addOnFailureListener(e -> {
                                        errorLiveData.setValue("Failed to update game status: " + e.getMessage());
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    errorLiveData.setValue("Failed to update game status: " + e.getMessage());
                });
    }

    private static void preserveEditGeneration(DocumentSnapshot snapshot, GameDataWrapper wrapper) {
        if (snapshot == null || wrapper == null || !snapshot.contains("editGeneration")) {
            return;
        }
        Long editGen = snapshot.getLong("editGeneration");
        if (editGen != null) {
            wrapper.setEditGeneration(editGen);
        }
    }

    /**
     * Load approved games with real-time listener (for Dashboard)
     */
    public void loadApprovedGamesWithRealtimeListener() {
        System.out.println("GameRepository: Setting up real-time listener for approved games collection");
        
        // Remove existing listener if any
        if (approvedGamesListener != null) {
            approvedGamesListener.remove();
        }
        
        // Set up real-time listener for approved games collection
        approvedGamesListener = db.collection(FirestoreCollections.APPROVED_GAMES)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        System.out.println("GameRepository: Error listening to approved games: " + error.getMessage());
                        errorLiveData.setValue("Failed to load approved games: " + error.getMessage());
                        totalApprovedGstLiveData.setValue(0.0);
                        approvedGamesCountLiveData.setValue(0);
                        return;
                    }
                    
                    if (querySnapshot != null) {
                        double totalGst = 0.0;
                        int approvedCount = 0;
                        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                            try {
                                ApprovedGameData approvedGame = document.toObject(ApprovedGameData.class);
                                if (approvedGame != null) {
                                    totalGst += approvedGame.getGstAmountAsDouble();
                                    approvedCount++;
                                }
                            } catch (Exception e) {
                                System.out.println("Error parsing approved game: " + e.getMessage());
                            }
                        }
                        totalApprovedGstLiveData.setValue(totalGst);
                        approvedGamesCountLiveData.setValue(approvedCount);
                        System.out.println("Total approved GST amount: ₹" + String.format("%.0f", totalGst));
                        System.out.println("Total approved games count: " + approvedCount);
                    }
                });
    }
    
    /**
     * Load approved games with one-time fetch (for Review screen - manual refresh only)
     */
    public void loadApprovedGames() {
        System.out.println("GameRepository: Fetching approved games collection (manual refresh)");
        
        // One-time fetch for approved games collection
        db.collection(FirestoreCollections.APPROVED_GAMES)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null) {
                        double totalGst = 0.0;
                        int approvedCount = 0;
                        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                            try {
                                ApprovedGameData approvedGame = document.toObject(ApprovedGameData.class);
                                if (approvedGame != null) {
                                    totalGst += approvedGame.getGstAmountAsDouble();
                                    approvedCount++;
                                }
                            } catch (Exception e) {
                                System.out.println("Error parsing approved game: " + e.getMessage());
                            }
                        }
                        totalApprovedGstLiveData.setValue(totalGst);
                        approvedGamesCountLiveData.setValue(approvedCount);
                        System.out.println("Total approved GST amount: ₹" + String.format("%.0f", totalGst));
                        System.out.println("Total approved games count: " + approvedCount);
                    }
                })
                .addOnFailureListener(error -> {
                    System.out.println("GameRepository: Error fetching approved games: " + error.getMessage());
                    errorLiveData.setValue("Failed to load approved games: " + error.getMessage());
                    totalApprovedGstLiveData.setValue(0.0);
                    approvedGamesCountLiveData.setValue(0);
                });
    }

    /**
     * Loads pre-aggregated month documents (one read per month doc, no composite indexes).
     */
    public void loadReportsFromSavedSummaries() {
        db.collection(FirestoreCollections.APPROVED_GAMES_REPORT)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Pair<String, MonthlyPointValueReport>> tagged = new ArrayList<>();
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        try {
                            ApprovedGamesReportMonth month = document.toObject(ApprovedGamesReportMonth.class);
                            if (month != null) {
                                tagged.add(new Pair<>(document.getId(), month.toMonthlyPointValueReport()));
                            }
                        } catch (Exception e) {
                            System.out.println("Error parsing approvedGamesReport doc: " + e.getMessage());
                        }
                    }
                    tagged.sort((a, b) -> b.first.compareTo(a.first));
                    List<MonthlyPointValueReport> out = new ArrayList<>();
                    for (Pair<String, MonthlyPointValueReport> p : tagged) {
                        out.add(p.second);
                    }
                    reportsSummariesLiveData.setValue(out);
                })
                .addOnFailureListener(e -> {
                    errorLiveData.setValue("Failed to load reports: " + e.getMessage());
                    reportsSummariesLiveData.setValue(new ArrayList<>());
                });
    }

    /**
     * Rebuilds {@code approvedGamesReport_v2/{yyyy-MM}} for the selected calendar month.
     * Uses a full collection read without {@code orderBy} (no composite index), then filters in memory
     * by {@link ReportAggregator#yearMonthKey(ApprovedGameData)} so games without {@code approvedAt}
     * still align with month grouping.
     */
    public void rebuildApprovedGamesReportForMonth(int year, int monthZeroBased,
                                                   Runnable onSuccess, Consumer<String> onFailure) {
        if (monthZeroBased < Calendar.JANUARY || monthZeroBased > Calendar.DECEMBER) {
            if (onFailure != null) {
                onFailure.accept("Invalid report month");
            }
            return;
        }
        final String yyyyMm = String.format(Locale.US, "%04d-%02d", year, monthZeroBased + 1);
        db.collection(FirestoreCollections.APPROVED_GAMES)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<ApprovedGameData> monthGames = new ArrayList<>();
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        try {
                            ApprovedGameData g = document.toObject(ApprovedGameData.class);
                            if (g != null && yyyyMm.equals(ReportAggregator.yearMonthKey(g))) {
                                monthGames.add(g);
                            }
                        } catch (Exception e) {
                            System.out.println("Error parsing approved game: " + e.getMessage());
                        }
                    }
                    MonthlyPointValueReport report = ReportAggregator.buildMonthlyPointValueReport(yyyyMm, monthGames);
                    ApprovedGamesReportMonth doc = new ApprovedGamesReportMonth(
                            report.getMonthYear(),
                            report.getPointValueReports(),
                            Timestamp.now());
                    db.collection(FirestoreCollections.APPROVED_GAMES_REPORT)
                            .document(yyyyMm)
                            .set(doc)
                            .addOnSuccessListener(aVoid -> {
                                if (onSuccess != null) {
                                    onSuccess.run();
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (onFailure != null) {
                                    onFailure.accept(e.getMessage());
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (onFailure != null) {
                        onFailure.accept(e.getMessage());
                    }
                });
    }

    /**
     * Rebuilds {@code approvedGamesReport_v2/{yyyy-MM}} for the current calendar month.
     */
    public void rebuildApprovedGamesReportForCurrentMonth(Runnable onSuccess, Consumer<String> onFailure) {
        Calendar cal = Calendar.getInstance();
        rebuildApprovedGamesReportForMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                onSuccess, onFailure);
    }

    /**
     * TODO: Remove this after initial backfill — reads all {@code approvedGames} and writes one summary
     * document per calendar month (batched, max 450 writes per batch).
     */
    public void rebuildAllApprovedGamesReports(Runnable onSuccess, Consumer<String> onFailure) {
        db.collection(FirestoreCollections.APPROVED_GAMES)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<ApprovedGameData> all = new ArrayList<>();
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        try {
                            ApprovedGameData g = document.toObject(ApprovedGameData.class);
                            if (g != null) {
                                all.add(g);
                            }
                        } catch (Exception e) {
                            System.out.println("Error parsing approved game: " + e.getMessage());
                        }
                    }
                    Map<String, List<ApprovedGameData>> byMonth = new HashMap<>();
                    for (ApprovedGameData g : all) {
                        String key = ReportAggregator.yearMonthKey(g);
                        if (key == null) {
                            continue;
                        }
                        byMonth.computeIfAbsent(key, k -> new ArrayList<>()).add(g);
                    }
                    if (byMonth.isEmpty()) {
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                        return;
                    }
                    List<Map.Entry<String, List<ApprovedGameData>>> entries = new ArrayList<>(byMonth.entrySet());
                    commitReportsBatch(entries, 0, onSuccess, onFailure);
                })
                .addOnFailureListener(e -> {
                    if (onFailure != null) {
                        onFailure.accept(e.getMessage());
                    }
                });
    }

    private void commitReportsBatch(List<Map.Entry<String, List<ApprovedGameData>>> entries, int start,
            Runnable onSuccess, Consumer<String> onFailure) {
        if (start >= entries.size()) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }
        int end = Math.min(start + 450, entries.size());
        WriteBatch batch = db.batch();
        for (int i = start; i < end; i++) {
            Map.Entry<String, List<ApprovedGameData>> e = entries.get(i);
            MonthlyPointValueReport report = ReportAggregator.buildMonthlyPointValueReport(e.getKey(), e.getValue());
            ApprovedGamesReportMonth doc = new ApprovedGamesReportMonth(
                    report.getMonthYear(),
                    report.getPointValueReports(),
                    Timestamp.now());
            batch.set(db.collection(FirestoreCollections.APPROVED_GAMES_REPORT).document(e.getKey()), doc);
        }
        int nextStart = end;
        batch.commit()
                .addOnSuccessListener(aVoid -> commitReportsBatch(entries, nextStart, onSuccess, onFailure))
                .addOnFailureListener(e -> {
                    if (onFailure != null) {
                        onFailure.accept(e.getMessage());
                    }
                });
    }

}
