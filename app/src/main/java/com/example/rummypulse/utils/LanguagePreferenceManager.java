package com.example.rummypulse.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Utility class to manage TTS language preferences across the app
 */
public class LanguagePreferenceManager {
    
    private static final String PREFS_NAME = "RummyPulse_TTS";
    private static final String KEY_TTS_LANGUAGE = "tts_language";
    private static final String KEY_TTS_COUNTRY = "tts_country";
    private static final String KEY_TTS_MUTED = "tts_muted";
    
    /**
     * Save language preference
     */
    public static void saveLanguagePreference(Context context, Locale locale) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_TTS_LANGUAGE, locale.getLanguage())
            .putString(KEY_TTS_COUNTRY, locale.getCountry())
            .putBoolean(KEY_TTS_MUTED, false) // Unmute when language is selected
            .apply();
    }
    
    /**
     * Load language preference (defaults to Bengali)
     */
    public static Locale loadLanguagePreference(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String language = prefs.getString(KEY_TTS_LANGUAGE, "bn"); // Default to Bengali
        String country = prefs.getString(KEY_TTS_COUNTRY, "IN");
        return new Locale(language, country);
    }
    
    /**
     * Set mute state
     */
    public static void setMuted(Context context, boolean muted) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean(KEY_TTS_MUTED, muted)
            .apply();
    }
    
    /**
     * Check if TTS is muted
     */
    public static boolean isMuted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_TTS_MUTED, false); // Default to unmuted
    }
}
