package com.example.rummypulse.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.rummypulse.data.GameRepository;

import java.util.ArrayList;
import java.util.List;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<String> mText;
    private final MutableLiveData<List<GameItem>> mGameItems;
    private final MutableLiveData<Integer> mTotalGames;
    private final MutableLiveData<Integer> mApprovedGames;
    private final MutableLiveData<Integer> mCompletedGames;
    private final MutableLiveData<Integer> mInProgressGames;
    private final MutableLiveData<Double> mTotalGstAmount;
    private final MutableLiveData<String> mError;
    
    private GameRepository gameRepository;

    public HomeViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("");
        
        mGameItems = new MutableLiveData<>();
        mTotalGames = new MutableLiveData<>();
        mApprovedGames = new MutableLiveData<>();
        mCompletedGames = new MutableLiveData<>();
        mInProgressGames = new MutableLiveData<>();
        mTotalGstAmount = new MutableLiveData<>();
        mError = new MutableLiveData<>();
        
        // Initialize repository
        gameRepository = new GameRepository();
        
        // Load data from Firebase
        loadGamesFromFirebase();
    }

    public LiveData<String> getText() {
        return mText;
    }

    public LiveData<List<GameItem>> getGameItems() {
        return mGameItems;
    }

    public LiveData<Integer> getTotalGames() {
        return mTotalGames;
    }

    public LiveData<Integer> getCompletedGames() {
        return mCompletedGames;
    }

    public LiveData<Integer> getInProgressGames() {
        return mInProgressGames;
    }

    public LiveData<Double> getTotalGstAmount() {
        return mTotalGstAmount;
    }

    public LiveData<Integer> getApprovedGamesCount() {
        return mApprovedGames;
    }

    public LiveData<String> getError() {
        return mError;
    }

    private void loadGamesFromFirebase() {
        // Observe game items from repository
        gameRepository.getGameItems().observeForever(gameItems -> {
            mGameItems.setValue(gameItems);
            calculateMetrics(gameItems);
        });
        
        // Observe errors from repository
        gameRepository.getError().observeForever(error -> {
            mError.setValue(error);
        });
        
        // Observe total approved GST amount
        gameRepository.getTotalApprovedGst().observeForever(totalGst -> {
            mTotalGstAmount.setValue(totalGst);
        });
        
        // Observe approved games count
        gameRepository.getApprovedGamesCount().observeForever(approvedCount -> {
            mApprovedGames.setValue(approvedCount);
        });
        
        
        // Load games from Firebase
        gameRepository.loadAllGames();
        
        // Load approved games and calculate total GST
        gameRepository.loadApprovedGames();
    }

    private void calculateMetrics(List<GameItem> games) {
        if (games == null) {
            games = new ArrayList<>();
        }
        
        int total = games.size();
        int completed = 0;
        int inProgress = 0;
        
        for (GameItem game : games) {
            if (game.isCompleted()) {
                completed++;
            } else {
                inProgress++;
            }
        }
        
        mTotalGames.setValue(total);
        mCompletedGames.setValue(completed);
        mInProgressGames.setValue(inProgress);
    }

    public void refreshGames() {
        gameRepository.loadAllGames();
    }

    public void deleteGame(String gameId) {
        gameRepository.deleteGame(gameId);
    }

    public void updateGameStatus(String gameId, String newStatus) {
        gameRepository.updateGameStatus(gameId, newStatus);
    }

    public void approveGame(GameItem gameItem) {
        gameRepository.approveGame(gameItem);
    }

    // Temporary test method to set first game to completed status
    public void setTestGameCompleted() {
        List<GameItem> currentGames = mGameItems.getValue();
        if (currentGames != null && !currentGames.isEmpty()) {
            GameItem firstGame = currentGames.get(0);
            gameRepository.updateGameStatus(firstGame.getGameId(), "Completed");
        }
    }
}