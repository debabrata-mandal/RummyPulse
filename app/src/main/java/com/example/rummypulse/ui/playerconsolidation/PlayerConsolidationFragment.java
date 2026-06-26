package com.example.rummypulse.ui.playerconsolidation;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.rummypulse.R;
import com.example.rummypulse.databinding.FragmentPlayerConsolidationBinding;
import com.example.rummypulse.ui.home.GameItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PlayerConsolidationFragment extends Fragment {

    private FragmentPlayerConsolidationBinding binding;
    private PlayerConsolidationViewModel viewModel;
    private ConsolidationGameAdapter adapter;
    private List<GameItem> currentGames = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlayerConsolidationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PlayerConsolidationViewModel.class);

        adapter = new ConsolidationGameAdapter();
        adapter.setOnGameSelectionListener(game -> viewModel.toggleGameSelection(game.getGameId()));
        binding.recyclerGames.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerGames.setAdapter(adapter);

        viewModel.getGameItems().observe(getViewLifecycleOwner(), games -> {
            currentGames = games != null ? games : new ArrayList<>();
            adapter.setGameItems(currentGames);
            boolean isEmpty = currentGames.isEmpty();
            binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        });

        viewModel.getSelectedGameIds().observe(getViewLifecycleOwner(), this::updateSelectionUi);

        binding.btnContinue.setOnClickListener(v -> showMapPlayersStep());
        binding.btnChangeGames.setOnClickListener(v -> showSelectGamesStep());
    }

    private void updateSelectionUi(Set<String> selectedIds) {
        adapter.setSelectedIds(selectedIds);
        int count = selectedIds != null ? selectedIds.size() : 0;
        binding.btnContinue.setEnabled(count >= 2);
        binding.btnContinue.setText(getString(R.string.player_consolidation_continue, count));
    }

    private void showMapPlayersStep() {
        List<GameItem> selected = viewModel.getSelectedGames(currentGames);
        List<String> labels = new ArrayList<>();
        for (GameItem game : selected) {
            labels.add(game.getDashboardPrimaryLabel());
        }
        binding.textSelectedGames.setText(TextUtils.join("\n", labels));
        binding.stepSelectGames.setVisibility(View.GONE);
        binding.stepMapPlayers.setVisibility(View.VISIBLE);
    }

    private void showSelectGamesStep() {
        binding.stepMapPlayers.setVisibility(View.GONE);
        binding.stepSelectGames.setVisibility(View.VISIBLE);
    }
}
