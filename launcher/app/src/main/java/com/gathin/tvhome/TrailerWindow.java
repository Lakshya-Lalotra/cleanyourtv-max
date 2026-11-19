package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * One muted YouTube trailer with no controls, in YouTube's own player, which is the way YouTube allows it (a raw video
 * stream is not). There is one player, in the Marvel screen's own process: 40 to 100 MB that Home must not carry.
 * The title's own picture shows at once. The trailer starts after a rest on the title, so walking the path starts no videos,
 * and the picture only leaves once the player reports it is playing, so a stuck player never looks broken.
 * A title with no trailer on Marvel's own channels just keeps its picture.
 */
final class TrailerWindow extends FrameLayout {
    private static final String TAG = "MarvelTrailer";
    private static final long WATCHDOG_MS = 15000;
    private static final int PLAYING = 1, ENDED = 0;
    /** Seconds into each trailer to start: most fade up from black, and YouTube's own title sits over the opening anyway. */
    private static final int START_S = 5;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ImageView thumb;
    private final String base;
    private WebView web;
    private String videoId = "";
    private int retries;
    private long loadStarted;

    private final Runnable rest = this::startTrailer;
    private final Runnable watchdog = this::onWatchdog;

    TrailerWindow(Context context) {
        super(context);
        base = "https://" + context.getPackageName();
        setBackgroundColor(0xFF0B0C10);
        thumb = new ImageView(context);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(thumb, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    /** The picture that shows while there is no trailer playing. */
    void setPicture(Bitmap bitmap) {
        thumb.setImageBitmap(bitmap);
    }

    /** On a new title: its picture now, the trailer after restMs on it, if it has one. */
    void show(String id, long restMs) {
        videoId = id;
        retries = 0;
        main.removeCallbacks(rest);
        main.removeCallbacks(watchdog);
        run("stopTitle()");
        thumb.animate().cancel();
        thumb.setAlpha(1f);
        thumb.setVisibility(VISIBLE);
        if (!id.isEmpty()) main.postDelayed(rest, restMs);
    }

    /** The player goes, and its memory with it: before JioHotstar opens, and whenever the screen is not showing. */
    void release() {
        main.removeCallbacks(rest);
        main.removeCallbacks(watchdog);
        videoId = "";
        destroyPlayer();
        thumb.animate().cancel();
        thumb.setAlpha(1f);
        thumb.setVisibility(VISIBLE);
    }

    void shutdown() {
        release();
    }

    private void run(String script) {
        if (web != null) web.evaluateJavascript(script, null);
    }

    private void startTrailer() {
        loadStarted = System.currentTimeMillis();
        if (web == null) {
            createPlayer(videoId);
        } else {
            run("loadTitle('" + videoId + "')");
        }
        main.removeCallbacks(watchdog);
        main.postDelayed(watchdog, WATCHDOG_MS);
    }

    /** No picture after a good wait: try once more, then let the thumbnail stand. The next title tries afresh. */
    private void onWatchdog() {
        if (retries == 0) {
            retries = 1;
            Log.w(TAG, "no picture yet, trying " + videoId + " again");
            destroyPlayer();
            startTrailer();
        } else {
            Log.w(TAG, "giving up on " + videoId);
            destroyPlayer();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createPlayer(String id) {
        web = new WebView(getContext());
        web.setBackgroundColor(0xFF000000);
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "TT");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;   // the player stays where it is; nothing here navigates away
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.w(TAG, "the player's renderer went, crashed=" + detail.didCrash());
                main.removeCallbacks(watchdog);
                if (view == web) destroyPlayer();
                return true;
            }
        });
        // Behind the thumbnail, which is the last child: the thumbnail covers the player only until it plays.
        addView(web, 0, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The base URL is the identity YouTube's terms ask of an app: its Referer, https plus the app id.
        web.loadDataWithBaseURL(base + "/", page(id), "text/html", "utf-8", null);
    }

    private void destroyPlayer() {
        if (web == null) return;
        WebView gone = web;
        web = null;
        removeView(gone);
        gone.stopLoading();
        gone.destroy();
    }

    private String page(String id) {
        return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style></head>"
                + "<body><div id=p></div><script src='https://www.youtube.com/iframe_api'></script><script>\n"
                + "var player, ready = false, first = '" + id + "', want = first;\n"
                + "function onYouTubeIframeAPIReady(){ player = new YT.Player('p', { width:'100%', height:'100%', videoId:first,\n"
                + " playerVars:{ autoplay:1, mute:1, controls:0, disablekb:1, fs:0, rel:0, playsinline:1, iv_load_policy:3, cc_load_policy:0, start:" + START_S + ", origin:'" + base + "' },\n"
                + " events:{\n"
                + "  onReady:function(e){ ready = true; e.target.mute(); if (want === first) e.target.playVideo(); else if (want) e.target.loadVideoById(want, " + START_S + "); else e.target.stopVideo(); },\n"
                + "  onStateChange:function(e){ if (e.data === 1 && !want) { e.target.stopVideo(); return; } if (e.data === 1) noCaptions(e.target); TT.state(want, e.data); },\n"
                + "  onError:function(e){ TT.error(want, e.data); },\n"
                + "  onAutoplayBlocked:function(){ TT.error(want, -1); }\n"
                + " } }); }\n"
                + "function noCaptions(p){ try { p.unloadModule('captions'); p.unloadModule('cc'); } catch (x) {} }\n"
                + "function loadTitle(id){ want = id; if (ready) { player.mute(); player.loadVideoById(id, " + START_S + "); } }\n"
                + "function stopTitle(){ want = ''; if (ready) player.stopVideo(); }\n"
                + "</script></body></html>";
    }

    private void onState(String id, int state) {
        // A report from the title just left. One with no id is the player starting after its title was left, and must never
        // uncover the player on a title that has no trailer.
        if (id.isEmpty() || !id.equals(videoId)) return;
        if (state == PLAYING) {
            main.removeCallbacks(watchdog);
            Log.i(TAG, "playing " + id + " after " + (System.currentTimeMillis() - loadStarted) + " ms");
            thumb.animate().alpha(0f).setDuration(240).withEndAction(() -> thumb.setVisibility(INVISIBLE)).start();
        } else if (state == ENDED) {
            thumb.animate().cancel();
            thumb.setVisibility(VISIBLE);
            thumb.animate().alpha(1f).setDuration(240).start();
        }
    }

    private void onError(String id, int code) {
        if (!id.equals(videoId)) return;
        Log.w(TAG, "player error " + code + " on " + id);
        main.removeCallbacks(watchdog);
    }

    private final class Bridge {
        @JavascriptInterface
        public void state(String id, int state) {
            main.post(() -> onState(id, state));
        }

        @JavascriptInterface
        public void error(String id, int code) {
            main.post(() -> onError(id, code));
        }
    }
}
