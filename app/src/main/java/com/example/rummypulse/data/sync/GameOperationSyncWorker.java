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
    /** Attempts allowed per operation before it is discarded instead of stalling the queue. */
    private static final int MAX_ATTEMPTS = 5;
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
        if (!GameOperationRepository.getInstance(getApplicationContext())
                .ownsQueueBlocking(editor.getUid())) {
            Log.w(TAG, "Queue belongs to another account or needs recovery");
            return Result.retry();
        }
        try {
            Tasks.await(editor.getIdToken(false), REMOTE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception authError) {
            Log.w(TAG, "Waiting for Firebase session recovery", authError);
            return Result.retry();
        }
        GameOperationDatabase database =
                GameOperationDatabase.getInstance(getApplicationContext());
        GameOperationDao dao = database.operations();
        dao.resetInterruptedOperations(gameId);
        // Rejections are usually ordering artefacts, so retry them against fresh server
        // state; anything still failing after MAX_ATTEMPTS is dropped so the queue drains.
        dao.discardExhaustedOperations(gameId, MAX_ATTEMPTS);
        dao.reviveBlockedOperations(gameId, MAX_ATTEMPTS);
        while (!isStopped()) {
            PendingGameOperation operation = dao.getNextPending(gameId);
            if (operation == null) {
                // Blocked work is revived on the next pass, so ask WorkManager to come
                // back with backoff rather than reporting the queue as drained.
                return dao.blockedOperationCount(gameId) > 0
                        ? Result.retry()
                        : Result.success();
            }
            dao.updateOperationState(
                    operation.operationId,
                    GameOperationStatus.IN_FLIGHT.name(),
                    1,
                    null);
            Log.i(TAG, "Synchronizing game operation: " + operation.type);
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
                Log.i(TAG, "Synchronized game operation: " + operation.type);
            } catch (TimeoutException timeout) {
                dao.updateOperationState(
                        operation.operationId,
                        GameOperationStatus.PENDING.name(),
                        0,
                        "Cloud sync timed out. Waiting to retry.");
                Log.w(TAG, "Cloud game sync timed out");
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
                if (isAuthenticationFailure(cause)) {
                    try {
                        Tasks.await(editor.getIdToken(true), REMOTE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    } catch (Exception refreshError) {
                        Log.w(TAG, "Firebase reauthentication is required", refreshError);
                    }
                    dao.updateOperationState(operation.operationId,
                            GameOperationStatus.PENDING.name(), 0, message);
                    return Result.retry();
                }
                if (isTransient(cause)) {
                    dao.updateOperationState(
                            operation.operationId,
                            GameOperationStatus.PENDING.name(),
                            0,
                            message);
                    return Result.retry();
                }
                dao.blockOperation(operation.operationId, message);
                continue;
            } catch (RuntimeException failure) {
                dao.blockOperation(operation.operationId, message(failure));
                continue;
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

    private static boolean isAuthenticationFailure(Throwable error) {
        if (!(error instanceof FirebaseFirestoreException)) return false;
        FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) error).getCode();
        return code == FirebaseFirestoreException.Code.UNAUTHENTICATED
                || code == FirebaseFirestoreException.Code.PERMISSION_DENIED;
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
