package com.example.rummypulse.data.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.room.RoomDatabase;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.rummypulse.data.GameData;
import com.example.rummypulse.data.GameDataSchema;
import com.google.gson.Gson;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Durable local operation log and optimistic state projector.
 */
public final class GameOperationRepository {
    public interface Callback {
        void onStored(GameData projected);

        void onError(String message);
    }

    public interface DraftCallback {
        void onLoaded(String serializedDraft);
    }

    public interface PendingPlayersCallback {
        void onLoaded(Set<String> playerIds);
    }

    private static final Gson GSON = new Gson();
    private static volatile GameOperationRepository instance;

    private final Context appContext;
    private final GameOperationDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private GameOperationRepository(Context context) {
        appContext = context.getApplicationContext();
        database = GameOperationDatabase.getInstance(appContext);
    }

    public static GameOperationRepository getInstance(Context context) {
        GameOperationRepository current = instance;
        if (current == null) {
            synchronized (GameOperationRepository.class) {
                current = instance;
                if (current == null) {
                    current = new GameOperationRepository(context);
                    instance = current;
                }
            }
        }
        return current;
    }

    /**
     * Restarts a previously backed-off queue when the game returns to the foreground.
     * The operation records remain in Room, so replacing only the scheduler job is safe.
     */
    public void resumePendingSync(String gameId) {
        if (gameId == null) {
            return;
        }
        executor.execute(() -> {
            if (database.operations().getNextPending(gameId) != null) {
                schedule(gameId, ExistingWorkPolicy.REPLACE);
            }
        });
    }

    public void saveAcknowledgedSnapshot(
            String gameId, GameData gameData, long revision, long editGeneration) {
        if (gameId == null || gameData == null) {
            return;
        }
        GameData normalized = GameDataCopies.deepCopy(gameData);
        executor.execute(() -> database.runInTransaction(() -> {
            GameSnapshotEntity existing = database.operations().getSnapshot(gameId);
            if (existing == null || revision >= existing.revision) {
                database.operations().upsertSnapshot(
                        new GameSnapshotEntity(
                                gameId,
                                GSON.toJson(normalized),
                                revision,
                                editGeneration,
                                System.currentTimeMillis()));
            }
        }));
    }

    public void saveRoundDraft(
            String gameId, long editGeneration, String serializedDraft) {
        if (gameId == null || serializedDraft == null) {
            return;
        }
        executor.execute(() -> database.operations().upsertRoundDraft(
                new RoundScoreDraftEntity(
                        gameId,
                        editGeneration,
                        serializedDraft,
                        System.currentTimeMillis())));
    }

    public void loadRoundDraft(
            String gameId, long editGeneration, DraftCallback callback) {
        if (gameId == null || callback == null) {
            return;
        }
        executor.execute(() -> {
            RoundScoreDraftEntity draft =
                    database.operations().getRoundDraft(gameId, editGeneration);
            mainHandler.post(() -> callback.onLoaded(
                    draft == null ? null : draft.serializedDraft));
        });
    }

    public void deleteRoundDraft(String gameId, long editGeneration) {
        if (gameId == null) {
            return;
        }
        executor.execute(() ->
                database.operations().deleteRoundDraft(gameId, editGeneration));
    }

    public void loadPendingPlayerIds(
            String gameId, PendingPlayersCallback callback) {
        if (gameId == null || callback == null) {
            return;
        }
        executor.execute(() -> {
            Set<String> playerIds = new HashSet<>();
            for (PendingGameOperation operation
                    : database.operations().getActiveOperations(gameId)) {
                if (operation.playerId != null) {
                    playerIds.add(operation.playerId);
                }
                GameOperationPayload payload =
                        GSON.fromJson(operation.payloadJson, GameOperationPayload.class);
                if (payload.playerOrder != null) {
                    playerIds.addAll(payload.playerOrder);
                }
                if (payload.scoresByPlayerId != null) {
                    playerIds.addAll(payload.scoresByPlayerId.keySet());
                }
                if (payload.fromPlayerId != null) {
                    playerIds.add(payload.fromPlayerId);
                }
            }
            mainHandler.post(() -> callback.onLoaded(playerIds));
        });
    }

    public void projectPending(String gameId, GameData serverData, Callback callback) {
        executor.execute(() -> {
            try {
                GameSnapshotEntity snapshot =
                        database.operations().getSnapshot(gameId);
                GameData projected = snapshot == null
                        ? GameDataCopies.deepCopy(serverData)
                        : GSON.fromJson(snapshot.snapshotJson, GameData.class);
                GameDataSchema.normalize(projected);
                for (PendingGameOperation operation
                        : database.operations().getActiveOperations(gameId)) {
                    projected = GameOperationProjector.apply(projected, operation);
                }
                postStored(callback, projected);
            } catch (RuntimeException error) {
                postError(callback, message(error));
            }
        });
    }

    /**
     * Loads the last acknowledged game plus every durable local operation. Used
     * when Firestore cannot be reached while reopening an editor session.
     */
    public void loadOfflineProjectedSnapshot(
            String gameId, long editGeneration, Callback callback) {
        executor.execute(() -> {
            try {
                GameSnapshotEntity snapshot = database.operations().getSnapshot(gameId);
                if (snapshot == null) {
                    throw new IllegalStateException(
                            "No offline game snapshot is available on this device.");
                }
                if (snapshot.editGeneration != editGeneration) {
                    throw new IllegalStateException(
                            "The offline game belongs to a different edit session.");
                }
                GameData projected = GSON.fromJson(snapshot.snapshotJson, GameData.class);
                GameDataSchema.normalize(projected);
                for (PendingGameOperation operation
                        : database.operations().getActiveOperations(gameId)) {
                    if (operation.editGeneration != editGeneration) {
                        throw new IllegalStateException(
                                "Pending edits belong to a different edit session.");
                    }
                    projected = GameOperationProjector.apply(projected, operation);
                }
                postStored(callback, projected);
            } catch (RuntimeException error) {
                postError(callback, message(error));
            }
        });
    }

    public void enqueue(
            String gameId,
            long editGeneration,
            GameData acknowledgedOrProjectedState,
            GameOperationType type,
            String playerId,
            GameOperationPayload payload,
            Callback callback) {
        executor.execute(() -> {
            try {
                if (gameId == null || gameId.trim().isEmpty()
                        || acknowledgedOrProjectedState == null) {
                    throw new IllegalArgumentException("Game state is unavailable.");
                }
                GameData fallback = GameDataCopies.deepCopy(acknowledgedOrProjectedState);
                GameDataSchema.normalize(fallback);
                boolean[] shouldSchedule = {false};
                GameData projected = database.runInTransaction(
                        (java.util.concurrent.Callable<GameData>) () -> {
                            GameSnapshotEntity snapshot =
                                    database.operations().getSnapshot(gameId);
                            if (snapshot == null) {
                                snapshot = new GameSnapshotEntity(
                                        gameId,
                                        GSON.toJson(fallback),
                                        0L,
                                        editGeneration,
                                        System.currentTimeMillis());
                                database.operations().upsertSnapshot(snapshot);
                            }
                            for (PendingGameOperation existing
                                    : database.operations().getActiveOperations(gameId)) {
                                if (existing.editGeneration != editGeneration) {
                                    throw new IllegalStateException(
                                            "Pending edits belong to an earlier edit session"
                                                    + " and must be recovered first.");
                                }
                            }
                            shouldSchedule[0] =
                                    database.operations().syncableOperationCount(gameId) == 0;
                            insertOrCoalesce(
                                    gameId,
                                    editGeneration,
                                    type,
                                    playerId,
                                    payload);
                            GameData value =
                                    GSON.fromJson(snapshot.snapshotJson, GameData.class);
                            GameDataSchema.normalize(value);
                            for (PendingGameOperation operation
                                    : database.operations().getActiveOperations(gameId)) {
                                value = GameOperationProjector.apply(value, operation);
                            }
                            return value;
                        });
                if (shouldSchedule[0]
                        && database.operations().syncableOperationCount(gameId) > 0) {
                    schedule(gameId, ExistingWorkPolicy.APPEND_OR_REPLACE);
                }
                postStored(callback, projected);
            } catch (Exception error) {
                postError(callback, message(error));
            }
        });
    }

    public int activeOperationCountBlocking(String gameId) {
        return database.operations().activeOperationCount(gameId);
    }

    private PendingGameOperation insertOrCoalesce(
            String gameId,
            long editGeneration,
            GameOperationType type,
            String playerId,
            GameOperationPayload payload) {
        GameOperationDao dao = database.operations();
        String payloadJson = GSON.toJson(payload == null
                ? new GameOperationPayload()
                : payload);

        if (type == GameOperationType.DELETE_PLAYER) {
            PendingGameOperation pendingAdd = dao.getLatestPending(
                    gameId, GameOperationType.ADD_PLAYER.name(), playerId);
            if (pendingAdd != null) {
                dao.deleteOperation(pendingAdd.operationId);
                return null;
            }
        }

        if (isReplaceable(type)) {
            PendingGameOperation existing =
                    dao.getLatestPending(gameId, type.name(), playerId);
            if (existing != null
                    && dao.replacePendingPayload(existing.operationId, payloadJson) == 1) {
                existing.payloadJson = payloadJson;
                return existing;
            }
        }

        if (type == GameOperationType.UPDATE_SCORE) {
            PendingGameOperation existing =
                    dao.getLatestPending(gameId, type.name(), null);
            if (existing != null) {
                GameOperationPayload previous =
                        GSON.fromJson(existing.payloadJson, GameOperationPayload.class);
                GameOperationPayload incoming =
                        GSON.fromJson(payloadJson, GameOperationPayload.class);
                if (previous.round1Based != null
                        && previous.round1Based.equals(incoming.round1Based)) {
                    previous.scoresByPlayerId.putAll(incoming.scoresByPlayerId);
                    String merged = GSON.toJson(previous);
                    if (dao.replacePendingPayload(existing.operationId, merged) == 1) {
                        existing.payloadJson = merged;
                        return existing;
                    }
                }
            }
        }

        PendingGameOperation operation = new PendingGameOperation(
                UUID.randomUUID().toString(),
                gameId,
                editGeneration,
                playerId,
                dao.nextSequence(gameId),
                type.name(),
                payloadJson,
                GameOperationStatus.PENDING.name(),
                0,
                null,
                System.currentTimeMillis());
        dao.insertOperation(operation);
        return operation;
    }

    private static boolean isReplaceable(GameOperationType type) {
        return type == GameOperationType.RENAME_PLAYER
                || type == GameOperationType.MAP_USER
                || type == GameOperationType.UNMAP_USER
                || type == GameOperationType.SET_PLAYER_ORDER;
    }

    private void schedule(String gameId, ExistingWorkPolicy policy) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        Data input = new Data.Builder()
                .putString(GameOperationSyncWorker.INPUT_GAME_ID, gameId)
                .build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(GameOperationSyncWorker.class)
                .setConstraints(constraints)
                .setInputData(input)
                .addTag("game-operation-sync")
                .addTag("game-operation-sync-" + gameId)
                .build();
        WorkManager.getInstance(appContext).enqueueUniqueWork(
                "game-operation-sync-" + gameId,
                policy,
                work);
    }

    private void postStored(Callback callback, GameData projected) {
        if (callback != null) {
            mainHandler.post(() -> callback.onStored(projected));
        }
    }

    private void postError(Callback callback, String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(message));
        }
    }

    @NonNull
    private static String message(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Could not store the pending game change."
                : message;
    }
}
