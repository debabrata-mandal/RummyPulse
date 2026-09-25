package com.example.rummypulse.service;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.firebase.auth.FirebaseAuth;
import com.example.rummypulse.data.AppUserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Saves unique public game-profile names through the trusted backend. */
public final class ProfileNameService {
    private static final long SAVE_TIMEOUT_MS = 20_000L;

    public void save(@Nullable String profileName, Callback callback) {
        Map<String, Object> request = new HashMap<>();
        request.put("profileName", profileName == null ? "" : profileName.trim());
        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean completed = new AtomicBoolean(false);
        Runnable timeout = () -> {
            if (completed.compareAndSet(false, true)) {
                callback.onFailure("The save timed out. Please try again.");
            }
        };
        handler.postDelayed(timeout, SAVE_TIMEOUT_MS);
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("setProfileName").call(request)
                .addOnSuccessListener(result -> {
                    if (!completed.compareAndSet(false, true)) return;
                    handler.removeCallbacks(timeout);
                    Object data = result.getData();
                    if (!(data instanceof Map)) {
                        callback.onFailure("The profile service returned an invalid response.");
                        return;
                    }
                    Map<?, ?> response = (Map<?, ?>) data;
                    String savedProfileName = asString(response.get("profileName"));
                    String displayName = asString(response.get("displayName"));
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        AppUserRepository.applyPublicProfileUpdate(
                                FirebaseAuth.getInstance().getCurrentUser().getUid(),
                                savedProfileName, displayName);
                    }
                    callback.onSuccess(savedProfileName, displayName);
                })
                .addOnFailureListener(error -> {
                    if (!completed.compareAndSet(false, true)) return;
                    handler.removeCallbacks(timeout);
                    callback.onFailure(messageFor(error));
                });
    }

    @Nullable
    private static String asString(Object value) {
        return value instanceof String && !((String) value).trim().isEmpty()
                ? ((String) value).trim() : null;
    }

    private static String messageFor(Exception exception) {
        if (exception instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException functionsException = (FirebaseFunctionsException) exception;
            if (functionsException.getMessage() != null) {
                return functionsException.getMessage();
            }
        }
        return "Could not save the profile name. Please try again.";
    }

    public interface Callback {
        void onSuccess(@Nullable String profileName, String displayName);
        void onFailure(String message);
    }
}
