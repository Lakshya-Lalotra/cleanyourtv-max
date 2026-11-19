package com.gathin.tvhome;

import android.animation.TimeAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;

/**
 * Hedwig, the Wizarding World's pointer: a snowy owl from two sheets of frames. In the air her wings beat; when she comes
 * down she lands, folds them and looks about. The point she points with is her feet, at (FOOT_X, FOOT_Y) in this view, in dp.
 */
final class HedwigView extends View {
    /** dp to one pixel of the standing frames: she stands about 95 dp tall. */
    private static final float SCALE = 0.62f;
    private static final int IDLE_FRAMES = 31, IDLE_COLS = 16, IDLE_W = 116, IDLE_H = 169;
    private static final float IDLE_FOOT_X = 65.4f, IDLE_FOOT_Y = 163.8f;
    /** The flying sheet is at half size: frames 0 to AIR_LAST are in the air, the rest are the landing. */
    private static final int FLY_FRAMES = 24, FLY_COLS = 6, FLY_W = 220, FLY_H = 203, AIR_LAST = 14;
    private static final float FLY_FOOT_X = 69.4f, FLY_FOOT_Y = 199.3f;
    private static final float FLY_MS = 1000f / 12f, IDLE_MS = 1000f / 6f;
    private static final float FRAME_W = FLY_W * 2f * SCALE, FRAME_FOOT_X = FLY_FOOT_X * 2f * SCALE;
    /** Her feet are in the middle of the view, with room for the widest frame either way round: turned to face left, the
     *  frame reaches as far to the left of her feet as it does to the right the other way. */
    static final float FOOT_X = Math.max(FRAME_FOOT_X, FRAME_W - FRAME_FOOT_X), FOOT_Y = FLY_FOOT_Y * 2f * SCALE;
    /** In the air the frames hold her well above where her feet will land: she is drawn this much lower while she flies,
     *  so the spot she points at is under her and not far below, and it comes off as she lands. */
    private static final float DROP = 70f;
    static final float WIDTH = FOOT_X * 2f, HEIGHT = FLY_H * 2f * SCALE + DROP;
    private static final int FLYING = 0, LANDING = 1, STANDING = 2;

    private final float density;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect from = new Rect();
    private final RectF to = new RectF();
    private final TimeAnimator clock = new TimeAnimator();
    private Bitmap idle, fly;
    private int state = STANDING, frame, step = 1;
    private float sinceFrame;
    private boolean mirrored;

    HedwigView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        idle = load("wizard/owl_idle.webp");
        fly = load("wizard/owl_fly.webp");
        clock.setTimeListener((animation, total, delta) -> tick(Math.min(100f, delta)));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private Bitmap load(String path) {
        try (InputStream in = getContext().getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (IOException | OutOfMemoryError e) {
            return null;   // she is simply not drawn
        }
    }

    void start() {
        if (!clock.isStarted()) clock.start();
    }

    void stop() {
        clock.cancel();
    }

    /** In the air, or coming down to stand where she is. */
    void setFlying(boolean flying) {
        if (flying == (state == FLYING)) return;
        state = flying ? FLYING : LANDING;
        frame = flying ? 0 : AIR_LAST + 1;
        step = 1;
        sinceFrame = 0f;
        invalidate();
    }

    /** Which way she is heading: she is drawn the other way round when it is to the left. */
    void setHeading(float direction) {
        if (direction == 0f || (direction < 0f) == mirrored) return;
        mirrored = direction < 0f;
        invalidate();
    }

    private void tick(float ms) {
        sinceFrame += ms;
        float each = state == STANDING ? IDLE_MS : FLY_MS;
        if (sinceFrame < each) return;
        sinceFrame -= each;
        if (state == FLYING) {
            // Up through the beat and back down it, so the wings never jump.
            if (frame + step > AIR_LAST || frame + step < 0) step = -step;
            frame += step;
        } else if (state == LANDING) {
            if (++frame >= FLY_FRAMES) {
                state = STANDING;
                frame = 0;
            }
        } else {
            frame = (frame + 1) % IDLE_FRAMES;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean standing = state == STANDING;
        Bitmap sheet = standing ? idle : fly;
        if (sheet == null) return;
        canvas.save();
        canvas.scale(density, density);
        if (mirrored) canvas.scale(-1f, 1f, FOOT_X, FOOT_Y);
        if (standing) {
            int x = (frame % IDLE_COLS) * IDLE_W, y = (frame / IDLE_COLS) * IDLE_H;
            from.set(x, y, x + IDLE_W, y + IDLE_H);
            float left = FOOT_X - IDLE_FOOT_X * SCALE, top = FOOT_Y - IDLE_FOOT_Y * SCALE;
            to.set(left, top, left + IDLE_W * SCALE, top + IDLE_H * SCALE);
        } else {
            int x = (frame % FLY_COLS) * FLY_W, y = (frame / FLY_COLS) * FLY_H;
            from.set(x, y, x + FLY_W, y + FLY_H);
            float lowered = state == FLYING ? DROP : DROP * (FLY_FRAMES - 1 - frame) / (float) (FLY_FRAMES - 2 - AIR_LAST);
            lowered = Math.max(0f, Math.min(DROP, lowered));
            to.set(FOOT_X - FRAME_FOOT_X, lowered, FOOT_X - FRAME_FOOT_X + FRAME_W, lowered + FLY_H * 2f * SCALE);
        }
        canvas.drawBitmap(sheet, from, to, paint);
        canvas.restore();
    }
}
