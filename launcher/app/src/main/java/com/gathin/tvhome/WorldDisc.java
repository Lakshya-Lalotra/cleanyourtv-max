package com.gathin.tvhome;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * fun_wheel opening into a world: the dial's disc grows in the world's own colour until it floods the screen, with the
 * world's name fading as it goes. That screen starts on the same solid colour, so the two meet with no seam, and when it
 * closes all of this runs backwards.
 */
final class WorldDisc extends View {
    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String name = "";
    private float centerX, startRadius, startLeft, startSize;
    /** 0 is the dial as it is, 1 the whole screen. */
    private float progress;
    private ValueAnimator running;

    WorldDisc(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.2f * density);
        label.setColor(0xFFFFFFFF);
        try {
            Typeface face = UiFont.get(context, R.string.font_regular);
            if (face != null) label.setTypeface(face);
        } catch (Exception ignored) {
            // the system font is fine
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The world's colour, and where the name and the disc start from, in pixels, as FunDial draws them. */
    void setStart(String worldName, int color, float dialCenterX, float dialRadius, float nameLeft, float nameSize) {
        name = worldName;
        fill.setColor(color);
        centerX = dialCenterX;
        startRadius = dialRadius;
        startLeft = nameLeft;
        startSize = nameSize;
    }

    void showFull() {
        if (running != null) running.cancel();
        progress = 1f;
        invalidate();
    }

    void grow(long ms, Runnable done) {
        animateTo(1f, ms, done);
    }

    void shrink(long ms, Runnable done) {
        animateTo(0f, ms, done);
    }

    void stop() {
        if (running != null) running.cancel();
        progress = 0f;
    }

    private void animateTo(float to, long ms, Runnable done) {
        if (running != null) running.cancel();
        running = ValueAnimator.ofFloat(progress, to);
        running.setDuration(ms);
        running.setInterpolator(new DecelerateInterpolator(1.5f));
        running.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        running.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!cancelled && done != null) done.run();
            }
        });
        running.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cy = getHeight() / 2f;
        float fullRadius = (float) Math.hypot(centerX, cy) + 2f;
        float radius = startRadius + (fullRadius - startRadius) * progress;
        fill.setAlpha(Math.round(255f * Math.min(1f, progress * 6f)));
        canvas.drawCircle(centerX, cy, radius, fill);
        rim.setColor(0xFFFFFFFF);
        rim.setAlpha(Math.round(71f * (1f - progress)));
        canvas.drawCircle(centerX, cy, radius, rim);

        float nameAlpha = 1f - Math.min(1f, progress * 2.2f);
        if (nameAlpha > 0f) {
            label.setAlpha(Math.round(255f * nameAlpha));
            label.setTextSize(startSize);
            canvas.drawText(name, startLeft, cy + startSize * 0.34f, label);
        }
    }
}
