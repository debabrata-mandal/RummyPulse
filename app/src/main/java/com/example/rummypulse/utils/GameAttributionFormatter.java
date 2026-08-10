package com.example.rummypulse.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import androidx.core.content.ContextCompat;

import com.example.rummypulse.R;
import com.example.rummypulse.ui.home.GameItem;

/**
 * Formats dashboard-style creator/editor attribution for game cards.
 */
public final class GameAttributionFormatter {

    private GameAttributionFormatter() {
    }

    public static CharSequence formatCreatorEditorLine(Context context, GameItem item) {
        int primaryColor = ContextCompat.getColor(context, R.color.text_primary);
        int secondaryColor = ContextCompat.getColor(context, R.color.text_secondary);

        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (isSameCreatorAndEditor(item)) {
            appendSecondary(builder, "Created & Edited by ", secondaryColor);
            appendBoldName(builder, displayOrUnknown(item.getCreatorName()), primaryColor);
        } else {
            appendSecondary(builder, "Created by ", secondaryColor);
            appendBoldName(builder, firstNameOrDisplay(item.getCreatorName()), primaryColor);
            appendSecondary(builder, " & Edited by ", secondaryColor);
            appendBoldName(builder, firstNameOrDisplay(item.getEditorName()), primaryColor);
        }
        return builder;
    }

    /** Plain-text attribution for review rows and other non-spannable UI. */
    public static String formatCreatorEditorPlainText(GameItem item) {
        if (isSameCreatorAndEditor(item)) {
            return "Created & Edited by " + displayOrUnknown(item.getCreatorName());
        }
        return "Created by " + firstNameOrDisplay(item.getCreatorName())
                + " & Edited by " + firstNameOrDisplay(item.getEditorName());
    }

    public static boolean isSameCreatorAndEditor(GameItem item) {
        if (item == null) {
            return true;
        }
        String creatorUserId = item.getCreatorUserId();
        String editorUserId = item.getEditorUserId();
        if (creatorUserId != null && !creatorUserId.isEmpty()
                && editorUserId != null && !editorUserId.isEmpty()) {
            return creatorUserId.equals(editorUserId);
        }
        String creatorName = normalizeName(item.getCreatorName());
        String editorName = normalizeName(item.getEditorName());
        if (creatorName.isEmpty() || editorName.isEmpty()) {
            return true;
        }
        return creatorName.equalsIgnoreCase(editorName);
    }

    private static void appendSecondary(SpannableStringBuilder builder, String text, int color) {
        int start = builder.length();
        builder.append(text);
        builder.setSpan(new ForegroundColorSpan(color), start, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendBoldName(SpannableStringBuilder builder, String name, int color) {
        int start = builder.length();
        builder.append(name);
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(color), start, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static String firstNameOrDisplay(String rawName) {
        String firstName = DisplayNameUtils.firstName(rawName);
        if (!firstName.isEmpty()) {
            return firstName;
        }
        return displayOrUnknown(rawName);
    }

    private static String displayOrUnknown(String name) {
        String normalized = normalizeName(name);
        return normalized.isEmpty() ? "Unknown" : normalized;
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }
}
