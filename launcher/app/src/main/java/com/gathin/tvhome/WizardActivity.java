package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Wizarding World: its name over Hogwarts, a choice of the films or the series, then a page of them to fly over. Hedwig is the pointer: holding an arrow flies her that way, the page
 * moves under her at its top and bottom, and she lands on the picture she is over. OK plays that film in whichever app
 * JustWatch lists it on now. Its pictures and Hedwig's frames are a good few megabytes, so like the Marvel screen it has a
 * process of its own (":wizard" in the manifest) that ends when the screen is left for good.
 */
public final class WizardActivity extends Activity implements Choreographer.FrameCallback {
    private static final float SCREEN_W = 960f, SCREEN_H = 540f;
    /** dp a second, and a second squared: a held arrow reaches full speed in about a third of a second. */
    private static final float TOP_SPEED = 520f, PUSH = 1700f, TAP = 200f, DRAG = 5.5f;
    /** Past these lines the page moves instead of her, while it has further to go. */
    private static final float EDGE_TOP = 120f, EDGE_BOTTOM = 420f;
    private static final float START_X = 250f, START_Y = 210f;
    private static final long LOOKUP_REST_MS = 600;
    private static final String NONE = "";
    /** Wizarding World screens alive in this process: created and not yet destroyed. Main thread only. */
    private static int open;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> pictures = new HashMap<>();
    /** What JustWatch said for each title looked up this visit: an offer, or NONE for nothing to stream. */
    private final Map<String, Object> found = new HashMap<>();
    private float density;
    private List<WizardData.Title> titles;
    private WizardGateView gate;
    private WizardPageView page;
    private HedwigView hedwig;
    /** The theme, from the moment the world opens, round and round until it is left. */
    private android.media.MediaPlayer music;

    private float x = START_X, y = START_Y, speedX, speedY, scroll, phase, bank, stillFor;
    private int heldX, heldY, target = -1;
    private boolean perched, running, leftForTitle, inPage;
    /** On the way in, where Hedwig is flying to: the card that is chosen. */
    private float glideX, glideY;
    private long lastFrame;
    private String openWhenFound = "";
    private volatile boolean over;

    private final Runnable lookup = this::lookUpTarget;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        open++;
        try {
            music = android.media.MediaPlayer.create(this, R.raw.hedwig_theme);
            if (music != null) {
                music.setLooping(true);
                music.start();
            }
        } catch (RuntimeException e) {
            music = null;   // the world opens without it
        }
        density = getResources().getDisplayMetrics().density;
        titles = WizardData.films();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(WizardPageView.GOLD);   // the colour the wheel's disc grew to
        page = new WizardPageView(this);
        page.setArt(pictures::get);
        page.setTitles(titles);
        page.setAlpha(0f);
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        gate = new WizardGateView(this, this::onGateReady);
        gate.setAlpha(0f);
        root.addView(gate, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hedwig = new HedwigView(this);
        hedwig.setAlpha(0f);
        root.addView(hedwig, new FrameLayout.LayoutParams(Math.round(HedwigView.WIDTH * density), Math.round(HedwigView.HEIGHT * density)));
        setContentView(root);
        gate.animate().alpha(1f).setDuration(420).start();
        gate.start();
        for (WizardData.Title title : WizardData.all()) {
            if (title.soon()) page.setPlayLine(title.key, "Coming soon");
        }
        loadPictures();
    }

    // ---- the way in -------------------------------------------------------------------------------

    private void onGateReady() {
        x = -60f;
        y = 150f;
        hedwig.animate().alpha(1f).setDuration(240).start();
        glideToCard();
    }

    private void glideToCard() {
        glideX = gate.perchX(gate.selected());
        glideY = gate.perchY();
        wake();
    }

    private boolean onGateKey(KeyEvent event) {
        int key = event.getKeyCode();
        boolean ours = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_LEFT
                || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_BACK;
        if (!ours) return super.dispatchKeyEvent(event);
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return true;
        if (key == KeyEvent.KEYCODE_BACK) {
            finish();
            overridePendingTransition(0, 0);
        } else if (!gate.ready()) {
            gate.skip();
        } else if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            gate.select(gate.selected() + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1));
            glideToCard();
        } else if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            enterPage(gate.selected());
        }
        return true;
    }

    private void enterPage(int which) {
        titles = which == 0 ? WizardData.films() : WizardData.series();
        page.setTitles(titles);
        scroll = 0f;
        page.setScroll(0f);
        target = -1;
        speedX = speedY = 0f;
        heldX = heldY = 0;
        inPage = true;
        gate.animate().alpha(0f).setDuration(260).withEndAction(() -> {
            gate.stop();
            gate.setVisibility(android.view.View.GONE);
        }).start();
        page.animate().alpha(1f).setDuration(320).start();
        wake();
    }

    private void backToGate() {
        inPage = false;
        main.removeCallbacks(lookup);
        openWhenFound = "";
        speedX = speedY = 0f;
        heldX = heldY = 0;
        page.animate().alpha(0f).setDuration(220).start();
        gate.animate().cancel();
        gate.setVisibility(android.view.View.VISIBLE);
        gate.start();
        gate.animate().alpha(1f).setDuration(260).start();
        glideToCard();
    }

    private void loadPictures() {
        List<WizardData.Title> all = WizardData.all();
        new Thread(() -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            for (WizardData.Title title : all) {
                if (over) return;
                try (InputStream in = getAssets().open("wizard/" + title.key + ".jpg")) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
                    if (bitmap == null) continue;
                    main.post(() -> {
                        pictures.put(title.key, bitmap);
                        page.invalidate();
                        if (title == all.get(0)) gate.setFilmPicture(bitmap);
                        // The sky behind the page takes the picture of the film she is already on.
                        if (inPage && target >= 0 && titles.get(target).key.equals(title.key)) {
                            page.setTarget(-1);
                            page.setTarget(target);
                        }
                    });
                } catch (IOException ignored) {
                    // no picture for this one (the series): its plain panel stands in
                }
            }
        }, "wizard-art").start();
    }

    // ---- keys -------------------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!inPage) return onGateKey(event);
        int key = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int dx = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        int dy = key == KeyEvent.KEYCODE_DPAD_UP ? -1 : key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        if (dx != 0 || dy != 0) {
            if (down && event.getRepeatCount() == 0) {
                // A single press is a hop of its own; holding the arrow keeps her flying.
                speedX += dx * TAP;
                speedY += dy * TAP;
            }
            if (dx != 0) heldX = down ? dx : heldX == dx ? 0 : heldX;
            if (dy != 0) heldY = down ? dy : heldY == dy ? 0 : heldY;
            wake();
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            if (down && event.getRepeatCount() == 0) play();
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK) {
            if (down && event.getRepeatCount() == 0) backToGate();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // ---- flying -----------------------------------------------------------------------------------

    private void wake() {
        perched = false;
        stillFor = 0f;
        if (running) return;
        running = true;
        lastFrame = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void doFrame(long nanos) {
        if (!running) return;
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (nanos - lastFrame) / 1e9f);
        lastFrame = nanos;
        if (!inPage) {
            // On the way in she is not steered: she flies to the card that is chosen and lands on it.
            float dx = glideX - x, dy = glideY - y, k = Math.min(1f, dt * 5f);
            x += dx * k;
            y += dy * k;
            bank += (Math.max(-1f, Math.min(1f, dx / 200f)) * 20f - bank) * Math.min(1f, dt * 10f);
            if (Math.hypot(dx, dy) < 1f) {
                x = glideX;
                y = glideY;
                perched = true;
            } else {
                phase += dt * 15f;
            }
            place();
            if (perched) running = false;
            else Choreographer.getInstance().postFrameCallback(this);
            return;
        }

        speedX += heldX * PUSH * dt;
        speedY += heldY * PUSH * dt;
        float ease = (float) Math.exp(-DRAG * dt);
        if (heldX == 0) speedX *= ease;
        if (heldY == 0) speedY *= ease;
        float speed = (float) Math.hypot(speedX, speedY);
        if (speed > TOP_SPEED) {
            speedX *= TOP_SPEED / speed;
            speedY *= TOP_SPEED / speed;
            speed = TOP_SPEED;
        }
        x = Math.max(28f, Math.min(SCREEN_W - 28f, x + speedX * dt));
        float nextY = y + speedY * dt;
        if (nextY < EDGE_TOP) {
            float moved = Math.min(EDGE_TOP - nextY, scroll);
            scroll -= moved;
            nextY += moved;
        } else if (nextY > EDGE_BOTTOM) {
            float moved = Math.min(nextY - EDGE_BOTTOM, page.maxScroll() - scroll);
            scroll += moved;
            nextY -= moved;
        }
        y = Math.max(104f, Math.min(SCREEN_H - 16f, nextY));   // she stands about 95 dp above her feet
        page.setScroll(scroll);
        setTarget(page.hit(x, y));

        boolean flying = heldX != 0 || heldY != 0 || speed > 14f;
        if (flying) {
            stillFor = 0f;
            phase += dt * 15f;
        } else {
            stillFor += dt;
            speedX = speedY = 0f;
            if (target >= 0) {
                // Down onto the top edge of the picture she is over.
                float perch = page.perchY(target);
                y += (perch - y) * Math.min(1f, dt * 9f);
                if (Math.abs(perch - y) < 0.8f && stillFor > 0.35f) {
                    y = perch;
                    perched = true;
                }
            }
            if (!perched) phase += dt * 10f;
        }
        bank += (speedX / TOP_SPEED * 20f - bank) * Math.min(1f, dt * 10f);
        place();
        if (perched) {
            running = false;
        } else {
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private void place() {
        hedwig.setTranslationX((x - HedwigView.FOOT_X) * density);
        hedwig.setTranslationY((y - HedwigView.FOOT_Y) * density);
        hedwig.setFlying(!perched);
        hedwig.setHeading(inPage ? speedX : glideX - x);
    }

    private void setTarget(int index) {
        if (index == target) return;
        target = index;
        page.setTarget(index);
        openWhenFound = "";
        main.removeCallbacks(lookup);
        if (index >= 0) main.postDelayed(lookup, LOOKUP_REST_MS);
    }

    // ---- playing ----------------------------------------------------------------------------------

    /** Where the title she rests on streams right now, asked once a visit. */
    private void lookUpTarget() {
        if (target < 0) return;
        WizardData.Title title = titles.get(target);
        if (title.soon() || found.containsKey(title.key)) return;
        found.put(title.key, NONE);
        page.setPlayLine(title.key, "Finding where it plays…");
        String country = getString(R.string.wheel_country);
        new Thread(() -> {
            StreamSearch.Offer offer = null;
            boolean reached = true;
            try {
                offer = WizardData.pick(title, StreamSearch.justWatch(title.lookupName, country, 4, title.year));
            } catch (Exception e) {
                reached = false;
            }
            StreamSearch.Offer answer = offer;
            boolean asked = reached;
            main.post(() -> {
                if (over) return;
                if (!asked) {
                    found.remove(title.key);   // ask again next time she lands here
                    page.setPlayLine(title.key, "Couldn't check where it plays. Is the TV online?");
                    return;
                }
                found.put(title.key, answer == null ? NONE : answer);
                page.setPlayLine(title.key, answer == null ? "Not streaming here right now" : "Press OK to play on " + answer.platform);
                if (answer != null && title.key.equals(openWhenFound)) open(answer);
            });
        }, "wizard-lookup").start();
    }

    private void play() {
        if (target < 0) return;
        WizardData.Title title = titles.get(target);
        if (title.soon()) return;
        Object known = found.get(title.key);
        if (known instanceof StreamSearch.Offer) {
            open((StreamSearch.Offer) known);
        } else if (known == null) {
            // OK before the answer is in: ask now, and open it when it comes.
            openWhenFound = title.key;
            main.removeCallbacks(lookup);
            lookUpTarget();
        } else {
            openWhenFound = title.key;
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
        leftForTitle = false;
        heldX = heldY = 0;
        if (!inPage) gate.start();
        hedwig.start();
        if (music != null && !music.isPlaying()) music.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        heldX = heldY = 0;
        gate.stop();
        hedwig.stop();
        if (music != null && music.isPlaying()) music.pause();   // a film has the sound now
        // Home, another app or the TV going to sleep: this screen is over. Only a film it started comes back to it.
        if (!leftForTitle && !isFinishing() && !isChangingConfigurations()) finish();
    }

    @Override
    protected void onDestroy() {
        over = true;
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        main.removeCallbacksAndMessages(null);
        gate.stop();
        hedwig.stop();
        page.stop();
        if (music != null) {
            music.release();
            music = null;
        }
        boolean left = isFinishing();
        open--;
        super.onDestroy();
        // As the Marvel screen does: the process is only for this screen, and goes once no such screen is open.
        if (left) new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (open == 0) android.os.Process.killProcess(android.os.Process.myPid());
        }, 500);
    }
}
