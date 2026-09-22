package com.example.rummypulse.utils;

import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/** Shared client-side validation for unique public game-profile names. */
public final class ProfileNameValidator {

    private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private ProfileNameValidator() {
    }

    public static boolean isValid(@Nullable String value) {
        return value != null && PATTERN.matcher(value.trim()).matches();
    }
}
