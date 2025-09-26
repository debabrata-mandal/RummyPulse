package com.example.rummypulse.ui.slideshow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.example.rummypulse.data.PointValueReport;

import java.util.ArrayList;
import java.util.List;

public class MonthlyPointValueCardAdapter extends RecyclerView.Adapter<MonthlyPointValueCardAdapter.CardViewHolder> {

    private List<PointValueReportWithMonth> items = new ArrayList<>();

    // Helper class to store point value report with month info
    public static class PointValueReportWithMonth {
        private String monthYear;
        private PointValueReport pointValueReport;

        public PointValueReportWithMonth(String monthYear, PointValueReport pointValueReport) {
            this.monthYear = monthYear;
            this.pointValueReport = pointValueReport;
        }

        public String getMonthYear() { return monthYear; }
        public PointValueReport getPointValueReport() { return pointValueReport; }
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_point_value_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        PointValueReportWithMonth item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setMonthlyPointValueReports(List<com.example.rummypulse.data.MonthlyPointValueReport> reports) {
        items.clear();
        if (reports != null) {
            for (com.example.rummypulse.data.MonthlyPointValueReport monthlyReport : reports) {
                String monthYear = monthlyReport.getMonthYear();
                if (monthlyReport.getPointValueReports() != null) {
                    for (PointValueReport pointReport : monthlyReport.getPointValueReports()) {
                        items.add(new PointValueReportWithMonth(monthYear, pointReport));
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        private TextView monthYearText;
        private TextView pointValueText;
        private TextView totalGamesText;
        private TextView totalGstText;
        private TextView avgGstText;
        private TextView totalPlayersText;
        private TextView avgPlayersText;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            monthYearText = itemView.findViewById(R.id.text_month_year);
            pointValueText = itemView.findViewById(R.id.text_point_value);
            totalGamesText = itemView.findViewById(R.id.text_total_games);
            totalGstText = itemView.findViewById(R.id.text_total_gst);
            avgGstText = itemView.findViewById(R.id.text_avg_gst);
            totalPlayersText = itemView.findViewById(R.id.text_total_players);
            avgPlayersText = itemView.findViewById(R.id.text_avg_players);
        }

        public void bind(PointValueReportWithMonth item) {
            String monthYear = item.getMonthYear();
            PointValueReport report = item.getPointValueReport();

            monthYearText.setText(monthYear);
            pointValueText.setText(report.getFormattedPointValue());
            totalGamesText.setText(report.getGamesText());
            
            // Format GST amounts
            totalGstText.setText(report.getFormattedGstAmount());
            avgGstText.setText("₹" + String.format("%.1f", report.getAverageGstPerGame()));
            
            // Format player counts
            totalPlayersText.setText(String.valueOf(report.getTotalPlayers()));
            avgPlayersText.setText(String.format("%.1f", report.getAveragePlayersPerGame()));
        }
    }
}
