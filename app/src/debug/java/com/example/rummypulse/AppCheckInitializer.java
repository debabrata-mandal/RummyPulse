package com.example.rummypulse;

import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

/** Configures Firebase App Check for local and emulator builds. */
final class AppCheckInitializer {

    private AppCheckInitializer() {
    }

    static void initialize() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance());
    }
}
