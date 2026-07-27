package com.example.rummypulse.data.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.gson.Gson;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GameOperationSyncWorker extends Worker {
    public static final String INPUT_GAME_ID = "gameId";
    private static final String TAG = "GameOperationSync";
    private static final long REMOTE_TIMEOUT_SECONDS = 30L;
    private static final Gson GSON = new Gson();

    public GameOperationSyncWorker(
            @NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String gameId = getInputData().getString(INPUT_GAME_ID);
        FirebaseUser editor = FirebaseAuth.getInstance().getCurrentUser();
        if (gameId == null) {
            return Result.failure();
        }
        if (editor == null) {
            return Result.retry();
        }
        GameOperationDatabase database =
                GameOperationDatabase.getInstance(getApplicationContext());
        GameOperationDao dao = database.operations();
        dao.resetInterruptedOperations(gameId);
        while (!isStopped()) {
            PendingGameOperation operation = dao.getNextPending(gameId);
            if (operation == null) {
                return Result.success();
            }
            dao.updateOperationState(
                    operation.operationId,
                    GameOperationStatus.IN_FLIGHT.name(),
                    1,
                    null);
            Log.i(TAG, "Synchronizing " + operation.type + " for game " + gameId);
            try {
                GameOperationRemoteApplier.Result remote = Tasks.await(
                        GameOperationRemoteApplier.apply(
                                FirebaseFirestore.getInstance(),
                                editor.getUid(),
                                operation),
                        REMOTE_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS);
                database.runInTransaction(() -> {
                    dao.upsertSnapshot(new GameSnapshotEntity(
                            gameId,
                            GSON.toJson(remote.gameData),
                            remote.revision,
                            operation.editGeneration,
                            System.currentTimeMillis()));
                    dao.deleteOperation(operation.operationId);
                });
                Log.i(TAG, "Synchronized " + operation.type + " for game " + gameId);
            } catch (TimeoutException timeout) {
                dao.updateOperationState(
                        operation.operationId,
                        GameOperationStatus.PENDING.name(),
                        0,
                        "Cloud sync timed out. Waiting to retry.");
                Log.w(TAG, "Cloud sync timed out for game " + gameId);
                return Result.retry();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                dao.updateOperationState(
                        operation.operationId,
                        GameOperationStatus.PENDING.name(),
                        0,
                        "Synchronization was interrupted.");
                return Result.retry();
            } catch (ExecutionException failure) {
                Throwable cause = rootCause(failure);
                String message = message(cause);
                if (isTransient(cause)) {
                    dao.updateOperationState(
                            operation.operationId,
                            GameOperationStatus.PENDING.name(),
                            0,
                            message);
                    return Result.retry();
                }
                dao.blockActiveOperations(gameId, message);
                return Result.failure();
            } catch (RuntimeException failure) {
                dao.blockActiveOperations(gameId, message(failure));
                return Result.failure();
            }
        }
        return Result.retry();
    }

    private static boolean isTransient(Throwable error) {
        if (!(error instanceof FirebaseFirestoreException)) {
            return false;
        }
        FirebaseFirestoreException.Code code =
                ((FirebaseFirestoreException) error).getCode();
        return code == FirebaseFirestoreException.Code.UNAVAILABLE
                || code == FirebaseFirestoreException.Code.ABORTED
                || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
                || code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable result = error;
        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result;
    }

    private static String message(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "The pending game change could not be synchronized."
                : message;
    }
}
