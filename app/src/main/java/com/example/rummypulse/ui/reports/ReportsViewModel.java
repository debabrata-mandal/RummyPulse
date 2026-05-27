package com.example.rummypulse.ui.reports;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.example.rummypulse.data.GameRepository;
import com.example.rummypulse.data.MonthlyPointValueReport;

import java.text.DateFormatSymbols;
import java.util.List;
import java.util.Locale;

public class ReportsViewModel extends ViewModel {

    private final MutableLiveData<List<MonthlyPointValueReport>> mMonthlyPointValueReports;
    private final MutableLiveData<Boolean> mIsLoading;
    private final MutableLiveData<String> mError;
    private final MutableLiveData<String> mUiMessage;
    private final GameRepository gameRepository;

    private final Observer<List<MonthlyPointValueReport>> summariesObserver;
    private final Observer<String> errorObserver;

    private boolean pendingPullRefreshToast;
    private String pendingSuccessMessage;

    public ReportsViewModel() {
        mMonthlyPointValueReports = new MutableLiveData<>();
        mIsLoading = new MutableLiveData<>();
        mError = new MutableLiveData<>();
        mUiMessage = new MutableLiveData<>();
        gameRepository = new GameRepository();

        summariesObserver = list -> {
            if (list == null) {
                return;
            }
            mMonthlyPointValueReports.setValue(list);
            mIsLoading.setValue(false);
            if (pendingPullRefreshToast) {
                mUiMessage.setValue("Reports refreshed");
                pendingPullRefreshToast = false;
            } else if (pendingSuccessMessage != null) {
                mUiMessage.setValue(pendingSuccessMessage);
                pendingSuccessMessage = null;
            }
        };
        errorObserver = error -> {
            mError.setValue(error);
            mIsLoading.setValue(false);
            if (error != null && !error.isEmpty()) {
                pendingPullRefreshToast = false;
                pendingSuccessMessage = null;
            }
        };

        gameRepository.getReportsSummaries().observeForever(summariesObserver);
        gameRepository.getError().observeForever(errorObserver);

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

    public LiveData<String> getUiMessage() {
        return mUiMessage;
    }

    public void clearUiMessage() {
        mUiMessage.setValue(null);
    }

    private void loadReportsData() {
        mIsLoading.setValue(true);
        gameRepository.loadReportsFromSavedSummaries();
    }

    public void refreshReports() {
        pendingPullRefreshToast = true;
        loadReportsData();
    }

    public void rebuildCurrentMonthReport() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        rebuildMonthReport(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH));
    }

    public void rebuildMonthReport(int year, int monthZeroBased) {
        mIsLoading.setValue(true);
        pendingSuccessMessage = formatMonthLabel(year, monthZeroBased) + " report saved";
        gameRepository.rebuildApprovedGamesReportForMonth(year, monthZeroBased,
                () -> gameRepository.loadReportsFromSavedSummaries(),
                err -> {
                    pendingSuccessMessage = null;
                    mError.setValue(err != null ? err : "Build failed");
                    mIsLoading.setValue(false);
                });
    }

    private String formatMonthLabel(int year, int monthZeroBased) {
        String[] months = DateFormatSymbols.getInstance(Locale.getDefault()).getMonths();
        if (monthZeroBased >= 0 && monthZeroBased < 12) {
            return months[monthZeroBased] + " " + year;
        }
        return String.format(Locale.getDefault(), "%04d-%02d", year, monthZeroBased + 1);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        gameRepository.getReportsSummaries().removeObserver(summariesObserver);
        gameRepository.getError().removeObserver(errorObserver);
    }
}
