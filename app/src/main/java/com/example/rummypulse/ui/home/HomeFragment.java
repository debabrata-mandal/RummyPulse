package com.example.rummypulse.ui.home;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.example.rummypulse.databinding.FragmentHomeBinding;
import com.example.rummypulse.data.AppUserRoleSession;
import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.AppUserDirectoryFilter;
import com.example.rummypulse.data.AppUserRepository;
import com.example.rummypulse.data.Player;
import com.example.rummypulse.utils.ProfileAvatarBinder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class HomeFragment extends Fragment implements TableAdapter.OnGameActionListener {

    private static final int LOCKED_OVERLAY_VIEW_ID = View.generateViewId();

    private FragmentHomeBinding binding;
    private TableAdapter tableAdapter;
    private HomeViewModel homeViewModel;
    private boolean adminViewConfigured;
    private boolean lockedViewConfigured;
    private boolean updatingSelectionControls;
    private boolean reviewOperationInProgress;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        AppUserRoleSession.getInstance().getRole().observe(getViewLifecycleOwner(), new Observer<AppUserRoleSession.Role>() {
            @Override
            public void onChanged(AppUserRoleSession.Role role) {
                if (!isAdded() || binding == null) {
                    return;
                }
                if (role == AppUserRoleSession.Role.UNKNOWN) {
                    showAccessLoading();
                    return;
                }
                hideAccessLoading();
                if (role == AppUserRoleSession.Role.ADMIN) {
                    removeLockedOverlayIfPresent();
                    if (!adminViewConfigured) {
                        setupAdminView();
                        adminViewConfigured = true;
                    }
                } else {
                    if (!lockedViewConfigured) {
                        setupLockedView();
                        lockedViewConfigured = true;
                    }
                }
            }
        });

        return root;
    }

    private void showAccessLoading() {
        binding.reviewAccessLoading.setVisibility(View.VISIBLE);
        binding.swipeRefresh.setAlpha(0.35f);
    }

    private void hideAccessLoading() {
        binding.reviewAccessLoading.setVisibility(View.GONE);
        binding.swipeRefresh.setAlpha(1f);
    }

    private void removeLockedOverlayIfPresent() {
        if (binding == null || !(binding.getRoot() instanceof ViewGroup)) {
            return;
        }
        ViewGroup coordinator = (ViewGroup) binding.getRoot();
        View overlay = coordinator.findViewById(LOCKED_OVERLAY_VIEW_ID);
        if (overlay != null) {
            coordinator.removeView(overlay);
        }
    }

    private void setupAdminView() {
        RecyclerView recyclerView = binding.recyclerViewTable;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        homeViewModel.getGameItems().observe(getViewLifecycleOwner(), gameItems -> {
            if (tableAdapter == null) {
                tableAdapter = new TableAdapter(gameItems);
                tableAdapter.setOnGameActionListener(this);
                tableAdapter.setOnSelectionChangedListener(this::updateSelectionControls);
                tableAdapter.setDirectoryUsers(homeViewModel.getDirectoryUsers().getValue());
                recyclerView.setAdapter(tableAdapter);
            } else {
                tableAdapter.submitItems(gameItems);
            }
            binding.reviewBulkActions.setVisibility(
                    tableAdapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
        });
        homeViewModel.getDirectoryUsers().observe(getViewLifecycleOwner(), users -> {
            if (tableAdapter == null) return;
            tableAdapter.setDirectoryUsers(users);
        });

        homeViewModel.getCompletedGames().observe(getViewLifecycleOwner(), completedGames -> {
            int c = completedGames != null ? completedGames : 0;
            binding.textCompletedGames.setText(String.valueOf(c));
            binding.btnApproveAll.setEnabled(c > 0);
        });

        homeViewModel.getInProgressGames().observe(getViewLifecycleOwner(), inProgressGames -> {
            binding.textInProgressGames.setText(String.valueOf(inProgressGames));
        });

        binding.swipeRefresh.setEnabled(false);
        binding.btnRefresh.setOnClickListener(v -> refreshGames());
        binding.btnApproveAll.setOnClickListener(v -> onApproveAllClicked());
        binding.checkboxSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updatingSelectionControls && tableAdapter != null) {
                tableAdapter.selectAll(isChecked);
            }
        });
        binding.btnDeleteSelected.setOnClickListener(v -> onDeleteSelectedClicked());

        homeViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                if (reviewOperationInProgress) {
                    endReviewOperation();
                }
                String message = HomeViewModel.ERROR_ADMIN_GAME_POINT_SETTINGS_REQUIRED.equals(error)
                        ? getString(R.string.review_game_point_settings_admin_required)
                        : error;
                com.example.rummypulse.utils.ModernToast.error(getContext(), message);
            }
        });
    }

    private void beginReviewOperation(String message) {
        if (binding == null) {
            return;
        }
        reviewOperationInProgress = true;
        binding.reviewOperationLoading.setVisibility(View.VISIBLE);
        binding.textReviewOperationMessage.setText(message);
        binding.swipeRefresh.setAlpha(0.35f);
        setReviewControlsEnabled(false);
    }

    private void endReviewOperation() {
        if (binding == null) {
            return;
        }
        reviewOperationInProgress = false;
        binding.reviewOperationLoading.setVisibility(View.GONE);
        binding.swipeRefresh.setAlpha(1f);
        setReviewControlsEnabled(true);
    }

    private void setReviewControlsEnabled(boolean enabled) {
        if (binding == null) {
            return;
        }
        Integer completedGames = homeViewModel.getCompletedGames().getValue();
        int completed = completedGames != null ? completedGames : 0;
        binding.btnApproveAll.setEnabled(enabled && completed > 0);
        binding.btnRefresh.setEnabled(enabled);
        if (tableAdapter != null) {
            binding.checkboxSelectAll.setEnabled(
                    enabled && tableAdapter.getItemCount() > 0);
            tableAdapter.setActionsEnabled(enabled);
            int selectedCount = tableAdapter.getSelectedGameIds().size();
            int itemCount = tableAdapter.getItemCount();
            updateSelectionControls(
                    selectedCount,
                    itemCount > 0 && selectedCount == itemCount);
        } else {
            binding.checkboxSelectAll.setEnabled(false);
            binding.btnDeleteSelected.setEnabled(false);
        }
    }

    @Override
    public void onEditGameEconomics(GameItem game, int position) {
        if (!isAdded() || getContext() == null || game == null) {
            return;
        }

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_game_economics, null);
        TextInputLayout layoutPoint = dialogView.findViewById(R.id.layout_edit_review_game_point_factor);
        TextInputEditText editPoint = dialogView.findViewById(R.id.edit_review_game_point_factor);
        TextInputLayout layoutBoardAdjustment = dialogView.findViewById(R.id.layout_edit_review_board_adjustment);
        TextInputEditText editBoardAdjustment = dialogView.findViewById(R.id.edit_review_board_adjustment);
        com.google.android.material.button.MaterialButton cancel =
                dialogView.findViewById(R.id.btn_edit_economics_cancel);
        com.google.android.material.button.MaterialButton save =
                dialogView.findViewById(R.id.btn_edit_economics_save);

        String pv = game.getGamePointFactor();
        if (pv == null || pv.isEmpty()) {
            editPoint.setText("");
        } else {
            editPoint.setText(formatPlainDecimalForField(parseGamePointFactorForDisplay(pv)));
        }
        String boardAdjustment = game.getBoardAdjustmentPercentage();
        if (boardAdjustment == null) {
            boardAdjustment = "";
        }
        editBoardAdjustment.setText(boardAdjustment.replace("%", "").trim());

        AlertDialog dialog = new AlertDialog.Builder(getContext(), R.style.DarkDialogTheme)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            Double point = parseAndClampGamePointFactor(layoutPoint, editPoint);
            Integer contrib = parseBoardAdjustmentPercent(layoutBoardAdjustment, editBoardAdjustment);
            if (point == null || contrib == null) {
                return;
            }
            homeViewModel.updateGameEconomics(game.getGameId(), point, contrib, () -> {
                if (isAdded() && getContext() != null) {
                    com.example.rummypulse.utils.ModernToast.success(getContext(),
                            getString(R.string.review_edit_economics_saved));
                }
            });
            dialog.dismiss();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
            android.util.DisplayMetrics dm =
                    getResources().getDisplayMetrics();
            int maxWidth = Math.round(420 * dm.density);
            int width = Math.min(Math.round(dm.widthPixels * 0.92f), maxWidth);
            dialog.getWindow().setLayout(
                    width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void setupLockedView() {
        binding.recyclerViewTable.setVisibility(View.GONE);
        binding.swipeRefresh.setVisibility(View.GONE);

        TextView lockedMessage = new TextView(getContext());
        lockedMessage.setId(LOCKED_OVERLAY_VIEW_ID);
        lockedMessage.setText(getString(
                R.string.review_access_restricted_combined,
                getString(R.string.review_access_restricted),
                getString(R.string.review_access_restricted_detail)));
        lockedMessage.setTextSize(15);
        lockedMessage.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        lockedMessage.setLineSpacing(4f, 1f);
        lockedMessage.setPadding(32, 100, 32, 32);
        lockedMessage.setTextColor(getResources().getColor(com.example.rummypulse.R.color.text_secondary, null));

        if (binding.getRoot() instanceof ViewGroup) {
            ((ViewGroup) binding.getRoot()).addView(lockedMessage);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onApproveBoardAdjustment(GameItem game, int position) {
        if (!"Completed".equals(game.getGameStatus())) {
            com.example.rummypulse.utils.ModernToast.warning(getContext(), "Game must be completed before approval");
            return;
        }

        showReviewActionDialog(
                R.drawable.ic_approve,
                "Approve game?",
                "Finalize this completed game",
                "The game will move to the approved list and its results will be finalized.",
                "Approve",
                false,
                () -> {
                    beginReviewOperation(getString(R.string.review_operation_approving_one));
                    homeViewModel.approveGame(game, () -> {
                        if (!isAdded() || getContext() == null) {
                            return;
                        }
                        endReviewOperation();
                        com.example.rummypulse.utils.ModernToast.success(
                                getContext(), "✅ Game approved successfully!");
                    });
                });
    }


    @Override
    public void onDeleteGame(GameItem game, int position) {
        showReviewActionDialog(
                R.drawable.ic_delete,
                "Delete game?",
                "Permanently remove this game",
                "This action cannot be undone. The game and its recorded results will be removed.",
                "Delete game",
                true,
                () -> {
                    beginReviewOperation(getResources().getQuantityString(
                            R.plurals.review_delete_selected_progress, 1, 1));
                    homeViewModel.deleteGame(
                            game.getGameId(),
                            () -> {
                                if (!isAdded() || getContext() == null) {
                                    return;
                                }
                                endReviewOperation();
                                com.example.rummypulse.utils.ModernToast.success(
                                        getContext(),
                                        getString(R.string.review_delete_success_one));
                            },
                            error -> {
                                if (!isAdded()) {
                                    return;
                                }
                                endReviewOperation();
                            });
                });
    }

    @Override
    public void onKickOutEditor(GameItem game, int position) {
        if (!isAdded() || getContext() == null || game == null) {
            return;
        }
        String editorName = game.getEditorName();
        String displayName = (editorName == null || editorName.trim().isEmpty())
                ? "the current editor" : editorName;

        showReviewActionDialog(
                R.drawable.ic_lock,
                getString(R.string.review_kick_editor_title),
                getString(R.string.review_kick_editor_subtitle, displayName),
                getString(R.string.review_kick_editor_message, displayName),
                getString(R.string.review_kick_editor),
                true,
                () -> {
                    beginReviewOperation(getString(R.string.review_operation_kicking_editor));
                    homeViewModel.kickOutEditor(
                            game.getGameId(),
                            () -> {
                                if (!isAdded() || getContext() == null) {
                                    return;
                                }
                                endReviewOperation();
                                com.example.rummypulse.utils.ModernToast.success(
                                        getContext(),
                                        getString(R.string.review_kick_editor_success));
                            },
                            error -> {
                                if (!isAdded()) {
                                    return;
                                }
                                endReviewOperation();
                                if (getContext() != null && error != null) {
                                    com.example.rummypulse.utils.ModernToast.warning(getContext(), error);
                                }
                            });
                });
    }

    @Override
    public void onChangePlayerMapping(GameItem game, Player player) {
        if (!isAdded() || getContext() == null || game == null || player == null) {
            return;
        }
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_map_player, null, false);
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setView(dialogView)
                .create();
        ((TextView) dialogView.findViewById(R.id.text_map_player_title))
                .setText(R.string.review_change_mapping);
        ((TextView) dialogView.findViewById(R.id.text_map_player_subtitle))
                .setText(getString(R.string.review_change_mapping_for, player.getName()));
        View unlink = dialogView.findViewById(R.id.btn_unlink_user);
        unlink.setVisibility(player.getUserId() == null || player.getUserId().trim().isEmpty()
                ? View.GONE : View.VISIBLE);
        unlink.setOnClickListener(v -> {
            dialog.dismiss();
            showReviewActionDialog(
                    R.drawable.ic_link_players,
                    getString(R.string.review_remove_mapping_title),
                    getString(R.string.review_remove_mapping_subtitle, player.getName()),
                    getString(R.string.review_remove_mapping_message),
                    getString(R.string.map_player_unlink),
                    true,
                    () -> updateReviewPlayerMapping(game, player, null));
        });
        dialogView.findViewById(R.id.btn_cancel_mapping)
                .setOnClickListener(v -> dialog.dismiss());
        android.widget.EditText search = dialogView.findViewById(R.id.input_user_search);
        android.widget.ListView list = dialogView.findViewById(R.id.list_users);
        android.widget.ProgressBar progress = dialogView.findViewById(R.id.progress_users);
        TextView empty = dialogView.findViewById(R.id.text_users_empty);
        TextView current = dialogView.findViewById(R.id.text_current_mapping);
        current.setVisibility(View.VISIBLE);
        current.setText(player.getUserId() == null || player.getUserId().trim().isEmpty()
                ? R.string.review_player_mapping_required
                : R.string.review_player_mapped);
        progress.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        dialog.show();
        styleDialogWindow(dialog);
        new AppUserRepository().getUsersCached(new AppUserRepository.UsersCallback() {
            @Override
            public void onSuccess(List<AppUser> users) {
                if (!dialog.isShowing()) return;
                List<AppUser> available = new java.util.ArrayList<>();
                java.util.Set<String> used = new java.util.HashSet<>();
                if (game.getPlayers() != null) {
                    for (Player candidate : game.getPlayers()) {
                        if (candidate != null
                                && (candidate.getPlayerId() == null
                                || !candidate.getPlayerId().equals(player.getPlayerId()))
                                && candidate.getUserId() != null) {
                            used.add(candidate.getUserId());
                        }
                    }
                }
                for (AppUser user : AppUserDirectoryFilter.forPlayerMapping(users)) {
                    if (user.getProfileName() != null && !used.contains(user.getUserId())) {
                        available.add(user);
                    }
                }
                List<AppUser> visible = new java.util.ArrayList<>(available);
                android.widget.ArrayAdapter<AppUser> adapter =
                        new android.widget.ArrayAdapter<AppUser>(
                                requireContext(), R.layout.item_map_user,
                                R.id.text_user_name, visible) {
                            @Override
                            public View getView(int position, View convertView,
                                    ViewGroup parent) {
                                View row = super.getView(position, convertView, parent);
                                AppUser user = getItem(position);
                                String displayName = user.getDisplayName();
                                ((TextView) row.findViewById(R.id.text_user_name)).setText(displayName);
                                ((TextView) row.findViewById(R.id.text_user_detail))
                                        .setText(user.getUserId().equals(player.getUserId())
                                                ? getString(R.string.map_player_currently_linked)
                                                : "");
                                ProfileAvatarBinder.bindWithPhotoUrl(
                                        row,
                                        row.findViewById(R.id.user_avatar_image),
                                        row.findViewById(R.id.user_avatar_initial),
                                        displayName,
                                        user.getPhotoUrl(),
                                        user.getProfileVersion(),
                                        null,
                                        false,
                                        null,
                                        null);
                                row.findViewById(R.id.icon_user_selected).setVisibility(
                                        user.getUserId().equals(player.getUserId())
                                                ? View.VISIBLE : View.GONE);
                                row.setBackgroundResource(user.getUserId().equals(player.getUserId())
                                        ? R.drawable.user_mapping_selected_background
                                        : R.drawable.user_mapping_row_background);
                                return row;
                            }
                        };
                list.setAdapter(adapter);
                progress.setVisibility(View.GONE);
                updateReviewMappingList(list, empty, visible);
                search.addTextChangedListener(new android.text.TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override public void afterTextChanged(android.text.Editable value) {
                        String query = value.toString().trim().toLowerCase(java.util.Locale.ROOT);
                        visible.clear();
                        for (AppUser user : available) {
                            if (query.isEmpty() || user.getDisplayName()
                                    .toLowerCase(java.util.Locale.ROOT).contains(query)) {
                                visible.add(user);
                            }
                        }
                        adapter.notifyDataSetChanged();
                        updateReviewMappingList(list, empty, visible);
                    }
                });
                list.setOnItemClickListener((parent, row, position, id) -> {
                    AppUser selected = visible.get(position);
                    dialog.dismiss();
                    if (selected.getUserId().equals(player.getUserId())) return;
                    showReviewActionDialog(
                            R.drawable.ic_link_players,
                            getString(R.string.review_replace_mapping_title),
                            getString(R.string.review_replace_mapping_subtitle,
                                    player.getName(), selected.getDisplayName()),
                            getString(R.string.review_replace_mapping_message),
                            getString(R.string.review_change_mapping),
                            false,
                            () -> updateReviewPlayerMapping(game, player, selected));
                });
            }

            @Override
            public void onFailure(Exception exception) {
                progress.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
                empty.setText(R.string.map_player_load_failed);
            }
        });
    }

    private void updateReviewPlayerMapping(
            GameItem game, Player player, AppUser selected) {
        beginReviewOperation(getString(R.string.review_operation_mapping));
        homeViewModel.updatePlayerMapping(
                game.getGameId(), player.getPlayerId(), selected == null ? null : selected.getUserId(),
                () -> {
                    if (!isAdded()) return;
                    endReviewOperation();
                    com.example.rummypulse.utils.ModernToast.success(
                            getContext(), getString(R.string.review_mapping_saved));
                },
                message -> {
                    if (!isAdded()) return;
                    endReviewOperation();
                    com.example.rummypulse.utils.ModernToast.error(getContext(), message);
                });
    }

    private void updateReviewMappingList(
            android.widget.ListView list, TextView empty, List<AppUser> users) {
        boolean populated = users != null && !users.isEmpty();
        list.setVisibility(populated ? View.VISIBLE : View.GONE);
        empty.setVisibility(populated ? View.GONE : View.VISIBLE);
        if (!populated) empty.setText(R.string.add_player_contact_admin);
    }

    private void styleDialogWindow(AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        dialog.getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = Math.min(Math.round(dm.widthPixels * 0.92f), Math.round(420 * dm.density));
        dialog.getWindow().setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void updateSelectionControls(int selectedCount, boolean allSelected) {
        if (binding == null || tableAdapter == null) {
            return;
        }
        updatingSelectionControls = true;
        binding.checkboxSelectAll.setChecked(allSelected);
        updatingSelectionControls = false;
        binding.checkboxSelectAll.setEnabled(
                !reviewOperationInProgress && tableAdapter.getItemCount() > 0);
        binding.textSelectedGames.setText(selectedCount == 0
                ? getString(R.string.review_selected_none)
                : getResources().getQuantityString(
                        R.plurals.review_selected_count, selectedCount, selectedCount));
        binding.btnDeleteSelected.setEnabled(
                !reviewOperationInProgress && selectedCount > 0);
    }

    private void onDeleteSelectedClicked() {
        if (tableAdapter == null || reviewOperationInProgress || getContext() == null) {
            return;
        }
        List<String> selectedGameIds = tableAdapter.getSelectedGameIds();
        int selectedCount = selectedGameIds.size();
        if (selectedCount == 0) {
            return;
        }

        showReviewActionDialog(
                R.drawable.ic_delete,
                getString(R.string.review_delete_selected_title),
                selectedCount == 1
                        ? "Remove 1 selected game"
                        : "Remove " + selectedCount + " selected games",
                getResources().getQuantityString(
                        R.plurals.review_delete_selected_message,
                        selectedCount,
                        selectedCount),
                getString(R.string.review_delete_selected),
                true,
                () -> {
                    beginReviewOperation(getResources().getQuantityString(
                            R.plurals.review_delete_selected_progress,
                            selectedCount,
                            selectedCount));
                    homeViewModel.deleteGames(
                            selectedGameIds,
                            () -> {
                                if (!isAdded() || binding == null || getContext() == null) {
                                    return;
                                }
                                tableAdapter.clearSelection();
                                endReviewOperation();
                                com.example.rummypulse.utils.ModernToast.success(
                                        getContext(),
                                        getResources().getQuantityString(
                                                R.plurals.review_delete_selected_success,
                                                selectedCount,
                                                selectedCount));
                            },
                            error -> {
                                if (!isAdded() || binding == null || tableAdapter == null) {
                                    return;
                                }
                                endReviewOperation();
                                updateSelectionControls(
                                        tableAdapter.getSelectedGameIds().size(),
                                        false);
                            });
                });
    }

    private void onApproveAllClicked() {
        List<GameItem> items = homeViewModel.getGameItems().getValue();
        if (items == null) {
            return;
        }
        int count = 0;
        for (GameItem g : items) {
            if (g != null && g.isCompleted()) {
                count++;
            }
        }
        if (count == 0) {
            com.example.rummypulse.utils.ModernToast.warning(getContext(), "No completed games to approve");
            return;
        }
        final int approvedCount = count;
        showReviewActionDialog(
                R.drawable.ic_approve,
                "Approve all completed games?",
                getResources().getQuantityString(
                        R.plurals.review_finalize_completed_games, approvedCount, approvedCount),
                "Each game will move to the approved list and its results will be finalized.",
                "Approve all",
                false,
                () -> {
                    beginReviewOperation(getString(
                            R.string.review_operation_approving_many, approvedCount));
                    homeViewModel.approveAllCompletedGames(items, () -> {
                        if (!isAdded() || getContext() == null) {
                            return;
                        }
                        endReviewOperation();
                        com.example.rummypulse.utils.ModernToast.success(getContext(),
                                getResources().getQuantityString(
                                        R.plurals.review_games_approved,
                                        approvedCount,
                                        approvedCount));
                    });
                });
    }

    private void showReviewActionDialog(
            int iconRes,
            CharSequence title,
            CharSequence subtitle,
            CharSequence message,
            CharSequence confirmText,
            boolean destructive,
            Runnable action) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        View view = LayoutInflater.from(getContext()).inflate(
                R.layout.dialog_action_confirmation, null, false);
        android.widget.ImageView icon =
                view.findViewById(R.id.image_action_dialog_icon);
        TextView titleView = view.findViewById(R.id.text_action_dialog_title);
        TextView subtitleView = view.findViewById(R.id.text_action_dialog_subtitle);
        TextView messageView = view.findViewById(R.id.text_action_dialog_message);
        com.google.android.material.card.MaterialCardView messageCard =
                view.findViewById(R.id.card_action_dialog_message);
        com.google.android.material.button.MaterialButton cancel =
                view.findViewById(R.id.btn_action_dialog_cancel);
        com.google.android.material.button.MaterialButton confirm =
                view.findViewById(R.id.btn_action_dialog_confirm);

        icon.setImageResource(iconRes);
        titleView.setText(title);
        subtitleView.setText(subtitle);
        messageView.setText(message);
        confirm.setText(confirmText);
        if (destructive) {
            int red = androidx.core.content.ContextCompat.getColor(
                    requireContext(), R.color.error_red);
            icon.setBackgroundResource(
                    R.drawable.view_access_icon_rejected_background);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(red));
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
                    messageView, android.content.res.ColorStateList.valueOf(red));
            messageCard.setStrokeColor(red);
            confirm.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(red));
        }

        AlertDialog dialog = new AlertDialog.Builder(
                requireContext(), R.style.DarkDialogTheme)
                .setView(view)
                .setCancelable(true)
                .create();
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
            android.util.DisplayMetrics dm =
                    getResources().getDisplayMetrics();
            int maxWidth = Math.round(420 * dm.density);
            int width = Math.min(Math.round(dm.widthPixels * 0.92f), maxWidth);
            dialog.getWindow().setLayout(
                    width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void refreshGames() {
        com.example.rummypulse.utils.ModernToast.progress(getContext(), "Refreshing games...");
        homeViewModel.refreshGames();

        binding.btnRefresh.postDelayed(() -> {
            if (isAdded() && getContext() != null) {
                com.example.rummypulse.utils.ModernToast.success(getContext(), "Games refreshed successfully!");
            }
        }, 1500);
    }

    private static String formatPlainDecimalForField(double v) {
        BigDecimal bd = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return bd.toPlainString();
    }

    private static double snapToFivePaise(double value) {
        return Math.round(value * 20.0) / 20.0;
    }

    private static double clampGamePointFactor(double value) {
        double s = snapToFivePaise(value);
        if (s < 0.05) {
            return 0.05;
        }
        if (s > 100.0) {
            return 100.0;
        }
        return s;
    }

    private static double parseGamePointFactorForDisplay(String gamePointFactorStr) {
        if (gamePointFactorStr == null || gamePointFactorStr.isEmpty()) {
            return 0.05;
        }
        try {
            return Double.parseDouble(gamePointFactorStr.trim());
        } catch (NumberFormatException e) {
            return 0.05;
        }
    }

    private Double parseAndClampGamePointFactor(TextInputLayout layout, TextInputEditText edit) {
        String s = edit.getText() != null ? edit.getText().toString().trim() : "";
        if (TextUtils.isEmpty(s)) {
            layout.setError(getString(R.string.dialog_game_point_factor_required));
            return null;
        }
        try {
            double raw = Double.parseDouble(s);
            if (raw <= 0 || raw > 100) {
                layout.setError(getString(R.string.dialog_game_point_factor_invalid));
                return null;
            }
            double clamped = clampGamePointFactor(raw);
            layout.setError(null);
            return clamped;
        } catch (NumberFormatException e) {
            layout.setError(getString(R.string.dialog_game_point_factor_invalid));
            return null;
        }
    }

    private Integer parseBoardAdjustmentPercent(TextInputLayout layout, TextInputEditText edit) {
        String s = edit.getText() != null ? edit.getText().toString().trim() : "";
        if (TextUtils.isEmpty(s)) {
            layout.setError(getString(R.string.dialog_board_adjustment_required));
            return null;
        }
        try {
            int value = Integer.parseInt(s);
            if (value < 0 || value > 100) {
                layout.setError(getString(R.string.dialog_board_adjustment_invalid));
                return null;
            }
            layout.setError(null);
            return value;
        } catch (NumberFormatException e) {
            layout.setError(getString(R.string.dialog_board_adjustment_invalid));
            return null;
        }
    }
}
