package com.example.rummypulse.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;

import java.util.List;

public class TableAdapter extends RecyclerView.Adapter<TableAdapter.TableViewHolder> {
    private List<GameItem> gameItems;

    public TableAdapter(List<GameItem> gameItems) {
        this.gameItems = gameItems;
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
        
        // Set Game ID
        holder.gameIdText.setText(item.getGameId());
        
        // Set Total Score with formatting
        holder.totalScoreText.setText(formatNumber(item.getTotalScore()));
        
        // Set Point Value with currency formatting
        holder.pointValueText.setText("₹" + item.getPointValue());
        
        // Set Creation DateTime
        holder.creationTimeText.setText(formatDateTime(item.getCreationDateTime()));
        
        // Set Game Status with indicator
        holder.statusText.setText(item.getGameStatus());
        if (item.getGameStatus().equals("Active")) {
            holder.statusIndicator.setBackgroundResource(R.drawable.status_indicator_online);
            holder.statusText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.status_online));
        } else if (item.getGameStatus().equals("Completed")) {
            holder.statusIndicator.setBackgroundResource(R.drawable.status_indicator_offline);
            holder.statusText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.status_offline));
        } else {
            holder.statusIndicator.setBackgroundResource(R.drawable.status_indicator_offline);
            holder.statusText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.text_secondary));
        }
        
        // Set Number of Players
        holder.playersText.setText(item.getNumberOfPlayers());
        
        // Set GST Percentage
        holder.gstPercentageText.setText(item.getGstPercentage() + "%");
        
        // Set GST Amount with currency symbol
        holder.gstAmountText.setText("₹" + item.getGstAmount());
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
        // Convert from "2024-01-15 14:30:00" to "15 Jan 2024\n14:30"
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
                
                return day + " " + months[monthIndex] + " " + year + "\n" + time;
            }
        }
        return dateTime;
    }

    public static class TableViewHolder extends RecyclerView.ViewHolder {
        TextView gameIdText, totalScoreText, pointValueText, creationTimeText, statusText, playersText, gstPercentageText, gstAmountText;
        View statusIndicator;

        public TableViewHolder(@NonNull View itemView) {
            super(itemView);
            gameIdText = itemView.findViewById(R.id.text_game_id);
            totalScoreText = itemView.findViewById(R.id.text_total_score);
            pointValueText = itemView.findViewById(R.id.text_point_value);
            creationTimeText = itemView.findViewById(R.id.text_creation_time);
            statusText = itemView.findViewById(R.id.text_status);
            playersText = itemView.findViewById(R.id.text_players);
            gstPercentageText = itemView.findViewById(R.id.text_gst_percentage);
            gstAmountText = itemView.findViewById(R.id.text_gst_amount);
            statusIndicator = itemView.findViewById(R.id.status_indicator);
        }
    }
}
