package com.example.rummypulse.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.databinding.FragmentHomeBinding;
import com.example.rummypulse.data.AppUserManager;

import java.util.List;

public class HomeFragment extends Fragment implements TableAdapter.OnGameActionListener {

    private FragmentHomeBinding binding;
    private TableAdapter tableAdapter;
    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Check if user is admin before setting up the screen
        AppUserManager.getInstance().isCurrentUserAdmin(new AppUserManager.AdminCheckCallback() {
            @Override
            public void onResult(boolean isAdmin) {
                if (isAdmin) {
                    setupAdminView();
                } else {
                    setupLockedView();
                }
            }
        });

        return root;
    }

    private void setupAdminView() {
        // Setup RecyclerView for the table
        RecyclerView recyclerView = binding.recyclerViewTable;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        // Observe game data and update adapter
        homeViewModel.getGameItems().observe(getViewLifecycleOwner(), gameItems -> {
            tableAdapter = new TableAdapter(gameItems);
            tableAdapter.setOnGameActionListener(this);
            recyclerView.setAdapter(tableAdapter);
        });

        // Observe and update metric tiles
        homeViewModel.getApprovedGamesCount().observe(getViewLifecycleOwner(), approvedGames -> {
            binding.textApprovedGames.setText(String.valueOf(approvedGames));
        });

        homeViewModel.getCompletedGames().observe(getViewLifecycleOwner(), completedGames -> {
            binding.textCompletedGames.setText(String.valueOf(completedGames));
        });

        homeViewModel.getInProgressGames().observe(getViewLifecycleOwner(), inProgressGames -> {
            binding.textInProgressGames.setText(String.valueOf(inProgressGames));
        });

        // Observe total GST amount from approved games
        homeViewModel.getTotalGstAmount().observe(getViewLifecycleOwner(), totalGst -> {
            if (totalGst != null) {
                binding.textTotalGstAmount.setText("₹" + String.format("%.0f", totalGst));
            } else {
                binding.textTotalGstAmount.setText("₹0");
            }
        });

        // Setup swipe refresh
        binding.swipeRefresh.setOnRefreshListener(() -> {
            refreshGames();
        });
        
        // Set swipe refresh colors
        binding.swipeRefresh.setColorSchemeResources(
                com.example.rummypulse.R.color.accent_blue,
                com.example.rummypulse.R.color.accent_blue_dark,
                com.example.rummypulse.R.color.accent_blue_light
        );

        // Setup floating action button for refresh
        binding.btnRefresh.setOnClickListener(v -> refreshGames());

        // Observe approved games count for the "From X approved games" text
        homeViewModel.getApprovedGamesCount().observe(getViewLifecycleOwner(), approvedCount -> {
            if (approvedCount != null) {
                binding.textApprovedGamesCount.setText("From " + approvedCount + " approved games");
            } else {
                binding.textApprovedGamesCount.setText("From 0 approved games");
            }
        });

        // Observe errors
        homeViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        // Setup refresh button
        binding.btnRefresh.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Refreshing data...", Toast.LENGTH_SHORT).show();
            homeViewModel.refreshGames();
        });
        
        // Temporary test button to force a game to completed status
        binding.btnRefresh.setOnLongClickListener(v -> {
            Toast.makeText(getContext(), "Test: Setting first game to Completed status", Toast.LENGTH_SHORT).show();
            homeViewModel.setTestGameCompleted();
            return true;
        });
    }

    private void setupLockedView() {
        // Hide all admin-only content
        binding.recyclerViewTable.setVisibility(View.GONE);
        binding.swipeRefresh.setVisibility(View.GONE);
        binding.btnRefresh.setVisibility(View.GONE);
        
        // Show locked message
        TextView lockedMessage = new TextView(getContext());
        lockedMessage.setText("🔒 Access Restricted\n\nThis screen requires administrator privileges.\nPlease contact an admin for access.");
        lockedMessage.setTextSize(18);
        lockedMessage.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        lockedMessage.setPadding(32, 100, 32, 32);
        lockedMessage.setTextColor(getResources().getColor(com.example.rummypulse.R.color.text_secondary, null));
        
        // Add the locked message to the root layout
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
        // Check if game is completed
        if (!"Completed".equals(game.getGameStatus())) {
            Toast.makeText(getContext(), "Game must be completed before approval", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("Approve Game")
                .setMessage("Are you sure you want to approve this completed game? This will finalize the game and move it to the approved games list.")
                .setPositiveButton("Approve", (dialog, which) -> {
                    // Call the approve method
                    homeViewModel.approveGame(game);
                    Toast.makeText(getContext(), "✅ Game approved successfully!", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Do nothing
                })
                .show();
    }


    @Override
    public void onDeleteGame(GameItem game, int position) {
        // Show confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("Delete Game")
                .setMessage("Are you sure you want to delete this game? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Delete game from Firebase
                    homeViewModel.deleteGame(game.getGameId());
                    Toast.makeText(getContext(), "🗑️ Game deleted successfully!", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Do nothing
                })
                .show();
    }

    private void refreshGames() {
        Toast.makeText(getContext(), "Refreshing games...", Toast.LENGTH_LONG).show();
        homeViewModel.refreshGames();
        
        // Stop the refresh animation after a short delay
        binding.swipeRefresh.postDelayed(() -> {
            binding.swipeRefresh.setRefreshing(false);
            Toast.makeText(getContext(), "Games refreshed successfully!", Toast.LENGTH_LONG).show();
        }, 2000);
    }
}