package com.example.rummypulse.ui.usermanagement;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
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
import java.util.Locale;
import java.util.Map;

/**
 * Handles bounded user pages and local role updates for User Management.
 */
public class UserManagementViewModel extends ViewModel {

    private static final String TAG = "UserManagementViewModel";

    private final AppUserRepository appUserRepository = new AppUserRepository();
    private final MutableLiveData<List<AppUser>> users = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MediatorLiveData<List<AppUser>> displayedUsers = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingMore = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> roleUpdateSuccess = new MutableLiveData<>();
    private final MutableLiveData<Boolean> deleteSuccess = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hiddenUpdateSuccess = new MutableLiveData<>();

    private DocumentSnapshot nextCursor;
    private boolean hasMore = true;
    private boolean pageRequestInProgress;
    private boolean refreshQueued;
    private boolean fullDirectoryRequestInProgress;

    public UserManagementViewModel() {
        displayedUsers.addSource(users, ignored -> publishDisplayedUsers());
        displayedUsers.addSource(searchQuery, ignored -> publishDisplayedUsers());
    }

    public LiveData<List<AppUser>> getDisplayedUsers() {
        return displayedUsers;
    }

    public LiveData<String> getSearchQuery() {
        return searchQuery;
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

    public LiveData<Boolean> getDeleteSuccess() {
        return deleteSuccess;
    }

    public LiveData<Boolean> getHiddenUpdateSuccess() {
        return hiddenUpdateSuccess;
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
        if (isSearchActive() || pageRequestInProgress || !hasMore) {
            return;
        }
        loadPage(false);
    }

    public void setSearchQuery(String query) {
        String normalized = query == null ? "" : query.trim();
        searchQuery.setValue(normalized);
        if (!normalized.isEmpty() && hasMore) {
            ensureFullDirectoryLoaded();
        }
    }

    public boolean isSearchActive() {
        String query = searchQuery.getValue();
        return query != null && !query.isEmpty();
    }

    private void publishDisplayedUsers() {
        displayedUsers.setValue(filterUsers(safeUsers(), searchQuery.getValue()));
    }

    private void ensureFullDirectoryLoaded() {
        if (fullDirectoryRequestInProgress || !hasMore) {
            return;
        }
        fullDirectoryRequestInProgress = true;
        appUserRepository.getUsersCached(new AppUserRepository.UsersCallback() {
            @Override
            public void onSuccess(List<AppUser> allUsers) {
                List<AppUser> merged = new ArrayList<>(allUsers);
                sortUsers(merged);
                users.setValue(merged);
                hasMore = false;
                nextCursor = null;
                fullDirectoryRequestInProgress = false;
                Log.d(TAG, "Loaded full user directory for search: " + merged.size());
            }

            @Override
            public void onFailure(Exception exception) {
                fullDirectoryRequestInProgress = false;
                Log.w(TAG, "Could not load full user directory for search", exception);
            }
        });
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
                        if (replaceExisting && isSearchActive()) {
                            ensureFullDirectoryLoaded();
                        }
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

    /**
     * Deletes the user document and removes the row from the current list without a full reload.
     */
    public void deleteUser(String userId) {
        loading.setValue(true);
        error.setValue(null);
        deleteSuccess.setValue(false);

        appUserRepository.deleteUser(userId, new AppUserRepository.VoidCallback() {
            @Override
            public void onSuccess() {
                List<AppUser> updatedList = new ArrayList<>(safeUsers());
                updatedList.removeIf(user -> userId.equals(user.getUserId()));
                users.setValue(updatedList);
                loading.setValue(false);
                deleteSuccess.setValue(true);
                Log.d(TAG, "Removed deleted user locally: " + userId);
            }

            @Override
            public void onFailure(Exception exception) {
                error.setValue("Failed to delete user: " + exception.getMessage());
                loading.setValue(false);
                deleteSuccess.setValue(false);
            }
        });
    }

    /**
     * Toggles whether a user appears in player-mapping pickers without removing their account.
     */
    public void updateUserHidden(String userId, boolean hidden) {
        loading.setValue(true);
        error.setValue(null);
        hiddenUpdateSuccess.setValue(false);

        appUserRepository.updateUserHidden(userId, hidden, new AppUserRepository.AppUserCallback() {
            @Override
            public void onSuccess(AppUser updatedUser) {
                List<AppUser> updatedList = new ArrayList<>(safeUsers());
                boolean found = false;
                for (AppUser user : updatedList) {
                    if (userId.equals(user.getUserId())) {
                        user.setHidden(hidden);
                        found = true;
                        break;
                    }
                }
                if (found) {
                    sortUsers(updatedList);
                    users.setValue(updatedList);
                }
                loading.setValue(false);
                hiddenUpdateSuccess.setValue(true);
                Log.d(TAG, "Applied hidden flag locally for " + userId + " hidden=" + hidden);
            }

            @Override
            public void onFailure(Exception exception) {
                error.setValue("Failed to update user visibility: " + exception.getMessage());
                loading.setValue(false);
                hiddenUpdateSuccess.setValue(false);
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

    static List<AppUser> filterUsers(List<AppUser> source, String query) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(source);
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<AppUser> filtered = new ArrayList<>();
        for (AppUser user : source) {
            if (matchesSearch(user, needle)) {
                filtered.add(user);
            }
        }
        return filtered;
    }

    static boolean matchesSearch(AppUser user, String needle) {
        if (user == null || needle == null || needle.isEmpty()) {
            return true;
        }
        String haystack = (
                safeText(user.getDisplayName()) + ' '
                        + safeText(user.getEmail()) + ' '
                        + safeText(user.getProvider()) + ' '
                        + (user.getRole() != null ? user.getRole().getDisplayName() : ""))
                .toLowerCase(Locale.ROOT);
        return haystack.contains(needle);
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
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
