package com.example.rummypulse.ui.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Single donut chart covering every ranked player on the dashboard.
 *
 * <p>Each slice is sized by the absolute net amount, so the ring shows who moved the most money
 * regardless of direction; the slice colour carries the sign. Each amount is written along its own
 * arc, in black or white depending on how bright that slice is. Drawn directly on canvas because
 * the project has no chart dependency and only needs this one shape.
 */
public class LeaderboardDonutView extends View {

    /** Gap between slices, in degrees. */
    private static final float GAP_DEGREES = 3f;
    /** Slices start at twelve o'clock rather than three. */
    private static final float START_DEGREES = -90f;
    /** Thick enough for an amount to sit inside the band. */
    private static final float RING_THICKNESS_DP = 28f;
    /** Keeps a near-zero player visible instead of collapsing to a hairline. */
    private static final float MIN_SWEEP_DEGREES = 8f;
    private static final float LABEL_TEXT_SP = 11f;
    /** Slack required around a label before it is drawn on its arc. */
    private static final float LABEL_PADDING_DP = 6f;
    /** Not pure black, so dark labels sit softer against a saturated slice. */
    private static final int DARK_LABEL = 0xFF10131A;

    /** One player's share of the ring. */
    public static final class Slice {
        final float weight;
        final int color;
        @Nullable
        final String label;

        public Slice(float weight, int color, @Nullable String label) {
            this.weight = Math.abs(weight);
            this.color = color;
            this.label = label;
        }
    }

    private final List<Slice> slices = new ArrayList<>();
    private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringBounds = new RectF();
    private final Rect labelBounds = new Rect();
    private float ringThicknessPx;
    private float labelPaddingPx;

    public LeaderboardDonutView(Context context) {
        super(context);
        init();
    }

    public LeaderboardDonutView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LeaderboardDonutView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        ringThicknessPx = RING_THICKNESS_DP * density;
        labelPaddingPx = LABEL_PADDING_DP * density;

        slicePaint.setStyle(Paint.Style.STROKE);
        slicePaint.setStrokeWidth(ringThicknessPx);
        // Butt caps keep each slice inside its own sweep, so the gaps stay even.
        slicePaint.setStrokeCap(Paint.Cap.BUTT);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(ringThicknessPx);
        trackPaint.setColor(0x14FFFFFF);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
        labelPaint.setTextSize(
                LABEL_TEXT_SP * getResources().getDisplayMetrics().scaledDensity);
    }

    public void setSlices(@NonNull List<Slice> newSlices) {
        slices.clear();
        slices.addAll(newSlices);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float available = Math.min(
                getWidth() - getPaddingLeft() - getPaddingRight(),
                getHeight() - getPaddingTop() - getPaddingBottom());
        if (available <= ringThicknessPx) {
            return;
        }
        float diameter = available - ringThicknessPx;
        float radius = diameter / 2f;
        float centerX = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) / 2f;
        float centerY = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
        ringBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        canvas.drawArc(ringBounds, 0f, 360f, false, trackPaint);
        if (slices.isEmpty()) {
            return;
        }

        float total = 0f;
        for (Slice slice : slices) {
            total += slice.weight;
        }

        int count = slices.size();
        float sweepBudget = 360f - GAP_DEGREES * count;
        // Reserve a floor for every slice, then share what is left by weight.
        float shareable = Math.max(0f, sweepBudget - MIN_SWEEP_DEGREES * count);

        float angle = START_DEGREES;
        for (Slice slice : slices) {
            float share = total > 0f ? slice.weight / total : 1f / count;
            float sweep = MIN_SWEEP_DEGREES + shareable * share;
            slicePaint.setColor(slice.color);
            canvas.drawArc(ringBounds, angle, sweep, false, slicePaint);
            drawSliceLabel(canvas, slice, angle + sweep / 2f, sweep, radius, centerX, centerY);
            angle += sweep + GAP_DEGREES;
        }
    }

    /**
     * Writes one amount along the middle of its arc. The canvas is rotated so the text follows the
     * ring, flipping past the halfway point so nothing reads upside down.
     */
    private void drawSliceLabel(
            Canvas canvas,
            Slice slice,
            float midAngle,
            float sweep,
            float radius,
            float centerX,
            float centerY) {
        if (slice.label == null || slice.label.isEmpty()) {
            return;
        }
        labelPaint.getTextBounds(slice.label, 0, slice.label.length(), labelBounds);
        float textWidth = labelBounds.width();
        float textHeight = labelBounds.height();
        if (textHeight + labelPaddingPx > ringThicknessPx) {
            return;
        }
        // Skip rather than overflow into the neighbouring slice.
        float arcLength = (float) (Math.toRadians(sweep) * radius);
        if (textWidth + labelPaddingPx > arcLength) {
            return;
        }

        labelPaint.setColor(textColorFor(slice.color));

        // Rotating by midAngle + 90 brings the slice midpoint to twelve o'clock.
        float rotation = midAngle + 90f;
        boolean flipped = midAngle > 0f && midAngle < 180f;

        canvas.save();
        canvas.rotate(flipped ? rotation + 180f : rotation, centerX, centerY);
        float baselineOffset = textHeight / 2f;
        float y = flipped
                ? centerY + radius + baselineOffset
                : centerY - radius + baselineOffset;
        canvas.drawText(slice.label, centerX, y, labelPaint);
        canvas.restore();
    }

    /**
     * Picks whichever of near-black or white contrasts better with the slice, rather than guessing
     * from a brightness cutoff. Bright greens and ambers land on dark text, deep reds on white.
     */
    private static int textColorFor(int sliceColor) {
        double onDark = ColorUtils.calculateContrast(Color.WHITE, sliceColor);
        double onLight = ColorUtils.calculateContrast(DARK_LABEL, sliceColor);
        return onLight >= onDark ? DARK_LABEL : Color.WHITE;
    }
}
