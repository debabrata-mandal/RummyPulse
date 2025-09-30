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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpandableMonthlyReportAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_MONTH_HEADER = 0;
    private static final int TYPE_POINT_VALUE_CARD = 1;

    private List<Object> items = new ArrayList<>();
    private Map<String, Boolean> expandedStates = new HashMap<>();

    // Item wrapper classes
    public static class MonthHeaderItem {
        private MonthlyPointValueReport monthlyReport;

        public MonthHeaderItem(MonthlyPointValueReport monthlyReport) {
            this.monthlyReport = monthlyReport;
        }

        public MonthlyPointValueReport getMonthlyReport() { return monthlyReport; }
    }

    public static class PointValueCardItem {
        private String monthYear;
        private PointValueReport pointValueReport;

        public PointValueCardItem(String monthYear, PointValueReport pointValueReport) {
            this.monthYear = monthYear;
            this.pointValueReport = pointValueReport;
        }

        public String getMonthYear() { return monthYear; }
        public PointValueReport getPointValueReport() { return pointValueReport; }
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof MonthHeaderItem) {
            return TYPE_MONTH_HEADER;
        } else {
            return TYPE_POINT_VALUE_CARD;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_MONTH_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_month_header_expandable, parent, false);
            return new MonthHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_point_value_card, parent, false);
            return new PointValueCardViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MonthHeaderViewHolder) {
            MonthHeaderItem headerItem = (MonthHeaderItem) items.get(position);
            ((MonthHeaderViewHolder) holder).bind(headerItem.getMonthlyReport(), this);
        } else if (holder instanceof PointValueCardViewHolder) {
            PointValueCardItem cardItem = (PointValueCardItem) items.get(position);
            ((PointValueCardViewHolder) holder).bind(cardItem);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setMonthlyPointValueReports(List<MonthlyPointValueReport> reports) {
        items.clear();
        expandedStates.clear();

        if (reports != null) {
            for (MonthlyPointValueReport monthlyReport : reports) {
                String monthYear = monthlyReport.getMonthYear();
                
                // Add month header
                items.add(new MonthHeaderItem(monthlyReport));
                
                // Set default expanded state (first month expanded, others collapsed)
                boolean isExpanded = expandedStates.getOrDefault(monthYear, reports.indexOf(monthlyReport) == 0);
                expandedStates.put(monthYear, isExpanded);
                
                // Add point value cards if expanded
                if (isExpanded && monthlyReport.getPointValueReports() != null) {
                    for (PointValueReport pointReport : monthlyReport.getPointValueReports()) {
                        items.add(new PointValueCardItem(monthYear, pointReport));
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    public void toggleMonth(String monthYear) {
        boolean currentState = expandedStates.getOrDefault(monthYear, false);
        expandedStates.put(monthYear, !currentState);
        
        // Rebuild the items list
        List<MonthlyPointValueReport> reports = getCurrentReports();
        if (reports != null) {
            setMonthlyPointValueReports(reports);
        }
    }

    private List<MonthlyPointValueReport> currentReports = new ArrayList<>();

    private List<MonthlyPointValueReport> getCurrentReports() {
        return currentReports;
    }

    public void updateReports(List<MonthlyPointValueReport> reports) {
        this.currentReports = reports != null ? new ArrayList<>(reports) : new ArrayList<>();
        setMonthlyPointValueReports(reports);
    }

    // ViewHolder for month headers
    static class MonthHeaderViewHolder extends RecyclerView.ViewHolder {
        private TextView expandCollapseIcon;
        private TextView monthYearText;
        private TextView monthlyGamesText;
        private TextView monthlyGstText;
        private TextView pointValuesCountText;

        public MonthHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            expandCollapseIcon = itemView.findViewById(R.id.icon_expand_collapse);
            monthYearText = itemView.findViewById(R.id.text_month_year);
            monthlyGamesText = itemView.findViewById(R.id.text_monthly_games);
            monthlyGstText = itemView.findViewById(R.id.text_monthly_gst);
            pointValuesCountText = itemView.findViewById(R.id.text_point_values_count);
        }

        public void bind(MonthlyPointValueReport report, ExpandableMonthlyReportAdapter adapter) {
            String monthYear = report.getMonthYear();
            boolean isExpanded = adapter.expandedStates.getOrDefault(monthYear, false);

            monthYearText.setText(monthYear);
            monthlyGamesText.setText(report.getMonthlyGamesText());
            monthlyGstText.setText(report.getFormattedMonthlyGst());
            
            // Point values count
            int pointValuesCount = report.getPointValueReports() != null ? report.getPointValueReports().size() : 0;
            String pointValuesText = pointValuesCount + " Point Value" + (pointValuesCount != 1 ? "s" : "");
            pointValuesCountText.setText(pointValuesText);

            // Set expand/collapse icon
            expandCollapseIcon.setText(isExpanded ? "▼" : "▶");

            // Set click listener
            itemView.setOnClickListener(v -> adapter.toggleMonth(monthYear));
        }
    }

    // ViewHolder for point value cards
    static class PointValueCardViewHolder extends RecyclerView.ViewHolder {
        private TextView monthYearText;
        private TextView pointValueText;
        private TextView totalGamesText;
        private TextView totalGstText;
        private TextView avgGstText;
        private TextView totalPlayersText;
        private TextView avgPlayersText;

        public PointValueCardViewHolder(@NonNull View itemView) {
            super(itemView);
            monthYearText = itemView.findViewById(R.id.text_month_year);
            pointValueText = itemView.findViewById(R.id.text_point_value);
            totalGamesText = itemView.findViewById(R.id.text_total_games);
            totalGstText = itemView.findViewById(R.id.text_total_gst);
            avgGstText = itemView.findViewById(R.id.text_avg_gst);
            totalPlayersText = itemView.findViewById(R.id.text_total_players);
            avgPlayersText = itemView.findViewById(R.id.text_avg_players);
        }

        public void bind(PointValueCardItem item) {
            String monthYear = item.getMonthYear();
            PointValueReport report = item.getPointValueReport();

            // Hide month year in card since it's shown in header
            monthYearText.setVisibility(View.GONE);
            
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
