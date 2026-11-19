package com.gathin.tvhome;

import android.animation.TimeAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;

/**
 * How Middle-earth opens: the name in gold on black, so close at first that it fills the screen, drawing slowly back over
 * five seconds until it stands whole; then Bag End comes up behind it. Drawn in dp on a 960 x 540 screen.
 */
final class MiddleEarthGateView extends View {
    interface Listener {
        /** The opening has begun: the music belongs with it. */
        void onMusicCue();

        /** The opening is over: on to Bag End. */
        void onGateDone();
    }

    private static final float SCREEN_W = 960f, SCREEN_H = 540f;
    private static final float ZOOM_MS = 5000f, HOLD_MS = 700f, FIRST_ZOOM = 16f, LOGO_W = 400f;

    private final float density;
    private final Listener listener;
    private final Paint art = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final TimeAnimator clock = new TimeAnimator();
    private Bitmap logo;
    private float time;
    private boolean done, cued;

    MiddleEarthGateView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = context.getAssets().open("middleearth/logo.webp")) {
            logo = BitmapFactory.decodeStream(in, null, options);
        } catch (IOException | OutOfMemoryError e) {
            logo = null;   // straight on to Bag End
        }
        clock.setTimeListener((animation, total, delta) -> {
            time += Math.min(50f, delta);
            if (!cued) {
                cued = true;
                this.listener.onMusicCue();
            }
            if (!done && time >= ZOOM_MS + HOLD_MS) {
                done = true;
                this.listener.onGateDone();
            }
            invalidate();
        });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void start() {
        if (!clock.isStarted()) clock.start();
    }

    /** Stops, and lets the logo go: the opening is only seen once a visit. */
    void finish() {
        clock.cancel();
        logo = null;
    }

    /** Any key during the opening: the name whole at once, and soon after Bag End. */
    void skip() {
        time = Math.max(time, ZOOM_MS);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF000000);
        if (logo == null) return;
        canvas.save();
        canvas.scale(density, density);
        // A steady pull back that only eases as it arrives, even on a ratio scale so it never seems to speed up.
        float left = 1f - Math.min(1f, time / ZOOM_MS), zoom = 1f - (float) Math.pow(left, 1.5);
        float scale = (float) Math.exp(Math.log(FIRST_ZOOM) * (1f - zoom));
        float width = LOGO_W * scale, height = width * logo.getHeight() / logo.getWidth();
        rect.set(SCREEN_W / 2f - width / 2f, SCREEN_H / 2f - height / 2f, SCREEN_W / 2f + width / 2f, SCREEN_H / 2f + height / 2f);
        art.setAlpha(Math.round(255f * Math.min(1f, time / 600f)));
        canvas.drawBitmap(logo, null, rect, art);
        canvas.restore();
    }
}
