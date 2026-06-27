package com.example.rummypulse.ui.playerconsolidation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.example.rummypulse.R;
import com.example.rummypulse.ui.home.GameItem;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
public class ConsolidationGameAdapter extends RecyclerView.Adapter<ConsolidationGameAdapter.GameViewHolder> {

    private List<GameItem> gameItems = new ArrayList<>();
    private Set<String> selectedIds = new HashSet<>();
    private OnGameSelectionListener listener;

    public interface OnGameSelectionListener {
        void onToggle(GameItem game);
    }

    public void setOnGameSelectionListener(OnGameSelectionListener listener) {
        this.listener = listener;
    }

    public void setGameItems(List<GameItem> gameItems) {
        this.gameItems = gameItems != null ? gameItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setSelectedIds(Set<String> selectedIds) {
        this.selectedIds = selectedIds != null ? selectedIds : new HashSet<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_consolidation_game, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        GameItem item = gameItems.get(position);
        boolean isSelected = selectedIds.contains(item.getGameId());

        holder.titleText.setText(item.getDashboardPrimaryLabel());
        ConsolidationGameStatusUi.bindStatus(holder.statusText, item.getGameStatus());

        holder.subtitleText.setText(ConsolidationGameStatusUi.formatGameSubtitle(holder.itemView.getContext(), item));

        if (item.getCreatorName() != null && !item.getCreatorName().trim().isEmpty()) {
            holder.creatorNameText.setText(item.getCreatorName());
            if (item.getCreatorPhotoUrl() != null && !item.getCreatorPhotoUrl().isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(item.getCreatorPhotoUrl())
                        .apply(new RequestOptions()
                                .centerCrop()
                                .placeholder(R.drawable.ic_person)
                                .error(R.drawable.ic_person)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .timeout(10000))
                        .into(holder.creatorProfileImage);
            } else {
                bindDefaultCreatorAvatar(holder.creatorProfileImage);
            }
        } else {
            holder.creatorNameText.setText("Unknown");
            bindDefaultCreatorAvatar(holder.creatorProfileImage);
        }

        holder.createdTimeText.setText(holder.itemView.getContext().getString(
                R.string.player_consolidation_started,
                formatCreationTime(item.getCreationDateTime())));

        holder.checkbox.setChecked(isSelected);
        int strokeColor = ContextCompat.getColor(holder.itemView.getContext(),
                isSelected ? R.color.accent_blue : R.color.divider_color);
        holder.card.setStrokeColor(strokeColor);
        holder.card.setStrokeWidth(isSelected
                ? holder.itemView.getResources().getDimensionPixelSize(R.dimen.consolidation_card_stroke_selected)
                : holder.itemView.getResources().getDimensionPixelSize(R.dimen.consolidation_card_stroke_default));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onToggle(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return gameItems.size();
    }

    private static void bindDefaultCreatorAvatar(ImageView imageView) {
        imageView.setImageResource(R.drawable.ic_person);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setPadding(6, 6, 6, 6);
    }

    private static String formatCreationTime(String dateTime) {
        if (dateTime == null || dateTime.isEmpty()) {
            return "recently";
        }
        try {
            return formatCreationTimeFromMillis(parseCreationMillis(dateTime));
        } catch (Exception e) {
            return "recently";
        }
    }

    private static long parseCreationMillis(String dateTime) throws Exception {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date creationDate = sdf.parse(dateTime);
            if (creationDate != null) {
                return creationDate.getTime();
            }
        } catch (Exception ignored) {
            // fall through to millis parse
        }
        return Long.parseLong(dateTime);
    }

    private static String formatCreationTimeFromMillis(long creationTime) {
        long currentTime = System.currentTimeMillis();
        long diffInMillis = currentTime - creationTime;
        long diffInMinutes = diffInMillis / (1000 * 60);
        long diffInHours = diffInMillis / (1000 * 60 * 60);
        long diffInDays = diffInMillis / (1000 * 60 * 60 * 24);
        Date creationDate = new Date(creationTime);
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a");
        String actualTime = timeFormat.format(creationDate);

        if (diffInMinutes < 1) {
            return "just now at " + actualTime;
        } else if (diffInMinutes < 60) {
            return diffInMinutes + " minutes ago at " + actualTime;
        } else if (diffInHours < 24) {
            return diffInHours + " hours ago at " + actualTime;
        } else if (diffInDays < 7) {
            return new SimpleDateFormat("MMM dd 'at' hh:mm a").format(creationDate);
        }
        return new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a").format(creationDate);
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final MaterialCheckBox checkbox;
        final TextView titleText;
        final TextView statusText;
        final TextView subtitleText;
        final TextView creatorNameText;
        final TextView createdTimeText;
        final ImageView creatorProfileImage;

        GameViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_game);
            checkbox = itemView.findViewById(R.id.checkbox_selected);
            titleText = itemView.findViewById(R.id.text_game_title);
            statusText = itemView.findViewById(R.id.text_game_status);
            subtitleText = itemView.findViewById(R.id.text_game_subtitle);
            creatorNameText = itemView.findViewById(R.id.text_creator_name);
            createdTimeText = itemView.findViewById(R.id.text_created_time);
            creatorProfileImage = itemView.findViewById(R.id.image_creator_profile);
        }
    }
}
