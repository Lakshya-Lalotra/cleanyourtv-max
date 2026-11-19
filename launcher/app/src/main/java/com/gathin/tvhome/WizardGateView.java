package com.gathin.tvhome;

import android.animation.TimeAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;

/**
 * How the Wizarding World opens: its gold name fills the screen, far too close to read, and draws slowly back until it stands
 * whole over Hogwarts at night, mist moving on the lake and the windows flickering. The wands above the name then close like
 * a fan and spread again, the name rises, and the two ways in appear under it: the films, or the series.
 * Drawn in dp on a 960 x 540 screen.
 */
final class WizardGateView extends View {
    interface Listener {
        /** The two ways in are up and can be chosen between. */
        void onGateReady();
    }

    private static final float SCREEN_W = 960f, SCREEN_H = 540f;
    private static final float ZOOM_MS = 5600f, CLOSE_MS = 520f, OPEN_MS = 900f, SETTLE_MS = 700f, FIRST_ZOOM = 16f;
    private static final float FAN_END = ZOOM_MS + CLOSE_MS + OPEN_MS, READY = FAN_END + SETTLE_MS;
    /**
     * The logo as it was cut up (work/scripts: wizard_prep.py), in the units of the picture it came from: the lettering's box,
     * the point the wands turn about, the upright strip each wand was stood in, and how far each leans when the fan is open.
     */
    private static final float LOGO_UNITS = 1200f, LOGO_MID_X = 1000f, LOGO_MID_Y = 574f;
    private static final float LETTERS_LEFT = 400f, LETTERS_TOP = 548f, LETTERS_W = 1200f, LETTERS_H = 462f;
    private static final float PIVOT_X = 1000f, PIVOT_Y = 578f, WAND_W = 96f, WAND_H = 440f;
    private static final float[] WAND_LEAN = {-71.5f, -53.5f, -35.5f, -16.5f, 0.5f, 18.5f, 35.5f, 54.5f, 72.5f};
    /** Where the lit windows' picture sits in the castle's, as fractions of it. */
    private static final float[] WINDOWS_AT = {0.3293f, 0.20364f, 0.56719f, 0.79636f};
    private static final float LOGO_W = 560f, LOGO_Y = 262f, LOGO_UP_W = 270f, LOGO_UP_Y = 108f;
    private static final float CARD_W = 300f, CARD_H = 170f, CARD_TOP = 236f;
    private static final float[] CARD_X = {150f, 510f};
    private static final String[] LABELS = {"THE FILMS", "THE SERIES"};
    private static final String[] NOTES = {"Harry Potter and Fantastic Beasts, eleven films", "Coming soon"};
    private static final int NIGHT = 0xFF05080F, CREAM = 0xFFEFE2B8;

    private final float density;
    private final Listener listener;
    private final Paint plain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint art = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect from = new Rect();
    private final RectF rect = new RectF();
    private final TimeAnimator clock = new TimeAnimator();
    private final Bitmap castle, mist, windowsA, windowsB, letters, wands;
    private Bitmap filmPicture;
    private float time;
    private int selected;
    private boolean ready;

    WizardGateView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        frame.setStyle(Paint.Style.STROKE);
        Typeface cinzel;
        try {
            cinzel = getResources().getFont(R.font.cinzel);
        } catch (RuntimeException e) {
            cinzel = Typeface.SERIF;
        }
        label.setTypeface(cinzel);
        label.setFontVariationSettings("'wght' 800");
        label.setTextSize(22f);
        label.setLetterSpacing(0.08f);
        label.setTextAlign(Paint.Align.CENTER);
        note.setTypeface(MarvelLook.outfit(context, R.string.font_regular));
        note.setTextSize(14f);
        note.setTextAlign(Paint.Align.CENTER);
        castle = load("wizard/castle.jpg", Bitmap.Config.RGB_565);
        mist = load("wizard/mist.webp", Bitmap.Config.ARGB_8888);
        windowsA = load("wizard/windows_a.webp", Bitmap.Config.ARGB_8888);
        windowsB = load("wizard/windows_b.webp", Bitmap.Config.ARGB_8888);
        letters = load("wizard/logo_letters.webp", Bitmap.Config.ARGB_8888);
        wands = load("wizard/logo_wands.webp", Bitmap.Config.ARGB_8888);
        clock.setTimeListener((animation, total, delta) -> {
            time += Math.min(50f, delta);
            if (!ready && time >= READY) {
                ready = true;
                this.listener.onGateReady();
            }
            invalidate();
        });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private Bitmap load(String path, Bitmap.Config config) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = config;
        try (InputStream in = getContext().getAssets().open(path)) {
            return BitmapFactory.decodeStream(in, null, options);
        } catch (IOException | OutOfMemoryError e) {
            return null;   // that layer is left out
        }
    }

    void start() {
        if (!clock.isStarted()) clock.start();
    }

    void stop() {
        clock.cancel();
    }

    /** Any key while the name is still drawing back or the wands are moving: straight to the two ways in. */
    boolean skip() {
        if (time >= FAN_END) return false;
        time = FAN_END;
        return true;
    }

    boolean ready() {
        return ready;
    }

    int selected() {
        return selected;
    }

    void select(int which) {
        selected = Math.max(0, Math.min(CARD_X.length - 1, which));
        invalidate();
    }

    /** The films' card shows the first film's picture. */
    void setFilmPicture(Bitmap bitmap) {
        filmPicture = bitmap;
        invalidate();
    }

    /** Where Hedwig lands for a card, in dp: the middle of its top edge. */
    float perchX(int which) {
        return CARD_X[which] + CARD_W / 2f;
    }

    float perchY() {
        return CARD_TOP;
    }

    private static float easeOut(float t) {
        float u = 1f - MarvelLook.clamp01(t);
        return 1f - u * u * u;
    }

    /** How far open the fan of wands is: open all the way in, then shut and spread again, a little past and back. */
    private float fan() {
        if (time <= ZOOM_MS) return 1f;
        float closing = MarvelLook.clamp01((time - ZOOM_MS) / CLOSE_MS);
        if (closing < 1f) return 1f - closing * closing * (3f - 2f * closing);
        float t = MarvelLook.clamp01((time - ZOOM_MS - CLOSE_MS) / OPEN_MS) - 1f;
        return t * t * (2.7f * t + 1.7f) + 1f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(NIGHT);
        canvas.save();
        canvas.scale(density, density);
        // A steady pull back that only eases as it arrives: with a stronger curve the name is whole in the first second.
        float left = 1f - Math.min(1f, time / ZOOM_MS), zoom = 1f - (float) Math.pow(left, 1.5);
        float settle = easeOut((time - FAN_END) / SETTLE_MS);

        // The castle draws back a little too, more gently than the name in front of it.
        float scene = 1.4f - 0.4f * zoom;
        canvas.save();
        canvas.scale(scene, scene, SCREEN_W / 2f, 250f);
        drawScene(canvas);
        canvas.restore();
        // The night deepens behind the options so their words read.
        plain.setColor(MarvelLook.withAlpha(NIGHT, 0.5f * settle));
        canvas.drawRect(0f, 0f, SCREEN_W, SCREEN_H, plain);

        // Evenly on a ratio scale, so it is the same slow pull all the way back.
        float scale = (float) Math.exp(Math.log(FIRST_ZOOM) * (1f - zoom));
        float unit = MarvelLook.lerp(LOGO_W, LOGO_UP_W, settle) * scale / LOGO_UNITS;
        drawLogo(canvas, SCREEN_W / 2f, MarvelLook.lerp(LOGO_Y, LOGO_UP_Y, settle), unit, Math.min(1f, time / 600f));
        if (settle > 0f) {
            for (int i = 0; i < CARD_X.length; i++) drawCard(canvas, i, settle);
        }
        canvas.restore();
    }

    private void drawScene(Canvas canvas) {
        if (castle == null) return;
        // The picture covers the screen, cut a little at the sides.
        float height = SCREEN_H, width = height * castle.getWidth() / castle.getHeight(), x = (SCREEN_W - width) / 2f;
        rect.set(x, 0f, x + width, height);
        art.setAlpha(255);
        canvas.drawBitmap(castle, null, rect, art);
        float seconds = time / 1000f;
        rect.set(x + width * WINDOWS_AT[0], height * WINDOWS_AT[1], x + width * (WINDOWS_AT[0] + WINDOWS_AT[2]),
                height * (WINDOWS_AT[1] + WINDOWS_AT[3]));
        if (windowsA != null) {
            art.setAlpha(Math.round(255f * (0.5f + 0.5f * (float) Math.sin(seconds * 1.9f))));
            canvas.drawBitmap(windowsA, null, rect, art);
        }
        if (windowsB != null) {
            art.setAlpha(Math.round(255f * (0.5f + 0.5f * (float) Math.sin(seconds * 1.3f + 2.1f))));
            canvas.drawBitmap(windowsB, null, rect, art);
        }
        if (mist != null) {
            // Two sheets of mist over the lake, moving at different speeds, each drawn twice so it never runs out.
            drawMist(canvas, 318f, 1180f, 150f, seconds * 14f, 150);
            drawMist(canvas, 352f, 1500f, 190f, 400f + seconds * 24f, 110);
        }
    }

    private void drawMist(Canvas canvas, float top, float width, float height, float travelled, int alpha) {
        float x = -(travelled % width);
        art.setAlpha(alpha);
        for (int k = 0; k < 3 && x < SCREEN_W + 200f; k++, x += width) {
            rect.set(x - 100f, top, x - 100f + width, top + height);
            canvas.drawBitmap(mist, null, rect, art);
        }
    }

    /** The lettering, and over it the nine wands, each turned about the same point by its share of the fan. */
    private void drawLogo(Canvas canvas, float midX, float midY, float unit, float alpha) {
        art.setAlpha(Math.round(255f * alpha));
        if (letters != null) {
            float x = midX + (LETTERS_LEFT - LOGO_MID_X) * unit, y = midY + (LETTERS_TOP - LOGO_MID_Y) * unit;
            rect.set(x, y, x + LETTERS_W * unit, y + LETTERS_H * unit);
            canvas.drawBitmap(letters, null, rect, art);
        }
        if (wands == null) return;
        float pivotX = midX + (PIVOT_X - LOGO_MID_X) * unit, pivotY = midY + (PIVOT_Y - LOGO_MID_Y) * unit;
        float open = fan();
        int each = wands.getWidth() / WAND_LEAN.length;
        for (int i = 0; i < WAND_LEAN.length; i++) {
            from.set(i * each, 0, (i + 1) * each, wands.getHeight());
            rect.set(pivotX - WAND_W / 2f * unit, pivotY - WAND_H * unit, pivotX + WAND_W / 2f * unit, pivotY);
            canvas.save();
            canvas.rotate(WAND_LEAN[i] * open, pivotX, pivotY);
            canvas.drawBitmap(wands, from, rect, art);
            canvas.restore();
        }
    }

    private void drawCard(Canvas canvas, int i, float shown) {
        boolean chosen = i == selected;
        float rise = (1f - shown) * 14f;
        int alpha = Math.round(255f * shown * (chosen ? 1f : 0.62f));
        rect.set(CARD_X[i], CARD_TOP + rise, CARD_X[i] + CARD_W, CARD_TOP + CARD_H + rise);
        plain.setColor(0xFF120E0A);
        plain.setAlpha(Math.round(alpha * 0.86f));
        canvas.drawRect(rect, plain);
        if (i == 0 && filmPicture != null) {
            art.setAlpha(alpha);
            canvas.drawBitmap(filmPicture, null, rect, art);
        } else if (i == 1) {
            label.setColor(WizardPageView.GOLD);
            label.setAlpha(Math.round(alpha * 0.8f));
            canvas.drawText("A NEW TELLING", rect.centerX(), rect.centerY() + 8f, label);
        }
        frame.setColor(chosen ? WizardPageView.GOLD : 0x99FFFFFF);
        frame.setAlpha(Math.round(255f * shown * (chosen ? 1f : 0.6f)));
        frame.setStrokeWidth(chosen ? 3f : 1.2f);
        canvas.drawRect(rect, frame);
        label.setColor(chosen ? WizardPageView.GOLD : CREAM);
        label.setAlpha(alpha);
        canvas.drawText(LABELS[i], rect.centerX(), rect.bottom + 34f, label);
        note.setColor(0xFFFFFFFF);
        note.setAlpha(Math.round(alpha * 0.75f));
        canvas.drawText(NOTES[i], rect.centerX(), rect.bottom + 56f, note);
    }
}
