package com.gathin.tvhome;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Choose a path, as two comic panels split by a slash. The chosen one takes more of the screen in full colour with its
 * name huge; the other drains to dark red dots until it is picked. Left and Right swap them, and OK blows the chosen
 * panel across the whole screen. Everything is drawn here, in dp on a 960 x 540 screen.
 */
final class MarvelChooserView extends View {
    private static final float W = 960f, H = 540f, SLANT = 34f;
    private static final float SPLIT_LEFT = 590f, SPLIT_RIGHT = 370f;
    private static final String[] TITLES = {"CHRONOLOGICAL", "PHASES"};
    private static final String[] LINES = {"The story as it happened", "In the order it hit cinemas"};

    private final float density;
    /** Null until loaded. */
    private final Bitmap[] art = new Bitmap[2];
    private final Bitmap logo;
    private final String[] starts = new String[2];
    private final Paint panel = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dots = new Paint();
    private final Paint shade = new Paint();
    private final Paint slash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint body = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Rect source = new Rect();
    private final RectF target = new RectF();
    private int focused;
    private float split = SPLIT_LEFT;
    /** 1 when a panel is the chosen one. */
    private final float[] lit = {1f, 0f};
    /** How far the panels have come in from the sides, 0 to 1. */
    private float arrived;
    private boolean logoShown;
    private ValueAnimator moving, arriving;

    MarvelChooserView(Context context, String startsChronological, String startsPhases) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        starts[0] = startsChronological;
        starts[1] = startsPhases;
        // Off the main thread: the screen has been red long enough by now, and the intro covers these until they are here.
        new Thread(() -> {
            Bitmap chronological = MarvelLook.art(context, R.drawable.marvel_panel_chrono);
            Bitmap phases = MarvelLook.art(context, R.drawable.marvel_panel_phases);
            post(() -> {
                art[0] = chronological;
                art[1] = phases;
                invalidate();
            });
        }, "marvel-panels").start();
        logo = MarvelLook.art(context, R.drawable.logo_marvel);
        dots.setShader(MarvelLook.halftone(density, density));
        shade.setShader(new LinearGradient(0f, 210f, 0f, H, new int[]{0x00000000, 0xD8000000, 0xFA000000},
                new float[]{0f, 0.6f, 0.82f}, Shader.TileMode.CLAMP));
        slash.setStyle(Paint.Style.STROKE);
        title.setTypeface(MarvelLook.anton(context));
        title.setColor(0xFFFFFFFF);
        body.setTypeface(MarvelLook.outfit(context, R.string.font_regular));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    int focused() {
        return focused;
    }

    void setFocused(int which) {
        if (which == focused) return;
        focused = which;
        animateSplit(which == 0 ? SPLIT_LEFT : SPLIT_RIGHT, 340, new OvershootInterpolator(1.1f), null);
    }

    private void animateSplit(float to, long ms, android.animation.TimeInterpolator how, Runnable done) {
        if (moving != null) moving.cancel();
        float fromSplit = split, from0 = lit[0], from1 = lit[1];
        float to0 = focused == 0 ? 1f : 0f, to1 = focused == 1 ? 1f : 0f;
        moving = ValueAnimator.ofFloat(0f, 1f);
        moving.setDuration(ms);
        moving.setInterpolator(how);
        moving.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            split = MarvelLook.lerp(fromSplit, to, t);
            float c = MarvelLook.clamp01(t);
            lit[0] = MarvelLook.lerp(from0, to0, c);
            lit[1] = MarvelLook.lerp(from1, to1, c);
            invalidate();
        });
        if (done != null) {
            moving.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (!cancelled) done.run();
                }
            });
        }
        moving.start();
    }

    /** The panels slam in from either side. */
    void reveal() {
        if (arriving != null) arriving.cancel();
        arriving = ValueAnimator.ofFloat(arrived, 1f);
        arriving.setDuration(420);
        arriving.setInterpolator(new DecelerateInterpolator(2f));
        arriving.addUpdateListener(a -> {
            arrived = (float) a.getAnimatedValue();
            invalidate();
        });
        arriving.start();
    }

    /** The panels in place at once, when there was no intro to bring them in. */
    void showArrived() {
        arrived = 1f;
        invalidate();
    }

    /** The intro's logo has landed in the corner: from here this view draws it. */
    void showLogo() {
        logoShown = true;
        invalidate();
    }

    /** The chosen panel blows across the whole screen, then done runs. */
    void pick(Runnable done) {
        if (arriving != null) arriving.end();
        animateSplit(focused == 0 ? W + 90f : -90f, 300, new AccelerateInterpolator(1.6f), done);
    }

    /** Back from the path: the two panels again. */
    void reset() {
        if (moving != null) moving.cancel();
        split = focused == 0 ? SPLIT_LEFT : SPLIT_RIGHT;
        lit[focused] = 1f;
        lit[1 - focused] = 0f;
        arrived = 1f;
        invalidate();
    }

    void stop() {
        if (moving != null) moving.cancel();
        if (arriving != null) arriving.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(MarvelLook.INK);
        canvas.save();
        canvas.scale(density, density);
        float topX = split + SLANT, bottomX = split - SLANT;
        float slide = (1f - arrived) * 620f;

        // Left panel: Cap's face. The picture slides so he stays in the panel when it is the narrow one.
        canvas.save();
        canvas.translate(-slide, 0f);
        clip.reset();
        clip.moveTo(-10f, -10f);
        clip.lineTo(topX, -10f);
        clip.lineTo(bottomX, H + 10f);
        clip.lineTo(-10f, H + 10f);
        clip.close();
        canvas.clipPath(clip);
        float leftX = MarvelLook.lerp(-120f, 0f, lit[0]);
        drawPanel(canvas, 0, leftX, Math.max(960f, topX + 24f - leftX));
        drawWords(canvas, 0, 40f);
        canvas.restore();

        // Right panel: the Iron Man suit, kept in the middle of the panel however wide it is.
        canvas.save();
        canvas.translate(slide, 0f);
        clip.reset();
        clip.moveTo(topX, -10f);
        clip.lineTo(W + 10f, -10f);
        clip.lineTo(W + 10f, H + 10f);
        clip.lineTo(bottomX, H + 10f);
        clip.close();
        canvas.clipPath(clip);
        float rightX = Math.min(MarvelLook.lerp(155f, 30f, lit[1]), bottomX - 30f);
        drawPanel(canvas, 1, rightX, Math.max(1000f, W - rightX + 10f));
        drawWords(canvas, 1, bottomX + 62f);
        canvas.restore();

        // The gutter between them: black under white, as if inked.
        if (arrived > 0.02f) {
            slash.setStrokeWidth(15f);
            slash.setColor(0xFF000000);
            canvas.drawLine(topX + 2f, -10f, bottomX + 2f, H + 10f, slash);
            slash.setStrokeWidth(8f);
            slash.setColor(0xFFFFFFFF);
            canvas.drawLine(topX, -10f, bottomX, H + 10f, slash);
        }

        if (logoShown) {
            source.set(0, 0, logo.getWidth(), logo.getHeight());
            target.set(MarvelIntroView.LOGO_X, MarvelIntroView.LOGO_Y,
                    MarvelIntroView.LOGO_X + MarvelIntroView.LOGO_H * MarvelIntroView.LOGO_RATIO,
                    MarvelIntroView.LOGO_Y + MarvelIntroView.LOGO_H);
            panel.setColorFilter(null);
            canvas.drawBitmap(logo, source, target, panel);
        }
        canvas.restore();
    }

    private void drawPanel(Canvas canvas, int which, float x, float width) {
        float on = lit[which];
        Bitmap bitmap = art[which];
        if (bitmap != null) {
            source.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            target.set(x, 0f, x + width, width * bitmap.getHeight() / bitmap.getWidth());
            panel.setColorFilter(MarvelLook.tone(MarvelLook.lerp(0.1f, 1f, on), MarvelLook.lerp(0.45f, 1f, on), MarvelLook.lerp(0.22f, 0f, on)));
            canvas.drawBitmap(bitmap, source, target, panel);
        }
        dots.setAlpha(Math.round(MarvelLook.lerp(150f, 28f, on)));
        canvas.drawRect(-10f, -10f, W + 10f, H + 10f, dots);
        canvas.drawRect(-10f, 200f, W + 10f, H + 10f, shade);
    }

    private void drawWords(Canvas canvas, int which, float x) {
        float on = lit[which];
        float size = MarvelLook.lerp(44f, 84f, on);
        float baseline = 468f;
        // The way in, as a red caption box like a comic's, only on the chosen panel.
        if (on > 0.02f) {
            String go = "PRESS OK TO ENTER";
            title.setTextSize(15f);
            float wide = title.measureText(go) + 22f;
            float top = baseline - size * 1.02f - 38f;
            fill.setColor(MarvelLook.RED);
            fill.setAlpha(Math.round(255f * on));
            canvas.drawRect(x, top, x + wide, top + 26f, fill);
            title.setColor(0xFFFFFFFF);
            title.setAlpha(Math.round(255f * on));
            canvas.drawText(go, x + 11f, top + 19.5f, title);
        }
        title.setTextSize(size);
        title.setColor(0xFFFFFFFF);
        title.setAlpha(Math.round(MarvelLook.lerp(190f, 255f, on)));
        canvas.drawText(TITLES[which], x, baseline, title);
        body.setTextSize(17f);
        body.setColor(0xFFFFFFFF);
        body.setAlpha(Math.round(MarvelLook.lerp(150f, 240f, on)));
        canvas.drawText(LINES[which], x, baseline + 28f, body);
        body.setTextSize(14f);
        body.setAlpha(Math.round(MarvelLook.lerp(110f, 180f, on)));
        canvas.drawText("Starts with " + starts[which], x, baseline + 49f, body);
    }
}
