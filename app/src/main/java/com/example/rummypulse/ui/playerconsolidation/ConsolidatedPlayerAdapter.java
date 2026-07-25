package com.example.rummypulse.ui.playerconsolidation;

import android.text.TextUtils;
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
        int gameCount = group.getMembers().size();
        holder.gameCountText.setText(holder.itemView.getContext().getString(
                gameCount == 1
                        ? R.string.player_consolidation_game_count_one
                        : R.string.player_consolidation_game_count,
                gameCount));

        bindSubtitle(holder, group);
        bindAmounts(holder, group);
        bindSelection(holder, group);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onToggle(group);
            }
        });
    }

    private void bindSubtitle(ViewHolder holder, ConsolidatedPlayerGroup group) {
        List<String> parts = new ArrayList<>();
        if (group.getMembers().size() <= 1) {
            parts.add(group.getMembers().get(0).getGameName());
        } else {
            for (GamePlayerEntry member : group.getMembers()) {
                parts.add(holder.itemView.getContext().getString(
                        R.string.player_consolidation_alias_format,
                        member.getPlayerName(),
                        member.getGameName()));
            }
        }
        double adjustment = group.getNetAdjustment();
        if (adjustment != 0) {
            parts.add(holder.itemView.getContext().getString(
                    R.string.player_consolidation_transfer_adj,
                    ConsolidationAmountFormatter.formatSignedAmount(adjustment)));
        }
        holder.aliasesText.setText(TextUtils.join(" · ", parts));
    }

    private void bindAmounts(ViewHolder holder, ConsolidatedPlayerGroup group) {
        bindSignedAmount(holder.grossAmountText, group.getTotalGrossAmount());
        holder.contributionAmountText.setText(
                ConsolidationAmountFormatter.formatAmount(group.getTotalContribution()));
        bindSignedAmount(holder.baseNetAmountText, group.getTotalNetAmount());
        bindSignedAmount(holder.adjustmentAmountText, group.getNetAdjustment());

        double net = group.getAdjustedNetAmount();
        holder.netAmountText.setText(
                ConsolidationAmountFormatter.formatSignedAmount(net));
        holder.netAmountText.setTextColor(
                ConsolidationAmountFormatter.getSignedAmountColor(holder.itemView.getContext(), net));
    }

    private void bindSignedAmount(TextView view, double amount) {
        view.setText(ConsolidationAmountFormatter.formatSignedAmount(amount));
        view.setTextColor(ConsolidationAmountFormatter.getSignedAmountColor(
                view.getContext(), amount));
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
        final TextView aliasesText;
        final TextView netAmountText;
        final TextView grossAmountText;
        final TextView contributionAmountText;
        final TextView baseNetAmountText;
        final TextView adjustmentAmountText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_player);
            displayNameText = itemView.findViewById(R.id.text_display_name);
            avatarInitialText = itemView.findViewById(R.id.text_avatar_initial);
            gameCountText = itemView.findViewById(R.id.text_game_count);
            aliasesText = itemView.findViewById(R.id.text_aliases);
            netAmountText = itemView.findViewById(R.id.text_net_amount);
            grossAmountText = itemView.findViewById(R.id.text_gross_amount);
            contributionAmountText = itemView.findViewById(R.id.text_contribution_amount);
            baseNetAmountText = itemView.findViewById(R.id.text_base_net_amount);
            adjustmentAmountText = itemView.findViewById(R.id.text_adjustment_amount);
        }
    }
}
