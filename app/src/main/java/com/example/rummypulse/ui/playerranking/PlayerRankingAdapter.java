package com.example.rummypulse.ui.playerranking;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rummypulse.R;
import com.example.rummypulse.ui.dashboard.LeaderboardAmountFormatter;
import com.example.rummypulse.ui.dashboard.LeaderboardEntry;
import com.example.rummypulse.ui.dashboard.RankingSort;
import com.example.rummypulse.utils.DisplayNameUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the full ranking as two-line cards.
 *
 * <p>The four extremes keep the dashboard donut's slice colours so a player recognises the ring
 * they tapped, and each row carries a bar showing its share of the largest net in the period.
 */
public class PlayerRankingAdapter extends RecyclerView.Adapter<PlayerRankingAdapter.RankingViewHolder> {

    /** Fill alpha for the neutral games chip. */
    private static final int STAT_CHIP_NEUTRAL_ALPHA = 0x24;

    /** Fill alpha for the win-rate chip, which carries a directional tint. */
    private static final int STAT_CHIP_TINT_ALPHA = 0x33;

    /** Alpha applied to an accent colour when it fills a pill behind text. */
    private static final int PILL_ALPHA = 0x2B;

    /** Fill and rim alphas for the outlined badge worn by everyone below third place. */
    private static final int BADGE_FILL_ALPHA = 0x1F;
    private static final int BADGE_RING_ALPHA = 0x66;

    private final List<LeaderboardEntry> entries = new ArrayList<>();
    private double maxAbsoluteNet;
    private boolean showAmounts = true;
    private RankingSort sort = RankingSort.NET_TOTAL;

    @SuppressLint("NotifyDataSetChanged")
    public void setEntries(List<LeaderboardEntry> newEntries) {
        entries.clear();
        if (newEntries != null) {
            entries.addAll(newEntries);
        }
        maxAbsoluteNet = 0;
        for (LeaderboardEntry entry : entries) {
            maxAbsoluteNet = Math.max(maxAbsoluteNet, Math.abs(entry.getNetAmount()));
        }
        notifyDataSetChanged();
    }

    /** Masks every net figure when the game defaults withhold amounts. */
    @SuppressLint("NotifyDataSetChanged")
    public void setShowAmounts(boolean show) {
        if (showAmounts != show) {
            showAmounts = show;
            notifyDataSetChanged();
        }
    }

    /**
     * The ordering the list is currently in, so each row can spell out the figure it was ranked on.
     * Games and win rate are always on the row; the per-game average is added only when it is what
     * the ordering is based on, which keeps the line short the rest of the time.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setSort(RankingSort newSort) {
        if (newSort != null && sort != newSort) {
            sort = newSort;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public RankingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_player_ranking, parent, false);
        return new RankingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RankingViewHolder holder, int position) {
        LeaderboardEntry entry = entries.get(position);
        Context context = holder.itemView.getContext();

        holder.itemView.setBackgroundResource(entry.isCurrentUser()
                ? R.drawable.bg_leaderboard_row_self
                : R.drawable.bg_leaderboard_row);

        bindRankBadge(holder, context, entry);
        bindIdentity(holder, context, entry);
        bindNet(holder, context, entry);
    }

    private void bindRankBadge(RankingViewHolder holder, Context context, LeaderboardEntry entry) {
        int rank = entry.getRank();
        int medal = medalColorFor(context, rank);
        holder.position.setText(String.valueOf(rank));

        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);

        if (medal != 0) {
            // A struck medal: solid metal with a brighter rim, dark digits punched out of it.
            badge.setColor(medal);
            badge.setStroke(dp(context, 1.5f), ColorUtils.blendARGB(medal, Color.WHITE, 0.45f));
            holder.position.setTextColor(ContextCompat.getColor(context, R.color.text_dark));
        } else {
            // Off the podium the badge is only an outline, so the medals stay the thing you see.
            int muted = ContextCompat.getColor(context, R.color.view_text_secondary);
            badge.setColor(ColorUtils.setAlphaComponent(muted, BADGE_FILL_ALPHA));
            badge.setStroke(dp(context, 1f), ColorUtils.setAlphaComponent(muted, BADGE_RING_ALPHA));
            holder.position.setTextColor(muted);
        }
        holder.position.setBackground(badge);
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void bindIdentity(RankingViewHolder holder, Context context, LeaderboardEntry entry) {
        int accent = ContextCompat.getColor(context, accentColorFor(entry.getRank()));
        holder.avatar.setText(DisplayNameUtils.initials(entry.getDisplayName()));
        holder.avatar.setBackgroundTintList(ColorStateList.valueOf(accent));

        holder.name.setText(entry.isCurrentUser()
                ? context.getString(R.string.player_ranking_name_you, entry.getDisplayName())
                : entry.getDisplayName());

        bindStatChips(holder, context, entry);
    }

    private void bindStatChips(RankingViewHolder holder, Context context, LeaderboardEntry entry) {
        long games = entry.getGames();
        int winRate = winRatePercent(entry);
        int muted = ContextCompat.getColor(context, R.color.view_text_secondary);
        int primary = ContextCompat.getColor(context, R.color.text_primary);

        String gamesValue = String.valueOf(games);
        String gamesLabel = ' ' + context.getString(R.string.player_ranking_stat_games_label);
        bindStatChip(
                holder.statGames,
                gamesValue,
                gamesLabel,
                primary,
                muted,
                ColorUtils.setAlphaComponent(muted, STAT_CHIP_NEUTRAL_ALPHA));

        String winsValue = context.getString(R.string.player_ranking_stat_win_rate, winRate);
        String winsLabel = "";
        int winTint = ContextCompat.getColor(context, winRateTintFor(entry));
        bindStatChip(
                holder.statWins,
                winsValue,
                winsLabel,
                winTint,
                muted,
                ColorUtils.setAlphaComponent(winTint, STAT_CHIP_TINT_ALPHA));

        if (sort == RankingSort.NET_PER_GAME && showAmounts) {
            holder.statAvg.setVisibility(View.VISIBLE);
            String avgValue = LeaderboardAmountFormatter.formatSigned(netPerGame(entry));
            String avgLabel = context.getString(R.string.player_ranking_stat_per_game);
            int avgTint = ContextCompat.getColor(context, Math.abs(entry.getNetAmount()) < 0.5
                    ? R.color.view_text_secondary
                    : entry.getNetAmount() > 0 ? R.color.view_mint : R.color.view_coral);
            bindStatChip(
                    holder.statAvg,
                    avgValue,
                    avgLabel,
                    avgTint,
                    muted,
                    ColorUtils.setAlphaComponent(avgTint, STAT_CHIP_TINT_ALPHA));
        } else {
            holder.statAvg.setVisibility(View.GONE);
        }
    }

    private static void bindStatChip(
            TextView view,
            String value,
            String label,
            @ColorInt int valueColor,
            @ColorInt int labelColor,
            @ColorInt int backgroundTint) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        int valueStart = text.length();
        text.append(value);
        int valueEnd = text.length();
        text.setSpan(new StyleSpan(Typeface.BOLD), valueStart, valueEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(valueColor), valueStart, valueEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(1.05f), valueStart, valueEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int labelStart = text.length();
        text.append(label);
        text.setSpan(
                new ForegroundColorSpan(labelColor),
                labelStart,
                text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
                new RelativeSizeSpan(0.92f),
                labelStart,
                text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        view.setText(text);
        view.setBackgroundTintList(ColorStateList.valueOf(backgroundTint));
    }

    private static int winRateTintFor(LeaderboardEntry entry) {
        if (entry.getGames() <= 0) {
            return R.color.view_text_secondary;
        }
        int winRate = winRatePercent(entry);
        if (winRate >= 50) {
            return R.color.view_mint;
        }
        if (winRate > 0) {
            return R.color.warning_orange;
        }
        return R.color.view_coral;
    }

    private static double netPerGame(LeaderboardEntry entry) {
        return entry.getGames() <= 0 ? 0 : entry.getNetAmount() / entry.getGames();
    }

    private void bindNet(RankingViewHolder holder, Context context, LeaderboardEntry entry) {
        if (!showAmounts) {
            int muted = ContextCompat.getColor(context, R.color.view_text_secondary);
            holder.net.setText(R.string.game_view_amount_hidden);
            holder.net.setTextColor(muted);
            holder.net.setBackgroundTintList(
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(muted, PILL_ALPHA)));
            holder.magnitudeTrack.setVisibility(View.GONE);
            return;
        }

        double net = entry.getNetAmount();
        int direction = ContextCompat.getColor(context, Math.abs(net) < 0.5
                ? R.color.view_text_secondary
                : net > 0 ? R.color.view_mint : R.color.view_coral);

        holder.net.setText(LeaderboardAmountFormatter.formatSigned(net));
        holder.net.setTextColor(direction);
        holder.net.setBackgroundTintList(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(direction, PILL_ALPHA)));

        holder.magnitudeTrack.setVisibility(View.VISIBLE);
        holder.magnitudeFill.setBackgroundTintList(ColorStateList.valueOf(direction));
        setMagnitude(holder, shareOfLargest(net));
    }

    /** Splits the track between the filled bar and the remainder using layout weights. */
    private void setMagnitude(RankingViewHolder holder, float share) {
        LinearLayout.LayoutParams fill =
                (LinearLayout.LayoutParams) holder.magnitudeFill.getLayoutParams();
        LinearLayout.LayoutParams rest =
                (LinearLayout.LayoutParams) holder.magnitudeRest.getLayoutParams();
        fill.weight = share;
        rest.weight = 1f - share;
        holder.magnitudeFill.setLayoutParams(fill);
        holder.magnitudeRest.setLayoutParams(rest);
    }

    /**
     * A floor of 4 per cent keeps near-zero results visible as a tick rather than nothing at all,
     * which would read as a rendering fault.
     */
    private float shareOfLargest(double net) {
        if (maxAbsoluteNet < 0.5) {
            return 0.04f;
        }
        return (float) Math.max(0.04, Math.abs(net) / maxAbsoluteNet);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    /**
     * Gold, silver and bronze for the podium; 0 means this rank gets no medal.
     *
     * <p>Keyed on the earned rank, not the row's position, because the current user is pinned to
     * the top of the list without that promoting them onto the podium.
     */
    @ColorInt
    private int medalColorFor(Context context, int rank) {
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

    /**
     * The two best and two worst ranks take the donut's slice colours. Everyone in between stays
     * neutral rather than inventing a gradient.
     */
    private int accentColorFor(int rank) {
        int lastRank = entries.size();
        if (rank == 1) {
            return R.color.leaderboard_slice_win_1;
        }
        if (rank == 2) {
            return R.color.leaderboard_slice_win_2;
        }
        if (rank == lastRank) {
            return R.color.leaderboard_slice_loss_2;
        }
        if (rank == lastRank - 1) {
            return R.color.leaderboard_slice_loss_1;
        }
        return R.color.view_violet_light;
    }

    private static int winRatePercent(LeaderboardEntry entry) {
        if (entry.getGames() <= 0) {
            return 0;
        }
        return (int) Math.round((entry.getWins() * 100.0) / entry.getGames());
    }

    static class RankingViewHolder extends RecyclerView.ViewHolder {

        final TextView position;
        final TextView avatar;
        final TextView name;
        final TextView statGames;
        final TextView statWins;
        final TextView statAvg;
        final TextView net;
        final View magnitudeTrack;
        final View magnitudeFill;
        final View magnitudeRest;

        RankingViewHolder(@NonNull View itemView) {
            super(itemView);
            position = itemView.findViewById(R.id.ranking_position);
            avatar = itemView.findViewById(R.id.ranking_avatar);
            name = itemView.findViewById(R.id.ranking_name);
            statGames = itemView.findViewById(R.id.ranking_stat_games);
            statWins = itemView.findViewById(R.id.ranking_stat_wins);
            statAvg = itemView.findViewById(R.id.ranking_stat_avg);
            net = itemView.findViewById(R.id.ranking_net);
            magnitudeTrack = itemView.findViewById(R.id.ranking_magnitude_track);
            magnitudeFill = itemView.findViewById(R.id.ranking_magnitude_fill);
            magnitudeRest = itemView.findViewById(R.id.ranking_magnitude_rest);
        }
    }
}
