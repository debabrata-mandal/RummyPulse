package com.example.rummypulse.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<String> mText;
    private final MutableLiveData<List<GameItem>> mGameItems;

    public HomeViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is home fragment");
        
        mGameItems = new MutableLiveData<>();
        mGameItems.setValue(createMockGameData());
    }

    public LiveData<String> getText() {
        return mText;
    }

    public LiveData<List<GameItem>> getGameItems() {
        return mGameItems;
    }

    private List<GameItem> createMockGameData() {
        List<GameItem> mockData = new ArrayList<>();
        
        // Add mock game data with point values
        mockData.add(new GameItem("GAME001", "2450", "25", "2024-01-15 14:30:00", "Active", "4", "18", "441"));
        mockData.add(new GameItem("GAME002", "1890", "20", "2024-01-15 12:15:00", "Completed", "3", "18", "340"));
        mockData.add(new GameItem("GAME003", "3200", "30", "2024-01-15 10:45:00", "Active", "5", "18", "576"));
        mockData.add(new GameItem("GAME004", "1560", "15", "2024-01-14 18:20:00", "Completed", "2", "18", "281"));
        mockData.add(new GameItem("GAME005", "2780", "28", "2024-01-14 16:10:00", "Active", "4", "18", "500"));
        mockData.add(new GameItem("GAME006", "2100", "22", "2024-01-14 14:30:00", "Completed", "3", "18", "378"));
        mockData.add(new GameItem("GAME007", "3450", "35", "2024-01-14 11:45:00", "Active", "6", "18", "621"));
        mockData.add(new GameItem("GAME008", "1720", "18", "2024-01-13 19:15:00", "Completed", "2", "18", "310"));
        mockData.add(new GameItem("GAME009", "2890", "29", "2024-01-13 17:30:00", "Active", "4", "18", "520"));
        mockData.add(new GameItem("GAME010", "1980", "20", "2024-01-13 15:20:00", "Completed", "3", "18", "356"));
        mockData.add(new GameItem("GAME011", "2650", "26", "2024-01-13 13:10:00", "Active", "5", "18", "477"));
        mockData.add(new GameItem("GAME012", "1420", "14", "2024-01-12 20:45:00", "Completed", "2", "18", "256"));
        
        return mockData;
    }
}