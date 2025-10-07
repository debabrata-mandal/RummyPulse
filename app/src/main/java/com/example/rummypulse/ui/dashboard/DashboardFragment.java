package com.example.rummypulse.ui.dashboard;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.rummypulse.JoinGameActivity;
import com.example.rummypulse.R;
import com.example.rummypulse.databinding.FragmentDashboardBinding;
import com.example.rummypulse.ui.home.GameItem;
import com.google.android.material.textfield.TextInputEditText;

public class DashboardFragment extends Fragment implements DashboardGameAdapter.OnGameJoinListener {

    private FragmentDashboardBinding binding;
    private DashboardViewModel dashboardViewModel;
    private DashboardGameAdapter gameAdapter;
    private DashboardGameAdapter completedGameAdapter;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        setupRecyclerView();
        setupSwipeRefresh();
        setupCreateGameButton();
        observeViewModel();
        
        return root;
    }

    private void setupRecyclerView() {
        // Setup active games adapter with custom layout manager that doesn't recycle views
        gameAdapter = new DashboardGameAdapter();
        gameAdapter.setOnGameJoinListener(this);
        
        // Use a custom LinearLayoutManager that properly measures all items
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext()) {
            @Override
            public boolean canScrollVertically() {
                return false; // Disable vertical scrolling in RecyclerView since parent ScrollView handles it
            }
        };
        binding.recyclerViewGames.setLayoutManager(layoutManager);
        binding.recyclerViewGames.setAdapter(gameAdapter);
        
        // Setup completed games adapter with custom layout manager
        completedGameAdapter = new DashboardGameAdapter();
        completedGameAdapter.setIsCompletedGamesAdapter(true);
        completedGameAdapter.setOnGameJoinListener(this);
        
        LinearLayoutManager completedLayoutManager = new LinearLayoutManager(getContext()) {
            @Override
            public boolean canScrollVertically() {
                return false; // Disable vertical scrolling in RecyclerView since parent ScrollView handles it
            }
        };
        binding.recyclerViewCompletedGames.setLayoutManager(completedLayoutManager);
        binding.recyclerViewCompletedGames.setAdapter(completedGameAdapter);
    }

    private void setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener(() -> {
            com.example.rummypulse.utils.ModernToast.progress(getContext(), "🔄 Refreshing active games...");
            dashboardViewModel.loadGames();
        });
        
        // Set swipe refresh colors
        binding.swipeRefresh.setColorSchemeResources(
                com.example.rummypulse.R.color.accent_blue,
                com.example.rummypulse.R.color.accent_blue_dark,
                com.example.rummypulse.R.color.accent_blue_light
        );
    }

    private void observeViewModel() {
        // Observe active games count
        dashboardViewModel.getActiveGamesCount().observe(getViewLifecycleOwner(), count -> {
            binding.textActiveGamesHeader.setText(count);
        });

        // Observe completed games count
        dashboardViewModel.getCompletedGamesCount().observe(getViewLifecycleOwner(), count -> {
            binding.textCompletedGamesHeader.setText(count);
        });

        // Observe in-progress games
        dashboardViewModel.getInProgressGames().observe(getViewLifecycleOwner(), games -> {
            gameAdapter.setGameItems(games);
            updateEmptyStateVisibility();
            binding.swipeRefresh.setRefreshing(false);
        });

        // Observe completed games
        dashboardViewModel.getCompletedGames().observe(getViewLifecycleOwner(), completedGames -> {
            completedGameAdapter.setGameItems(completedGames);
            
            // Show/hide completed games section
            if (completedGames != null && !completedGames.isEmpty()) {
                binding.completedGamesSection.setVisibility(View.VISIBLE);
            } else {
                binding.completedGamesSection.setVisibility(View.GONE);
            }
            
            // Update empty state visibility based on both active and completed games
            updateEmptyStateVisibility();
        });

        // Observe game creation event for notifications (only for games created by others)
        dashboardViewModel.getGameCreationEvent().observe(getViewLifecycleOwner(), gameCreationData -> {
            if (gameCreationData != null && getContext() != null) {
                // Show notification for games created by other users
                com.example.rummypulse.utils.NotificationHelper.showGameCreatedNotification(
                    getContext(), 
                    gameCreationData.gameId, 
                    gameCreationData.creatorName,
                    gameCreationData.pointValue
                );
                dashboardViewModel.clearGameCreationEvent();
            }
        });
        
        // Observe new game creation
        dashboardViewModel.getNewGameCreated().observe(getViewLifecycleOwner(), gameId -> {
            if (gameId != null) {
                // Navigate to the newly created game with creator access
                Intent intent = new Intent(getContext(), JoinGameActivity.class);
                intent.putExtra("GAME_ID", gameId);
                intent.putExtra("IS_CREATOR", true); // Flag to indicate creator access
                startActivity(intent);
                
                // Clear the value to prevent re-navigation when returning to dashboard
                dashboardViewModel.clearNewGameCreated();
            }
        });
    }

    private void updateEmptyStateVisibility() {
        boolean hasActiveGames = gameAdapter.getItemCount() > 0;
        boolean hasCompletedGames = completedGameAdapter.getItemCount() > 0;
        
        if (!hasActiveGames && !hasCompletedGames) {
            binding.emptyState.setVisibility(View.VISIBLE);
        } else {
            binding.emptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onJoinGame(GameItem game, int position, String joinType) {
        if ("view".equals(joinType)) {
            com.example.rummypulse.utils.ModernToast.info(getContext(), "👁️ Viewing completed game #" + game.getGameId());
        } else {
            String roleText = "moderator".equals(joinType) ? "Moderator" : "Player";
            String emoji = "moderator".equals(joinType) ? "🛡️" : "👤";
            com.example.rummypulse.utils.ModernToast.info(getContext(), emoji + " Joining game #" + game.getGameId() + " as " + roleText);
        }
        
        // Navigate to JoinGameActivity with the game ID
        Intent intent = new Intent(getContext(), JoinGameActivity.class);
        intent.putExtra("GAME_ID", game.getGameId());
        intent.putExtra("JOIN_TYPE", joinType);
        startActivity(intent);
    }

    private void setupCreateGameButton() {
        binding.fabCreateGame.setOnClickListener(v -> showCreateGameDialog());
    }

    private void showCreateGameDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_create_game, null);
        
        TextInputEditText editPointValue = dialogView.findViewById(R.id.edit_point_value);
        TextInputEditText editGstPercentage = dialogView.findViewById(R.id.edit_gst_percentage);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnCreate = dialogView.findViewById(R.id.btn_create);

        AlertDialog dialog = new AlertDialog.Builder(getContext(), R.style.DarkDialogTheme)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnCreate.setOnClickListener(v -> {
            String pointValueStr = editPointValue.getText().toString().trim();
            String gstPercentageStr = editGstPercentage.getText().toString().trim();

            if (TextUtils.isEmpty(pointValueStr)) {
                editPointValue.setError("Point value is required");
                return;
            }

            if (TextUtils.isEmpty(gstPercentageStr)) {
                editGstPercentage.setError("Contribution percentage is required");
                return;
            }

            try {
                double pointValue = Double.parseDouble(pointValueStr);
                double gstPercentage = Double.parseDouble(gstPercentageStr);

                // Validate Point Value
                if (pointValue < 0) {
                    editPointValue.setError("Point value cannot be negative");
                    return;
                }
                
                if (pointValue == 0) {
                    editPointValue.setError("Point value must be greater than 0");
                    return;
                }

                if (pointValue > 100) {
                    editPointValue.setError("Point value cannot be more than ₹100");
                    return;
                }

                // Validate Contribution Percentage
                if (gstPercentage < 0) {
                    editGstPercentage.setError("Contribution percentage cannot be negative");
                    return;
                }

                if (gstPercentage > 100) {
                    editGstPercentage.setError("Contribution percentage cannot be more than 100%");
                    return;
                }

                // Create the game
                dashboardViewModel.createNewGame(pointValue, gstPercentage);
                dialog.dismiss();
                
                com.example.rummypulse.utils.ModernToast.success(getContext(), "🎮 Creating new game with you as Player 1...");

            } catch (NumberFormatException e) {
                com.example.rummypulse.utils.ModernToast.error(getContext(), "Please enter valid numbers");
            }
        });

        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Stop time updates when fragment is destroyed
        if (gameAdapter != null) {
            gameAdapter.stopTimeUpdates();
        }
        if (completedGameAdapter != null) {
            completedGameAdapter.stopTimeUpdates();
        }
        binding = null;
    }
}
