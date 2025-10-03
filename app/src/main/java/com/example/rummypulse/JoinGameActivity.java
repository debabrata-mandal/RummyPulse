package com.example.rummypulse;

import android.content.Intent;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.example.rummypulse.databinding.ActivityJoinGameBinding;
import com.example.rummypulse.ui.join.JoinGameViewModel;
import com.example.rummypulse.utils.ModernToast;
import com.google.android.material.textfield.TextInputEditText;

public class JoinGameActivity extends AppCompatActivity {

    private JoinGameViewModel viewModel;
    private ActivityJoinGameBinding binding;
    private String currentGamePin;

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
            
            if (gameId != null) {
                // Keep toolbar title as "Game View" only
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("Game View");
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

    private void initializeViews() {
        // Initially hide the cards
        binding.playersSection.setVisibility(View.GONE);
        binding.standingsCard.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        binding.btnClose.setOnClickListener(v -> finish());
        binding.btnAddPlayer.setOnClickListener(v -> {
            addNewPlayerDirectly();
        });
        
        // Setup collapsible sections
        setupCollapsibleSections();
    }

    private void observeViewModel() {
        viewModel.getGameData().observe(this, gameData -> {
            if (gameData != null) {
                displayGameData(gameData);
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
                ModernToast.success(this, "✅ Edit access granted!");
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


    private void displayGameData(com.example.rummypulse.data.GameData gameData) {
        // Show the cards
        binding.playersSection.setVisibility(View.VISIBLE);
        binding.standingsCard.setVisibility(View.VISIBLE);

        // Get the game ID from intent
        Intent intent = getIntent();
        String gameId = "";
        if (intent != null && intent.hasExtra("GAME_ID")) {
            gameId = intent.getStringExtra("GAME_ID");
        }

        // Update header with game ID
        binding.textGameIdHeader.setText(gameId);

        // Update game info in both sections
        updatePlayersInfo(gameData);
        updateStandingsInfo(gameData);

        // Generate player cards
        generatePlayerCards(gameData);

        // Generate standings table
        generateStandingsTable(gameData);
        
        // Update score chart
        updateScoreChart(gameData);

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
                        updateScoreColor(scoreInput);
                        updateStandings(gameData);
                        updateCurrentRound(gameData);
                        updateRoundValidation(gameData);
                        updateScoreChart(gameData);
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
        int maxRound = 1; // Start with round 1
        
        // Check all players' scores to find the highest round with valid scores
        for (int playerIndex = 0; playerIndex < gameData.getPlayers().size(); playerIndex++) {
            for (int round = 1; round <= 10; round++) {
                EditText scoreInput = binding.playersContainer.findViewWithTag("p" + (playerIndex + 1) + "r" + round);
                if (scoreInput != null) {
                        try {
                            String text = scoreInput.getText().toString();
                            if (!text.isEmpty()) {
                                int score = Integer.parseInt(text);
                                if (score >= 0) { // Valid score (0 or positive)
                                    maxRound = Math.max(maxRound, round);
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Ignore invalid scores
                        }
                }
            }
        }
        
        return maxRound;
    }

    private void generateStandingsTable(com.example.rummypulse.data.GameData gameData) {
        // Clear existing standings
        binding.standingsTableContainer.removeAllViews();

        // Calculate standings (no header needed)
        updateStandings(gameData);
    }

    private void updateStandings(com.example.rummypulse.data.GameData gameData) {
        // Clear existing standings rows
        binding.standingsTableContainer.removeAllViews();

        // First pass: collect all scores
        java.util.List<PlayerStanding> standings = new java.util.ArrayList<>();
        int totalAllScores = 0;
        
        for (int i = 0; i < gameData.getPlayers().size(); i++) {
            com.example.rummypulse.data.Player player = gameData.getPlayers().get(i);
            
            // Calculate total score from input fields
            int totalScore = 0;
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
                        // Empty values are treated as 0, so no addition needed
                    } catch (NumberFormatException e) {
                        // Ignore invalid scores
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
            
            // Set rank
            TextView rankText = standingsRowView.findViewById(R.id.text_rank);
            if (i == 0) rankText.setText("🥇");
            else if (i == 1) rankText.setText("🥈");
            else if (i == 2) rankText.setText("🥉");
            else rankText.setText(String.valueOf(i + 1));

            // Set player name
            TextView playerName = standingsRowView.findViewById(R.id.text_player_name);
            playerName.setText(standing.player.getName());

            // Set player ID if available
            TextView playerId = standingsRowView.findViewById(R.id.text_player_id);
            if (gameData.getNumPlayers() > 2 && standing.player.getRandomNumber() != null) {
                playerId.setText("(#" + standing.player.getRandomNumber() + ")");
                playerId.setVisibility(View.VISIBLE);
            }

            // Set score with label
            TextView scoreText = standingsRowView.findViewById(R.id.text_score);
            scoreText.setText("Score: " + String.valueOf(standing.totalScore));

            // Set gross amount
            TextView grossAmountText = standingsRowView.findViewById(R.id.text_gross_amount);
            grossAmountText.setText("₹" + String.format("%.0f", standing.grossAmount));

            // Set GST with label
            TextView gstText = standingsRowView.findViewById(R.id.text_gst);
            gstText.setText("GST: ₹" + String.format("%.0f", standing.gstPaid));

            // Set net amount with label and conditional coloring
            TextView netAmountText = standingsRowView.findViewById(R.id.text_net_amount);
            netAmountText.setText("Net: ₹" + String.format("%.0f", standing.netAmount));
            
            // Color code based on positive/negative net amount
            if (standing.netAmount > 0) {
                // Positive net amount (winners) - green text and background
                netAmountText.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                netAmountText.setBackgroundColor(getResources().getColor(R.color.positive_background, getTheme()));
            } else {
                // Negative net amount (losers) - red text and background
                netAmountText.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                netAmountText.setBackgroundColor(getResources().getColor(R.color.negative_background, getTheme()));
            }

            binding.standingsTableContainer.addView(standingsRowView);
        }
    }

    private void updatePlayersInfo(com.example.rummypulse.data.GameData gameData) {
        // Update Players & Scores information card
        binding.textPlayersPointValue.setText("₹" + formatPointValue(gameData.getPointValue()) + " per point");
        binding.textPlayersGstRate.setText(String.format("%.0f", gameData.getGstPercent()) + "% GST");
        
        int currentRound = calculateCurrentRound(gameData);
        binding.textPlayersCurrentRound.setText("Round " + currentRound);
        
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
            // For standings section, toggle both the standings table and the chart
            View standingsTableContainer = findViewById(R.id.standings_table_container);
            View chartContainer = findViewById(R.id.chart_container);
            
            if (standingsTableContainer != null) {
                if (standingsTableContainer.getVisibility() == View.VISIBLE) {
                    // Collapse - hide the standings table and chart
                    standingsTableContainer.setVisibility(View.GONE);
                    // Also hide the chart section (find its parent LinearLayout)
                    if (chartContainer != null && chartContainer.getParent() != null) {
                        View chartSection = (View) chartContainer.getParent().getParent(); // FrameLayout -> LinearLayout
                        if (chartSection != null) {
                            chartSection.setVisibility(View.GONE);
                        }
                    }
                    arrowIcon.setImageResource(R.drawable.ic_expand_more);
                } else {
                    // Expand - show the standings table and chart
                    standingsTableContainer.setVisibility(View.VISIBLE);
                    // Also show the chart section
                    if (chartContainer != null && chartContainer.getParent() != null) {
                        View chartSection = (View) chartContainer.getParent().getParent(); // FrameLayout -> LinearLayout
                        if (chartSection != null) {
                            chartSection.setVisibility(View.VISIBLE);
                        }
                    }
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

    private void updateScoreChart(com.example.rummypulse.data.GameData gameData) {
        // Get the chart container
        android.widget.LinearLayout chartContainer = binding.chartContainer;

        // Clear existing bars
        chartContainer.removeAllViews();

        // Get all players and their total scores
        java.util.List<PlayerStanding> standings = new java.util.ArrayList<>();

        for (int i = 0; i < gameData.getPlayers().size(); i++) {
            com.example.rummypulse.data.Player player = gameData.getPlayers().get(i);
            int totalScore = 0;

            // Calculate total score from input fields
            for (int round = 1; round <= 10; round++) {
                EditText scoreInput = binding.playersContainer.findViewWithTag("p" + (i + 1) + "r" + round);
                if (scoreInput != null && !scoreInput.getText().toString().trim().isEmpty()) {
                    try {
                        int score = Integer.parseInt(scoreInput.getText().toString().trim());
                        if (score != -1) { // Don't count -1 values
                            totalScore += score;
                        }
                    } catch (NumberFormatException e) {
                        // Skip invalid scores
                    }
                }
            }

            PlayerStanding standing = new PlayerStanding();
            standing.player = player;
            standing.totalScore = totalScore;
            standings.add(standing);
        }

        // Sort by score (ascending - lower scores are better/winners)
        standings.sort((a, b) -> Integer.compare(a.totalScore, b.totalScore));

        // Find max score for scaling
        int maxScore = standings.isEmpty() ? 100 : Math.max(standings.get(standings.size() - 1).totalScore, 100);
        if (maxScore == 0) maxScore = 100; // Avoid division by zero

        // Create bars for each player
        for (PlayerStanding standing : standings) {
            // Create bar container
            android.widget.LinearLayout barContainer = new android.widget.LinearLayout(this);
            android.widget.LinearLayout.LayoutParams containerParams = new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            containerParams.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            barContainer.setLayoutParams(containerParams);
            barContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
            barContainer.setGravity(android.view.Gravity.CENTER);

            // Create the bar
            android.view.View bar = new android.view.View(this);
            // Reserve space for score label (20dp) and name label (~24dp), use remaining space for bar
            int availableHeight = dpToPx(140) - dpToPx(20) - dpToPx(24) - dpToPx(16); // Total - score label - name label - padding
            int barHeight = Math.max(dpToPx(20), (int) (standing.totalScore * availableHeight / (double) maxScore));
            android.widget.LinearLayout.LayoutParams barParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, barHeight);
            barParams.setMargins(0, 0, 0, dpToPx(4));
            bar.setLayoutParams(barParams);

            // Set bar color based on ranking (winners = green, losers = red/yellow)
            int rank = standings.indexOf(standing);
            if (rank < standings.size() / 2) {
                // Winners (lower scores) - green
                bar.setBackgroundColor(0xFF4CAF50);
            } else if (rank == standings.size() - 1) {
                // Highest score - red
                bar.setBackgroundColor(0xFFF44336);
            } else {
                // Middle scores - yellow
                bar.setBackgroundColor(0xFFFFEB3B);
            }

            // Create score value label (on top of bar)
            android.widget.TextView scoreLabel = new android.widget.TextView(this);
            scoreLabel.setText(String.valueOf(standing.totalScore));
            scoreLabel.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            scoreLabel.setTextSize(10);
            scoreLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            scoreLabel.setGravity(android.view.Gravity.CENTER);
            
            // Set fixed height for score label to prevent clipping
            android.widget.LinearLayout.LayoutParams scoreLabelParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(20));
            scoreLabelParams.setMargins(0, dpToPx(4), 0, dpToPx(2)); // Add top margin
            scoreLabel.setLayoutParams(scoreLabelParams);

            // Create player name label
            android.widget.TextView nameLabel = new android.widget.TextView(this);
            nameLabel.setText(standing.player.getName());
            nameLabel.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            nameLabel.setTextSize(12);
            nameLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            nameLabel.setGravity(android.view.Gravity.CENTER);

            // Add views to container
            barContainer.addView(scoreLabel);
            barContainer.addView(bar);
            barContainer.addView(nameLabel);

            // Add container to chart
            chartContainer.addView(barContainer);
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
            
            // Calculate total score from input fields
            for (int round = 1; round <= 10; round++) {
                EditText scoreInput = binding.playersContainer.findViewWithTag("p" + (i + 1) + "r" + round);
                if (scoreInput != null && !scoreInput.getText().toString().trim().isEmpty()) {
                    try {
                        totalScore += Integer.parseInt(scoreInput.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        // Skip invalid scores
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
        binding.textGstRateInfo.setText(String.format("%.0f", gameData.getGstPercent()) + "% GST");
        
        int currentRound = calculateCurrentRound(gameData);
        binding.textCurrentRoundInfo.setText("Round " + currentRound);
        
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
                winnersRule.setText("Winners (Green): Receive money but pay " + gstPercentText + " GST on winnings");
            }
            
            // Update Formula
            TextView formulaText = findViewById(R.id.text_settlement_formula);
            if (formulaText != null) {
                formulaText.setText("Formula: (Total All Scores - Your Score × " + playerCount + ") × " + pointValueText);
            }
            
            // Update GST Rule
            TextView gstRule = findViewById(R.id.text_settlement_gst_rule);
            if (gstRule != null) {
                gstRule.setText("GST: Only winners pay " + gstPercentText + " GST on positive amounts");
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
        // Create dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Add New Player");
        
        // Create input field
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        input.setHint("Enter player name");
        input.setTextColor(getResources().getColor(android.R.color.black));
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
        
        // Refresh UI
        displayGameData(gameData);
        
        ModernToast.success(this, "Player '" + playerName + "' added successfully!");
    }
    
    private void showDeletePlayerConfirmation(com.example.rummypulse.data.Player player, com.example.rummypulse.data.GameData gameData) {
        // Don't allow deleting if only 2 players remain
        if (gameData.getPlayers().size() <= 2) {
            ModernToast.warning(this, "Cannot delete player. Minimum 2 players required.");
            return;
        }
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Delete Player");
        builder.setMessage("Are you sure you want to delete '" + player.getName() + "'?\n\nThis action cannot be undone.");
        
        builder.setPositiveButton("Delete", (dialog, which) -> {
            deletePlayer(player, gameData);
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        
        // Make delete button red
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark));
    }
    
    private void deletePlayer(com.example.rummypulse.data.Player player, com.example.rummypulse.data.GameData gameData) {
        // Remove player from game data
        gameData.getPlayers().remove(player);
        gameData.setNumPlayers(gameData.getPlayers().size());
        
        // Refresh UI
        displayGameData(gameData);
        
        ModernToast.success(this, "Player '" + player.getName() + "' deleted successfully!");
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
