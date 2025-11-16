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
    
    // Network monitoring
    private android.net.ConnectivityManager.NetworkCallback networkCallback;
    private boolean isConnected = true;
    private android.os.Handler reconnectHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    
    // Text-to-Speech
    private android.speech.tts.TextToSpeech textToSpeech;
    private boolean ttsInitialized = false;
    private android.os.Handler ttsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private java.util.Map<String, Runnable> announcementRunnables = new java.util.HashMap<>();
    private java.util.Locale currentTtsLocale; // Will be loaded from preferences
    
    // Track previous scores for view mode announcements
    private java.util.Map<String, java.util.List<Integer>> previousPlayerScores = new java.util.HashMap<>();
    private boolean gameCompletionAnnounced = false;
    
    // Anti-spam: Track last announced values per player-round to prevent duplicate announcements
    private java.util.Map<String, Integer> lastAnnouncedScores = new java.util.HashMap<>();
    
    // Announcement queue management
    private java.util.Queue<Runnable> announcementQueue = new java.util.LinkedList<>();
    private boolean isAnnouncementInProgress = false;
    private static final long ANNOUNCEMENT_GAP_MS = 1000; // 1 second gap between announcements
    
    // Timing constants
    private static final long EDIT_MODE_DEBOUNCE_MS = 4000; // 4 seconds for edit mode
    private static final long VIEW_MODE_DEBOUNCE_MS = 2000; // 2 seconds for view mode
    private static final long BULK_UPDATE_DEBOUNCE_MS = 6000; // 6 seconds for bulk updates
    private static final long GAME_COMPLETION_BUFFER_MS = 2000; // 2 seconds buffer after pending announcements
    private static final long MAX_GAME_COMPLETION_DELAY_MS = 10000; // Maximum 10 seconds delay
    
    // Bulk update detection
    private long lastScoreChangeTime = 0;
    private int scoreChangesInWindow = 0;
    
    // Utterance ID counter for TTS tracking
    private int utteranceIdCounter = 0;

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
        
        // Setup network monitoring
        setupNetworkMonitoring();
        
        // Load saved language preference
        currentTtsLocale = loadLanguagePreference();
        
        // Initialize Text-to-Speech
        initializeTextToSpeech();

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
                
                // Automatically join the game
                if (isCreator) {
                    ModernToast.success(this, "🎮 Welcome to your new game!");
                    // For creator, join game and auto-grant edit access
                    // First join to load game data and fetch PIN
                    joinGameAsCreator(gameId);
                } else {
                    // Regular join without edit access
                    joinGame(gameId);
                }
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        handleBackPress();
        return true;
    }
    
    @Override
    public void onBackPressed() {
        handleBackPress();
    }
    
    private void handleBackPress() {
        // Check if user has edit access
        Boolean editAccess = viewModel.getEditAccessGranted().getValue();
        if (editAccess != null && editAccess) {
            // Show warning dialog before exiting edit mode
            showExitWarningDialog();
        } else {
            // Normal exit for view mode
            finish();
        }
    }
    
    private void showExitWarningDialog() {
        // Create custom dialog
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_exit_edit_mode);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.setCancelable(true);
        
        // Get views
        android.widget.CheckBox checkboxForgetPin = dialog.findViewById(R.id.checkbox_forget_pin);
        Button btnStay = dialog.findViewById(R.id.btn_stay);
        Button btnExit = dialog.findViewById(R.id.btn_exit);
        
        // Set up buttons
        btnStay.setOnClickListener(v -> dialog.dismiss());
        
        btnExit.setOnClickListener(v -> {
            // Check if user wants to forget the PIN
            if (checkboxForgetPin.isChecked() && currentGameId != null) {
                clearSavedPin(currentGameId);
                ModernToast.info(this, "PIN forgotten. You'll need to enter it again next time.");
            }
            dialog.dismiss();
            finish();
        });
        
        dialog.show();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Refresh standings when returning to the activity
        // This ensures calculations are updated even if network was offline
        com.example.rummypulse.data.GameData currentGameData = viewModel.getGameData().getValue();
        if (currentGameData != null) {
            System.out.println("onResume: Refreshing standings with cached game data");
            // Force a complete refresh of the standings
            // updateStandings now handles both view and edit mode intelligently
            generateStandingsTable(currentGameData);
            updateStandingsInfo(currentGameData);
        } else {
            System.out.println("onResume: No cached game data available, will wait for data to load");
        }
    }
    
    /**
     * Initialize Text-to-Speech engine with Bengali language support
     */
    private void initializeTextToSpeech() {
        textToSpeech = new android.speech.tts.TextToSpeech(this, status -> {
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                // Try Bengali first, fall back to English if not available
                int result = textToSpeech.setLanguage(currentTtsLocale);
                
                if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || 
                    result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Bengali not available, fall back to English
                    System.out.println("Bengali TTS not available, falling back to English");
                    currentTtsLocale = java.util.Locale.US;
                    result = textToSpeech.setLanguage(currentTtsLocale);
                }
                
                if (result != android.speech.tts.TextToSpeech.LANG_MISSING_DATA && 
                    result != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsInitialized = true;
                    System.out.println("TTS initialized successfully with locale: " + currentTtsLocale.getDisplayLanguage());
                    System.out.println("TTS initialized successfully with locale: " + currentTtsLocale.getDisplayLanguage());
                    
                    // Set up utterance progress listener to know when speech completes
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                        textToSpeech.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                            @Override
                            public void onStart(String utteranceId) {
                                System.out.println("TTS: Started speaking utterance: " + utteranceId);
                            }
                            
                            @Override
                            public void onDone(String utteranceId) {
                                System.out.println("TTS: Finished speaking utterance: " + utteranceId);
                                // Process next announcement after this one completes
                                ttsHandler.postDelayed(() -> {
                                    isAnnouncementInProgress = false;
                                    processAnnouncementQueue();
                                }, ANNOUNCEMENT_GAP_MS);
                            }
                            
                            @Override
                            public void onError(String utteranceId) {
                                System.err.println("TTS: Error speaking utterance: " + utteranceId);
                                // Still process next announcement on error
                                ttsHandler.postDelayed(() -> {
                                    isAnnouncementInProgress = false;
                                    processAnnouncementQueue();
                                }, ANNOUNCEMENT_GAP_MS);
                            }
                        });
                    }
                } else {
                    System.out.println("TTS language not supported");
                }
            } else {
                System.out.println("TTS initialization failed");
            }
        });
    }
    
    /**
     * Announce player name and score with context-aware debounce and anti-spam
     * @param playerName Name of the player
     * @param score Score entered
     * @param playerIndex Index of the player (for unique key)
     * @param round Round number (1-based)
     * @param isEditMode True if in edit mode, false if in view mode
     */
    private void announceScoreWithDebounce(String playerName, int score, int playerIndex, int round, boolean isEditMode) {
        // Check if muted
        if (com.example.rummypulse.utils.LanguagePreferenceManager.isMuted(this)) {
            return;
        }
        
        if (!ttsInitialized || textToSpeech == null) {
            return;
        }
        
        // Anti-spam: Check if this exact score was already announced for this player-round
        String scoreKey = "player_" + playerIndex + "_round_" + round;
        Integer lastAnnouncedScore = lastAnnouncedScores.get(scoreKey);
        if (lastAnnouncedScore != null && lastAnnouncedScore == score) {
            System.out.println("TTS: Skipping duplicate announcement for " + playerName + " round " + round + " score " + score);
            return;
        }
        
        // Create unique key for this player
        String key = "player_" + playerIndex;
        
        // Cancel any pending announcement for this player
        Runnable existingRunnable = announcementRunnables.get(key);
        if (existingRunnable != null) {
            ttsHandler.removeCallbacks(existingRunnable);
        }
        
        // Detect bulk updates: multiple score changes within 1 second
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScoreChangeTime < 1000) {
            scoreChangesInWindow++;
        } else {
            scoreChangesInWindow = 1;
        }
        lastScoreChangeTime = currentTime;
        
        // Determine debounce delay based on context
        long debounceDelay;
        if (scoreChangesInWindow > 2) {
            // Bulk update detected: use longer delay
            debounceDelay = BULK_UPDATE_DEBOUNCE_MS;
            System.out.println("TTS: Bulk update detected, using " + debounceDelay + "ms delay");
        } else if (isEditMode) {
            // Edit mode: user is actively typing
            debounceDelay = EDIT_MODE_DEBOUNCE_MS;
        } else {
            // View mode: Firebase update, faster response
            debounceDelay = VIEW_MODE_DEBOUNCE_MS;
        }
        
        // Create new announcement runnable
        Runnable announcementRunnable = () -> {
            announceScore(playerName, score, round);
            announcementRunnables.remove(key);
            // Update last announced score for anti-spam
            lastAnnouncedScores.put(scoreKey, score);
        };
        
        // Store and schedule the announcement
        announcementRunnables.put(key, announcementRunnable);
        ttsHandler.postDelayed(announcementRunnable, debounceDelay);
        
        System.out.println("TTS: Scheduled announcement for " + playerName + " round " + round + " with " + debounceDelay + "ms delay");
    }
    
    /**
     * Overloaded method for backward compatibility (defaults to edit mode)
     */
    private void announceScoreWithDebounce(String playerName, int score, int playerIndex, int round) {
        announceScoreWithDebounce(playerName, score, playerIndex, round, true);
    }
    
    /**
     * Announce player name and score using TTS with queue management
     * Supports Bengali and English announcements
     * @param playerName Name of the player
     * @param score Score to announce
     * @param round Round number (1-based)
     */
    private void announceScore(String playerName, int score, int round) {
        if (!ttsInitialized || textToSpeech == null) {
            return;
        }
        
        // Skip announcement for -1 (placeholder value)
        if (score == -1) {
            return;
        }
        
        // Create announcement text based on current locale
        String announcement;
        if (currentTtsLocale.getLanguage().equals("bn")) {
            // Bengali announcement: "খেলোয়াড় X রাউন্ড X এ xxx পয়েন্ট স্কোর করেছে"
            announcement = playerName + " রাউন্ড " + round + " এ " + score + " পয়েন্ট স্কোর করেছে";
        } else {
            // English announcement: "Player X score xxx point in round X"
            announcement = playerName + " score " + score + " point in round " + round;
        }
        
        // Add to queue instead of speaking directly
        queueAnnouncement(() -> {
            String utteranceId = "score_" + (utteranceIdCounter++);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                android.os.Bundle params = new android.os.Bundle();
                textToSpeech.speak(announcement, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, utteranceId);
            } else {
                java.util.HashMap<String, String> params = new java.util.HashMap<>();
                params.put(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                textToSpeech.speak(announcement, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params);
            }
            System.out.println("TTS Speaking: " + announcement);
        });
    }
    
    /**
     * Queue an announcement with minimum gap between announcements
     * @param announcementRunnable The runnable that performs the TTS speak
     */
    private void queueAnnouncement(Runnable announcementRunnable) {
        announcementQueue.offer(announcementRunnable);
        processAnnouncementQueue();
    }
    
    /**
     * Process the announcement queue with gaps between announcements
     * The UtteranceProgressListener will trigger processing of the next announcement
     * when the current one completes
     */
    private void processAnnouncementQueue() {
        if (isAnnouncementInProgress || announcementQueue.isEmpty()) {
            return;
        }
        
        isAnnouncementInProgress = true;
        Runnable nextAnnouncement = announcementQueue.poll();
        
        if (nextAnnouncement != null) {
            // Execute the announcement (which will call textToSpeech.speak with utterance ID)
            // The UtteranceProgressListener.onDone will be called when speech completes,
            // which will then call processAnnouncementQueue again for the next announcement
            nextAnnouncement.run();
        } else {
            isAnnouncementInProgress = false;
        }
    }
    
    /**
     * Clear all pending announcements (used when TTS is disabled or activity destroyed)
     */
    private void clearAnnouncementQueue() {
        announcementQueue.clear();
        isAnnouncementInProgress = false;
    }
    
    /**
     * Check for score changes and announce them in view mode (for real-time updates)
     * @param gameData Current game data with updated scores
     */
    private void announceScoreChangesInViewMode(com.example.rummypulse.data.GameData gameData) {
        if (!ttsInitialized || textToSpeech == null || gameData == null || gameData.getPlayers() == null) {
            return;
        }
        
        // Check each player for score changes
        for (int i = 0; i < gameData.getPlayers().size(); i++) {
            com.example.rummypulse.data.Player player = gameData.getPlayers().get(i);
            String playerKey = "player_" + i + "_" + player.getName();
            
            java.util.List<Integer> currentScores = player.getScores();
            java.util.List<Integer> previousScores = previousPlayerScores.get(playerKey);
            
            if (currentScores != null) {
                if (previousScores == null) {
                    // First time seeing this player - store scores but don't announce
                    previousPlayerScores.put(playerKey, new java.util.ArrayList<>(currentScores));
                } else {
                    // Check for differences
                    for (int round = 0; round < Math.min(currentScores.size(), previousScores.size()); round++) {
                        Integer currentScore = currentScores.get(round);
                        Integer previousScore = previousScores.get(round);
                        
                        // If score changed and is valid (not -1)
                        if (currentScore != null && !currentScore.equals(previousScore) && currentScore != -1) {
                            // Announce the new score with VIEW MODE debounce (2 seconds - faster)
                            final int playerIndex = i;
                            final int scoreToAnnounce = currentScore;
                            final int roundNumber = round + 1; // Convert to 1-based
                            announceScoreWithDebounce(player.getName(), scoreToAnnounce, playerIndex, roundNumber, false);
                        }
                    }
                    
                    // Update stored scores
                    previousPlayerScores.put(playerKey, new java.util.ArrayList<>(currentScores));
                }
            }
        }
    }
    
    /**
     * Announce game completion results when all 10 rounds are finished
     * @param gameData Game data with all scores
     */
    private void announceGameCompletion(com.example.rummypulse.data.GameData gameData) {
        // Check if muted
        if (com.example.rummypulse.utils.LanguagePreferenceManager.isMuted(this)) {
            return;
        }
        
        if (!ttsInitialized || textToSpeech == null || gameData == null || gameCompletionAnnounced) {
            return;
        }
        
        // Check if game is actually completed
        if (!isGameCompleted(gameData)) {
            return;
        }
        
        gameCompletionAnnounced = true;
        
        // Calculate dynamic delay based on pending announcements
        long calculatedDelay = calculateGameCompletionDelay();
        
        System.out.println("TTS: Scheduling game completion announcement with dynamic delay of " + calculatedDelay + "ms");
        
        // Delay the game completion announcement to ensure all score announcements complete first
        ttsHandler.postDelayed(() -> {
            // Calculate standings
            java.util.List<PlayerStanding> standings = calculateStandings(gameData);
            
            // Calculate total contribution
            double totalContribution = calculateTotalContribution(gameData);
            
            // Sort by total score (ascending - lower is better)
            standings.sort((a, b) -> Integer.compare(a.totalScore, b.totalScore));
            
            // Build announcement based on current locale
            StringBuilder announcement = new StringBuilder();
            
            if (currentTtsLocale.getLanguage().equals("bn")) {
                // Bengali announcement
                announcement.append("খেলা শেষ। চূড়ান্ত ফলাফল। ");
                
                // Announce each player's results
                for (int i = 0; i < standings.size(); i++) {
                    PlayerStanding standing = standings.get(i);
                    String playerName = standing.player.getName();
                    int totalScore = standing.totalScore;
                    double netAmount = standing.netAmount;
                    
                    announcement.append(playerName).append(" মোট স্কোর ").append(totalScore).append(" পয়েন্ট। ");
                    
                    if (netAmount > 0) {
                        announcement.append("পাবেন ").append(String.format("%.0f", netAmount)).append(" টাকা। ");
                    } else if (netAmount < 0) {
                        announcement.append("দিতে হবে ").append(String.format("%.0f", Math.abs(netAmount))).append(" টাকা। ");
                    } else {
                        announcement.append("কোন পেমেন্ট নেই। ");
                    }
                }
                
                // Announce total contribution
                if (totalContribution > 0) {
                    announcement.append("মোট অবদান সংগৃহীত ")
                               .append(String.format("%.0f", totalContribution))
                               .append(" টাকা।");
                }
            } else {
                // English announcement
                announcement.append("Game over. Final results. ");
                
                // Announce each player's results
                for (int i = 0; i < standings.size(); i++) {
                    PlayerStanding standing = standings.get(i);
                    String playerName = standing.player.getName();
                    int totalScore = standing.totalScore;
                    double netAmount = standing.netAmount;
                    
                    announcement.append(playerName).append(" total score ").append(totalScore).append(" point. ");
                    
                    if (netAmount > 0) {
                        announcement.append("Will receive ").append(String.format("%.0f", netAmount)).append(" rupees. ");
                    } else if (netAmount < 0) {
                        announcement.append("Will pay ").append(String.format("%.0f", Math.abs(netAmount))).append(" rupees. ");
                    } else {
                        announcement.append("No payment. ");
                    }
                }
                
                // Announce total contribution
                if (totalContribution > 0) {
                    announcement.append("Total contribution collected is ")
                               .append(String.format("%.0f", totalContribution))
                               .append(" rupees.");
                }
            }
            
            // Queue the announcement (don't speak directly)
            final String announcementText = announcement.toString();
            System.out.println("TTS Game Completion: " + announcementText);
            
            queueAnnouncement(() -> {
                String utteranceId = "game_completion_" + (utteranceIdCounter++);
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    android.os.Bundle params = new android.os.Bundle();
                    textToSpeech.speak(announcementText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, utteranceId);
                } else {
                    java.util.HashMap<String, String> params = new java.util.HashMap<>();
                    params.put(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                    textToSpeech.speak(announcementText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params);
                }
            });
        }, calculatedDelay);
    }
    
    /**
     * Calculate dynamic delay for game completion announcement
     * Checks pending score announcements and adds buffer
     * @return Delay in milliseconds (capped at MAX_GAME_COMPLETION_DELAY_MS)
     */
    private long calculateGameCompletionDelay() {
        // Count pending score announcements
        int pendingAnnouncements = announcementRunnables.size();
        
        if (pendingAnnouncements == 0) {
            // No pending announcements, use minimum buffer
            return GAME_COMPLETION_BUFFER_MS;
        }
        
        // Calculate delay: longest debounce time + buffer
        // We use the maximum possible debounce (BULK_UPDATE or EDIT_MODE) + buffer
        long maxDebounceTime = Math.max(EDIT_MODE_DEBOUNCE_MS, BULK_UPDATE_DEBOUNCE_MS);
        long calculatedDelay = maxDebounceTime + GAME_COMPLETION_BUFFER_MS;
        
        // Cap at maximum delay
        long finalDelay = Math.min(calculatedDelay, MAX_GAME_COMPLETION_DELAY_MS);
        
        System.out.println("TTS: Pending announcements: " + pendingAnnouncements + 
                          ", calculated delay: " + calculatedDelay + "ms, final delay: " + finalDelay + "ms");
        
        return finalDelay;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up Firestore listener to prevent memory leaks
        if (gameDataListener != null) {
            gameDataListener.remove();
            gameDataListener = null;
        }
        
        // Unregister network callback
        if (networkCallback != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.net.ConnectivityManager connectivityManager = 
                (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                try {
                    connectivityManager.unregisterNetworkCallback(networkCallback);
                } catch (Exception e) {
                    System.err.println("Error unregistering network callback: " + e.getMessage());
                }
            }
        }
        
        // Remove any pending reconnect tasks
        reconnectHandler.removeCallbacksAndMessages(null);
        
        // Clean up Text-to-Speech
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        
        // Clear announcement queue
        clearAnnouncementQueue();
        
        // Remove any pending TTS announcements
        ttsHandler.removeCallbacksAndMessages(null);
        
        // Clear tracking maps
        previousPlayerScores.clear();
        lastAnnouncedScores.clear();
        announcementRunnables.clear();
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
    
    /**
     * Switch TTS language dynamically
     * @param locale New locale to use for TTS
     */
    private void switchLanguage(java.util.Locale locale) {
        if (textToSpeech == null || !ttsInitialized) {
            ModernToast.error(this, "Text-to-Speech not initialized");
            return;
        }
        
        int result = textToSpeech.setLanguage(locale);
        
        if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || 
            result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
            // Language not available
            String languageName = locale.getDisplayLanguage();
            ModernToast.error(this, languageName + " voice not available. Please install language data.");
            
            // Show dialog to install language data
            showInstallLanguageDialog(locale);
        } else {
            // Language switched successfully
            currentTtsLocale = locale;
            String languageName = locale.getDisplayLanguage();
            ModernToast.success(this, "Voice language changed to " + languageName);
            System.out.println("TTS language switched to: " + languageName);
            
            // Save preference using utility class
            com.example.rummypulse.utils.LanguagePreferenceManager.saveLanguagePreference(this, locale);
        }
    }
    
    /**
     * Show dialog to guide user to install TTS language data
     */
    private void showInstallLanguageDialog(java.util.Locale locale) {
        String languageName = locale.getDisplayLanguage();
        
        new android.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("Language Data Missing")
            .setMessage(languageName + " voice is not installed on your device. Would you like to install it from Google Play Store?")
            .setPositiveButton("Install", (dialog, which) -> {
                // Open TTS settings or Play Store
                try {
                    Intent intent = new Intent();
                    intent.setAction(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(intent);
                } catch (Exception e) {
                    ModernToast.error(this, "Could not open TTS settings");
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Load language preference from utility class
     */
    private java.util.Locale loadLanguagePreference() {
        return com.example.rummypulse.utils.LanguagePreferenceManager.loadLanguagePreference(this);
    }

    private void initializeViews() {
        // Initially hide the cards and FAB
        binding.playersSection.setVisibility(View.GONE);
        binding.standingsCard.setVisibility(View.GONE);
        binding.btnAddPlayer.setVisibility(View.GONE); // Hide Add Player FAB in view mode
        binding.btnRefresh.setVisibility(View.GONE); // Hide Refresh FAB initially
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
            
            // Set placeholder round badge
            TextView roundBadge = playerCardView.findViewById(R.id.text_current_round_badge);
            if (roundBadge != null) {
                roundBadge.setText("Round#1");
            }
            
            binding.playersContainer.addView(playerCardView);
        }
    }

    private void setupClickListeners() {
        binding.btnClose.setOnClickListener(v -> handleBackPress());
        binding.btnAddPlayer.setOnClickListener(v -> {
            addNewPlayerDirectly();
        });
        
        // Setup refresh button click listener (View Mode Only)
        binding.btnRefresh.setOnClickListener(v -> {
            if (currentGameId != null) {
                ModernToast.info(this, "Refreshing game data...");
                viewModel.refreshGameData(currentGameId);
            }
        });
        
        // Setup QR code click listener
        binding.iconQrCodeHeader.setOnClickListener(v -> {
            if (currentGameId != null) {
                showQrCodeDialog(currentGameId);
            }
        });
        
        // Setup share button click listener
        binding.btnShareHeader.setOnClickListener(v -> {
            shareStandingsToWhatsApp();
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
                    // If edit access is already granted, check if player cards need to be generated
                    // Check if REAL player cards exist (with EditText fields that have tags)
                    // Loading placeholders don't have these tags, so we'll regenerate
                    boolean realPlayerCardsExist = false;
                    if (binding.playersContainer.getChildCount() > 0) {
                        // Check if first card has a score input with a tag (real player card)
                        View firstCard = binding.playersContainer.getChildAt(0);
                        EditText firstScoreInput = firstCard.findViewById(R.id.edit_score_r1);
                        if (firstScoreInput != null && firstScoreInput.getTag() != null) {
                            realPlayerCardsExist = true;
                        }
                    }
                    
                    if (!realPlayerCardsExist) {
                        System.out.println("Player cards don't exist yet (or are placeholders) - generating them");
                        updatePlayersInfo(gameData);
                        generatePlayerCards(gameData);
                        // Update current round indicator and validation
                        updateCurrentRound(gameData);
                        updateRoundValidation(gameData);
                        updateRoundColors(gameData);
                    } else {
                        System.out.println("Real player cards already exist - skipping regeneration to preserve user input");
                        // Update player info section, standings, and validation, but don't touch the input fields
                        updatePlayersInfo(gameData);
                        updateStandings(gameData);
                        updateCurrentRound(gameData);
                        updateRoundValidation(gameData);
                        updateRoundColors(gameData);
                    }
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
                // Show Players section and Add Player FAB when edit access is granted
                binding.playersSection.setVisibility(View.VISIBLE);
                binding.btnAddPlayer.setVisibility(View.VISIBLE);
                // Hide Refresh FAB in edit mode
                binding.btnRefresh.setVisibility(View.GONE);
                
                // Always show loading placeholders when switching to edit mode
                // The real player cards will be generated when fresh data arrives in the gameData observer
                showLoadingPlayerCards();
                System.out.println("Edit access granted - showing loading placeholders, waiting for fresh data");
                
                // Hide the 3-dot menu when edit access is granted
                invalidateOptionsMenu();
                // Don't show duplicate success message here since it's already shown in ViewModel
                System.out.println("Edit access granted - Players section should now be visible");
                
                // Save PIN for persistent edit access across app restarts
                String pin = viewModel.getGamePin().getValue();
                if (currentGameId != null && pin != null) {
                    savePin(currentGameId, pin);
                }
                
                // Show online/offline indicator when edit access is granted
                runOnUiThread(() -> {
                    updateOnlineOfflineIndicators();
                });
                
                // Update header PIN visibility (show masked in edit mode)
                updateHeaderPinVisibility();
                
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
                // Hide online/offline indicators in view mode
                if (binding.editOnlineIndicator != null && binding.editOfflineIndicator != null) {
                    binding.editOnlineIndicator.setVisibility(View.GONE);
                    binding.editOfflineIndicator.setVisibility(View.GONE);
                }
                // Show Refresh FAB in view mode (only when standings are visible)
                if (binding.standingsCard != null && binding.standingsCard.getVisibility() == View.VISIBLE) {
                    binding.btnRefresh.setVisibility(View.VISIBLE);
                }
                // Update header PIN visibility (hide in view mode)
                updateHeaderPinVisibility();
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
                // Update header PIN display
                updateHeaderPinVisibility();
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

        currentGameId = gameId;
        
        // Check if user had edit access for this game previously
        String savedPin = getSavedPin(gameId);
        if (savedPin != null) {
            // User had edit access before, try to restore it
            System.out.println("Restoring edit access for game: " + gameId);
            viewModel.joinGame(gameId, true, savedPin);
        } else {
            // Join game in view mode (no PIN required)
            viewModel.joinGame(gameId, false, null);
        }
    }
    
    /**
     * Join game as creator with automatic edit access and PIN saving
     */
    private void joinGameAsCreator(String gameId) {
        currentGameId = gameId;
        
        // Fetch the game PIN from Firebase and grant edit access
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("games")
            .document(gameId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    try {
                        com.example.rummypulse.data.GameAuth gameAuth = 
                            documentSnapshot.toObject(com.example.rummypulse.data.GameAuth.class);
                        if (gameAuth != null && gameAuth.getPin() != null) {
                            String pin = gameAuth.getPin();
                            System.out.println("Creator PIN fetched: " + pin);
                            
                            // Save the PIN immediately for persistent edit access
                            savePin(gameId, pin);
                            
                            // Join with the PIN to grant edit access
                            viewModel.joinGame(gameId, true, pin);
                        } else {
                            System.err.println("PIN not found for creator game");
                            // Fallback to normal join
                            viewModel.joinGame(gameId, false, null);
                        }
                    } catch (Exception e) {
                        System.err.println("Error fetching creator PIN: " + e.getMessage());
                        viewModel.joinGame(gameId, false, null);
                    }
                } else {
                    ModernToast.error(this, "Game not found");
                }
            })
            .addOnFailureListener(e -> {
                System.err.println("Failed to fetch game for creator: " + e.getMessage());
                ModernToast.error(this, "Failed to load game");
            });
    }
    
    /**
     * Save PIN for a game when edit access is granted
     */
    private void savePin(String gameId, String pin) {
        if (gameId != null && pin != null) {
            android.content.SharedPreferences prefs = getSharedPreferences("RummyPulse_EditAccess", MODE_PRIVATE);
            prefs.edit().putString("pin_" + gameId, pin).apply();
            System.out.println("PIN saved for game: " + gameId);
        }
    }
    
    /**
     * Get saved PIN for a game
     */
    private String getSavedPin(String gameId) {
        if (gameId != null) {
            android.content.SharedPreferences prefs = getSharedPreferences("RummyPulse_EditAccess", MODE_PRIVATE);
            return prefs.getString("pin_" + gameId, null);
        }
        return null;
    }
    
    /**
     * Clear saved PIN for a game
     */
    private void clearSavedPin(String gameId) {
        if (gameId != null) {
            android.content.SharedPreferences prefs = getSharedPreferences("RummyPulse_EditAccess", MODE_PRIVATE);
            prefs.edit().remove("pin_" + gameId).apply();
            System.out.println("PIN cleared for game: " + gameId);
        }
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
            // Show Refresh FAB in view mode
            binding.btnRefresh.setVisibility(View.VISIBLE);
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

        // Update compact info header (dashboard style)
        updateGameInfoHeader(gameData);

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

    private void updateGameInfoHeader(com.example.rummypulse.data.GameData gameData) {
        // Show the header
        binding.gameInfoHeader.setVisibility(View.VISIBLE);
        
        // Setup Share button click listener (set up each time header is updated)
        binding.btnShareHeader.setOnClickListener(v -> {
            shareStandingsToWhatsApp();
        });
        
        // Update Point Value
        binding.textHeaderPointValue.setText("₹" + formatPointValue(gameData.getPointValue()));
        
        // Update Number of Players
        int numberOfPlayers = gameData.getPlayers() != null ? gameData.getPlayers().size() : 0;
        binding.textHeaderPlayers.setText(String.valueOf(numberOfPlayers));
        
        // Update Current Round (or "Game Over" if completed)
        int currentRound = calculateCurrentRound(gameData);
        if (currentRound == 10 && isGameCompleted(gameData)) {
            binding.textHeaderCurrentRound.setText("Game Over");
        } else {
            binding.textHeaderCurrentRound.setText(String.valueOf(currentRound));
        }
        
        // Update Contribution %
        binding.textHeaderContribution.setText(String.format("%.0f", gameData.getGstPercent()));
        
        // Update Total Contribution Amount (rounded, no decimals)
        double totalContribution = calculateTotalContribution(gameData);
        binding.textHeaderTotalContribution.setText("₹" + Math.round(totalContribution));
        
        // Update Game PIN visibility (show masked in edit mode)
        updateHeaderPinVisibility();
    }
    
    private void updateHeaderPinVisibility() {
        Boolean editAccess = viewModel.getEditAccessGranted().getValue();
        
        if (editAccess != null && editAccess && currentGamePin != null) {
            // Show PIN section in edit mode
            binding.headerPinSection.setVisibility(View.VISIBLE);
            binding.headerPinDivider.setVisibility(View.VISIBLE);
            
            // Display the PIN as masked initially
            binding.textHeaderGamePin.setText("****");
            binding.textHeaderGamePin.setTag(currentGamePin); // Store actual PIN in tag
            
            // Set up PIN visibility toggle
            binding.iconTogglePin.setOnClickListener(v -> {
                String currentText = binding.textHeaderGamePin.getText().toString();
                if (currentText.equals("****")) {
                    // Show the actual PIN
                    binding.textHeaderGamePin.setText(currentGamePin);
                    binding.iconTogglePin.setImageResource(R.drawable.ic_visibility_off);
                    
                    // Auto-hide PIN after 10 seconds
                    binding.textHeaderGamePin.postDelayed(() -> {
                        binding.textHeaderGamePin.setText("****");
                        binding.iconTogglePin.setImageResource(R.drawable.ic_visibility);
                    }, 10000);
                } else {
                    // Hide the PIN
                    binding.textHeaderGamePin.setText("****");
                    binding.iconTogglePin.setImageResource(R.drawable.ic_visibility);
                }
            });
        } else {
            // Hide PIN section in view mode
            binding.headerPinSection.setVisibility(View.GONE);
            binding.headerPinDivider.setVisibility(View.GONE);
        }
    }
    
    private double calculateTotalContribution(com.example.rummypulse.data.GameData gameData) {
        if (gameData == null || gameData.getPlayers() == null) {
            return 0.0;
        }
        
        double totalContribution = 0.0;
        int numPlayers = gameData.getPlayers().size();
        
        for (com.example.rummypulse.data.Player player : gameData.getPlayers()) {
            // Calculate settlement for each player
            int totalScore = 0;
            for (com.example.rummypulse.data.Player p : gameData.getPlayers()) {
                totalScore += p.getTotalScore();
            }
            
            // Calculate gross amount
            int playerScore = player.getTotalScore();
            double grossAmount = (totalScore - (playerScore * numPlayers)) * gameData.getPointValue();
            
            // Only winners pay GST (positive gross amount)
            if (grossAmount > 0) {
                double gstAmount = grossAmount * (gameData.getGstPercent() / 100.0);
                totalContribution += gstAmount;
            }
        }
        
        return totalContribution;
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
                private String previousName = player.getName();
                
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    previousName = s.toString();
                }

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
                    // Note: If name is empty, we don't update - user can still type
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
            
            // Handle focus change to restore name if left empty
            playerName.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    // When focus is lost, if field is empty, restore the player's name
                    String currentText = playerName.getText().toString().trim();
                    if (currentText.isEmpty()) {
                        // Restore the saved name from player object
                        playerName.setText(player.getName());
                    }
                }
            });

            // Set player ID if available (for games with more than 2 players)
            TextView playerId = playerCardView.findViewById(R.id.text_player_id);
            if (gameData.getNumPlayers() > 2 && player.getRandomNumber() != null) {
                playerId.setText("#" + player.getRandomNumber());
                playerId.setVisibility(View.VISIBLE);
            }

            // Set current round badge
            TextView roundBadge = playerCardView.findViewById(R.id.text_current_round_badge);
            if (roundBadge != null) {
                int currentRound = calculateCurrentRound(gameData);
                roundBadge.setText("Round#" + currentRound);
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
                    } else {
                        // When focus is lost, if field is empty, show -1 placeholder
                        String currentText = scoreInput.getText().toString().trim();
                        if (currentText.isEmpty()) {
                            scoreInput.setText("-1");
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
                            
                            // Only save if user entered a valid number (not empty, not just clearing)
                            if (!text.isEmpty()) {
                                int score = Integer.parseInt(text);
                                
                                // Update the player's score in the game data
                                if (player.getScores() != null && finalRound < player.getScores().size()) {
                                    player.getScores().set(finalRound, score);
                                }
                                
                                // Save the updated game data to Firebase (with debouncing)
                                saveGameDataWithDebounce(gameData);
                                
                                // Announce player name and score with 4-second debounce
                                announceScoreWithDebounce(player.getName(), score, finalPlayerIndex, finalRound + 1);
                                
                                updateScoreColor(scoreInput);
                                updateStandings(gameData);
                                updateCurrentRound(gameData);
                                updateRoundValidation(gameData);
                                
                                // Check if game is completed and announce results (edit mode)
                                announceGameCompletion(gameData);
                            } else {
                                // Field is empty - just update color, don't save to DB
                                updateScoreColor(scoreInput);
                            }
                            
                        } catch (NumberFormatException e) {
                            // Invalid number - just update color
                            updateScoreColor(scoreInput);
                        }
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
        // Always check game data directly for round 10 completion
        // This works reliably in both edit and view modes
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
            boolean scoresFoundInInputFields = false;
            
            // First try to get scores from input fields (edit mode)
            Boolean editAccess = viewModel.getEditAccessGranted().getValue();
            if (editAccess != null && editAccess) {
                // Edit mode: try to get scores from input fields
                // Check if input fields exist first
                EditText firstScoreInput = binding.playersContainer.findViewWithTag("p" + (i + 1) + "r1");
                if (firstScoreInput != null) {
                    // Input fields exist, read from them
                    for (int round = 1; round <= 10; round++) {
                        EditText scoreInput = binding.playersContainer.findViewWithTag("p" + (i + 1) + "r" + round);
                        if (scoreInput != null) {
                            try {
                                String text = scoreInput.getText().toString();
                                if (!text.isEmpty()) {
                                    int score = Integer.parseInt(text);
                                    if (score > 0) {
                                        totalScore += score;
                                        scoresFoundInInputFields = true;
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Ignore invalid scores
                            }
                        }
                    }
                }
                
                // If no input fields found or no scores in them, fall back to game data
                if (!scoresFoundInInputFields && player.getScores() != null) {
                    System.out.println("Edit mode but input fields not ready, using game data for player " + (i + 1));
                    for (Integer score : player.getScores()) {
                        if (score != null && score > 0) {
                            totalScore += score;
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
                    // Valid score (including 0)
                    if (score == 0) {
                        // Zero score (winning round) - show empty box with green filled background
                        roundScoreText.setText(""); // Empty - color indicates winning round
                        roundScoreText.setBackground(getResources().getDrawable(R.drawable.round_score_box_zero, getTheme()));
                        roundScoreText.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                        roundScoreText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD);
                        roundScoreText.setPaintFlags(roundScoreText.getPaintFlags() | android.graphics.Paint.FAKE_BOLD_TEXT_FLAG);
                    } else {
                        // Show the score value
                        roundScoreText.setText(String.valueOf(score));
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
                    // No score yet (null or -1) - show dash or asterisks for current round
                    if ((round + 1) == currentRound) {
                        // Current round - show cycling asterisks with blinking animation (* → ** → ***)
                        roundScoreText.setBackground(getResources().getDrawable(R.drawable.round_score_box, getTheme()));
                        roundScoreText.setTextColor(getResources().getColor(R.color.warning_orange, getTheme())); // Orange color for visibility
                        roundScoreText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD);
                        roundScoreText.setPaintFlags(roundScoreText.getPaintFlags() | android.graphics.Paint.FAKE_BOLD_TEXT_FLAG);
                        
                        // Start with one asterisk
                        roundScoreText.setText("*");
                        
                        // Start blinking animation
                        android.view.animation.Animation blinkAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.blink_animation);
                        roundScoreText.startAnimation(blinkAnimation);
                        
                        // Use Handler to cycle asterisks every 400ms (* → ** → *** → * → ...)
                        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                        final int[] asteriskCount = {1}; // Start with 1 asterisk
                        Runnable asteriskChanger = new Runnable() {
                            @Override
                            public void run() {
                                // Only change if this TextView still has the animation running
                                if (roundScoreText.getAnimation() != null) {
                                    // Cycle through 1, 2, 3 asterisks
                                    asteriskCount[0] = (asteriskCount[0] % 3) + 1;
                                    String asterisks = "";
                                    for (int i = 0; i < asteriskCount[0]; i++) {
                                        asterisks += "*";
                                    }
                                    roundScoreText.setText(asterisks);
                                    // Schedule next change
                                    handler.postDelayed(this, 400); // Change every 400ms
                                }
                            }
                        };
                        // Start the asterisk cycling after initial delay
                        handler.postDelayed(asteriskChanger, 400);
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
        // Players info card has been removed - all info now shown in top header
        // This method is kept for compatibility but does nothing
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
        
        // Setup Settlement section collapsible
        binding.settlementHeader.setOnClickListener(v -> {
            toggleSection(binding.settlementContent, binding.settlementToggleIcon);
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
            boolean scoresFoundInInputFields = false;
            
            // Calculate total score from game data (works in both view and edit mode)
            Boolean editAccess = viewModel.getEditAccessGranted().getValue();
            if (editAccess != null && editAccess) {
                // Edit mode: try to get scores from input fields
                // Check if input fields exist first
                EditText firstScoreInput = binding.playersContainer.findViewWithTag("p" + (i + 1) + "r1");
                if (firstScoreInput != null) {
                    // Input fields exist, read from them
                    for (int round = 1; round <= 10; round++) {
                        EditText scoreInput = binding.playersContainer.findViewWithTag("p" + (i + 1) + "r" + round);
                        if (scoreInput != null && !scoreInput.getText().toString().trim().isEmpty()) {
                            try {
                                int score = Integer.parseInt(scoreInput.getText().toString().trim());
                                if (score > 0) { // Only count positive scores
                                    totalScore += score;
                                    scoresFoundInInputFields = true;
                                }
                            } catch (NumberFormatException e) {
                                // Skip invalid scores
                            }
                        }
                    }
                }
                
                // If no input fields found or no scores in them, fall back to game data
                if (!scoresFoundInInputFields && player.getScores() != null) {
                    for (Integer score : player.getScores()) {
                        if (score != null && score > 0) {
                            totalScore += score;
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
        // Standings info card has been removed - all info now shown in top header
        // This method is kept for compatibility but does nothing
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
            
            // Update Contribution Rule
            TextView gstRule = findViewById(R.id.text_settlement_gst_rule);
            if (gstRule != null) {
                gstRule.setText("Contribution: Only winners pay " + gstPercentText + " on positive amounts");
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
        
        // Set up Firestore listener with metadata changes to track cache vs server data
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        gameDataListener = db.collection("gameData")
            .document(currentGameId)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (documentSnapshot, error) -> {
                if (error != null) {
                    System.err.println("Error listening to game data: " + error.getMessage());
                    return;
                }
                
                if (documentSnapshot != null && documentSnapshot.exists()) {
                    try {
                        // Check if data is from cache or server
                        String dataSource = documentSnapshot.getMetadata().isFromCache() ? "LOCAL CACHE" : "SERVER";
                        System.out.println("Real-time update received for game: " + currentGameId + " [Source: " + dataSource + "]");
                        System.out.println("Raw document data keys: " + documentSnapshot.getData().keySet());
                        
                        // If data is from cache and we're online, skip this update and wait for server data
                        if (documentSnapshot.getMetadata().isFromCache() && isConnected && isNetworkAvailable()) {
                            System.out.println("Skipping cached data - waiting for server update...");
                            return;
                        }
                        
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
                                    // Check if we're in edit mode
                                    Boolean editAccess = viewModel.getEditAccessGranted().getValue();
                                    
                                    if (editAccess != null && editAccess) {
                                        // EDIT MODE: Only update standings (read-only display)
                                        // DO NOT update player cards to avoid destroying focused input fields
                                        System.out.println("Real-time update in EDIT MODE - updating standings only, preserving input fields");
                                        updateStandings(gameData);
                                        updateStandingsInfo(gameData);
                                        updateGameInfoHeader(gameData);
                                        
                                        // Check if game is completed and announce results (edit mode)
                                        announceGameCompletion(gameData);
                                        // DO NOT call updatePlayersInfo or generatePlayerCards in edit mode
                                    } else {
                                        // VIEW MODE: Update everything including player cards
                                        System.out.println("Real-time update in VIEW MODE - updating all UI elements");
                                        updateStandings(gameData);
                                        updateStandingsInfo(gameData);
                                        updatePlayersInfo(gameData);
                                        updateGameInfoHeader(gameData);
                                        
                                        // Announce score changes in view mode
                                        announceScoreChangesInViewMode(gameData);
                                        
                                        // Check if game is completed and announce results
                                        announceGameCompletion(gameData);
                                    }
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
    
    /**
     * Setup network monitoring to track connectivity and update status indicator
     */
    private void setupNetworkMonitoring() {
        android.net.ConnectivityManager connectivityManager = 
            (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager == null) {
            return;
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(android.net.Network network) {
                    runOnUiThread(() -> {
                        isConnected = true;
                        updateNetworkStatus(true);
                        // Attempt to reconnect listener if it was disconnected
                        attemptReconnect();
                    });
                }
                
                @Override
                public void onLost(android.net.Network network) {
                    runOnUiThread(() -> {
                        isConnected = false;
                        updateNetworkStatus(false);
                    });
                }
            };
            
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        }
        
        // Initial status check
        updateNetworkStatus(isNetworkAvailable());
    }
    
    /**
     * Check if network is currently available
     */
    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager = 
            (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager == null) {
            return false;
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.net.Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            
            android.net.NetworkCapabilities capabilities = 
                connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && 
                   (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
    }
    
    /**
     * Update online/offline indicators based on network status
     * @param connected Current network connection status
     */
    private void updateOnlineOfflineIndicators(boolean connected) {
        if (binding == null || binding.editOnlineIndicator == null || binding.editOfflineIndicator == null) {
            System.out.println("updateOnlineOfflineIndicators: Binding or indicators are null, skipping update");
            return;
        }
        
        // Check if we're in edit mode
        Boolean editAccess = viewModel != null ? viewModel.getEditAccessGranted().getValue() : null;
        if (editAccess == null || !editAccess) {
            // Not in edit mode - hide both indicators
            System.out.println("updateOnlineOfflineIndicators: Not in edit mode, hiding indicators");
            binding.editOnlineIndicator.setVisibility(View.GONE);
            binding.editOfflineIndicator.setVisibility(View.GONE);
            return;
        }
        
        System.out.println("updateOnlineOfflineIndicators: Edit mode active, network connected: " + connected);
        
        if (connected) {
            binding.editOnlineIndicator.setVisibility(View.VISIBLE);
            binding.editOfflineIndicator.setVisibility(View.GONE);
        } else {
            binding.editOnlineIndicator.setVisibility(View.GONE);
            binding.editOfflineIndicator.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Update online/offline indicators - convenience method that checks network status
     */
    private void updateOnlineOfflineIndicators() {
        updateOnlineOfflineIndicators(isNetworkAvailable());
    }
    
    /**
     * Update network status indicator UI
     */
    private void updateNetworkStatus(boolean connected) {
        if (binding.networkStatusIndicator == null) {
            return;
        }
        
        View statusDot = binding.statusDot;
        TextView statusText = binding.statusText;
        LinearLayout statusIndicator = binding.networkStatusIndicator;
        
        if (connected) {
            // Show "Live" status
            statusText.setText("Live");
            statusText.setTextColor(0xFF4CAF50); // Green
            if (statusDot != null) {
                statusDot.setBackgroundResource(R.drawable.status_dot);
                android.graphics.drawable.GradientDrawable drawable = 
                    (android.graphics.drawable.GradientDrawable) statusDot.getBackground();
                drawable.setColor(0xFF4CAF50);
            }
            statusIndicator.setBackgroundResource(R.drawable.status_badge_background);
            
            // Update online/offline indicators for edit mode
            runOnUiThread(() -> {
                Boolean editAccess = viewModel.getEditAccessGranted().getValue();
                if (editAccess != null && editAccess) {
                    updateOnlineOfflineIndicators(connected);
                }
            });
        } else {
            // Show "Offline" status
            statusText.setText("Offline");
            statusText.setTextColor(0xFFF44336); // Red
            if (statusDot != null) {
                statusDot.setBackgroundResource(R.drawable.status_dot);
                android.graphics.drawable.GradientDrawable drawable = 
                    (android.graphics.drawable.GradientDrawable) statusDot.getBackground();
                drawable.setColor(0xFFF44336);
            }
            // Change badge background to red theme
            android.graphics.drawable.GradientDrawable badgeDrawable = 
                new android.graphics.drawable.GradientDrawable();
            badgeDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            badgeDrawable.setColor(0x1AF44336); // Semi-transparent red
            badgeDrawable.setStroke(2, 0xFFF44336); // Red border
            badgeDrawable.setCornerRadius(12 * getResources().getDisplayMetrics().density);
            statusIndicator.setBackground(badgeDrawable);
            
            // Update online/offline indicators for edit mode
            runOnUiThread(() -> {
                Boolean editAccess = viewModel.getEditAccessGranted().getValue();
                if (editAccess != null && editAccess) {
                    updateOnlineOfflineIndicators(connected);
                }
            });
        }
    }
    
    /**
     * Attempt to reconnect Firebase listener when network is restored
     */
    private void attemptReconnect() {
        if (currentGameId == null) {
            return;
        }
        
        System.out.println("Network restored, attempting to reconnect and refresh data...");
        
        // Remove old listener if exists
        if (gameDataListener != null) {
            gameDataListener.remove();
            gameDataListener = null;
        }
        
        // Wait a bit before reconnecting to ensure network is stable
        reconnectHandler.postDelayed(() -> {
            if (isConnected && isNetworkAvailable()) {
                // Only reconnect if NOT in edit mode
                Boolean editAccess = viewModel.getEditAccessGranted().getValue();
                if (editAccess == null || !editAccess) {
                    System.out.println("Reconnecting Firebase listener for game: " + currentGameId + " (VIEW MODE)");
                    
                    // Force fetch fresh data from server first
                    fetchFreshGameData();
                    
                    // Then setup real-time listener
                    setupRealtimeListener();
                } else {
                    System.out.println("NOT reconnecting Firebase listener - currently in EDIT MODE (user has edit access)");
                }
                // Network status indicator already shows connection state, no need for toast
            }
        }, 1000); // 1 second delay
    }
    
    /**
     * Force fetch fresh game data from server (not cache) when network reconnects
     */
    private void fetchFreshGameData() {
        if (currentGameId == null) {
            return;
        }
        
        System.out.println("Fetching fresh game data from server...");
        
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        db.collection("gameData")
            .document(currentGameId)
            .get(com.google.firebase.firestore.Source.SERVER) // Force server fetch, not cache
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot != null && documentSnapshot.exists()) {
                    System.out.println("Fresh data fetched from server successfully");
                    try {
                        Object dataField = documentSnapshot.get("data");
                        if (dataField instanceof java.util.Map) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) dataField;
                            
                            // Convert to GameData object (same logic as in listener)
                            com.example.rummypulse.data.GameData gameData = parseGameDataFromMap(dataMap);
                            
                            // Update UI with fresh data
                            runOnUiThread(() -> {
                                if (gameData != null && gameData.getPlayers() != null && !gameData.getPlayers().isEmpty()) {
                                    System.out.println("Updating UI with fresh server data");
                                    updateStandings(gameData);
                                    updateStandingsInfo(gameData);
                                    updatePlayersInfo(gameData);
                                    updateGameInfoHeader(gameData);
                                }
                            });
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing fresh game data: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("Fresh data fetch: document does not exist");
                }
            })
            .addOnFailureListener(e -> {
                System.err.println("Failed to fetch fresh data from server: " + e.getMessage());
            });
    }
    
    /**
     * Parse game data from Firestore map
     */
    private com.example.rummypulse.data.GameData parseGameDataFromMap(java.util.Map<String, Object> dataMap) {
        try {
            com.example.rummypulse.data.GameData gameData = new com.example.rummypulse.data.GameData();
            
            // Map basic fields
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
                                scores.add(-1);
                            }
                        }
                        player.setScores(scores);
                    }
                    
                    players.add(player);
                }
                gameData.setPlayers(players);
            }
            
            return gameData;
        } catch (Exception e) {
            System.err.println("Error in parseGameDataFromMap: " + e.getMessage());
            return null;
        }
    }

    // Helper class for standings
    private void shareStandingsToWhatsApp() {
        com.example.rummypulse.data.GameData gameData = viewModel.getGameData().getValue();
        
        if (gameData == null) {
            ModernToast.error(this, "No game data available to share");
            return;
        }
        
        // Format the standings data as text
        String shareText = formatStandingsText(gameData);
        
        // Create share intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        
        // Create chooser to allow user to select WhatsApp or other apps
        Intent chooserIntent = Intent.createChooser(shareIntent, "Share Standings via");
        
        try {
            startActivity(chooserIntent);
        } catch (android.content.ActivityNotFoundException e) {
            ModernToast.error(this, "No app available to share");
        }
    }
    
    private String formatStandingsText(com.example.rummypulse.data.GameData gameData) {
        StringBuilder text = new StringBuilder();
        
        // Add game header
        text.append("🎮 *RUMMY PULSE - GAME STANDINGS* 🎮\n");
        // Calculate standings
        java.util.List<PlayerStanding> standings = calculateStandings(gameData);
        
        // Calculate total contribution
        double totalContribution = 0;
        for (PlayerStanding standing : standings) {
            totalContribution += standing.gstPaid;
        }
        
        // Add game info
        if (currentGameId != null) {
            text.append("🎯 *Game ID:* ").append(currentGameId).append("\n");
        }
        text.append("👥 *Players:* ").append(gameData.getNumPlayers()).append("\n");
        text.append("💰 *Point Value:* ₹").append(String.format("%.2f", gameData.getPointValue())).append("\n");
        text.append("📊 *Contribution %:* ").append(String.format("%.0f", gameData.getGstPercent())).append("%\n");
        text.append("💵 *Total Contribution:* ₹").append(String.format("%.0f", totalContribution)).append("\n");
        text.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        
        // Sort by total score (ascending - lower is better)
        standings.sort((a, b) -> Integer.compare(a.totalScore, b.totalScore));
        
        // Add standings header
        text.append("🏆 *STANDINGS* 🏆\n");
        
        // Add each player's standing (compact format)
        for (int i = 0; i < standings.size(); i++) {
            PlayerStanding standing = standings.get(i);
            
            // Rank emoji
            String rankEmoji;
            if (i == 0) rankEmoji = "🥇";
            else if (i == 1) rankEmoji = "🥈";
            else if (i == 2) rankEmoji = "🥉";
            else rankEmoji = String.valueOf(i + 1) + ".";
            
            // Compact format: Rank Name • Score: X • Net: ₹Y
            text.append(rankEmoji).append(" *").append(standing.player.getName()).append("*");
            text.append(" • Score: ").append(standing.totalScore);
            text.append(" • Net: ₹").append(String.format("%.0f", standing.netAmount));
            text.append("\n");
        }
        
        text.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        text.append("📱 *Shared from RummyPulse App*\n");
        
        return text.toString();
    }
    
    private java.util.List<PlayerStanding> calculateStandings(com.example.rummypulse.data.GameData gameData) {
        java.util.List<PlayerStanding> standings = new java.util.ArrayList<>();
        int totalAllScores = 0;
        
        // First pass: collect all scores
        for (com.example.rummypulse.data.Player player : gameData.getPlayers()) {
            int totalScore = 0;
            
            // Get scores from game data
            if (player.getScores() != null) {
                for (Integer score : player.getScores()) {
                    if (score != null && score > 0) {
                        totalScore += score;
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
        
        return standings;
    }
    
    private static class PlayerStanding {
        com.example.rummypulse.data.Player player;
        int totalScore;
        double grossAmount;
        double gstPaid;
        double netAmount;
    }
}
