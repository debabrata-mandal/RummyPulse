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
    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
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

        // Observe errors
        homeViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        // Setup refresh button
        binding.btnRefresh.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Refreshing data...", Toast.LENGTH_SHORT).show();
            homeViewModel.refreshGames();
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
        // Disabled for now - no action
        Toast.makeText(getContext(), "Approve button disabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNotApplicable(GameItem game, int position) {
        // Disabled for now - no action
        Toast.makeText(getContext(), "NA button disabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDeleteGame(GameItem game, int position) {
        Toast.makeText(getContext(), "Deleting Game: " + game.getGameId(), Toast.LENGTH_SHORT).show();
        // Delete game from Firebase
        homeViewModel.deleteGame(game.getGameId());
    }
}