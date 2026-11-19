package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Middle-earth: an opening through Gollum's eye to the name, then three places one above the other. Bag End, whose
 * lanterns lead on; the beacons of Gondor, a fire for each film; and Mordor, where the Eye looks on the series and the
 * anime. Left and Right choose, Down and Up go between the places, OK plays what is chosen in whichever app JustWatch lists
 * it on now, Back goes up a place and then out. Like the other worlds it has a process of its own (":middleearth" in the
 * manifest) that ends when it is left for good.
 */
public final class MiddleEarthActivity extends Activity implements MiddleEarthGateView.Listener {
    private static final long LOOKUP_REST_MS = 600;
    private static final String NONE = "";
    /** Middle-earth screens alive in this process: created and not yet destroyed. Main thread only. */
    private static int open;

    private final Handler main = new Handler(Looper.getMainLooper());
    /** What JustWatch said for each title looked up this visit: an offer, or NONE for nothing to stream. */
    private final Map<String, Object> found = new HashMap<>();
    private MiddleEarthGateView gate;
    private MiddleEarthSceneView scenes;
    private MediaPlayer music;
    private boolean inScenes, leftForTitle, started;
    private String openWhenFound = "";
    private volatile boolean over;

    private final Runnable lookup = this::lookUpChosen;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        open++;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        scenes = new MiddleEarthSceneView(this);
        scenes.setAlpha(0f);
        root.addView(scenes, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        gate = new MiddleEarthGateView(this, this);
        root.addView(gate, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        for (MiddleEarthData.Entry e : MiddleEarthData.shows()) {
            if (e.title.soon()) scenes.setPlayLine(e.title.key, "Coming soon");
        }
        new Thread(() -> {
            Bitmap[] p = {decode("bagend.jpg", Bitmap.Config.RGB_565), decode("bagend_lit.jpg", Bitmap.Config.RGB_565),
                    decode("beacons.jpg", Bitmap.Config.RGB_565), decode("beacons_lit.jpg", Bitmap.Config.RGB_565),
                    decode("mordor.jpg", Bitmap.Config.RGB_565), decode("lava.webp", Bitmap.Config.ARGB_8888),
                    decode("moth.webp", Bitmap.Config.ARGB_8888), decode("logo.webp", Bitmap.Config.ARGB_8888)};
            main.post(() -> {
                if (!over) scenes.setPictures(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]);
            });
        }, "middleearth-art").start();
    }

    private Bitmap decode(String name, Bitmap.Config config) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = config;
        try (InputStream in = getAssets().open("middleearth/" + name)) {
            return BitmapFactory.decodeStream(in, null, options);
        } catch (IOException | OutOfMemoryError e) {
            return null;
        }
    }

    // ---- the opening ------------------------------------------------------------------------------

    /** The theme, if one has been put in the assets: from the Ring's letters on, round and round until the world is left. */
    @Override
    public void onMusicCue() {
        if (music != null) return;
        try (AssetFileDescriptor file = getAssets().openFd("middleearth/theme.ogg")) {
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            player.setLooping(true);
            player.prepare();
            if (started) player.start();
            music = player;
        } catch (IOException | RuntimeException e) {
            music = null;   // no theme yet: the world is silent
        }
    }

    @Override
    public void onGateDone() {
        if (inScenes) return;
        inScenes = true;
        scenes.start();
        scenes.animate().alpha(1f).setDuration(900).start();
        gate.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            gate.finish();
            gate.setVisibility(View.GONE);
        }).start();
    }

    // ---- keys -------------------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean ours = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_LEFT
                || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_BACK;
        if (!ours) return super.dispatchKeyEvent(event);
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        boolean moving = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (event.getRepeatCount() > 0 && !moving) return true;   // holding only walks along a row
        if (!inScenes) {
            if (key == KeyEvent.KEYCODE_BACK) leave();
            else gate.skip();
            return true;
        }
        int place = scenes.scene();
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                scenes.choose(scenes.chosen() + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1));
                chosenChanged();
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                goTo(place + 1);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                goTo(place - 1);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                scenes.flare();
                if (place == MiddleEarthSceneView.BAG_END) {
                    // The lanterns lead on: the Hobbit's beacons, the Lord of the Rings', or Mordor.
                    int lantern = scenes.chosen();
                    if (lantern < 2) scenes.chooseIn(MiddleEarthSceneView.BEACONS, lantern == 0 ? 0 : MiddleEarthData.FIRST_RING_FILM);
                    main.postDelayed(() -> goTo(lantern < 2 ? MiddleEarthSceneView.BEACONS : MiddleEarthSceneView.MORDOR), 350);
                } else {
                    play();
                }
                break;
            default:
                if (place == MiddleEarthSceneView.BAG_END) leave();
                else goTo(place - 1);
                break;
        }
        return true;
    }

    private void goTo(int place) {
        if (place < MiddleEarthSceneView.BAG_END || place > MiddleEarthSceneView.MORDOR) return;
        scenes.go(place);
        chosenChanged();
    }

    private void leave() {
        finish();
        overridePendingTransition(0, 0);
    }

    // ---- playing ----------------------------------------------------------------------------------

    /** The title chosen now, or null at Bag End. */
    private WizardData.Title chosenTitle() {
        int place = scenes.scene();
        if (place == MiddleEarthSceneView.BEACONS) return MiddleEarthData.films().get(scenes.chosen()).title;
        if (place == MiddleEarthSceneView.MORDOR) return MiddleEarthData.shows().get(scenes.chosen()).title;
        return null;
    }

    private void chosenChanged() {
        openWhenFound = "";
        main.removeCallbacks(lookup);
        if (chosenTitle() != null) main.postDelayed(lookup, LOOKUP_REST_MS);
    }

    /** Where the chosen title streams right now, asked once a visit. */
    private void lookUpChosen() {
        WizardData.Title title = chosenTitle();
        if (title == null || title.soon() || found.containsKey(title.key)) return;
        found.put(title.key, NONE);
        scenes.setPlayLine(title.key, "Finding where it plays…");
        String country = getString(R.string.wheel_country);
        new Thread(() -> {
            StreamSearch.Offer offer = null;
            boolean reached = true;
            try {
                offer = WizardData.pick(title, StreamSearch.justWatch(title.lookupName, country, 5, title.isSeries() ? 0 : title.year));
            } catch (Exception e) {
                reached = false;
            }
            StreamSearch.Offer answer = offer;
            boolean asked = reached;
            main.post(() -> {
                if (over) return;
                if (!asked) {
                    found.remove(title.key);   // ask again next time it is chosen
                    scenes.setPlayLine(title.key, "Couldn't check where it plays. Is the TV online?");
                    return;
                }
                found.put(title.key, answer == null ? NONE : answer);
                scenes.setPlayLine(title.key, answer == null ? "Not streaming here right now" : "OK to play on " + answer.platform);
                if (answer != null && title.key.equals(openWhenFound)) open(answer);
            });
        }, "middleearth-lookup").start();
    }

    private void play() {
        WizardData.Title title = chosenTitle();
        if (title == null || title.soon()) return;
        Object known = found.get(title.key);
        if (known instanceof StreamSearch.Offer) {
            // The fire flares first, then the film opens.
            main.postDelayed(() -> open((StreamSearch.Offer) known), 450);
        } else {
            openWhenFound = title.key;
            if (known == null) {
                main.removeCallbacks(lookup);
                lookUpChosen();
            }
        }
    }

    private void open(StreamSearch.Offer offer) {
        openWhenFound = "";
        leftForTitle = TitleOpener.open(this, offer.pkg, offer.url);
    }

    // ---- life -------------------------------------------------------------------------------------

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        leftForTitle = false;
        if (inScenes) scenes.start();
        else gate.start();
        if (music != null && !music.isPlaying()) music.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        scenes.stop();
        if (music != null && music.isPlaying()) music.pause();   // a film has the sound now
        // Home, another app or the TV going to sleep: this screen is over. Only a film it started comes back to it.
        if (!leftForTitle && !isFinishing() && !isChangingConfigurations()) finish();
    }

    @Override
    protected void onDestroy() {
        over = true;
        main.removeCallbacksAndMessages(null);
        gate.finish();
        scenes.stop();
        if (music != null) {
            music.release();
            music = null;
        }
        boolean left = isFinishing();
        open--;
        super.onDestroy();
        // The process is only for this screen, and goes once no such screen is open.
        if (left) new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (open == 0) android.os.Process.killProcess(android.os.Process.myPid());
        }, 500);
    }
}
