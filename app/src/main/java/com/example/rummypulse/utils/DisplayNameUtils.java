package com.example.rummypulse.utils;

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
}
