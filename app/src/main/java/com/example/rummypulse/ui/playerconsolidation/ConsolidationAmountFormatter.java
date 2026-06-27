package com.example.rummypulse.ui.playerconsolidation;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.example.rummypulse.R;

public final class ConsolidationAmountFormatter {

    private ConsolidationAmountFormatter() {
    }

    public static String formatSignedAmount(double amount) {
        long rounded = Math.round(amount);
        if (rounded > 0) {
            return "+₹" + rounded;
        }
        if (rounded < 0) {
            return "₹" + rounded;
        }
        return "₹0";
    }

    public static String formatContribution(double amount) {
        return "₹" + Math.round(amount);
    }

    public static int getSignedAmountColor(Context context, double amount) {
        if (amount > 0) {
            return ContextCompat.getColor(context, R.color.success_green);
        }
        if (amount < 0) {
            return ContextCompat.getColor(context, R.color.error_red);
        }
        return ContextCompat.getColor(context, R.color.text_secondary);
    }

    public static int getContributionColor(Context context, double amount) {
        if (amount > 0) {
            return ContextCompat.getColor(context, R.color.warning_orange);
        }
        return ContextCompat.getColor(context, R.color.text_secondary);
    }
}
