package com.example.rummypulse.ui.reports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.example.rummypulse.data.MonthlyPointValueReport;
import com.example.rummypulse.data.PointValueReport;

import java.util.ArrayList;
import java.util.List;

public class MonthlyPointValueAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_MONTH_HEADER = 0;
    private static final int TYPE_POINT_VALUE = 1;

    private List<Object> items = new ArrayList<>(); // Mixed list of MonthlyPointValueReport and PointValueReport

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof MonthlyPointValueReport) {
            return TYPE_MONTH_HEADER;
        } else {
            return TYPE_POINT_VALUE;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_MONTH_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_monthly_header, parent, false);
            return new MonthlyHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_point_value, parent, false);
            return new PointValueViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MonthlyHeaderViewHolder) {
            MonthlyPointValueReport monthlyReport = (MonthlyPointValueReport) items.get(position);
            ((MonthlyHeaderViewHolder) holder).bind(monthlyReport);
        } else if (holder instanceof PointValueViewHolder) {
            PointValueReport pointValueReport = (PointValueReport) items.get(position);
            ((PointValueViewHolder) holder).bind(pointValueReport);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setMonthlyPointValueReports(List<MonthlyPointValueReport> reports) {
        items.clear();
        if (reports != null) {
            for (MonthlyPointValueReport monthlyReport : reports) {
                // Add month header
                items.add(monthlyReport);
                
                // Add all point value reports for this month
                if (monthlyReport.getPointValueReports() != null) {
                    items.addAll(monthlyReport.getPointValueReports());
                }
            }
        }
        notifyDataSetChanged();
    }

    // ViewHolder for monthly headers
    static class MonthlyHeaderViewHolder extends RecyclerView.ViewHolder {
        private TextView monthYearText;
        private TextView monthlyGamesText;
        private TextView monthlyGstText;

        public MonthlyHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            monthYearText = itemView.findViewById(R.id.text_month_year);
            monthlyGamesText = itemView.findViewById(R.id.text_monthly_games);
            monthlyGstText = itemView.findViewById(R.id.text_monthly_gst);
        }

        public void bind(MonthlyPointValueReport report) {
            monthYearText.setText(report.getMonthYear());
            monthlyGamesText.setText(report.getMonthlyGamesText());
            monthlyGstText.setText(report.getFormattedMonthlyGst());
        }
    }

    // ViewHolder for point value items
    static class PointValueViewHolder extends RecyclerView.ViewHolder {
        private TextView pointValueText;
        private TextView gamesCountText;
        private TextView gstAmountText;
        private TextView playersCountText;

        public PointValueViewHolder(@NonNull View itemView) {
            super(itemView);
            pointValueText = itemView.findViewById(R.id.text_point_value);
            gamesCountText = itemView.findViewById(R.id.text_games_count);
            gstAmountText = itemView.findViewById(R.id.text_gst_amount);
            playersCountText = itemView.findViewById(R.id.text_players_count);
        }

        public void bind(PointValueReport report) {
            pointValueText.setText(report.getFormattedPointValue());
            gamesCountText.setText(report.getGamesText());
            gstAmountText.setText(report.getFormattedGstAmount());
            playersCountText.setText(report.getTotalPlayers() + " 👥");
        }
    }
}
