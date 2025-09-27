package com.example.rummypulse.ui.dashboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

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
        void onJoinGame(GameItem game, int position);
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
        
        // Set join button click listener
        holder.joinButton.setOnClickListener(v -> {
            if (joinListener != null) {
                joinListener.onJoinGame(item, position);
            }
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
        
        // Simple time formatting - you can enhance this
        try {
            // For now, just return a simple format
            return "recently";
        } catch (Exception e) {
            return "recently";
        }
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        TextView gameIdText, gameStatusText, playersText, pointValueText, gstText, createdTimeText;
        Button joinButton;

        GameViewHolder(@NonNull View itemView) {
            super(itemView);
            gameIdText = itemView.findViewById(R.id.text_game_id);
            gameStatusText = itemView.findViewById(R.id.text_game_status);
            playersText = itemView.findViewById(R.id.text_players);
            pointValueText = itemView.findViewById(R.id.text_point_value);
            gstText = itemView.findViewById(R.id.text_gst);
            createdTimeText = itemView.findViewById(R.id.text_created_time);
            joinButton = itemView.findViewById(R.id.btn_join_game);
        }
    }
}
