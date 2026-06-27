package com.example.rummypulse.ui.playerconsolidation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.List;

public class SelectedGamesStatusAdapter extends RecyclerView.Adapter<SelectedGamesStatusAdapter.ViewHolder> {

    private List<GameItem> games = new ArrayList<>();

    public void setGames(List<GameItem> games) {
        this.games = games != null ? games : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_consolidation_selected_game_status, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GameItem item = games.get(position);
        holder.titleText.setText(item.getDashboardPrimaryLabel());
        ConsolidationGameStatusUi.bindStatus(holder.statusText, item.getGameStatus());
        holder.subtitleText.setText(ConsolidationGameStatusUi.formatGameSubtitle(holder.itemView.getContext(), item));
    }

    @Override
    public int getItemCount() {
        return games.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;
        final TextView statusText;
        final TextView subtitleText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.text_game_title);
            statusText = itemView.findViewById(R.id.text_game_status);
            subtitleText = itemView.findViewById(R.id.text_game_subtitle);
        }
    }
}
