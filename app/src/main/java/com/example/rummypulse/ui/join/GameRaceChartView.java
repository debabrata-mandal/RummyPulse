package com.example.rummypulse.ui.join;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Every player's running total across the rounds of one game, drawn as a line each.
 *
 * <p>The list beside this chart can only ever show where the game stands now. The scores that got
 * it there are already loaded, so the whole race is drawable without asking for anything more:
 * where someone pulled ahead, where someone collapsed, and who is still close enough to catch up.
 *
 * <p><b>The vertical axis is inverted.</b> A low total wins at rummy, so a line that climbs is a
 * player doing well. Drawn the conventional way round the chart would say the opposite of what it
 * means, which is why the panel around it carries a caption saying so.
 *
 * <p>Drawn straight onto a canvas for the same reason {@code LeaderboardDonutView} is: the project
 * has no chart dependency and this is the only shape it needs.
 */
public class GameRaceChartView extends View {

    /** Lines other than the leader's and the viewer's, which would otherwise be a thicket. */
    private static final int MUTED_ALPHA = 0x8C;
    /** Room on the left for the two score rules to be labelled. */
    private static final float SCALE_GUTTER_DP = 24f;
    private static final float LINE_WIDTH_DP = 2f;
    private static final float LEAD_LINE_WIDTH_DP = 2.8f;
    private static final float DOT_RADIUS_DP = 3.4f;
    private static final float LABEL_TEXT_SP = 9f;
    /** Room on the right for the end label, and below for the round numbers. */
    private static final float LABEL_GUTTER_DP = 78f;
    private static final float AXIS_GUTTER_DP = 13f;

    /** One player's run of cumulative totals. */
    public static final class RaceLine {
        final String label;
        final int color;
        final List<Integer> cumulativeTotals;
        final boolean emphasised;

        /**
         * @param cumulativeTotals running total after each played round; an empty list draws
         *     nothing for this player, which is what a game with no scores yet should look like
         * @param emphasised drawn at full strength - the leader, and whoever is looking
         */
        public RaceLine(String label, int color, List<Integer> cumulativeTotals,
                boolean emphasised) {
            this.label = label;
            this.color = color;
            this.cumulativeTotals = new ArrayList<>(cumulativeTotals);
            this.emphasised = emphasised;
        }
    }

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RaceLine> lines = new ArrayList<>();

    private int totalRounds = 10;
    private float density;
    private int gridColor;
    private int gridColorFuture;
    private int labelColor;

    public GameRaceChartView(Context context) {
        super(context);
        init(context);
    }

    public GameRaceChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GameRaceChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        gridColor = androidx.core.content.ContextCompat.getColor(
                context, com.example.rummypulse.R.color.race_grid);
        gridColorFuture = androidx.core.content.ContextCompat.getColor(
                context, com.example.rummypulse.R.color.race_grid_future);
        labelColor = androidx.core.content.ContextCompat.getColor(
                context, com.example.rummypulse.R.color.view_text_secondary);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        gridPaint.setStrokeWidth(Math.max(1f, density));
        labelPaint.setTextSize(LABEL_TEXT_SP * context.getResources()
                .getDisplayMetrics().scaledDensity);
    }

    /**
     * @param totalRounds rounds the game runs to, so unplayed rounds still take up their share of
     *     the width and the line does not stretch to fill the chart early on
     */
    public void setRace(@NonNull List<RaceLine> newLines, int totalRounds) {
        this.totalRounds = Math.max(1, totalRounds);
        lines.clear();
        lines.addAll(newLines);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float labelGutter = LABEL_GUTTER_DP * density;
        float axisGutter = AXIS_GUTTER_DP * density;
        float plotLeft = SCALE_GUTTER_DP * density;
        float plotRight = getWidth() - labelGutter;
        float plotTop = 5f * density;
        float plotBottom = getHeight() - axisGutter;
        if (plotRight <= plotLeft || plotBottom <= plotTop) {
            return;
        }

        int playedRounds = 0;
        int worstTotal = 0;
        for (RaceLine line : lines) {
            playedRounds = Math.max(playedRounds, line.cumulativeTotals.size());
            for (int total : line.cumulativeTotals) {
                worstTotal = Math.max(worstTotal, total);
            }
        }

        // The axis spans the rounds actually played, not all ten. Reserving the full game left the
        // lines crushed into the first third for most of it, and how many rounds remain is already
        // on the header directly above this.
        int spanRounds = Math.max(2, playedRounds);
        float stepX = (plotRight - plotLeft) / (spanRounds - 1);
        for (int round = 0; round < spanRounds; round++) {
            float x = plotLeft + round * stepX;
            gridPaint.setColor(round < playedRounds ? gridColor : gridColorFuture);
            canvas.drawLine(x, plotTop, x, plotBottom, gridPaint);
        }

        // Nobody has scored yet: the gridlines alone say the game has not started.
        if (playedRounds == 0 || worstTotal <= 0) {
            drawRoundLabels(canvas, plotLeft, stepX, getHeight() - 3f * density,
                    playedRounds, spanRounds);
            return;
        }

        drawScoreScale(canvas, plotLeft, plotTop, plotRight, plotBottom, worstTotal);

        // Lines first, labels after: two players on the same total end at the same point, and a
        // label drawn with its line would be written straight over its neighbour's.
        List<float[]> endPoints = new ArrayList<>(lines.size());
        for (RaceLine line : lines) {
            endPoints.add(drawLine(canvas, line, plotLeft, plotTop, plotBottom, stepX, worstTotal));
        }
        drawEndLabels(canvas, endPoints, plotTop, plotBottom);
        drawRoundLabels(canvas, plotLeft, stepX, getHeight() - 3f * density,
                playedRounds, spanRounds);
    }

    /**
     * Two horizontal rules with the running total they stand for. Without them the chart says who
     * is ahead but never by how much, and in a game where one bad hand is worth eighty points the
     * size of a gap is the whole question.
     */
    private void drawScoreScale(Canvas canvas, float plotLeft, float plotTop, float plotRight,
            float plotBottom, int worstTotal) {
        labelPaint.setColor(ColorUtils.setAlphaComponent(labelColor, MUTED_ALPHA));
        gridPaint.setColor(gridColor);
        // Thirds, not halves: a rule at the very bottom puts its label on the round-number row.
        for (int step = 1; step <= 2; step++) {
            float fraction = step / 3f;
            float y = plotTop + fraction * (plotBottom - plotTop);
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint);
            String label = String.valueOf(Math.round(worstTotal * fraction));
            canvas.drawText(
                    label,
                    plotLeft - labelPaint.measureText(label) - 4f * density,
                    y + labelPaint.getTextSize() / 3f,
                    labelPaint);
        }
    }

    /** @return the line's last point as {x, y}, or null when the player has no scores yet */
    @Nullable
    private float[] drawLine(Canvas canvas, RaceLine line, float plotLeft, float plotTop,
            float plotBottom, float stepX, int worstTotal) {
        if (line.cumulativeTotals.isEmpty()) {
            return null;
        }
        int color = line.emphasised ? line.color : ColorUtils.setAlphaComponent(
                line.color, MUTED_ALPHA);
        linePaint.setColor(color);
        linePaint.setStrokeWidth(
                (line.emphasised ? LEAD_LINE_WIDTH_DP : LINE_WIDTH_DP) * density);

        float previousX = 0f;
        float previousY = 0f;
        for (int round = 0; round < line.cumulativeTotals.size(); round++) {
            float x = plotLeft + round * stepX;
            float y = yFor(line.cumulativeTotals.get(round), worstTotal, plotTop, plotBottom);
            if (round > 0) {
                canvas.drawLine(previousX, previousY, x, y, linePaint);
            }
            previousX = x;
            previousY = y;
        }

        linePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(previousX, previousY, DOT_RADIUS_DP * density, linePaint);
        linePaint.setStyle(Paint.Style.STROKE);
        return new float[] {previousX, previousY};
    }

    /**
     * Writes each line's name beside where it ended, nudging labels apart when players are level
     * on points - which happens often enough in a real game that overlapping names would be the
     * normal case rather than the exception.
     */
    private void drawEndLabels(Canvas canvas, List<float[]> endPoints, float plotTop,
            float plotBottom) {
        float minGap = labelPaint.getTextSize() * 1.15f;
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < endPoints.size(); index++) {
            if (endPoints.get(index) != null) {
                order.add(index);
            }
        }
        order.sort((left, right) -> Float.compare(
                endPoints.get(left)[1], endPoints.get(right)[1]));

        // Two passes. Pushing down alone piles every label onto the floor once the lines finish
        // low - which is exactly what level players do - so a second pass walks back up and
        // reclaims the gap. Clamping in one direction only was the bug: it stacked them.
        float[] labelY = new float[order.size()];
        float previous = Float.NEGATIVE_INFINITY;
        for (int slot = 0; slot < order.size(); slot++) {
            labelY[slot] = Math.max(endPoints.get(order.get(slot))[1], previous + minGap);
            previous = labelY[slot];
        }
        previous = plotBottom + minGap;
        for (int slot = order.size() - 1; slot >= 0; slot--) {
            labelY[slot] = Math.max(plotTop, Math.min(labelY[slot], previous - minGap));
            previous = labelY[slot];
        }

        for (int slot = 0; slot < order.size(); slot++) {
            int index = order.get(slot);
            float[] point = endPoints.get(index);
            RaceLine line = lines.get(index);
            float textY = labelY[slot] + labelPaint.getTextSize() / 3f;
            float textX = point[0] + 6f * density;

            labelPaint.setColor(line.emphasised ? line.color : labelColor);
            canvas.drawText(line.label, textX, textY, labelPaint);

            // The running total beside the name, so the chart answers "by how much" without making
            // anyone read it off the scale. Muted, because which line is whose comes first.
            List<Integer> totals = line.cumulativeTotals;
            if (!totals.isEmpty()) {
                String total = String.valueOf(totals.get(totals.size() - 1));
                labelPaint.setColor(ColorUtils.setAlphaComponent(labelColor, MUTED_ALPHA));
                canvas.drawText(
                        total,
                        textX + labelPaint.measureText(line.label) + 4f * density,
                        textY,
                        labelPaint);
            }
        }
    }

    /** Inverted: the lowest total sits at the top, because the lowest total is winning. */
    private float yFor(int total, int worstTotal, float plotTop, float plotBottom) {
        float fraction = Math.min(1f, (float) total / worstTotal);
        return plotTop + fraction * (plotBottom - plotTop);
    }

    private void drawRoundLabels(Canvas canvas, float plotLeft, float stepX, float baseline,
            int playedRounds, int spanRounds) {
        // Label every round while they are few, then thin them out: past about six, the labels
        // touch each other and stop being readable at all.
        int stride = spanRounds <= 6 ? 1 : 2;
        for (int round = 0; round < spanRounds; round++) {
            boolean last = round == spanRounds - 1;
            if (!last && round % stride != 0) {
                continue;
            }
            labelPaint.setColor(round < playedRounds
                    ? labelColor
                    : ColorUtils.setAlphaComponent(labelColor, MUTED_ALPHA));
            String text = "R" + (round + 1);
            float x = plotLeft + round * stepX - labelPaint.measureText(text) / 2f;
            canvas.drawText(text, x, baseline, labelPaint);
        }
    }
}
