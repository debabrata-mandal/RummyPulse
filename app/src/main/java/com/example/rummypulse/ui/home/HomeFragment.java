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
            tableAdapter = new TableAdapter(gameItems);
            tableAdapter.setOnGameActionListener(this);
            recyclerView.setAdapter(tableAdapter);
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

        homeViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                com.example.rummypulse.utils.ModernToast.error(getContext(), error);
            }
        });
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
                .setTitle(R.string.review_edit_economics_title)
                .setView(dialogView)
                .setPositiveButton(R.string.review_edit_economics_save, null)
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
                })
                .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
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
        });

        dialog.show();
    }

    private void setupLockedView() {
        binding.recyclerViewTable.setVisibility(View.GONE);
        binding.swipeRefresh.setVisibility(View.GONE);
        binding.btnRefresh.setVisibility(View.GONE);
        binding.btnApproveAll.setVisibility(View.GONE);

        TextView lockedMessage = new TextView(getContext());
        lockedMessage.setId(LOCKED_OVERLAY_VIEW_ID);
        lockedMessage.setText("🔒 Access Restricted\n\nThis screen requires administrator privileges.\nPlease contact an admin for access.");
        lockedMessage.setTextSize(18);
        lockedMessage.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
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

        new androidx.appcompat.app.AlertDialog.Builder(getContext(), com.example.rummypulse.R.style.DarkDialogTheme)
                .setTitle("Approve Game")
                .setMessage("Are you sure you want to approve this completed game? This will finalize the game and move it to the approved games list.")
                .setPositiveButton("Approve", (dialog, which) -> {
                    homeViewModel.approveGame(game);
                    com.example.rummypulse.utils.ModernToast.success(getContext(), "✅ Game approved successfully!");
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                })
                .show();
    }


    @Override
    public void onDeleteGame(GameItem game, int position) {
        new androidx.appcompat.app.AlertDialog.Builder(getContext(), com.example.rummypulse.R.style.DarkDialogTheme)
                .setTitle("Delete Game")
                .setMessage("Are you sure you want to delete this game? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    homeViewModel.deleteGame(game.getGameId());
                    com.example.rummypulse.utils.ModernToast.success(getContext(), "🗑️ Game deleted successfully!");
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                })
                .show();
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
        new androidx.appcompat.app.AlertDialog.Builder(getContext(), com.example.rummypulse.R.style.DarkDialogTheme)
                .setTitle("Approve all completed games")
                .setMessage("Approve " + approvedCount + " completed game(s)? Each will be finalized and moved to the approved games list.")
                .setPositiveButton("Approve all", (dialog, which) -> {
                    homeViewModel.approveAllCompletedGames(items, () -> {
                        if (!isAdded() || getContext() == null) {
                            return;
                        }
                        com.example.rummypulse.utils.ModernToast.success(getContext(),
                                approvedCount == 1 ? "1 game approved." : approvedCount + " games approved.");
                    });
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                })
                .show();
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
