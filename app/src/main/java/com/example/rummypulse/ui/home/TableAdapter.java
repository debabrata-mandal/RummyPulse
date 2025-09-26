package com.example.rummypulse.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.example.rummypulse.data.Player;

import java.util.List;

public class TableAdapter extends RecyclerView.Adapter<TableAdapter.TableViewHolder> {
    private List<GameItem> gameItems;
    private OnGameActionListener actionListener;

    public interface OnGameActionListener {
        void onApproveGst(GameItem game, int position);
        void onDeleteGame(GameItem game, int position);
    }

    public TableAdapter(List<GameItem> gameItems) {
        this.gameItems = gameItems;
    }

    public void setOnGameActionListener(OnGameActionListener listener) {
        this.actionListener = listener;
    }

    @NonNull
    @Override
    public TableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.table_row_item, parent, false);
        return new TableViewHolder(view);
    }

        @Override
        public void onBindViewHolder(@NonNull TableViewHolder holder, int position) {
            GameItem item = gameItems.get(position);

            // Set Game ID Header
            holder.gameIdHeaderText.setText(item.getGameId());
            
            // Set Game ID in created text
            holder.gameIdInCreatedText.setText(item.getGameId());

            // Set Game PIN (initially masked)
            holder.gamePinText.setText("****");
            holder.gamePinText.setTag(item.getGamePin()); // Store actual PIN in tag

            // Set up PIN visibility toggle
            holder.iconViewPin.setOnClickListener(v -> {
                String actualPin = (String) holder.gamePinText.getTag();
                holder.gamePinText.setText(actualPin);
                holder.gamePinText.setTextColor(holder.itemView.getContext().getColor(R.color.accent_orange));
                
                // Hide PIN after 30 seconds
                holder.gamePinText.postDelayed(() -> {
                    holder.gamePinText.setText("****");
                    holder.gamePinText.setTextColor(holder.itemView.getContext().getColor(R.color.accent_orange));
                }, 30000);
            });

            // Set up Game ID click to copy to clipboard
            holder.gameIdHeaderText.setOnClickListener(v -> {
                copyToClipboard(holder.itemView.getContext(), item.getGameId(), "Game ID");
            });
            
            holder.gameIdInCreatedText.setOnClickListener(v -> {
                copyToClipboard(holder.itemView.getContext(), item.getGameId(), "Game ID");
            });

            // Set Point Value with currency formatting and null checking
            String pointValue = item.getPointValue();
            if (pointValue == null || pointValue.isEmpty()) {
                holder.pointValueText.setText("₹0.00");
                System.out.println("Point value is null/empty for game " + item.getGameId() + ", setting to ₹0.00");
            } else {
                holder.pointValueText.setText("₹" + pointValue);
                System.out.println("Setting point value for game " + item.getGameId() + ": ₹" + pointValue);
            }
        
            // Set Creation DateTime
            holder.creationDateText.setText(formatDateTime(item.getCreationDateTime()));
        
        // Set Number of Players
        holder.playersText.setText(item.getNumberOfPlayers());
        
        // Make players text clickable to show players dialog
        holder.playersText.setOnClickListener(v -> {
            showPlayersDialog(holder.itemView.getContext(), item);
        });
        
        // Set GST Percentage
        holder.gstPercentageText.setText(item.getGstPercentage() + "%");
        
            // Set GST Amount with currency symbol
            holder.gstAmountText.setText("₹" + item.getGstAmount());

            // Set Age
            String age = item.getAge();
            if (age == null) {
                age = "Unknown";
            }
            holder.ageText.setText(age);

            // Set Status with null checking and debug logging
            String status = item.getGameStatus();
            if (status == null || status.isEmpty()) {
                status = "Unknown";
            }
            holder.statusText.setText(status);
            
            // Debug logging for status
            System.out.println("Setting status for game " + item.getGameId() + ": '" + status + "'");
            
            // Set status color based on status
            try {
                if (status.equals("Completed")) {
                    holder.statusText.setTextColor(holder.itemView.getContext().getColor(R.color.status_offline));
                } else if (status.startsWith("R")) {
                    holder.statusText.setTextColor(holder.itemView.getContext().getColor(R.color.status_online));
                } else {
                    holder.statusText.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));
                }
            } catch (Exception e) {
                // Fallback to default color if there's any issue
                holder.statusText.setTextColor(holder.itemView.getContext().getColor(android.R.color.black));
            }

            // Enable/disable approve button based on game status
            String gameStatus = item.getGameStatus();
            boolean isGameCompleted = "Completed".equals(gameStatus);
            
            // Debug logging
            System.out.println("Game ID: " + item.getGameId() + ", Status: '" + gameStatus + "', Completed: " + isGameCompleted);
            
            holder.btnApproveGst.setEnabled(isGameCompleted);
            holder.btnApproveGst.setAlpha(isGameCompleted ? 1.0f : 0.5f);
            
            // Set up button click listeners
            holder.btnApproveGst.setOnClickListener(v -> {
                if (actionListener != null && isGameCompleted) {
                    actionListener.onApproveGst(item, position);
                }
            });


            holder.btnDeleteGame.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onDeleteGame(item, position);
                }
            });
        }

    @Override
    public int getItemCount() {
        return gameItems.size();
    }

    private String formatNumber(String number) {
        try {
            int num = Integer.parseInt(number);
            return String.format("%,d", num);
        } catch (NumberFormatException e) {
            return number;
        }
    }

    private String formatDateTime(String dateTime) {
        // Convert from "2024-01-15 14:30:00" to "15 Jan 2024 at 14:30"
        if (dateTime.length() >= 19) {
            String date = dateTime.substring(0, 10);
            String time = dateTime.substring(11, 16);
            
            String[] parts = date.split("-");
            if (parts.length == 3) {
                String year = parts[0];
                String month = parts[1];
                String day = parts[2];
                
                String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                                 "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                int monthIndex = Integer.parseInt(month) - 1;
                
                return day + " " + months[monthIndex] + " " + year + " at " + time;
            }
        }
        return dateTime;
    }

    private void copyToClipboard(Context context, String text, String label) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, label + " copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void showPlayersDialog(Context context, GameItem gameItem) {
        // Create dialog view
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_players_list, null);
        
        // Set game ID
        TextView gameIdText = dialogView.findViewById(R.id.text_dialog_game_id);
        gameIdText.setText(gameItem.getGameId());
        
        // Get players container
        LinearLayout playersContainer = dialogView.findViewById(R.id.players_container);
        
        // Clear existing views
        playersContainer.removeAllViews();
        
        // Add players to the dialog
        List<Player> players = gameItem.getPlayers();
        int totalScore = 0;
        
        if (players != null && !players.isEmpty()) {
            // Sort players by total score (lowest to highest)
            players.sort((p1, p2) -> Integer.compare(p1.getTotalScore(), p2.getTotalScore()));
            
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                View playerView = LayoutInflater.from(context).inflate(R.layout.item_player_score, null);
                
                TextView playerNameText = playerView.findViewById(R.id.text_player_name);
                TextView playerScoreText = playerView.findViewById(R.id.text_player_score);
                
                String playerName = player.getName();
                if (playerName == null || playerName.isEmpty()) {
                    playerName = "Unknown Player";
                }
                
                int playerScore = player.getTotalScore();
                totalScore += playerScore;
                
                // Add ranking indicator and winner highlighting
                int rank = i + 1;
                String rankIndicator = "";
                if (rank == 1) {
                    rankIndicator = "🏆 "; // Winner (lowest score)
                    playerScoreText.setTextColor(context.getColor(R.color.success_green));
                } else if (rank == 2) {
                    rankIndicator = "🥈 ";
                    playerScoreText.setTextColor(context.getColor(R.color.warning_orange));
                } else if (rank == 3) {
                    rankIndicator = "🥉 ";
                    playerScoreText.setTextColor(context.getColor(R.color.error_red));
                } else {
                    rankIndicator = rank + ". ";
                    playerScoreText.setTextColor(context.getColor(R.color.text_primary));
                }
                
                playerNameText.setText(rankIndicator + playerName);
                playerScoreText.setText(String.valueOf(playerScore));
                
                playersContainer.addView(playerView);
            }
        } else {
            // Show message if no players
            TextView noPlayersText = new TextView(context);
            noPlayersText.setText("No players data available");
            noPlayersText.setTextSize(16);
            noPlayersText.setTextColor(context.getColor(R.color.text_secondary));
            noPlayersText.setPadding(32, 32, 32, 32);
            noPlayersText.setGravity(android.view.Gravity.CENTER);
            playersContainer.addView(noPlayersText);
        }
        
        // Set total score
        TextView totalScoreText = dialogView.findViewById(R.id.text_total_score);
        totalScoreText.setText(String.valueOf(totalScore));
        
        // Create and show dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        
        // Set close button listener
        ImageView closeButton = dialogView.findViewById(R.id.btn_close);
        closeButton.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

        public static class TableViewHolder extends RecyclerView.ViewHolder {
            TextView gameIdHeaderText, gameIdInCreatedText, gamePinText, pointValueText, creationDateText, playersText, gstPercentageText, gstAmountText, ageText, statusText;
            ImageView iconViewPin;
            View btnApproveGst, btnDeleteGame;

            public TableViewHolder(@NonNull View itemView) {
                super(itemView);
                gameIdHeaderText = itemView.findViewById(R.id.text_game_id_header);
                gameIdInCreatedText = itemView.findViewById(R.id.text_game_id_in_created);
                gamePinText = itemView.findViewById(R.id.text_game_pin);
                pointValueText = itemView.findViewById(R.id.text_point_value);
                creationDateText = itemView.findViewById(R.id.text_creation_date);
                playersText = itemView.findViewById(R.id.text_players);
                gstPercentageText = itemView.findViewById(R.id.text_gst_percentage);
                gstAmountText = itemView.findViewById(R.id.text_gst_amount);
                ageText = itemView.findViewById(R.id.text_age);
                statusText = itemView.findViewById(R.id.text_status);
                iconViewPin = itemView.findViewById(R.id.icon_view_pin);
                btnApproveGst = itemView.findViewById(R.id.btn_approve_gst);
                btnDeleteGame = itemView.findViewById(R.id.btn_delete_game);
            }
        }
}
