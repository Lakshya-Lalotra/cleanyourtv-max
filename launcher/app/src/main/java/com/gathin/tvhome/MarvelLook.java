package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;

/** What the Marvel screens share: comic-book red and ink, Anton's heavy poster lettering, and a printed halftone dot. */
final class MarvelLook {
    static final int RED = 0xFFED1D24, INK = 0xFF07070A;

    private MarvelLook() {
    }

    static Typeface anton(Context context) {
        try {
            return context.getResources().getFont(R.font.anton);
        } catch (RuntimeException e) {
            return Typeface.DEFAULT_BOLD;
        }
    }

    static Typeface outfit(Context context, int fontName) {
        try {
            Typeface face = UiFont.get(context, fontName);
            return face != null ? face : Typeface.DEFAULT;
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }

    /** Art needs no alpha, so it takes half the memory: this is a 1 GB TV. */
    static Bitmap art(Context context, int drawable) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inScaled = false;
        return BitmapFactory.decodeResource(context.getResources(), drawable, options);
    }

    /**
     * A screen of printed dots, one small tile repeated. Dots stay a crisp 6 dp on the panel however the canvas is scaled:
     * pass the scale the canvas will be drawn at (1 for pixels, density when it is scaled to dp).
     */
    static Shader halftone(float density, float canvasScale) {
        int size = Math.max(4, Math.round(6 * density));
        Bitmap tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0xFF000000);
        new Canvas(tile).drawCircle(size / 2f, size / 2f, size * 0.34f, dot);
        Shader shader = new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        Matrix matrix = new Matrix();
        matrix.setScale(1f / canvasScale, 1f / canvasScale);
        shader.setLocalMatrix(matrix);
        return shader;
    }

    /** Saturation, then brightness, with redLift pushing what is left towards comic-book red. */
    static ColorMatrixColorFilter tone(float saturation, float brightness, float redLift) {
        ColorMatrix result = new ColorMatrix();
        result.setSaturation(saturation);
        result.postConcat(new ColorMatrix(new float[]{
                brightness + redLift, 0, 0, 0, 0,
                0, brightness, 0, 0, 0,
                0, 0, brightness, 0, 0,
                0, 0, 0, 1, 0}));
        return new ColorMatrixColorFilter(result);
    }

    /** Letters that read on this colour: white while it has the contrast large bold type needs (3:1), else ink. */
    static int onColor(int color) {
        double contrastWithWhite = 1.05 / (luminance(color) + 0.05);
        return contrastWithWhite >= 3.0 ? 0xFFFFFFFF : INK;
    }

    private static double luminance(int color) {
        return 0.2126 * channel(color >> 16) + 0.7152 * channel(color >> 8) + 0.0722 * channel(color);
    }

    private static double channel(int value) {
        double c = (value & 0xFF) / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Opaque from a towards b. */
    static int mix(int a, int b, float t) {
        int red = Math.round(lerp((a >> 16) & 0xFF, (b >> 16) & 0xFF, t));
        int green = Math.round(lerp((a >> 8) & 0xFF, (b >> 8) & 0xFF, t));
        int blue = Math.round(lerp(a & 0xFF, b & 0xFF, t));
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    static int withAlpha(int color, float alpha) {
        return (Math.round(255f * alpha) << 24) | (color & 0xFFFFFF);
    }

    static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
