package com.example.rummypulse.data.sync;

import android.text.TextUtils;

import com.example.rummypulse.data.FirestoreCollections;
import com.example.rummypulse.data.GameAuth;
import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.GameDataSchema;
import com.example.rummypulse.data.GameDataWrapper;
import com.example.rummypulse.data.GameMembership;
import com.example.rummypulse.data.GameViewApprovalRepository;
import com.example.rummypulse.data.Player;
import com.example.rummypulse.data.ScoreHistoryEvent;
import com.example.rummypulse.data.ScoreRegressionGuard;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class GameOperationRemoteApplier {
    static final class Result {
        final GameData gameData;
        final long revision;

        Result(GameData gameData, long revision) {
            this.gameData = gameData;
            this.revision = revision;
        }
    }

    private static final Gson GSON = new Gson();

    private GameOperationRemoteApplier() {
    }

    static Task<Result> apply(
            FirebaseFirestore db, String editorUserId, PendingGameOperation operation) {
        DocumentReference gameRef =
                db.collection(FirestoreCollections.GAMES).document(operation.gameId);
        DocumentReference dataRef =
                db.collection(FirestoreCollections.GAME_DATA).document(operation.gameId);
        return db.runTransaction(transaction -> {
            DocumentSnapshot authSnapshot = transaction.get(gameRef);
            DocumentSnapshot dataSnapshot = transaction.get(dataRef);
            validateEditor(authSnapshot, dataSnapshot, editorUserId, operation.editGeneration);
            GameAuth auth = authSnapshot.toObject(GameAuth.class);

            GameDataWrapper wrapper = dataSnapshot.toObject(GameDataWrapper.class);
            GameData latest = wrapper != null ? wrapper.getData() : null;
            if (latest == null) {
                throw new IllegalStateException("Game data is unavailable.");
            }
            GameDataSchema.normalize(latest);
            Long storedRevision = dataSnapshot.getLong("revision");
            long previousRevision = storedRevision == null ? 0L : storedRevision;
            if (operation.operationId.equals(dataSnapshot.getString("lastOperationId"))) {
                return new Result(latest, previousRevision);
            }
            GameOperationPayload payload =
                    GSON.fromJson(operation.payloadJson, GameOperationPayload.class);
            String mappedUserId = mappedUserId(operation.operationType(), payload);
            boolean mappedUserCanSignIn = true;
            if (!TextUtils.isEmpty(mappedUserId)) {
                DocumentSnapshot profileSnapshot = transaction.get(
                        db.collection(FirestoreCollections.APP_USER).document(mappedUserId));
                applyCanonicalProfile(operation.operationType(), payload, profileSnapshot);
                mappedUserCanSignIn = !isManagedProfile(profileSnapshot);
            }
            Player targetBefore = operation.playerId == null
                    ? null
                    : GameDataSchema.findPlayer(latest, operation.playerId);
            String previousTargetUserId =
                    targetBefore == null ? null : targetBefore.getUserId();
            Player deletedBefore = operation.operationType() == GameOperationType.DELETE_PLAYER
                    && targetBefore != null
                    ? GameDataCopies.copyPlayer(targetBefore)
                    : null;
            GameData patched = GameOperationProjector.apply(
                    latest, operation.operationType(), operation.playerId, payload);
            long nextRevision = previousRevision + 1L;
            validateScoreMutation(latest, patched, operation.operationType(), payload);
            transaction.set(
                    dataRef,
                    buildGameDataDocument(
                            patched,
                            operation.editGeneration,
                            nextRevision,
                            operation.operationId));

            writeScoreHistory(transaction, db, operation, payload, latest, patched,
                    editorUserId, previousRevision, nextRevision);

            applyApprovalSideEffects(
                    transaction,
                    db,
                    gameRef,
                    operation,
                    payload,
                    previousTargetUserId,
                    deletedBefore,
                    mappedUserCanSignIn);
            if (affectsDashboard(operation.operationType())) {
                transaction.update(gameRef, buildDashboardSummary(patched, auth));
            }
            return new Result(patched, nextRevision);
        });
    }

    private static void validateScoreMutation(GameData latest, GameData patched,
            GameOperationType type, GameOperationPayload payload) {
        if (type == GameOperationType.UPDATE_SCORE) {
            if (payload == null || payload.round1Based == null
                    || payload.scoresByPlayerId == null
                    || payload.scoresByPlayerId.isEmpty()) {
                throw new IllegalStateException("A round score operation is incomplete.");
            }
            ScoreRegressionGuard.requireOnlyRoundChanged(latest, patched,
                    payload.round1Based, new HashSet<>(payload.scoresByPlayerId.keySet()));
        } else if (type != GameOperationType.ADD_PLAYER
                && type != GameOperationType.DELETE_PLAYER) {
            ScoreRegressionGuard.requireMetadataPreservesScores(latest, patched);
        }
    }

    private static String mappedUserId(
            GameOperationType type, GameOperationPayload payload) {
        if (payload == null) return null;
        if (type == GameOperationType.MAP_USER) return payload.userId;
        if (type == GameOperationType.ADD_PLAYER && payload.player != null) {
            return payload.player.getUserId();
        }
        return null;
    }

    private static void applyCanonicalProfile(
            GameOperationType type,
            GameOperationPayload payload,
            DocumentSnapshot profileSnapshot) {
        String profileName = profileSnapshot == null
                ? null : profileSnapshot.getString("profileName");
        Boolean hidden = profileSnapshot == null
                ? null : profileSnapshot.getBoolean("hidden");
        if (profileSnapshot == null || !profileSnapshot.exists()
                || TextUtils.isEmpty(profileName) || Boolean.TRUE.equals(hidden)) {
            throw new IllegalStateException(
                    "Select an available player profile before saving.");
        }
        if (type == GameOperationType.MAP_USER) {
            payload.name = profileName;
        } else if (type == GameOperationType.ADD_PLAYER && payload.player != null) {
            payload.player.setName(profileName);
        }
    }

    private static boolean isManagedProfile(DocumentSnapshot profileSnapshot) {
        String profileType = profileSnapshot.getString("profileType");
        String provider = profileSnapshot.getString("provider");
        return "managed".equalsIgnoreCase(profileType) || "managed".equalsIgnoreCase(provider);
    }

    private static void writeScoreHistory(Transaction transaction, FirebaseFirestore db,
            PendingGameOperation operation, GameOperationPayload payload, GameData latest,
            GameData patched, String editorUserId, long previousRevision,
            long committedRevision) {
        Set<Integer> rounds = new HashSet<>();
        GameData snapshotSource = patched;
        if (operation.operationType() == GameOperationType.UPDATE_SCORE
                && payload != null && payload.round1Based != null) {
            rounds.add(payload.round1Based);
        } else if (operation.operationType() == GameOperationType.ADD_PLAYER
                && payload != null && payload.player != null) {
            for (int round = 1; round <= 10; round++) {
                if (com.example.rummypulse.data.GameIntegrityValidator.hasValidScore(
                        payload.player, round)) {
                    rounds.add(round);
                }
            }
        } else if (operation.operationType() == GameOperationType.DELETE_PLAYER) {
            // Preserve the removed player's final score matrix before deleting the
            // player from the canonical document.
            snapshotSource = latest;
            for (int round = 1; round <= 10; round++) rounds.add(round);
        }
        for (Integer round : rounds) {
            Map<String, Object> event = ScoreHistoryEvent.create(
                    operation.gameId, round, snapshotSource, operation.operationId,
                    operation.operationType() == GameOperationType.ADD_PLAYER
                            ? "MID_GAME_BACKFILL"
                            : operation.operationType() == GameOperationType.DELETE_PLAYER
                                    ? "PLAYER_REMOVAL_SNAPSHOT"
                            : Boolean.TRUE.equals(payload.correction)
                                    ? "ROUND_CORRECTION" : "ROUND_SAVE",
                    editorUserId, operation.editGeneration, previousRevision,
                    committedRevision);
            DocumentReference eventRef = db.collection(FirestoreCollections.GAME_SCORE_HISTORY)
                    .document(operation.gameId)
                    .collection("rounds")
                    .document(String.valueOf(round))
                    .collection("events")
                    .document(operation.operationId);
            transaction.set(eventRef, event);
        }
    }

    private static void applyApprovalSideEffects(
            Transaction transaction,
            FirebaseFirestore db,
            DocumentReference gameRef,
            PendingGameOperation operation,
            GameOperationPayload payload,
            String previousTargetUserId,
            Player deletedBefore,
            boolean mappedUserCanSignIn) {
        GameOperationType type = operation.operationType();
        if (type == GameOperationType.ADD_PLAYER
                && payload != null
                && payload.player != null) {
            updateApprovalForMappedUser(transaction, db, gameRef, operation.gameId,
                    payload.player.getUserId(), mappedUserCanSignIn);
        } else if (type == GameOperationType.MAP_USER) {
            if (!TextUtils.isEmpty(previousTargetUserId)
                    && !previousTargetUserId.equals(payload.userId)) {
                revokeApproval(
                        transaction, db, gameRef, operation.gameId, previousTargetUserId);
            }
            updateApprovalForMappedUser(transaction, db, gameRef, operation.gameId,
                    payload.userId, mappedUserCanSignIn);
        } else if (type == GameOperationType.UNMAP_USER
                && !TextUtils.isEmpty(previousTargetUserId)) {
            revokeApproval(
                    transaction, db, gameRef, operation.gameId, previousTargetUserId);
        } else if (type == GameOperationType.DELETE_PLAYER
                && deletedBefore != null
                && !TextUtils.isEmpty(deletedBefore.getUserId())) {
            revokeApproval(
                    transaction, db, gameRef, operation.gameId, deletedBefore.getUserId());
        } else if (type == GameOperationType.TRANSFER_MAPPING) {
            // The same user keeps view approval; only the owning playerId changes.
            approve(
                    transaction,
                    db,
                    gameRef,
                    operation.gameId,
                    payload.userId);
        }
    }

    private static void updateApprovalForMappedUser(Transaction transaction, FirebaseFirestore db,
            DocumentReference gameRef, String gameId, String userId, boolean canSignIn) {
        if (canSignIn) {
            approve(transaction, db, gameRef, gameId, userId);
        } else {
            revokeApproval(transaction, db, gameRef, gameId, userId);
        }
    }

    private static void approve(
            Transaction transaction,
            FirebaseFirestore db,
            DocumentReference gameRef,
            String gameId,
            String userId) {
        if (TextUtils.isEmpty(userId)) {
            throw new IllegalArgumentException("Mapped user is required.");
        }
        DocumentReference approvalRef =
                db.collection(FirestoreCollections.GAME_VIEW_APPROVALS)
                        .document(GameViewApprovalRepository.documentId(gameId, userId));
        Map<String, Object> approval = new HashMap<>();
        approval.put("gameId", gameId);
        approval.put("userId", userId);
        approval.put("status", "approved");
        approval.put("requestedAt", FieldValue.serverTimestamp());
        approval.put("lastUpdatedAt", FieldValue.serverTimestamp());
        transaction.set(approvalRef, approval);

        Map<String, Object> mirrored = new HashMap<>();
        mirrored.put("status", "approved");
        mirrored.put("requestedAt", FieldValue.serverTimestamp());
        mirrored.put("lastUpdatedAt", FieldValue.serverTimestamp());
        transaction.update(
                gameRef,
                FieldPath.of(GameViewApprovalRepository.PENDING_VIEW_REQUESTS_FIELD, userId),
                mirrored);
    }

    private static void revokeApproval(
            Transaction transaction,
            FirebaseFirestore db,
            DocumentReference gameRef,
            String gameId,
            String userId) {
        DocumentReference approvalRef =
                db.collection(FirestoreCollections.GAME_VIEW_APPROVALS)
                        .document(GameViewApprovalRepository.documentId(gameId, userId));
        transaction.delete(approvalRef);
        transaction.update(
                gameRef,
                FieldPath.of(GameViewApprovalRepository.PENDING_VIEW_REQUESTS_FIELD, userId),
                FieldValue.delete());
    }

    private static void validateEditor(
            DocumentSnapshot authSnapshot,
            DocumentSnapshot dataSnapshot,
            String editorUserId,
            long expectedGeneration) {
        if (!authSnapshot.exists() || !dataSnapshot.exists()) {
            throw new IllegalStateException("Game data is no longer available.");
        }
        GameAuth auth = authSnapshot.toObject(GameAuth.class);
        if (auth == null
                || TextUtils.isEmpty(editorUserId)
                || !editorUserId.equals(auth.getActiveEditorUserId())
                || auth.getPinGenerationOrDefault() != expectedGeneration) {
            throw new IllegalStateException("Edit access changed.");
        }
        Long dataGeneration = dataSnapshot.getLong("editGeneration");
        long actualGeneration =
                dataGeneration == null || dataGeneration <= 0 ? 1L : dataGeneration;
        if (actualGeneration != expectedGeneration) {
            throw new IllegalStateException("Edit access changed.");
        }
    }

    private static Map<String, Object> buildGameDataDocument(
            GameData gameData,
            long editGeneration,
            long revision,
            String operationId) {
        Map<String, Object> document = new HashMap<>();
        document.put("data", GameDataSchema.toFirestoreData(gameData));
        document.put("lastUpdated", FieldValue.serverTimestamp());
        document.put("version", "2.0");
        document.put("editGeneration", editGeneration);
        document.put("revision", revision);
        document.put("lastOperationId", operationId);
        return document;
    }

    private static boolean affectsDashboard(GameOperationType type) {
        return type == GameOperationType.UPDATE_SCORE
                || type == GameOperationType.ADD_PLAYER
                || type == GameOperationType.DELETE_PLAYER;
    }

    private static Map<String, Object> buildDashboardSummary(GameData gameData, GameAuth auth) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("dashboardGamePointFactor", gameData.getGamePointFactor());
        summary.put("dashboardNumPlayers", gameData.getPlayers().size());
        summary.put("dashboardBoardAdjustmentPercent", gameData.getBoardAdjustmentPercent());
        String status = gameData.getGameStatus();
        summary.put("dashboardGameStatus",
                status == null || status.trim().isEmpty() ? "R1" : status.trim());
        summary.put(GameMembership.FIELD, GameMembership.resolve(
                gameData,
                auth == null ? null : auth.getCreatorUserId(),
                auth == null ? null : auth.getActiveEditorUserId()));
        return summary;
    }
}
