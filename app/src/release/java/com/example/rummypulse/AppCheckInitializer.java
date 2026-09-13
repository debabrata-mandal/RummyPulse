package com.example.rummypulse;

import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/** Configures Firebase App Check for signed release APKs. */
final class AppCheckInitializer {

    private AppCheckInitializer() {
    }

    static void initialize() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance());
    }
}
