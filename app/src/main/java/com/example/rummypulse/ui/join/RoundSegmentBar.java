package com.example.rummypulse.ui.join;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.rummypulse.R;
import androidx.core.graphics.ColorUtils;

/**
 * Draws one rounded segment per round instead of a single continuous bar. Rounds are discrete, so
 * a player can count how many are left at a glance rather than estimating a fraction.
 *
 * <p>Segments read as solid for a finished round, half-lit for the round being played, and faint
 * for rounds still to come.</p>
 */
public class RoundSegmentBar extends View {

    private static final int DEFAULT_TOTAL_ROUNDS = 10;
    /** Alpha applied to the accent for the round currently in play. */
    private static final int ACTIVE_ALPHA = 0x66;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF segment = new RectF();

    private int totalRounds = DEFAULT_TOTAL_ROUNDS;
    private int completedRounds;
    /** 1-based round in play, or 0 when none is. */
    private int activeRound;

    private int doneColor;
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
        doneColor = androidx.core.content.ContextCompat.getColor(
                context, R.color.view_violet_light);
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

    /** Overrides the finished-segment colour, so a completed game can read as green. */
    public void setDoneColor(int color) {
        if (doneColor == color) {
            return;
        }
        doneColor = color;
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
            paint.setColor(colorFor(index));
            canvas.drawRoundRect(segment, radius, radius, paint);
        }
    }

    private int colorFor(int index) {
        if (index < completedRounds) {
            return doneColor;
        }
        if (activeRound > 0 && index == activeRound - 1) {
            return ColorUtils.setAlphaComponent(doneColor, ACTIVE_ALPHA);
        }
        return trackColor;
    }
}
