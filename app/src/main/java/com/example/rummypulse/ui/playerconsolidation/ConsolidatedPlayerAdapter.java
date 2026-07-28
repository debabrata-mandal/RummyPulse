package com.example.rummypulse.ui.playerconsolidation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConsolidatedPlayerAdapter extends RecyclerView.Adapter<ConsolidatedPlayerAdapter.ViewHolder> {

    private List<ConsolidatedPlayerGroup> groups = new ArrayList<>();
    private Set<String> selectedEntryIds = new HashSet<>();
    private OnGroupToggleListener listener;

    public interface OnGroupToggleListener {
        void onToggle(ConsolidatedPlayerGroup group);
    }

    public void setOnGroupToggleListener(OnGroupToggleListener listener) {
        this.listener = listener;
    }

    public void setGroups(List<ConsolidatedPlayerGroup> groups) {
        this.groups = groups != null ? groups : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setSelectedEntryIds(Set<String> selectedEntryIds) {
        this.selectedEntryIds = selectedEntryIds != null ? selectedEntryIds : new HashSet<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_consolidated_player, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConsolidatedPlayerGroup group = groups.get(position);
        holder.displayNameText.setText(group.getDisplayName());
        String displayName = group.getDisplayName();
        holder.avatarInitialText.setText(
                displayName == null || displayName.trim().isEmpty()
                        ? "?"
                        : displayName.trim().substring(0, 1).toUpperCase());
        Set<String> gameIds = new LinkedHashSet<>();
        Set<String> gameNames = new LinkedHashSet<>();
        for (GamePlayerEntry member : group.getMembers()) {
            gameIds.add(member.getGameId());
            if (member.getGameName() != null && !member.getGameName().trim().isEmpty()) {
                gameNames.add(member.getGameName().trim());
            }
        }
        int gameCount = gameIds.size();
        holder.gameCountText.setText(holder.itemView.getContext().getString(
                gameCount == 1
                        ? R.string.player_consolidation_game_count_one
                        : R.string.player_consolidation_game_count,
                gameCount));
        holder.gameNamesText.setText(String.join(" · ", gameNames));
        holder.gameNamesText.setVisibility(gameNames.isEmpty() ? View.GONE : View.VISIBLE);

        bindFinalBalance(holder, group);
        bindSelection(holder, group);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onToggle(group);
            }
        });
    }

    private void bindFinalBalance(ViewHolder holder, ConsolidatedPlayerGroup group) {
        double net = group.getAdjustedNetAmount();
        holder.netAmountText.setText(
                ConsolidationAmountFormatter.formatSignedAmount(net));
        holder.netAmountText.setTextColor(
                ConsolidationAmountFormatter.getSignedAmountColor(holder.itemView.getContext(), net));
    }

    private void bindSelection(ViewHolder holder, ConsolidatedPlayerGroup group) {
        boolean isSelected = isGroupSelected(group);
        int strokeColor = ContextCompat.getColor(holder.itemView.getContext(),
                isSelected ? R.color.accent_blue : R.color.divider_color);
        holder.card.setStrokeColor(strokeColor);
        holder.card.setStrokeWidth(isSelected
                ? holder.itemView.getResources().getDimensionPixelSize(R.dimen.consolidation_card_stroke_selected)
                : holder.itemView.getResources().getDimensionPixelSize(R.dimen.consolidation_card_stroke_default));
    }

    private boolean isGroupSelected(ConsolidatedPlayerGroup group) {
        if (selectedEntryIds.isEmpty() || group.getMembers().isEmpty()) {
            return false;
        }
        for (GamePlayerEntry member : group.getMembers()) {
            if (!selectedEntryIds.contains(member.getEntryId())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView displayNameText;
        final TextView avatarInitialText;
        final TextView gameCountText;
        final TextView gameNamesText;
        final TextView netAmountText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_player);
            displayNameText = itemView.findViewById(R.id.text_display_name);
            avatarInitialText = itemView.findViewById(R.id.text_avatar_initial);
            gameCountText = itemView.findViewById(R.id.text_game_count);
            gameNamesText = itemView.findViewById(R.id.text_game_names);
            netAmountText = itemView.findViewById(R.id.text_net_amount);
        }
    }
}
