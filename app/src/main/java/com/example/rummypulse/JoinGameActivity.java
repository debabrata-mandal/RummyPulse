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
import androidx.lifecycle.ViewModelProvider;

import com.example.rummypulse.ui.join.JoinGameViewModel;
import com.example.rummypulse.utils.ModernToast;
import com.google.android.material.textfield.TextInputEditText;

public class JoinGameActivity extends AppCompatActivity {

    private JoinGameViewModel viewModel;
    private LinearLayout playersSection;
    private LinearLayout standingsCard;
    private LinearLayout playersContainer;
    private Button btnAddPlayer;
    private LinearLayout standingsTableContainer;
    private TextView textPointValueInfo;
    private TextView textGstInfo;
    private TextView textCurrentRound;
    private TextView textGameIdHeader;
    private TextView textAdminMode;
    private TextView textHeaderTitle;
    private ImageView btnClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_game);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(JoinGameViewModel.class);

        // Initialize views
        initializeViews();
        setupClickListeners();
        observeViewModel();

        // Set up back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Get game ID from intent if available
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("GAME_ID")) {
            String gameId = intent.getStringExtra("GAME_ID");
            String joinType = intent.getStringExtra("JOIN_TYPE");
            
            if (gameId != null) {
                // Update header title with actual game ID
                textHeaderTitle.setText("Game View: " + gameId);
                // Automatically join the game
                joinGame(gameId);
            }
        }
    }

    private void initializeViews() {
        playersSection = findViewById(R.id.players_section);
        standingsCard = findViewById(R.id.standings_card);
        playersContainer = findViewById(R.id.players_container);
        btnAddPlayer = findViewById(R.id.btn_add_player);
        standingsTableContainer = findViewById(R.id.standings_table_container);
        textPointValueInfo = findViewById(R.id.text_point_value_info);
        textGstInfo = findViewById(R.id.text_gst_info);
        textCurrentRound = findViewById(R.id.text_current_round);
        textGameIdHeader = findViewById(R.id.text_game_id_header);
        textAdminMode = findViewById(R.id.text_admin_mode);
        textHeaderTitle = findViewById(R.id.text_header_title);
        btnClose = findViewById(R.id.btn_close);

        // Initially hide the cards
        playersSection.setVisibility(View.GONE);
        standingsCard.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        btnClose.setOnClickListener(v -> finish());
        btnAddPlayer.setOnClickListener(v -> {
            // TODO: Show dialog to add new player
            ModernToast.info(this, "Add player feature coming soon!");
        });
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
                textAdminMode.setVisibility(View.VISIBLE);
                ModernToast.success(this, "✅ Edit access granted!");
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
        playersSection.setVisibility(View.VISIBLE);
        standingsCard.setVisibility(View.VISIBLE);

        // Get the game ID from intent
        Intent intent = getIntent();
        String gameId = "";
        if (intent != null && intent.hasExtra("GAME_ID")) {
            gameId = intent.getStringExtra("GAME_ID");
        }

        // Update header with game ID
        textGameIdHeader.setText(gameId);

        // Update game info bar
        textPointValueInfo.setText("₹" + String.format("%.0f", gameData.getPointValue()) + " /point");
        textGstInfo.setText(String.format("%.0f", gameData.getGstPercent()) + "% GST");

        // Generate player cards
        generatePlayerCards(gameData);

        // Generate standings table
        generateStandingsTable(gameData);

        // Update current round
        updateCurrentRound(gameData);
        
        // Update round validation (this is crucial for existing games)
        updateRoundValidation(gameData);
        
        // Update round colors
        updateRoundColors(gameData);

        // Hide admin mode initially
        textAdminMode.setVisibility(View.GONE);
    }

    private void generatePlayerCards(com.example.rummypulse.data.GameData gameData) {
        // Clear existing player cards
        playersContainer.removeAllViews();

        // Add player cards
        for (int i = 0; i < gameData.getPlayers().size(); i++) {
            final int playerIndex = i;
            com.example.rummypulse.data.Player player = gameData.getPlayers().get(i);
            View playerCardView = LayoutInflater.from(this).inflate(R.layout.item_player_card, playersContainer, false);
            
            // Set player name
            TextView playerName = playerCardView.findViewById(R.id.text_player_name);
            playerName.setText(player.getName());

            // Set player ID if available (for games with more than 2 players)
            TextView playerId = playerCardView.findViewById(R.id.text_player_id);
            if (gameData.getNumPlayers() > 2 && player.getRandomNumber() != null) {
                playerId.setText("#" + player.getRandomNumber());
                playerId.setVisibility(View.VISIBLE);
            }

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
                    }
                });
            }


            playersContainer.addView(playerCardView);
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
            EditText previousScoreInput = playersContainer.findViewWithTag("p" + (p + 1) + "r" + round);
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
                EditText scoreInput = playersContainer.findViewWithTag("p" + (playerIndex + 1) + "r" + (round + 1));
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
            View playerCardView = playersContainer.getChildAt(playerIndex);
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
            EditText scoreInput = playersContainer.findViewWithTag("p" + (p + 1) + "r" + round);
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
        textCurrentRound.setText("Round#" + currentRound + " Round");
        
        // Update all player cards' round badges
        for (int i = 0; i < playersContainer.getChildCount(); i++) {
            View playerCardView = playersContainer.getChildAt(i);
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
                EditText scoreInput = playersContainer.findViewWithTag("p" + (playerIndex + 1) + "r" + round);
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
        standingsTableContainer.removeAllViews();

        // Add header
        View headerView = LayoutInflater.from(this).inflate(R.layout.item_standings_header, standingsTableContainer, false);
        standingsTableContainer.addView(headerView);

        // Calculate standings
        updateStandings(gameData);
    }

    private void updateStandings(com.example.rummypulse.data.GameData gameData) {
        // Clear existing standings rows (keep header)
        if (standingsTableContainer.getChildCount() > 1) {
            standingsTableContainer.removeViews(1, standingsTableContainer.getChildCount() - 1);
        }

        // First pass: collect all scores
        java.util.List<PlayerStanding> standings = new java.util.ArrayList<>();
        int totalAllScores = 0;
        
        for (int i = 0; i < gameData.getPlayers().size(); i++) {
            com.example.rummypulse.data.Player player = gameData.getPlayers().get(i);
            
            // Calculate total score from input fields
            int totalScore = 0;
            for (int round = 1; round <= 10; round++) {
                EditText scoreInput = playersContainer.findViewWithTag("p" + (i + 1) + "r" + round);
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
            View standingsRowView = LayoutInflater.from(this).inflate(R.layout.item_standings_row, standingsTableContainer, false);
            
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

            // Set score
            TextView scoreText = standingsRowView.findViewById(R.id.text_score);
            scoreText.setText(String.valueOf(standing.totalScore));

            // Set gross amount
            TextView grossAmountText = standingsRowView.findViewById(R.id.text_gross_amount);
            grossAmountText.setText("₹" + String.format("%.0f", standing.grossAmount));

            // Set GST
            TextView gstText = standingsRowView.findViewById(R.id.text_gst);
            gstText.setText("₹" + String.format("%.0f", standing.gstPaid));

            // Set net amount
            TextView netAmountText = standingsRowView.findViewById(R.id.text_net_amount);
            netAmountText.setText("₹" + String.format("%.0f", standing.netAmount));

            standingsTableContainer.addView(standingsRowView);
        }
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
