package com.example.rummypulse.ui.reports;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.rummypulse.R;
import com.example.rummypulse.data.AppUserRoleSession;
import com.example.rummypulse.databinding.FragmentReportsBinding;

import java.text.DateFormatSymbols;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

public class ReportsFragment extends Fragment {

    private FragmentReportsBinding binding;
    private ReportsViewModel reportsViewModel;
    private ExpandableMonthlyReportAdapter reportAdapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView errorText;
    private LinearLayout emptyState;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        reportsViewModel = new ViewModelProvider(this).get(ReportsViewModel.class);

        binding = FragmentReportsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        initializeViews();
        setupRecyclerView();
        setupBuildActions();
        setupSwipeRefresh();
        observeViewModel();

        return root;
    }

    private void initializeViews() {
        recyclerView = binding.recyclerReports;
        swipeRefreshLayout = binding.swipeRefresh;
        progressBar = binding.progressBar;
        errorText = binding.textError;
        emptyState = binding.emptyState;
    }

    private void setupRecyclerView() {
        reportAdapter = new ExpandableMonthlyReportAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(reportAdapter);
    }

    private void setupBuildActions() {
        applyBuildMonthAccess(AppUserRoleSession.getInstance().peekRole());
        binding.fabBuildMonth.setOnClickListener(v -> {
            if (AppUserRoleSession.getInstance().peekRole() != AppUserRoleSession.Role.ADMIN) {
                applyBuildMonthAccess(AppUserRoleSession.getInstance().peekRole());
                return;
            }
            showRebuildMonthDialog();
        });
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            com.example.rummypulse.utils.ModernToast.progress(getContext(), "Refreshing reports...");
            reportsViewModel.refreshReports();
        });
        
        // Set swipe refresh colors
        swipeRefreshLayout.setColorSchemeResources(
                com.example.rummypulse.R.color.accent_blue,
                com.example.rummypulse.R.color.accent_blue_light,
                com.example.rummypulse.R.color.success_green);
    }

    private void observeViewModel() {
        // Observe monthly point value reports
        reportsViewModel.getMonthlyPointValueReports().observe(getViewLifecycleOwner(), reports -> {
            if (reports != null && !reports.isEmpty()) {
                showReports();
                reportAdapter.updateReports(reports);
            } else {
                showEmptyState();
            }
        });

        // Observe loading state
        reportsViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading != null) {
                swipeRefreshLayout.setRefreshing(isLoading);
                if (isLoading) {
                    progressBar.setVisibility(View.VISIBLE);
                    hideAllStates();
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        reportsViewModel.getUiMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                com.example.rummypulse.utils.ModernToast.success(getContext(), msg);
                reportsViewModel.clearUiMessage();
            }
        });

        // Observe errors
        reportsViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                showError(error);
            }
        });

        AppUserRoleSession.getInstance().getRole().observe(getViewLifecycleOwner(),
                this::applyBuildMonthAccess);
    }

    private void applyBuildMonthAccess(AppUserRoleSession.Role role) {
        if (binding == null) {
            return;
        }
        boolean isAdmin = role == AppUserRoleSession.Role.ADMIN;
        binding.fabBuildMonth.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        binding.fabBuildMonth.setEnabled(isAdmin);
    }

    private void showRebuildMonthDialog() {
        if (!isAdded() || getContext() == null) {
            return;
        }

        Calendar defaultMonth = Calendar.getInstance();

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), 0);

        TextView message = new TextView(requireContext());
        message.setText(R.string.reports_rebuild_month_dialog_message);
        content.addView(message);

        LinearLayout pickerRow = new LinearLayout(requireContext());
        pickerRow.setOrientation(LinearLayout.HORIZONTAL);
        pickerRow.setPadding(0, dp(12), 0, 0);
        content.addView(pickerRow);

        NumberPicker monthPicker = new NumberPicker(requireContext());
        String[] monthNames = Arrays.copyOf(
                DateFormatSymbols.getInstance(Locale.getDefault()).getMonths(), 12);
        monthPicker.setMinValue(Calendar.JANUARY);
        monthPicker.setMaxValue(Calendar.DECEMBER);
        monthPicker.setDisplayedValues(monthNames);
        monthPicker.setValue(defaultMonth.get(Calendar.MONTH));

        NumberPicker yearPicker = new NumberPicker(requireContext());
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        yearPicker.setMinValue(2000);
        yearPicker.setMaxValue(currentYear + 1);
        yearPicker.setWrapSelectorWheel(false);
        yearPicker.setValue(defaultMonth.get(Calendar.YEAR));

        pickerRow.addView(createPickerColumn(
                getString(R.string.reports_rebuild_month_month_label), monthPicker));
        pickerRow.addView(createPickerColumn(
                getString(R.string.reports_rebuild_month_year_label), yearPicker));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.reports_rebuild_month_dialog_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reports_rebuild_month_confirm,
                        (dialog, which) -> reportsViewModel.rebuildMonthReport(
                                yearPicker.getValue(), monthPicker.getValue()))
                .show();
    }

    private LinearLayout createPickerColumn(String label, NumberPicker picker) {
        LinearLayout column = new LinearLayout(requireContext());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView labelView = new TextView(requireContext());
        labelView.setText(label);
        labelView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        column.addView(labelView);
        column.addView(picker);
        return column;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showReports() {
        recyclerView.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        emptyState.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    private void showError(String error) {
        errorText.setVisibility(View.VISIBLE);
        errorText.setText("Error: " + error);
        recyclerView.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    private void hideAllStates() {
        recyclerView.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
