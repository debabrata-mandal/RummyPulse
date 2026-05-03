package com.example.rummypulse.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.databinding.FragmentHomeBinding;
import com.example.rummypulse.data.AppUserRoleSession;

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
}
