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
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * fun_wheel's dial: a big ring reaching past the right edge of the screen that really turns. World names are set like
 * spokes around it and tick marks turn with them past a fixed pointer at nine o'clock, and the selected world's colour
 * spills out across the screen. Closed, it is the same ring waiting off-screen with only a lit crescent showing, which
 * springs out a little now and then to say there is something here. Drawn in dp in one pass with plain lines and radial
 * gradients: no bitmaps and no blur, on a 1 GB TV.
 */
final class FunDial extends View {
    /** Open, the ring's centre sits this far past the screen edge, so the arc runs the full height. */
    private static final float RADIUS = 300f, BEYOND_EDGE = 40f;
    /** How much of the closed ring shows at rest and when it springs out, and how far its ticks turn as it does. */
    private static final float PEEK_REST = 24f, PEEK_OUT = 50f, POP_TURN = 7f;
    private static final long POP_HOLD_MS = 1100, PEEK_OUT_MS = 460, PEEK_BACK_MS = 300;
    /** Degrees between two worlds, and the tick marks between them. */
    private static final float STEP = 13f;
    private static final int TICKS_PER_WORLD = 5;
    private static final float LABEL_INSET = 24f, LABEL_SMALL = 16f, LABEL_BIG = 28f, VISIBLE_DEGREES = 64f;
    /** The extra turn the ring makes as it opens. */
    private static final float OPENING_SPIN = 26f;
    private static final int MAX_TICKS = 256;

    private final float density;
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float[] minorTicks = new float[4 * MAX_TICKS], majorTicks = new float[4 * MAX_TICKS];
    private final Map<Integer, Shader> washShaders = new HashMap<>();
    private List<String> names = new ArrayList<>();
    private int[] colors = new int[0];
    private int selected;
    private float turn;
    private float open;
    private float others = 1f;
    private int washFrom, washTo;
    private float washBlend = 1f;
    private float peek = PEEK_REST;
    /** Out and staying out, because the next press of Right opens it. */
    private boolean inviting;
    private ValueAnimator turning, opening, fadingOthers, washing, peeking;
    private final Runnable settle = () -> peekTo(PEEK_REST);

    FunDial(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        disc.setShader(new RadialGradient(0f, 0f, RADIUS, new int[]{0xF50A0B0E, 0xEB101115, 0xE01A1C22},
                new float[]{0f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        rim.setStyle(Paint.Style.STROKE);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeCap(Paint.Cap.ROUND);
        pointer.setStyle(Paint.Style.STROKE);
        pointer.setStrokeCap(Paint.Cap.ROUND);
        label.setColor(0xFFFFFFFF);
        try {
            Typeface face = UiFont.get(context, R.string.font_regular);
            if (face != null) label.setTypeface(face);
        } catch (Exception ignored) {
            // the system font is fine
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setWorlds(List<String> worldNames, int[] worldColors) {
        names = new ArrayList<>(worldNames);
        colors = worldColors.clone();
        washFrom = washTo = colors[selected];
        invalidate();
    }

    int selected() {
        return selected;
    }

    /** Turns the ring to this world (clamped to the ends). */
    void select(int index, boolean animate) {
        int to = Math.max(0, Math.min(names.size() - 1, index));
        selected = to;
        if (turning != null) turning.cancel();
        if (!animate) {
            turn = to;
            washFrom = washTo = colors[to];
            washBlend = 1f;
            invalidate();
            return;
        }
        turning = ValueAnimator.ofFloat(turn, to);
        turning.setDuration(380);
        turning.setInterpolator(new DecelerateInterpolator(1.8f));
        turning.addUpdateListener(a -> {
            turn = (float) a.getAnimatedValue();
            invalidate();
        });
        turning.start();
        fadeWashTo(colors[to]);
    }

    private void fadeWashTo(int color) {
        if (color == washTo) return;
        if (washing != null) washing.cancel();
        washFrom = washTo;
        washTo = color;
        washBlend = 0f;
        washing = ValueAnimator.ofFloat(0f, 1f);
        washing.setDuration(460);
        washing.addUpdateListener(a -> {
            washBlend = (float) a.getAnimatedValue();
            invalidate();
        });
        washing.start();
    }

    /** Opens (1) or closes (0) the ring, which turns as it grows. */
    void setOpen(boolean on, boolean animate) {
        float to = on ? 1f : 0f;
        if (opening != null) opening.cancel();
        if (!animate) {
            open = to;
            invalidate();
            return;
        }
        opening = ValueAnimator.ofFloat(open, to);
        opening.setDuration(on ? 460 : 240);
        opening.setInterpolator(new DecelerateInterpolator(1.8f));
        opening.addUpdateListener(a -> {
            open = (float) a.getAnimatedValue();
            invalidate();
        });
        opening.start();
    }

    /** The worlds other than the selected one fade as a world opens around it. */
    void showOthers(boolean show, long ms) {
        if (fadingOthers != null) fadingOthers.cancel();
        if (ms <= 0) {
            others = show ? 1f : 0f;
            invalidate();
            return;
        }
        fadingOthers = ValueAnimator.ofFloat(others, show ? 1f : 0f);
        fadingOthers.setDuration(ms);
        fadingOthers.addUpdateListener(a -> {
            others = (float) a.getAnimatedValue();
            invalidate();
        });
        fadingOthers.start();
    }

    // ---- closed: the crescent ----------------------------------------------------------------------

    /**
     * Springs out and back once. With animations off in the TV's settings it stays still: the invitation is only ever
     * motion, and what it invites is still there to see.
     */
    void pop() {
        if (inviting || open > 0f || !ValueAnimator.areAnimatorsEnabled()) return;
        removeCallbacks(settle);
        peekTo(PEEK_OUT);
        postDelayed(settle, POP_HOLD_MS);
    }

    /** Out for as long as the focus is on the app next to it, where one press of Right opens it. */
    void setInviting(boolean on) {
        if (on == inviting) return;
        inviting = on;
        removeCallbacks(settle);
        peekTo(on ? PEEK_OUT : PEEK_REST);
    }

    /** Home is leaving the screen: back at rest at once, with nothing left running. */
    void restNow() {
        inviting = false;
        removeCallbacks(settle);
        if (peeking != null) peeking.cancel();
        peek = PEEK_REST;
        invalidate();
    }

    /** Any new target replaces the one in flight, from wherever the ring is now. */
    private void peekTo(float to) {
        if (peeking != null) peeking.cancel();
        boolean out = to > peek;
        peeking = ValueAnimator.ofFloat(peek, to);
        peeking.setDuration(out ? PEEK_OUT_MS : PEEK_BACK_MS);
        peeking.setInterpolator(out ? new OvershootInterpolator(2.6f) : new DecelerateInterpolator(1.6f));
        peeking.addUpdateListener(a -> {
            peek = (float) a.getAnimatedValue();
            invalidate();
        });
        peeking.start();
    }

    // ---- where things are when open, in pixels, for the disc that grows out of it ------------------

    float openCenterX() {
        return getWidth() + BEYOND_EDGE * density;
    }

    float openRadius() {
        return RADIUS * density;
    }

    float selectedLabelLeft() {
        return openCenterX() - (RADIUS - LABEL_INSET) * density;
    }

    float selectedLabelSize() {
        return LABEL_BIG * density;
    }

    // ---- drawing ----------------------------------------------------------------------------------

    private Shader washShader(int color) {
        Shader shader = washShaders.get(color);
        if (shader == null) {
            int rgb = color & 0xFFFFFF;
            shader = new RadialGradient(0f, 0f, 560f, new int[]{0x73000000 | rgb, 0x29000000 | rgb, rgb},
                    new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP);
            washShaders.put(color, shader);
        }
        return shader;
    }

    private void drawWash(Canvas canvas, int color, float alpha, float x, float y, float size) {
        wash.setShader(washShader(color));
        wash.setAlpha(Math.round(255f * alpha));
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(size, size);
        canvas.drawCircle(0f, 0f, 560f, wash);
        canvas.restore();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() == 0) return;
        float width = getWidth() / density, height = getHeight() / density;
        // Out of 1: how far it has sprung out when closed, which can overshoot in position but not in light.
        float out = Math.max(0f, Math.min(1f, (peek - PEEK_REST) / (PEEK_OUT - PEEK_REST)));
        float closedX = width + RADIUS - peek, openX = width + BEYOND_EDGE;
        float cx = closedX + (openX - closedX) * open, cy = height / 2f, r = RADIUS;
        float shown = Math.max(0f, Math.min(1f, (open - 0.08f) / 0.6f));
        canvas.save();
        canvas.scale(density, density);

        // The colour stays close around the crescent when closed, flares as it springs out, and fills the screen open.
        float closedSize = 0.26f + 0.24f * out, closedLight = 0.55f + 0.45f * out;
        float size = closedSize + (1f - closedSize) * open, light = closedLight + (1f - closedLight) * open;
        if (washBlend < 1f) drawWash(canvas, washFrom, light * (1f - washBlend), cx - r, cy, size);
        drawWash(canvas, washTo, light * washBlend, cx - r, cy, size);
        rim.setStrokeWidth(1f);
        rim.setColor(0xFFFFFFFF);
        rim.setAlpha(Math.round(Math.max(18f + 13f * out, 26f * shown)));
        canvas.drawCircle(cx, cy, r + 26f, rim);
        if (shown > 0f) {
            rim.setAlpha(Math.round(13f * shown));
            canvas.drawCircle(cx, cy, r + 92f, rim);
        }

        canvas.save();
        canvas.translate(cx, cy);
        canvas.drawCircle(0f, 0f, RADIUS, disc);
        canvas.restore();
        rim.setStrokeWidth(1.2f);
        rim.setColor(0x57FFFFFF);
        canvas.drawCircle(cx, cy, r, rim);

        float spin = (1f - open) * (OPENING_SPIN + POP_TURN * out);
        drawTicks(canvas, cx, cy, r, Math.max(0.85f, shown), spin);
        drawPointer(canvas, cx, cy, r, 1f);
        if (shown > 0f) drawNames(canvas, cx, cy, r, shown, spin);
        canvas.restore();
    }

    private void drawTicks(Canvas canvas, float cx, float cy, float r, float shown, float spin) {
        int from = (int) Math.floor((turn - (VISIBLE_DEGREES + spin) / STEP) * TICKS_PER_WORLD);
        int to = (int) Math.ceil((turn + (VISIBLE_DEGREES + spin) / STEP) * TICKS_PER_WORLD);
        int minor = 0, major = 0;
        for (int k = from; k <= to; k++) {
            double angle = Math.toRadians((k / (float) TICKS_PER_WORLD - turn) * STEP + spin);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            boolean world = k % TICKS_PER_WORLD == 0 && k >= 0 && k / TICKS_PER_WORLD < names.size();
            float outer = r + (world ? 19f : 11f), inner = r + 5f;
            float[] out = world ? majorTicks : minorTicks;
            int n = world ? major : minor;
            if (n >= out.length) continue;
            out[n] = cx - inner * cos;
            out[n + 1] = cy + inner * sin;
            out[n + 2] = cx - outer * cos;
            out[n + 3] = cy + outer * sin;
            if (world) major += 4;
            else minor += 4;
        }
        tick.setStrokeWidth(1f);
        tick.setColor(0xFFFFFFFF);
        tick.setAlpha(Math.round(64f * shown));
        canvas.drawLines(minorTicks, 0, minor, tick);
        tick.setStrokeWidth(1.6f);
        tick.setAlpha(Math.round(150f * shown));
        canvas.drawLines(majorTicks, 0, major, tick);
    }

    /** Fixed at nine o'clock while the ring turns under it: an arc of the world's colour on the rim, lit in three widths. */
    private void drawPointer(Canvas canvas, float cx, float cy, float r, float shown) {
        oval.set(cx - r, cy - r, cx + r, cy + r);
        int color = washTo & 0xFFFFFF;
        pointer.setColor(0xFF000000 | color);
        pointer.setStrokeWidth(10f);
        pointer.setAlpha(Math.round(46f * shown));
        canvas.drawArc(oval, 169f, 22f, false, pointer);
        pointer.setStrokeWidth(5f);
        pointer.setAlpha(Math.round(115f * shown));
        canvas.drawArc(oval, 171f, 18f, false, pointer);
        pointer.setStrokeWidth(2.4f);
        pointer.setAlpha(Math.round(255f * shown));
        canvas.drawArc(oval, 172f, 16f, false, pointer);
        pointer.setColor(0xFFFFFFFF);
        pointer.setStrokeWidth(2.2f);
        pointer.setAlpha(Math.round(255f * shown));
        canvas.drawLine(cx - r - 5f, cy, cx - r - 25f, cy, pointer);
    }

    private void drawNames(Canvas canvas, float cx, float cy, float r, float shown, float spin) {
        label.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < names.size(); i++) {
            float offset = i - turn;
            float degrees = offset * STEP + spin;
            if (Math.abs(degrees) > VISIBLE_DEGREES) continue;
            float emphasis = Math.max(0f, 1f - Math.abs(offset));
            float size = LABEL_SMALL + (LABEL_BIG - LABEL_SMALL) * emphasis;
            label.setTextSize(size);
            float alpha = (0.4f + 0.6f * emphasis) * (i == selected ? 1f : others);
            label.setAlpha(Math.round(255f * shown * alpha));
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(-degrees);
            canvas.drawText(names.get(i), -(r - LABEL_INSET), size * 0.34f, label);
            canvas.restore();
        }
    }

}
