package com.example.rummypulse.ui.dashboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.rummypulse.JoinGameActivity;
import com.example.rummypulse.R;
import com.example.rummypulse.databinding.FragmentDashboardBinding;
import com.example.rummypulse.service.GroqGameNameService;
import com.example.rummypulse.ui.home.GameItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.textfield.TextInputEditText;

public class DashboardFragment extends Fragment implements DashboardGameAdapter.OnGameJoinListener {

    private FragmentDashboardBinding binding;
    private DashboardViewModel dashboardViewModel;
    private DashboardGameAdapter gameAdapter;
    private DashboardGameAdapter completedGameAdapter;
    private boolean isActiveExpanded = true;
    private boolean isCompletedExpanded = false;
    private boolean isNetworkAvailable = false;
    private boolean hasRealtimeData = false;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        binding.textDashboardTitle.setText(buildWelcomeTitle());

        setupRecyclerView();
        setupSwipeRefresh();
        setupCollapsibleSections();
        setupConnectivityMonitoring();
        setupCreateGameButton();
        observeViewModel();
        updateLiveStatusChip();
        
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
            hasRealtimeData = false;
            updateLiveStatusChip();
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
        binding.textActiveGamesHeader.setText("Active Games");
        binding.textCompletedGamesHeader.setText("Completed Games");

        // Observe in-progress games
        dashboardViewModel.getInProgressGames().observe(getViewLifecycleOwner(), games -> {
            gameAdapter.setGameItems(games);
            hasRealtimeData = true;
            int activeCount = games != null ? games.size() : 0;
            binding.textActiveGamesCount.setText(String.valueOf(activeCount));
            binding.textMetricActive.setText(String.valueOf(activeCount));
            binding.textActiveEmpty.setVisibility(activeCount == 0 ? View.VISIBLE : View.GONE);
            updateOverviewTotal();
            updateLiveStatusChip();
            updateEmptyStateVisibility();
            binding.swipeRefresh.setRefreshing(false);
        });

        // Observe completed games
        dashboardViewModel.getCompletedGames().observe(getViewLifecycleOwner(), completedGames -> {
            completedGameAdapter.setGameItems(completedGames);
            hasRealtimeData = true;
            int completedCount = completedGames != null ? completedGames.size() : 0;
            binding.textCompletedGamesCount.setText(String.valueOf(completedCount));
            binding.textMetricCompleted.setText(String.valueOf(completedCount));
            binding.textCompletedEmpty.setVisibility(completedCount == 0 ? View.VISIBLE : View.GONE);
            updateOverviewTotal();
            updateLiveStatusChip();

            // Update empty state visibility based on both active and completed games
            updateEmptyStateVisibility();
        });

        // Observe game creation event (only for games created by others)
        dashboardViewModel.getGameCreationEvent().observe(getViewLifecycleOwner(), gameCreationData -> {
            if (gameCreationData != null && getContext() != null) {
                android.util.Log.d("DashboardFragment", "📱 Received game creation event for game: " + 
                    gameCreationData.gameId + " by " + gameCreationData.creatorName);
                
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

    private void setupCollapsibleSections() {
        applySectionState(false);
        binding.activeHeaderRow.setOnClickListener(v -> {
            isActiveExpanded = !isActiveExpanded;
            applySectionState(true);
        });
        binding.completedHeaderRow.setOnClickListener(v -> {
            isCompletedExpanded = !isCompletedExpanded;
            applySectionState(true);
        });
    }

    private void updateOverviewTotal() {
        int active = gameAdapter != null ? gameAdapter.getItemCount() : 0;
        int completed = completedGameAdapter != null ? completedGameAdapter.getItemCount() : 0;
        binding.textMetricTotal.setText(String.valueOf(active + completed));
    }

    private String buildWelcomeTitle() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String displayName = user.getDisplayName();
            if (displayName != null && !displayName.trim().isEmpty()) {
                return "Welcome " + displayName.trim();
            }
            String email = user.getEmail();
            if (email != null && email.contains("@")) {
                return "Welcome " + email.substring(0, email.indexOf('@'));
            }
        }
        return "Welcome Player";
    }

    private void applySectionState(boolean animate) {
        if (animate) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(220);
            TransitionManager.beginDelayedTransition((ViewGroup) binding.getRoot(), transition);
        }

        binding.activeSectionContent.setVisibility(isActiveExpanded ? View.VISIBLE : View.GONE);
        binding.iconActiveExpand.setImageResource(isActiveExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);

        binding.completedSectionContent.setVisibility(isCompletedExpanded ? View.VISIBLE : View.GONE);
        binding.iconCompletedExpand.setImageResource(isCompletedExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
    }

    private void setupConnectivityMonitoring() {
        connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        isNetworkAvailable = checkNetworkAvailable();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                isNetworkAvailable = true;
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> updateLiveStatusChip());
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                isNetworkAvailable = checkNetworkAvailable();
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> updateLiveStatusChip());
                }
            }
        };
    }

    private boolean checkNetworkAvailable() {
        if (connectivityManager == null) {
            return false;
        }
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }

    private void updateLiveStatusChip() {
        if (binding == null) {
            return;
        }

        if (!isNetworkAvailable) {
            binding.textLiveStatus.setText("OFFLINE");
            binding.textLiveStatus.setAlpha(0.85f);
            return;
        }

        if (!hasRealtimeData) {
            binding.textLiveStatus.setText("SYNCING");
            binding.textLiveStatus.setAlpha(0.95f);
            return;
        }

        binding.textLiveStatus.setText("LIVE");
        binding.textLiveStatus.setAlpha(1.0f);
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

                if (GroqGameNameService.isConfigured()) {
                    btnCreate.setEnabled(false);
                    com.example.rummypulse.utils.ModernToast.progress(getContext(), "Creating game…");
                    GroqGameNameService.suggestNameWithRetries(displayName -> {
                        if (!isAdded() || getContext() == null) {
                            return;
                        }
                        btnCreate.setEnabled(true);
                        dashboardViewModel.createNewGame(pointValue, gstPercentage, displayName);
                        dialog.dismiss();
                        com.example.rummypulse.utils.ModernToast.success(getContext(),
                                "🎮 Creating new game with you as Player 1...");
                    });
                } else {
                    dashboardViewModel.createNewGame(pointValue, gstPercentage, "");
                    dialog.dismiss();
                    com.example.rummypulse.utils.ModernToast.success(getContext(),
                            "🎮 Creating new game with you as Player 1...");
                }

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
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
                // No-op: callback may not be registered.
            }
        }
        binding = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (connectivityManager != null && networkCallback != null) {
            try {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                connectivityManager.registerNetworkCallback(request, networkCallback);
            } catch (Exception ignored) {
                // Keep last known status if callback registration fails.
            }
        }
        isNetworkAvailable = checkNetworkAvailable();
        updateLiveStatusChip();
    }
}
