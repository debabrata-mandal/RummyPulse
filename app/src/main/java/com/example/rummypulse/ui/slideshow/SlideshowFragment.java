package com.example.rummypulse.ui.slideshow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.rummypulse.databinding.FragmentSlideshowBinding;

public class SlideshowFragment extends Fragment {

    private FragmentSlideshowBinding binding;
    private SlideshowViewModel slideshowViewModel;
    private ExpandableMonthlyReportAdapter reportAdapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView errorText;
    private LinearLayout emptyState;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        slideshowViewModel = new ViewModelProvider(this).get(SlideshowViewModel.class);

        binding = FragmentSlideshowBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        initializeViews();
        setupRecyclerView();
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

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            slideshowViewModel.refreshReports();
        });
        
        // Set swipe refresh colors
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        );
    }

    private void observeViewModel() {
        // Observe monthly point value reports
        slideshowViewModel.getMonthlyPointValueReports().observe(getViewLifecycleOwner(), reports -> {
            if (reports != null && !reports.isEmpty()) {
                showReports();
                reportAdapter.updateReports(reports);
            } else {
                showEmptyState();
            }
        });

        // Observe loading state
        slideshowViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
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

        // Observe errors
        slideshowViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                showError(error);
            }
        });
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