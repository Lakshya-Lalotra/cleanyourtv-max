package com.gathin.tvhome;

import android.animation.TimeAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Middle-earth's three places, one above the other, after Middle-earth TV (github.com/ikevinadams/middle-earth-tv-home),
 * whose paintings, places in them and effects these are. Bag End at dusk: three lanterns on the fence are the ways on, and
 * the one chosen is lit. The beacons of Gondor: a fire for each film in story order, burning as far as the one chosen. Mordor:
 * the Eye's beam sweeps across the plain to the series or film chosen, lighting the ash it passes through, while Mount Doom
 * smokes and spits. Gandalf's moth flies to whatever is chosen and circles it. Drawn in dp on a 960 x 540 screen; every
 * position is half of the one in the original's 1920 x 1080 frame.
 */
final class MiddleEarthSceneView extends View {
    static final int BAG_END = 0, BEACONS = 1, MORDOR = 2;
    private static final float SCREEN_W = 960f, SCREEN_H = 540f, SLIDE_MS = 700f;
    /** Bag End's lanterns (their flames), and what each leads to. */
    private static final float[][] LANTERN = {{238.5f, 415.5f}, {402.5f, 418f}, {568.5f, 418.5f}};
    private static final String[] LANTERN_NAME = {"The Hobbit", "The Lord of the Rings", "Mordor"};
    /** Each beacon's fire and the size of its light, and where its film's title goes: beside the near ones, above the far. */
    private static final float[][] BEACON = {{152f, 75f, 115f}, {402f, 113f, 75f}, {585f, 141f, 55f}, {716.5f, 159f, 39f},
            {826.5f, 176f, 26f}, {917f, 189f, 18f}};
    private static final float[][] LABEL = {{220f, 36f}, {450f, 50f}, {585f, 48f}, {716.5f, 75f}, {826.5f, 98f}, {933f, 113f}};
    private static final Paint.Align[] LABEL_ALIGN = {Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.CENTER, Paint.Align.CENTER,
            Paint.Align.CENTER, Paint.Align.RIGHT};
    private static final float EYE_X = 666f, EYE_Y = 86f, GROUND_Y = 455f, CRATER_X = 205f, CRATER_Y = 209f;
    private static final float[] SLOT_X = {150f, 380f, 610f, 830f};
    private static final float LAVA_X = 120f, LAVA_Y = 170f, LAVA_W = 210f, LAVA_H = 195f, MOTH_W = 30f;

    private final float density;
    private final Paint art = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint plain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beam = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lavaPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint kicker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint play = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lanternText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF rect = new RectF();
    private final SoftGlow fire = new SoftGlow(0xFFFFAA50), smokeGlow = new SoftGlow(0xFF2E2420);
    private final TimeAnimator clock = new TimeAnimator();
    private final Random random = new Random(3);
    private final Map<String, String> playLines = new HashMap<>();

    private Bitmap bagEnd, beacons, mordor, lava, moth, logo;
    private Paint[] lanternLight, beaconLight;
    private final float[] lanternOn = new float[3], beaconOn = new float[6];
    private final int[] chosen = {1, 0, 0};
    private int scene;
    private float time, at, from, slideFor = -1f, flare, gaze = SLOT_X[0], gazeSpeed;
    private float mothX = 400f, mothY = 360f, mothVX, mothVY, mothTurn, mothCircle, mothArrived;

    private final float[][] flies = new float[24][5];
    private final float[][] ash = new float[150][6];
    private final List<float[]> embers = new ArrayList<>(), sparks = new ArrayList<>(), smoke = new ArrayList<>();

    MiddleEarthSceneView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        Typeface serif = Typeface.create(Typeface.SERIF, Typeface.NORMAL);
        for (Paint p : new Paint[]{kicker, name, sub, play, lanternText}) {
            p.setTypeface(serif);
            p.setShadowLayer(8f, 0f, 1f, 0xD9000000);
        }
        kicker.setTextSize(9.5f);
        kicker.setLetterSpacing(0.3f);
        name.setTextSize(21f);
        sub.setTextSize(10f);
        sub.setLetterSpacing(0.06f);
        play.setTextSize(10.5f);
        play.setColor(0xFFF0C27A);
        lanternText.setTextSize(15f);
        lanternText.setLetterSpacing(0.04f);
        lanternText.setTextAlign(Paint.Align.CENTER);
        lavaPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        for (float[] f : flies) {
            f[0] = 100f + random.nextFloat() * 760f;
            f[1] = 230f + random.nextFloat() * 160f;
            f[4] = random.nextFloat() * 9f;
        }
        for (float[] a : ash) {
            a[0] = 150f + random.nextFloat() * 780f;
            a[1] = 70f + random.nextFloat() * 360f;
            a[4] = 0.4f + random.nextFloat() * 1.2f;
            a[5] = random.nextFloat() < 0.14f ? 1f : 0f;
        }
        clock.setTimeListener((animation, total, delta) -> {
            tick(Math.min(50f, delta) / 1000f);
            invalidate();
        });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The paintings, each with its lit copy where it has one, the lava, the moth and the name for Bag End's sky. */
    void setPictures(Bitmap bagEndDark, Bitmap bagEndLit, Bitmap beaconsDark, Bitmap beaconsLit, Bitmap mordorPicture,
            Bitmap lavaPicture, Bitmap mothPicture, Bitmap logoPicture) {
        bagEnd = bagEndDark;
        beacons = beaconsDark;
        mordor = mordorPicture;
        lava = lavaPicture;
        moth = mothPicture;
        logo = logoPicture;
        lanternLight = new Paint[LANTERN.length];
        for (int i = 0; i < LANTERN.length; i++) lanternLight[i] = litSpot(bagEndLit, LANTERN[i][0], LANTERN[i][1], 60f);
        beaconLight = new Paint[BEACON.length];
        for (int i = 0; i < BEACON.length; i++) beaconLight[i] = litSpot(beaconsLit, BEACON[i][0], BEACON[i][1], BEACON[i][2]);
        invalidate();
    }

    /** A round patch of the lit painting, strongest in the middle: drawn over the dark one, that lamp or fire is burning. */
    private Paint litSpot(Bitmap lit, float x, float y, float radius) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (lit == null) return paint;
        BitmapShader picture = new BitmapShader(lit, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix fit = new Matrix();
        fit.setScale(SCREEN_W / lit.getWidth(), SCREEN_H / lit.getHeight());
        picture.setLocalMatrix(fit);
        RadialGradient round = new RadialGradient(x, y, radius, new int[]{0xFF000000, 0xFF000000, 0x00000000},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(new ComposeShader(picture, round, PorterDuff.Mode.DST_IN));
        return paint;
    }

    void start() {
        if (!clock.isStarted()) clock.start();
    }

    void stop() {
        clock.cancel();
    }

    int scene() {
        return scene;
    }

    int chosen() {
        return chosen[scene];
    }

    /** How many things there are to choose between in this place. */
    int count(int place) {
        return place == BAG_END ? LANTERN.length : place == BEACONS ? BEACON.length : SLOT_X.length;
    }

    /** To another place, sliding up or down to it. */
    void go(int place) {
        if (place == scene || place < 0 || place > MORDOR) return;
        from = at;
        scene = place;
        slideFor = 0f;
        mothArrived = 0f;
    }

    void choose(int which) {
        which = Math.max(0, Math.min(count(scene) - 1, which));
        if (which == chosen[scene]) return;
        chosen[scene] = which;
        mothArrived = 0f;
    }

    void chooseIn(int place, int which) {
        chosen[place] = Math.max(0, Math.min(count(place) - 1, which));
    }

    /** OK: the lantern, the beacon or the Eye flares. */
    void flare() {
        flare = 1f;
    }

    void setPlayLine(String key, String line) {
        playLines.put(key, line);
    }

    // ---- motion -----------------------------------------------------------------------------------

    private void tick(float dt) {
        time += dt;
        flare = Math.max(0f, flare - dt / 0.9f);
        if (slideFor >= 0f) {
            slideFor += dt * 1000f;
            float t = Math.min(1f, slideFor / SLIDE_MS), e = t * t * (3f - 2f * t);
            at = from + (scene - from) * e;
            if (t >= 1f) slideFor = -1f;
        } else {
            at = scene;
        }
        for (int i = 0; i < LANTERN.length; i++) lanternOn[i] = approach(lanternOn[i], i == chosen[BAG_END] ? 1f : 0f, dt / 0.5f);
        for (int i = 0; i < BEACON.length; i++) {
            float want = i < chosen[BEACONS] ? 0.7f : i == chosen[BEACONS] ? 1f : 0f;
            beaconOn[i] = approach(beaconOn[i], want, dt / 0.5f);
        }
        float aim = SLOT_X[chosen[MORDOR]], before = gaze;
        gazeSpeed += ((aim - gaze) * 60f - gazeSpeed * 13f) * dt;
        gaze += gazeSpeed * dt;
        gazeSpeed = (gaze - before) / Math.max(dt, 1e-3f);
        moveMoth(dt);
        if (Math.abs(at - BAG_END) < 1f) moveFlies(dt);
        if (Math.abs(at - BEACONS) < 1f) moveEmbers(dt);
        if (Math.abs(at - MORDOR) < 1f) moveMordor(dt);
    }

    private static float approach(float value, float to, float by) {
        return value < to ? Math.min(to, value + by) : Math.max(to, value - by);
    }

    /** Where the moth heads for: just over the chosen lantern, fire or title, on the screen as it is now. */
    private float[] mothAim() {
        float y = (scene - at) * SCREEN_H;
        if (scene == BAG_END) return new float[]{LANTERN[chosen[0]][0], LANTERN[chosen[0]][1] - 20f + y, 22f};
        if (scene == BEACONS) {
            float[] b = BEACON[chosen[1]];
            return new float[]{b[0] + b[2] * 0.35f, b[1] - b[2] * 0.2f + y, 10f + b[2] * 0.18f};
        }
        return new float[]{SLOT_X[chosen[2]], GROUND_Y - 34f + y, 26f};
    }

    private void moveMoth(float dt) {
        float[] aim = mothAim();
        mothArrived += dt;
        mothCircle += dt * (2.2f + 0.8f * (float) Math.sin(time * 0.7f));
        // It flies to the light, then circles it, never quite still.
        float orbit = mothArrived > 1.2f ? 1f : 0f;
        float tx = aim[0] + (float) Math.cos(mothCircle) * aim[2] * orbit + (float) Math.sin(time * 3.1f) * 3f;
        float ty = aim[1] + (float) Math.sin(mothCircle * 1.3f) * aim[2] * 0.5f * orbit + (float) Math.sin(time * 4.3f) * 2.5f;
        mothVX += ((tx - mothX) * 40f - mothVX * 11f) * dt;
        mothVY += ((ty - mothY) * 40f - mothVY * 11f) * dt;
        mothX += mothVX * dt;
        mothY += mothVY * dt;
        float heading = (float) Math.toDegrees(Math.atan2(mothVY, mothVX)) + 90f, turn = heading - mothTurn;
        while (turn > 180f) turn -= 360f;
        while (turn < -180f) turn += 360f;
        mothTurn += turn * Math.min(1f, dt * 6f);
    }

    private void moveFlies(float dt) {
        for (float[] f : flies) {
            f[2] = (f[2] + (random.nextFloat() - 0.5f) * 15f * dt) * 0.98f;
            f[3] = (f[3] + (random.nextFloat() - 0.5f) * 15f * dt) * 0.98f;
            f[0] = Math.max(75f, Math.min(900f, f[0] + f[2] * dt));
            f[1] = Math.max(220f, Math.min(400f, f[1] + f[3] * dt));
        }
    }

    private void moveEmbers(float dt) {
        float[] b = BEACON[chosen[BEACONS]];
        if (random.nextFloat() < dt * 14f * (b[2] / 60f)) {
            embers.add(new float[]{b[0] + (random.nextFloat() - 0.5f) * b[2] * 0.25f, b[1] - b[2] * 0.1f, 5f + random.nextFloat() * 12f,
                    -10f - random.nextFloat() * 20f * b[2] / 60f, 1f});
        }
        for (int i = embers.size() - 1; i >= 0; i--) {
            float[] e = embers.get(i);
            e[4] -= dt * 0.5f;
            e[0] += e[2] * dt;
            e[1] += e[3] * dt;
            if (e[4] <= 0f) embers.remove(i);
        }
    }

    private void moveMordor(float dt) {
        if (random.nextFloat() < dt * 5f) {
            smoke.add(new float[]{CRATER_X + (random.nextFloat() - 0.5f) * 15f, CRATER_Y - 5f, 3f + random.nextFloat() * 5f,
                    -11f - random.nextFloat() * 7f, 11f + random.nextFloat() * 7f, 0f, 7f + random.nextFloat() * 4f});
        }
        for (int i = smoke.size() - 1; i >= 0; i--) {
            float[] p = smoke.get(i);
            p[5] += dt;
            if (p[5] > p[6]) {
                smoke.remove(i);
                continue;
            }
            p[0] += p[2] * dt;
            p[1] += p[3] * dt;
            p[2] += 0.75f * dt;
            p[4] += 6.5f * dt;
        }
        if (random.nextFloat() < dt * 9f) {
            sparks.add(new float[]{CRATER_X + (random.nextFloat() - 0.5f) * 12f, CRATER_Y - 3f, (random.nextFloat() - 0.5f) * 45f,
                    -45f - random.nextFloat() * 65f, 1f + random.nextFloat()});
        }
        for (int i = sparks.size() - 1; i >= 0; i--) {
            float[] p = sparks.get(i);
            p[4] -= dt;
            if (p[4] <= 0f) {
                sparks.remove(i);
                continue;
            }
            p[3] += 70f * dt;
            p[0] += p[2] * dt;
            p[1] += p[3] * dt;
        }
        // Ash and grit round the tower, pushed along as the beam passes through.
        float gx = gaze, gy = GROUND_Y - 10f, dx = gx - EYE_X, dy = gy - EYE_Y, length = dx * dx + dy * dy;
        for (float[] a : ash) {
            float u = MarvelLook.clamp01(((a[0] - EYE_X) * dx + (a[1] - EYE_Y) * dy) / length);
            float d = (float) Math.hypot(a[0] - (EYE_X + dx * u), a[1] - (EYE_Y + dy * u)), wide = 10f + 45f * u;
            float near = MarvelLook.clamp01(1f - d / (wide * 2.6f));
            a[2] += gazeSpeed * 0.55f * near * dt - a[2] * 1.2f * dt + (random.nextFloat() - 0.5f) * 9f * dt + 2f * dt;
            a[3] += -Math.abs(gazeSpeed) * 0.06f * near * dt - a[3] * 1.2f * dt + (random.nextFloat() - 0.5f) * 7f * dt + 1.5f * dt;
            a[0] += a[2] * dt;
            a[1] += a[3] * dt;
            if (a[0] > 940f) a[0] = 150f;
            if (a[0] < 140f) a[0] = 930f;
            if (a[1] > 440f) a[1] = 70f;
            if (a[1] < 60f) a[1] = 430f;
        }
    }

    // ---- drawing ----------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF000000);
        canvas.save();
        canvas.scale(density, density);
        for (int place = BAG_END; place <= MORDOR; place++) {
            float y = (place - at) * SCREEN_H;
            if (Math.abs(y) >= SCREEN_H) continue;
            canvas.save();
            canvas.translate(0f, y);
            canvas.clipRect(0f, 0f, SCREEN_W, SCREEN_H);
            if (place == BAG_END) drawBagEnd(canvas);
            else if (place == BEACONS) drawBeacons(canvas);
            else drawMordor(canvas);
            canvas.restore();
        }
        drawMoth(canvas);
        canvas.restore();
    }

    /** A painting, very slowly drifting, as if seen by someone breathing. */
    private void painting(Canvas canvas, Bitmap bitmap, float speed) {
        if (bitmap == null) return;
        float grow = 1.02f + 0.012f * (float) Math.sin(time * speed);
        rect.set(SCREEN_W / 2f * (1f - grow), SCREEN_H / 2f * (1f - grow), SCREEN_W / 2f * (1f + grow), SCREEN_H / 2f * (1f + grow));
        canvas.drawBitmap(bitmap, null, rect, art);
    }

    private float flick(float speed, int i) {
        return 0.9f + 0.06f * (float) Math.sin(time * speed + i * 2f) * (float) Math.sin(time * 5.1f + i) + 0.04f * (float) Math.sin(time * 29f + i);
    }

    private void drawBagEnd(Canvas canvas) {
        painting(canvas, bagEnd, 0.05f);
        if (logo != null) {
            float width = 170f, height = width * logo.getHeight() / logo.getWidth();
            rect.set(SCREEN_W / 2f - width / 2f, 22f, SCREEN_W / 2f + width / 2f, 22f + height);
            canvas.drawBitmap(logo, null, rect, art);
        }
        for (int i = 0; i < LANTERN.length; i++) {
            float on = lanternOn[i] + (i == chosen[BAG_END] ? flare * 0.6f : 0f);
            if (lanternLight != null && on > 0.01f) {
                lanternLight[i].setAlpha(Math.round(255f * Math.min(1f, smooth(lanternOn[i]) * flick(13f, i))));
                canvas.drawCircle(LANTERN[i][0], LANTERN[i][1], 60f, lanternLight[i]);
                if (flare > 0f && i == chosen[BAG_END]) fire.draw(canvas, LANTERN[i][0], LANTERN[i][1], 80f, 80f, flare * 0.6f);
            }
            boolean picked = i == chosen[BAG_END];
            float y = LANTERN[i][1] - 64f + (float) Math.sin(time * 0.8f + i) * 1f;
            canvas.save();
            canvas.scale(picked ? 1.12f : 0.95f, picked ? 1.12f : 0.95f, LANTERN[i][0], y);
            lanternText.setColor(picked ? 0xFFFFF0D2 : 0xFFF4D6A2);
            lanternText.setShadowLayer(picked ? 10f : 8f, 0f, picked ? 0f : 1f, picked ? 0xBFFFBE64 : 0xD9080603);
            lanternText.setAlpha(picked ? 255 : 220);
            canvas.drawText(LANTERN_NAME[i], LANTERN[i][0], y, lanternText);
            canvas.restore();
        }
        for (float[] f : flies) {
            float blink = (float) Math.sin(time * (0.6f + f[4] * 0.1f) * 1.4f + f[4]);
            if (blink < 0.05f) continue;
            plain.setColor(0xFFF0FFAA);
            plain.setAlpha(Math.round(60f * blink));
            canvas.drawCircle(f[0], f[1], 4f, plain);
            plain.setAlpha(Math.round(240f * blink));
            canvas.drawCircle(f[0], f[1], 1.3f, plain);
        }
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    private void drawBeacons(Canvas canvas) {
        painting(canvas, beacons, 0.04f);
        for (int i = 0; i < BEACON.length; i++) {
            float on = beaconOn[i];
            if (beaconLight == null || on <= 0.01f) continue;
            beaconLight[i].setAlpha(Math.round(255f * Math.min(1f, smooth(Math.min(1f, on)) * (0.92f + 0.08f * flick(11f, i)))));
            canvas.drawCircle(BEACON[i][0], BEACON[i][1], BEACON[i][2], beaconLight[i]);
        }
        float[] b = BEACON[chosen[BEACONS]];
        if (flare > 0f) fire.draw(canvas, b[0], b[1] - b[2] * 0.2f, b[2] * 1.6f, b[2] * 1.6f, flare * 0.75f);
        for (float[] e : embers) {
            plain.setColor(0xFFFF8C3C);
            plain.setAlpha(Math.round(230f * e[4]));
            canvas.drawRect(e[0], e[1], e[0] + 1.2f, e[1] + 1.2f, plain);
        }
        MiddleEarthData.Entry film = MiddleEarthData.films().get(chosen[BEACONS]);
        float[] l = LABEL[chosen[BEACONS]];
        words(canvas, film, l[0], l[1], LABEL_ALIGN[chosen[BEACONS]], String.valueOf(film.title.year));
    }

    /** A title as the beacons show it: the saga small, the name, its year, and where it plays. */
    private void words(Canvas canvas, MiddleEarthData.Entry entry, float x, float top, Paint.Align align, String year) {
        for (Paint p : new Paint[]{kicker, name, sub, play}) p.setTextAlign(align);
        kicker.setColor(0xA8F3E8CF);
        canvas.drawText(entry.kicker.toUpperCase(Locale.ROOT), x, top + 9f, kicker);
        name.setColor(0xFFF3E8CF);
        canvas.drawText(entry.title.name, x, top + 33f, name);
        sub.setColor(0x99F3E8CF);
        canvas.drawText(year, x, top + 48f, sub);
        String line = playLines.get(entry.title.key);
        if (line != null) canvas.drawText(line, x, top + 64f, play);
    }

    private void drawMordor(Canvas canvas) {
        painting(canvas, mordor, 0.035f);
        for (float[] p : smoke) {
            float k = p[5] / p[6], warm = MarvelLook.clamp01(1f - (CRATER_Y - p[1]) / 80f);
            smokeGlow.draw(canvas, p[0], p[1], p[4], p[4], (float) Math.sin(Math.PI * k) * (0.35f + 0.25f * warm));
        }
        if (lava != null) {
            lavaPaint.setAlpha(Math.round(255f * (0.22f + 0.2f * (0.5f + 0.5f * (float) Math.sin(time * 2.3f))
                    * (0.75f + 0.25f * (float) Math.sin(time * 7.1f)))));
            rect.set(LAVA_X, LAVA_Y, LAVA_X + LAVA_W, LAVA_Y + LAVA_H);
            canvas.drawBitmap(lava, null, rect, lavaPaint);
        }
        fire.draw(canvas, CRATER_X, CRATER_Y, 45f, 45f, 0.35f + 0.15f * (float) Math.sin(time * 5.3f) * (float) Math.sin(time * 1.7f));
        for (float[] p : sparks) {
            plain.setColor(0xFFFFA046);
            plain.setAlpha(Math.round(255f * MarvelLook.clamp01(p[4])));
            canvas.drawRect(p[0] - 0.5f, p[1] - 0.5f, p[0] + 0.6f, p[1] + 0.6f, plain);
        }
        float gx = gaze, gy = GROUND_Y - 10f, dx = gx - EYE_X, dy = gy - EYE_Y;
        for (float[] a : ash) {
            float u = MarvelLook.clamp01(((a[0] - EYE_X) * dx + (a[1] - EYE_Y) * dy) / (dx * dx + dy * dy));
            float d = (float) Math.hypot(a[0] - (EYE_X + dx * u), a[1] - (EYE_Y + dy * u));
            float inBeam = MarvelLook.clamp01(1f - d / (10f + 45f * u)), twinkle = 0.6f + 0.4f * (float) Math.sin(time * 2f + a[4] * 7f);
            if (a[5] > 0f) plain.setColor(MarvelLook.withAlpha(0xFFFF8C32, 0.45f + 0.45f * inBeam));
            else if (inBeam > 0.05f) plain.setColor(MarvelLook.withAlpha(0xFFFFD6A0, 0.25f + 0.6f * inBeam));
            else plain.setColor(MarvelLook.withAlpha(0xFF100B09, 0.55f * twinkle));
            canvas.drawRect(a[0], a[1], a[0] + a[4], a[1] + a[4], plain);
        }
        // The beam: a narrow cone from the Eye to the ground, brightest where it leaves it.
        float length = (float) Math.hypot(dx, dy), half = 35f + (float) Math.sin(time * 1.7f) * 3f, nx = -dy / length, ny = dx / length;
        shape.reset();
        shape.moveTo(EYE_X + nx * 4f, EYE_Y + ny * 4f);
        shape.lineTo(gx + nx * half, gy + ny * half);
        shape.lineTo(gx - nx * half, gy - ny * half);
        shape.lineTo(EYE_X - nx * 4f, EYE_Y - ny * 4f);
        shape.close();
        beam.setShader(new LinearGradient(EYE_X, EYE_Y, gx, gy, 0x80FFC880, 0x1AFFA850, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, beam);
        fire.draw(canvas, gx, gy, 130f, 60f, 0.32f);
        fire.draw(canvas, EYE_X, EYE_Y, 60f, 60f, 0.35f + 0.1f * (float) Math.sin(time * 3.3f) + flare * 0.6f);
        for (int i = 0; i < SLOT_X.length; i++) {
            MiddleEarthData.Entry show = MiddleEarthData.shows().get(i);
            boolean picked = i == chosen[MORDOR];
            for (Paint p : new Paint[]{kicker, name, play}) p.setTextAlign(Paint.Align.CENTER);
            kicker.setColor(picked ? 0xFFFFC98A : 0x6BF3E8CF);
            canvas.drawText(show.kicker.toUpperCase(Locale.ROOT), SLOT_X[i], 446f, kicker);
            name.setColor(picked ? 0xFFFFE6C2 : 0x6BF3E8CF);
            name.setTextSize(16f);
            canvas.drawText(show.title.name, SLOT_X[i], 468f, name);
            name.setTextSize(21f);
            String line = playLines.get(show.title.key);
            if (picked && line != null) canvas.drawText(line, SLOT_X[i], 488f, play);
        }
    }

    private void drawMoth(Canvas canvas) {
        if (moth == null) return;
        float speed = (float) Math.hypot(mothVX, mothVY);
        float flap = 0.3f + 0.7f * Math.abs((float) Math.sin(time * (speed > 40f ? 26f : 17f)));
        float height = MOTH_W * moth.getHeight() / moth.getWidth();
        canvas.save();
        canvas.translate(mothX, mothY);
        canvas.rotate(mothTurn);
        canvas.scale(flap, 1f);
        plain.setColor(0xFF000000);
        plain.setAlpha(70);
        canvas.drawOval(-MOTH_W * 0.4f, height * 0.1f, MOTH_W * 0.4f, height * 0.9f, plain);
        rect.set(-MOTH_W / 2f, -height / 2f, MOTH_W / 2f, height / 2f);
        canvas.drawBitmap(moth, null, rect, art);
        canvas.restore();
    }
}
