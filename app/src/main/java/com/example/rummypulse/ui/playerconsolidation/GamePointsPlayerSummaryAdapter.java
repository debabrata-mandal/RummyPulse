package com.example.rummypulse.ui.playerconsolidation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.annotation.SuppressLint;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GamePointsPlayerSummaryAdapter
        extends RecyclerView.Adapter<GamePointsPlayerSummaryAdapter.ViewHolder> {

    private final List<ConsolidatedPlayerGroup> groups = new ArrayList<>();
    private Map<String, String> photoUrlByUserId = new HashMap<>();
    private Runnable editMappingsListener;

    @SuppressLint("NotifyDataSetChanged")
    public void setGroups(List<ConsolidatedPlayerGroup> updatedGroups) {
        groups.clear();
        if (updatedGroups != null) {
            groups.addAll(updatedGroups);
            groups.sort(Comparator
                    .comparingDouble(ConsolidatedPlayerGroup::getAdjustedFinalGamePoints)
                    .thenComparing(
                            ConsolidatedPlayerGroup::getDisplayName,
                            String.CASE_INSENSITIVE_ORDER));
        }
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setPhotoUrlByUserId(@Nullable Map<String, String> photoUrlsByUserId) {
        photoUrlByUserId = photoUrlsByUserId != null ? photoUrlsByUserId : new HashMap<>();
        notifyDataSetChanged();
    }

    public void setEditMappingsListener(Runnable listener) {
        editMappingsListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_game_points_player_summary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConsolidatedPlayerGroup group = groups.get(position);
        String name = group.getDisplayName();
        int gameCount = group.getMembers().size();
        ConsolidationPlayerAvatarBinder.bind(
                holder.itemView,
                holder.avatarImage,
                holder.avatarInitial,
                group,
                photoUrlByUserId,
                name);
        holder.name.setText(name);
        holder.games.setText(String.valueOf(gameCount));
        holder.gamesSubtitle.setText(holder.itemView.getContext().getResources().getQuantityString(
                R.plurals.player_consolidation_game_count_plural, gameCount, gameCount));
        bindSigned(holder.finalBalance, group.getAdjustedFinalGamePoints());
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
        private final ImageView avatarImage;
        private final TextView avatarInitial;
        private final TextView name;
        private final TextView games;
        private final TextView gamesSubtitle;
        private final TextView finalBalance;

        private ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.image_summary_avatar);
            avatarInitial = itemView.findViewById(R.id.text_summary_avatar);
            name = itemView.findViewById(R.id.text_summary_name);
            games = itemView.findViewById(R.id.text_summary_games);
            gamesSubtitle = itemView.findViewById(R.id.text_summary_games_subtitle);
            finalBalance = itemView.findViewById(R.id.text_summary_final);
        }
    }
}
