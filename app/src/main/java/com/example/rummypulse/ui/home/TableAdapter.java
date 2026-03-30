package com.example.rummypulse.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

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

            String rowTitle = titleForGameRow(item);

            holder.gameIdHeaderText.setText(rowTitle);
            holder.gameCreatedSummaryText.setText(buildGameCreatedSummary(item));

            // Set Game PIN (initially masked)
            holder.gamePinText.setText("****");
            holder.gamePinText.setTag(item.getGamePin()); // Store actual PIN in tag

            // Set up PIN visibility toggle
            holder.iconViewPin.setOnClickListener(v -> {
                String actualPin = (String) holder.gamePinText.getTag();
                holder.gamePinText.setText(actualPin);
                holder.gamePinText.setTextColor(holder.itemView.getContext().getColor(R.color.accent_orange));
                
                // Hide PIN after 10 seconds
                holder.gamePinText.postDelayed(() -> {
                    holder.gamePinText.setText("****");
                    holder.gamePinText.setTextColor(holder.itemView.getContext().getColor(R.color.accent_orange));
                }, 10000);
            });

            // Set up Game ID click to copy to clipboard
            holder.gameIdHeaderText.setOnClickListener(v -> {
                copyToClipboard(holder.itemView.getContext(), item.getGameId(), "Game ID");
            });
            
            holder.gameCreatedSummaryText.setOnClickListener(v -> {
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
        
        // Set Number of Players
        holder.playersText.setText(item.getNumberOfPlayers());
        
        // Make players text clickable to show players dialog
        holder.playersText.setOnClickListener(v -> {
            showPlayersDialog(holder.itemView.getContext(), item);
        });
        
        // Set GST Percentage
        holder.gstPercentageText.setText(item.getGstPercentage());
        
        // Set GST Amount with currency symbol and null handling
        String gstAmount = item.getGstAmount();
        System.out.println("TableAdapter: Game " + item.getGameId() + " - gstAmount = '" + gstAmount + "'");
        System.out.println("TableAdapter: holder.gstAmountText is " + (holder.gstAmountText == null ? "NULL" : "NOT NULL"));
        
        if (holder.gstAmountText != null) {
            if (gstAmount == null || gstAmount.isEmpty()) {
                System.out.println("TableAdapter: gstAmount is null or empty, setting to ₹0");
                holder.gstAmountText.setText("₹0");
                holder.gstAmountText.setVisibility(android.view.View.VISIBLE);
            } else {
                System.out.println("TableAdapter: Setting gstAmount to ₹" + gstAmount);
                holder.gstAmountText.setText("₹" + gstAmount);
                holder.gstAmountText.setVisibility(android.view.View.VISIBLE);
            }
        } else {
            System.out.println("TableAdapter: ERROR - gstAmountText TextView is NULL!");
        }

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
            
            
            holder.btnApproveGst.setEnabled(isGameCompleted);
            // Remove alpha setting - handled by state list drawable now
            
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

    /** Visible title: {@code games.displayName} when non-empty, else game ID. */
    private static String titleForGameRow(GameItem item) {
        if (item == null) {
            return "";
        }
        String dn = item.getGameDisplayName();
        if (dn != null && !dn.trim().isEmpty()) {
            return dn.trim();
        }
        return item.getGameId() != null ? item.getGameId() : "";
    }

    /** First whitespace-separated token of display name (e.g. email local-part before @ if no space). */
    private static String creatorFirstName(String creatorName) {
        if (creatorName == null) {
            return "";
        }
        String t = creatorName.trim();
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

    private static String buildGameCreatedSummary(GameItem item) {
        String title = titleForGameRow(item);
        String rawWhen = item.getCreationDateTime();
        String when = (rawWhen != null) ? formatDateTime(rawWhen) : "";
        if (when.isEmpty()) {
            when = "unknown date";
        }
        String first = creatorFirstName(item.getCreatorName());
        if (!first.isEmpty()) {
            return title + " created by " + first + " on " + when;
        }
        return title + " on " + when;
    }

    private static String formatDateTime(String dateTime) {
        if (dateTime == null || dateTime.length() < 19) {
            return dateTime == null ? "" : dateTime;
        }
        // Convert from "2024-01-15 14:30:00" to "15 Jan 2024 at 14:30"
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
        return dateTime;
    }

    private void copyToClipboard(Context context, String text, String label) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        com.example.rummypulse.utils.ModernToast.success(context, label + " copied to clipboard");
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
            
            // Calculate total of all scores for net amount calculation
            for (Player player : players) {
                totalScore += player.getTotalScore();
            }
            
            // Get game settings for net amount calculation
            double pointValue = gameItem.getPointValueAsDouble();
            double gstPercent = Double.parseDouble(gameItem.getGstPercentage());
            int numPlayers = gameItem.getNumberOfPlayersAsInt();
            
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                View playerView = LayoutInflater.from(context).inflate(R.layout.item_player_score, null);
                
                TextView playerNameText = playerView.findViewById(R.id.text_player_name);
                TextView playerScoreText = playerView.findViewById(R.id.text_player_score);
                TextView netAmountText = playerView.findViewById(R.id.text_net_amount);
                
                String playerName = player.getName();
                if (playerName == null || playerName.isEmpty()) {
                    playerName = "Unknown Player";
                }
                
                int playerScore = player.getTotalScore();
                
                // Calculate net amount using the same formula as index.html
                // Formula: (Total of all scores - Player's score × Number of players) × Point value
                double grossAmount = Math.round((totalScore - playerScore * numPlayers) * pointValue);
                
                double gstPaid = 0;
                double netAmount = grossAmount;
                
                // GST is only paid by winners (those with positive gross amount)
                if (grossAmount > 0) {
                    gstPaid = Math.round((grossAmount * gstPercent) / 100.0);
                    netAmount = grossAmount - gstPaid;
                }
                
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
                
                // Format and color net amount
                String netAmountFormatted;
                if (netAmount > 0) {
                    netAmountFormatted = "+₹" + Math.round(netAmount);
                    netAmountText.setTextColor(context.getColor(R.color.success_green)); // Green for winners
                } else if (netAmount < 0) {
                    netAmountFormatted = "₹" + Math.round(netAmount);
                    netAmountText.setTextColor(context.getColor(R.color.error_red)); // Red for losers
                } else {
                    netAmountFormatted = "₹0";
                    netAmountText.setTextColor(context.getColor(R.color.text_secondary)); // Gray for break-even
                }
                
                netAmountText.setText(netAmountFormatted);
                
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

        public static class TableViewHolder extends RecyclerView.ViewHolder {
            TextView gameIdHeaderText, gameCreatedSummaryText, gamePinText, pointValueText, playersText, gstPercentageText, gstAmountText, ageText, statusText;
            ImageView iconViewPin;
            View btnApproveGst, btnDeleteGame;

            public TableViewHolder(@NonNull View itemView) {
                super(itemView);
                gameIdHeaderText = itemView.findViewById(R.id.text_game_id_header);
                gameCreatedSummaryText = itemView.findViewById(R.id.text_game_created_summary);
                gamePinText = itemView.findViewById(R.id.text_game_pin);
                pointValueText = itemView.findViewById(R.id.text_point_value);
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
