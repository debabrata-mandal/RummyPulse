package com.example.rummypulse.service;

import androidx.annotation.Nullable;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.firebase.auth.FirebaseAuth;
import com.example.rummypulse.data.AppUserRepository;

import java.util.HashMap;
import java.util.Map;

/** Saves unique public game-profile names through the trusted backend. */
public final class ProfileNameService {

    public void save(@Nullable String profileName, Callback callback) {
        Map<String, Object> request = new HashMap<>();
        request.put("profileName", profileName == null ? "" : profileName.trim());
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("setProfileName").call(request)
                .addOnSuccessListener(result -> {
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
                .addOnFailureListener(error -> callback.onFailure(messageFor(error)));
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
