package com.example.rummypulse.service;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.io.IOException;
import java.util.Map;

/**
 * Requests an AI-generated game name from an authenticated Firebase callable function.
 * The Groq credential and prompt live only in the backend and are never packaged in the APK.
 */
public final class GroqGameNameService {

    private static final String TAG = "GroqGameNameService";
    private static final String FUNCTIONS_REGION = "asia-south1";
    private static final String FUNCTION_NAME = "suggestGameName";
    private static final int MAX_NAME_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 400L;

    private static final FirebaseFunctions FUNCTIONS =
            FirebaseFunctions.getInstance(FUNCTIONS_REGION);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(String name);

        void onError(String errorCode);
    }

    /** Receives a trimmed name, or an empty string when generation is unavailable. */
    public interface NameResultCallback {
        void onComplete(String displayName);
    }

    private GroqGameNameService() {
    }

    public static boolean isConfigured() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    public static void suggestNameWithRetries(NameResultCallback callback) {
        if (!isConfigured()) {
            Log.w(TAG, "Game name generation skipped because no user is signed in");
            MAIN.post(() -> callback.onComplete(""));
            return;
        }
        requestWithRetries(1, callback);
    }

    private static void requestWithRetries(int attempt, NameResultCallback callback) {
        requestName(
                callback::onComplete,
                errorCode -> {
                    Log.w(TAG, "Game name request attempt " + attempt + "/"
                            + MAX_NAME_ATTEMPTS + " failed: " + errorCode);
                    if (attempt >= MAX_NAME_ATTEMPTS || !isRetryable(errorCode)) {
                        callback.onComplete("");
                        return;
                    }
                    MAIN.postDelayed(
                            () -> requestWithRetries(attempt + 1, callback),
                            RETRY_DELAY_MS);
                });
    }

    public static void suggestName(Callback callback) {
        if (!isConfigured()) {
            MAIN.post(() -> callback.onError("unauthenticated"));
            return;
        }
        requestName(callback::onSuccess, callback::onError);
    }

    private static void requestName(SuccessCallback success, ErrorCallback error) {
        FUNCTIONS.getHttpsCallable(FUNCTION_NAME)
                .call()
                .addOnSuccessListener(result -> {
                    try {
                        success.onSuccess(extractName(result.getData()));
                    } catch (Exception exception) {
                        Log.w(TAG, "Game name function returned an invalid response");
                        error.onError("invalid-response");
                    }
                })
                .addOnFailureListener(exception -> error.onError(errorCode(exception)));
    }

    static String extractName(Object data) throws IOException {
        if (!(data instanceof Map)) {
            throw new IOException("Response was not an object");
        }
        Object value = ((Map<?, ?>) data).get("name");
        if (!(value instanceof String)) {
            throw new IOException("Response did not contain a name");
        }
        String name = ((String) value).trim();
        if (name.length() < 3 || name.length() > 32
                || !name.matches("[A-Za-z]+(?: [A-Za-z]+)?")) {
            throw new IOException("Response name was invalid");
        }
        return name;
    }

    private static String errorCode(Exception exception) {
        if (exception instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException functionsException =
                    (FirebaseFunctionsException) exception;
            return functionsException.getCode().name().toLowerCase();
        }
        return "unavailable";
    }

    private static boolean isRetryable(String errorCode) {
        return "aborted".equals(errorCode)
                || "deadline_exceeded".equals(errorCode)
                || "internal".equals(errorCode)
                || "unavailable".equals(errorCode)
                || "unknown".equals(errorCode);
    }

    private interface SuccessCallback {
        void onSuccess(String name);
    }

    private interface ErrorCallback {
        void onError(String errorCode);
    }
}
