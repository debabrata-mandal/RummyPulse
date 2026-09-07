package com.example.rummypulse.utils;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Helpers for shortening Firebase display names for in-game player labels.
 */
public final class DisplayNameUtils {

    private DisplayNameUtils() {
    }

    /** First whitespace-separated token; email local-part before @ if no space. */
    public static String firstName(String displayName) {
        if (displayName == null) {
            return "";
        }
        String t = displayName.trim();
        if (t.isEmpty()) {
            return "";
        }
        int at = t.indexOf('@');
        if (at > 0 && !t.contains(" ")) {
            t = t.substring(0, at);
            int dot = t.indexOf('.');
            if (dot > 0) {
                t = t.substring(0, dot);
            }
        }
        int sp = t.indexOf(' ');
        if (sp < 0) {
            return t;
        }
        return t.substring(0, sp);
    }

    /**
     * Short in-game label: first token plus last-token initial when a full name is available.
     * Example: {@code "Debabrata Mandal"} → {@code "Debabrata M"}.
     */
    public static String firstNameLastInitial(String displayName) {
        if (displayName == null) {
            return "";
        }
        String normalized = normalizeForNameTokens(displayName);
        if (normalized.isEmpty()) {
            return "";
        }
        String[] parts = normalized.split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        String lastInitial = parts[parts.length - 1].substring(0, 1).toUpperCase(Locale.ROOT);
        return parts[0] + " " + lastInitial;
    }

    /**
     * Player label for UI: prefer the mapped account's full profile name when available,
     * otherwise format the stored in-game name.
     */
    public static String playerLabel(
            @Nullable String storedPlayerName,
            @Nullable String userId,
            @Nullable Map<String, String> displayNameByUserId) {
        if (userId != null && !userId.isEmpty()
                && displayNameByUserId != null && !displayNameByUserId.isEmpty()) {
            String accountName = displayNameByUserId.get(userId);
            if (accountName != null && !accountName.trim().isEmpty()) {
                return firstNameLastInitial(accountName.trim());
            }
        }
        if (storedPlayerName == null || storedPlayerName.trim().isEmpty()) {
            return "";
        }
        return firstNameLastInitial(storedPlayerName.trim());
    }

    /**
     * Avatar initials: first and last token initials, or just the first for a single token.
     *
     * @return one or two uppercase letters, or {@code "?"} when there is no usable name
     */
    public static String initials(String name) {
        if (name == null) {
            return "?";
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "?";
        }
        String[] parts = trimmed.split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1
                ? parts[parts.length - 1].substring(0, 1)
                : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    private static String normalizeForNameTokens(String displayName) {
        String t = displayName.trim();
        if (t.isEmpty()) {
            return "";
        }
        int at = t.indexOf('@');
        if (at > 0 && !t.contains(" ")) {
            t = t.substring(0, at);
            int dot = t.indexOf('.');
            if (dot > 0) {
                t = t.substring(0, dot);
            }
        }
        return t;
    }
}
