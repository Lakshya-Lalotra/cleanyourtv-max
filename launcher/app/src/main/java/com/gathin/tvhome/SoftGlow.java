package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/** Firelight: a patch of colour that is strongest in the middle and fades to nothing at its edge. */
final class SoftGlow {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    SoftGlow(int color) {
        paint.setShader(new RadialGradient(0f, 0f, 1f, new int[]{color, MarvelLook.withAlpha(color, 0.35f), MarvelLook.withAlpha(color, 0f)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
    }

    /** Centred on (x, y), reaching `wide` to each side and `tall` up and down, at this strength (0 to 1). */
    void draw(Canvas canvas, float x, float y, float wide, float tall, float strength) {
        if (strength <= 0f) return;
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(wide, tall);
        paint.setAlpha(Math.round(255f * Math.min(1f, strength)));
        canvas.drawCircle(0f, 0f, 1f, paint);
        canvas.restore();
    }
}
