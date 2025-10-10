package com.example.rummypulse.ui.dashboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.List;

public class DashboardGameAdapter extends RecyclerView.Adapter<DashboardGameAdapter.GameViewHolder> {

    private List<GameItem> gameItems = new ArrayList<>();
    private OnGameJoinListener joinListener;
    private boolean isCompletedGamesAdapter = false;
    private android.os.Handler updateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable updateRunnable;

    public interface OnGameJoinListener {
        void onJoinGame(GameItem game, int position, String joinType);
    }

    public void setOnGameJoinListener(OnGameJoinListener listener) {
        this.joinListener = listener;
    }

    public void setGameItems(List<GameItem> gameItems) {
        this.gameItems = gameItems != null ? gameItems : new ArrayList<>();
        notifyDataSetChanged();
        startTimeUpdates();
    }
    
    private void startTimeUpdates() {
        // Cancel any existing updates
        if (updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
        
        // Create new update runnable
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                // Update only the time text for all visible items
                notifyItemRangeChanged(0, gameItems.size(), "time_update");
                // Schedule next update in 60 seconds
                updateHandler.postDelayed(this, 60000);
            }
        };
        
        // Start updates after 60 seconds
        updateHandler.postDelayed(updateRunnable, 60000);
    }
    
    public void stopTimeUpdates() {
        if (updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }

    public void setIsCompletedGamesAdapter(boolean isCompletedGamesAdapter) {
        this.isCompletedGamesAdapter = isCompletedGamesAdapter;
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dashboard_game, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        onBindViewHolder(holder, position, new ArrayList<>());
    }
    
    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position, @NonNull List<Object> payloads) {
        GameItem item = gameItems.get(position);
        
        // If this is a partial update for time only
        if (!payloads.isEmpty() && payloads.contains("time_update")) {
            holder.createdTimeText.setText("Started " + formatDateTime(item.getCreationDateTime()));
            return;
        }
        
        // Full bind
        // Set game ID
        holder.gameIdText.setText("Game #" + item.getGameId());
        
        // Set game status
        String status = item.getGameStatus();
        if (status == null || status.isEmpty()) {
            status = "In Progress";
        }
        holder.gameStatusText.setText(status);
        
        // Set status background based on status
        if ("Completed".equals(status)) {
            holder.gameStatusText.setBackgroundResource(R.drawable.status_background_green);
        } else if (status.startsWith("R")) {
            holder.gameStatusText.setBackgroundResource(R.drawable.status_background_green);
        } else {
            holder.gameStatusText.setBackgroundResource(R.drawable.status_background_orange);
        }
        
        // Set players count
        holder.playersText.setText(String.valueOf(item.getNumberOfPlayers()));
        
        // Set point value with color coding
        String pointValue = item.getPointValue();
        if (pointValue == null || pointValue.isEmpty()) {
            holder.pointValueText.setText("₹0.00");
            holder.pointValueText.setTextColor(holder.itemView.getContext().getColor(R.color.success_green));
        } else {
            holder.pointValueText.setText("₹" + pointValue);
            
            // Color code based on point value
            try {
                double value = Double.parseDouble(pointValue);
                int color;
                
                if (value <= 0.25) {
                    // Green for 0 to 0.25
                    color = holder.itemView.getContext().getColor(R.color.success_green);
                } else if (value <= 0.50) {
                    // Yellow for 0.25 to 0.50
                    color = holder.itemView.getContext().getColor(R.color.warning_orange);
                } else {
                    // Red for above 0.50
                    color = holder.itemView.getContext().getColor(R.color.error_red);
                }
                
                holder.pointValueText.setTextColor(color);
                holder.pointValueText.setShadowLayer(12, 0, 0, color);
            } catch (NumberFormatException e) {
                // Default to blue if parsing fails
                int defaultColor = holder.itemView.getContext().getColor(R.color.accent_blue);
                holder.pointValueText.setTextColor(defaultColor);
                holder.pointValueText.setShadowLayer(12, 0, 0, defaultColor);
            }
        }
        
        // Set GST percentage
        holder.gstText.setText(item.getGstPercentage());
        
        // Set created time
        holder.createdTimeText.setText("Started " + formatDateTime(item.getCreationDateTime()));
        
        // Set creator section - always visible
        holder.creatorSection.setVisibility(View.VISIBLE);
        
        // Set creator name or default to Unknown
        if (item.getCreatorName() != null && !item.getCreatorName().trim().isEmpty()) {
            holder.creatorNameText.setText(item.getCreatorName());
            
            // Load creator profile image with Glide
            if (item.getCreatorPhotoUrl() != null && !item.getCreatorPhotoUrl().isEmpty()) {
                Glide.with(holder.itemView.getContext())
                    .load(item.getCreatorPhotoUrl())
                    .apply(new RequestOptions()
                        .centerCrop()
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .timeout(10000))
                    .into(holder.creatorProfileImage);
            } else {
                // No photo URL, show default icon with proper styling
                holder.creatorProfileImage.setImageResource(R.drawable.ic_person);
                holder.creatorProfileImage.setScaleType(android.widget.ImageView.ScaleType.CENTER);
                holder.creatorProfileImage.setPadding(8, 8, 8, 8);
            }
        } else {
            // No creator info - show unknown user
            holder.creatorNameText.setText("Unknown");
            holder.creatorProfileImage.setImageResource(R.drawable.ic_person);
            holder.creatorProfileImage.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            holder.creatorProfileImage.setPadding(8, 8, 8, 8);
        }
        
        // Set card click behavior based on game status
        final String finalStatus = status;
        holder.itemView.setOnClickListener(v -> {
            if (joinListener != null) {
                String joinType = (isCompletedGamesAdapter || "Completed".equals(finalStatus)) ? "view" : "player";
                joinListener.onJoinGame(item, position, joinType);
            }
        });
        
        // Set QR code icon click listener
        holder.qrCodeIcon.setOnClickListener(v -> {
            showQrCodeDialog(v.getContext(), item);
        });
    }

    @Override
    public int getItemCount() {
        return gameItems.size();
    }

    private String formatDateTime(String dateTime) {
        if (dateTime == null || dateTime.isEmpty()) {
            return "recently";
        }
        
        try {
            // Parse the date time and calculate relative time
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            java.util.Date creationDate = sdf.parse(dateTime);
            long currentTime = System.currentTimeMillis();
            long creationTime = creationDate.getTime();
            long diffInMillis = currentTime - creationTime;
            
            // Convert to different time units
            long diffInMinutes = diffInMillis / (1000 * 60);
            long diffInHours = diffInMillis / (1000 * 60 * 60);
            long diffInDays = diffInMillis / (1000 * 60 * 60 * 24);
            
            // Format the actual time
            java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("hh:mm a");
            String actualTime = timeFormat.format(creationDate);
            
            if (diffInMinutes < 1) {
                return "just now at " + actualTime;
            } else if (diffInMinutes < 60) {
                return diffInMinutes + " minutes ago at " + actualTime;
            } else if (diffInHours < 24) {
                return diffInHours + " hours ago at " + actualTime;
            } else if (diffInDays < 7) {
                java.text.SimpleDateFormat dateTimeFormat = new java.text.SimpleDateFormat("MMM dd 'at' hh:mm a");
                return dateTimeFormat.format(creationDate);
            } else {
                // For older dates, show the actual date and time
                java.text.SimpleDateFormat displayFormat = new java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a");
                return displayFormat.format(creationDate);
            }
        } catch (Exception e) {
            // If parsing fails, try to handle Firebase Timestamp format
            try {
                // Firebase timestamps are usually in milliseconds
                long timestamp = Long.parseLong(dateTime);
                long currentTime = System.currentTimeMillis();
                long diffInMillis = currentTime - timestamp;
                
                long diffInMinutes = diffInMillis / (1000 * 60);
                long diffInHours = diffInMillis / (1000 * 60 * 60);
                long diffInDays = diffInMillis / (1000 * 60 * 60 * 24);
                
                // Format the actual time
                java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("hh:mm a");
                java.util.Date date = new java.util.Date(timestamp);
                String actualTime = timeFormat.format(date);
                
                if (diffInMinutes < 1) {
                    return "just now at " + actualTime;
                } else if (diffInMinutes < 60) {
                    return diffInMinutes + " minutes ago at " + actualTime;
                } else if (diffInHours < 24) {
                    return diffInHours + " hours ago at " + actualTime;
                } else if (diffInDays < 7) {
                    java.text.SimpleDateFormat dateTimeFormat = new java.text.SimpleDateFormat("MMM dd 'at' hh:mm a");
                    return dateTimeFormat.format(date);
                } else {
                    java.text.SimpleDateFormat displayFormat = new java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a");
                    return displayFormat.format(date);
                }
            } catch (Exception ex) {
                return "recently";
            }
        }
    }
    
    private void showQrCodeDialog(Context context, GameItem gameItem) {
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DarkDialogTheme);
        
        // Inflate custom layout
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_qr_code, null);
        
        // Get views
        ImageView qrCodeImage = dialogView.findViewById(R.id.qr_code_image);
        TextView gameIdText = dialogView.findViewById(R.id.text_game_id_qr);
        ImageView closeButton = dialogView.findViewById(R.id.btn_close);
        
        // Set game information
        gameIdText.setText("Game ID: " + gameItem.getGameId());
        
        // Generate QR code
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(gameItem.getGameId(), BarcodeFormat.QR_CODE, 300, 300);
            qrCodeImage.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
            com.example.rummypulse.utils.ModernToast.error(context, "❌ Failed to generate QR code");
            return;
        }
        
        // Set up dialog
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        
        // Set QR code click listener to copy Game ID
        qrCodeImage.setOnClickListener(v -> {
            copyToClipboard(context, gameItem.getGameId(), "Game ID");
            com.example.rummypulse.utils.ModernToast.success(context, "📋 Game ID copied to clipboard!");
        });
        
        // Set close button listener
        closeButton.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void copyToClipboard(Context context, String text, String label) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        TextView gameIdText, gameStatusText, playersText, pointValueText, gstText, createdTimeText, creatorNameText;
        ImageView qrCodeIcon, creatorProfileImage;
        LinearLayout creatorSection;

        GameViewHolder(@NonNull View itemView) {
            super(itemView);
            gameIdText = itemView.findViewById(R.id.text_game_id);
            gameStatusText = itemView.findViewById(R.id.text_game_status);
            playersText = itemView.findViewById(R.id.text_players);
            pointValueText = itemView.findViewById(R.id.text_point_value);
            gstText = itemView.findViewById(R.id.text_gst);
            createdTimeText = itemView.findViewById(R.id.text_created_time);
            creatorNameText = itemView.findViewById(R.id.text_creator_name);
            creatorProfileImage = itemView.findViewById(R.id.image_creator_profile);
            creatorSection = itemView.findViewById(R.id.creator_section);
            qrCodeIcon = itemView.findViewById(R.id.icon_qr_code_dashboard);
        }
    }
}
