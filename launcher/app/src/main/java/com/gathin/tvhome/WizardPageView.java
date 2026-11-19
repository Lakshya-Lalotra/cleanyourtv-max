package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The Wizarding World page: one tall sheet under a night sky, a title to a section, and the sections change sides as they go
 * down. On the left a gold name with the film's picture under it and its story to the right; the next has them the other way
 * round. Drawn in dp on a 960 x 540 screen, and only the sections on screen.
 */
final class WizardPageView extends View {
    interface Art {
        /** A title's picture by its key, or null while it is not here. */
        Bitmap get(String titleKey);
    }

    static final int GOLD = 0xFFD4A84B, NIGHT = 0xFF0A0E1C;
    private static final float SCREEN_W = 960f, SCREEN_H = 540f;
    private static final float TOP = 24f, SECTION = 290f, PICTURE_W = 330f, PICTURE_H = 186f, PICTURE_TOP = 116f, PICTURE_X = 92f;
    private static final float NAME_X = 40f, NAME_W = 420f, STORY_X = 80f, STORY_W = 370f, STORY_TOP = 62f, REACH = 14f;
    private static final long FADE_MS = 360;

    private final float density;
    private final Paint plain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint picture = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint sky = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint stars = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint nameText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint storyText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ColorFilter resting = MarvelLook.tone(0.85f, 0.72f, 0f);
    private final ColorFilter skyTone = MarvelLook.tone(0.7f, 0.42f, 0f);
    private final float[] starPoints = new float[2 * 70];
    private final Map<String, String> playLines = new HashMap<>();

    private Art art;
    private List<WizardData.Title> titles;
    private Layout[] names, stories;
    private float scroll;
    private int target = -1;
    private Bitmap skyNow, skyBefore;
    private float skyBlend = 1f;
    private ValueAnimator fading;

    WizardPageView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        Typeface cinzel;
        try {
            cinzel = getResources().getFont(R.font.cinzel);
        } catch (RuntimeException e) {
            cinzel = Typeface.SERIF;
        }
        nameText.setTypeface(cinzel);
        nameText.setFontVariationSettings("'wght' 800");
        nameText.setTextSize(30f);
        nameText.setColor(GOLD);
        smallText.setTypeface(cinzel);
        smallText.setFontVariationSettings("'wght' 700");
        smallText.setTextSize(13f);
        smallText.setLetterSpacing(0.12f);
        storyText.setTypeface(MarvelLook.outfit(context, R.string.font_regular));
        storyText.setTextSize(16f);
        storyText.setColor(0xE6FFFFFF);
        lineText.setTypeface(MarvelLook.outfit(context, R.string.font_medium));
        lineText.setTextSize(14f);
        frame.setStyle(Paint.Style.STROKE);
        stars.setColor(0xB3FFFFFF);
        stars.setStrokeCap(Paint.Cap.ROUND);
        stars.setStrokeWidth(1.6f);
        Random random = new Random(7);
        for (int i = 0; i < starPoints.length; i += 2) {
            starPoints[i] = random.nextFloat() * SCREEN_W;
            starPoints[i + 1] = random.nextFloat() * SCREEN_H;
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setArt(Art source) {
        art = source;
    }

    void setTitles(List<WizardData.Title> all) {
        titles = all;
        target = -1;
        names = new Layout[all.size()];
        stories = new Layout[all.size()];
        invalidate();
    }

    /** The line under a title's story: where OK plays it. */
    void setPlayLine(String key, String line) {
        playLines.put(key, line);
        invalidate();
    }

    /** How far down the page can go, in dp. */
    float maxScroll() {
        return Math.max(0f, pictureTop(titles.size() - 1) + PICTURE_H + 48f - SCREEN_H);
    }

    void setScroll(float to) {
        if (to == scroll) return;
        scroll = to;
        invalidate();
    }

    /** The section whose picture is at this point of the screen (dp), or -1. */
    int hit(float x, float screenY) {
        float y = screenY + scroll;
        for (int i = 0; i < titles.size(); i++) {
            float left = pictureLeft(i), top = pictureTop(i);
            if (x >= left - REACH && x <= left + PICTURE_W + REACH && y >= top - REACH && y <= top + PICTURE_H + REACH) return i;
        }
        return -1;
    }

    /** Where on the screen the top edge of a section's picture is, in dp: where Hedwig lands. */
    float perchY(int index) {
        return pictureTop(index) - scroll;
    }

    /** The section Hedwig is on: its picture is framed in gold and lights the sky behind the page. */
    void setTarget(int index) {
        if (index == target) return;
        target = index;
        Bitmap bitmap = index < 0 || art == null ? null : art.get(titles.get(index).key);
        if (bitmap != null && bitmap != skyNow) {
            if (fading != null) fading.cancel();
            skyBefore = skyNow;
            skyNow = bitmap;
            skyBlend = 0f;
            fading = ValueAnimator.ofFloat(0f, 1f);
            fading.setDuration(FADE_MS);
            fading.addUpdateListener(a -> {
                skyBlend = (float) a.getAnimatedValue();
                invalidate();
            });
            fading.start();
        }
        invalidate();
    }

    void stop() {
        if (fading != null) fading.cancel();
    }

    private static boolean onLeft(int index) {
        return index % 2 == 0;
    }

    private float pictureLeft(int index) {
        return onLeft(index) ? PICTURE_X : SCREEN_W - PICTURE_X - PICTURE_W;
    }

    private float pictureTop(int index) {
        return TOP + index * SECTION + PICTURE_TOP;
    }

    private Layout name(int i) {
        if (names[i] == null) {
            String text = titles.get(i).name;
            names[i] = StaticLayout.Builder.obtain(text, 0, text.length(), nameText, Math.round(NAME_W))
                    .setAlignment(onLeft(i) ? Layout.Alignment.ALIGN_NORMAL : Layout.Alignment.ALIGN_OPPOSITE)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setIncludePad(false)
                    .setLineSpacing(0f, 0.95f)
                    .build();
        }
        return names[i];
    }

    private Layout story(int i) {
        if (stories[i] == null) {
            String text = titles.get(i).about;
            stories[i] = StaticLayout.Builder.obtain(text, 0, text.length(), storyText, Math.round(STORY_W))
                    .setMaxLines(6)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setIncludePad(false)
                    .setLineSpacing(4f, 1f)
                    .build();
        }
        return stories[i];
    }

    private void drawSky(Canvas canvas, Bitmap bitmap, float alpha) {
        rect.set(0f, 0f, SCREEN_W, SCREEN_H);
        sky.setColorFilter(skyTone);
        sky.setAlpha(Math.round(255f * alpha));
        canvas.drawBitmap(bitmap, null, rect, sky);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(NIGHT);
        if (titles == null) return;
        canvas.save();
        canvas.scale(density, density);
        if (skyBefore != null && skyBlend < 1f) drawSky(canvas, skyBefore, 1f);
        if (skyNow != null) drawSky(canvas, skyNow, skyBlend);
        plain.setColor(MarvelLook.withAlpha(NIGHT, 0.62f));
        canvas.drawRect(0f, 0f, SCREEN_W, SCREEN_H, plain);
        canvas.drawPoints(starPoints, stars);

        canvas.translate(0f, -scroll);
        for (int i = 0; i < titles.size(); i++) {
            float top = TOP + i * SECTION;
            if (top + PICTURE_TOP + PICTURE_H + 20f < scroll || top > scroll + SCREEN_H) continue;
            drawSection(canvas, i, top);
        }
        canvas.restore();
    }

    private void drawSection(Canvas canvas, int i, float top) {
        WizardData.Title title = titles.get(i);
        boolean left = onLeft(i), chosen = i == target;

        canvas.save();
        canvas.translate(left ? NAME_X : SCREEN_W - NAME_X - NAME_W, top);
        name(i).draw(canvas);
        canvas.restore();

        float x = pictureLeft(i), y = top + PICTURE_TOP;
        rect.set(x, y, x + PICTURE_W, y + PICTURE_H);
        plain.setColor(0xFF161C30);
        canvas.drawRect(rect, plain);
        Bitmap bitmap = art == null ? null : art.get(title.key);
        if (bitmap != null) {
            picture.setColorFilter(chosen ? null : resting);
            canvas.drawBitmap(bitmap, null, rect, picture);
        } else if (title.soon()) {
            smallText.setColor(GOLD);
            smallText.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("COMING SOON", rect.centerX(), rect.centerY() + 5f, smallText);
        }
        frame.setColor(chosen ? GOLD : 0x99FFFFFF);
        frame.setStrokeWidth(chosen ? 3f : 1.2f);
        canvas.drawRect(rect, frame);

        float storyX = left ? SCREEN_W - STORY_X - STORY_W : STORY_X, storyY = top + STORY_TOP;
        smallText.setColor(GOLD);
        smallText.setTextAlign(Paint.Align.LEFT);
        String when = (title.soon() ? "Coming soon" : String.valueOf(title.year)) + "  ·  " + title.kind;
        canvas.drawText(when.toUpperCase(Locale.ROOT), storyX, storyY, smallText);
        Layout story = story(i);
        canvas.save();
        canvas.translate(storyX, storyY + 14f);
        story.draw(canvas);
        canvas.restore();
        String line = playLines.get(title.key);
        if (chosen && line != null) {
            lineText.setColor(GOLD);
            canvas.drawText(line, storyX, storyY + 14f + story.getHeight() + 26f, lineText);
        }
    }
}
