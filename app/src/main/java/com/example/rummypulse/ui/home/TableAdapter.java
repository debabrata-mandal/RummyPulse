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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;

import java.util.List;

public class TableAdapter extends RecyclerView.Adapter<TableAdapter.TableViewHolder> {
    private List<GameItem> gameItems;
    private OnGameActionListener actionListener;

    public interface OnGameActionListener {
        void onApproveGst(GameItem game, int position);
        void onNotApplicable(GameItem game, int position);
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
                holder.gamePinText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.accent_orange));
                
                // Hide PIN after 30 seconds
                holder.gamePinText.postDelayed(() -> {
                    holder.gamePinText.setText("****");
                    holder.gamePinText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.accent_orange));
                }, 30000);
            });

            // Set up Game ID click to copy to clipboard
            holder.gameIdHeaderText.setOnClickListener(v -> {
                copyToClipboard(holder.itemView.getContext(), item.getGameId(), "Game ID");
            });
            
            holder.gameIdInCreatedText.setOnClickListener(v -> {
                copyToClipboard(holder.itemView.getContext(), item.getGameId(), "Game ID");
            });

            // Set Point Value with currency formatting
        holder.pointValueText.setText("₹" + item.getPointValue());
        
            // Set Creation DateTime
            holder.creationDateText.setText(formatDateTime(item.getCreationDateTime()));
        
        // Set Number of Players
        holder.playersText.setText(item.getNumberOfPlayers());
        
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

            // Set Status
            String status = item.getGameStatus();
            if (status == null) {
                status = "Unknown";
            }
            holder.statusText.setText(status);
            
            // Set status color based on status
            try {
                if (status.equals("Completed")) {
                    holder.statusText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.status_offline));
                } else if (status.startsWith("R")) {
                    holder.statusText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.status_online));
                } else {
                    holder.statusText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.text_secondary));
                }
            } catch (Exception e) {
                // Fallback to default color if there's any issue
                holder.statusText.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.black));
            }

            // Set up button click listeners
            holder.btnApproveGst.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onApproveGst(item, position);
                }
            });

            holder.btnNotApplicable.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onNotApplicable(item, position);
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

        public static class TableViewHolder extends RecyclerView.ViewHolder {
            TextView gameIdHeaderText, gameIdInCreatedText, gamePinText, pointValueText, creationDateText, playersText, gstPercentageText, gstAmountText, ageText, statusText;
            ImageView iconViewPin;
            View btnApproveGst, btnNotApplicable, btnDeleteGame;

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
                btnNotApplicable = itemView.findViewById(R.id.btn_not_applicable);
                btnDeleteGame = itemView.findViewById(R.id.btn_delete_game);
            }
        }
}
