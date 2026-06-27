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
        double net = group.getAdjustedNetAmount();
        holder.netAmountText.setText(ConsolidationAmountFormatter.formatSignedAmount(net));
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
        final TextView aliasesText;
        final TextView netAmountText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_player);
            displayNameText = itemView.findViewById(R.id.text_display_name);
            aliasesText = itemView.findViewById(R.id.text_aliases);
            netAmountText = itemView.findViewById(R.id.text_net_amount);
        }
    }
}
