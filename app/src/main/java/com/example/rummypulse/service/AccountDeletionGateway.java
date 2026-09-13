package com.example.rummypulse.service;

import androidx.annotation.NonNull;

/** Secure backend operations for permanent RummyPulse account deletion. */
public interface AccountDeletionGateway {

    void deleteMyAccount(@NonNull Callback callback);

    void deleteUserAsAdmin(@NonNull String userId, @NonNull Callback callback);

    interface Callback {
        void onSuccess();

        void onFailure(@NonNull Exception exception);
    }
}
