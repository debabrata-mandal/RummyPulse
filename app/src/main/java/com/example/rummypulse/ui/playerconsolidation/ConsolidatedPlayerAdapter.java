package com.example.rummypulse.ui.playerconsolidation;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;

import java.util.ArrayList;
import java.util.List;

public class ConsolidatedPlayerAdapter extends RecyclerView.Adapter<ConsolidatedPlayerAdapter.ViewHolder> {

    private List<ConsolidatedPlayerGroup> groups = new ArrayList<>();

    public void setGroups(List<ConsolidatedPlayerGroup> groups) {
        this.groups = groups != null ? groups : new ArrayList<>();
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

        if (group.getMembers().size() <= 1) {
            GamePlayerEntry member = group.getMembers().get(0);
            holder.aliasesText.setText(member.getGameName());
        } else {
            List<String> aliasParts = new ArrayList<>();
            for (GamePlayerEntry member : group.getMembers()) {
                aliasParts.add(holder.itemView.getContext().getString(
                        R.string.player_consolidation_alias_format,
                        member.getPlayerName(),
                        member.getGameName()));
            }
            holder.aliasesText.setText(TextUtils.join(", ", aliasParts));
        }
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView displayNameText;
        final TextView aliasesText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            displayNameText = itemView.findViewById(R.id.text_display_name);
            aliasesText = itemView.findViewById(R.id.text_aliases);
        }
    }
}
