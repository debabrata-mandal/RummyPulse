package com.example.rummypulse.ui.playerconsolidation;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SettlementPaymentAdapter
        extends RecyclerView.Adapter<SettlementPaymentAdapter.ViewHolder> {

    private final List<PayerGroup> payerGroups = new ArrayList<>();
    private final Set<String> paidPaymentIds = new HashSet<>();

    public void setPayments(List<SettlementPayment> updatedPayments) {
        Set<String> validIds = new HashSet<>();
        Map<String, PayerGroup> grouped = new LinkedHashMap<>();
        if (updatedPayments != null) {
            for (SettlementPayment payment : updatedPayments) {
                validIds.add(payment.getPaymentId());
                PayerGroup group = grouped.get(payment.getDebtor());
                if (group == null) {
                    group = new PayerGroup(payment.getDebtor());
                    grouped.put(payment.getDebtor(), group);
                }
                group.payments.add(payment);
                group.totalPaise += payment.getAmountPaise();
            }
        }
        paidPaymentIds.retainAll(validIds);
        payerGroups.clear();
        payerGroups.addAll(grouped.values());
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_settlement_payment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PayerGroup group = payerGroups.get(position);
        holder.payer.setText(holder.itemView.getContext().getString(
                R.string.player_consolidation_payer_total,
                group.payer));
        holder.total.setText(ConsolidationAmountFormatter.formatAmount(
                group.totalPaise / 100.0));
        holder.distributions.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
        for (SettlementPayment payment : group.payments) {
            View row = inflater.inflate(
                    R.layout.item_settlement_distribution,
                    holder.distributions,
                    false);
            TextView recipient = row.findViewById(R.id.text_distribution_recipient);
            TextView amount = row.findViewById(R.id.text_distribution_amount);
            CheckBox paid = row.findViewById(R.id.checkbox_distribution_paid);
            String paymentId = payment.getPaymentId();

            recipient.setText(holder.itemView.getContext().getString(
                    R.string.player_consolidation_distribution_recipient,
                    payment.getCreditor()));
            amount.setText(ConsolidationAmountFormatter.formatAmount(payment.getAmount()));
            paid.setOnCheckedChangeListener(null);
            paid.setChecked(paidPaymentIds.contains(paymentId));
            applyPaidStyle(recipient, amount, paid.isChecked());
            paid.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    paidPaymentIds.add(paymentId);
                } else {
                    paidPaymentIds.remove(paymentId);
                }
                applyPaidStyle(recipient, amount, checked);
                bindGroupPaidCheckbox(holder, group);
            });
            holder.distributions.addView(row);
        }

        bindGroupPaidCheckbox(holder, group);
    }

    private void bindGroupPaidCheckbox(ViewHolder holder, PayerGroup group) {
        boolean allPaid = !group.payments.isEmpty();
        for (SettlementPayment payment : group.payments) {
            if (!paidPaymentIds.contains(payment.getPaymentId())) {
                allPaid = false;
                break;
            }
        }
        holder.allPaid.setOnCheckedChangeListener(null);
        holder.allPaid.setChecked(allPaid);
        holder.allPaid.setText(allPaid
                ? R.string.player_consolidation_all_distributions_paid
                : R.string.player_consolidation_mark_all_paid);
        holder.allPaid.setOnCheckedChangeListener((button, checked) -> {
            for (SettlementPayment payment : group.payments) {
                if (checked) {
                    paidPaymentIds.add(payment.getPaymentId());
                } else {
                    paidPaymentIds.remove(payment.getPaymentId());
                }
            }
            notifyItemChanged(holder.getBindingAdapterPosition());
        });
    }

    private static void applyPaidStyle(TextView recipient, TextView amount, boolean paid) {
        recipient.setAlpha(paid ? 0.55f : 1f);
        amount.setAlpha(paid ? 0.55f : 1f);
        int flags = recipient.getPaintFlags();
        recipient.setPaintFlags(paid
                ? flags | Paint.STRIKE_THRU_TEXT_FLAG
                : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
    }

    @Override
    public int getItemCount() {
        return payerGroups.size();
    }

    private static final class PayerGroup {
        private final String payer;
        private final List<SettlementPayment> payments = new ArrayList<>();
        private long totalPaise;

        private PayerGroup(String payer) {
            this.payer = payer;
        }
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView payer;
        private final TextView total;
        private final LinearLayout distributions;
        private final CheckBox allPaid;

        private ViewHolder(@NonNull View itemView) {
            super(itemView);
            payer = itemView.findViewById(R.id.text_settlement_payer);
            total = itemView.findViewById(R.id.text_settlement_group_total);
            distributions = itemView.findViewById(R.id.layout_settlement_distributions);
            allPaid = itemView.findViewById(R.id.checkbox_settlement_group_paid);
        }
    }
}
