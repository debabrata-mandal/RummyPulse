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
        gameAdapter = new DashboardGameAdapter();
        gameAdapter.setOnGameJoinListener(this);
        binding.recyclerViewGames.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewGames.setAdapter(gameAdapter);
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
        // Observe games count
        dashboardViewModel.getGamesCount().observe(getViewLifecycleOwner(), count -> {
            binding.textGamesCount.setText(count);
            binding.swipeRefresh.setRefreshing(false);
        });

        // Observe in-progress games
        dashboardViewModel.getInProgressGames().observe(getViewLifecycleOwner(), games -> {
            gameAdapter.setGameItems(games);
            
            // Show/hide empty state
            if (games == null || games.isEmpty()) {
                binding.emptyState.setVisibility(View.VISIBLE);
                binding.recyclerViewGames.setVisibility(View.GONE);
            } else {
                binding.emptyState.setVisibility(View.GONE);
                binding.recyclerViewGames.setVisibility(View.VISIBLE);
            }
            
            binding.swipeRefresh.setRefreshing(false);
            // Removed automatic "Games refreshed" toast - only show on manual refresh
        });

        // Observe new game creation
        dashboardViewModel.getNewGameCreated().observe(getViewLifecycleOwner(), gameId -> {
            if (gameId != null) {
                // Navigate to the newly created game with creator access
                Intent intent = new Intent(getContext(), JoinGameActivity.class);
                intent.putExtra("GAME_ID", gameId);
                intent.putExtra("IS_CREATOR", true); // Flag to indicate creator access
                startActivity(intent);
            }
        });
    }

    @Override
    public void onJoinGame(GameItem game, int position, String joinType) {
        String roleText = "moderator".equals(joinType) ? "Moderator" : "Player";
        String emoji = "moderator".equals(joinType) ? "🛡️" : "👤";
        
        com.example.rummypulse.utils.ModernToast.info(getContext(), emoji + " Joining game #" + game.getGameId() + " as " + roleText);
        
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
                editGstPercentage.setError("GST percentage is required");
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

                // Validate GST Percentage
                if (gstPercentage < 0) {
                    editGstPercentage.setError("GST percentage cannot be negative");
                    return;
                }

                if (gstPercentage > 100) {
                    editGstPercentage.setError("GST percentage cannot be more than 100%");
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
        binding = null;
    }
}
