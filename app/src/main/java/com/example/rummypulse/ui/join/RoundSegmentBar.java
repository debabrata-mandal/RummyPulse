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
 * <p>Colour carries where the round sits in the game: the opening rounds are blue, the middle
 * warms to yellow, and the closing rounds run to red. Brightness carries progress on top of that -
 * solid for a round already played, dimmer for the round in play, faint for rounds still to come -
 * so the whole ramp stays visible from the first round and simply lights up as the game runs.</p>
 */
public class RoundSegmentBar extends View {

    private static final int DEFAULT_TOTAL_ROUNDS = 10;
    /** Alpha for the round in play, and for rounds not yet reached. */
    private static final int ACTIVE_ALPHA = 0x99;
    private static final int UPCOMING_ALPHA = 0x59;
    /**
     * Where the ramp turns. The opening 40% holds blue, then it blends to yellow by 70% and to
     * red at the last round.
     */
    private static final float BLUE_HOLD = 0.4f;
    private static final float YELLOW_STOP = 0.7f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF segment = new RectF();

    private int totalRounds = DEFAULT_TOTAL_ROUNDS;
    private int completedRounds;
    /** 1-based round in play, or 0 when none is. */
    private int activeRound;

    private int startColor;
    private int midColor;
    private int endColor;
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
        midColor = androidx.core.content.ContextCompat.getColor(
                context, R.color.view_gold);
        endColor = androidx.core.content.ContextCompat.getColor(
                context, R.color.view_coral);
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
        int hue = hueFor(index);
        if (index < completedRounds) {
            return hue;
        }
        return ColorUtils.setAlphaComponent(
                hue,
                activeRound > 0 && index == activeRound - 1 ? ACTIVE_ALPHA : UPCOMING_ALPHA);
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
