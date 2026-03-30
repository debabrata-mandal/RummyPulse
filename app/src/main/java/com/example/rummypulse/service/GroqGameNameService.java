package com.example.rummypulse.service;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.rummypulse.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Calls <a href="https://console.groq.com/">Groq</a> OpenAI-compatible {@code /chat/completions} API.
 * <p>
 * Not an {@link android.app.Service}. At <strong>build time</strong>, Gradle reads {@code GROQ_API_KEY} and
 * {@code GROQ_MODEL_ID} at Gradle build time into {@link com.example.rummypulse.BuildConfig} (see README:
 * env, Gradle properties, or gitignored {@code local.properties}).
 * Groq is often usable where other providers are region- or org-blocked (e.g. many India setups).
 */
public final class GroqGameNameService {

    private static final String TAG = "GroqGameNameService";

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private static final Executor IO = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final String PROMPT =
            "Generate one short, catchy English name for a rummy / card game app. "
                    + "One or two words, title case, no numbers, no punctuation except spaces. "
                    + "Reply with only that name, nothing else.";

    public interface Callback {
        void onSuccess(String name);

        void onError(String message);
    }

    /**
     * Delivers a trimmed name or {@code ""} after up to {@link #MAX_NAME_ATTEMPTS} tries (initial + 2 retries).
     * Runs on a background thread; callback is always invoked on the main thread.
     */
    public interface NameResultCallback {
        void onComplete(String displayName);
    }

    private static final int MAX_NAME_ATTEMPTS = 3;

    private static final long RETRY_DELAY_MS = 400L;

    private GroqGameNameService() {
    }

    public static boolean isConfigured() {
        return BuildConfig.GROQ_API_KEY != null && !BuildConfig.GROQ_API_KEY.isEmpty();
    }

    /**
     * Up to three attempts with short delay between failures. {@link NameResultCallback#onComplete} receives
     * a non-null string: trimmed model output, or {@code ""} if Groq is not configured, misconfigured, or all attempts fail.
     */
    public static void suggestNameWithRetries(NameResultCallback callback) {
        if (!isConfigured()) {
            Log.w(TAG, "Game name generation skipped: BuildConfig.GROQ_API_KEY is empty (add GROQ_API_KEY to local.properties or Gradle env; GitHub secrets only on CI).");
            MAIN.post(() -> callback.onComplete(""));
            return;
        }
        String modelId = BuildConfig.GROQ_MODEL_ID;
        if (modelId == null || modelId.isEmpty()) {
            Log.e(TAG, "Groq model id is not configured.");
            MAIN.post(() -> callback.onComplete(""));
            return;
        }

        IO.execute(() -> {
            String apiKey = BuildConfig.GROQ_API_KEY;
            for (int attempt = 1; attempt <= MAX_NAME_ATTEMPTS; attempt++) {
                if (attempt > 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        MAIN.post(() -> callback.onComplete(""));
                        return;
                    }
                }
                try {
                    String name = requestNameSync(modelId, apiKey);
                    if (name != null && !name.trim().isEmpty()) {
                        String trimmed = name.trim();
                        MAIN.post(() -> callback.onComplete(trimmed));
                        return;
                    }
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    Log.w(TAG, "Groq name attempt " + attempt + "/" + MAX_NAME_ATTEMPTS + " failed: " + msg);
                }
            }
            Log.w(TAG, "Groq name generation exhausted after " + MAX_NAME_ATTEMPTS + " attempts.");
            MAIN.post(() -> callback.onComplete(""));
        });
    }

    public static void suggestName(Callback callback) {
        if (!isConfigured()) {
            Log.w(TAG, "Game name generation skipped: BuildConfig.GROQ_API_KEY is empty (add GROQ_API_KEY to local.properties or Gradle env; GitHub secrets only on CI).");
            MAIN.post(() -> callback.onError(
                    "No Groq key in this APK. GitHub secrets only apply to CI-built APKs. "
                            + "Easiest: add GROQ_API_KEY=... to project local.properties (same folder as sdk.dir), "
                            + "then Sync/Rebuild. Or use ~/.gradle/gradle.properties or GROQ_API_KEY env var."));
            return;
        }
        String modelId = BuildConfig.GROQ_MODEL_ID;
        if (modelId == null || modelId.isEmpty()) {
            MAIN.post(() -> callback.onError("Groq model id is not configured."));
            return;
        }

        IO.execute(() -> {
            try {
                String name = requestNameSync(modelId, BuildConfig.GROQ_API_KEY);
                MAIN.post(() -> callback.onSuccess(name));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Log.e(TAG, "Groq request failed: " + msg);
                MAIN.post(() -> callback.onError(msg));
            }
        });
    }

    private static String requestNameSync(String modelId, String apiKey) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(API_URL).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", PROMPT);

        JSONObject body = new JSONObject();
        body.put("model", modelId);
        body.put("messages", new JSONArray().put(userMessage));
        body.put("temperature", 0.9);
        body.put("max_tokens", 64);

        byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bodyBytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
        }

        int code = conn.getResponseCode();
        String response = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new IOException(parseErrorMessage(response, code));
        }

        return parseNameFromChatResponse(response);
    }

    private static String parseErrorMessage(String json, int code) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("error")) {
                Object errObj = root.get("error");
                if (errObj instanceof JSONObject) {
                    JSONObject err = (JSONObject) errObj;
                    return "HTTP " + code + ": " + err.optString("message", json);
                }
                return "HTTP " + code + ": " + errObj.toString();
            }
        } catch (Exception ignored) {
        }
        return "HTTP " + code + ": " + json;
    }

    private static String parseNameFromChatResponse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IOException("No choices in response.");
        }
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        if (message == null) {
            throw new IOException("No message in response.");
        }
        String text = message.optString("content", "").trim();
        if (text.isEmpty()) {
            throw new IOException("Empty model text.");
        }
        int newline = text.indexOf('\n');
        if (newline >= 0) {
            text = text.substring(0, newline).trim();
        }
        return text;
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
