package com.example.rummypulse.ui.playerconsolidation;

import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class BalanceAdjustmentAdapter
        extends RecyclerView.Adapter<BalanceAdjustmentAdapter.ViewHolder> {

    private final List<BalanceAdjustment> adjustments = new ArrayList<>();
    private OnDeleteAdjustmentListener deleteListener;

    public interface OnDeleteAdjustmentListener {
        void onDelete(BalanceAdjustment adjustment);
    }

    public void setOnDeleteAdjustmentListener(OnDeleteAdjustmentListener listener) {
        deleteListener = listener;
    }

    public void setAdjustments(List<BalanceAdjustment> updatedAdjustments) {
        adjustments.clear();
        if (updatedAdjustments != null) {
            adjustments.addAll(updatedAdjustments);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_balance_adjustment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BalanceAdjustment adjustment = adjustments.get(position);
        holder.route.setText(holder.itemView.getContext().getString(
                R.string.player_consolidation_adjustment_route,
                adjustment.getFromName(),
                adjustment.getToName()));
        holder.amount.setText(ConsolidationAmountFormatter.formatAmount(adjustment.getAmount()));
        holder.reason.setText(TextUtils.isEmpty(adjustment.getReason())
                ? holder.itemView.getContext().getString(
                        R.string.player_consolidation_adjustment_no_reason)
                : adjustment.getReason());
        holder.timestamp.setText(DateFormat.getMediumDateFormat(holder.itemView.getContext())
                .format(new Date(adjustment.getCreatedAtMillis())));
        holder.delete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(adjustment);
            }
        });
    }

    @Override
    public int getItemCount() {
        return adjustments.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView route;
        private final TextView reason;
        private final TextView amount;
        private final TextView timestamp;
        private final View delete;

        private ViewHolder(@NonNull View itemView) {
            super(itemView);
            route = itemView.findViewById(R.id.text_adjustment_route);
            reason = itemView.findViewById(R.id.text_adjustment_reason);
            amount = itemView.findViewById(R.id.text_adjustment_amount);
            timestamp = itemView.findViewById(R.id.text_adjustment_timestamp);
            delete = itemView.findViewById(R.id.btn_delete_adjustment);
        }
    }
}
