package com.example.rummypulse.ui.gamedefaults;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.rummypulse.R;
import com.example.rummypulse.data.GameDefaults;
import com.example.rummypulse.data.GameDefaultsRepository;
import com.google.firebase.firestore.FirebaseFirestoreException;

public class GameDefaultsViewModel extends AndroidViewModel {

    private final GameDefaultsRepository repository;
    private final MutableLiveData<GameDefaults> defaults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveSuccess = new MutableLiveData<>();

    public GameDefaultsViewModel(@NonNull Application application) {
        super(application);
        repository = GameDefaultsRepository.getInstance(application);
    }

    public LiveData<GameDefaults> getDefaults() {
        return defaults;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getSaveSuccess() {
        return saveSuccess;
    }

    public void load() {
        loading.setValue(true);
        error.setValue(null);
        repository.refreshFromServer(() -> {
            loading.postValue(false);
            defaults.postValue(repository.getCachedResolved());
        }, ex -> {
            if (ex instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) ex).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                error.postValue(getApplication().getString(R.string.game_defaults_firestore_permission));
            } else if (ex != null && ex.getMessage() != null && !ex.getMessage().isEmpty()) {
                error.postValue(ex.getMessage());
            }
        });
    }

    /**
     * @param boardAdjustmentPercentOrNull ignored when {@code isAdmin} is false.
     * @param displayIntermediateOrNull ignored when {@code isAdmin} is false (repository omits display field).
     * @param showDashboardApprovalCountsOrNull saved for all users when non-null.
     */
    public void save(double gamePointFactor, @Nullable Double boardAdjustmentPercentOrNull, long midGameIncrement,
            @Nullable Boolean displayIntermediateOrNull,
            @Nullable Boolean showDashboardApprovalCountsOrNull,
            boolean isAdmin) {
        loading.setValue(true);
        error.setValue(null);
        saveSuccess.setValue(false);
        Double boardAdjustmentWrite = isAdmin ? boardAdjustmentPercentOrNull : null;
        Boolean displayWrite = isAdmin ? displayIntermediateOrNull : null;
        Boolean countsWrite = showDashboardApprovalCountsOrNull;
        repository.saveDefaults(gamePointFactor, midGameIncrement, displayWrite, countsWrite, boardAdjustmentWrite)
                .addOnSuccessListener(aVoid -> {
                    loading.postValue(false);
                    saveSuccess.postValue(true);
                    defaults.postValue(repository.getCachedResolved());
                })
                .addOnFailureListener(e -> {
                    loading.postValue(false);
                    if (e instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        error.postValue(getApplication().getString(R.string.game_defaults_firestore_permission));
                    } else {
                        error.postValue(e.getMessage() != null ? e.getMessage() : "Save failed");
                    }
                });
    }

    public void clearSaveSuccessFlag() {
        saveSuccess.setValue(false);
    }

    public void saveShowLiveGamePoints(boolean enabled, boolean isAdmin) {
        if (!isAdmin) {
            return;
        }
        repository.saveShowLiveGamePoints(enabled)
                .addOnFailureListener(e -> error.postValue(
                        e.getMessage() != null ? e.getMessage() : "Failed to save display setting"));
    }

    /** Admin-only: the flag is global, so it changes the dashboard for every player. */
    public void saveShowDashboardLeaderboard(boolean enabled, boolean isAdmin) {
        if (!isAdmin) {
            return;
        }
        repository.saveShowDashboardLeaderboard(enabled)
                .addOnFailureListener(e -> error.postValue(e.getMessage() != null
                        ? e.getMessage()
                        : "Failed to save leaderboard setting"));
    }

    /** Admin-only: the flag is global, so it changes the dashboard for every player. */
    public void saveShowDashboardLeaderboardGamePoints(boolean enabled, boolean isAdmin) {
        if (!isAdmin) {
            return;
        }
        repository.saveShowDashboardLeaderboardGamePoints(enabled)
                .addOnFailureListener(e -> error.postValue(e.getMessage() != null
                        ? e.getMessage()
                        : "Failed to save leaderboard amount setting"));
    }

    public void saveShowDashboardApprovalCounts(boolean enabled) {
        repository.saveShowDashboardApprovalCounts(enabled)
                .addOnFailureListener(e -> error.postValue(
                        e.getMessage() != null ? e.getMessage() : "Failed to save dashboard count setting"));
    }
}
