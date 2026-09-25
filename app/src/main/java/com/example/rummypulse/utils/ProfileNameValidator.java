package com.example.rummypulse.utils;

import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/** Shared client-side validation for unique public game-profile names. */
public final class ProfileNameValidator {

    private static final Pattern PATTERN = Pattern.compile("[\\p{L}\\p{N}_ ]+\\.?(?: [0-9]+)?");

    private ProfileNameValidator() {
    }

    public static boolean isValid(@Nullable String value) {
        if (value == null) return false;
        String normalized = value.trim().replaceAll("\\s+", " ");
        int length = normalized.codePointCount(0, normalized.length());
        return length >= 3 && length <= 24 && PATTERN.matcher(normalized).matches();
    }
}
