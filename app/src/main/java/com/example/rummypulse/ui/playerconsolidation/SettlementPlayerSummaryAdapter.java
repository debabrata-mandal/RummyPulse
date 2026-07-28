package com.example.rummypulse.ui.playerconsolidation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SettlementPlayerSummaryAdapter
        extends RecyclerView.Adapter<SettlementPlayerSummaryAdapter.ViewHolder> {

    private final List<ConsolidatedPlayerGroup> groups = new ArrayList<>();
    private Runnable editMappingsListener;

    public void setGroups(List<ConsolidatedPlayerGroup> updatedGroups) {
        groups.clear();
        if (updatedGroups != null) {
            groups.addAll(updatedGroups);
            groups.sort(Comparator
                    .comparingDouble(ConsolidatedPlayerGroup::getAdjustedNetAmount)
                    .thenComparing(
                            ConsolidatedPlayerGroup::getDisplayName,
                            String.CASE_INSENSITIVE_ORDER));
        }
        notifyDataSetChanged();
    }

    public void setEditMappingsListener(Runnable listener) {
        editMappingsListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_settlement_player_summary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConsolidatedPlayerGroup group = groups.get(position);
        String name = group.getDisplayName();
        int gameCount = group.getMembers().size();
        holder.avatar.setText(name == null || name.trim().isEmpty()
                ? "?"
                : name.trim().substring(0, 1).toUpperCase());
        holder.name.setText(name);
        holder.games.setText(String.valueOf(gameCount));
        holder.gamesSubtitle.setText(holder.itemView.getContext().getString(
                gameCount == 1
                        ? R.string.player_consolidation_game_count_one
                        : R.string.player_consolidation_game_count,
                gameCount));
        bindSigned(holder.finalBalance, group.getAdjustedNetAmount());
        holder.itemView.setOnClickListener(v -> {
            if (editMappingsListener != null) {
                editMappingsListener.run();
            }
        });
    }

    private static void bindSigned(TextView view, double amount) {
        view.setText(ConsolidationAmountFormatter.formatSignedAmount(amount));
        view.setTextColor(ConsolidationAmountFormatter.getSignedAmountColor(
                view.getContext(), amount));
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView avatar;
        private final TextView name;
        private final TextView games;
        private final TextView gamesSubtitle;
        private final TextView finalBalance;

        private ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.text_summary_avatar);
            name = itemView.findViewById(R.id.text_summary_name);
            games = itemView.findViewById(R.id.text_summary_games);
            gamesSubtitle = itemView.findViewById(R.id.text_summary_games_subtitle);
            finalBalance = itemView.findViewById(R.id.text_summary_final);
        }
    }
}
