package com.example.rummypulse.ui.join;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.rummypulse.R;
import androidx.core.graphics.ColorUtils;

/**
 * Draws one rounded segment per round instead of a single continuous bar. Rounds are discrete, so
 * a player can count how many are left at a glance rather than estimating a fraction.
 *
 * <p>Colour means the round has happened. A round not yet reached stays neutral grey, the round in
 * play takes its colour at reduced strength, and a round already played takes it in full, so on and
 * off can never be confused for one another. Where a round sits in the game decides which colour
 * that is - the opening rounds blue, the middle yellow, the closing rounds red - so the bar fills
 * in from the left and warms as the game runs out.</p>
 */
public class RoundSegmentBar extends View {

    private static final int DEFAULT_TOTAL_ROUNDS = 10;
    /** Alpha for the round in play: on, but not yet a round that counts as played. */
    private static final int ACTIVE_ALPHA = 0x99;
    /**
     * Where the ramp turns. The opening 40% holds blue, then it blends to yellow by 70% and to
     * red at the last round.
     */
    private static final float BLUE_HOLD = 0.4f;
    private static final float YELLOW_STOP = 0.7f;
    /** Score that still counts as a clean round, and the score at which the ramp bottoms out. */
    private static final int GOOD_SCORE = 40;
    private static final int WORST_SCORE = 80;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF segment = new RectF();

    private int totalRounds = DEFAULT_TOTAL_ROUNDS;
    private int completedRounds;
    /** 1-based round in play, or 0 when none is. */
    private int activeRound;
    /**
     * Per-round scores for the player the bar is showing, or null to fall back to colouring by
     * where the round sits in the game. Index 0 is round 1; a null entry is a round not yet scored.
     */
    private Integer[] roundScores;

    private int startColor;
    private int goodColor;
    private int midColor;
    private int endColor;
    private int trackColor;
    private float gapPx;

    public RoundSegmentBar(Context context) {
        super(context);
        init(context);
    }

    public RoundSegmentBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RoundSegmentBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        startColor = androidx.core.content.ContextCompat.getColor(
                context, R.color.round_progress_cyan);
        goodColor = androidx.core.content.ContextCompat.getColor(
                context, R.color.view_mint);
        midColor = androidx.core.content.ContextCompat.getColor(
                context, R.color.view_gold);
        endColor = androidx.core.content.ContextCompat.getColor(
                context, R.color.view_coral);
        trackColor = androidx.core.content.ContextCompat.getColor(
                context, R.color.round_segment_track);
        gapPx = 3f * context.getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * @param totalRounds     rounds in the game
     * @param completedRounds rounds already scored
     * @param activeRound     1-based round in play, or 0 when the game is finished
     */
    public void setRounds(int totalRounds, int completedRounds, int activeRound) {
        int safeTotal = Math.max(1, totalRounds);
        int safeCompleted = Math.max(0, Math.min(completedRounds, safeTotal));
        int safeActive = activeRound < 1 || activeRound > safeTotal ? 0 : activeRound;
        if (this.totalRounds == safeTotal
                && this.completedRounds == safeCompleted
                && this.activeRound == safeActive) {
            return;
        }
        this.totalRounds = safeTotal;
        this.completedRounds = safeCompleted;
        this.activeRound = safeActive;
        invalidate();
    }

    /**
     * Colours the bar by how the player actually scored rather than by how far the game has run.
     * Pass null to go back to the positional ramp, which is what a header with no single player in
     * focus wants.
     *
     * @param scores one entry per round, index 0 being round 1; null entries are unscored rounds
     */
    public void setRoundScores(@Nullable Integer[] scores) {
        if (java.util.Arrays.equals(this.roundScores, scores)) {
            return;
        }
        this.roundScores = scores == null ? null : scores.clone();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float height = getHeight();
        float usableWidth = getWidth() - gapPx * (totalRounds - 1);
        if (usableWidth <= 0f || height <= 0f) {
            return;
        }
        float segmentWidth = usableWidth / totalRounds;
        float radius = height / 2f;
        for (int index = 0; index < totalRounds; index++) {
            float left = index * (segmentWidth + gapPx);
            segment.set(left, 0f, left + segmentWidth, height);
            // Each segment runs from the colour of the round before it to its own, so the played
            // part of the bar reads as one gradient instead of ten flat chips.
            int color = colorFor(index);
            int previous = index == 0 ? color : colorFor(index - 1);
            if (color == previous) {
                paint.setShader(null);
                paint.setColor(color);
            } else {
                paint.setShader(new LinearGradient(
                        segment.left, 0f, segment.right, 0f,
                        ColorUtils.blendARGB(previous, color, 0.55f), color,
                        Shader.TileMode.CLAMP));
            }
            canvas.drawRoundRect(segment, radius, radius, paint);
        }
        paint.setShader(null);
    }

    private int colorFor(int index) {
        if (roundScores != null) {
            return scoredColorFor(index);
        }
        if (index < completedRounds) {
            return hueFor(index);
        }
        if (activeRound > 0 && index == activeRound - 1) {
            return ColorUtils.setAlphaComponent(hueFor(index), ACTIVE_ALPHA);
        }
        return trackColor;
    }

    /**
     * Colour for a round when the bar is showing one player's scores. An unscored round stays on
     * the track colour whether or not the game has passed it, because a blank round says nothing
     * about how that player did.
     */
    private int scoredColorFor(int index) {
        Integer score = index < roundScores.length ? roundScores[index] : null;
        if (score == null) {
            return activeRound > 0 && index == activeRound - 1
                    ? ColorUtils.setAlphaComponent(goodColor, ACTIVE_ALPHA)
                    : trackColor;
        }
        return scoreColor(score);
    }

    /**
     * Blends the score into a continuous mint-to-gold-to-coral ramp rather than snapping it to one
     * of three swatches, so two rounds that played out differently do not end up the same colour
     * and the bar as a whole reads as a gradient. The stops line up with the thresholds the round
     * score boxes already use: a clean round is mint, 40 is gold, and 80 or worse is full coral.
     */
    private int scoreColor(int score) {
        int safeScore = Math.max(0, score);
        if (safeScore <= GOOD_SCORE) {
            return ColorUtils.blendARGB(
                    goodColor, midColor, (float) safeScore / GOOD_SCORE);
        }
        float toWorst = Math.min(1f,
                (float) (safeScore - GOOD_SCORE) / (WORST_SCORE - GOOD_SCORE));
        return ColorUtils.blendARGB(midColor, endColor, toWorst);
    }

    /**
     * Which band the round at {@code index} falls in. Kept as three flat bands rather than a
     * continuous blend: blending blue into yellow runs through green, and at the low alpha an
     * unplayed round is drawn with, that green turns to mud and the ramp stops being readable.
     */
    private int hueFor(int index) {
        float position = totalRounds <= 1 ? 0f : (float) index / (totalRounds - 1);
        if (position <= BLUE_HOLD) {
            return startColor;
        }
        return position <= YELLOW_STOP ? midColor : endColor;
    }
}
