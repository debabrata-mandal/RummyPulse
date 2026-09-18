package com.example.rummypulse.ui.playerranking;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.example.rummypulse.R;
import com.example.rummypulse.ui.dashboard.LeaderboardAmountFormatter;
import com.example.rummypulse.ui.dashboard.LeaderboardEntry;
import com.example.rummypulse.ui.dashboard.RankingSort;
import com.example.rummypulse.ui.dashboard.StatsPeriod;
import com.example.rummypulse.utils.ProfileAvatarBinder;
import com.google.android.material.card.MaterialCardView;

import java.util.Locale;
import java.util.Map;

/**
 * Interactive ranking profile with a period selector and deep stats for the chosen window.
 */
public final class PlayerRankingDetailDialog {

    private static final int BADGE_FILL_ALPHA = 0x1F;
    private static final int BADGE_RING_ALPHA = 0x66;

    private PlayerRankingDetailDialog() {
    }

    public static void show(
            @NonNull Context context,
            @NonNull PlayerRankingDetail detail,
            @Nullable Map<String, String> photoUrlsByUserId,
            @Nullable Map<String, Long> profileVersionsByUserId) {
        Dialog dialog = new Dialog(context, R.style.DarkDialogTheme);
        dialog.setContentView(R.layout.dialog_player_ranking_detail);
        dialog.setCancelable(true);

        DetailController controller = new DetailController(
                context, dialog, detail, photoUrlsByUserId, profileVersionsByUserId);
        controller.bind();

        dialog.findViewById(R.id.btn_ranking_detail_close).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int maxPx = context.getResources().getDimensionPixelSize(R.dimen.dialog_create_game_max_width);
            int widthPx = Math.min((int) (dm.widthPixels * 0.94f), maxPx);
            window.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT);

            ScrollView scroll = dialog.findViewById(R.id.scroll_ranking_detail_body);
            if (scroll != null) {
                ViewGroup.LayoutParams params = scroll.getLayoutParams();
                params.height = (int) (dm.heightPixels * 0.58f);
                scroll.setLayoutParams(params);
            }
        }
    }

    private static final class DetailController {

        private final Context context;
        private final Dialog dialog;
        private final PlayerRankingDetail detail;
        private final Map<String, String> photoUrlsByUserId;
        private final Map<String, Long> profileVersionsByUserId;
        private StatsPeriod selectedPeriod;

        DetailController(
                Context context,
                Dialog dialog,
                PlayerRankingDetail detail,
                Map<String, String> photoUrlsByUserId,
                Map<String, Long> profileVersionsByUserId) {
            this.context = context;
            this.dialog = dialog;
            this.detail = detail;
            this.photoUrlsByUserId = photoUrlsByUserId;
            this.profileVersionsByUserId = profileVersionsByUserId;
            this.selectedPeriod = detail.getActivePeriod();
        }

        void bind() {
            bindIdentityHeader();
            wirePeriodSelector();
            bindSelectedPeriod();
        }

        private void bindIdentityHeader() {
            LeaderboardEntry entry = detail.getListEntry();
            TextView rankBadge = dialog.findViewById(R.id.text_ranking_detail_rank_badge);
            ImageView avatarImage = dialog.findViewById(R.id.image_ranking_detail_avatar);
            TextView avatarInitials = dialog.findViewById(R.id.text_ranking_detail_avatar_initials);
            TextView name = dialog.findViewById(R.id.text_ranking_detail_name);

            bindRankBadge(rankBadge, entry.getRank());

            PlayerRankingDetail.PeriodBreakdown active = detail.activeBreakdown();
            int totalPlayers = active == null ? 0 : active.getTotalPlayers();
            int accent = ContextCompat.getColor(context, accentColorFor(entry.getRank(), totalPlayers));
            ProfileAvatarBinder.bind(
                    dialog.findViewById(android.R.id.content),
                    avatarImage,
                    avatarInitials,
                    entry.getUserId(),
                    entry.getDisplayName(),
                    photoUrlsByUserId,
                    profileVersionsByUserId,
                    ColorStateList.valueOf(accent));

            name.setText(entry.isCurrentUser()
                    ? context.getString(R.string.player_ranking_name_you, entry.getDisplayName())
                    : entry.getDisplayName());
        }

        private void wirePeriodSelector() {
            bindSegment(R.id.ranking_detail_segment_all_time, StatsPeriod.ALL_TIME);
            bindSegment(R.id.ranking_detail_segment_this_month, StatsPeriod.THIS_MONTH);
            bindSegment(R.id.ranking_detail_segment_last_month, StatsPeriod.LAST_MONTH);
            bindSegment(R.id.ranking_detail_segment_this_week, StatsPeriod.THIS_WEEK);
            stylePeriodSegments();
        }

        private void bindSegment(int viewId, StatsPeriod period) {
            dialog.findViewById(viewId).setOnClickListener(v -> {
                if (selectedPeriod != period) {
                    selectedPeriod = period;
                    stylePeriodSegments();
                    bindSelectedPeriod();
                }
            });
        }

        private void stylePeriodSegments() {
            styleSegment(R.id.ranking_detail_segment_all_time, StatsPeriod.ALL_TIME);
            styleSegment(R.id.ranking_detail_segment_this_month, StatsPeriod.THIS_MONTH);
            styleSegment(R.id.ranking_detail_segment_last_month, StatsPeriod.LAST_MONTH);
            styleSegment(R.id.ranking_detail_segment_this_week, StatsPeriod.THIS_WEEK);
        }

        private void styleSegment(int viewId, StatsPeriod period) {
            TextView segment = dialog.findViewById(viewId);
            boolean selected = selectedPeriod == period;
            segment.setBackgroundResource(selected
                    ? R.drawable.bg_segment_selected
                    : R.drawable.bg_segment_unselected);
            segment.setTextColor(ContextCompat.getColor(context, selected
                    ? R.color.text_primary
                    : R.color.view_text_secondary));
        }

        private void bindSelectedPeriod() {
            PlayerRankingDetail.PeriodBreakdown breakdown = detail.breakdownFor(selectedPeriod);
            TextView subtitle = dialog.findViewById(R.id.text_ranking_detail_subtitle);
            View content = dialog.findViewById(R.id.layout_ranking_detail_content);
            View empty = dialog.findViewById(R.id.text_ranking_detail_empty);

            if (breakdown == null || !breakdown.hasGames()) {
                subtitle.setText(context.getString(
                        R.string.player_ranking_detail_period_empty_subtitle,
                        context.getString(selectedPeriod.getTabLabelRes())));
                content.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
                bindRankBadge(dialog.findViewById(R.id.text_ranking_detail_rank_badge), 0);
                return;
            }

            empty.setVisibility(View.GONE);
            content.setVisibility(View.VISIBLE);

            bindRankBadge(dialog.findViewById(R.id.text_ranking_detail_rank_badge), breakdown.getRank());
            subtitle.setText(context.getString(
                    R.string.player_ranking_detail_period_subtitle,
                    breakdown.topPercentile(),
                    breakdown.getTotalPlayers()));

            bindHero(breakdown);
            bindActivityTiles(breakdown);
            bindRankRows(breakdown);
            bindPointRows(breakdown);
            bindComparison(breakdown);
        }

        private void bindHero(PlayerRankingDetail.PeriodBreakdown breakdown) {
            TextView heroNet = dialog.findViewById(R.id.text_ranking_detail_hero_net);
            TextView heroLabel = dialog.findViewById(R.id.text_ranking_detail_hero_label);
            TextView heroRank = dialog.findViewById(R.id.text_ranking_detail_hero_rank);

            heroLabel.setText(breakdown.getPeriod().getNetLabelRes());
            heroRank.setText(context.getString(
                    R.string.player_ranking_detail_rank_of,
                    breakdown.getRank(),
                    breakdown.getTotalPlayers()));

            if (detail.isShowAmounts()) {
                styleAmountText(heroNet, breakdown.getBucket().getFinalGamePoints());
            } else {
                heroNet.setText(R.string.game_view_amount_hidden);
                heroNet.setTextColor(ContextCompat.getColor(context, R.color.view_text_secondary));
            }
        }

        private void bindActivityTiles(PlayerRankingDetail.PeriodBreakdown breakdown) {
            bindTile(R.id.tile_ranking_detail_games,
                    String.format(Locale.getDefault(), "%,d", breakdown.getBucket().getGames()),
                    R.string.dashboard_stat_games, R.color.text_primary);
            bindTile(R.id.tile_ranking_detail_wins,
                    String.format(Locale.getDefault(), "%,d", breakdown.getBucket().getWins()),
                    R.string.dashboard_stat_wins, R.color.text_primary);
            bindTile(R.id.tile_ranking_detail_losses,
                    String.format(Locale.getDefault(), "%,d", breakdown.getLosses()),
                    R.string.player_ranking_detail_losses, R.color.text_primary);
            bindTile(R.id.tile_ranking_detail_win_rate,
                    context.getString(R.string.player_ranking_stat_win_rate, breakdown.winRatePercent()),
                    R.string.player_ranking_detail_win_rate, winRateTint(breakdown.winRatePercent()));
        }

        private void bindTile(int tileId, String value, @StringRes int labelRes, int valueColorRes) {
            View tile = dialog.findViewById(tileId);
            TextView valueView = tile.findViewById(R.id.text_ranking_detail_tile_value);
            TextView labelView = tile.findViewById(R.id.text_ranking_detail_tile_label);
            valueView.setText(value);
            valueView.setTextColor(ContextCompat.getColor(context, valueColorRes));
            labelView.setText(labelRes);
        }

        private void bindRankRows(PlayerRankingDetail.PeriodBreakdown breakdown) {
            LinearLayout container = dialog.findViewById(R.id.layout_ranking_detail_rank_rows);
            container.removeAllViews();
            addMetricRow(container, R.string.player_ranking_sort_net,
                    breakdown.rankFor(RankingSort.NET_TOTAL), breakdown.getTotalPlayers());
            addMetricRow(container, R.string.player_ranking_sort_avg,
                    breakdown.rankFor(RankingSort.NET_PER_GAME), breakdown.getTotalPlayers());
            addMetricRow(container, R.string.player_ranking_sort_win_rate,
                    breakdown.rankFor(RankingSort.WIN_RATE), breakdown.getTotalPlayers());
            addMetricRow(container, R.string.player_ranking_sort_games,
                    breakdown.rankFor(RankingSort.GAMES), breakdown.getTotalPlayers());
            addMetricRow(container, R.string.player_ranking_detail_players_ahead,
                    String.valueOf(breakdown.playersAhead()), false);
            addMetricRow(container, R.string.player_ranking_detail_players_behind,
                    String.valueOf(breakdown.playersBehind()), false);
        }

        private void bindPointRows(PlayerRankingDetail.PeriodBreakdown breakdown) {
            MaterialCardView card = dialog.findViewById(R.id.card_ranking_detail_points);
            LinearLayout container = dialog.findViewById(R.id.layout_ranking_detail_point_rows);
            container.removeAllViews();

            if (!detail.isShowAmounts()) {
                card.setVisibility(View.GONE);
                return;
            }
            card.setVisibility(View.VISIBLE);

            addAmountRow(container, R.string.player_ranking_detail_net_total,
                    breakdown.getBucket().getFinalGamePoints());
            addAmountRow(container, R.string.player_ranking_detail_base_points,
                    breakdown.getBucket().getBaseGamePoints());
            addAmountRow(container, R.string.player_ranking_detail_board_adjustment,
                    breakdown.getBucket().getBoardAdjustmentPoints());
            addAmountRow(container, R.string.player_ranking_detail_avg_per_game, breakdown.netPerGame());
            addAmountRow(container, R.string.player_ranking_detail_avg_base_per_game, breakdown.basePerGame());
            addAmountRow(container, R.string.player_ranking_detail_avg_board_per_game, breakdown.boardPerGame());
            addMetricRow(container, R.string.player_ranking_detail_board_share_of_base,
                    context.getString(
                            R.string.player_ranking_detail_share_value,
                            breakdown.boardShareOfBasePercent()),
                    false);
        }

        private void bindComparison(PlayerRankingDetail.PeriodBreakdown breakdown) {
            MaterialCardView card = dialog.findViewById(R.id.card_ranking_detail_comparison);
            LinearLayout container = dialog.findViewById(R.id.layout_ranking_detail_comparison_rows);
            View shareSection = dialog.findViewById(R.id.layout_ranking_detail_share);
            container.removeAllViews();

            if (!detail.isShowAmounts()) {
                card.setVisibility(View.GONE);
                return;
            }
            card.setVisibility(View.VISIBLE);
            shareSection.setVisibility(View.VISIBLE);

            addAmountRow(container, R.string.player_ranking_detail_leader_net, breakdown.getLeaderNet());
            addAmountRow(container, R.string.player_ranking_detail_gap_to_leader, breakdown.gapToLeader());
            addAmountRow(container, R.string.player_ranking_detail_period_average, breakdown.getPeriodAverageNet());
            addAmountRow(container, R.string.player_ranking_detail_vs_average, breakdown.gapToAverage());
            addMetricRow(container, R.string.player_ranking_detail_ranked_pool,
                    String.valueOf(breakdown.getTotalPlayers()), false);

            TextView shareValue = dialog.findViewById(R.id.text_ranking_detail_share);
            View fill = dialog.findViewById(R.id.ranking_detail_magnitude_fill);
            View rest = dialog.findViewById(R.id.ranking_detail_magnitude_rest);

            shareValue.setText(context.getString(
                    R.string.player_ranking_detail_share_value,
                    breakdown.shareOfLeaderPercent()));

            double net = breakdown.getBucket().getFinalGamePoints();
            fill.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(context, amountTint(net))));

            LinearLayout.LayoutParams fillParams = (LinearLayout.LayoutParams) fill.getLayoutParams();
            LinearLayout.LayoutParams restParams = (LinearLayout.LayoutParams) rest.getLayoutParams();
            float share = breakdown.shareOfLeaderFraction();
            fillParams.weight = share;
            restParams.weight = 1f - share;
            fill.setLayoutParams(fillParams);
            rest.setLayoutParams(restParams);
        }

        private void addMetricRow(
                LinearLayout container,
                @StringRes int labelRes,
                int rank,
                int totalPlayers) {
            if (rank <= 0) {
                addMetricRow(container, labelRes,
                        context.getString(R.string.player_ranking_detail_unranked), false);
                return;
            }
            addMetricRow(container, labelRes,
                    context.getString(R.string.player_ranking_detail_rank_of, rank, totalPlayers),
                    true);
        }

        private void addMetricRow(
                LinearLayout container,
                @StringRes int labelRes,
                String value,
                boolean highlight) {
            View row = LayoutInflater.from(context)
                    .inflate(R.layout.item_player_ranking_detail_metric_row, container, false);
            TextView label = row.findViewById(R.id.text_ranking_metric_label);
            TextView valueView = row.findViewById(R.id.text_ranking_metric_value);
            label.setText(labelRes);
            valueView.setText(value);
            if (highlight) {
                valueView.setTextColor(ContextCompat.getColor(context, R.color.view_violet_light));
            }
            container.addView(row);
        }

        private void addAmountRow(LinearLayout container, @StringRes int labelRes, double amount) {
            View row = LayoutInflater.from(context)
                    .inflate(R.layout.item_player_ranking_detail_metric_row, container, false);
            TextView label = row.findViewById(R.id.text_ranking_metric_label);
            TextView valueView = row.findViewById(R.id.text_ranking_metric_value);
            label.setText(labelRes);
            styleAmountText(valueView, amount);
            container.addView(row);
        }

        private void styleAmountText(TextView view, double amount) {
            view.setText(LeaderboardAmountFormatter.formatSigned(amount));
            view.setTextColor(ContextCompat.getColor(context, amountTint(amount)));
        }

        private void bindRankBadge(TextView badge, int rank) {
            if (rank <= 0) {
                badge.setText("—");
                int muted = ContextCompat.getColor(context, R.color.view_text_secondary);
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(ColorUtils.setAlphaComponent(muted, BADGE_FILL_ALPHA));
                drawable.setStroke(dp(1f), ColorUtils.setAlphaComponent(muted, BADGE_RING_ALPHA));
                badge.setBackground(drawable);
                badge.setTextColor(muted);
                return;
            }
            badge.setText(String.valueOf(rank));
            int medal = medalColorFor(rank);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            if (medal != 0) {
                drawable.setColor(medal);
                drawable.setStroke(dp(1.5f), ColorUtils.blendARGB(medal, Color.WHITE, 0.45f));
                badge.setTextColor(ContextCompat.getColor(context, R.color.text_dark));
            } else {
                int muted = ContextCompat.getColor(context, R.color.view_text_secondary);
                drawable.setColor(ColorUtils.setAlphaComponent(muted, BADGE_FILL_ALPHA));
                drawable.setStroke(dp(1f), ColorUtils.setAlphaComponent(muted, BADGE_RING_ALPHA));
                badge.setTextColor(muted);
            }
            badge.setBackground(drawable);
        }

        @ColorInt
        private int medalColorFor(int rank) {
            switch (rank) {
                case 1:
                    return ContextCompat.getColor(context, R.color.view_gold);
                case 2:
                    return ContextCompat.getColor(context, R.color.ranking_medal_silver);
                case 3:
                    return ContextCompat.getColor(context, R.color.ranking_medal_bronze);
                default:
                    return 0;
            }
        }

        private int accentColorFor(int rank, int totalPlayers) {
            if (rank == 1) {
                return R.color.leaderboard_slice_win_1;
            }
            if (rank == 2) {
                return R.color.leaderboard_slice_win_2;
            }
            if (rank == totalPlayers && totalPlayers > 0) {
                return R.color.leaderboard_slice_loss_2;
            }
            if (rank == totalPlayers - 1 && totalPlayers > 1) {
                return R.color.leaderboard_slice_loss_1;
            }
            return R.color.view_violet_light;
        }

        private int amountTint(double amount) {
            if (Math.abs(amount) < 0.5) {
                return R.color.view_text_secondary;
            }
            return amount > 0 ? R.color.view_mint : R.color.view_coral;
        }

        private int winRateTint(int winRatePercent) {
            if (winRatePercent >= 50) {
                return R.color.view_mint;
            }
            if (winRatePercent > 0) {
                return R.color.warning_orange;
            }
            return R.color.view_coral;
        }

        private int dp(float value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
