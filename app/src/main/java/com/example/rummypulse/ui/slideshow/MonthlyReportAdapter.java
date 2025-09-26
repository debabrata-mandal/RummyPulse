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

public class MonthlyReportAdapter extends RecyclerView.Adapter<MonthlyReportAdapter.MonthlyReportViewHolder> {

    private List<PointValueReport> pointValueReports = new ArrayList<>();

    @NonNull
    @Override
    public MonthlyReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_monthly_report, parent, false);
        return new MonthlyReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MonthlyReportViewHolder holder, int position) {
        PointValueReport report = pointValueReports.get(position);
        holder.bind(report);
    }

    @Override
    public int getItemCount() {
        return pointValueReports.size();
    }

    public void setPointValueReports(List<PointValueReport> reports) {
        this.pointValueReports = reports != null ? reports : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class MonthlyReportViewHolder extends RecyclerView.ViewHolder {
        private TextView pointValueText;
        private TextView totalGamesText;
        private TextView totalGstText;
        private TextView avgGstText;
        private TextView totalPlayersText;
        private TextView avgPlayersText;

        public MonthlyReportViewHolder(@NonNull View itemView) {
            super(itemView);
            pointValueText = itemView.findViewById(R.id.text_point_value);
            totalGamesText = itemView.findViewById(R.id.text_total_games);
            totalGstText = itemView.findViewById(R.id.text_total_gst);
            avgGstText = itemView.findViewById(R.id.text_avg_gst);
            totalPlayersText = itemView.findViewById(R.id.text_total_players);
            avgPlayersText = itemView.findViewById(R.id.text_avg_players);
        }

        public void bind(PointValueReport report) {
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
