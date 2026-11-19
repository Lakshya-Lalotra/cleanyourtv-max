package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * The dial of side_wheel and fun_wheel: a half disc on one edge of the screen. Closed, only a small tab of it shows; open, it grows
 * and its options sit around the rim, the selected one level with the middle in a glass pill, the rest turning away
 * above and below it. Drawn in one pass with a handful of paints; no bitmaps, no blur.
 */
final class WheelView extends View {
    /** Up and down the disc between two options, evenly: by angle they would bunch up towards the top and bottom. */
    private static final float LABEL_GAP_DP = 48f;

    private final float density;
    private final float closedRadius, openRadius;
    /** On the right edge of the screen; everything is drawn mirrored. */
    private final boolean onRight;
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private List<String> labels = new ArrayList<>();
    private int selected;
    /** The option level with the middle right now, fractional while turning. */
    private float turn;
    /** 0 closed (the tab), 1 open. */
    private float open;
    private ValueAnimator turning, opening;
    private float shaderRadius = -1f;

    WheelView(Context context, boolean onRight, int openRadiusDp) {
        super(context);
        this.onRight = onRight;
        density = getResources().getDisplayMetrics().density;
        closedRadius = 26 * density;
        openRadius = openRadiusDp * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.2f * density);
        rim.setColor(0x40FFFFFF);
        pill.setColor(0x2EFFFFFF);
        pillRim.setStyle(Paint.Style.STROKE);
        pillRim.setStrokeWidth(1f * density);
        pillRim.setColor(0x66FFFFFF);
        label.setColor(0xFFFFFFFF);
        label.setTextAlign(Paint.Align.CENTER);
        Typeface face = null;
        try {
            face = UiFont.get(context, R.string.font_regular);
        } catch (Exception ignored) {
            // the system font is fine
        }
        if (face != null) label.setTypeface(face);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The width and height the view needs: the open half disc and a little room for its rim. */
    int wantedWidth() {
        return Math.round(openRadius + 8 * density);
    }

    int wantedHeight() {
        return Math.round(2 * openRadius + 16 * density);
    }

    void setLabels(List<String> labels) {
        this.labels = new ArrayList<>(labels);
        invalidate();
    }

    int selected() {
        return selected;
    }

    /** How far from the wheel's edge the glass pill's centre sits, for a pill this wide. A long name sits closer to the edge. */
    static float pillCenterFromEdge(float pillWidth, float radius, float density) {
        return Math.max(pillWidth / 2f + 10 * density, Math.min(radius * 0.56f, radius - pillWidth / 2f - 6 * density));
    }

    /** Turns the wheel to this option (clamped to the ends). */
    void select(int index, boolean animate) {
        int to = Math.max(0, Math.min(labels.size() - 1, index));
        selected = to;
        if (turning != null) turning.cancel();
        if (!animate) {
            turn = to;
            invalidate();
            return;
        }
        turning = ValueAnimator.ofFloat(turn, to);
        turning.setDuration(240);
        turning.setInterpolator(new DecelerateInterpolator(1.6f));
        turning.addUpdateListener(a -> {
            turn = (float) a.getAnimatedValue();
            invalidate();
        });
        turning.start();
    }

    /** Opens (1) or closes (0) the wheel; endAction runs when it gets there. */
    void setOpen(boolean on, boolean animate, Runnable endAction) {
        float to = on ? 1f : 0f;
        if (opening != null) opening.cancel();
        if (!animate) {
            open = to;
            invalidate();
            if (endAction != null) endAction.run();
            return;
        }
        opening = ValueAnimator.ofFloat(open, to);
        opening.setDuration(on ? 300 : 220);
        opening.setInterpolator(new DecelerateInterpolator(1.4f));
        opening.addUpdateListener(a -> {
            open = (float) a.getAnimatedValue();
            invalidate();
        });
        if (endAction != null) {
            opening.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (!cancelled) endAction.run();
                }
            });
        }
        opening.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cy = getHeight() / 2f;
        float r = closedRadius + (openRadius - closedRadius) * open;
        if (Math.abs(r - shaderRadius) > 0.5f) {
            shaderRadius = r;
            disc.setShader(new RadialGradient(mirror(0), cy, r, new int[]{0xF20A0B0E, 0xE6141519, 0xB3141519},
                    new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        }
        canvas.drawCircle(mirror(0), cy, r, disc);
        canvas.drawCircle(mirror(0), cy, r, rim);
        if (open < 0.08f) {
            // Closed: a small chevron in the tab says there is something to pull out.
            label.setTextSize(16 * density);
            label.setAlpha(170);
            canvas.drawText(onRight ? "‹" : "›", mirror(closedRadius * 0.5f), cy + 5.5f * density, label);
            return;
        }
        int fade = Math.round(255 * Math.min(1f, (open - 0.08f) / 0.6f));
        float ring = r * 0.56f;
        // The glass pill behind the selected option, level with the middle.
        if (!labels.isEmpty()) {
            label.setTextSize(21 * density);
            float w = label.measureText(labels.get(selected)) + 34 * density;
            // Inside the disc: a long name sits closer to the edge rather than past the rim.
            float cx = mirror(pillCenterFromEdge(w, r, density));
            rect.set(cx - w / 2f, cy - 21 * density, cx + w / 2f, cy + 21 * density);
            pill.setAlpha(Math.round(0x2E * fade / 255f));
            pillRim.setAlpha(Math.round(0x66 * fade / 255f));
            canvas.drawRoundRect(rect, 21 * density, 21 * density, pill);
            canvas.drawRoundRect(rect, 21 * density, 21 * density, pillRim);
        }
        for (int i = 0; i < labels.size(); i++) {
            float offset = i - turn;
            float down = offset * LABEL_GAP_DP * density;
            if (Math.abs(down) > r - 16 * density) continue;
            float emphasis = Math.max(0f, 1f - Math.abs(offset));
            label.setTextSize((15 + 6 * emphasis) * density);
            label.setAlpha(Math.round(fade * (0.42f + 0.58f * emphasis)));
            String text = labels.get(i);
            float half = label.measureText(text) / 2f;
            // The same rule as the pill's, whose half is the text's and 17dp of padding.
            float curve = (float) Math.sqrt(Math.max(0f, ring * ring - down * down));
            float x = Math.max(half + 10 * density, Math.min(curve, r - half - 23 * density));
            float y = cy + down;
            canvas.drawText(text, mirror(x), y + 7 * density, label);
        }
    }

    /** A distance from the wheel's edge of the screen, as an x in this view. */
    private float mirror(float fromEdge) {
        return onRight ? getWidth() - fromEdge : fromEdge;
    }
}
