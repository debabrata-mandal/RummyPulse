package com.example.rummypulse.ui.usermanagement;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.AppUserRepository;
import com.example.rummypulse.data.UserRole;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles bounded user pages and local role updates for User Management.
 */
public class UserManagementViewModel extends ViewModel {

    private static final String TAG = "UserManagementViewModel";

    private final AppUserRepository appUserRepository = new AppUserRepository();
    private final MutableLiveData<List<AppUser>> users = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingMore = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> roleUpdateSuccess = new MutableLiveData<>();

    private DocumentSnapshot nextCursor;
    private boolean hasMore = true;
    private boolean pageRequestInProgress;
    private boolean refreshQueued;

    public LiveData<List<AppUser>> getUsers() {
        return users;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getLoadingMore() {
        return loadingMore;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getRoleUpdateSuccess() {
        return roleUpdateSuccess;
    }

    /**
     * Explicit refresh: clears the cursor and reloads the first bounded page.
     */
    public void loadAllUsers() {
        if (pageRequestInProgress) {
            refreshQueued = true;
            return;
        }
        nextCursor = null;
        hasMore = true;
        loadPage(true);
    }

    public void loadNextPage() {
        if (pageRequestInProgress || !hasMore) {
            return;
        }
        loadPage(false);
    }

    private void loadPage(boolean replaceExisting) {
        pageRequestInProgress = true;
        error.setValue(null);
        if (replaceExisting) {
            loading.setValue(true);
        } else {
            loadingMore.setValue(true);
        }

        appUserRepository.getUsersPage(
                replaceExisting ? null : nextCursor,
                AppUserRepository.USER_PAGE_SIZE,
                new AppUserRepository.UsersPageCallback() {
                    @Override
                    public void onSuccess(AppUserRepository.UsersPage page) {
                        List<AppUser> merged = replaceExisting
                                ? new ArrayList<>()
                                : new ArrayList<>(safeUsers());
                        mergeByUserId(merged, page.users);
                        sortUsers(merged);
                        users.setValue(merged);
                        nextCursor = page.nextCursor;
                        hasMore = page.hasMore && page.nextCursor != null;
                        finishPageRequest();
                        Log.d(TAG, "Displayed " + merged.size()
                                + " users; hasMore=" + hasMore);
                    }

                    @Override
                    public void onFailure(Exception exception) {
                        error.setValue("Failed to load users: " + exception.getMessage());
                        finishPageRequest();
                    }
                });
    }

    private void finishPageRequest() {
        pageRequestInProgress = false;
        loading.setValue(false);
        loadingMore.setValue(false);
        if (refreshQueued) {
            refreshQueued = false;
            loadAllUsers();
        }
    }

    /**
     * Applies the confirmed role write to the existing row without a document read or list reload.
     */
    public void updateUserRole(String userId, UserRole newRole) {
        loading.setValue(true);
        error.setValue(null);
        roleUpdateSuccess.setValue(false);

        appUserRepository.updateUserRole(userId, newRole, new AppUserRepository.AppUserCallback() {
            @Override
            public void onSuccess(AppUser updatedUser) {
                List<AppUser> updatedList = new ArrayList<>(safeUsers());
                boolean found = false;
                for (AppUser user : updatedList) {
                    if (userId.equals(user.getUserId())) {
                        user.setRole(newRole);
                        found = true;
                        break;
                    }
                }
                if (found) {
                    sortUsers(updatedList);
                    users.setValue(updatedList);
                }
                loading.setValue(false);
                roleUpdateSuccess.setValue(true);
                Log.d(TAG, "Applied role update locally for " + userId);
            }

            @Override
            public void onFailure(Exception exception) {
                error.setValue("Failed to update user role: " + exception.getMessage());
                loading.setValue(false);
                roleUpdateSuccess.setValue(false);
            }
        });
    }

    private List<AppUser> safeUsers() {
        List<AppUser> current = users.getValue();
        return current != null ? current : Collections.emptyList();
    }

    private static void mergeByUserId(List<AppUser> destination, List<AppUser> incoming) {
        Map<String, AppUser> merged = new LinkedHashMap<>();
        for (AppUser user : destination) {
            merged.put(user.getUserId(), user);
        }
        for (AppUser user : incoming) {
            merged.put(user.getUserId(), user);
        }
        destination.clear();
        destination.addAll(merged.values());
    }

    static void sortUsers(List<AppUser> userList) {
        userList.sort(new Comparator<AppUser>() {
            @Override
            public int compare(AppUser first, AppUser second) {
                boolean firstAdmin = first.getRole() == UserRole.ADMIN_USER;
                boolean secondAdmin = second.getRole() == UserRole.ADMIN_USER;
                if (firstAdmin != secondAdmin) {
                    return firstAdmin ? -1 : 1;
                }
                String firstName = first.getDisplayName() != null ? first.getDisplayName() : "";
                String secondName = second.getDisplayName() != null ? second.getDisplayName() : "";
                return firstName.compareToIgnoreCase(secondName);
            }
        });
    }
}
