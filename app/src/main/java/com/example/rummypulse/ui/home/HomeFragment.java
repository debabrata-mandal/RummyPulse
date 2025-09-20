package com.example.rummypulse.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.databinding.FragmentHomeBinding;

import java.util.List;

public class HomeFragment extends Fragment implements TableAdapter.OnGameActionListener {

    private FragmentHomeBinding binding;
    private TableAdapter tableAdapter;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Setup RecyclerView for the table
        RecyclerView recyclerView = binding.recyclerViewTable;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
            // Observe game data and update adapter
            homeViewModel.getGameItems().observe(getViewLifecycleOwner(), gameItems -> {
                tableAdapter = new TableAdapter(gameItems);
                tableAdapter.setOnGameActionListener(this);
                recyclerView.setAdapter(tableAdapter);
            });

        // Observe and update metric tiles
        homeViewModel.getTotalGames().observe(getViewLifecycleOwner(), totalGames -> {
            binding.textTotalGames.setText(String.valueOf(totalGames));
        });

        homeViewModel.getGstApproved().observe(getViewLifecycleOwner(), gstApproved -> {
            binding.textGstApproved.setText(String.valueOf(gstApproved));
        });

        homeViewModel.getGstPending().observe(getViewLifecycleOwner(), gstPending -> {
            binding.textGstPending.setText(String.valueOf(gstPending));
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onApproveGst(GameItem game, int position) {
        Toast.makeText(getContext(), "GST Approved for " + game.getGameId(), Toast.LENGTH_SHORT).show();
        // TODO: Implement actual GST approval logic
        // Update game status, refresh data, etc.
    }

    @Override
    public void onNotApplicable(GameItem game, int position) {
        Toast.makeText(getContext(), "Marked as Not Applicable: " + game.getGameId(), Toast.LENGTH_SHORT).show();
        // TODO: Implement not applicable logic
        // Update game status, refresh data, etc.
    }

    @Override
    public void onDeleteGame(GameItem game, int position) {
        Toast.makeText(getContext(), "Delete Game: " + game.getGameId(), Toast.LENGTH_SHORT).show();
        // TODO: Implement delete game logic
        // Remove from list, refresh data, etc.
    }
}