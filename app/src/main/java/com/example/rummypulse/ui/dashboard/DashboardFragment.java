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
import android.view.inputmethod.EditorInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.rummypulse.JoinGameActivity;
import com.example.rummypulse.R;
import com.example.rummypulse.data.GameDefaults;
import com.example.rummypulse.data.GameDefaultsRepository;
import com.example.rummypulse.databinding.FragmentDashboardBinding;
import com.example.rummypulse.service.GroqGameNameService;
import com.example.rummypulse.ui.home.GameItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

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
    private Runnable openCreateDialogNetworkUpdater;

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
            dashboardViewModel.refreshCreatorDashboardRows();
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
                isNetworkAvailable = checkNetworkAvailable();
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        updateLiveStatusChip();
                        if (openCreateDialogNetworkUpdater != null) {
                            openCreateDialogNetworkUpdater.run();
                        }
                    });
                }
            }

            @Override
            public void onCapabilitiesChanged(
                    @NonNull Network network,
                    @NonNull NetworkCapabilities networkCapabilities) {
                isNetworkAvailable = checkNetworkAvailable();
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        updateLiveStatusChip();
                        if (openCreateDialogNetworkUpdater != null) {
                            openCreateDialogNetworkUpdater.run();
                        }
                    });
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                isNetworkAvailable = checkNetworkAvailable();
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        updateLiveStatusChip();
                        if (openCreateDialogNetworkUpdater != null) {
                            openCreateDialogNetworkUpdater.run();
                        }
                    });
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
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void updateLiveStatusChip() {
        if (binding == null) {
            return;
        }

        if (!isNetworkAvailable) {
            binding.textLiveStatus.setText("OFFLINE");
            binding.textLiveStatus.setBackgroundResource(R.drawable.bg_dashboard_status_offline);
            binding.textLiveStatus.setAlpha(1.0f);
            return;
        }

        if (!hasRealtimeData) {
            binding.textLiveStatus.setText("SYNCING");
            binding.textLiveStatus.setBackgroundResource(R.drawable.bg_dashboard_status_syncing);
            binding.textLiveStatus.setAlpha(1.0f);
            return;
        }

        binding.textLiveStatus.setText("LIVE");
        binding.textLiveStatus.setBackgroundResource(R.drawable.bg_dashboard_status_live);
        binding.textLiveStatus.setAlpha(1.0f);
    }

    @Override
    public void onJoinGame(GameItem game, int position, String joinType) {
        if (game.isViewAccessPending()) {
            com.example.rummypulse.utils.ViewAccessDialog.showPending(getContext(), null);
            return;
        }
        if (game.isViewAccessRejected()) {
            com.example.rummypulse.utils.ViewAccessDialog.showRejected(getContext(), null);
            return;
        }

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

        TextInputLayout layoutGameDisplayName = dialogView.findViewById(R.id.layout_game_display_name);
        TextInputEditText editGameDisplayName = dialogView.findViewById(R.id.edit_game_display_name);
        ImageButton btnGenerateGameName = dialogView.findViewById(R.id.btn_generate_game_name);
        TextInputLayout layoutPointValue = dialogView.findViewById(R.id.layout_point_value);
        TextInputEditText editPointValue = dialogView.findViewById(R.id.edit_point_value);
        ImageButton btnPointDecrement = dialogView.findViewById(R.id.btn_point_value_decrement);
        ImageButton btnPointIncrement = dialogView.findViewById(R.id.btn_point_value_increment);
        TextView textContributionPercentDisplay = dialogView.findViewById(R.id.text_contribution_percent_display);
        View layoutCreationStatus = dialogView.findViewById(R.id.layout_creation_status);
        View progressCreation = dialogView.findViewById(R.id.progress_creation);
        TextView textCreationStatus = dialogView.findViewById(R.id.text_creation_status);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnCreate = dialogView.findViewById(R.id.btn_create);

        CharSequence defaultGameNameHint = getString(R.string.dialog_game_name_hint);

        AlertDialog dialog = new AlertDialog.Builder(getContext(), R.style.DarkDialogTheme)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        final boolean[] nameGenerationInProgress = {false};
        final boolean[] retryMode = {false};
        final boolean[] successShown = {false};

        dashboardViewModel.resetGameCreationState();

        Runnable renderCreationAvailability = () -> {
            DashboardViewModel.GameCreationState state =
                    dashboardViewModel.getGameCreationState().getValue();
            DashboardViewModel.GameCreationStatus status = state != null
                    ? state.status
                    : DashboardViewModel.GameCreationStatus.IDLE;
            boolean active = status == DashboardViewModel.GameCreationStatus.CREATING
                    || status == DashboardViewModel.GameCreationStatus.RETRY_QUEUED;
            boolean slow = status == DashboardViewModel.GameCreationStatus.SLOW;
            boolean error = status == DashboardViewModel.GameCreationStatus.ERROR;
            retryMode[0] = slow || error;

            if (status == DashboardViewModel.GameCreationStatus.SUCCESS) {
                if (!successShown[0] && isAdded() && getContext() != null) {
                    successShown[0] = true;
                    com.example.rummypulse.utils.ModernToast.success(
                            getContext(), "Game created successfully.");
                    dialog.dismiss();
                }
                return;
            }

            if (active || slow || error) {
                layoutCreationStatus.setVisibility(View.VISIBLE);
                textCreationStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.text_secondary));
                textCreationStatus.setText(state != null && state.message != null
                        ? state.message
                        : "Creating game…");
                progressCreation.setVisibility(active ? View.VISIBLE : View.GONE);
            } else if (!isNetworkAvailable) {
                layoutCreationStatus.setVisibility(View.VISIBLE);
                progressCreation.setVisibility(View.GONE);
                textCreationStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.error_red));
                textCreationStatus.setText(R.string.dialog_create_game_offline);
            } else {
                layoutCreationStatus.setVisibility(View.GONE);
                textCreationStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.text_secondary));
            }

            btnCreate.setText(retryMode[0]
                    ? R.string.dialog_create_game_retry
                    : active
                    ? R.string.dialog_create_game_creating
                    : R.string.dialog_create_game_confirm);
            btnCreate.setEnabled(!active
                    && !nameGenerationInProgress[0]
                    && isNetworkAvailable);
            btnGenerateGameName.setEnabled(!active
                    && !nameGenerationInProgress[0]
                    && isNetworkAvailable
                    && GroqGameNameService.isConfigured());
            btnCancel.setEnabled(!dashboardViewModel.isGameCreationInProgress());
            dialog.setCancelable(!dashboardViewModel.isGameCreationInProgress());
            dialog.setCanceledOnTouchOutside(!dashboardViewModel.isGameCreationInProgress());
        };

        openCreateDialogNetworkUpdater = () -> {
            isNetworkAvailable = checkNetworkAvailable();
            renderCreationAvailability.run();
        };

        Observer<DashboardViewModel.GameCreationState> creationObserver =
                state -> renderCreationAvailability.run();
        dashboardViewModel.getGameCreationState().observe(
                getViewLifecycleOwner(), creationObserver);

        dialog.setOnDismissListener(ignored -> {
            dashboardViewModel.getGameCreationState().removeObserver(creationObserver);
            openCreateDialogNetworkUpdater = null;
            dashboardViewModel.resetGameCreationState();
        });

        btnCancel.setOnClickListener(v -> {
            if (!dashboardViewModel.isGameCreationInProgress()) {
                dialog.dismiss();
            }
        });

        btnGenerateGameName.setOnClickListener(v -> {
            isNetworkAvailable = checkNetworkAvailable();
            if (!isNetworkAvailable) {
                layoutCreationStatus.setVisibility(View.VISIBLE);
                progressCreation.setVisibility(View.GONE);
                textCreationStatus.setText(R.string.dialog_create_game_offline);
                renderCreationAvailability.run();
                return;
            }
            if (!GroqGameNameService.isConfigured()) {
                com.example.rummypulse.utils.ModernToast.error(getContext(),
                        getString(R.string.dialog_game_name_groq_missing));
                return;
            }
            nameGenerationInProgress[0] = true;
            btnGenerateGameName.setEnabled(false);
            layoutGameDisplayName.setHint(getString(R.string.dialog_game_name_generating));
            GroqGameNameService.suggestNameWithRetries(name -> {
                if (!isAdded() || getContext() == null) {
                    return;
                }
                layoutGameDisplayName.setHint(defaultGameNameHint);
                nameGenerationInProgress[0] = false;
                btnGenerateGameName.setEnabled(true);
                if (name != null && !name.isEmpty()) {
                    editGameDisplayName.setText(name);
                } else {
                    com.example.rummypulse.utils.ModernToast.error(getContext(),
                            getString(R.string.dialog_game_name_generate_failed));
                }
                renderCreationAvailability.run();
            });
        });

        if (GroqGameNameService.isConfigured() && isNetworkAvailable) {
            nameGenerationInProgress[0] = true;
            btnCreate.setEnabled(false);
            btnGenerateGameName.setEnabled(false);
            layoutGameDisplayName.setHint(getString(R.string.dialog_game_name_generating));
            GroqGameNameService.suggestNameWithRetries(name -> {
                if (!isAdded() || getContext() == null) {
                    return;
                }
                layoutGameDisplayName.setHint(defaultGameNameHint);
                nameGenerationInProgress[0] = false;
                if (name != null && !name.isEmpty()) {
                    editGameDisplayName.setText(name);
                }
                btnCreate.setEnabled(true);
                btnGenerateGameName.setEnabled(true);
                renderCreationAvailability.run();
            });
        } else {
            editGameDisplayName.setText("");
        }

        GameDefaultsRepository defaultsRepo = GameDefaultsRepository.getInstance(requireContext());
        GameDefaults gd = defaultsRepo.getCachedResolved();
        final double[] pointValueState = {clampPointValue(gd.getDefaultPointValue())};
        final double[] gstFromDefaults = {clampGstPercentForCreateGame(gd.getDefaultGstPercent())};

        Runnable refreshPointFieldUi = () -> {
            editPointValue.setText(formatPlainDecimalForField(pointValueState[0]));
            layoutPointValue.setError(null);
            btnPointDecrement.setEnabled(pointValueState[0] > 0.05 + 1e-9);
            btnPointIncrement.setEnabled(pointValueState[0] < 100.0 - 1e-9);
        };
        Runnable refreshContributionFieldUi = () -> {
            textContributionPercentDisplay.setText(
                    String.format(Locale.US, "%.0f%%", gstFromDefaults[0]));
        };

        editPointValue.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus || !isAdded()) {
                return;
            }
            if (!tryCommitPointField(layoutPointValue, editPointValue, pointValueState)) {
                editPointValue.setText(formatPlainDecimalForField(pointValueState[0]));
            }
            btnPointDecrement.setEnabled(pointValueState[0] > 0.05 + 1e-9);
            btnPointIncrement.setEnabled(pointValueState[0] < 100.0 - 1e-9);
        });
        editPointValue.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (tryCommitPointField(layoutPointValue, editPointValue, pointValueState)) {
                    editPointValue.clearFocus();
                }
                btnPointDecrement.setEnabled(pointValueState[0] > 0.05 + 1e-9);
                btnPointIncrement.setEnabled(pointValueState[0] < 100.0 - 1e-9);
                return true;
            }
            return false;
        });

        btnPointDecrement.setOnClickListener(v -> {
            pointValueState[0] = clampPointValue(pointValueState[0] - 0.05);
            refreshPointFieldUi.run();
        });
        btnPointIncrement.setOnClickListener(v -> {
            pointValueState[0] = clampPointValue(pointValueState[0] + 0.05);
            refreshPointFieldUi.run();
        });
        btnPointDecrement.setOnLongClickListener(v -> {
            pointValueState[0] = clampPointValue(pointValueState[0] - 1.0);
            refreshPointFieldUi.run();
            return true;
        });
        btnPointIncrement.setOnLongClickListener(v -> {
            pointValueState[0] = clampPointValue(pointValueState[0] + 1.0);
            refreshPointFieldUi.run();
            return true;
        });

        refreshPointFieldUi.run();
        refreshContributionFieldUi.run();

        btnCreate.setOnClickListener(v -> {
            isNetworkAvailable = checkNetworkAvailable();
            if (!isNetworkAvailable) {
                layoutCreationStatus.setVisibility(View.VISIBLE);
                progressCreation.setVisibility(View.GONE);
                textCreationStatus.setText(R.string.dialog_create_game_offline);
                renderCreationAvailability.run();
                return;
            }
            if (retryMode[0]) {
                dashboardViewModel.retryGameCreation();
                return;
            }
            if (!tryCommitPointField(layoutPointValue, editPointValue, pointValueState)) {
                return;
            }

            double pointValue = pointValueState[0];
            double gstPercentage = clampGstPercentForCreateGame(
                    defaultsRepo.getCachedResolved().getDefaultGstPercent());

            String displayName = editGameDisplayName.getText() != null
                    ? editGameDisplayName.getText().toString().trim()
                    : "";
            dashboardViewModel.createNewGame(pointValue, gstPercentage, displayName);
        });

        final double[] baselinePoint = {pointValueState[0]};

        dialog.show();
        renderCreationAvailability.run();

        Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics dm = requireContext().getResources().getDisplayMetrics();
            int maxPx = getResources().getDimensionPixelSize(R.dimen.dialog_create_game_max_width);
            int widthPx = Math.min((int) (dm.widthPixels * 0.92f), maxPx);
            window.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        defaultsRepo.refreshFromServer(() -> {
            if (!isAdded() || getContext() == null || !dialog.isShowing()) {
                return;
            }
            GameDefaults fresh = defaultsRepo.getCachedResolved();
            gstFromDefaults[0] = clampGstPercentForCreateGame(fresh.getDefaultGstPercent());
            refreshContributionFieldUi.run();

            boolean stillAtBaseline = Math.abs(pointValueState[0] - baselinePoint[0]) < 1e-9;
            if (!stillAtBaseline) {
                return;
            }
            pointValueState[0] = clampPointValue(fresh.getDefaultPointValue());
            baselinePoint[0] = pointValueState[0];
            refreshPointFieldUi.run();
        });
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
        openCreateDialogNetworkUpdater = null;
        binding = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getContext() != null) {
            GameDefaultsRepository.getInstance(requireContext()).refreshFromServer(() -> {
                if (!isAdded()) {
                    return;
                }
                if (gameAdapter != null) {
                    gameAdapter.notifyDataSetChanged();
                }
                if (completedGameAdapter != null) {
                    completedGameAdapter.notifyDataSetChanged();
                }
            });
        }
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

    private static String formatPlainDecimalForField(double v) {
        BigDecimal bd = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return bd.toPlainString();
    }

    private boolean tryCommitPointField(TextInputLayout layout, TextInputEditText edit, double[] state) {
        String s = edit.getText() != null ? edit.getText().toString().trim() : "";
        if (TextUtils.isEmpty(s)) {
            layout.setError(getString(R.string.dialog_point_value_required));
            return false;
        }
        try {
            double raw = Double.parseDouble(s);
            if (raw <= 0 || raw > 100) {
                layout.setError(getString(R.string.dialog_point_value_invalid));
                return false;
            }
            state[0] = clampPointValue(snapToFivePaise(raw));
            edit.setText(formatPlainDecimalForField(state[0]));
            layout.setError(null);
            return true;
        } catch (NumberFormatException e) {
            layout.setError(getString(R.string.dialog_point_value_invalid));
            return false;
        }
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

    private static double clampGstPercentForCreateGame(double v) {
        if (v < 0) {
            return 0;
        }
        if (v > 100) {
            return 100;
        }
        return v;
    }
}
