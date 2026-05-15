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
     * @param gstPercentOrNull ignored when {@code canEditContribution} is false (repository omits GST field).
     */
    public void save(double pointValue, @Nullable Double gstPercentOrNull, long midGameIncrement,
            boolean displayIntermediateCalculation, boolean canEditContribution) {
        loading.setValue(true);
        error.setValue(null);
        saveSuccess.setValue(false);
        Double gstWrite = canEditContribution ? gstPercentOrNull : null;
        repository.saveDefaults(pointValue, midGameIncrement, displayIntermediateCalculation, gstWrite)
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

    public void saveDisplayIntermediateCalculation(boolean enabled) {
        repository.setDisplayIntermediateCalculationCached(enabled);
        repository.saveDisplayIntermediateCalculation(enabled)
                .addOnFailureListener(e -> error.postValue(
                        e.getMessage() != null ? e.getMessage() : "Failed to save display setting"));
    }
}
