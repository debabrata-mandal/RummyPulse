package com.example.rummypulse;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.example.rummypulse.databinding.ActivityJoinGameBinding;
import com.example.rummypulse.ui.join.JoinGameViewModel;
import com.example.rummypulse.utils.ModernToast;
import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class JoinGameActivity extends AppCompatActivity {

    private JoinGameViewModel viewModel;
    private ActivityJoinGameBinding binding;
    private String currentGamePin;
    private String currentGameId;
    
    // Track previous ranks for animation
    private java.util.Map<String, Integer> previousRanks = new java.util.HashMap<>();
    
    // Firestore listener for real-time updates
    private com.google.firebase.firestore.ListenerRegistration gameDataListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize ViewBinding
        binding = ActivityJoinGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(JoinGameViewModel.class);

        // Initialize views
        initializeViews();
        setupClickListeners();
        observeViewModel();

        // Get game ID from intent if available
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("GAME_ID")) {
            String gameId = intent.getStringExtra("GAME_ID");
            String joinType = intent.getStringExtra("JOIN_TYPE");
            boolean isCreator = intent.getBooleanExtra("IS_CREATOR", false);
            
            if (gameId != null) {
                currentGameId = gameId; // Store the game ID
                
                // Keep toolbar title as "Game View" only
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("Game View");
                }
                
                // If creator, automatically grant edit access
                if (isCreator) {
                    viewModel.grantEditAccess();
                    // The menu will be hidden when the observer triggers
                    ModernToast.success(this, "🎮 Welcome to your new game!");
                }
                
                // Automatically join the game
                joinGame(gameId);
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up Firestore listener to prevent memory leaks
        if (gameDataListener != null) {
            gameDataListener.remove();
            gameDataListener = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_join_game, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        // Hide menu if user already has edit access
        Boolean editAccess = viewModel.getEditAccessGranted().getValue();
        if (editAccess != null && editAccess) {
            menu.clear(); // Remove all menu items when in edit mode
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_edit_access) {
            requestEditAccess();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeViews() {
        // Initially hide the cards and FAB
        binding.playersSection.setVisibility(View.GONE);
        binding.standingsCard.setVisibility(View.GONE);
        binding.btnAddPlayer.setVisibility(View.GONE); // Hide FAB in view mode
    }
    
    private void showLoadingState() {
        // Show standings card with placeholder data
        binding.standingsCard.setVisibility(View.VISIBLE);
        
        // Clear any existing standings
        binding.standingsTableContainer.removeAllViews();
        
        // Create placeholder standings for 2 players with zero values
        for (int i = 0; i < 2; i++) {
            View standingsRowView = LayoutInflater.from(this).inflate(R.layout.item_standings_card, binding.standingsTableContainer, false);
            
            // Set rank
            TextView rankText = standingsRowView.findViewById(R.id.text_rank);
            rankText.setText(String.valueOf(i + 1));

            // Set placeholder player name
            TextView playerName = standingsRowView.findViewById(R.id.text_player_name);
            playerName.setText("Player " + (i + 1));

            // Hide player ID during loading
            TextView playerId = standingsRowView.findViewById(R.id.text_player_id);
            playerId.setVisibility(View.GONE);

            // Set zero score (no label for cleaner look)
            TextView scoreText = standingsRowView.findViewById(R.id.text_score);
            scoreText.setText("0");

            // Set zero net amount only (simplified display)
            TextView netAmountText = standingsRowView.findViewById(R.id.text_net_amount);
            netAmountText.setText("₹0");
            netAmountText.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
            netAmountText.setBackground(null); // Remove any background

            // Set all round scores to dash for loading state
            populateLoadingRoundScores(standingsRowView);

            binding.standingsTableContainer.addView(standingsRowView);
        }
        
        // Set placeholder game info
        showLoadingGameInfo();
    }
    
    private void showLoadingGameInfo() {
        // Set placeholder values for game info in standings section
        binding.textPointValueInfo.setText("₹0.00 per point");
        binding.textGstRateInfo.setText("0%");
        binding.textCurrentRoundInfo.setText("Round 1");
        binding.textTotalGstInfo.setText("Total GST: ₹0");
        
        // Set placeholder game ID if not already set
        if (currentGameId != null) {
            binding.textGameIdHeader.setText(currentGameId);
        } else {
            binding.textGameIdHeader.setText("Loading...");
        }
    }
    
    private void showLoadingPlayerCards() {
        // Clear existing player cards
        binding.playersContainer.removeAllViews();
        
        // Create placeholder player cards with zero scores
        for (int i = 0; i < 2; i++) {
            View playerCardView = LayoutInflater.from(this).inflate(R.layout.item_player_card, binding.playersContainer, false);
            
            // Set placeholder player name
            EditText playerNameText = playerCardView.findViewById(R.id.text_player_name);
            playerNameText.setText("Player " + (i + 1));
            playerNameText.setEnabled(false); // Disable during loading
            
            binding.playersContainer.addView(playerCardView);
        }
        
        // Set placeholder values for players info
        binding.textPlayersPointValue.setText("₹0.00 per point");
        binding.textPlayersGstRate.setText("0%");
        binding.textPlayersCurrentRound.setText("Round 1");
        binding.textNumberOfPlayers.setText("2 Players");
    }

    private void setupClickListeners() {
        binding.btnClose.setOnClickListener(v -> finish());
        binding.btnAddPlayer.setOnClickListener(v -> {
            addNewPlayerDirectly();
        });
        
        // Setup QR code click listener
        binding.iconQrCodeHeader.setOnClickListener(v -> {
            if (currentGameId != null) {
                showQrCodeDialog(currentGameId);
            }
        });
        
        // Setup collapsible sections
        setupCollapsibleSections();
    }

    private void observeViewModel() {
        viewModel.getGameData().observe(this, gameData -> {
            if (gameData != null) {
                displayGameData(gameData);
                
                // Set up real-time listener if in view mode (no edit access)
                Boolean editAccess = viewModel.getEditAccessGranted().getValue();
                if (editAccess == null || !editAccess) {
                    System.out.println("Game data loaded in VIEW MODE - setting up real-time listener");
                    setupRealtimeListener();
                } else {
                    System.out.println("Game data loaded in EDIT MODE - real-time listener NOT started (edit access granted)");
                }
            }
        });

        viewModel.getErrorMessage().observe(this, errorMessage -> {
            if (!TextUtils.isEmpty(errorMessage)) {
                ModernToast.error(this, errorMessage);
            }
        });

        viewModel.getSuccessMessage().observe(this, successMessage -> {
            if (!TextUtils.isEmpty(successMessage)) {
                ModernToast.success(this, successMessage);
            }
        });

        viewModel.getEditAccessGranted().observe(this, granted -> {
            if (granted) {
                binding.textAdminMode.setVisibility(View.VISIBLE);
                // Show Players section and FAB when edit access is granted
                binding.playersSection.setVisibility(View.VISIBLE);
                binding.btnAddPlayer.setVisibility(View.VISIBLE);
                // Generate player cards and update players info when edit access is granted
                com.example.rummypulse.data.GameData gameData = viewModel.getGameData().getValue();
                if (gameData != null) {
                    updatePlayersInfo(gameData);
                    generatePlayerCards(gameData);
                } else {
                    // Show loading state for player cards if data not yet available
                    showLoadingPlayerCards();
                }
                // Hide the 3-dot menu when edit access is granted
                invalidateOptionsMenu();
                // Don't show duplicate success message here since it's already shown in ViewModel
                System.out.println("Edit access granted - Players section should now be visible");
                
                // Remove real-time listener when in edit mode (to avoid conflicts)
                if (gameDataListener != null) {
                    System.out.println("EDIT ACCESS GRANTED - Removing existing real-time listener to avoid conflicts");
                    gameDataListener.remove();
                    gameDataListener = null;
                } else {
                    System.out.println("EDIT ACCESS GRANTED - No existing real-time listener to remove");
                }
            } else {
                // In view mode - set up real-time listener for game data updates
                System.out.println("EDIT ACCESS DENIED - Setting up real-time listener for view mode");
                setupRealtimeListener();
            }
        });

        // Observe loading state
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                System.out.println("Loading state changed: " + isLoading);
                if (isLoading) {
                    System.out.println("Showing loading spinner with blur");
                    binding.loadingOverlay.setVisibility(View.VISIBLE);
                    binding.loadingOverlay.bringToFront(); // Ensure it's on top
                    // Apply blur effect to the main content
                    applyBlurEffect(true);
                    
                    // Show loading placeholders only during actual loading
                    showLoadingState();
                } else {
                    System.out.println("Hiding loading spinner");
                    binding.loadingOverlay.setVisibility(View.GONE);
                    // Remove blur effect
                    applyBlurEffect(false);
                }
            }
        });

        // Observe game PIN
        viewModel.getGamePin().observe(this, pin -> {
            if (pin != null) {
                currentGamePin = pin;
                // Update PIN display if game data is already loaded
                updateGamePinDisplay();
            }
        });
    }

    private void joinGame(String gameId) {
        if (TextUtils.isEmpty(gameId)) {
            ModernToast.error(this, "Game ID is required");
            return;
        }

        if (gameId.length() != 9) {
            ModernToast.error(this, "Game ID must be 9 characters");
            return;
        }

        // Join game in view mode (no PIN required)
        viewModel.joinGame(gameId, false);
    }

    private void requestEditAccess() {
        // Get game ID from intent
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("GAME_ID")) {
            String gameId = intent.getStringExtra("GAME_ID");
            if (gameId != null) {
                // Show PIN input dialog
                showPinInputDialog(gameId);
            }
        } else {
            ModernToast.error(this, "Game ID not found");
        }
    }

    private void showPinInputDialog(String gameId) {
        // Create custom dialog
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_pin_input);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.setCancelable(true);
        
        // Get views
        com.google.android.material.textfield.TextInputEditText pinInput = dialog.findViewById(R.id.edit_pin_input);
        Button btnVerify = dialog.findViewById(R.id.btn_verify);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        
        // Set up PIN input
        pinInput.requestFocus();
        
        // Set up buttons
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnVerify.setOnClickListener(v -> {
            String enteredPin = pinInput.getText().toString().trim();
            if (enteredPin.length() == 4) {
                dialog.dismiss();
                // Request edit access with PIN
                viewModel.joinGame(gameId, true, enteredPin);
            } else {
                pinInput.setError("Please enter a 4-digit PIN");
                ModernToast.error(this, "Please enter a 4-digit PIN");
            }
        });
        
        // Handle Enter key
        pinInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                btnVerify.performClick();
                return true;
            }
            return false;
        });
        
        dialog.show();
        
        // Show keyboard
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(pinInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }


    private void displayGameData(com.example.rummypulse.data.GameData gameData) {
        // Show the cards - only hide players section if edit access is not granted
        Boolean editAccess = viewModel.getEditAccessGranted().getValue();
        if (editAccess == null || !editAccess) {
            binding.playersSection.setVisibility(View.GONE); // Hidden in view mode only
        }
        binding.standingsCard.setVisibility(View.VISIBLE);

        // Get the game ID from intent
        Intent intent = getIntent();
        String gameId = "";
        if (intent != null && intent.hasExtra("GAME_ID")) {
            gameId = intent.getStringExtra("GAME_ID");
        }

        // Update header with game ID
        binding.textGameIdHeader.setText(gameId);

        // Update game info in standings section only
        updateStandingsInfo(gameData);

        // Generate standings table
        generateStandingsTable(gameData);

        // Update current round
        updateCurrentRound(gameData);
        
        // Update round validation (this is crucial for existing games)
        updateRoundValidation(gameData);
        
        // Update round colors
        updateRoundColors(gameData);

        // Hide admin mode initially
        binding.textAdminMode.setVisibility(View.GONE);
        
        // Update settlement explanation with dynamic values
        updateSettlementExplanation(gameData);
    }

    private void generatePlayerCards(com.example.rummypulse.data.GameData gameData) {
        // Clear existing player cards
        binding.playersContainer.removeAllViews();

        // Add player cards
        for (int i = 0; i < gameData.getPlayers().size(); i++) {
            final int playerIndex = i;
            com.example.rummypulse.data.Player player = gameData.getPlayers().get(i);
            View playerCardView = LayoutInflater.from(this).inflate(R.layout.item_player_card, binding.playersContainer, false);
            
            // Set player name
            EditText playerName = playerCardView.findViewById(R.id.text_player_name);
            playerName.setText(player.getName());
            
            // Add text change listener for player name
            playerName.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    // Update player name in the data model
                    String newName = s.toString().trim();
                    if (!newName.isEmpty()) {
                        player.setName(newName);
                        // Save the updated game data to Firebase (with debouncing)
                        saveGameDataWithDebounce(gameData);
                        // Update standings table to reflect name change
                        generateStandingsTable(gameData);
                    }
                }
            });

            // Handle done action on player name
            playerName.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    // Hide keyboard and clear focus
                    android.view.inputmethod.InputMethodManager imm = 
                        (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(playerName.getWindowToken(), 0);
                    playerName.clearFocus();
                    return true;
                }
                return false;
            });

            // Set player ID if available (for games with more than 2 players)
            TextView playerId = playerCardView.findViewById(R.id.text_player_id);
            if (gameData.getNumPlayers() > 2 && player.getRandomNumber() != null) {
                playerId.setText("#" + player.getRandomNumber());
                playerId.setVisibility(View.VISIBLE);
            }

            // Setup delete player button
            ImageView deleteButton = playerCardView.findViewById(R.id.btn_delete_player);
            deleteButton.setOnClickListener(v -> {
                showDeletePlayerConfirmation(player, gameData);
            });

            // Set up score inputs
            EditText[] scoreInputs = {
                playerCardView.findViewById(R.id.edit_score_r1),
                playerCardView.findViewById(R.id.edit_score_r2),
                playerCardView.findViewById(R.id.edit_score_r3),
                playerCardView.findViewById(R.id.edit_score_r4),
                playerCardView.findViewById(R.id.edit_score_r5),
                playerCardView.findViewById(R.id.edit_score_r6),
                playerCardView.findViewById(R.id.edit_score_r7),
                playerCardView.findViewById(R.id.edit_score_r8),
                playerCardView.findViewById(R.id.edit_score_r9),
                playerCardView.findViewById(R.id.edit_score_r10)
            };

            // Initialize score inputs with player data
            for (int round = 0; round < 10; round++) {
                EditText scoreInput = scoreInputs[round];
                scoreInput.setTag("p" + (i + 1) + "r" + (round + 1)); // Tag for identification
                
                // Set initial value from player data
                if (player.getScores() != null && player.getScores().size() > round) {
                    Integer score = player.getScores().get(round);
                    if (score != null && score != -1) {
                        scoreInput.setText(score.toString());
                    } else {
                        scoreInput.setText("-1");
                    }
                } else {
                    scoreInput.setText("-1");
                }

                // Set up sequential round validation
                setupSequentialRoundValidation(scoreInput, round, playerIndex, gameData);

                // Add focus listener to clear -1 values when user clicks
                scoreInput.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        String currentText = scoreInput.getText().toString().trim();
                        if ("-1".equals(currentText)) {
                            scoreInput.setText("");
                        }
                    }
                });

                // Add text change listener for color coding and standings update
                final int finalPlayerIndex = playerIndex;
                final int finalRound = round;
                scoreInput.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(android.text.Editable s) {
                        // Update the score in the player's data
                        try {
                            String text = s.toString().trim();
                            int score = text.isEmpty() ? -1 : Integer.parseInt(text);
                            
                            // Update the player's score in the game data
                            if (player.getScores() != null && finalRound < player.getScores().size()) {
                                player.getScores().set(finalRound, score);
                            }
                            
                            // Save the updated game data to Firebase (with debouncing)
                            saveGameDataWithDebounce(gameData);
                            
                        } catch (NumberFormatException e) {
                            // Invalid number, set to -1
                            if (player.getScores() != null && finalRound < player.getScores().size()) {
                                player.getScores().set(finalRound, -1);
                            }
                        }
                        
                        updateScoreColor(scoreInput);
                        updateStandings(gameData);
                        updateCurrentRound(gameData);
                        updateRoundValidation(gameData);
                    }
                });
            }


            binding.playersContainer.addView(playerCardView);
        }
    }

    private void setupSequentialRoundValidation(EditText scoreInput, int round, int playerIndex, com.example.rummypulse.data.GameData gameData) {
        // Enable only if previous rounds are completed
        boolean isEnabled = isRoundEnabled(round, playerIndex, gameData);
        scoreInput.setEnabled(isEnabled);
        scoreInput.setAlpha(isEnabled ? 1.0f : 0.5f);
    }

    private boolean isRoundEnabled(int round, int playerIndex, com.example.rummypulse.data.GameData gameData) {
        // Round 1 is always enabled
        if (round == 0) return true;
        
        // For rounds 2+, check if ALL players have completed the previous round
        // A player has "completed" a round if they have entered 0 or positive score
        for (int p = 0; p < gameData.getPlayers().size(); p++) {
            com.example.rummypulse.data.Player player = gameData.getPlayers().get(p);
            boolean playerCompleted = false;
            
            // Check UI input field first
            EditText previousScoreInput = binding.playersContainer.findViewWithTag("p" + (p + 1) + "r" + round);
            if (previousScoreInput != null) {
                String text = previousScoreInput.getText().toString();
                
                try {
                    int score = Integer.parseInt(text);
                    // Only 0 or positive scores are considered valid completion
                    if (score >= 0) {
                        playerCompleted = true;
                    }
                } catch (NumberFormatException e) {
                    // Invalid input, check existing data
                }
            }
            
            // If UI field is not completed, check existing player data
            if (!playerCompleted && player.getScores() != null && player.getScores().size() > round) {
                Integer existingScore = player.getScores().get(round);
                if (existingScore != null && existingScore >= 0) {
                    playerCompleted = true;
                }
            }
            
            // If player hasn't completed this round, next round is not enabled
            if (!playerCompleted) {
                return false;
            }
        }
        
        // All players have completed the previous round
        return true;
    }

    private void updateRoundValidation(com.example.rummypulse.data.GameData gameData) {
        // Find the round that should have the red border
        int roundWithRedBorder = 1; // Start with round 1
        
        // Check each round to find the first incomplete round
        for (int round = 1; round <= 10; round++) {
            if (!isRoundComplete(round, gameData)) {
                roundWithRedBorder = round;
                break;
            }
        }
        
        // Update all score inputs based on sequential validation
        for (int playerIndex = 0; playerIndex < gameData.getPlayers().size(); playerIndex++) {
            for (int round = 0; round < 10; round++) {
                EditText scoreInput = binding.playersContainer.findViewWithTag("p" + (playerIndex + 1) + "r" + (round + 1));
                if (scoreInput != null) {
                    boolean isEnabled = isRoundEnabled(round, playerIndex, gameData);
                    scoreInput.setEnabled(isEnabled);
                    scoreInput.setAlpha(isEnabled ? 1.0f : 0.5f);
                    
                    // Only add red border to the incomplete round
                    if (isEnabled && (round + 1) == roundWithRedBorder) {
                        // Use layered background with red border
                        scoreInput.setBackgroundResource(R.drawable.score_input_with_red_border);
                    } else {
                        // Keep original background for all other rounds
                        scoreInput.setBackgroundResource(R.drawable.score_input_background);
                    }
                }
            }
        }
        
        // Update round colors based on completion status
        updateRoundColors(gameData);
    }
    
    private void updateRoundColors(com.example.rummypulse.data.GameData gameData) {
        // Update round badge colors based on completion status
        for (int playerIndex = 0; playerIndex < gameData.getPlayers().size(); playerIndex++) {
            View playerCardView = binding.playersContainer.getChildAt(playerIndex);
            if (playerCardView != null) {
                TextView roundBadge = playerCardView.findViewById(R.id.text_current_round_badge);
                if (roundBadge != null) {
                           // Get the current round number
                           String badgeText = roundBadge.getText().toString();
                           if (badgeText.startsWith("Round#")) {
                               try {
                                   int currentRound = Integer.parseInt(badgeText.substring(6));
                            boolean isRoundComplete = isRoundComplete(currentRound, gameData);
                            
                            // Update badge text and icon color based on completion
                            LinearLayout badgeContainer = (LinearLayout) roundBadge.getParent();
                            TextView iconText = (TextView) badgeContainer.getChildAt(0); // Icon is first child
                            
                            if (isRoundComplete) {
                                // Green text and icon for completed rounds
                                roundBadge.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                                iconText.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                            } else {
                                // Red text and icon for incomplete rounds
                                roundBadge.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                                iconText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                            }
                        } catch (NumberFormatException e) {
                            // Ignore invalid round numbers
                        }
                    }
                }
            }
        }
    }
    
    private boolean isRoundComplete(int round, com.example.rummypulse.data.GameData gameData) {
        // Check if ALL players have completed this round
        for (int p = 0; p < gameData.getPlayers().size(); p++) {
            com.example.rummypulse.data.Player player = gameData.getPlayers().get(p);
            boolean playerCompleted = false;
            
            // Check UI input field first
            EditText scoreInput = binding.playersContainer.findViewWithTag("p" + (p + 1) + "r" + round);
            if (scoreInput != null) {
                String text = scoreInput.getText().toString();
                
                try {
                    int score = Integer.parseInt(text);
                    // Only 0 or positive scores are considered valid completion
                    if (score >= 0) {
                        playerCompleted = true;
                    }
                } catch (NumberFormatException e) {
                    // Invalid input, check existing data
                }
            }
            
            // If UI field is not completed, check existing player data
            if (!playerCompleted && player.getScores() != null && player.getScores().size() >= round) {
                Integer existingScore = player.getScores().get(round - 1); // round is 1-based, scores array is 0-based
                if (existingScore != null && existingScore >= 0) {
                    playerCompleted = true;
                }
            }
            
            // If player hasn't completed this round, round is not complete
            if (!playerCompleted) {
                return false;
            }
        }
        
        // All players have completed this round
        return true;
    }

    private void updateScoreColor(EditText scoreInput) {
        try {
            String text = scoreInput.getText().toString();
            int score = Integer.parseInt(text);
            
            if (score == -1) {
                // Default -1 value (not entered yet)
                scoreInput.setTextColor(getResources().getColor(android.R.color.darker_gray));
                scoreInput.setTypeface(null, android.graphics.Typeface.ITALIC);
            } else if (score < 0) {
                // Negative scores (invalid)
                scoreInput.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                scoreInput.setTypeface(null, android.graphics.Typeface.BOLD);
            } else if (score < 40) {
                // Low scores (good)
                scoreInput.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                scoreInput.setTypeface(null, android.graphics.Typeface.NORMAL);
            } else if (score <= 60) {
                // Medium scores (okay)
                scoreInput.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
                scoreInput.setTypeface(null, android.graphics.Typeface.NORMAL);
            } else {
                // High scores (bad)
                scoreInput.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                scoreInput.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        } catch (NumberFormatException e) {
            scoreInput.setTextColor(getResources().getColor(android.R.color.white));
            scoreInput.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }


    private void updateCurrentRound(com.example.rummypulse.data.GameData gameData) {
        int currentRound = calculateCurrentRound(gameData);
        // Update current round in both info cards
        updatePlayersInfo(gameData);
        updateStandingsInfo(gameData);
        
        // Update all player cards' round badges
        for (int i = 0; i < binding.playersContainer.getChildCount(); i++) {
            View playerCardView = binding.playersContainer.getChildAt(i);
            TextView roundBadge = playerCardView.findViewById(R.id.text_current_round_badge);
            if (roundBadge != null) {
                roundBadge.setText("Round#" + currentRound);
            }
        }
    }

    private int calculateCurrentRound(com.example.rummypulse.data.GameData gameData) {
        // Find the first round that is not completed by ALL players
        for (int round = 1; round <= 10; round++) {
            if (!isRoundComplete(round, gameData)) {
                return round; // This is the current round - not all players have completed it
            }
        }
        
        // If all rounds 1-10 are complete, we're still on round 10 (game over)
        return 10;
    }
    
    private boolean isGameCompleted(com.example.rummypulse.data.GameData gameData) {
        // Check if all players have completed round 10
        Boolean editAccess = viewModel.getEditAccessGranted().getValue();
        
        if (editAccess != null && editAccess) {
            // Edit mode: check input fields for round 10
            for (int playerIndex = 0; playerIndex < gameData.getPlayers().size(); playerIndex++) {
                EditText scoreInput = binding.playersContainer.findViewWithTag("p" + (playerIndex + 1) + "r10");
                if (scoreInput != null) {
                    try {
                        String text = scoreInput.getText().toString();
                        if (text.isEmpty()) {
                            return false; // Player hasn't completed round 10
                        }
                        int score = Integer.parseInt(text);
                        if (score < 0) { // -1 means not played
                            return false;
                        }
                    } catch (NumberFormatException e) {
                        return false; // Invalid score means not completed
                    }
                } else {
                    return false; // Input field not found
                }
            }
            return true; // All players have completed round 10
        } else {
            // View mode: check game data for round 10 (index 9)
            for (com.example.rummypulse.data.Player player : gameData.getPlayers()) {
                if (player.getScores() == null || player.getScores().size() < 10) {
                    return false; // Player doesn't have 10 rounds
                }
                Integer round10Score = player.getScores().get(9); // Round 10 is index 9
                if (round10Score == null || round10Score < 0) {
                    return false; // Round 10 not completed
                }
            }
            return true; // All players have completed round 10
        }
    }

    private void generateStandingsTable(com.example.rummypulse.data.GameData gameData) {
        // Clear existing standings
        binding.standingsTableContainer.removeAllViews();

        // Calculate standings (no header needed)
        updateStandings(gameData);
    }

    private void updateStandings(com.example.rummypulse.data.GameData gameData) {
        // Validate input data
        if (gameData == null || gameData.getPlayers() == null || gameData.getPlayers().isEmpty()) {
            System.err.println("Cannot update standings: gameData or players is null/empty");
            return;
        }
        
        // Clear existing standings rows
        binding.standingsTableContainer.removeAllViews();

        // First pass: collect all scores
        java.util.List<PlayerStanding> standings = new java.util.ArrayList<>();
        int totalAllScores = 0;
        
        for (int i = 0; i < gameData.getPlayers().size(); i++) {
            com.example.rummypulse.data.Player player = gameData.getPlayers().get(i);
            
            // Calculate total score from game data (works in both view and edit mode)
            int totalScore = 0;
            
            // First try to get scores from input fields (edit mode)
            Boolean editAccess = viewModel.getEditAccessGranted().getValue();
            if (editAccess != null && editAccess) {
                // Edit mode: get scores from input fields
                for (int round = 1; round <= 10; round++) {
                    EditText scoreInput = binding.playersContainer.findViewWithTag("p" + (i + 1) + "r" + round);
                    if (scoreInput != null) {
                        try {
                            String text = scoreInput.getText().toString();
                            if (!text.isEmpty()) {
                                int score = Integer.parseInt(text);
                                if (score > 0) {
                                    totalScore += score;
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Ignore invalid scores
                        }
                    }
                }
            } else {
                // View mode: get scores from game data
                if (player.getScores() != null) {
                    for (Integer score : player.getScores()) {
                        if (score != null && score > 0) {
                            totalScore += score;
                        }
                    }
                }
            }

            PlayerStanding standing = new PlayerStanding();
            standing.player = player;
            standing.totalScore = totalScore;
            
            standings.add(standing);
            totalAllScores += totalScore;
        }

        // Second pass: calculate amounts using Rummy formula
        // Formula: (Total of all scores - Player's score × Number of players) × Point value
        for (PlayerStanding standing : standings) {
            double grossAmount = (totalAllScores - standing.totalScore * gameData.getNumPlayers()) * gameData.getPointValue();
            
            // Calculate GST (only for winners with positive gross amount)
            double gstPaid = 0;
            if (grossAmount > 0) {
                gstPaid = (grossAmount * gameData.getGstPercent()) / 100.0;
            }
            
            // Calculate net amount
            double netAmount = grossAmount - gstPaid;

            standing.grossAmount = grossAmount;
            standing.gstPaid = gstPaid;
            standing.netAmount = netAmount;
        }

        // Sort by total score (ascending - lower is better in Rummy)
        standings.sort((a, b) -> Integer.compare(a.totalScore, b.totalScore));

        // Add standings rows
        for (int i = 0; i < standings.size(); i++) {
            PlayerStanding standing = standings.get(i);
            View standingsRowView = LayoutInflater.from(this).inflate(R.layout.item_standings_card, binding.standingsTableContainer, false);
            
            // Check for rank changes and apply animations
            String playerKey = standing.player.getName(); // Use name as unique identifier
            int currentRank = i + 1;
            Integer previousRank = previousRanks.get(playerKey);
            
            // Set rank
            TextView rankText = standingsRowView.findViewById(R.id.text_rank);
            if (i == 0) rankText.setText("🥇");
            else if (i == 1) rankText.setText("🥈");
            else if (i == 2) rankText.setText("🥉");
            else rankText.setText(String.valueOf(i + 1));
            
            // Apply rank change animation if rank changed
            if (previousRank != null && previousRank != currentRank) {
                if (currentRank < previousRank) {
                    // Rank improved (moved up) - celebration animation
                    android.view.animation.Animation rankUpAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rank_up_animation);
                    standingsRowView.startAnimation(rankUpAnim);
                } else {
                    // Rank worsened (moved down) - shake animation
                    android.view.animation.Animation rankDownAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rank_down_animation);
                    standingsRowView.startAnimation(rankDownAnim);
                }
            }
            
            // Update previous rank for next comparison
            previousRanks.put(playerKey, currentRank);

            // Set player name
            TextView playerName = standingsRowView.findViewById(R.id.text_player_name);
            playerName.setText(standing.player.getName());

            // Player ID is hidden (not displayed in standings)
            // TextView playerIdText = standingsRowView.findViewById(R.id.text_player_id);

            // Set score (with "Score:" label for clarity)
            TextView scoreText = standingsRowView.findViewById(R.id.text_score);
            scoreText.setText(String.valueOf(standing.totalScore));

            // Set net amount only (simplified display)
            TextView netAmountText = standingsRowView.findViewById(R.id.text_net_amount);
            netAmountText.setText("₹" + String.format("%.0f", standing.netAmount));
            
            // Color code based on positive/negative net amount - text color only
            if (standing.netAmount > 0) {
                // Positive net amount (winners) - green text only
                netAmountText.setTextColor(getResources().getColor(R.color.success_green, getTheme()));
                netAmountText.setBackground(null); // Remove any background
            } else {
                // Negative net amount (losers) - red text only
                netAmountText.setTextColor(getResources().getColor(R.color.error_red, getTheme()));
                netAmountText.setBackground(null); // Remove any background
            }

            // Populate round scores
            populateRoundScores(standingsRowView, standing.player, gameData);

            binding.standingsTableContainer.addView(standingsRowView);
        }
    }

    /**
     * Populate round scores with dashes for loading state
     */
    private void populateLoadingRoundScores(View standingsRowView) {
        int[] roundScoreIds = {
            R.id.round_1_score, R.id.round_2_score, R.id.round_3_score, R.id.round_4_score, R.id.round_5_score,
            R.id.round_6_score, R.id.round_7_score, R.id.round_8_score, R.id.round_9_score, R.id.round_10_score
        };

        for (int roundScoreId : roundScoreIds) {
            TextView roundScoreText = standingsRowView.findViewById(roundScoreId);
            if (roundScoreText != null) {
                roundScoreText.setText("-");
                roundScoreText.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
            }
        }
    }

    /**
     * Populate the 10 round score boxes for a player
     */
    private void populateRoundScores(View standingsRowView, com.example.rummypulse.data.Player player, com.example.rummypulse.data.GameData gameData) {
        // Array of round score TextViews
        int[] roundScoreIds = {
            R.id.round_1_score, R.id.round_2_score, R.id.round_3_score, R.id.round_4_score, R.id.round_5_score,
            R.id.round_6_score, R.id.round_7_score, R.id.round_8_score, R.id.round_9_score, R.id.round_10_score
        };

        // Get current round for blinking animation
        int currentRound = calculateCurrentRound(gameData);

        for (int round = 0; round < 10; round++) {
            TextView roundScoreText = standingsRowView.findViewById(roundScoreIds[round]);
            if (roundScoreText != null) {
                // Clear any existing animation first
                roundScoreText.clearAnimation();
                
                Integer score = null;
                if (player.getScores() != null && round < player.getScores().size()) {
                    score = player.getScores().get(round);
                }
                
                if (score != null && score >= 0) {
                    // Valid score (including 0) - show it
                    roundScoreText.setText(String.valueOf(score));
                    
                    if (score == 0) {
                        // Zero score (winning round) - use rounded green background
                        roundScoreText.setBackground(getResources().getDrawable(R.drawable.round_score_box_zero, getTheme()));
                        roundScoreText.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                        roundScoreText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD);
                        roundScoreText.setPaintFlags(roundScoreText.getPaintFlags() | android.graphics.Paint.FAKE_BOLD_TEXT_FLAG);
                    } else {
                        // Regular score - use colored boundary based on score value
                        
                        if (score > 65) {
                            // Very high score (bad round) - red boundary and text
                            roundScoreText.setBackground(getResources().getDrawable(R.drawable.round_score_box_red, getTheme()));
                            roundScoreText.setTextColor(getResources().getColor(R.color.error_red, getTheme()));
                        } else if (score >= 40) {
                            // High score - yellow boundary
                            roundScoreText.setBackground(getResources().getDrawable(R.drawable.round_score_box_yellow, getTheme()));
                            roundScoreText.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
                        } else {
                            // Low score (good performance) - green boundary
                            roundScoreText.setBackground(getResources().getDrawable(R.drawable.round_score_box_green, getTheme()));
                            roundScoreText.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
                        }
                        
                        // Make ALL scores bold for better readability
                        roundScoreText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD);
                        roundScoreText.setPaintFlags(roundScoreText.getPaintFlags() | android.graphics.Paint.FAKE_BOLD_TEXT_FLAG);
                    }
                } else {
                    // No score yet (null or -1) - show dash or random number for current round
                    if ((round + 1) == currentRound) {
                        // Current round - show random number with blinking animation that changes numbers
                        roundScoreText.setBackground(getResources().getDrawable(R.drawable.round_score_box, getTheme()));
                        roundScoreText.setTextColor(getResources().getColor(R.color.warning_orange, getTheme())); // Orange color for visibility
                        roundScoreText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD);
                        roundScoreText.setPaintFlags(roundScoreText.getPaintFlags() | android.graphics.Paint.FAKE_BOLD_TEXT_FLAG);
                        
                        // Start with initial random number
                        int initialNumber = (int) (Math.random() * 101); // 0-100
                        roundScoreText.setText(String.valueOf(initialNumber));
                        
                        // Start blinking animation
                        android.view.animation.Animation blinkAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.blink_animation);
                        roundScoreText.startAnimation(blinkAnimation);
                        
                        // Use Handler to change numbers every 400ms (half of blink cycle)
                        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                        Runnable numberChanger = new Runnable() {
                            @Override
                            public void run() {
                                // Only change if this TextView still has the animation running
                                if (roundScoreText.getAnimation() != null) {
                                    int newRandomNumber = (int) (Math.random() * 101);
                                    roundScoreText.setText(String.valueOf(newRandomNumber));
                                    // Schedule next change
                                    handler.postDelayed(this, 400); // Change every 400ms
                                }
                            }
                        };
                        // Start the number changing after initial delay
                        handler.postDelayed(numberChanger, 400);
                    } else {
                        // Future rounds - show dash
                        roundScoreText.setText("-");
                        roundScoreText.setBackground(getResources().getDrawable(R.drawable.round_score_box, getTheme()));
                        roundScoreText.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
                        roundScoreText.setTypeface(null, android.graphics.Typeface.NORMAL);
                        roundScoreText.setPaintFlags(roundScoreText.getPaintFlags() & ~android.graphics.Paint.FAKE_BOLD_TEXT_FLAG);
                    }
                }
            }
        }
    }

    private void updatePlayersInfo(com.example.rummypulse.data.GameData gameData) {
        // Update Players & Scores information card
        binding.textPlayersPointValue.setText("₹" + formatPointValue(gameData.getPointValue()) + " per point");
        binding.textPlayersGstRate.setText(String.format("%.0f", gameData.getGstPercent()) + "%");
        
        int currentRound = calculateCurrentRound(gameData);
        if (currentRound == 10 && isGameCompleted(gameData)) {
            binding.textPlayersCurrentRound.setText("Game Over");
        } else {
            binding.textPlayersCurrentRound.setText("Round " + currentRound);
        }
        
        int numberOfPlayers = gameData.getPlayers().size();
        binding.textNumberOfPlayers.setText(numberOfPlayers + " Players");
        
        // Setup Game PIN display and masking
        setupGamePinDisplay(gameData);
    }

    private void setupGamePinDisplay(com.example.rummypulse.data.GameData gameData) {
        // This method is called when game data is loaded
        // The actual PIN setup is done in updateGamePinDisplay() when PIN is available
        updateGamePinDisplay();
    }

    private void updateGamePinDisplay() {
        TextView gamePinText = findViewById(R.id.text_players_game_pin);
        if (gamePinText != null && currentGamePin != null) {
            String maskedPin = "••••••";
            
            // Initially show masked PIN
            gamePinText.setText(maskedPin);
            gamePinText.setTag(false); // false = masked, true = revealed
            
            // Set click listener to toggle between masked and revealed
            gamePinText.setOnClickListener(v -> {
                boolean isRevealed = (Boolean) gamePinText.getTag();
                if (isRevealed) {
                    // Currently revealed, mask it
                    gamePinText.setText(maskedPin);
                    gamePinText.setTag(false);
                    // No toast needed - user can see the PIN is now masked
                } else {
                    // Currently masked, reveal it
                    gamePinText.setText(currentGamePin);
                    gamePinText.setTag(true);
                    // No toast needed - user can see the PIN is now revealed
                    
                    // Auto-mask after 3 seconds for security
                    gamePinText.postDelayed(() -> {
                        if (gamePinText.getTag() != null && (Boolean) gamePinText.getTag()) {
                            gamePinText.setText(maskedPin);
                            gamePinText.setTag(false);
                            // Optional: subtle indication that PIN was auto-masked
                            ModernToast.info(this, "🔒 PIN auto-masked");
                        }
                    }, 3000);
                }
            });
        }
    }

    private void setupCollapsibleSections() {
        // Setup Players section collapsible
        binding.playersHeader.setOnClickListener(v -> {
            toggleSection(binding.playersContent, binding.playersCollapseIcon);
        });
        
        // Setup Standings section collapsible
        binding.standingsHeader.setOnClickListener(v -> {
            toggleSection(binding.standingsContent, binding.standingsCollapseIcon);
        });
    }
    
    private void toggleSection(View contentView, ImageView arrowIcon) {
        if (contentView.getId() == R.id.players_content) {
            // For players section, only toggle the players_container (not the info card)
            View playersContainer = findViewById(R.id.players_container);
            if (playersContainer != null) {
                if (playersContainer.getVisibility() == View.VISIBLE) {
                    // Collapse - hide only the player cards
                    playersContainer.setVisibility(View.GONE);
                    arrowIcon.setImageResource(R.drawable.ic_expand_more);
                } else {
                    // Expand - show the player cards
                    playersContainer.setVisibility(View.VISIBLE);
                    arrowIcon.setImageResource(R.drawable.ic_expand_less);
                }
            }
        } else if (contentView.getId() == R.id.standings_content) {
            // For standings section, toggle the standings table
            View standingsTableContainer = findViewById(R.id.standings_table_container);
            
            if (standingsTableContainer != null) {
                if (standingsTableContainer.getVisibility() == View.VISIBLE) {
                    // Collapse - hide the standings table
                    standingsTableContainer.setVisibility(View.GONE);
                    arrowIcon.setImageResource(R.drawable.ic_expand_more);
                } else {
                    // Expand - show the standings table
                    standingsTableContainer.setVisibility(View.VISIBLE);
                    arrowIcon.setImageResource(R.drawable.ic_expand_less);
                }
            }
        } else {
            // Default behavior for other sections
            if (contentView.getVisibility() == View.VISIBLE) {
                // Collapse
                contentView.setVisibility(View.GONE);
                arrowIcon.setImageResource(R.drawable.ic_expand_more);
            } else {
                // Expand
                contentView.setVisibility(View.VISIBLE);
                arrowIcon.setImageResource(R.drawable.ic_expand_less);
            }
        }
    }


    // Helper method to convert dp to pixels
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private double calculateTotalGST(com.example.rummypulse.data.GameData gameData) {
        double totalGST = 0.0;
        
        // Calculate total GST from all players' standings
        java.util.List<PlayerStanding> standings = new java.util.ArrayList<>();
        
        for (int i = 0; i < gameData.getPlayers().size(); i++) {
            com.example.rummypulse.data.Player player = gameData.getPlayers().get(i);
            int totalScore = 0;
            
            // Calculate total score from game data (works in both view and edit mode)
            Boolean editAccess = viewModel.getEditAccessGranted().getValue();
            if (editAccess != null && editAccess) {
                // Edit mode: get scores from input fields
                for (int round = 1; round <= 10; round++) {
                    EditText scoreInput = binding.playersContainer.findViewWithTag("p" + (i + 1) + "r" + round);
                    if (scoreInput != null && !scoreInput.getText().toString().trim().isEmpty()) {
                        try {
                            int score = Integer.parseInt(scoreInput.getText().toString().trim());
                            if (score > 0) { // Only count positive scores
                                totalScore += score;
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid scores
                        }
                    }
                }
            } else {
                // View mode: get scores from game data
                if (player.getScores() != null) {
                    for (Integer score : player.getScores()) {
                        if (score != null && score > 0) {
                            totalScore += score;
                        }
                    }
                }
            }
            
            PlayerStanding standing = new PlayerStanding();
            standing.player = player;
            standing.totalScore = totalScore;
            standings.add(standing);
        }
        
        // Calculate gross amounts and GST
        int totalAllScores = standings.stream().mapToInt(s -> s.totalScore).sum();
        
        for (PlayerStanding standing : standings) {
            standing.grossAmount = (totalAllScores - standing.totalScore * gameData.getPlayers().size()) * gameData.getPointValue();
            
            if (standing.grossAmount > 0) {
                standing.gstPaid = standing.grossAmount * (gameData.getGstPercent() / 100.0);
                totalGST += standing.gstPaid;
            } else {
                standing.gstPaid = 0;
            }
        }
        
        return totalGST;
    }

    private void updateStandingsInfo(com.example.rummypulse.data.GameData gameData) {
        // Update Standings information card
        binding.textPointValueInfo.setText("₹" + String.format("%.2f", gameData.getPointValue()) + " per point");
        binding.textGstRateInfo.setText(String.format("%.0f", gameData.getGstPercent()) + "%");
        
        int currentRound = calculateCurrentRound(gameData);
        if (currentRound == 10 && isGameCompleted(gameData)) {
            binding.textCurrentRoundInfo.setText("Game Over");
        } else {
            binding.textCurrentRoundInfo.setText("Round " + currentRound);
        }
        
        // Calculate total GST collected
        double totalGST = calculateTotalGST(gameData);
        binding.textTotalGstInfo.setText("₹" + String.format("%.0f", totalGST));
    }

    private void updateSettlementExplanation(com.example.rummypulse.data.GameData gameData) {
        try {
            double pointValue = gameData.getPointValue();
            double gstPercent = gameData.getGstPercent();
            int playerCount = gameData.getPlayers() != null ? gameData.getPlayers().size() : 4;
            
            // Format values - preserve fractional parts for point value
            String pointValueText = "₹" + formatPointValue(pointValue);
            
            String gstPercentText = String.format("%.0f", gstPercent) + "%";
            
            // Update Winners Rule
            TextView winnersRule = findViewById(R.id.text_settlement_winners_rule);
            if (winnersRule != null) {
                winnersRule.setText("Winners (Green): Receive money but pay " + gstPercentText + " on winnings");
            }
            
            // Update Formula
            TextView formulaText = findViewById(R.id.text_settlement_formula);
            if (formulaText != null) {
                formulaText.setText("Formula: (Total All Scores - Your Score × " + playerCount + ") × " + pointValueText);
            }
            
            // Update GST Rule
            TextView gstRule = findViewById(R.id.text_settlement_gst_rule);
            if (gstRule != null) {
                gstRule.setText("GST: Only winners pay " + gstPercentText + " on positive amounts");
            }
            
            // Update Example Description
            TextView exampleDesc = findViewById(R.id.text_settlement_example_description);
            if (exampleDesc != null) {
                exampleDesc.setText("If you score 25 points in a " + playerCount + "-player game with " + pointValueText + "/point:");
            }
            
            // Update Example Formula
            TextView exampleFormula = findViewById(R.id.text_settlement_example_formula);
            if (exampleFormula != null) {
                exampleFormula.setText("Your settlement = (Total of all " + playerCount + " scores - 25 × " + playerCount + ") × " + pointValueText);
            }
            
        } catch (Exception e) {
            System.out.println("Error updating settlement explanation: " + e.getMessage());
        }
    }

    private String formatPointValue(double value) {
        // Remove unnecessary trailing zeros while preserving meaningful decimals
        if (value == Math.floor(value)) {
            // Whole number - show without decimals
            return String.format("%.0f", value);
        } else {
            // Has decimals - format to remove trailing zeros
            String formatted = String.format("%.2f", value);
            // Remove trailing zeros after decimal point
            formatted = formatted.replaceAll("0*$", "").replaceAll("\\.$", "");
            return formatted;
        }
    }

    private void applyBlurEffect(boolean apply) {
        try {
            if (apply) {
                // Apply blur effect to the main content areas only
                View[] viewsToBlur = {
                    binding.playersSection,
                    binding.standingsCard,
                    findViewById(R.id.admin_mode_card)
                };
                
                for (View view : viewsToBlur) {
                    if (view != null) {
                        view.animate()
                            .alpha(0.6f)
                            .scaleX(0.98f)
                            .scaleY(0.98f)
                            .setDuration(200)
                            .start();
                    }
                }
                
                // Also blur the floating action button
                binding.btnAddPlayer.animate()
                    .alpha(0.5f)
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(200)
                    .start();
                    
            } else {
                // Remove blur effect
                View[] viewsToRestore = {
                    binding.playersSection,
                    binding.standingsCard,
                    findViewById(R.id.admin_mode_card)
                };
                
                for (View view : viewsToRestore) {
                    if (view != null) {
                        view.animate()
                            .alpha(1.0f)
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(200)
                            .start();
                    }
                }
                
                // Restore floating action button
                binding.btnAddPlayer.animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .start();
            }
        } catch (Exception e) {
            System.out.println("Error applying blur effect: " + e.getMessage());
        }
    }
    
    private void addNewPlayerDirectly() {
        // Get current game data
        com.example.rummypulse.data.GameData gameData = viewModel.getGameData().getValue();
        if (gameData == null) return;
        
        // Check maximum player limit
        if (gameData.getPlayers().size() >= 15) {
            ModernToast.warning(this, "Cannot add more players. Maximum 15 players allowed.");
            return;
        }
        
        // Generate default player name
        int playerNumber = gameData.getPlayers().size() + 1;
        String defaultName = "Player " + playerNumber;
        
        // Check if name already exists and make it unique
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        for (com.example.rummypulse.data.Player player : gameData.getPlayers()) {
            existingNames.add(player.getName().toLowerCase());
        }
        
        int counter = playerNumber;
        while (existingNames.contains(defaultName.toLowerCase())) {
            counter++;
            defaultName = "Player " + counter;
        }
        
        addNewPlayer(defaultName);
    }
    
    private void showAddPlayerDialog() {
        // Create dialog with dark theme to match the app
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this, R.style.DarkDialogTheme);
        builder.setTitle("Add New Player");
        
        // Create input field
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        input.setHint("Enter player name");
        input.setTextColor(getResources().getColor(android.R.color.white)); // White text for dark theme
        input.setHintTextColor(getResources().getColor(android.R.color.darker_gray));
        input.setPadding(50, 20, 50, 20);
        
        builder.setView(input);
        
        builder.setPositiveButton("Add Player", (dialog, which) -> {
            String playerName = input.getText().toString().trim();
            if (!playerName.isEmpty()) {
                addNewPlayer(playerName);
            } else {
                ModernToast.error(this, "Please enter a player name");
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        
        // Show keyboard
        input.requestFocus();
        android.view.inputmethod.InputMethodManager imm = 
            (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }
    
    private void addNewPlayer(String playerName) {
        // Get current game data
        com.example.rummypulse.data.GameData gameData = viewModel.getGameData().getValue();
        if (gameData == null) return;
        
        // Create new player
        com.example.rummypulse.data.Player newPlayer = new com.example.rummypulse.data.Player();
        newPlayer.setName(playerName);
        
        // Initialize scores with -1 for all rounds
        java.util.List<Integer> scores = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            scores.add(-1);
        }
        newPlayer.setScores(scores);
        
        // Generate random number for player ID (if more than 2 players)
        if (gameData.getPlayers().size() >= 2) {
            // Generate a unique random number
            java.util.Set<Integer> existingNumbers = new java.util.HashSet<>();
            for (com.example.rummypulse.data.Player player : gameData.getPlayers()) {
                if (player.getRandomNumber() != null) {
                    existingNumbers.add(player.getRandomNumber());
                }
            }
            
            int randomNumber;
            do {
                randomNumber = 10 + new java.util.Random().nextInt(90); // 10-99
            } while (existingNumbers.contains(randomNumber));
            
            newPlayer.setRandomNumber(randomNumber);
        }
        
        // Add player to game data
        gameData.getPlayers().add(newPlayer);
        gameData.setNumPlayers(gameData.getPlayers().size());
        
        // Save the updated game data to Firebase
        viewModel.saveGameData(currentGameId, gameData);
        
        // Refresh player cards UI immediately
        generatePlayerCards(gameData);
        
        // Update players info section
        updatePlayersInfo(gameData);
        
        // Refresh standings and chart
        displayGameData(gameData);
        
        ModernToast.success(this, "Player '" + playerName + "' added successfully!");
    }
    
    private void showDeletePlayerConfirmation(com.example.rummypulse.data.Player player, com.example.rummypulse.data.GameData gameData) {
        // Don't allow deleting if only 2 players remain
        if (gameData.getPlayers().size() <= 2) {
            ModernToast.warning(this, "Cannot delete player. Minimum 2 players required.");
            return;
        }
        
        // Create custom dialog with proper button styling
        android.app.Dialog dialog = new android.app.Dialog(this, R.style.DarkDialogTheme);
        dialog.setContentView(R.layout.dialog_delete_player);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.setCancelable(true);
        
        // Get views
        TextView deleteMessage = dialog.findViewById(R.id.text_delete_message);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnDelete = dialog.findViewById(R.id.btn_delete);
        
        // Set player-specific message
        deleteMessage.setText("Are you sure you want to delete '" + player.getName() + "'?");
        
        // Set up buttons
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            deletePlayer(player, gameData);
        });
        
        dialog.show();
    }
    
    private void deletePlayer(com.example.rummypulse.data.Player player, com.example.rummypulse.data.GameData gameData) {
        // Remove player from game data
        gameData.getPlayers().remove(player);
        gameData.setNumPlayers(gameData.getPlayers().size());
        
        // Save the updated game data to Firebase
        viewModel.saveGameData(currentGameId, gameData);
        
        // Refresh player cards UI immediately
        generatePlayerCards(gameData);
        
        // Update players info section
        updatePlayersInfo(gameData);
        
        // Refresh standings and chart
        displayGameData(gameData);
        
        ModernToast.success(this, "Player '" + player.getName() + "' deleted successfully!");
    }

    // Debouncing for Firebase saves
    private android.os.Handler saveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable saveRunnable;
    private static final int SAVE_DELAY_MS = 1000; // 1 second delay
    
    private void saveGameDataWithDebounce(com.example.rummypulse.data.GameData gameData) {
        // Cancel any pending save
        if (saveRunnable != null) {
            saveHandler.removeCallbacks(saveRunnable);
        }
        
        // Schedule a new save
        saveRunnable = new Runnable() {
            @Override
            public void run() {
                viewModel.saveGameData(currentGameId, gameData);
            }
        };
        
        saveHandler.postDelayed(saveRunnable, SAVE_DELAY_MS);
    }

    private void showQrCodeDialog(String gameId) {
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkDialogTheme);
        
        // Inflate custom layout
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_qr_code, null);
        
        // Get views
        ImageView qrCodeImage = dialogView.findViewById(R.id.qr_code_image);
        TextView gameIdText = dialogView.findViewById(R.id.text_game_id_qr);
        ImageView closeButton = dialogView.findViewById(R.id.btn_close);
        
        // Set game information
        gameIdText.setText("Game ID: " + gameId);
        
        // Generate QR code
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(gameId, BarcodeFormat.QR_CODE, 300, 300);
            qrCodeImage.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
            ModernToast.error(this, "❌ Failed to generate QR code");
            return;
        }
        
        // Set up dialog
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        
        // Set QR code click listener to copy Game ID
        qrCodeImage.setOnClickListener(v -> {
            copyToClipboard(gameId, "Game ID");
            ModernToast.success(this, "📋 Game ID copied to clipboard!");
        });
        
        // Set close button listener
        closeButton.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void copyToClipboard(String text, String label) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
    }
    
    /**
     * Set up real-time Firestore listener for game data updates in view mode
     * This ensures the standings update automatically when other players modify scores or names
     */
    private void setupRealtimeListener() {
        if (currentGameId == null) {
            System.out.println("Cannot setup listener: currentGameId is null");
            return;
        }
        
        // Don't create duplicate listeners
        if (gameDataListener != null) {
            System.out.println("Real-time listener already exists for game: " + currentGameId);
            return;
        }
        
        System.out.println("Setting up real-time listener for game: " + currentGameId);
        
        // Set up Firestore listener
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        gameDataListener = db.collection("gameData")
            .document(currentGameId)
            .addSnapshotListener((documentSnapshot, error) -> {
                if (error != null) {
                    System.err.println("Error listening to game data: " + error.getMessage());
                    return;
                }
                
                if (documentSnapshot != null && documentSnapshot.exists()) {
                    try {
                        System.out.println("Real-time update received for game: " + currentGameId);
                        System.out.println("Raw document data keys: " + documentSnapshot.getData().keySet());
                        
                        // The actual game data is nested inside the 'data' field
                        Object dataField = documentSnapshot.get("data");
                        if (dataField instanceof java.util.Map) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) dataField;
                            
                            // Convert the nested data to GameData object
                            com.example.rummypulse.data.GameData gameData = new com.example.rummypulse.data.GameData();
                            
                            // Manually map the fields
                            if (dataMap.get("numPlayers") instanceof Number) {
                                gameData.setNumPlayers(((Number) dataMap.get("numPlayers")).intValue());
                            }
                            if (dataMap.get("pointValue") instanceof Number) {
                                gameData.setPointValue(((Number) dataMap.get("pointValue")).doubleValue());
                            }
                            if (dataMap.get("gstPercent") instanceof Number) {
                                gameData.setGstPercent(((Number) dataMap.get("gstPercent")).doubleValue());
                            }
                            
                            // Handle players array
                            Object playersField = dataMap.get("players");
                            if (playersField instanceof java.util.List) {
                                @SuppressWarnings("unchecked")
                                java.util.List<java.util.Map<String, Object>> playersMapList = (java.util.List<java.util.Map<String, Object>>) playersField;
                                
                                java.util.List<com.example.rummypulse.data.Player> players = new java.util.ArrayList<>();
                                for (java.util.Map<String, Object> playerMap : playersMapList) {
                                    com.example.rummypulse.data.Player player = new com.example.rummypulse.data.Player();
                                    
                                    if (playerMap.get("name") instanceof String) {
                                        player.setName((String) playerMap.get("name"));
                                    }
                                    if (playerMap.get("randomNumber") instanceof Number) {
                                        player.setRandomNumber(((Number) playerMap.get("randomNumber")).intValue());
                                    }
                                    
                                    // Handle scores array
                                    Object scoresField = playerMap.get("scores");
                                    if (scoresField instanceof java.util.List) {
                                        @SuppressWarnings("unchecked")
                                        java.util.List<Object> scoresObjectList = (java.util.List<Object>) scoresField;
                                        java.util.List<Integer> scores = new java.util.ArrayList<>();
                                        for (Object scoreObj : scoresObjectList) {
                                            if (scoreObj instanceof Number) {
                                                scores.add(((Number) scoreObj).intValue());
                                            } else {
                                                scores.add(-1); // Default for null/invalid scores
                                            }
                                        }
                                        player.setScores(scores);
                                    }
                                    
                                    players.add(player);
                                }
                                gameData.setPlayers(players);
                            }
                            
                            System.out.println("Players field: " + (gameData.getPlayers() != null ? gameData.getPlayers().size() + " players" : "null"));
                            
                            // Update UI on main thread
                            runOnUiThread(() -> {
                                // Validate game data before updating UI
                                if (gameData.getPlayers() != null && !gameData.getPlayers().isEmpty()) {
                                    // Update the standings table with new data
                                    updateStandings(gameData);
                                    updateStandingsInfo(gameData);
                                    
                                    // Also update the game info cards
                                    updatePlayersInfo(gameData);
                                } else {
                                    System.err.println("Real-time update received but players data is null or empty");
                                }
                            });
                        } else {
                            System.err.println("Data field is not a Map or is null");
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing game data: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("Game document does not exist: " + currentGameId);
                }
            });
    }

    // Helper class for standings
    private static class PlayerStanding {
        com.example.rummypulse.data.Player player;
        int totalScore;
        double grossAmount;
        double gstPaid;
        double netAmount;
    }
}
