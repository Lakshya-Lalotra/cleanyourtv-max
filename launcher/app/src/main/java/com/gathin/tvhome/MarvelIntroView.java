package com.gathin.tvhome;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Entering the Marvel world: pages flip through the heroes behind a big MARVEL block, with the same pages showing through
 * its letters, until the letters flash solid white and the logo flies up to its corner as the chooser comes in underneath.
 * A little under two seconds, and any key skips it. Art is 480 x 270 and 16-bit: about 2 MB, gone when it ends.
 */
final class MarvelIntroView extends View {
    interface Listener {
        /** The logo is leaving: time to bring in what is underneath. */
        void onIntroReveal();

        void onIntroDone();
    }

    private static final int[] FRAMES = {
            R.drawable.marvel_flip_00, R.drawable.marvel_flip_01, R.drawable.marvel_flip_02, R.drawable.marvel_flip_03,
            R.drawable.marvel_flip_04, R.drawable.marvel_flip_05, R.drawable.marvel_flip_06, R.drawable.marvel_flip_07,
            R.drawable.marvel_flip_08, R.drawable.marvel_flip_09, R.drawable.marvel_flip_10, R.drawable.marvel_flip_11,
            R.drawable.marvel_flip_12, R.drawable.marvel_flip_13};
    private static final float FRAME_MS = 85f, FLIP_END = 1190f, SOLID_END = 1500f, END = 1950f;
    private static final float START_H = 300f, HELD_H = 200f, SLAM_H = 218f;
    static final float LOGO_RATIO = 2.48f;
    /** Where the logo lands, in dp: MarvelChooserView draws it in the same place. */
    static final float LOGO_X = 40f, LOGO_Y = 16f, LOGO_H = 30f;

    private final float density;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint page = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint();
    private final Paint letters = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint dots = new Paint();
    private final Rect source = new Rect();
    private final RectF target = new RectF();
    private final RectF logo = new RectF();
    private final Matrix pageMatrix = new Matrix();
    private final ColorFilter pageTone = MarvelLook.tone(0.8f, 0.78f, 0.1f);
    private volatile Bitmap[] pages;
    private volatile Bitmap lettersMask;
    private BitmapShader[] pageShaders;
    private ValueAnimator clock;
    private float time;
    private boolean revealed, finished;

    MarvelIntroView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        dots.setShader(MarvelLook.halftone(density, 1f));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Loads the pages off the main thread, then runs. Until then the disc Home grew stays on screen behind this. */
    void start() {
        Context context = getContext();
        new Thread(() -> {
            Bitmap[] loaded = new Bitmap[FRAMES.length];
            for (int i = 0; i < FRAMES.length; i++) loaded[i] = MarvelLook.art(context, FRAMES[i]);
            Bitmap white = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo_marvel_letters);
            Bitmap mask = white.extractAlpha();
            white.recycle();
            main.post(() -> {
                if (finished) return;
                pages = loaded;
                lettersMask = mask;
                run();
            });
        }, "marvel-intro").start();
    }

    private void run() {
        pageShaders = new BitmapShader[pages.length];
        clock = ValueAnimator.ofFloat(0f, END);
        clock.setDuration((long) END);
        clock.setInterpolator(new LinearInterpolator());
        clock.addUpdateListener(a -> {
            time = (float) a.getAnimatedValue();
            if (!revealed && time >= SOLID_END) {
                revealed = true;
                listener.onIntroReveal();
            }
            invalidate();
        });
        clock.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!cancelled) finish();
            }
        });
        clock.start();
    }

    /** Any key: straight to what comes after. */
    void skip() {
        if (clock != null) clock.cancel();
        if (!revealed) {
            revealed = true;
            listener.onIntroReveal();
        }
        finish();
    }

    void stop() {
        if (clock != null) clock.cancel();
        finished = true;
    }

    private void finish() {
        if (finished) return;
        finished = true;
        pages = null;
        lettersMask = null;
        pageShaders = null;
        setVisibility(GONE);
        listener.onIntroDone();
    }

    private void drawPage(Canvas canvas, Bitmap bitmap, float w, float h) {
        source.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        target.set(0f, 0f, w, h);
        page.setColorFilter(pageTone);
        canvas.drawBitmap(bitmap, source, target, page);
    }

    private Shader pageShader(int index) {
        BitmapShader shader = pageShaders[index];
        if (shader == null) {
            shader = new BitmapShader(pages[index], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            pageShaders[index] = shader;
        }
        return shader;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Bitmap[] all = pages;
        Bitmap mask = lettersMask;
        if (all == null || mask == null) {
            // Still loading: the same red the wheel's disc grew into.
            canvas.drawColor(MarvelLook.RED);
            return;
        }
        float w = getWidth(), h = getHeight();
        int index = Math.min((int) (time / FRAME_MS), (int) (FLIP_END / FRAME_MS)) % all.length;

        if (time < FLIP_END) {
            // Each page wipes in from the right like one being turned, with a white edge leading it.
            float within = (time % FRAME_MS) / FRAME_MS;
            float edge = w * (1f - within);
            drawPage(canvas, all[(index + all.length - 1) % all.length], w, h);
            canvas.save();
            canvas.clipRect(edge, 0f, w, h);
            drawPage(canvas, all[index], w, h);
            canvas.restore();
            fill.setColor(0xCCFFFFFF);
            canvas.drawRect(edge - 3f * density, 0f, edge, h, fill);
            dots.setAlpha(80);
            canvas.drawRect(0f, 0f, w, h, dots);
        } else {
            float fade = time < SOLID_END ? 1f : 1f - MarvelLook.clamp01((time - SOLID_END) / (END - SOLID_END - 120f));
            fill.setColor((Math.round(255f * fade) << 24) | (MarvelLook.INK & 0xFFFFFF));
            canvas.drawRect(0f, 0f, w, h, fill);
        }

        layoutLogo(w, h);
        fill.setColor(MarvelLook.RED);
        canvas.drawRect(logo, fill);
        if (time < FLIP_END) {
            // The letters are windows onto another page than the one behind, bright against the red.
            int inside = (index + 5) % all.length;
            Shader shader = pageShader(inside);
            float pageScale = (float) mask.getWidth() / all[inside].getWidth();
            pageMatrix.setScale(pageScale, pageScale);
            pageMatrix.postTranslate(0f, (mask.getHeight() - all[inside].getHeight() * pageScale) / 2f);
            shader.setLocalMatrix(pageMatrix);
            letters.setShader(shader);
        } else {
            letters.setShader(null);
            letters.setColor(0xFFFFFFFF);
        }
        // Scaling the canvas rather than the bitmap, so the page in the letters moves and scales with them.
        float scale = logo.width() / mask.getWidth();
        canvas.save();
        canvas.translate(logo.left, logo.top);
        canvas.scale(scale, scale);
        canvas.drawBitmap(mask, 0f, 0f, letters);
        canvas.restore();

        // The slam: a white flash as the letters go solid.
        if (time >= FLIP_END && time < FLIP_END + 200f) {
            float flash = 1f - (time - FLIP_END) / 200f;
            fill.setColor((Math.round(190f * flash) << 24) | 0xFFFFFF);
            canvas.drawRect(0f, 0f, w, h, fill);
        }
    }

    private void layoutLogo(float w, float h) {
        float height, left, top;
        if (time < FLIP_END) {
            float t = time / FLIP_END;
            height = MarvelLook.lerp(START_H, HELD_H, 1f - (1f - t) * (1f - t)) * density;
            left = (w - height * LOGO_RATIO) / 2f;
            top = (h - height) / 2f;
        } else if (time < SOLID_END) {
            float t = (time - FLIP_END) / (SOLID_END - FLIP_END);
            height = MarvelLook.lerp(SLAM_H, HELD_H, MarvelLook.clamp01(t * 3f)) * density;
            left = (w - height * LOGO_RATIO) / 2f;
            top = (h - height) / 2f;
        } else {
            float t = MarvelLook.clamp01((time - SOLID_END) / (END - SOLID_END - 80f));
            float e = t * t * (3f - 2f * t);
            height = MarvelLook.lerp(HELD_H, LOGO_H, e) * density;
            left = MarvelLook.lerp((w - HELD_H * density * LOGO_RATIO) / 2f, LOGO_X * density, e);
            top = MarvelLook.lerp((h - HELD_H * density) / 2f, LOGO_Y * density, e);
        }
        logo.set(left, top, left + height * LOGO_RATIO, top + height);
    }
}
