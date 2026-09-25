package com.example.rummypulse.ui.playerranking;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.rummypulse.data.AppUser;
import com.example.rummypulse.data.AppUserRepository;
import com.example.rummypulse.utils.UserProfileIndex;
import com.example.rummypulse.data.PlayerLeaderboardRepository;
import com.example.rummypulse.data.PlayerStats;
import com.example.rummypulse.ui.dashboard.Leaderboard;
import com.example.rummypulse.ui.dashboard.LeaderboardEntry;
import com.example.rummypulse.ui.dashboard.RankingSort;
import com.example.rummypulse.ui.dashboard.StatsPeriod;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full player ranking for one reporting period.
 *
 * <p>Reads the leaderboard listener the dashboard already holds, so opening this screen and
 * switching periods on it cost no Firestore reads. The listener is process-wide and torn down at
 * sign-out, not when this ViewModel clears.
 *
 * <p>Names come from the account directory rather than the stats documents. Stats carry the
 * deliberately shortened in-game player name, which makes players who share a first name
 * indistinguishable in a ranked list.
 */
public class PlayerRankingViewModel extends ViewModel {

    private final PlayerLeaderboardRepository repository;
    private final MutableLiveData<StatsPeriod> selectedPeriod;
    private final MutableLiveData<RankingSort> selectedSort;
    private final MutableLiveData<Map<String, String>> fullNames;
    private final MutableLiveData<Map<String, String>> photoUrlsByUserId;
    private final MutableLiveData<Map<String, Long>> profileVersionsByUserId;
    private final MediatorLiveData<List<LeaderboardEntry>> ranking;
    private final AppUserRepository.DirectoryChangeListener directoryChangeListener =
            changedUsers -> loadFullNames();

    public PlayerRankingViewModel() {
        repository = PlayerLeaderboardRepository.getInstance();
        repository.start();
        selectedPeriod = new MutableLiveData<>(StatsPeriod.THIS_MONTH);
        selectedSort = new MutableLiveData<>(RankingSort.NET_TOTAL);
        fullNames = new MutableLiveData<>(Collections.emptyMap());
        photoUrlsByUserId = new MutableLiveData<>(Collections.emptyMap());
        profileVersionsByUserId = new MutableLiveData<>(Collections.emptyMap());

        ranking = new MediatorLiveData<>();
        ranking.setValue(Collections.emptyList());
        ranking.addSource(repository.getAllStats(), stats -> rebuild());
        ranking.addSource(selectedPeriod, period -> rebuild());
        ranking.addSource(selectedSort, sort -> rebuild());
        ranking.addSource(fullNames, names -> rebuild());

        loadFullNames();
        AppUserRepository.addDirectoryChangeListener(directoryChangeListener);
    }

    @Override
    protected void onCleared() {
        AppUserRepository.removeDirectoryChangeListener(directoryChangeListener);
        super.onCleared();
    }

    /**
     * Fills the account directory from a process-wide six-hour cache shared with the map-player
     * dialog. A failure leaves the shortened stats names in place rather than blanking the list.
     */
    private void loadFullNames() {
        new AppUserRepository().getUsersCached(new AppUserRepository.UsersCallback() {
            @Override
            public void onSuccess(List<AppUser> users) {
                fullNames.setValue(indexByUserId(users));
                photoUrlsByUserId.setValue(UserProfileIndex.photoUrlsByUserId(users));
                profileVersionsByUserId.setValue(UserProfileIndex.profileVersionsByUserId(users));
            }

            @Override
            public void onFailure(Exception exception) {
                // Keep whatever names the stats documents already provide.
            }
        });
    }

    private static Map<String, String> indexByUserId(List<AppUser> users) {
        Map<String, String> byUserId = new HashMap<>();
        if (users == null) {
            return byUserId;
        }
        for (AppUser user : users) {
            if (user == null || user.getUserId() == null) {
                continue;
            }
            String name = preferredName(user);
            if (name != null) {
                byUserId.put(user.getUserId(), name);
            }
        }
        return byUserId;
    }

    /** Effective public profile name; private email is never used as a label. */
    private static String preferredName(AppUser user) {
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName.trim();
        }
        return null;
    }

    public LiveData<List<LeaderboardEntry>> getRanking() {
        return ranking;
    }

    public LiveData<StatsPeriod> getSelectedPeriod() {
        return selectedPeriod;
    }

    public void selectPeriod(StatsPeriod period) {
        if (period != null && period != selectedPeriod.getValue()) {
            selectedPeriod.setValue(period);
        }
    }

    public LiveData<RankingSort> getSelectedSort() {
        return selectedSort;
    }

    public LiveData<Map<String, String>> getPhotoUrlsByUserId() {
        return photoUrlsByUserId;
    }

    public LiveData<Map<String, Long>> getProfileVersionsByUserId() {
        return profileVersionsByUserId;
    }

    /**
     * Builds a full profile across every reporting period for one ranked row.
     */
    @Nullable
    public PlayerRankingDetail buildDetail(LeaderboardEntry entry, boolean showAmounts) {
        if (entry == null) {
            return null;
        }

        PlayerStats stats = findStats(entry.getUserId());
        StatsPeriod activePeriod = selectedPeriod.getValue();
        if (activePeriod == null) {
            activePeriod = StatsPeriod.THIS_MONTH;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String currentUserId = user == null ? null : user.getUid();

        List<PlayerStats> allStats = repository.getAllStats().getValue();
        List<PlayerRankingDetail.PeriodBreakdown> breakdowns = new ArrayList<>();
        for (StatsPeriod period : StatsPeriod.values()) {
            PlayerStats.Bucket bucket = period.bucketOf(stats);
            Map<RankingSort, Integer> rankBySort = new EnumMap<>(RankingSort.class);
            double maxAbsoluteNet = 0;
            double leaderNet = 0;
            double periodAverageNet = 0;
            int totalPlayers = 0;

            for (RankingSort sort : RankingSort.values()) {
                List<LeaderboardEntry> ranked = Leaderboard.rankAll(
                        allStats, period, currentUserId, sort, fullNames.getValue());
                if (sort == RankingSort.NET_TOTAL) {
                    totalPlayers = ranked.size();
                    for (LeaderboardEntry candidate : ranked) {
                        maxAbsoluteNet = Math.max(
                                maxAbsoluteNet, Math.abs(candidate.getFinalGamePoints()));
                    }
                    if (!ranked.isEmpty()) {
                        leaderNet = ranked.get(0).getFinalGamePoints();
                        double sum = 0;
                        for (LeaderboardEntry candidate : ranked) {
                            sum += candidate.getFinalGamePoints();
                        }
                        periodAverageNet = sum / ranked.size();
                    }
                }
                LeaderboardEntry match = findEntry(ranked, entry.getUserId());
                rankBySort.put(sort, match == null ? 0 : match.getRank());
            }

            breakdowns.add(new PlayerRankingDetail.PeriodBreakdown(
                    period,
                    totalPlayers,
                    bucket,
                    maxAbsoluteNet,
                    leaderNet,
                    periodAverageNet,
                    rankBySort));
        }

        return new PlayerRankingDetail(entry, activePeriod, breakdowns, showAmounts);
    }

    @Nullable
    private static LeaderboardEntry findEntry(List<LeaderboardEntry> ranked, String userId) {
        if (ranked == null || userId == null) {
            return null;
        }
        for (LeaderboardEntry candidate : ranked) {
            if (userId.equals(candidate.getUserId())) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private PlayerStats findStats(String userId) {
        if (userId == null) {
            return null;
        }
        List<PlayerStats> all = repository.getAllStats().getValue();
        if (all == null) {
            return null;
        }
        for (PlayerStats stats : all) {
            if (stats != null && userId.equals(stats.getUserId())) {
                return stats;
            }
        }
        return null;
    }

    public void selectSort(RankingSort sort) {
        if (sort != null && sort != selectedSort.getValue()) {
            selectedSort.setValue(sort);
        }
    }

    /** Applies the period the caller arrived with, without clobbering a later user choice. */
    public void setInitialPeriod(StatsPeriod period) {
        if (period != null) {
            selectedPeriod.setValue(period);
        }
    }

    private void rebuild() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        List<LeaderboardEntry> ranked = Leaderboard.rankAll(
                repository.getAllStats().getValue(),
                selectedPeriod.getValue(),
                user == null ? null : user.getUid(),
                selectedSort.getValue(),
                fullNames.getValue());
        ranking.setValue(pinCurrentUser(withFullNames(ranked, fullNames.getValue())));
    }

    /**
     * Lifts the signed-in player to the first row so they never have to hunt for themselves in a
     * long list.
     *
     * <p>Only the display order moves. Each entry keeps the rank it earned, which the row badge
     * shows, so a mid-table player sitting at the top still reads as mid-table.
     */
    static List<LeaderboardEntry> pinCurrentUser(List<LeaderboardEntry> ranked) {
        for (int i = 0; i < ranked.size(); i++) {
            if (!ranked.get(i).isCurrentUser()) {
                continue;
            }
            if (i == 0) {
                return ranked;
            }
            List<LeaderboardEntry> reordered = new ArrayList<>(ranked);
            reordered.add(0, reordered.remove(i));
            return reordered;
        }
        return ranked;
    }

    /** Swaps in the account name where we have one, keeping the stats name otherwise. */
    private static List<LeaderboardEntry> withFullNames(
            List<LeaderboardEntry> ranked, Map<String, String> names) {
        if (ranked.isEmpty() || names == null || names.isEmpty()) {
            return ranked;
        }
        List<LeaderboardEntry> resolved = new ArrayList<>(ranked.size());
        for (LeaderboardEntry entry : ranked) {
            String fullName = names.get(entry.getUserId());
            resolved.add(fullName == null ? entry : new LeaderboardEntry(
                    entry.getUserId(),
                    fullName,
                    entry.getFinalGamePoints(),
                    entry.getGames(),
                    entry.getWins(),
                    entry.getRank(),
                    entry.isCurrentUser()));
        }
        return resolved;
    }
}
