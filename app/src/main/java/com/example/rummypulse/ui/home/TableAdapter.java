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
import android.annotation.SuppressLint;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.Player;
import com.example.rummypulse.ui.playerconsolidation.PlayerGamePointsCalculator;
import com.example.rummypulse.utils.GameAttributionFormatter;
import com.example.rummypulse.utils.ProfileAvatarBinder;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TableAdapter extends RecyclerView.Adapter<TableAdapter.TableViewHolder> {
    private List<GameItem> gameItems;
    private OnGameActionListener actionListener;
    private OnSelectionChangedListener selectionChangedListener;
    private final ReviewSelectionModel selection = new ReviewSelectionModel();
    private boolean actionsEnabled = true;
    private Map<String, AppUser> directoryUsersById;

    public interface OnGameActionListener {
        void onApproveBoardAdjustment(GameItem game, int position);
        void onDeleteGame(GameItem game, int position);
        void onEditGameEconomics(GameItem game, int position);
        void onKickOutEditor(GameItem game, int position);
        void onChangePlayerMapping(GameItem game, Player player);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount, boolean allSelected);
    }

    public TableAdapter(List<GameItem> gameItems) {
        this.gameItems = gameItems;
    }

    public void setOnGameActionListener(OnGameActionListener listener) {
        this.actionListener = listener;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
        notifySelectionChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void submitItems(List<GameItem> items) {
        gameItems = items != null ? items : new ArrayList<>();
        selection.retainAvailable(availableGameIds());
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void selectAll(boolean select) {
        if (select) {
            selection.selectAll(availableGameIds());
        } else {
            selection.clear();
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void clearSelection() {
        selectAll(false);
    }

    public List<String> getSelectedGameIds() {
        return selection.snapshot();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setActionsEnabled(boolean enabled) {
        if (actionsEnabled == enabled) {
            return;
        }
        actionsEnabled = enabled;
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setDirectoryUsers(List<AppUser> users) {
        directoryUsersById = new HashMap<>();
        if (users != null) {
            for (AppUser user : users) {
                if (user != null && user.getUserId() != null) {
                    directoryUsersById.put(user.getUserId(), user);
                }
            }
        }
        notifyDataSetChanged();
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
            String gameId = item.getGameId();

            holder.selectGameCheckBox.setOnCheckedChangeListener(null);
            holder.selectGameCheckBox.setEnabled(actionsEnabled);
            holder.selectGameCheckBox.setChecked(selection.isSelected(gameId));
            holder.selectGameCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!actionsEnabled) {
                    return;
                }
                selection.setSelected(gameId, isChecked);
                notifySelectionChanged();
            });

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
                
                // Hide PIN after 10 seconds
                holder.gamePinText.postDelayed(() -> {
                    holder.gamePinText.setText("****");
                }, 10000);
            });

            // Set up Game ID click to copy to clipboard
            holder.gameIdHeaderText.setOnClickListener(v -> {
                copyToClipboard(holder.itemView.getContext(), item.getGameId(), "Game ID");
            });
            
            holder.gameCreatedSummaryText.setOnClickListener(v -> {
                copyToClipboard(holder.itemView.getContext(), item.getGameId(), "Game ID");
            });

            // Set Game Point Factor with null checking.
            String gamePointFactor = item.getGamePointFactor();
            if (gamePointFactor == null || gamePointFactor.isEmpty()) {
                holder.gamePointFactorText.setText(
                        holder.itemView.getContext().getString(
                                R.string.format_game_point_factor, "0"));
                System.out.println("Point value is null/empty, using zero");
            } else {
                holder.gamePointFactorText.setText(
                        holder.itemView.getContext().getString(
                                R.string.format_game_point_factor, gamePointFactor));
                System.out.println("Setting game point value");
            }

            holder.gamePointFactorText.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onEditGameEconomics(item, position);
                }
            });
        
        // Set Number of Players
        holder.playersText.setText(item.getNumberOfPlayers());
        
        // Make players text clickable to show players dialog
        holder.playersText.setOnClickListener(v -> {
            showPlayersDialog(holder.itemView.getContext(), item);
        });
        
        // Set Board Adjustment percentage
        holder.boardAdjustmentPercentageText.setText(item.getBoardAdjustmentPercentage());
        holder.boardAdjustmentPercentageText.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onEditGameEconomics(item, position);
            }
        });
        
        // Set Board Points with unit formatting and null handling.
        String boardPoints = item.getBoardPoints();
        System.out.println("TableAdapter: Binding game boardAdjustment amount");
        System.out.println("TableAdapter: holder.boardPointsText is " + (holder.boardPointsText == null ? "NULL" : "NOT NULL"));
        
        if (holder.boardPointsText != null) {
            if (boardPoints == null || boardPoints.isEmpty()) {
                System.out.println("TableAdapter: Board Points are null or empty, using zero");
                holder.boardPointsText.setText(R.string.game_points_zero);
                holder.boardPointsText.setVisibility(android.view.View.VISIBLE);
            } else {
                System.out.println("TableAdapter: Setting boardAdjustment amount");
                holder.boardPointsText.setText(
                        holder.itemView.getContext().getString(R.string.format_game_points, boardPoints));
                holder.boardPointsText.setVisibility(android.view.View.VISIBLE);
            }
        } else {
            System.out.println("TableAdapter: ERROR - boardPointsText TextView is NULL!");
        }

            // Set Age
            String age = item.getAge();
            if (age == null) {
                age = "Unknown";
            }
            holder.ageText.setText(age);

            String status = item.getGameStatus();
            if (status == null || status.isEmpty()) {
                status = "Unknown";
            }
            holder.statusText.setText(status);
            holder.statusText.setTextColor(holder.itemView.getContext().getColor(R.color.white));
            if ("Completed".equals(status)) {
                holder.statusText.setBackgroundResource(R.drawable.status_background_green);
            } else if (status.startsWith("R")) {
                holder.statusText.setBackgroundResource(R.drawable.status_background_orange);
            } else {
                holder.statusText.setBackgroundResource(R.drawable.bg_dashboard_status_offline);
            }

            // Enable/disable approve button based on game status
            String gameStatus = item.getGameStatus();
            boolean isGameCompleted = "Completed".equals(gameStatus);
            boolean hasValidMappings = hasValidPlayerMappings(item);
            
            
            holder.btnApproveBoardAdjustment.setEnabled(
                    actionsEnabled && isGameCompleted && hasValidMappings);
            holder.btnDeleteGame.setEnabled(actionsEnabled);

            // Set up button click listeners
            holder.btnApproveBoardAdjustment.setOnClickListener(v -> {
                if (actionListener != null && actionsEnabled
                        && isGameCompleted && hasValidMappings) {
                    actionListener.onApproveBoardAdjustment(item, position);
                }
            });


            holder.btnDeleteGame.setOnClickListener(v -> {
                if (actionListener != null && actionsEnabled) {
                    actionListener.onDeleteGame(item, position);
                }
            });

            // Surface the kick control only when someone currently holds edit access; who last
            // touched the game is already shown in the created/edited-by summary line above.
            if (holder.btnKickEditor != null) {
                if (item.hasActiveEditor()) {
                    holder.btnKickEditor.setVisibility(android.view.View.VISIBLE);
                    holder.btnKickEditor.setEnabled(actionsEnabled);
                    holder.btnKickEditor.setOnClickListener(v -> {
                        if (actionListener != null && actionsEnabled) {
                            actionListener.onKickOutEditor(item, position);
                        }
                    });
                } else {
                    holder.btnKickEditor.setVisibility(android.view.View.GONE);
                    holder.btnKickEditor.setOnClickListener(null);
                }
            }
        }

    @Override
    public int getItemCount() {
        return gameItems.size();
    }

    private List<String> availableGameIds() {
        List<String> ids = new ArrayList<>();
        for (GameItem item : gameItems) {
            if (item != null && item.getGameId() != null && !item.getGameId().trim().isEmpty()) {
                ids.add(item.getGameId());
            }
        }
        return ids;
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(
                    selection.size(), selection.areAllSelected(availableGameIds()));
        }
    }

    private String formatNumber(String number) {
        try {
            int num = Integer.parseInt(number);
            return String.format(Locale.getDefault(), "%,d", num);
        } catch (NumberFormatException e) {
            return number;
        }
    }

    /** Visible title: {@code games_v2.displayName} when non-empty, else game ID. */
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

    private static String buildGameCreatedSummary(GameItem item) {
        String rawWhen = item.getCreationDateTime();
        String when = (rawWhen != null) ? formatDateTime(rawWhen) : "";
        if (when.isEmpty()) {
            when = "unknown date";
        }
        return GameAttributionFormatter.formatCreatorEditorPlainText(item) + " · started " + when;
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
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_players_list, new android.widget.FrameLayout(context), false);
        AlertDialog dialog = new AlertDialog.Builder(context, R.style.DarkDialogTheme)
                .setView(dialogView)
                .create();
        
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
            players = new ArrayList<>(players);
            players.sort((p1, p2) -> Integer.compare(p1.getTotalScore(), p2.getTotalScore()));

            for (Player player : players) {
                totalScore += player.getTotalScore();
            }
            
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                View playerView = LayoutInflater.from(context)
                        .inflate(R.layout.item_player_score, new android.widget.FrameLayout(context), false);
                
                TextView playerNameText = playerView.findViewById(R.id.text_player_name);
                TextView playerScoreText = playerView.findViewById(R.id.text_player_score);
                TextView finalGamePointsText = playerView.findViewById(R.id.text_final_game_points);
                View avatarButton = playerView.findViewById(R.id.btn_review_map_player);
                ImageView avatarImage = playerView.findViewById(R.id.review_player_avatar_image);
                TextView avatarInitial = playerView.findViewById(R.id.review_player_avatar_initial);
                
                AppUser directoryUser = directoryUser(player);
                String playerName = directoryUser != null
                        ? directoryUser.getDisplayName() : player.getName();
                if (playerName == null || playerName.trim().isEmpty()) playerName = "Unknown";
                playerNameText.setText(playerName);
                ProfileAvatarBinder.bindWithPhotoUrl(
                        playerView,
                        avatarImage,
                        avatarInitial,
                        playerName,
                        directoryUser == null ? null : directoryUser.getPhotoUrl(),
                        directoryUser == null ? 0L : directoryUser.getProfileVersion(),
                        null,
                        false,
                        null,
                        null);
                avatarButton.setEnabled(actionsEnabled);
                avatarButton.setAlpha(actionsEnabled ? 1f : 0.65f);
                avatarButton.setOnClickListener(v -> {
                    if (actionListener != null && actionsEnabled) {
                        dialog.dismiss();
                        actionListener.onChangePlayerMapping(gameItem, player);
                    }
                });
                
                int playerScore = player.getTotalScore();
                
                PlayerGamePointsCalculator.PlayerGamePoints gamePoints =
                        PlayerGamePointsCalculator.compute(gameItem, player);
                double finalGamePoints = gamePoints.finalGamePoints;
                
                playerScoreText.setTextColor(context.getColor(R.color.view_gold));
                playerScoreText.setText(String.valueOf(playerScore));

                if (finalGamePoints > 0) {
                    finalGamePointsText.setText(context.getString(
                            R.string.format_game_points_positive, String.valueOf(Math.round(finalGamePoints))));
                    finalGamePointsText.setTextColor(context.getColor(R.color.success_green));
                } else if (finalGamePoints < 0) {
                    finalGamePointsText.setText(context.getString(
                            R.string.format_game_points, String.valueOf(Math.round(finalGamePoints))));
                    finalGamePointsText.setTextColor(context.getColor(R.color.error_red));
                } else {
                    finalGamePointsText.setText(R.string.game_points_zero);
                    finalGamePointsText.setTextColor(context.getColor(R.color.text_secondary));
                }
                
                playersContainer.addView(playerView);
            }
        } else {
            // Show message if no players
            TextView noPlayersText = new TextView(context);
            noPlayersText.setText(context.getString(R.string.no_players_data_available));
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
        // Set close button listener
        View closeButton = dialogView.findViewById(R.id.btn_close);
        closeButton.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
            android.util.DisplayMetrics dm =
                    context.getResources().getDisplayMetrics();
            int maxWidth = Math.round(420 * dm.density);
            int width = Math.min(Math.round(dm.widthPixels * 0.92f), maxWidth);
            dialog.getWindow().setLayout(
                    width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private boolean hasValidPlayerMappings(GameItem item) {
        if (item == null || item.getPlayers() == null || item.getPlayers().size() < 2) {
            return false;
        }
        java.util.Set<String> userIds = new java.util.HashSet<>();
        for (Player player : item.getPlayers()) {
            if (!isKnownProfile(player)
                    || !userIds.add(player.getUserId())) {
                return false;
            }
        }
        return true;
    }

    private boolean isKnownProfile(Player player) {
        if (player == null || player.getUserId() == null
                || player.getUserId().trim().isEmpty()) {
            return false;
        }
        return directoryUsersById == null || directoryUsersById.containsKey(player.getUserId());
    }

    private AppUser directoryUser(Player player) {
        if (player == null || player.getUserId() == null || directoryUsersById == null) return null;
        return directoryUsersById.get(player.getUserId());
    }
    
    private void showQrCodeDialog(Context context, GameItem gameItem) {
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DarkDialogTheme);
        
        // Inflate custom layout
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_qr_code, new android.widget.FrameLayout(context), false);
        
        // Get views
        ImageView qrCodeImage = dialogView.findViewById(R.id.qr_code_image);
        TextView gameIdText = dialogView.findViewById(R.id.text_game_id_qr);
        ImageView closeButton = dialogView.findViewById(R.id.btn_close);
        
        // Set game information
        gameIdText.setText(context.getString(R.string.game_id_label, gameItem.getGameId()));
        
        // Generate QR code
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(gameItem.getGameId(), BarcodeFormat.QR_CODE, 300, 300);
            qrCodeImage.setImageBitmap(bitmap);
        } catch (WriterException e) {
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
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int width = Math.min(Math.round(dm.widthPixels * 0.92f),
                    Math.round(420 * dm.density));
            dialog.getWindow().setLayout(
                    width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

        public static class TableViewHolder extends RecyclerView.ViewHolder {
            TextView gameIdHeaderText, gameCreatedSummaryText, gamePinText, gamePointFactorText, playersText, boardAdjustmentPercentageText, boardPointsText, ageText, statusText;
            ImageView iconViewPin;
            View btnApproveBoardAdjustment, btnDeleteGame, btnKickEditor;
            MaterialCheckBox selectGameCheckBox;

            public TableViewHolder(@NonNull View itemView) {
                super(itemView);
                gameIdHeaderText = itemView.findViewById(R.id.text_game_id_header);
                gameCreatedSummaryText = itemView.findViewById(R.id.text_game_created_summary);
                gamePinText = itemView.findViewById(R.id.text_game_pin);
                gamePointFactorText = itemView.findViewById(R.id.text_game_point_factor);
                playersText = itemView.findViewById(R.id.text_players);
                boardAdjustmentPercentageText = itemView.findViewById(R.id.text_board_adjustment_percentage);
                boardPointsText = itemView.findViewById(R.id.text_board_points);
                ageText = itemView.findViewById(R.id.text_age);
                statusText = itemView.findViewById(R.id.text_status);
                selectGameCheckBox = itemView.findViewById(R.id.checkbox_select_game);
                iconViewPin = itemView.findViewById(R.id.icon_view_pin);
                btnApproveBoardAdjustment = itemView.findViewById(R.id.btn_approve_board_adjustment);
                btnDeleteGame = itemView.findViewById(R.id.btn_delete_game);
                btnKickEditor = itemView.findViewById(R.id.btn_kick_editor);
            }
        }
}
