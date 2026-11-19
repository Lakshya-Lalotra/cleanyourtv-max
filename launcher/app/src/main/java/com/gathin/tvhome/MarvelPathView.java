package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import java.util.Locale;

/**
 * The Marvel screen's path: the selected title's backdrop fills the screen under a printed dot, washed in its phase's colour,
 * and a line runs through the titles' posters, each a little tilted until it comes up. Behind you the line is drawn in each
 * phase's colour; ahead it is dotted. Drawn in dp on a 960 x 540 screen in one pass, and only what is on screen.
 */
final class MarvelPathView extends View {
    interface Art {
        /** A title's poster by its key, or null while it is not here yet. */
        Bitmap get(String titleKey);
    }

    private static final float SCREEN_W = 960f, SCREEN_H = 540f, FOCUS_X = 282f, GAP = 150f, CHAPTER_GAP = 92f;
    private static final float HIGH = 396f, LOW = 412f, MIDDLE = 404f, LEAD_IN_X = -80f, DOT_STEP = 8f, LABEL_WIDTH = 140f;
    private static final float[] DRIFT = {0, 3, -2, 2, -3, 2, 0, -2, 3, -2};
    private static final float BIG_SCALE = 1.35f, BIG_GAP = 260f;
    private static final float[] MERGE_Y = {92f, 212f, 508f}, MERGE_END_Y = {372f, 392f, 436f}, MERGE_WAVE = {-16f, 12f, -8f, 18f};
    private static final float POSTER_SMALL = 64f, POSTER_BIG = 88f, POSTER_RATIO = 1.5f, TILT = 2.6f;
    private static final long MOVE_MS = 340, FADE_MS = 360, ENTER_MS = 480, ENTER_ALL_MS = 1400;

    /** The strip of the screen a lane's view covers, in dp: everything a path draws is inside it. */
    static final float LANE_TOP = 316f, LANE_HEIGHT = 224f;

    private final float density;
    private final boolean lane;
    private final Paint plain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ahead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint travelled = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint picture = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint backdrop = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dots = new Paint();
    private final Paint leftShade = new Paint();
    private final Paint wash = new Paint();
    private final Paint lowShade = new Paint();
    private final Paint chipText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint yearText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tagText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelNormal = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelChosen = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final PathMeasure measure = new PathMeasure();
    private final Path piece = new Path();
    private final Rect source = new Rect();
    private final RectF rect = new RectF();
    private final float[] point = new float[2];
    private final ColorFilter backdropTone = MarvelLook.tone(1f, 0.85f, 0f);
    private final ColorFilter restingTone = MarvelLook.tone(1f, 0.78f, 0f);

    private Art art;
    private MarvelData.Order order;
    private int count;
    private float[] nodeX, nodeY;
    private Path[] segments;
    private float[] lengths;
    private float[] dotPoints;
    private int dotCount;
    private Layout[] normalLabels, chosenLabels;
    private float[] chipX, chipY, chipWidth;
    /** Where we are on the path: -1 at the start of the lead-in, 0 on the first stop, and fractions on the way between. */
    private float position;
    private int selected;
    /** How much this is the path being walked, 0 to 1: only on that one is the chosen title bigger and framed. */
    private float walked = 1f;
    /** A title that is another path's card: this path's line runs up off its strip there and has no card of its own. */
    private String[] bridgeKeys = {};
    private boolean[] bridges;
    /** The one card every universe ends in: bigger, with the other universes' lines running into it. */
    private String bigKey = "";
    private int big = -1;
    /** Cards other paths' lines run into: each comes to rest at its own place across the screen, not the usual one. */
    private String[] anchorKeys = {};
    private float[] anchorFocus = {};
    private int[] anchors = {};
    private float mergeEndX;
    /** On the backdrop: the other universes' lines, drawn right across the screen into the big card, and how far in they are. */
    private int[] mergeColors = {};
    private float merging;
    /** The colour of the phase we are in. */
    private int accent = MarvelLook.RED;
    private Bitmap backdropNow, backdropBefore;
    private String backdropKey = "";
    private float backdropBlend = 1f;
    /** Milliseconds into the entrance, or -1 once it is over. */
    private float entering = -1f;
    private ValueAnimator mover, fading, entrance;

    /** A lane draws one path in its own strip of the screen; the other kind draws only the backdrop behind them all. */
    MarvelPathView(Context context, boolean lane) {
        super(context);
        this.lane = lane;
        density = getResources().getDisplayMetrics().density;
        ahead.setStyle(Paint.Style.STROKE);
        ahead.setStrokeCap(Paint.Cap.ROUND);
        ahead.setStrokeWidth(2.4f);
        ahead.setColor(0xA6FFFFFF);
        travelled.setStyle(Paint.Style.STROKE);
        travelled.setStrokeCap(Paint.Cap.ROUND);
        travelled.setStrokeWidth(4f);
        frame.setStyle(Paint.Style.STROKE);
        dots.setShader(MarvelLook.halftone(density, density));
        leftShade.setShader(new LinearGradient(0f, 0f, 700f, 0f, new int[]{0xD907070A, 0xA607070A, 0x1A07070A},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        applyAccent();
        Typeface anton = MarvelLook.anton(context);
        chipText.setTypeface(anton);
        chipText.setTextSize(13f);
        tagText.setTypeface(anton);
        tagText.setTextSize(9f);
        labelNormal.setTypeface(anton);
        labelNormal.setTextSize(13f);
        labelChosen.setTypeface(anton);
        labelChosen.setTextSize(15f);
        yearText.setTypeface(MarvelLook.outfit(context, R.string.font_regular));
        yearText.setTextSize(11f);
        yearText.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setArt(Art source) {
        art = source;
    }

    int selected() {
        return selected;
    }

    /** The phase's colour, on everything that carries it here. */
    void setAccent(int color) {
        if (color == accent) return;
        accent = color;
        applyAccent();
        invalidate();
    }

    private void applyAccent() {
        wash.setShader(new LinearGradient(0f, 0f, 640f, 0f, MarvelLook.withAlpha(accent, 0.34f), MarvelLook.withAlpha(accent, 0f),
                Shader.TileMode.CLAMP));
        int low = MarvelLook.mix(MarvelLook.INK, accent, 0.2f);
        lowShade.setShader(new LinearGradient(0f, 300f, 0f, 420f, MarvelLook.withAlpha(low, 0f), MarvelLook.withAlpha(low, 0.92f),
                Shader.TileMode.CLAMP));
    }

    /** Puts a path up with this title selected, without animating. */
    void setOrder(MarvelData.Order newOrder, int index) {
        cancelAnimations();
        build(newOrder);
        selected = clamp(index);
        position = selected;
        entering = -1f;
        invalidate();
    }

    /** The line runs in from the left while the posters appear one after another. */
    void enter(MarvelData.Order newOrder) {
        cancelAnimations();
        build(newOrder);
        selected = 0;
        position = -1f;
        entering = 0f;
        entrance = ValueAnimator.ofFloat(0f, ENTER_ALL_MS);
        entrance.setDuration(ENTER_ALL_MS);
        entrance.setInterpolator(new LinearInterpolator());
        entrance.addUpdateListener(a -> {
            float ms = (float) a.getAnimatedValue();
            entering = ms >= ENTER_ALL_MS ? -1f : ms;
            float t = Math.min(1f, ms / ENTER_MS);
            position = -(1f - t) * (1f - t) * (1f - t);
            invalidate();
        });
        entrance.start();
    }

    void moveTo(int index) {
        int to = clamp(index);
        if (to == selected) return;
        selected = to;
        if (entrance != null && entrance.isRunning()) entrance.end();
        if (mover != null) mover.cancel();
        mover = ValueAnimator.ofFloat(position, to);
        mover.setDuration(MOVE_MS);
        mover.setInterpolator(new DecelerateInterpolator(1.5f));
        mover.addUpdateListener(a -> {
            position = (float) a.getAnimatedValue();
            invalidate();
        });
        mover.start();
    }

    /** The art behind everything, which crossfades to a new title's. */
    void setBackdrop(String key, Bitmap bitmap) {
        if (bitmap == null || key.equals(backdropKey)) return;
        backdropKey = key;
        if (fading != null) fading.cancel();
        backdropBefore = backdropNow;
        backdropNow = bitmap;
        backdropBlend = 0f;
        fading = ValueAnimator.ofFloat(0f, 1f);
        fading.setDuration(FADE_MS);
        fading.addUpdateListener(a -> {
            backdropBlend = (float) a.getAnimatedValue();
            invalidate();
        });
        fading.start();
    }

    void setBridges(String... titleKeys) {
        bridgeKeys = titleKeys;
    }

    void setBig(String titleKey) {
        bigKey = titleKey;
    }

    void setAnchors(String[] titleKeys, float[] focusX) {
        anchorKeys = titleKeys;
        anchorFocus = focusX;
    }

    /** The lines to draw into a card whose left side is at this x. */
    void setMerge(float endX, int... otherPathsColors) {
        mergeEndX = endX;
        mergeColors = otherPathsColors;
        invalidate();
    }

    void setMerging(float amount) {
        if (merging == amount) return;
        merging = amount;
        invalidate();
    }

    /** One universe's line from the left edge, in gentle waves, into the left side of the big card. */
    private void drawMerging(Canvas canvas, int k) {
        // Two lines come in one above the path and one below it; three, two above and one below.
        int row = k + MERGE_Y.length - mergeColors.length;
        float y = MERGE_Y[row], endY = MERGE_END_Y[row], reach = (mergeEndX + 10f) / 5f;
        piece.reset();
        piece.moveTo(-10f, y);
        float lastY = y;
        for (int j = 0; j < 4; j++) {
            float x = -10f + j * reach, nextY = y + MERGE_WAVE[(j + k) % MERGE_WAVE.length];
            piece.cubicTo(x + reach / 2f, lastY, x + reach / 2f, nextY, x + reach, nextY);
            lastY = nextY;
        }
        piece.cubicTo(mergeEndX - reach * 0.3f, lastY, mergeEndX - reach * 0.5f, endY, mergeEndX, endY);
        travelled.setColor(mergeColors[k]);
        travelled.setAlpha(Math.round(merging * 255f));
        canvas.drawPath(piece, travelled);
    }

    void setWalked(float amount) {
        if (walked == amount) return;
        walked = amount;
        invalidate();
    }

    void stop() {
        cancelAnimations();
    }

    private void cancelAnimations() {
        if (mover != null) mover.cancel();
        if (fading != null) fading.cancel();
        if (entrance != null) entrance.cancel();
    }

    private int clamp(int index) {
        return Math.max(0, Math.min(count - 1, index));
    }

    /** The whole path's shape in dp. Each stop sits on a crest or a trough, with the curve flat there. */
    private void build(MarvelData.Order newOrder) {
        order = newOrder;
        count = order.stops.size();
        bridges = new boolean[count];
        anchors = new int[anchorKeys.length];
        java.util.Arrays.fill(anchors, -1);
        big = -1;
        nodeX = new float[count];
        nodeY = new float[count];
        int chapters = order.chapters.size();
        chipX = new float[chapters];
        chipY = new float[chapters];
        chipWidth = new float[chapters];
        float x = FOCUS_X;
        for (int i = 0; i < count; i++) {
            MarvelData.Stop stop = order.stops.get(i);
            if (i > 0) x += GAP;
            if (i > 0 && stop.inChapter == 0) x += CHAPTER_GAP;
            // Far off to the right, with room in front of it for the other universes' lines to come in.
            if (stop.title.key.equals(bigKey)) x += BIG_GAP;
            nodeX[i] = x;
            nodeY[i] = (i % 2 == 1 ? HIGH : LOW) + DRIFT[i % DRIFT.length];
            for (String key : bridgeKeys) {
                if (!stop.title.key.equals(key)) continue;
                bridges[i] = true;
                nodeY[i] = LANE_TOP - 16f;
            }
            for (int a = 0; a < anchorKeys.length; a++) if (stop.title.key.equals(anchorKeys[a])) anchors[a] = i;
            if (stop.title.key.equals(bigKey)) {
                big = i;
                nodeY[i] = LOW;
            }
            if (stop.inChapter == 0) {
                int c = stop.chapter;
                chipX[c] = i == 0 ? x - 122f : x - (GAP + CHAPTER_GAP) / 2f;
                chipY[c] = i == 0 ? MIDDLE + 6f : (nodeY[i - 1] + nodeY[i]) / 2f;
                chipWidth[c] = chipText.measureText(order.chapters.get(c).label.toUpperCase(Locale.ROOT)) + 18f;
            }
        }
        segments = new Path[count + 1];
        lengths = new float[count + 1];
        float lastX = LEAD_IN_X, lastY = MIDDLE;
        for (int i = 0; i <= count; i++) {
            float toX = i < count ? nodeX[i] : nodeX[count - 1] + GAP * 1.4f;
            // A path that ends in another's card stays up there.
            float toY = i < count ? nodeY[i] : bridges[count - 1] ? nodeY[count - 1] : MIDDLE;
            Path path = new Path();
            path.moveTo(lastX, lastY);
            float half = (toX - lastX) / 2f;
            path.cubicTo(lastX + half, lastY, toX - half, toY, toX, toY);
            segments[i] = path;
            measure.setPath(path, false);
            lengths[i] = measure.getLength();
            lastX = toX;
            lastY = toY;
        }
        // The dotted line as ready-made points, so drawing it never asks for a texture as wide as the whole path.
        float total = 0f;
        for (float length : lengths) total += length;
        float[] all = new float[2 * ((int) (total / DOT_STEP) + count + 8)];
        int n = 0;
        for (int i = 0; i <= count; i++) {
            measure.setPath(segments[i], false);
            for (float at = 0f; at < lengths[i]; at += DOT_STEP) {
                measure.getPosTan(at, point, null);
                all[n++] = point[0];
                all[n++] = point[1];
            }
        }
        dotPoints = all;
        dotCount = n / 2;
        normalLabels = new Layout[count];
        chosenLabels = new Layout[count];
    }

    private int stopColor(int stop) {
        return order.chapters.get(order.stops.get(Math.min(stop, count - 1)).chapter).color;
    }

    private void pointAt(float at) {
        int whole = (int) Math.floor(at);
        int segment = whole + 1;
        if (segment >= count) {
            point[0] = nodeX[count - 1];
            point[1] = nodeY[count - 1];
            return;
        }
        measure.setPath(segments[segment], false);
        measure.getPosTan(lengths[segment] * (at - whole), point, null);
    }

    /** How far the entrance has brought in the item with this place in the queue, 0 to 1. */
    private float arrival(int queue) {
        if (entering < 0f) return 1f;
        return Math.max(0f, Math.min(1f, (entering - 220f - queue * 55f) / 320f));
    }

    private Layout label(int i, boolean chosen) {
        Layout[] cache = chosen ? chosenLabels : normalLabels;
        if (cache[i] == null) {
            String name = order.stops.get(i).title.shortName.toUpperCase(Locale.ROOT);
            TextPaint paint = chosen ? labelChosen : labelNormal;
            cache[i] = StaticLayout.Builder.obtain(name, 0, name.length(), paint, Math.round(LABEL_WIDTH))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setIncludePad(false)
                    .setLineSpacing(0f, 0.86f)
                    .build();
        }
        return cache[i];
    }

    /** Text fades out towards the screen's edges rather than being cut off by them. */
    private static float edgeFade(float screenX, float halfWidth) {
        float left = (screenX - halfWidth) / 64f, right = (SCREEN_W - (screenX + halfWidth)) / 64f;
        return Math.max(0f, Math.min(1f, Math.min(left, right)));
    }

    private String yearOf(MarvelData.Title title) {
        return order.isChronological() ? title.story : String.valueOf(title.released);
    }

    private void drawBackdrop(Canvas canvas, Bitmap bitmap, float alpha) {
        source.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        rect.set(0f, 0f, SCREEN_W, SCREEN_H);
        backdrop.setColorFilter(backdropTone);
        backdrop.setAlpha(Math.round(255f * alpha));
        canvas.drawBitmap(bitmap, source, rect, backdrop);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!lane) {
            canvas.drawColor(MarvelLook.INK);
            canvas.save();
            canvas.scale(density, density);
            if (backdropBefore != null && backdropBlend < 1f) drawBackdrop(canvas, backdropBefore, 1f);
            if (backdropNow != null) drawBackdrop(canvas, backdropNow, backdropBlend);
            dots.setAlpha(56);
            canvas.drawRect(0f, 0f, SCREEN_W, SCREEN_H, dots);
            canvas.drawRect(0f, 0f, SCREEN_W, SCREEN_H, leftShade);
            canvas.drawRect(0f, 0f, SCREEN_W, SCREEN_H, wash);
            canvas.drawRect(0f, 300f, SCREEN_W, SCREEN_H, lowShade);
            if (merging > 0f) for (int k = 0; k < mergeColors.length; k++) drawMerging(canvas, k);
            canvas.restore();
            return;
        }
        if (order == null) return;
        canvas.save();
        canvas.scale(density, density);
        canvas.translate(0f, -LANE_TOP);

        pointAt(position);
        float cometX = point[0], cometY = point[1];
        // An anchor card comes to rest further over, leaving the screen's width for the lines that run into it.
        float focus = FOCUS_X;
        for (int a = 0; a < anchors.length; a++) {
            if (anchors[a] >= 0) focus += (anchorFocus[a] - FOCUS_X) * MarvelLook.clamp01(1f - Math.abs(position - anchors[a]));
        }
        float camera = Math.max(0f, cometX - focus);
        float right = camera + SCREEN_W;
        canvas.translate(-camera, 0f);

        drawAhead(canvas, cometX, right);
        drawTravelled(canvas, camera, right);
        for (int c = 0; c < chipX.length; c++) {
            if (chipX[c] + chipWidth[c] < camera || chipX[c] - chipWidth[c] > right) continue;
            drawChip(canvas, c, arrival(c));
        }
        for (int i = 0; i < count; i++) {
            if (bridges[i] || nodeX[i] < camera - 110f || nodeX[i] > right + 110f) continue;
            drawStop(canvas, i, camera);
        }
        // A bright dot on the line while it is on the move, before it settles under a poster.
        if (Math.abs(position - Math.round(position)) > 0.02f) {
            plain.setColor(0xFFFFFFFF);
            canvas.drawCircle(cometX, cometY, 6f, plain);
        }
        canvas.restore();
    }

    private void drawAhead(Canvas canvas, float fromX, float right) {
        // The points run left to right, so the visible ones are one run of the array.
        int low = 0, high = dotCount;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (dotPoints[2 * middle] <= fromX) low = middle + 1;
            else high = middle;
        }
        int end = low;
        while (end < dotCount && dotPoints[2 * end] <= right) end++;
        if (end > low) canvas.drawPoints(dotPoints, 2 * low, 2 * (end - low), ahead);
    }

    /** Each stretch of line behind us in the colour of the phase it leads into. */
    private void drawTravelled(Canvas canvas, float camera, float right) {
        int whole = (int) Math.floor(position);
        int current = whole + 1;
        for (int s = 0; s < current && s <= count; s++) {
            float from = s == 0 ? LEAD_IN_X : nodeX[s - 1];
            float to = s < count ? nodeX[s] : from;
            if (to < camera - 10f || from > right + 10f) continue;
            travelled.setColor(stopColor(s));
            canvas.drawPath(segments[s], travelled);
        }
        if (current <= count && position > whole) {
            measure.setPath(segments[current], false);
            piece.reset();
            measure.getSegment(0f, lengths[current] * (position - whole), piece, true);
            travelled.setColor(stopColor(current));
            canvas.drawPath(piece, travelled);
        }
    }

    private void drawChip(Canvas canvas, int c, float alpha) {
        if (alpha <= 0f) return;
        MarvelData.Chapter chapter = order.chapters.get(c);
        String text = chapter.label.toUpperCase(Locale.ROOT);
        float half = chipWidth[c] / 2f, x = chipX[c], y = chipY[c];
        canvas.save();
        canvas.rotate(-2f, x, y);
        rect.set(x - half, y - 12f, x + half, y + 12f);
        plain.setColor(chapter.color);
        plain.setAlpha(Math.round(alpha * 255f));
        canvas.drawRect(rect, plain);
        chipText.setColor(MarvelLook.onColor(chapter.color));
        chipText.setAlpha(Math.round(alpha * 255f));
        canvas.drawText(text, x - half + 9f, y + 6f, chipText);
        canvas.restore();
    }

    private void drawStop(Canvas canvas, int i, float camera) {
        float alpha = arrival(i);
        if (alpha <= 0f) return;
        MarvelData.Title title = order.stops.get(i).title;
        int color = stopColor(i);
        float x = nodeX[i], y = nodeY[i];
        float near = MarvelLook.clamp01(1f - Math.abs(i - position)) * walked;
        float width = MarvelLook.lerp(POSTER_SMALL, POSTER_BIG, near) * (i == big ? BIG_SCALE : 1f), height = width * POSTER_RATIO;
        boolean chosen = i == selected && walked > 0.5f;
        float tilt = (i % 2 == 0 ? -TILT : TILT) * (1f - near);

        canvas.save();
        canvas.rotate(tilt, x, y);
        rect.set(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f);
        int a = Math.round(alpha * 255f);
        plain.setColor(MarvelLook.mix(MarvelLook.INK, color, 0.35f));
        plain.setAlpha(a);
        canvas.drawRect(rect, plain);
        Bitmap poster = art == null ? null : art.get(title.key);
        if (poster != null) {
            picture.setAlpha(a);
            picture.setColorFilter(chosen ? null : restingTone);
            canvas.drawBitmap(poster, null, rect, picture);
        }
        // A panel's ink line: white, and the phase's colour around the chosen one.
        frame.setColor(0xFFFFFFFF);
        frame.setAlpha(a);
        frame.setStrokeWidth(chosen ? 2f : 1.5f);
        canvas.drawRect(rect, frame);
        if (chosen) {
            float out = 3f;
            frame.setColor(accent);
            frame.setAlpha(a);
            frame.setStrokeWidth(4f);
            canvas.drawRect(rect.left - out, rect.top - out, rect.right + out, rect.bottom + out, frame);
        }
        if (title.isSeries()) {
            float tagWide = tagText.measureText("SERIES") + 8f;
            plain.setColor(color);
            plain.setAlpha(a);
            canvas.drawRect(rect.right - tagWide, rect.top, rect.right, rect.top + 13f, plain);
            tagText.setColor(MarvelLook.onColor(color));
            tagText.setAlpha(a);
            canvas.drawText("SERIES", rect.right - tagWide + 4f, rect.top + 10f, tagText);
        }
        canvas.restore();

        if (i == big) return;   // its name is the screen's title, and there is no room under a card this size
        Layout layout = label(i, chosen);
        float half = Math.min(LABEL_WIDTH / 2f, layout.getLineWidth(0) / 2f);
        float fade = alpha * edgeFade(x - camera, half);
        if (fade <= 0f) return;
        float top = y + height / 2f + (chosen ? 8f : 6f);
        TextPaint paint = layout.getPaint();
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(Math.round(fade * (chosen ? 255f : 205f)));
        canvas.save();
        canvas.translate(x - LABEL_WIDTH / 2f, top);
        layout.draw(canvas);
        canvas.restore();
        // A chosen title with a two-line name has no room for its year, and its year is up in the title's details anyway.
        if (chosen && layout.getLineCount() > 1) return;
        yearText.setColor(0xFFFFFFFF);
        yearText.setAlpha(Math.round(fade * (chosen ? 210f : 130f)));
        canvas.drawText(yearOf(title), x, top + layout.getHeight() + 12f, yearText);
    }
}
