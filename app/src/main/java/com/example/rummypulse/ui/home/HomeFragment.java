package com.example.rummypulse.ui.home;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.example.rummypulse.databinding.FragmentHomeBinding;
import com.example.rummypulse.data.AppUserRoleSession;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class HomeFragment extends Fragment implements TableAdapter.OnGameActionListener {

    private static final int LOCKED_OVERLAY_VIEW_ID = View.generateViewId();

    private FragmentHomeBinding binding;
    private TableAdapter tableAdapter;
    private HomeViewModel homeViewModel;
    private boolean adminViewConfigured;
    private boolean lockedViewConfigured;
    private boolean updatingSelectionControls;
    private boolean reviewOperationInProgress;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        AppUserRoleSession.getInstance().getRole().observe(getViewLifecycleOwner(), new Observer<AppUserRoleSession.Role>() {
            @Override
            public void onChanged(AppUserRoleSession.Role role) {
                if (!isAdded() || binding == null) {
                    return;
                }
                if (role == AppUserRoleSession.Role.UNKNOWN) {
                    showAccessLoading();
                    return;
                }
                hideAccessLoading();
                if (role == AppUserRoleSession.Role.ADMIN) {
                    removeLockedOverlayIfPresent();
                    if (!adminViewConfigured) {
                        setupAdminView();
                        adminViewConfigured = true;
                    }
                } else {
                    if (!lockedViewConfigured) {
                        setupLockedView();
                        lockedViewConfigured = true;
                    }
                }
            }
        });

        return root;
    }

    private void showAccessLoading() {
        binding.reviewAccessLoading.setVisibility(View.VISIBLE);
        binding.swipeRefresh.setAlpha(0.35f);
    }

    private void hideAccessLoading() {
        binding.reviewAccessLoading.setVisibility(View.GONE);
        binding.swipeRefresh.setAlpha(1f);
    }

    private void removeLockedOverlayIfPresent() {
        if (binding == null || !(binding.getRoot() instanceof ViewGroup)) {
            return;
        }
        ViewGroup coordinator = (ViewGroup) binding.getRoot();
        View overlay = coordinator.findViewById(LOCKED_OVERLAY_VIEW_ID);
        if (overlay != null) {
            coordinator.removeView(overlay);
        }
    }

    private void setupAdminView() {
        RecyclerView recyclerView = binding.recyclerViewTable;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        homeViewModel.getGameItems().observe(getViewLifecycleOwner(), gameItems -> {
            if (tableAdapter == null) {
                tableAdapter = new TableAdapter(gameItems);
                tableAdapter.setOnGameActionListener(this);
                tableAdapter.setOnSelectionChangedListener(this::updateSelectionControls);
                recyclerView.setAdapter(tableAdapter);
            } else {
                tableAdapter.submitItems(gameItems);
            }
            binding.reviewBulkActions.setVisibility(
                    tableAdapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
        });

        homeViewModel.getCompletedGames().observe(getViewLifecycleOwner(), completedGames -> {
            int c = completedGames != null ? completedGames : 0;
            binding.textCompletedGames.setText(String.valueOf(c));
            binding.btnApproveAll.setEnabled(c > 0);
        });

        homeViewModel.getInProgressGames().observe(getViewLifecycleOwner(), inProgressGames -> {
            binding.textInProgressGames.setText(String.valueOf(inProgressGames));
        });

        binding.swipeRefresh.setEnabled(false);
        binding.btnRefresh.setOnClickListener(v -> refreshGames());
        binding.btnApproveAll.setOnClickListener(v -> onApproveAllClicked());
        binding.checkboxSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updatingSelectionControls && tableAdapter != null) {
                tableAdapter.selectAll(isChecked);
            }
        });
        binding.btnDeleteSelected.setOnClickListener(v -> onDeleteSelectedClicked());

        homeViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                if (reviewOperationInProgress) {
                    endReviewOperation();
                }
                com.example.rummypulse.utils.ModernToast.error(getContext(), error);
            }
        });
    }

    private void beginReviewOperation(String message) {
        if (binding == null) {
            return;
        }
        reviewOperationInProgress = true;
        binding.reviewOperationLoading.setVisibility(View.VISIBLE);
        binding.textReviewOperationMessage.setText(message);
        binding.swipeRefresh.setAlpha(0.35f);
        setReviewControlsEnabled(false);
    }

    private void endReviewOperation() {
        if (binding == null) {
            return;
        }
        reviewOperationInProgress = false;
        binding.reviewOperationLoading.setVisibility(View.GONE);
        binding.swipeRefresh.setAlpha(1f);
        setReviewControlsEnabled(true);
    }

    private void setReviewControlsEnabled(boolean enabled) {
        if (binding == null) {
            return;
        }
        Integer completedGames = homeViewModel.getCompletedGames().getValue();
        int completed = completedGames != null ? completedGames : 0;
        binding.btnApproveAll.setEnabled(enabled && completed > 0);
        binding.btnRefresh.setEnabled(enabled);
        if (tableAdapter != null) {
            binding.checkboxSelectAll.setEnabled(
                    enabled && tableAdapter.getItemCount() > 0);
            tableAdapter.setActionsEnabled(enabled);
            int selectedCount = tableAdapter.getSelectedGameIds().size();
            int itemCount = tableAdapter.getItemCount();
            updateSelectionControls(
                    selectedCount,
                    itemCount > 0 && selectedCount == itemCount);
        } else {
            binding.checkboxSelectAll.setEnabled(false);
            binding.btnDeleteSelected.setEnabled(false);
        }
    }

    @Override
    public void onEditGameEconomics(GameItem game, int position) {
        if (!isAdded() || getContext() == null || game == null) {
            return;
        }

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_game_economics, null);
        TextInputLayout layoutPoint = dialogView.findViewById(R.id.layout_edit_review_point_value);
        TextInputEditText editPoint = dialogView.findViewById(R.id.edit_review_point_value);
        TextInputLayout layoutContribution = dialogView.findViewById(R.id.layout_edit_review_contribution);
        TextInputEditText editContribution = dialogView.findViewById(R.id.edit_review_contribution);
        com.google.android.material.button.MaterialButton cancel =
                dialogView.findViewById(R.id.btn_edit_economics_cancel);
        com.google.android.material.button.MaterialButton save =
                dialogView.findViewById(R.id.btn_edit_economics_save);

        String pv = game.getPointValue();
        if (pv == null || pv.isEmpty()) {
            editPoint.setText("");
        } else {
            editPoint.setText(formatPlainDecimalForField(parsePointValueForDisplay(pv)));
        }
        String gst = game.getGstPercentage();
        if (gst == null) {
            gst = "";
        }
        editContribution.setText(gst.replace("%", "").trim());

        AlertDialog dialog = new AlertDialog.Builder(getContext(), R.style.DarkDialogTheme)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            Double point = parseAndClampPointValue(layoutPoint, editPoint);
            Integer contrib = parseContributionPercent(layoutContribution, editContribution);
            if (point == null || contrib == null) {
                return;
            }
            homeViewModel.updateGameEconomics(game.getGameId(), point, contrib, () -> {
                if (isAdded() && getContext() != null) {
                    com.example.rummypulse.utils.ModernToast.success(getContext(),
                            getString(R.string.review_edit_economics_saved));
                }
            });
            dialog.dismiss();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
            android.util.DisplayMetrics dm =
                    getResources().getDisplayMetrics();
            int maxWidth = Math.round(420 * dm.density);
            int width = Math.min(Math.round(dm.widthPixels * 0.92f), maxWidth);
            dialog.getWindow().setLayout(
                    width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void setupLockedView() {
        binding.recyclerViewTable.setVisibility(View.GONE);
        binding.swipeRefresh.setVisibility(View.GONE);

        TextView lockedMessage = new TextView(getContext());
        lockedMessage.setId(LOCKED_OVERLAY_VIEW_ID);
        lockedMessage.setText(getString(R.string.review_access_restricted)
                + "\n\n"
                + getString(R.string.review_access_restricted_detail));
        lockedMessage.setTextSize(15);
        lockedMessage.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        lockedMessage.setLineSpacing(4f, 1f);
        lockedMessage.setPadding(32, 100, 32, 32);
        lockedMessage.setTextColor(getResources().getColor(com.example.rummypulse.R.color.text_secondary, null));

        if (binding.getRoot() instanceof ViewGroup) {
            ((ViewGroup) binding.getRoot()).addView(lockedMessage);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onApproveGst(GameItem game, int position) {
        if (!"Completed".equals(game.getGameStatus())) {
            com.example.rummypulse.utils.ModernToast.warning(getContext(), "Game must be completed before approval");
            return;
        }

        showReviewActionDialog(
                R.drawable.ic_approve,
                "Approve game?",
                "Finalize this completed game",
                "The game will move to the approved list and its results will be finalized.",
                "Approve",
                false,
                () -> {
                    beginReviewOperation(getString(R.string.review_operation_approving_one));
                    homeViewModel.approveGame(game, () -> {
                        if (!isAdded() || getContext() == null) {
                            return;
                        }
                        endReviewOperation();
                        com.example.rummypulse.utils.ModernToast.success(
                                getContext(), "✅ Game approved successfully!");
                    });
                });
    }


    @Override
    public void onDeleteGame(GameItem game, int position) {
        showReviewActionDialog(
                R.drawable.ic_delete,
                "Delete game?",
                "Permanently remove this game",
                "This action cannot be undone. The game and its recorded results will be removed.",
                "Delete game",
                true,
                () -> {
                    beginReviewOperation(getString(R.string.review_delete_selected_progress, 1));
                    homeViewModel.deleteGame(
                            game.getGameId(),
                            () -> {
                                if (!isAdded() || getContext() == null) {
                                    return;
                                }
                                endReviewOperation();
                                com.example.rummypulse.utils.ModernToast.success(
                                        getContext(),
                                        getString(R.string.review_delete_success_one));
                            },
                            error -> {
                                if (!isAdded()) {
                                    return;
                                }
                                endReviewOperation();
                            });
                });
    }

    private void updateSelectionControls(int selectedCount, boolean allSelected) {
        if (binding == null || tableAdapter == null) {
            return;
        }
        updatingSelectionControls = true;
        binding.checkboxSelectAll.setChecked(allSelected);
        updatingSelectionControls = false;
        binding.checkboxSelectAll.setEnabled(
                !reviewOperationInProgress && tableAdapter.getItemCount() > 0);
        binding.textSelectedGames.setText(selectedCount == 0
                ? getString(R.string.review_selected_none)
                : getString(R.string.review_selected_count, selectedCount));
        binding.btnDeleteSelected.setEnabled(
                !reviewOperationInProgress && selectedCount > 0);
    }

    private void onDeleteSelectedClicked() {
        if (tableAdapter == null || reviewOperationInProgress || getContext() == null) {
            return;
        }
        List<String> selectedGameIds = tableAdapter.getSelectedGameIds();
        int selectedCount = selectedGameIds.size();
        if (selectedCount == 0) {
            return;
        }

        showReviewActionDialog(
                R.drawable.ic_delete,
                getString(R.string.review_delete_selected_title),
                selectedCount == 1
                        ? "Remove 1 selected game"
                        : "Remove " + selectedCount + " selected games",
                getString(R.string.review_delete_selected_message, selectedCount),
                getString(R.string.review_delete_selected),
                true,
                () -> {
                    beginReviewOperation(
                            getString(R.string.review_delete_selected_progress, selectedCount));
                    homeViewModel.deleteGames(
                            selectedGameIds,
                            () -> {
                                if (!isAdded() || binding == null || getContext() == null) {
                                    return;
                                }
                                tableAdapter.clearSelection();
                                endReviewOperation();
                                com.example.rummypulse.utils.ModernToast.success(
                                        getContext(),
                                        selectedCount == 1
                                                ? getString(R.string.review_delete_selected_success_one)
                                                : getString(
                                                        R.string.review_delete_selected_success_many,
                                                        selectedCount));
                            },
                            error -> {
                                if (!isAdded() || binding == null || tableAdapter == null) {
                                    return;
                                }
                                endReviewOperation();
                                updateSelectionControls(
                                        tableAdapter.getSelectedGameIds().size(),
                                        false);
                            });
                });
    }

    private void onApproveAllClicked() {
        List<GameItem> items = homeViewModel.getGameItems().getValue();
        if (items == null) {
            return;
        }
        int count = 0;
        for (GameItem g : items) {
            if (g != null && g.isCompleted()) {
                count++;
            }
        }
        if (count == 0) {
            com.example.rummypulse.utils.ModernToast.warning(getContext(), "No completed games to approve");
            return;
        }
        final int approvedCount = count;
        showReviewActionDialog(
                R.drawable.ic_approve,
                "Approve all completed games?",
                approvedCount == 1
                        ? "Finalize 1 completed game"
                        : "Finalize " + approvedCount + " completed games",
                "Each game will move to the approved list and its results will be finalized.",
                "Approve all",
                false,
                () -> {
                    beginReviewOperation(getString(
                            R.string.review_operation_approving_many, approvedCount));
                    homeViewModel.approveAllCompletedGames(items, () -> {
                        if (!isAdded() || getContext() == null) {
                            return;
                        }
                        endReviewOperation();
                        com.example.rummypulse.utils.ModernToast.success(getContext(),
                                approvedCount == 1 ? "1 game approved." : approvedCount + " games approved.");
                    });
                });
    }

    private void showReviewActionDialog(
            int iconRes,
            CharSequence title,
            CharSequence subtitle,
            CharSequence message,
            CharSequence confirmText,
            boolean destructive,
            Runnable action) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        View view = LayoutInflater.from(getContext()).inflate(
                R.layout.dialog_action_confirmation, null, false);
        android.widget.ImageView icon =
                view.findViewById(R.id.image_action_dialog_icon);
        TextView titleView = view.findViewById(R.id.text_action_dialog_title);
        TextView subtitleView = view.findViewById(R.id.text_action_dialog_subtitle);
        TextView messageView = view.findViewById(R.id.text_action_dialog_message);
        com.google.android.material.card.MaterialCardView messageCard =
                view.findViewById(R.id.card_action_dialog_message);
        com.google.android.material.button.MaterialButton cancel =
                view.findViewById(R.id.btn_action_dialog_cancel);
        com.google.android.material.button.MaterialButton confirm =
                view.findViewById(R.id.btn_action_dialog_confirm);

        icon.setImageResource(iconRes);
        titleView.setText(title);
        subtitleView.setText(subtitle);
        messageView.setText(message);
        confirm.setText(confirmText);
        if (destructive) {
            int red = androidx.core.content.ContextCompat.getColor(
                    requireContext(), R.color.error_red);
            icon.setBackgroundResource(
                    R.drawable.view_access_icon_rejected_background);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(red));
            messageView.setCompoundDrawableTintList(
                    android.content.res.ColorStateList.valueOf(red));
            messageCard.setStrokeColor(red);
            confirm.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(red));
        }

        AlertDialog dialog = new AlertDialog.Builder(
                requireContext(), R.style.DarkDialogTheme)
                .setView(view)
                .setCancelable(true)
                .create();
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
            android.util.DisplayMetrics dm =
                    getResources().getDisplayMetrics();
            int maxWidth = Math.round(420 * dm.density);
            int width = Math.min(Math.round(dm.widthPixels * 0.92f), maxWidth);
            dialog.getWindow().setLayout(
                    width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void refreshGames() {
        com.example.rummypulse.utils.ModernToast.progress(getContext(), "Refreshing games...");
        homeViewModel.refreshGames();

        binding.btnRefresh.postDelayed(() -> {
            if (isAdded() && getContext() != null) {
                com.example.rummypulse.utils.ModernToast.success(getContext(), "Games refreshed successfully!");
            }
        }, 1500);
    }

    private static String formatPlainDecimalForField(double v) {
        BigDecimal bd = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return bd.toPlainString();
    }

    private static double snapToFivePaise(double value) {
        return Math.round(value * 20.0) / 20.0;
    }

    private static double clampPointValue(double value) {
        double s = snapToFivePaise(value);
        if (s < 0.05) {
            return 0.05;
        }
        if (s > 100.0) {
            return 100.0;
        }
        return s;
    }

    private static double parsePointValueForDisplay(String pointValueStr) {
        if (pointValueStr == null || pointValueStr.isEmpty()) {
            return 0.05;
        }
        try {
            return Double.parseDouble(pointValueStr.replace("₹", "").trim());
        } catch (NumberFormatException e) {
            return 0.05;
        }
    }

    private Double parseAndClampPointValue(TextInputLayout layout, TextInputEditText edit) {
        String s = edit.getText() != null ? edit.getText().toString().trim() : "";
        if (TextUtils.isEmpty(s)) {
            layout.setError(getString(R.string.dialog_point_value_required));
            return null;
        }
        try {
            double raw = Double.parseDouble(s);
            if (raw <= 0 || raw > 100) {
                layout.setError(getString(R.string.dialog_point_value_invalid));
                return null;
            }
            double clamped = clampPointValue(raw);
            layout.setError(null);
            return clamped;
        } catch (NumberFormatException e) {
            layout.setError(getString(R.string.dialog_point_value_invalid));
            return null;
        }
    }

    private Integer parseContributionPercent(TextInputLayout layout, TextInputEditText edit) {
        String s = edit.getText() != null ? edit.getText().toString().trim() : "";
        if (TextUtils.isEmpty(s)) {
            layout.setError(getString(R.string.dialog_contribution_required));
            return null;
        }
        try {
            int value = Integer.parseInt(s);
            if (value < 0 || value > 100) {
                layout.setError(getString(R.string.dialog_contribution_invalid));
                return null;
            }
            layout.setError(null);
            return value;
        } catch (NumberFormatException e) {
            layout.setError(getString(R.string.dialog_contribution_invalid));
            return null;
        }
    }
}
