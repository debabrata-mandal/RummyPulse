package com.example.rummypulse.ui.playerranking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.rummypulse.R;
import com.example.rummypulse.data.GameDefaultsRepository;
import com.example.rummypulse.databinding.FragmentPlayerRankingBinding;
import com.example.rummypulse.ui.dashboard.LeaderboardEntry;
import com.example.rummypulse.ui.dashboard.StatsPeriod;

import java.util.List;

/**
 * Every ranked player for one reporting period, the full table the dashboard donut samples the
 * ends of.
 *
 * <p>Reachable from the donut and from the navigation drawer. The drawer entry stays available
 * even when an admin hides the dashboard leaderboard, so this screen deliberately does not consult
 * {@code showDashboardLeaderboard} for its own visibility; only amounts are gated, and then by
 * both switches together.
 */
public class PlayerRankingFragment extends Fragment {

    /** Period to open with, as a {@link StatsPeriod} name. Absent when opened from the drawer. */
    public static final String ARG_PERIOD = "period";

    private FragmentPlayerRankingBinding binding;
    private PlayerRankingViewModel viewModel;
    private PlayerRankingAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(PlayerRankingViewModel.class);
        binding = FragmentPlayerRankingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new PlayerRankingAdapter();
        binding.recyclerRanking.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerRanking.setAdapter(adapter);

        // Only on first creation, so a rotation or a return from the back stack keeps the choice.
        if (savedInstanceState == null) {
            viewModel.setInitialPeriod(periodFromArguments());
        }

        binding.segmentAllTime.setOnClickListener(
                v -> viewModel.selectPeriod(StatsPeriod.ALL_TIME));
        binding.segmentThisMonth.setOnClickListener(
                v -> viewModel.selectPeriod(StatsPeriod.THIS_MONTH));
        binding.segmentLastMonth.setOnClickListener(
                v -> viewModel.selectPeriod(StatsPeriod.LAST_MONTH));
        binding.segmentThisWeek.setOnClickListener(
                v -> viewModel.selectPeriod(StatsPeriod.THIS_WEEK));

        viewModel.getSelectedPeriod().observe(getViewLifecycleOwner(), this::applyPeriodSelection);
        viewModel.getRanking().observe(getViewLifecycleOwner(), this::renderRanking);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Defaults may have changed on another device since this screen was last shown.
        GameDefaultsRepository.getInstance(requireContext()).refreshFromServer(() -> {
            if (binding != null) {
                applyAmountVisibility();
            }
        });
        applyAmountVisibility();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private StatsPeriod periodFromArguments() {
        String name = getArguments() == null ? null : getArguments().getString(ARG_PERIOD);
        if (name == null) {
            return StatsPeriod.THIS_MONTH;
        }
        try {
            return StatsPeriod.valueOf(name);
        } catch (IllegalArgumentException e) {
            return StatsPeriod.THIS_MONTH;
        }
    }

    private void applyAmountVisibility() {
        boolean visible =
                GameDefaultsRepository.getInstance(requireContext()).isLeaderboardAmountsVisible();
        adapter.setShowAmounts(visible);
        binding.textRankingAmountsHidden.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private void applyPeriodSelection(StatsPeriod period) {
        StatsPeriod selected = period == null ? StatsPeriod.THIS_MONTH : period;
        styleSegment(binding.segmentAllTime, selected == StatsPeriod.ALL_TIME);
        styleSegment(binding.segmentThisMonth, selected == StatsPeriod.THIS_MONTH);
        styleSegment(binding.segmentLastMonth, selected == StatsPeriod.LAST_MONTH);
        styleSegment(binding.segmentThisWeek, selected == StatsPeriod.THIS_WEEK);
    }

    private void styleSegment(TextView segment, boolean selected) {
        segment.setBackgroundResource(selected
                ? R.drawable.bg_segment_selected
                : R.drawable.bg_segment_unselected);
        segment.setTextColor(ContextCompat.getColor(requireContext(), selected
                ? R.color.text_primary
                : R.color.view_text_secondary));
    }

    private void renderRanking(List<LeaderboardEntry> entries) {
        if (binding == null) {
            return;
        }
        int count = entries == null ? 0 : entries.size();
        adapter.setEntries(entries);

        binding.textRankingEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        binding.textRankingSummary.setText(count == 0
                ? ""
                : getResources().getQuantityString(
                        R.plurals.dashboard_leaderboard_summary, count, count));
    }
}
