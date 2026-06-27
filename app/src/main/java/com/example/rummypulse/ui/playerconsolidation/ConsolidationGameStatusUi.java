package com.example.rummypulse.ui.playerconsolidation;

import android.content.Context;
import android.widget.TextView;

import com.example.rummypulse.R;
import com.example.rummypulse.ui.home.GameItem;

public final class ConsolidationGameStatusUi {

    private ConsolidationGameStatusUi() {
    }

    public static void bindStatus(TextView statusView, String status) {
        if (status == null || status.isEmpty()) {
            status = "In Progress";
        }
        statusView.setText(status);
        if ("Completed".equals(status) || status.startsWith("R")) {
            statusView.setBackgroundResource(R.drawable.status_background_green);
        } else {
            statusView.setBackgroundResource(R.drawable.status_background_orange);
        }
    }

    public static String formatGameSubtitle(Context context, GameItem item) {
        String players = item.getNumberOfPlayers() != null ? item.getNumberOfPlayers() : "0";
        String pointValue = item.getPointValue() != null && !item.getPointValue().isEmpty()
                ? item.getPointValue() : "0.00";
        return context.getString(R.string.player_consolidation_game_subtitle, players, pointValue);
    }
}
