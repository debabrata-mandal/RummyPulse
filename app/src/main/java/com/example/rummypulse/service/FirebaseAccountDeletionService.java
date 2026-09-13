package com.example.rummypulse.service;

import androidx.annotation.NonNull;

import com.google.firebase.functions.FirebaseFunctions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Calls the App Check-protected deletion functions hosted in asia-south1. */
public final class FirebaseAccountDeletionService implements AccountDeletionGateway {

    private static final String FUNCTIONS_REGION = "asia-south1";
    private static final FirebaseFunctions FUNCTIONS =
            FirebaseFunctions.getInstance(FUNCTIONS_REGION);

    @Override
    public void deleteMyAccount(@NonNull Callback callback) {
        FUNCTIONS.getHttpsCallable("deleteMyAccount")
                .call(Collections.emptyMap())
                .addOnSuccessListener(result -> callback.onSuccess())
                .addOnFailureListener(callback::onFailure);
    }

    @Override
    public void deleteUserAsAdmin(@NonNull String userId, @NonNull Callback callback) {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", userId);
        FUNCTIONS.getHttpsCallable("adminDeleteAccount")
                .call(request)
                .addOnSuccessListener(result -> callback.onSuccess())
                .addOnFailureListener(callback::onFailure);
    }
}
