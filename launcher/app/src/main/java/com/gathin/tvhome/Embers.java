package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Random;

/** Sparks drifting up the screen, for Middle-earth's fires. Drawn in dp on a 960 x 540 screen from a time in seconds. */
final class Embers {
    private static final float SCREEN_W = 960f, SCREEN_H = 540f;
    private final float[] x, speed, sway, size;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Embers(int count, long seed) {
        Random random = new Random(seed);
        x = new float[count];
        speed = new float[count];
        sway = new float[count];
        size = new float[count];
        for (int i = 0; i < count; i++) {
            x[i] = random.nextFloat() * SCREEN_W;
            speed[i] = 16f + random.nextFloat() * 38f;
            sway[i] = random.nextFloat() * 6.28f;
            size[i] = 1.1f + random.nextFloat() * 1.5f;
        }
    }

    void draw(Canvas canvas, float seconds, float strength) {
        if (strength <= 0f) return;
        for (int i = 0; i < x.length; i++) {
            float travelled = (seconds * speed[i] + sway[i] * 90f) % (SCREEN_H + 40f);
            float y = SCREEN_H + 20f - travelled;
            // Bright where it leaves the fire, gone before the top.
            float life = 1f - travelled / (SCREEN_H + 40f);
            paint.setColor(0xFFFF9A3A);
            paint.setAlpha(Math.round(210f * strength * life * life));
            canvas.drawCircle(x[i] + (float) Math.sin(seconds * 0.9f + sway[i]) * 16f, y, size[i], paint);
        }
    }
}
