package com.example.rummypulse.ui.reports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import android.annotation.SuppressLint;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.example.rummypulse.data.MonthlyGamePointFactorReport;
import com.example.rummypulse.data.GamePointFactorReport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExpandableMonthlyReportAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_MONTH_HEADER = 0;
    private static final int TYPE_POINT_VALUE_CARD = 1;

    private List<Object> items = new ArrayList<>();
    private Map<String, Boolean> expandedStates = new HashMap<>();

    // Item wrapper classes
    public static class MonthHeaderItem {
        private MonthlyGamePointFactorReport monthlyReport;

        public MonthHeaderItem(MonthlyGamePointFactorReport monthlyReport) {
            this.monthlyReport = monthlyReport;
        }

        public MonthlyGamePointFactorReport getMonthlyReport() { return monthlyReport; }
    }

    public static class GamePointFactorCardItem {
        private String monthYear;
        private GamePointFactorReport gamePointFactorReport;

        public GamePointFactorCardItem(String monthYear, GamePointFactorReport gamePointFactorReport) {
            this.monthYear = monthYear;
            this.gamePointFactorReport = gamePointFactorReport;
        }

        public String getMonthYear() { return monthYear; }
        public GamePointFactorReport getGamePointFactorReport() { return gamePointFactorReport; }
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
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_game_point_factor_card, parent, false);
            return new GamePointFactorCardViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MonthHeaderViewHolder) {
            MonthHeaderItem headerItem = (MonthHeaderItem) items.get(position);
            ((MonthHeaderViewHolder) holder).bind(headerItem.getMonthlyReport(), this);
        } else if (holder instanceof GamePointFactorCardViewHolder) {
            GamePointFactorCardItem cardItem = (GamePointFactorCardItem) items.get(position);
            ((GamePointFactorCardViewHolder) holder).bind(cardItem);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setMonthlyGamePointFactorReports(List<MonthlyGamePointFactorReport> reports) {
        items.clear();
        // Don't clear expandedStates to preserve user's expand/collapse preferences
        
        if (reports != null) {
            for (MonthlyGamePointFactorReport monthlyReport : reports) {
                String monthYear = monthlyReport.getMonthYear();
                
                // Add month header
                items.add(new MonthHeaderItem(monthlyReport));
                
                // Set default expanded state only if not already set (first month expanded, others collapsed)
                if (!expandedStates.containsKey(monthYear)) {
                    boolean isExpanded = reports.indexOf(monthlyReport) == 0;
                    expandedStates.put(monthYear, isExpanded);
                }
                
                boolean isExpanded = expandedStates.get(monthYear);
                
                // Add point value cards if expanded
                if (isExpanded && monthlyReport.getGamePointFactorReports() != null) {
                    for (GamePointFactorReport pointReport : monthlyReport.getGamePointFactorReports()) {
                        items.add(new GamePointFactorCardItem(monthYear, pointReport));
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    public void toggleMonth(String monthYear) {
        boolean currentState = expandedStates.getOrDefault(monthYear, false);
        boolean newState = !currentState;
        expandedStates.put(monthYear, newState);
        
        // Find the header position for this month
        int headerPosition = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof MonthHeaderItem) {
                MonthHeaderItem headerItem = (MonthHeaderItem) items.get(i);
                if (monthYear.equals(headerItem.getMonthlyReport().getMonthYear())) {
                    headerPosition = i;
                    break;
                }
            }
        }
        
        if (headerPosition == -1) return;
        
        // Update the header icon
        notifyItemChanged(headerPosition);
        
        // Find the corresponding monthly report
        MonthlyGamePointFactorReport monthlyReport = null;
        for (MonthlyGamePointFactorReport report : getCurrentReports()) {
            if (monthYear.equals(report.getMonthYear())) {
                monthlyReport = report;
                break;
            }
        }
        
        if (monthlyReport == null || monthlyReport.getGamePointFactorReports() == null) return;
        
        if (newState) {
            // Expanding - insert point value cards
            List<GamePointFactorCardItem> cardsToAdd = new ArrayList<>();
            for (GamePointFactorReport pointReport : monthlyReport.getGamePointFactorReports()) {
                cardsToAdd.add(new GamePointFactorCardItem(monthYear, pointReport));
            }
            
            // Insert cards after the header
            int insertPosition = headerPosition + 1;
            for (int i = 0; i < cardsToAdd.size(); i++) {
                items.add(insertPosition + i, cardsToAdd.get(i));
            }
            notifyItemRangeInserted(insertPosition, cardsToAdd.size());
            
        } else {
            // Collapsing - remove point value cards
            int removeStart = headerPosition + 1;
            int removeCount = 0;
            
            // Count how many point value cards to remove
            for (int i = removeStart; i < items.size(); i++) {
                if (items.get(i) instanceof GamePointFactorCardItem) {
                    GamePointFactorCardItem cardItem = (GamePointFactorCardItem) items.get(i);
                    if (monthYear.equals(cardItem.getMonthYear())) {
                        removeCount++;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            
            // Remove the cards
            for (int i = 0; i < removeCount; i++) {
                items.remove(removeStart);
            }
            notifyItemRangeRemoved(removeStart, removeCount);
        }
    }

    private List<MonthlyGamePointFactorReport> currentReports = new ArrayList<>();

    private List<MonthlyGamePointFactorReport> getCurrentReports() {
        return currentReports;
    }

    public void updateReports(List<MonthlyGamePointFactorReport> reports) {
        this.currentReports = reports != null ? new ArrayList<>(reports) : new ArrayList<>();
        setMonthlyGamePointFactorReports(reports);
    }

    // ViewHolder for month headers
    static class MonthHeaderViewHolder extends RecyclerView.ViewHolder {
        private TextView expandCollapseIcon;
        private TextView monthYearText;
        private TextView monthlyGamesText;
        private TextView monthlyBoardAdjustmentText;
        private TextView gamePointFactorsCountText;

        public MonthHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            expandCollapseIcon = itemView.findViewById(R.id.icon_expand_collapse);
            monthYearText = itemView.findViewById(R.id.text_month_year);
            monthlyGamesText = itemView.findViewById(R.id.text_monthly_games);
            monthlyBoardAdjustmentText = itemView.findViewById(R.id.text_monthly_board_adjustment);
            gamePointFactorsCountText = itemView.findViewById(R.id.text_game_point_factors_count);
        }

        public void bind(MonthlyGamePointFactorReport report, ExpandableMonthlyReportAdapter adapter) {
            String monthYear = report.getMonthYear();
            boolean isExpanded = adapter.expandedStates.getOrDefault(monthYear, false);

            monthYearText.setText(monthYear);
            monthlyGamesText.setText(itemView.getContext().getResources().getQuantityString(
                    R.plurals.report_games_count,
                    report.getTotalGamesForMonth(),
                    report.getTotalGamesForMonth()));
            monthlyBoardAdjustmentText.setText(report.getFormattedMonthlyBoardAdjustment());
            
            // Point values count
            int gamePointFactorsCount = report.getGamePointFactorReports() != null ? report.getGamePointFactorReports().size() : 0;
            gamePointFactorsCountText.setText(itemView.getContext().getResources().getQuantityString(
                    R.plurals.report_game_point_factors_count, gamePointFactorsCount, gamePointFactorsCount));

            // Set expand/collapse icon
            expandCollapseIcon.setText(isExpanded ? "▼" : "▶");

            // Set click listener
            itemView.setOnClickListener(v -> adapter.toggleMonth(monthYear));
        }
    }

    // ViewHolder for point value cards
    static class GamePointFactorCardViewHolder extends RecyclerView.ViewHolder {
        private TextView monthYearText;
        private TextView gamePointFactorText;
        private TextView totalGamesText;
        private TextView totalBoardAdjustmentText;
        private TextView avgBoardAdjustmentText;
        private TextView totalPlayersText;
        private TextView avgPlayersText;

        public GamePointFactorCardViewHolder(@NonNull View itemView) {
            super(itemView);
            monthYearText = itemView.findViewById(R.id.text_month_year);
            gamePointFactorText = itemView.findViewById(R.id.text_game_point_factor);
            totalGamesText = itemView.findViewById(R.id.text_total_games);
            totalBoardAdjustmentText = itemView.findViewById(R.id.text_total_board_adjustment);
            avgBoardAdjustmentText = itemView.findViewById(R.id.text_avg_board_adjustment);
            totalPlayersText = itemView.findViewById(R.id.text_total_players);
            avgPlayersText = itemView.findViewById(R.id.text_avg_players);
        }

        public void bind(GamePointFactorCardItem item) {
            String monthYear = item.getMonthYear();
            GamePointFactorReport report = item.getGamePointFactorReport();

            // Hide month year in card since it's shown in header
            monthYearText.setVisibility(View.GONE);
            
            gamePointFactorText.setText(report.getFormattedGamePointFactor());
            totalGamesText.setText(itemView.getContext().getResources().getQuantityString(
                    R.plurals.report_games_count, report.getTotalGames(), report.getTotalGames()));
            
            // Format Board Points
            totalBoardAdjustmentText.setText(report.getFormattedBoardPoints());
            avgBoardAdjustmentText.setText(itemView.getContext().getString(
                    R.string.format_game_points,
                    String.format(Locale.getDefault(), "%.1f", report.getAverageBoardPointsPerGame())));
            
            // Format player counts
            totalPlayersText.setText(String.valueOf(report.getTotalPlayers()));
            avgPlayersText.setText(String.format(Locale.getDefault(), "%.1f", report.getAveragePlayersPerGame()));
        }
    }
}
