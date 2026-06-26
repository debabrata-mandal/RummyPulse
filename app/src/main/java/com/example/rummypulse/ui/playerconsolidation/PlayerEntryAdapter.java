package com.example.rummypulse.ui.playerconsolidation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlayerEntryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ENTRY = 1;

    private List<PlayerConsolidationEngine.GamePlayerSection> sections = new ArrayList<>();
    private Set<String> selectedEntryIds = new HashSet<>();
    private OnEntryToggleListener listener;

    public interface OnEntryToggleListener {
        void onToggle(GamePlayerEntry entry);
    }

    public void setOnEntryToggleListener(OnEntryToggleListener listener) {
        this.listener = listener;
    }

    public void setSections(List<PlayerConsolidationEngine.GamePlayerSection> sections) {
        this.sections = sections != null ? sections : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setSelectedEntryIds(Set<String> selectedEntryIds) {
        this.selectedEntryIds = selectedEntryIds != null ? selectedEntryIds : new HashSet<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return getListItem(position).viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_consolidation_game_header, parent, false);
            return new HeaderViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_consolidation_player_entry, parent, false);
        return new EntryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem item = getListItem(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).titleText.setText(item.gameName);
            return;
        }
        EntryViewHolder entryHolder = (EntryViewHolder) holder;
        GamePlayerEntry entry = item.entry;
        boolean isSelected = selectedEntryIds.contains(entry.getEntryId());
        entryHolder.playerNameText.setText(entry.getPlayerName());
        entryHolder.gameNameText.setText(entry.getGameName());
        entryHolder.checkbox.setChecked(isSelected);
        entryHolder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onToggle(entry);
            }
        });
    }

    @Override
    public int getItemCount() {
        int count = 0;
        for (PlayerConsolidationEngine.GamePlayerSection section : sections) {
            count += 1 + section.getEntries().size();
        }
        return count;
    }

    private ListItem getListItem(int position) {
        int index = 0;
        for (PlayerConsolidationEngine.GamePlayerSection section : sections) {
            if (index == position) {
                return ListItem.header(section.getGameName());
            }
            index++;
            for (GamePlayerEntry entry : section.getEntries()) {
                if (index == position) {
                    return ListItem.entry(entry);
                }
                index++;
            }
        }
        throw new IndexOutOfBoundsException("Invalid position: " + position);
    }

    private static final class ListItem {
        final int viewType;
        final String gameName;
        final GamePlayerEntry entry;

        private ListItem(int viewType, String gameName, GamePlayerEntry entry) {
            this.viewType = viewType;
            this.gameName = gameName;
            this.entry = entry;
        }

        static ListItem header(String gameName) {
            return new ListItem(VIEW_TYPE_HEADER, gameName, null);
        }

        static ListItem entry(GamePlayerEntry entry) {
            return new ListItem(VIEW_TYPE_ENTRY, null, entry);
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = (TextView) itemView;
        }
    }

    static class EntryViewHolder extends RecyclerView.ViewHolder {
        final MaterialCheckBox checkbox;
        final TextView playerNameText;
        final TextView gameNameText;

        EntryViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.checkbox_entry);
            playerNameText = itemView.findViewById(R.id.text_player_name);
            gameNameText = itemView.findViewById(R.id.text_game_name);
        }
    }
}
