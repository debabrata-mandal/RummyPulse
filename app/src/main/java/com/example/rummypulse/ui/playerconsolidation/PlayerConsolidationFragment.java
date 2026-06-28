package com.example.rummypulse.ui.playerconsolidation;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.rummypulse.R;
import com.example.rummypulse.databinding.FragmentPlayerConsolidationBinding;
import com.example.rummypulse.ui.home.GameItem;
import com.example.rummypulse.utils.ModernToast;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PlayerConsolidationFragment extends Fragment {

    private FragmentPlayerConsolidationBinding binding;
    private PlayerConsolidationViewModel viewModel;
    private ConsolidationGameAdapter gameAdapter;
    private ConsolidatedPlayerAdapter consolidatedAdapter;
    private SelectedGamesStatusAdapter selectedGamesStatusAdapter;
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
        viewModel = new ViewModelProvider(requireActivity()).get(PlayerConsolidationViewModel.class);

        setupGameSelectionStep();
        setupMapPlayersStep();

        viewModel.getGameItems().observe(getViewLifecycleOwner(), games -> {
            currentGames = games != null ? games : new ArrayList<>();
            gameAdapter.setGameItems(currentGames);
            boolean isEmpty = currentGames.isEmpty();
            binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            if (viewModel.hasActiveConsolidation() && binding.stepMapPlayers.getVisibility() == View.VISIBLE) {
                updateSelectedGamesStatus();
                handleRefreshOutcome(viewModel.refreshConsolidationFromLatestGames(currentGames, false));
            }
        });

        viewModel.getSelectedGameIds().observe(getViewLifecycleOwner(), this::updateGameSelectionUi);

        if (viewModel.hasActiveConsolidation()) {
            showMapPlayersStep(false);
        }
    }

    private void setupGameSelectionStep() {
        gameAdapter = new ConsolidationGameAdapter();
        gameAdapter.setOnGameSelectionListener(game -> viewModel.toggleGameSelection(game.getGameId()));
        binding.recyclerGames.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerGames.setAdapter(gameAdapter);
        binding.btnContinue.setOnClickListener(v -> showMapPlayersStep(true));
        binding.btnChangeGames.setOnClickListener(v -> showSelectGamesStep());
    }

    private void setupMapPlayersStep() {
        consolidatedAdapter = new ConsolidatedPlayerAdapter();
        consolidatedAdapter.setOnGroupToggleListener(viewModel::toggleGroupSelection);

        selectedGamesStatusAdapter = new SelectedGamesStatusAdapter();
        LinearLayoutManager selectedGamesLayoutManager = new LinearLayoutManager(requireContext());
        selectedGamesLayoutManager.setAutoMeasureEnabled(true);
        binding.recyclerSelectedGamesStatus.setLayoutManager(selectedGamesLayoutManager);
        binding.recyclerSelectedGamesStatus.setAdapter(selectedGamesStatusAdapter);
        binding.recyclerSelectedGamesStatus.setNestedScrollingEnabled(false);

        LinearLayoutManager consolidatedLayoutManager = new LinearLayoutManager(requireContext());
        consolidatedLayoutManager.setAutoMeasureEnabled(true);
        binding.recyclerConsolidatedPlayers.setLayoutManager(consolidatedLayoutManager);
        binding.recyclerConsolidatedPlayers.setAdapter(consolidatedAdapter);
        binding.recyclerConsolidatedPlayers.setNestedScrollingEnabled(false);

        viewModel.getPlayerGroups().observe(getViewLifecycleOwner(), groups -> {
            consolidatedAdapter.setGroups(groups);
            boolean isEmpty = groups == null || groups.isEmpty();
            binding.textNoConsolidatedPlayers.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.recyclerConsolidatedPlayers.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            binding.consolidationTotalsSummary.getRoot().setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });

        viewModel.getSelectedEntryIds().observe(getViewLifecycleOwner(), this::updateEntrySelectionUi);
        viewModel.getConsolidationTotals().observe(getViewLifecycleOwner(), this::updateTotalsSummary);

        binding.btnLinkSelected.setOnClickListener(v -> showLinkDialog());
        binding.btnUnlinkSelected.setOnClickListener(v -> showUnlinkDialog());
        binding.btnTransferAmount.setOnClickListener(v -> showTransferDialog());
        binding.fabRefreshGameData.setOnClickListener(v -> {
            PlayerConsolidationViewModel.RefreshOutcome outcome =
                    viewModel.refreshConsolidationFromLatestGames(currentGames, true);
            if (outcome == PlayerConsolidationViewModel.RefreshOutcome.SKIPPED) {
                return;
            }
            if (outcome == PlayerConsolidationViewModel.RefreshOutcome.REFRESHED_WITH_MISSING_MEMBERS) {
                ModernToast.warning(requireContext(),
                        getString(R.string.player_consolidation_refresh_missing_members));
            } else {
                ModernToast.success(requireContext(), getString(R.string.player_consolidation_refresh_data_done));
            }
        });
        binding.btnResetMappings.setOnClickListener(v -> {
            List<GameItem> selected = viewModel.getSelectedGames(currentGames);
            viewModel.resetConsolidation(selected);
        });
    }

    private void updateGameSelectionUi(Set<String> selectedIds) {
        gameAdapter.setSelectedIds(selectedIds);
        int count = selectedIds != null ? selectedIds.size() : 0;
        binding.btnContinue.setEnabled(count >= 2);
        binding.btnContinue.setText(getString(R.string.player_consolidation_continue, count));
    }

    private void updateEntrySelectionUi(Set<String> selectedIds) {
        consolidatedAdapter.setSelectedEntryIds(selectedIds);
        binding.btnUnlinkSelected.setVisibility(viewModel.canUnlinkSelected() ? View.VISIBLE : View.GONE);
        binding.btnLinkSelected.setVisibility(viewModel.canLinkSelected() ? View.VISIBLE : View.GONE);
        binding.btnTransferAmount.setVisibility(viewModel.canTransferBetweenSelected() ? View.VISIBLE : View.GONE);
    }

    private void updateSelectedGamesStatus() {
        selectedGamesStatusAdapter.setGames(viewModel.getSelectedGames(currentGames));
    }

    private void handleRefreshOutcome(PlayerConsolidationViewModel.RefreshOutcome outcome) {
        if (outcome == PlayerConsolidationViewModel.RefreshOutcome.REFRESHED_WITH_MISSING_MEMBERS) {
            ModernToast.warning(requireContext(),
                    getString(R.string.player_consolidation_refresh_missing_members));
        }
    }

    private void updateTotalsSummary(ConsolidationTotals totals) {
        if (totals == null) {
            binding.consolidationTotalsSummary.getRoot().setVisibility(View.GONE);
            return;
        }
        binding.consolidationTotalsSummary.getRoot().setVisibility(View.VISIBLE);
        binding.consolidationTotalsSummary.textTotalContribution.setText(
                ConsolidationAmountFormatter.formatContribution(totals.getTotalContribution()));
        binding.consolidationTotalsSummary.textTotalContribution.setTextColor(
                ConsolidationAmountFormatter.getContributionColor(
                        requireContext(), totals.getTotalContribution()));
    }

    private void showMapPlayersStep(boolean initializeIfNeeded) {
        List<GameItem> selected = viewModel.getSelectedGames(currentGames);
        if (initializeIfNeeded || !viewModel.hasActiveConsolidation()) {
            viewModel.initializeConsolidation(selected);
        }
        binding.stepSelectGames.setVisibility(View.GONE);
        binding.stepMapPlayers.setVisibility(View.VISIBLE);
        binding.fabRefreshGameData.setVisibility(View.VISIBLE);
        updateSelectedGamesStatus();
    }

    private void showSelectGamesStep() {
        binding.stepMapPlayers.setVisibility(View.GONE);
        binding.stepSelectGames.setVisibility(View.VISIBLE);
        binding.fabRefreshGameData.setVisibility(View.GONE);
    }

    private void showLinkDialog() {
        List<String> selectedNames = viewModel.getSelectedEntryNames();
        if (selectedNames.size() < 2) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_link_players, null);
        TextView selectedNamesText = dialogView.findViewById(R.id.text_selected_names);
        TextInputEditText displayNameInput = dialogView.findViewById(R.id.input_display_name);
        selectedNamesText.setText(TextUtils.join(", ", selectedNames));
        displayNameInput.setText(selectedNames.get(0));

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_link).setOnClickListener(v -> {
            String displayName = displayNameInput.getText() != null
                    ? displayNameInput.getText().toString()
                    : selectedNames.get(0);
            viewModel.mergeSelectedPlayers(displayName);
            dialog.dismiss();
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.show();
        configureDialogWidth(dialog);
    }

    private void showUnlinkDialog() {
        ConsolidatedPlayerGroup group = viewModel.getSelectedGroupForUnlink();
        if (group == null) {
            return;
        }

        List<String> memberNames = new ArrayList<>();
        for (GamePlayerEntry member : group.getMembers()) {
            memberNames.add(member.getPlayerName());
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_unlink_players, null);
        TextView memberNamesText = dialogView.findViewById(R.id.text_member_names);
        memberNamesText.setText(TextUtils.join(", ", memberNames));

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_unlink).setOnClickListener(v -> {
            viewModel.unlinkSelectedGroup();
            dialog.dismiss();
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.show();
        configureDialogWidth(dialog);
    }

    private void showTransferDialog() {
        ConsolidatedPlayerGroup defaultFrom = viewModel.getDefaultTransferFromGroup();
        ConsolidatedPlayerGroup defaultTo = viewModel.getDefaultTransferToGroup();
        if (defaultFrom == null || defaultTo == null) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_transfer_amount, null);
        RadioButton radioFromFirst = dialogView.findViewById(R.id.radio_from_first);
        RadioButton radioFromSecond = dialogView.findViewById(R.id.radio_from_second);
        TextInputEditText amountInput = dialogView.findViewById(R.id.input_transfer_amount);

        radioFromFirst.setText(formatTransferFromOption(defaultFrom));
        radioFromSecond.setText(formatTransferFromOption(defaultTo));
        radioFromFirst.setTag(defaultFrom.getGroupId());
        radioFromSecond.setTag(defaultTo.getGroupId());

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_apply_transfer).setOnClickListener(v -> {
            String fromGroupId = radioFromFirst.isChecked()
                    ? (String) radioFromFirst.getTag()
                    : (String) radioFromSecond.getTag();
            String toGroupId = radioFromFirst.isChecked()
                    ? (String) radioFromSecond.getTag()
                    : (String) radioFromFirst.getTag();
            double amount = parseTransferAmount(amountInput);
            if (amount <= 0) {
                ModernToast.error(requireContext(),
                        getString(R.string.player_consolidation_transfer_invalid_amount));
                return;
            }
            if (viewModel.applyTransfer(fromGroupId, toGroupId, amount)) {
                dialog.dismiss();
            }
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.show();
        configureDialogWidth(dialog);
    }

    private String formatTransferFromOption(ConsolidatedPlayerGroup group) {
        return getString(
                R.string.player_consolidation_transfer_from_option,
                group.getDisplayName(),
                ConsolidationAmountFormatter.formatSignedAmount(group.getAdjustedNetAmount()));
    }

    private static double parseTransferAmount(TextInputEditText amountInput) {
        if (amountInput.getText() == null) {
            return 0;
        }
        String raw = amountInput.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) {
            return 0;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void configureDialogWidth(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics dm = requireContext().getResources().getDisplayMetrics();
            int maxPx = getResources().getDimensionPixelSize(R.dimen.dialog_create_game_max_width);
            int widthPx = Math.min((int) (dm.widthPixels * 0.92f), maxPx);
            window.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}
