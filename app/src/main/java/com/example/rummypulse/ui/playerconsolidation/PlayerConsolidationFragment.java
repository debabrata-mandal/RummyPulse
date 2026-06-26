package com.example.rummypulse.ui.playerconsolidation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.rummypulse.databinding.FragmentPlayerConsolidationBinding;

public class PlayerConsolidationFragment extends Fragment {

    private FragmentPlayerConsolidationBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlayerConsolidationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
}
