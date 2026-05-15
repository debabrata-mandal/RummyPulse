package com.example.rummypulse.ui.gamedefaults;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.rummypulse.R;
import com.example.rummypulse.data.AppUserRoleSession;
import com.example.rummypulse.data.GameDefaults;
import com.example.rummypulse.databinding.FragmentGameDefaultsBinding;
import com.example.rummypulse.utils.ModernToast;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.util.Locale;

public class GameDefaultsFragment extends Fragment {

    private FragmentGameDefaultsBinding binding;
    private GameDefaultsViewModel viewModel;
    /** True when {@link AppUserRoleSession} reports {@link AppUserRoleSession.Role#ADMIN} (appUser.role == admin_user). */
    private boolean canEditDefaultContribution;
    private boolean suppressSwitchCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(GameDefaultsViewModel.class);
        binding = FragmentGameDefaultsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnSaveDefaults.setOnClickListener(v -> attemptSave());

        binding.switchDisplayIntermediateCalculation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchCallback) {
                return;
            }
            viewModel.saveDisplayIntermediateCalculation(isChecked);
        });

        viewModel.getDefaults().observe(getViewLifecycleOwner(), this::populateFieldsFromDefaults);
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean b = Boolean.TRUE.equals(loading);
            binding.loadingOverlay.setVisibility(b ? View.VISIBLE : View.GONE);
            binding.btnSaveDefaults.setEnabled(!b);
        });
        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null && !err.isEmpty()) {
                ModernToast.error(requireContext(), err);
            }
        });
        viewModel.getSaveSuccess().observe(getViewLifecycleOwner(), ok -> {
            if (Boolean.TRUE.equals(ok)) {
                ModernToast.success(requireContext(), getString(R.string.game_defaults_saved));
                viewModel.clearSaveSuccessFlag();
            }
        });

        AppUserRoleSession.getInstance().getRole().observe(getViewLifecycleOwner(), role -> {
            if (binding == null) {
                return;
            }
            canEditDefaultContribution = role == AppUserRoleSession.Role.ADMIN;
            applyDefaultContributionFieldState();
        });

        viewModel.load();
    }

    private void applyDefaultContributionFieldState() {
        if (binding == null) {
            return;
        }
        boolean enabled = canEditDefaultContribution;
        binding.layoutDefaultContribution.setEnabled(enabled);
        binding.editDefaultContribution.setEnabled(enabled);
        binding.editDefaultContribution.setFocusable(enabled);
        binding.editDefaultContribution.setFocusableInTouchMode(enabled);
        binding.editDefaultContribution.setCursorVisible(enabled);
        if (enabled) {
            binding.layoutDefaultContribution.setStartIconDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_percent));
            binding.layoutDefaultContribution.setStartIconTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.accent_blue_light)));
            binding.layoutDefaultContribution.setStartIconContentDescription(null);
            binding.layoutDefaultContribution.setHelperText(null);
        } else {
            binding.layoutDefaultContribution.setStartIconDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_lock));
            binding.layoutDefaultContribution.setStartIconTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_secondary)));
            binding.layoutDefaultContribution.setStartIconContentDescription(
                    getString(R.string.cd_game_defaults_contribution_locked));
            binding.layoutDefaultContribution.setHelperText(getString(R.string.game_defaults_contribution_admin_only_helper));
        }
    }

    private void populateFieldsFromDefaults(GameDefaults g) {
        if (g == null || binding == null) {
            return;
        }
        binding.editDefaultPointValue.setText(formatPoint(g.getDefaultPointValue()));
        binding.editDefaultContribution.setText(String.valueOf((int) Math.round(g.getDefaultGstPercent())));
        binding.editMidGameIncrement.setText(String.valueOf(g.getDefaultMidGameNewPlayerScoreIncrement()));
        suppressSwitchCallback = true;
        binding.switchDisplayIntermediateCalculation.setChecked(g.isDisplayIntermediateCalculation());
        suppressSwitchCallback = false;
        clearFieldErrors();
        binding.textAudit.setText(buildAuditText(g));
        applyDefaultContributionFieldState();
    }

    private String buildAuditText(GameDefaults g) {
        Timestamp ts = g.getUpdatedAt();
        String when = ts != null
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                .format(ts.toDate())
                : getString(R.string.game_defaults_audit_never);
        String who = g.getUpdatedByUserName();
        if (who == null || who.isEmpty()) {
            who = g.getUpdatedByUserId();
        }
        if (who == null || who.isEmpty()) {
            who = getString(R.string.game_defaults_audit_unknown_user);
        }
        return getString(R.string.game_defaults_audit_format, when, who);
    }

    private void attemptSave() {
        clearFieldErrors();
        Double point = parsePoint(binding.layoutDefaultPointValue, binding.editDefaultPointValue.getText().toString());
        if (point == null) {
            return;
        }
        Long inc = parseLongNonNegative(binding.layoutMidGameIncrement, binding.editMidGameIncrement.getText().toString());
        if (inc == null) {
            return;
        }
        if (canEditDefaultContribution) {
            Integer gst = parseIntField(binding.layoutDefaultContribution, binding.editDefaultContribution.getText().toString(),
                    getString(R.string.dialog_contribution_required),
                    getString(R.string.dialog_contribution_invalid), 0, 100);
            if (gst == null) {
                return;
            }
            viewModel.save(point, (double) gst, inc,
                    binding.switchDisplayIntermediateCalculation.isChecked(), true);
        } else {
            viewModel.save(point, null, inc,
                    binding.switchDisplayIntermediateCalculation.isChecked(), false);
        }
    }

    private void clearFieldErrors() {
        binding.layoutDefaultPointValue.setError(null);
        binding.layoutDefaultContribution.setError(null);
        binding.layoutMidGameIncrement.setError(null);
    }

    @Nullable
    private Double parsePoint(TextInputLayout layout, String raw) {
        String s = raw != null ? raw.trim() : "";
        if (TextUtils.isEmpty(s)) {
            layout.setError(getString(R.string.dialog_point_value_required));
            return null;
        }
        try {
            double v = Double.parseDouble(s);
            if (v <= 0 || v > 100) {
                layout.setError(getString(R.string.dialog_point_value_invalid));
                return null;
            }
            double snapped = Math.round(v * 20.0) / 20.0;
            if (snapped < 0.05) {
                snapped = 0.05;
            }
            if (snapped > 100.0) {
                snapped = 100.0;
            }
            layout.setError(null);
            return snapped;
        } catch (NumberFormatException e) {
            layout.setError(getString(R.string.dialog_point_value_invalid));
            return null;
        }
    }

    @Nullable
    private Integer parseIntField(TextInputLayout layout, String raw, String emptyErr, String rangeErr, int min, int max) {
        String s = raw != null ? raw.trim() : "";
        if (TextUtils.isEmpty(s)) {
            layout.setError(emptyErr);
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            if (v < min || v > max) {
                layout.setError(rangeErr);
                return null;
            }
            layout.setError(null);
            return v;
        } catch (NumberFormatException e) {
            layout.setError(rangeErr);
            return null;
        }
    }

    @Nullable
    private Long parseLongNonNegative(TextInputLayout layout, String raw) {
        String s = raw != null ? raw.trim() : "";
        if (TextUtils.isEmpty(s)) {
            layout.setError(getString(R.string.game_defaults_increment_required));
            return null;
        }
        try {
            long v = Long.parseLong(s);
            if (v < 0) {
                layout.setError(getString(R.string.game_defaults_increment_invalid));
                return null;
            }
            layout.setError(null);
            return v;
        } catch (NumberFormatException e) {
            layout.setError(getString(R.string.game_defaults_increment_invalid));
            return null;
        }
    }

    private static String formatPoint(double v) {
        BigDecimal bd = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return bd.toPlainString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
