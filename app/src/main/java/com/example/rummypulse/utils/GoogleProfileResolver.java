package com.example.rummypulse.utils;

import android.accounts.Account;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.rummypulse.data.AppUserRepository;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.Scope;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Resolves fresh Google profile fields after an explicit sign-in. */
public final class GoogleProfileResolver {

    private static final String TAG = "GoogleProfileResolver";
    private static final String USER_INFO_URL =
            "https://openidconnect.googleapis.com/v1/userinfo";
    private static final int NETWORK_TIMEOUT_MS = 5_000;
    private static final ExecutorService NETWORK_EXECUTOR = Executors.newSingleThreadExecutor();

    private GoogleProfileResolver() {
    }

    public static void resolve(
            @NonNull AppCompatActivity activity,
            @Nullable GoogleSignInAccount googleAccount,
            @NonNull Callback callback) {
        AppUserRepository.ProfileOverrides fallback = fromGoogleAccount(googleAccount);
        if (googleAccount == null) {
            callback.onResolved(fallback);
            return;
        }
        Account account = googleAccount.getAccount();
        if (account == null) {
            callback.onResolved(fallback);
            return;
        }

        AuthorizationRequest request = AuthorizationRequest.builder()
                .setAccount(account)
                .setRequestedScopes(Collections.singletonList(new Scope(Scopes.PROFILE)))
                .build();
        Identity.getAuthorizationClient(activity)
                .authorize(request)
                .addOnSuccessListener(activity, result -> {
                    if (result == null || result.hasResolution()
                            || isNullOrEmpty(result.getAccessToken())) {
                        callback.onResolved(fallback);
                        return;
                    }
                    fetchUserInfo(
                            activity,
                            result,
                            googleAccount.getId(),
                            fallback,
                            callback);
                })
                .addOnFailureListener(activity, exception -> {
                    Log.w(TAG, "Google profile authorization unavailable", exception);
                    callback.onResolved(fallback);
                });
    }

    private static void fetchUserInfo(
            AppCompatActivity activity,
            AuthorizationResult authorizationResult,
            @Nullable String expectedSubject,
            AppUserRepository.ProfileOverrides fallback,
            Callback callback) {
        NETWORK_EXECUTOR.execute(() -> {
            AppUserRepository.ProfileOverrides resolved = fallback;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(USER_INFO_URL).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
                connection.setReadTimeout(NETWORK_TIMEOUT_MS);
                connection.setRequestProperty(
                        "Authorization", "Bearer " + authorizationResult.getAccessToken());
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            connection.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        resolved = mergeUserInfo(fallback, expectedSubject, response.toString());
                    }
                } else {
                    Log.w(TAG, "Google UserInfo request failed with HTTP "
                            + connection.getResponseCode());
                }
            } catch (Exception exception) {
                Log.w(TAG, "Could not refresh Google profile", exception);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            AppUserRepository.ProfileOverrides finalResolved = resolved;
            activity.runOnUiThread(() -> callback.onResolved(finalResolved));
        });
    }

    static AppUserRepository.ProfileOverrides mergeUserInfo(
            AppUserRepository.ProfileOverrides fallback,
            @Nullable String expectedSubject,
            String userInfoJson) {
        try {
            JsonObject userInfo = JsonParser.parseString(userInfoJson).getAsJsonObject();
            String subject = stringValue(userInfo, "sub");
            if (!isNullOrEmpty(expectedSubject) && !expectedSubject.equals(subject)) {
                return fallback;
            }
            String displayName = stringValue(userInfo, "name");
            String photoUrl = stringValue(userInfo, "picture");
            return new AppUserRepository.ProfileOverrides(
                    isNullOrEmpty(displayName) ? fallback.displayName : displayName,
                    isNullOrEmpty(photoUrl) ? fallback.photoUrl : photoUrl);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static AppUserRepository.ProfileOverrides fromGoogleAccount(
            @Nullable GoogleSignInAccount account) {
        if (account == null) {
            return new AppUserRepository.ProfileOverrides(null, null);
        }
        String photoUrl = account.getPhotoUrl() != null
                ? account.getPhotoUrl().toString()
                : null;
        return new AppUserRepository.ProfileOverrides(account.getDisplayName(), photoUrl);
    }

    @Nullable
    private static String stringValue(JsonObject object, String fieldName) {
        JsonElement value = object.get(fieldName);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        String text = value.getAsString();
        return isNullOrEmpty(text) ? null : text.trim();
    }

    private static boolean isNullOrEmpty(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface Callback {
        void onResolved(AppUserRepository.ProfileOverrides overrides);
    }
}
