package com.gathin.tvhome;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Each title's poster and backdrop, shipped in the app's assets so they appear at once with or without a network. Only what
 * is near the current stop is in memory: 32 posters of 180 x 270 (three paths can be on screen at once) and 3 backdrops of
 * 960 x 540, about 6 MB at 16 bits.
 */
final class MarvelArt {
    interface Listener {
        /** A poster has arrived: anything drawing posters should draw again. */
        void onPosterReady();

        void onBackdropReady(String titleKey, Bitmap bitmap);
    }

    private final AssetManager assets;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> posters = new LruCache<>(32);
    private final LruCache<String, Bitmap> backdrops = new LruCache<>(3);
    /** Posters already queued. Touched on the main thread only. */
    private final Set<String> coming = new HashSet<>();
    private volatile String backdropWanted = "";

    MarvelArt(Context context, Listener listener) {
        assets = context.getAssets();
        this.listener = listener;
    }

    /** The title's poster, or null while it is not in memory. */
    Bitmap poster(String titleKey) {
        return posters.get(titleKey);
    }

    /** The posters on screen around this stop, the nearest first. */
    void wantPosters(MarvelData.Order order, int around) {
        int last = order.stops.size() - 1;
        for (int i = Math.max(0, around); i <= Math.min(last, around + 6); i++) loadPoster(order.stops.get(i).title.key);
        for (int i = Math.min(last, around - 1); i >= Math.max(0, around - 2); i--) loadPoster(order.stops.get(i).title.key);
    }

    private void loadPoster(String key) {
        if (posters.get(key) != null || loader.isShutdown() || !coming.add(key)) return;
        loader.execute(() -> {
            Bitmap bitmap = decode("marvel/poster/" + key + ".jpg");
            main.post(() -> {
                coming.remove(key);
                if (bitmap == null) return;
                posters.put(key, bitmap);
                listener.onPosterReady();
            });
        });
    }

    /** Only the last title asked for arrives: walking quickly past titles never decodes the ones in between. */
    void wantBackdrop(String key) {
        backdropWanted = key;
        Bitmap known = backdrops.get(key);
        if (known != null) {
            listener.onBackdropReady(key, known);
            return;
        }
        if (loader.isShutdown()) return;
        loader.execute(() -> {
            if (!key.equals(backdropWanted)) return;
            Bitmap bitmap = decode("marvel/back/" + key + ".jpg");
            main.post(() -> {
                if (bitmap == null) return;
                backdrops.put(key, bitmap);
                if (key.equals(backdropWanted)) listener.onBackdropReady(key, bitmap);
            });
        });
    }

    void shutdown() {
        loader.shutdownNow();
    }

    private Bitmap decode(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream in = assets.open(path)) {
            return BitmapFactory.decodeStream(in, null, options);
        } catch (IOException e) {
            return null;   // the frame's plain colour stands in
        }
    }
}
