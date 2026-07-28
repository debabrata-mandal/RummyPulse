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
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.rummypulse.R;
import com.example.rummypulse.databinding.FragmentPlayerConsolidationBinding;
import com.example.rummypulse.ui.home.GameItem;
import com.example.rummypulse.utils.ModernToast;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PlayerConsolidationFragment extends Fragment {

    private FragmentPlayerConsolidationBinding binding;
    private PlayerConsolidationViewModel viewModel;
    private ConsolidationGameAdapter gameAdapter;
    private ConsolidatedPlayerAdapter consolidatedAdapter;
    private SelectedGamesStatusAdapter selectedGamesStatusAdapter;
    private SettlementPaymentAdapter settlementPaymentAdapter;
    private SettlementPlayerSummaryAdapter settlementPlayerSummaryAdapter;
    private BalanceAdjustmentAdapter balanceAdjustmentAdapter;
    private List<GameItem> currentGames = new ArrayList<>();
    private boolean hasPlayerGroups;
    private boolean mappingsConfirmed;

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
            viewModel.pruneUnavailableGameSelections(currentGames);
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
        consolidatedAdapter.setOnGroupToggleListener(group -> {
            if (!mappingsConfirmed) {
                viewModel.toggleGroupSelection(group);
            }
        });

        selectedGamesStatusAdapter = new SelectedGamesStatusAdapter();
        LinearLayoutManager selectedGamesLayoutManager = new LinearLayoutManager(requireContext());
        selectedGamesLayoutManager.setAutoMeasureEnabled(true);
        binding.recyclerSelectedGamesStatus.setLayoutManager(selectedGamesLayoutManager);
        binding.recyclerSelectedGamesStatus.setAdapter(selectedGamesStatusAdapter);
        binding.recyclerSelectedGamesStatus.setNestedScrollingEnabled(false);

        LinearLayoutManager consolidatedLayoutManager =
                new LinearLayoutManager(requireContext());
        consolidatedLayoutManager.setAutoMeasureEnabled(true);
        binding.recyclerConsolidatedPlayers.setLayoutManager(consolidatedLayoutManager);
        binding.recyclerConsolidatedPlayers.setAdapter(consolidatedAdapter);
        binding.recyclerConsolidatedPlayers.setNestedScrollingEnabled(false);

        settlementPlayerSummaryAdapter = new SettlementPlayerSummaryAdapter();
        settlementPlayerSummaryAdapter.setEditMappingsListener(() -> {
            viewModel.editMappings();
            binding.scrollMappingSettlement.post(
                    () -> binding.scrollMappingSettlement.scrollTo(0, 0));
        });
        GridLayoutManager playerSummaryLayoutManager =
                new GridLayoutManager(requireContext(), 2);
        playerSummaryLayoutManager.setAutoMeasureEnabled(true);
        binding.recyclerSettlementPlayerSummary.setLayoutManager(
                playerSummaryLayoutManager);
        binding.recyclerSettlementPlayerSummary.setAdapter(
                settlementPlayerSummaryAdapter);
        binding.recyclerSettlementPlayerSummary.setNestedScrollingEnabled(false);

        settlementPaymentAdapter = new SettlementPaymentAdapter();
        LinearLayoutManager settlementLayoutManager = new LinearLayoutManager(requireContext());
        settlementLayoutManager.setAutoMeasureEnabled(true);
        binding.recyclerSettlementPayments.setLayoutManager(settlementLayoutManager);
        binding.recyclerSettlementPayments.setAdapter(settlementPaymentAdapter);
        binding.recyclerSettlementPayments.setNestedScrollingEnabled(false);

        balanceAdjustmentAdapter = new BalanceAdjustmentAdapter();
        balanceAdjustmentAdapter.setOnDeleteAdjustmentListener(
                adjustment -> viewModel.deleteAdjustment(adjustment.getAdjustmentId()));
        LinearLayoutManager adjustmentLayoutManager = new LinearLayoutManager(requireContext());
        adjustmentLayoutManager.setAutoMeasureEnabled(true);
        binding.recyclerBalanceAdjustments.setLayoutManager(adjustmentLayoutManager);
        binding.recyclerBalanceAdjustments.setAdapter(balanceAdjustmentAdapter);
        binding.recyclerBalanceAdjustments.setNestedScrollingEnabled(false);

        viewModel.getPlayerGroups().observe(getViewLifecycleOwner(), groups -> {
            consolidatedAdapter.setGroups(groups);
            settlementPlayerSummaryAdapter.setGroups(groups);
            updatePlayerTableTotals(groups);
            boolean isEmpty = groups == null || groups.isEmpty();
            hasPlayerGroups = !isEmpty;
            binding.textNoConsolidatedPlayers.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.recyclerConsolidatedPlayers.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            updateStageVisibility();
        });

        viewModel.getSelectedEntryIds().observe(getViewLifecycleOwner(), this::updateEntrySelectionUi);
        viewModel.getConsolidationTotals().observe(getViewLifecycleOwner(), this::updateTotalsSummary);
        viewModel.getSettlementResult().observe(
                getViewLifecycleOwner(), this::updateSettlementUi);
        viewModel.getBalanceAdjustments().observe(getViewLifecycleOwner(), adjustments -> {
            balanceAdjustmentAdapter.setAdjustments(adjustments);
        });
        viewModel.getMappingsConfirmed().observe(getViewLifecycleOwner(), confirmed -> {
            mappingsConfirmed = Boolean.TRUE.equals(confirmed);
            updateStageVisibility();
            updateEntrySelectionUi(viewModel.getSelectedEntryIds().getValue());
        });

        binding.btnLinkSelected.setOnClickListener(v -> showLinkDialog());
        binding.btnUnlinkSelected.setOnClickListener(v -> showUnlinkDialog());
        binding.btnConfirmMappings.setOnClickListener(v -> {
            viewModel.confirmMappings();
            binding.scrollMappingSettlement.post(
                    () -> binding.scrollMappingSettlement.scrollTo(0, 0));
        });
        binding.btnEditMappings.setOnClickListener(v -> viewModel.editMappings());
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
        int count = viewModel.getSelectedGames(currentGames).size();
        binding.btnContinue.setEnabled(count >= 2);
        binding.btnContinue.setText(getString(R.string.player_consolidation_continue, count));
    }

    private void updateEntrySelectionUi(Set<String> selectedIds) {
        consolidatedAdapter.setSelectedEntryIds(selectedIds);
        binding.btnUnlinkSelected.setVisibility(
                !mappingsConfirmed && viewModel.canUnlinkSelected()
                        ? View.VISIBLE : View.GONE);
        binding.btnLinkSelected.setVisibility(
                !mappingsConfirmed && viewModel.canLinkSelected()
                        ? View.VISIBLE : View.GONE);
    }

    private void updateStageVisibility() {
        if (binding == null) {
            return;
        }
        boolean showMappingControls = hasPlayerGroups && !mappingsConfirmed;
        boolean showSettlement = hasPlayerGroups && mappingsConfirmed;

        binding.textMapInstructions.setVisibility(
                showMappingControls ? View.VISIBLE : View.GONE);
        binding.textSelectedGamesLabel.setVisibility(
                showMappingControls ? View.VISIBLE : View.GONE);
        binding.recyclerSelectedGamesStatus.setVisibility(
                showMappingControls ? View.VISIBLE : View.GONE);
        binding.cardMappingStep.setVisibility(
                showMappingControls ? View.VISIBLE : View.GONE);
        binding.textMappingPlayersTitle.setVisibility(
                showMappingControls ? View.VISIBLE : View.GONE);
        binding.recyclerConsolidatedPlayers.setVisibility(
                showMappingControls ? View.VISIBLE : View.GONE);
        binding.textNoConsolidatedPlayers.setVisibility(
                !mappingsConfirmed && !hasPlayerGroups ? View.VISIBLE : View.GONE);
        binding.btnConfirmMappings.setVisibility(
                showMappingControls ? View.VISIBLE : View.GONE);
        binding.btnEditMappings.setVisibility(View.GONE);
        binding.cardPlayerSummaryTable.setVisibility(
                showSettlement ? View.VISIBLE : View.GONE);
        binding.consolidationTotalsSummary.getRoot().setVisibility(
                showSettlement ? View.VISIBLE : View.GONE);
        binding.cardBalanceAdjustments.setVisibility(
                showSettlement ? View.VISIBLE : View.GONE);
        binding.cardSettlement.setVisibility(showSettlement ? View.VISIBLE : View.GONE);
        binding.fabRefreshGameData.setVisibility(
                showMappingControls ? View.VISIBLE : View.GONE);

        binding.textMappingStepTitle.setText(mappingsConfirmed
                ? R.string.player_consolidation_mapping_confirmed_title
                : R.string.player_consolidation_mapping_step_title);
        binding.textMappingStepSubtitle.setText(mappingsConfirmed
                ? R.string.player_consolidation_mapping_confirmed_subtitle
                : R.string.player_consolidation_mapping_step_subtitle);
    }

    private void updatePlayerTableTotals(List<ConsolidatedPlayerGroup> groups) {
        int gameEntries = 0;
        double gross = 0;
        double contribution = 0;
        double net = 0;
        double adjustment = 0;
        if (groups != null) {
            for (ConsolidatedPlayerGroup group : groups) {
                gameEntries += group.getMembers().size();
                gross += group.getTotalGrossAmount();
                contribution += group.getTotalContribution();
                net += group.getTotalNetAmount();
                adjustment += group.getNetAdjustment();
            }
        }
        double tolerance = gameEntries * 0.5;
        double normalizedGross = Math.abs(gross) <= tolerance ? 0 : gross;
        double normalizedNet = Math.abs(net + contribution) <= tolerance
                ? 0 : net + contribution;
        double normalizedFinal = Math.abs(net + adjustment + contribution) <= tolerance
                ? 0 : net + adjustment + contribution;

        binding.textPlayerTableTotalGames.setText(String.valueOf(gameEntries));
        binding.textPlayerTableTotalGross.setText(
                ConsolidationAmountFormatter.formatSignedAmount(normalizedGross));
        binding.textPlayerTableTotalContribution.setText(
                ConsolidationAmountFormatter.formatAmount(contribution));
        binding.textPlayerTableTotalNet.setText(
                ConsolidationAmountFormatter.formatSignedAmount(normalizedNet));
        binding.textPlayerTableTotalAdjustment.setText(
                ConsolidationAmountFormatter.formatSignedAmount(adjustment));
        binding.textPlayerTableTotalFinal.setText(
                ConsolidationAmountFormatter.formatSignedAmount(normalizedFinal));
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
        binding.consolidationTotalsSummary.textTotalContribution.setText(
                ConsolidationAmountFormatter.formatContribution(totals.getTotalContribution()));
        binding.consolidationTotalsSummary.textTotalContribution.setTextColor(
                ConsolidationAmountFormatter.getContributionColor(
                        requireContext(), totals.getTotalContribution()));
        binding.consolidationTotalsSummary.textSummaryContribution.setText(
                ConsolidationAmountFormatter.formatContribution(
                        totals.getTotalContribution()));
        binding.consolidationTotalsSummary.textTotalGross.setText(
                ConsolidationAmountFormatter.formatAmount(
                        totals.getTotalGrossWinnings()));
        binding.consolidationTotalsSummary.textNetPlayerBalance.setText(
                ConsolidationAmountFormatter.formatSignedAmount(
                        totals.getNetPlayerBalance()));
        binding.consolidationTotalsSummary.textNetPlayerBalance.setTextColor(
                ConsolidationAmountFormatter.getSignedAmountColor(
                        requireContext(), totals.getNetPlayerBalance()));
    }

    private void updateSettlementUi(ConsolidatedSettlementCalculator.Result result) {
        if (result == null) {
            binding.cardSettlement.setVisibility(View.GONE);
            return;
        }

        boolean unbalanced = result.getStatus()
                == ConsolidatedSettlementCalculator.Status.UNBALANCED_INPUT;
        boolean allSettled = result.getStatus()
                == ConsolidatedSettlementCalculator.Status.ALL_SETTLED;

        settlementPaymentAdapter.setPayments(result.getPayments());
        binding.recyclerSettlementPayments.setVisibility(
                unbalanced || allSettled ? View.GONE : View.VISIBLE);
        binding.layoutSettlementMetrics.setVisibility(unbalanced ? View.GONE : View.VISIBLE);
        binding.textSettlementEmpty.setVisibility(allSettled ? View.VISIBLE : View.GONE);
        binding.textSettlementWarning.setVisibility(unbalanced ? View.VISIBLE : View.GONE);
        binding.textSettlementTotal.setText(ConsolidationAmountFormatter.formatAmount(
                result.getPlayerPaymentTotalPaise() / 100.0));
        binding.textSettlementCount.setText(String.valueOf(result.getPayments().size()));
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
        List<ConsolidatedPlayerGroup> groups = viewModel.getAvailableGroups();
        if (groups.size() < 2) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_transfer_amount, null);
        MaterialAutoCompleteTextView fromInput =
                dialogView.findViewById(R.id.input_adjustment_from);
        MaterialAutoCompleteTextView toInput =
                dialogView.findViewById(R.id.input_adjustment_to);
        TextInputEditText amountInput = dialogView.findViewById(R.id.input_transfer_amount);
        TextInputEditText reasonInput = dialogView.findViewById(R.id.input_adjustment_reason);

        List<String> groupOptions = new ArrayList<>();
        for (ConsolidatedPlayerGroup group : groups) {
            groupOptions.add(formatAdjustmentOption(group));
        }
        ArrayAdapter<String> dropdownAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_adjustment_dropdown,
                groupOptions);
        fromInput.setAdapter(dropdownAdapter);
        toInput.setAdapter(dropdownAdapter);
        fromInput.setText(groupOptions.get(0), false);
        toInput.setText(groupOptions.get(1), false);
        final int[] selectedIndexes = {0, 1};
        fromInput.setOnItemClickListener((parent, view, position, id) ->
                selectedIndexes[0] = position);
        toInput.setOnItemClickListener((parent, view, position, id) ->
                selectedIndexes[1] = position);

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_apply_transfer).setOnClickListener(v -> {
            ConsolidatedPlayerGroup fromGroup = groups.get(selectedIndexes[0]);
            ConsolidatedPlayerGroup toGroup = groups.get(selectedIndexes[1]);
            double amount = parseTransferAmount(amountInput);
            if (selectedIndexes[0] == selectedIndexes[1]) {
                ModernToast.error(requireContext(),
                        getString(R.string.player_consolidation_adjustment_same_player));
                return;
            }
            if (amount <= 0) {
                ModernToast.error(requireContext(),
                        getString(R.string.player_consolidation_transfer_invalid_amount));
                return;
            }
            String reason = reasonInput.getText() != null
                    ? reasonInput.getText().toString()
                    : "";
            if (viewModel.applyTransfer(
                    fromGroup.getGroupId(), toGroup.getGroupId(), amount, reason)) {
                dialog.dismiss();
            }
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.show();
        configureDialogWidth(dialog,
                R.dimen.dialog_balance_adjustment_max_width,
                0.94f);
    }

    private String formatAdjustmentOption(ConsolidatedPlayerGroup group) {
        return getString(
                R.string.player_consolidation_adjustment_option,
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
        configureDialogWidth(dialog, R.dimen.dialog_create_game_max_width, 0.92f);
    }

    private void configureDialogWidth(AlertDialog dialog, int maxWidthResource, float screenFraction) {
        Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics dm = requireContext().getResources().getDisplayMetrics();
            int maxPx = getResources().getDimensionPixelSize(maxWidthResource);
            int widthPx = Math.min((int) (dm.widthPixels * screenFraction), maxPx);
            window.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}
