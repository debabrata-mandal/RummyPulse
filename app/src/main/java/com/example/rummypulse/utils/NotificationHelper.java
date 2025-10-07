package com.example.rummypulse.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.rummypulse.JoinGameActivity;
import com.example.rummypulse.R;

public class NotificationHelper {
    
    private static final String CHANNEL_ID = "game_notifications";
    private static final String CHANNEL_NAME = "Game Notifications";
    private static final String CHANNEL_DESCRIPTION = "Notifications for game creation and updates";
    private static final int NOTIFICATION_ID_BASE = 1000;
    private static final String GROUP_KEY = "com.example.rummypulse.GAME_NOTIFICATIONS";
    private static int notificationCounter = 0;
    
    /**
     * Create notification channel (required for Android 8.0+)
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableVibration(true);
            channel.enableLights(true);
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Show notification when a new game is created
     */
    public static void showGameCreatedNotification(Context context, String gameId, String creatorName, double pointValue) {
        android.util.Log.d("NotificationHelper", "🔔 Showing notification for game: " + gameId + 
            " created by: " + creatorName + " value: ₹" + pointValue);
        // Create intent to open the game when notification is tapped
        Intent intent = new Intent(context, JoinGameActivity.class);
        intent.putExtra("GAME_ID", gameId);
        intent.putExtra("IS_CREATOR", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            gameId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Format point value (show decimals if needed, otherwise show as integer)
        String pointValueStr;
        if (pointValue == (long) pointValue) {
            pointValueStr = String.format("₹%.0f", pointValue);
        } else {
            pointValueStr = String.format("₹%.2f", pointValue);
        }
        
        // Build the notification with grouping support
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("🎮 New Game Created!")
            .setContentText("Game ID: " + gameId + " • " + pointValueStr + "/point")
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText("Game ID: " + gameId + "\nCreated by: " + creatorName + "\nPoint Value: " + pointValueStr + "\n\nTap to open the game and join!"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(new long[]{0, 500, 200, 500})
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(GROUP_KEY)
            .setGroupSummary(false);
        
        // Create a unique notification ID for each game
        int notificationId = NOTIFICATION_ID_BASE + (notificationCounter++);
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(notificationId, builder.build());
            android.util.Log.d("NotificationHelper", "✅ Notification posted successfully with ID: " + notificationId);
            
            // Show summary notification if multiple notifications exist
            showSummaryNotification(context, notificationManager);
        } catch (SecurityException e) {
            android.util.Log.e("NotificationHelper", "❌ Permission denied for notification", e);
        }
    }
    
    /**
     * Show a summary notification when multiple game notifications exist
     */
    private static void showSummaryNotification(Context context, NotificationManagerCompat notificationManager) {
        if (notificationCounter > 1) {
            NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle("🎮 New Games")
                .setContentText(notificationCounter + " new games created")
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
            
            try {
                notificationManager.notify(0, summaryBuilder.build());
            } catch (SecurityException e) {
                android.util.Log.e("NotificationHelper", "❌ Permission denied for summary notification", e);
            }
        }
    }
    
    /**
     * Show notification for game updates
     */
    public static void showGameUpdateNotification(Context context, String gameId, String message) {
        Intent intent = new Intent(context, JoinGameActivity.class);
        intent.putExtra("GAME_ID", gameId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            gameId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Game Update")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(NOTIFICATION_ID_BASE + gameId.hashCode() + 1, builder.build());
        } catch (SecurityException e) {
            android.util.Log.e("NotificationHelper", "Permission denied for notification", e);
        }
    }
    
    /**
     * Cancel all notifications
     */
    public static void cancelAllNotifications(Context context) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancelAll();
    }
}

