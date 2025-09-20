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
    private final MutableLiveData<Integer> mGstApproved;
    private final MutableLiveData<Integer> mGstPending;
    private final MutableLiveData<String> mError;
    
    private GameRepository gameRepository;

    public HomeViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("");
        
        mGameItems = new MutableLiveData<>();
        mTotalGames = new MutableLiveData<>();
        mGstApproved = new MutableLiveData<>();
        mGstPending = new MutableLiveData<>();
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

    public LiveData<Integer> getGstApproved() {
        return mGstApproved;
    }

    public LiveData<Integer> getGstPending() {
        return mGstPending;
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
        
        // Load games from Firebase
        gameRepository.loadAllGames();
    }

    private void calculateMetrics(List<GameItem> games) {
        if (games == null) {
            games = new ArrayList<>();
        }
        
        int total = games.size();
        int approved = 0;
        int pending = 0;
        
        for (GameItem game : games) {
            if (game.isCompleted()) {
                approved++;
            } else {
                pending++;
            }
        }
        
        mTotalGames.setValue(total);
        mGstApproved.setValue(approved);
        mGstPending.setValue(pending);
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
}