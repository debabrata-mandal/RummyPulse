package com.example.rummypulse.ui.slideshow;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.rummypulse.data.ApprovedGameData;
import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.data.PointValueReport;
import com.example.rummypulse.data.MonthlyPointValueReport;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SlideshowViewModel extends ViewModel {

    private final MutableLiveData<List<MonthlyPointValueReport>> mMonthlyPointValueReports;
    private final MutableLiveData<Boolean> mIsLoading;
    private final MutableLiveData<String> mError;
    private final GameRepository gameRepository;

    public SlideshowViewModel() {
        mMonthlyPointValueReports = new MutableLiveData<>();
        mIsLoading = new MutableLiveData<>();
        mError = new MutableLiveData<>();
        gameRepository = new GameRepository();
        
        loadReportsData();
    }

    public LiveData<List<MonthlyPointValueReport>> getMonthlyPointValueReports() {
        return mMonthlyPointValueReports;
    }

    public LiveData<Boolean> getIsLoading() {
        return mIsLoading;
    }

    public LiveData<String> getError() {
        return mError;
    }

    private void loadReportsData() {
        mIsLoading.setValue(true);
        
        // Observe approved games data
        gameRepository.getApprovedGamesForReports().observeForever(approvedGames -> {
            if (approvedGames != null) {
                processApprovedGamesIntoMonthlyPointValueReports(approvedGames);
            }
            mIsLoading.setValue(false);
        });
        
        // Observe errors
        gameRepository.getError().observeForever(error -> {
            mError.setValue(error);
            mIsLoading.setValue(false);
        });
        
        // Load approved games for reports
        gameRepository.loadApprovedGamesForReports();
    }

    private void processApprovedGamesIntoMonthlyPointValueReports(List<ApprovedGameData> approvedGames) {
        Map<String, List<ApprovedGameData>> gamesByMonth = new HashMap<>();
        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        
        // First, group games by month-year
        for (ApprovedGameData game : approvedGames) {
            try {
                String monthYear;
                if (game.getApprovedAt() != null) {
                    // Use approvedAt timestamp
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(game.getApprovedAt().toDate());
                    monthYear = monthYearFormat.format(calendar.getTime());
                } else {
                    // Fallback to creation date parsing
                    monthYear = parseMonthYearFromCreationDate(game.getCreationDateTime());
                }
                
                if (!gamesByMonth.containsKey(monthYear)) {
                    gamesByMonth.put(monthYear, new ArrayList<>());
                }
                gamesByMonth.get(monthYear).add(game);
            } catch (Exception e) {
                System.out.println("Error processing game date: " + e.getMessage());
            }
        }
        
        // Create monthly reports with point value breakdowns
        List<MonthlyPointValueReport> monthlyReports = new ArrayList<>();
        for (Map.Entry<String, List<ApprovedGameData>> monthEntry : gamesByMonth.entrySet()) {
            String monthYear = monthEntry.getKey();
            List<ApprovedGameData> monthGames = monthEntry.getValue();
            
            // Group this month's games by point value
            Map<Double, List<ApprovedGameData>> gamesByPointValue = new HashMap<>();
            for (ApprovedGameData game : monthGames) {
                double pointValue = game.getPointValue();
                if (!gamesByPointValue.containsKey(pointValue)) {
                    gamesByPointValue.put(pointValue, new ArrayList<>());
                }
                gamesByPointValue.get(pointValue).add(game);
            }
            
            // Create point value reports for this month
            List<PointValueReport> pointValueReports = new ArrayList<>();
            for (Map.Entry<Double, List<ApprovedGameData>> pointEntry : gamesByPointValue.entrySet()) {
                double pointValue = pointEntry.getKey();
                List<ApprovedGameData> pointValueGames = pointEntry.getValue();
                
                int totalGames = pointValueGames.size();
                double totalGstCollected = 0.0;
                int totalPlayers = 0;
                
                for (ApprovedGameData game : pointValueGames) {
                    totalGstCollected += game.getGstAmountAsDouble();
                    totalPlayers += game.getNumPlayers();
                }
                
                PointValueReport pointReport = new PointValueReport(
                    pointValue, 
                    totalGames, 
                    totalGstCollected,
                    totalPlayers,
                    pointValueGames
                );
                
                pointValueReports.add(pointReport);
            }
            
            // Sort point value reports by point value (ascending order)
            pointValueReports.sort((r1, r2) -> Double.compare(r1.getPointValue(), r2.getPointValue()));
            
            MonthlyPointValueReport monthlyReport = new MonthlyPointValueReport(monthYear, pointValueReports);
            monthlyReports.add(monthlyReport);
        }
        
        // Sort monthly reports by date (most recent first)
        monthlyReports.sort((r1, r2) -> {
            try {
                Calendar cal1 = Calendar.getInstance();
                Calendar cal2 = Calendar.getInstance();
                cal1.setTime(monthYearFormat.parse(r1.getMonthYear()));
                cal2.setTime(monthYearFormat.parse(r2.getMonthYear()));
                return cal2.compareTo(cal1); // Descending order (most recent first)
            } catch (Exception e) {
                return r2.getMonthYear().compareTo(r1.getMonthYear());
            }
        });
        
        mMonthlyPointValueReports.setValue(monthlyReports);
    }

    private String parseMonthYearFromCreationDate(String creationDateTime) {
        try {
            // Parse creation date format: "yyyy-MM-dd HH:mm:ss"
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
            return outputFormat.format(inputFormat.parse(creationDateTime));
        } catch (Exception e) {
            // Fallback to current month if parsing fails
            SimpleDateFormat fallbackFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
            return fallbackFormat.format(Calendar.getInstance().getTime());
        }
    }

    public void refreshReports() {
        loadReportsData();
    }
}