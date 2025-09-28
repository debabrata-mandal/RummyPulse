package com.example.rummypulse.ui.dashboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
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

    public interface OnGameJoinListener {
        void onJoinGame(GameItem game, int position, String joinType);
    }

    public void setOnGameJoinListener(OnGameJoinListener listener) {
        this.joinListener = listener;
    }

    public void setGameItems(List<GameItem> gameItems) {
        this.gameItems = gameItems != null ? gameItems : new ArrayList<>();
        notifyDataSetChanged();
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
        GameItem item = gameItems.get(position);
        
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
        holder.playersText.setText(item.getNumberOfPlayers() + " players");
        
        // Set point value
        String pointValue = item.getPointValue();
        if (pointValue == null || pointValue.isEmpty()) {
            holder.pointValueText.setText("₹0.00");
        } else {
            holder.pointValueText.setText("₹" + pointValue);
        }
        
        // Set GST percentage
        holder.gstText.setText(item.getGstPercentage());
        
        // Set created time
        holder.createdTimeText.setText("Started " + formatDateTime(item.getCreationDateTime()));
        
        // Set creator name if available
        if (item.getCreatorName() != null && !item.getCreatorName().trim().isEmpty()) {
            holder.creatorNameText.setText("Created by: " + item.getCreatorName());
            holder.creatorNameText.setVisibility(View.VISIBLE);
        } else {
            holder.creatorNameText.setVisibility(View.GONE);
        }
        
        // Set join button click listener (default join as player)
        holder.joinButton.setOnClickListener(v -> {
            if (joinListener != null) {
                joinListener.onJoinGame(item, position, "player");
            }
        });
        
        // Set dropdown button click listener
        holder.dropdownButton.setOnClickListener(v -> {
            showJoinOptionsMenu(v, item, position);
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
    
    private void showJoinOptionsMenu(View anchor, GameItem item, int position) {
        // Create popup menu with dark theme
        Context wrapper = new ContextThemeWrapper(anchor.getContext(), R.style.DarkPopupMenuStyle);
        PopupMenu popup = new PopupMenu(wrapper, anchor);
        popup.getMenuInflater().inflate(R.menu.join_game_menu, popup.getMenu());
        
        // Force show icons in popup menu
        try {
            java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(popup);
            Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
            java.lang.reflect.Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
            setForceIcons.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            // Ignore if reflection fails
        }
        
        popup.setOnMenuItemClickListener(menuItem -> {
            if (joinListener != null) {
                if (menuItem.getItemId() == R.id.join_as_moderator) {
                    joinListener.onJoinGame(item, position, "moderator");
                    return true;
                }
            }
            return false;
        });
        
        popup.show();
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
            
            if (diffInMinutes < 1) {
                return "just now";
            } else if (diffInMinutes < 60) {
                return diffInMinutes + " minutes ago";
            } else if (diffInHours < 24) {
                return diffInHours + " hours ago";
            } else if (diffInDays < 7) {
                return diffInDays + " days ago";
            } else {
                // For older dates, show the actual date
                java.text.SimpleDateFormat displayFormat = new java.text.SimpleDateFormat("MMM dd, yyyy");
                return "on " + displayFormat.format(creationDate);
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
                
                if (diffInMinutes < 1) {
                    return "just now";
                } else if (diffInMinutes < 60) {
                    return diffInMinutes + " minutes ago";
                } else if (diffInHours < 24) {
                    return diffInHours + " hours ago";
                } else {
                    return diffInDays + " days ago";
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
            Toast.makeText(context, "❌ Failed to generate QR code", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Set up dialog
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        
        // Set QR code click listener to copy Game ID
        qrCodeImage.setOnClickListener(v -> {
            copyToClipboard(context, gameItem.getGameId(), "Game ID");
            Toast.makeText(context, "📋 Game ID copied to clipboard!", Toast.LENGTH_SHORT).show();
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
        Button joinButton, dropdownButton;
        ImageView qrCodeIcon;

        GameViewHolder(@NonNull View itemView) {
            super(itemView);
            gameIdText = itemView.findViewById(R.id.text_game_id);
            gameStatusText = itemView.findViewById(R.id.text_game_status);
            playersText = itemView.findViewById(R.id.text_players);
            pointValueText = itemView.findViewById(R.id.text_point_value);
            gstText = itemView.findViewById(R.id.text_gst);
            createdTimeText = itemView.findViewById(R.id.text_created_time);
            creatorNameText = itemView.findViewById(R.id.text_creator_name);
            qrCodeIcon = itemView.findViewById(R.id.icon_qr_code_dashboard);
            joinButton = itemView.findViewById(R.id.btn_join_game);
            dropdownButton = itemView.findViewById(R.id.btn_join_dropdown);
        }
    }
}
