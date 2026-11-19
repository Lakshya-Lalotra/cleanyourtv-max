package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Marvel world: the logo over flipping pages, then pick a path (story order or Marvel's Phases) from two comic panels,
 * then walk it title by title with a trailer playing and OK starting the title in JioHotstar. Every phase has its own colour,
 * and the screen takes it on as you walk into that phase. It runs in its own process (":marvel" in the manifest) so the video
 * player's memory never sits in Home, and a player that dies takes nothing with it. The process ends when the screen is left
 * for good.
 */
public final class MarvelActivity extends Activity {
    private static final String STATE_SCREEN = "screen", STATE_ORDER = "order", STATE_INDEX = "index";
    private static final int CHOOSER = 0, PATH = 1, TABS = 2;
    private static final long REST_MS = 1200, FIRST_REST_MS = 1600;
    /** How far apart the universes' paths sit, in dp: the one below shows the tops of its posters at the screen's foot. */
    private static final float LANE_GAP = 160f;
    /** How far a path that has been walked past moves up: its titles' names end behind the trailer, not under its caption. */
    private static final float LANE_UP = 192f;
    private static final long SLIDE_MS = 320;
    /** Where the two anchor cards come to rest, in dp across the screen. */
    private static final float NWH_X = 520f, DOOM_X = 800f;
    /** The MCU's cards that each other universe's path runs into: walking onto one there goes up to the MCU's path. */
    private static final String[][] BRIDGES = {{}, {"dw", "doom"}, {"nwh", "doom"}, {"doom"}};
    /** Marvel screens alive in this process: created and not yet destroyed. Main thread only. */
    private static int open;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, MarvelLookup.Link> links = new HashMap<>();
    private final Map<String, Boolean> lookedUp = new HashMap<>();
    private float density;

    private FrameLayout pathLayer;
    private MarvelIntroView intro;
    private MarvelChooserView chooser;
    /** The backdrop, and over it one path per universe, stacked: the one being walked in the main place. */
    private MarvelPathView scene;
    private final MarvelPathView[] lanes = new MarvelPathView[MarvelData.UNIVERSES.length];
    private final MarvelData.Order[] orders = new MarvelData.Order[MarvelData.UNIVERSES.length];
    private FrameLayout stack;
    private ValueAnimator slide;
    /** Which path is in the main place: a fraction while the stack slides. */
    private float stackAt;
    /** On Doomsday: how far the other universes' lines have come in across the screen, 0 to 1. */
    private float merging;
    private ValueAnimator merge;
    /** Where each path goes on from when you come back down off a shared card. */
    private final int[] bridgeExit = new int[BRIDGES.length];
    private LinearLayout hero;
    private TextView kicker, title, meta, about, playLabel;
    private LinearLayout play;
    private ImageView playIcon;
    private ShapeDrawable playShape;
    private GradientDrawable playFill;
    private TextView[] tabViews;
    private int universeIndex;
    private TrailerWindow trailer;
    private View rim, rimOffset;
    private LinearLayout caption;
    private TextView captionFrom, captionMuted;
    private Typeface anton;

    private int screen = CHOOSER;
    private MarvelData.Order order;
    private boolean busy;
    /** Off to JioHotstar: stay in the background and come back to the same title. Any other way out ends this screen. */
    private boolean leftForTitle;
    private boolean trailerDue;

    private MarvelArt art;
    /** The phase colour on screen, and where it is heading. */
    private int accent = MarvelLook.RED, accentGoal = MarvelLook.RED;
    private ValueAnimator accentMove;

    private final Runnable lookup = this::lookUpCurrent;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        open++;
        getWindow().setFormat(PixelFormat.RGBA_8888);
        density = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        // The same red as the window and the disc Home grew: Home, this window and the intro meet with no seam.
        root.setBackgroundColor(MarvelLook.RED);
        anton = MarvelLook.anton(this);
        art = new MarvelArt(this, new MarvelArt.Listener() {
            @Override
            public void onPosterReady() {
                for (MarvelPathView lane : lanes) lane.invalidate();
            }

            @Override
            public void onBackdropReady(String titleKey, android.graphics.Bitmap bitmap) {
                if (screen == CHOOSER || !current().title.key.equals(titleKey)) return;
                scene.setBackdrop(titleKey, bitmap);
                trailer.setPicture(bitmap);
            }
        });
        if (saved == null) {
            // Its pages start loading before anything else here does: they are what ends the wait on plain red.
            intro = new MarvelIntroView(this, new MarvelIntroView.Listener() {
                @Override
                public void onIntroReveal() {
                    chooser.reveal();
                }

                @Override
                public void onIntroDone() {
                    root.removeView(intro);
                    intro = null;
                    chooser.showLogo();
                }
            });
            intro.start();
        }
        buildChooser(root);
        buildPath(root);   // over the chooser, so it fades in on top of it and away again
        if (intro != null) root.addView(intro, full());
        setContentView(root);

        order = MarvelData.order(saved == null ? MarvelData.CHRONOLOGICAL : saved.getString(STATE_ORDER));
        int savedScreen = saved == null ? CHOOSER : saved.getInt(STATE_SCREEN, CHOOSER);
        if (savedScreen == CHOOSER) {
            if (intro == null) {
                chooser.showArrived();
                chooser.showLogo();
            }
            art.wantPosters(MarvelData.chronological(), 0);
        } else {
            screen = PATH;
            chooser.setVisibility(View.GONE);
            pathLayer.setVisibility(View.VISIBLE);
            universeIndex = Math.max(0, java.util.Arrays.asList(MarvelData.UNIVERSES).indexOf(order.key));
            setOrders(universeIndex == 0 ? order : MarvelData.chronological());
            lane().setOrder(order, saved.getInt(STATE_INDEX, 0));
            showLanes(false);
            accent = accentGoal = order.chapters.get(current().chapter).color;
            applyAccent();
            showTitle(false);
            trailerDue = true;
        }
    }

    // ---- building ---------------------------------------------------------------------------------

    private void buildChooser(FrameLayout root) {
        chooser = new MarvelChooserView(this, MarvelData.chronological().stops.get(0).title.shortName,
                MarvelData.phases().stops.get(0).title.shortName);
        root.addView(chooser, full());
    }

    private void buildPath(FrameLayout root) {
        pathLayer = new FrameLayout(this);
        pathLayer.setVisibility(View.GONE);
        scene = new MarvelPathView(this, false);
        pathLayer.addView(scene, full());
        // Under the title and the trailer, so a path that has moved up goes behind them.
        stack = new FrameLayout(this);
        for (int i = 0; i < lanes.length; i++) {
            lanes[i] = new MarvelPathView(this, true);
            lanes[i].setArt(key -> art.poster(key));
            lanes[i].setBridges(BRIDGES[i]);
            if (i == 0) {
                lanes[i].setBig("doom");
                lanes[i].setAnchors(new String[]{"nwh", "doom"}, new float[]{NWH_X, DOOM_X});
            }
            stack.addView(lanes[i], at(0, Math.round(MarvelPathView.LANE_TOP), 960, Math.round(MarvelPathView.LANE_HEIGHT)));
        }
        pathLayer.addView(stack, full());

        ImageView brand = new ImageView(this);
        brand.setImageResource(R.drawable.logo_marvel);
        brand.setScaleType(ImageView.ScaleType.FIT_XY);
        pathLayer.addView(brand, at(Math.round(MarvelIntroView.LOGO_X), Math.round(MarvelIntroView.LOGO_Y),
                Math.round(MarvelIntroView.LOGO_H * MarvelIntroView.LOGO_RATIO), Math.round(MarvelIntroView.LOGO_H)));

        LinearLayout tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] tabNames = {"CHRONOLOGICAL", "PHASES"};
        tabViews = new TextView[tabNames.length];
        for (int i = 0; i < tabNames.length; i++) {
            tabViews[i] = tab(tabNames[i]);
            if (i == 0) {
                tabsRow.addView(tabViews[i]);
            } else {
                LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
                gap.setMarginStart(dp(4));
                tabsRow.addView(tabViews[i], gap);
            }
        }
        FrameLayout.LayoutParams tabsAt = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32), Gravity.END);
        tabsAt.setMarginEnd(dp(40));
        tabsAt.topMargin = dp(12);
        pathLayer.addView(tabsRow, tabsAt);

        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        // A red caption box, as on a comic's panel.
        kicker = comic(13, 0xFFFFFFFF);
        kicker.setBackgroundColor(MarvelLook.RED);
        kicker.setPadding(dp(8), dp(3), dp(8), dp(3));
        title = comic(44, 0xFFFFFFFF);
        title.setSingleLine(false);
        title.setMaxLines(2);
        title.setPadding(0, dp(8), 0, 0);
        title.setLineSpacing(0f, 0.86f);
        meta = text(14, 0xCCFFFFFF, R.string.font_regular);
        meta.setPadding(0, dp(4), 0, 0);
        about = text(15, 0xE6FFFFFF, R.string.font_regular);
        about.setSingleLine(false);
        about.setMaxLines(3);
        about.setPadding(0, dp(8), 0, 0);
        about.setLineSpacing(dp(2), 1f);
        hero.addView(kicker, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hero.addView(title);
        hero.addView(meta);
        hero.addView(about);
        View space = new View(this);
        hero.addView(space, new LinearLayout.LayoutParams(0, 0, 1f));
        play = new LinearLayout(this);
        play.setOrientation(LinearLayout.HORIZONTAL);
        play.setGravity(Gravity.CENTER_VERTICAL);
        play.setPadding(dp(14), 0, dp(18), 0);
        playIcon = new ImageView(this);
        Path triangle = new Path();
        triangle.moveTo(4f, 2.5f);
        triangle.lineTo(4f, 13.5f);
        triangle.lineTo(13.6f, 8f);
        triangle.close();
        playShape = new ShapeDrawable(new PathShape(triangle, 16f, 16f));
        // An ImageView draws nothing for a drawable with no size of its own, and a ShapeDrawable has none until told.
        playShape.setIntrinsicWidth(dp(16));
        playShape.setIntrinsicHeight(dp(16));
        playIcon.setImageDrawable(playShape);
        play.addView(playIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));
        playLabel = comic(18, 0xFFFFFFFF);
        LinearLayout.LayoutParams labelGap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelGap.setMarginStart(dp(10));
        play.addView(playLabel, labelGap);
        playFill = glass(MarvelLook.RED, 0xFFFFFFFF, 0f);
        play.setBackground(playFill);
        hero.addView(play, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        pathLayer.addView(hero, at(40, 58, 428, 274));

        // A comic panel's ink line and a red print offset, behind the player and not a frame in front of it: nothing is drawn
        // over YouTube's player.
        rimOffset = new View(this);
        rimOffset.setBackgroundColor(MarvelLook.RED);
        pathLayer.addView(rimOffset, at(496, 64, 432, 243));
        rim = new View(this);
        rim.setBackgroundColor(0xFFFFFFFF);
        pathLayer.addView(rim, at(485, 53, 438, 249));
        trailer = new TrailerWindow(this);
        pathLayer.addView(trailer, at(488, 56, 432, 243));

        caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.HORIZONTAL);
        captionFrom = text(12, 0xB3FFFFFF, R.string.font_regular);
        caption.addView(captionFrom, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        captionMuted = text(12, 0xB3FFFFFF, R.string.font_regular);
        captionMuted.setText("Muted");
        caption.addView(captionMuted);
        pathLayer.addView(caption, at(488, 314, 432, -2));
        root.addView(pathLayer, full());
    }

    private TextView tab(String name) {
        TextView tab = comic(15, 0xB3FFFFFF);
        tab.setText(name);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(12), 0, dp(12), 0);
        tab.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
        return tab;
    }

    // ---- keys -------------------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean ours = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_LEFT
                || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_BACK;
        if (!ours) return super.dispatchKeyEvent(event);
        if (event.getAction() != KeyEvent.ACTION_DOWN || busy) return true;
        if (intro != null) {
            if (event.getRepeatCount() == 0) intro.skip();
            return true;
        }
        boolean moving = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (event.getRepeatCount() > 0 && !(moving && screen == PATH)) return true;   // holding a key only walks the path
        switch (screen) {
            case CHOOSER:
                onChooserKey(key);
                break;
            case PATH:
                onPathKey(key);
                break;
            default:
                break;
        }
        return true;
    }

    private void onChooserKey(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
                chooseOrder(0);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                chooseOrder(1);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                busy = true;
                chooser.pick(this::enterPath);
                break;
            case KeyEvent.KEYCODE_BACK:
                leave();
                break;
            default:
                break;
        }
    }

    private void onPathKey(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                step(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                step(1);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                switchUniverse(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                switchUniverse(1);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                playCurrent();
                break;
            case KeyEvent.KEYCODE_BACK:
                backToChooser();
                break;
            default:
                break;
        }
    }

    // ---- the chooser ------------------------------------------------------------------------------

    private void chooseOrder(int which) {
        chooser.setFocused(which);
        art.wantPosters(which == 0 ? MarvelData.chronological() : MarvelData.phases(), 0);
    }

    /** Back on the wheel: Home opens the disc back up around it. */
    private void leave() {
        finish();
        overridePendingTransition(0, 0);
    }

    // ---- the path ---------------------------------------------------------------------------------

    private void enterPath() {
        order = chooser.focused() == 0 ? MarvelData.chronological() : MarvelData.phases();
        universeIndex = 0;
        setOrders(order);
        screen = PATH;
        pathLayer.setAlpha(0f);
        pathLayer.setVisibility(View.VISIBLE);
        pathLayer.animate().alpha(1f).setDuration(240).withEndAction(() -> {
            chooser.setVisibility(View.GONE);
            chooser.reset();
            busy = false;
        }).start();
        lane().enter(order);
        showLanes(false);
        accent = accentGoal = order.chapters.get(0).color;
        applyAccent();
        showTitle(false);
        for (View view : new View[]{hero, trailer, rim, rimOffset, caption}) {
            view.setAlpha(0f);
            view.setTranslationY(dp(8));
            view.animate().alpha(1f).translationY(0f).setStartDelay(260).setDuration(360).start();
        }
        startTrailer(FIRST_REST_MS);
    }

    private void backToChooser() {
        main.removeCallbacks(lookup);
        trailer.release();
        stopPaths();
        screen = CHOOSER;
        busy = true;
        chooser.reset();
        chooser.setVisibility(View.VISIBLE);
        pathLayer.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            pathLayer.setVisibility(View.GONE);
            pathLayer.setAlpha(1f);
            busy = false;
        }).start();
    }

    private void step(int by) {
        int to = lane().selected() + by;
        if (to < 0 || to >= order.stops.size()) return;
        String key = order.stops.get(to).title.key;
        if (isBridge(universeIndex, key)) {
            // The shared card is the MCU's: carry on from there, and come back down on the far side of it (or where the path ended).
            lane().moveTo(to);
            int beyond = to + by;
            boolean own = beyond >= 0 && beyond < order.stops.size() && !isBridge(universeIndex, order.stops.get(beyond).title.key);
            bridgeExit[universeIndex] = own ? beyond : to - by;
            lanes[0].setOrder(orders[0], orders[0].indexOf(key));
            goTo(0);
            return;
        }
        lane().moveTo(to);
        if (universeIndex == 0) syncLanes();
        showMerging(true);
        moveAccent();
        showTitle(true);
        startTrailer(REST_MS);
    }

    /** Down to the next universe's path or back up: the whole stack slides, and each path stays where it was left. */
    private void switchUniverse(int direction) {
        int next = universeIndex + direction;
        if (next < 0 || next >= lanes.length) return;
        goTo(next);
    }

    private void goTo(int next) {
        if (onBridge(next)) lanes[next].moveTo(bridgeExit[next]);
        universeIndex = next;
        order = orders[next];
        if (next == 0) syncLanes();
        showLanes(true);
        moveAccent();
        showTabs();
        showTitle(true);
        startTrailer(REST_MS);
    }

    /**
     * Keeps the other paths in step with the MCU's: on a card they share, each is brought to where it meets that card, and
     * at Doomsday a path that never reaches it waits at its own end. Off a shared card, a path resting under one moves on.
     */
    private void syncLanes() {
        String key = orders[0].stops.get(lanes[0].selected()).title.key;
        for (int i = 1; i < lanes.length; i++) {
            int was = lanes[i].selected();
            if (isBridge(i, key)) {
                int at = orders[i].indexOf(key);
                if (was != at) {
                    int exit = at - 1;
                    while (exit > 0 && isBridge(i, orders[i].stops.get(exit).title.key)) exit--;
                    bridgeExit[i] = exit;
                    lanes[i].moveTo(at);
                }
            } else if ("doom".equals(key)) {
                lanes[i].moveTo(orders[i].stops.size() - 1);
            } else if (onBridge(i)) {
                lanes[i].moveTo(bridgeExit[i]);
            }
            if (i == 1 && lanes[i].selected() != was) art.wantPosters(orders[i], lanes[i].selected());
        }
    }

    private static boolean isBridge(int lane, String titleKey) {
        return java.util.Arrays.asList(BRIDGES[lane]).contains(titleKey);
    }

    /** Whether this universe's path is resting under a shared card. */
    private boolean onBridge(int lane) {
        return isBridge(lane, orders[lane].stops.get(lanes[lane].selected()).title.key);
    }

    private MarvelPathView lane() {
        return lanes[universeIndex];
    }

    /** Every universe's path from its first title, with this one as the MCU's. */
    private void setOrders(MarvelData.Order mcu) {
        for (int i = 0; i < lanes.length; i++) {
            orders[i] = i == 0 ? mcu : MarvelData.order(MarvelData.UNIVERSES[i]);
            lanes[i].setOrder(orders[i], 0);
        }
    }

    /** Brings the path being walked to the main place, sliding there or at once. */
    private void showLanes(boolean sliding) {
        // Its titles' names are drawn over the edges of the paths above and below, never under them.
        lane().bringToFront();
        for (int i = universeIndex - 1; i <= universeIndex + 1; i += 2) {
            if (i >= 0 && i < lanes.length) art.wantPosters(orders[i], lanes[i].selected());
        }
        showMerging(sliding);
        if (slide != null) slide.cancel();
        if (!sliding) {
            placeLanes(universeIndex);
            return;
        }
        slide = ValueAnimator.ofFloat(stackAt, universeIndex);
        slide.setDuration(SLIDE_MS);
        slide.setInterpolator(new DecelerateInterpolator(1.5f));
        slide.addUpdateListener(a -> placeLanes((float) a.getAnimatedValue()));
        slide.start();
    }

    private void showMerging(boolean animate) {
        String key = universeIndex == 0 ? orders[0].stops.get(lanes[0].selected()).title.key : "";
        // Doomsday takes in the X-Men's, the Spider-Verse's and Fox's lines; No Way Home, the Raimi and the Amazing Spider-Man's.
        if ("doom".equals(key)) scene.setMerge(DOOM_X - 62f, 0xFFE58A2B, 0xFFCCCCCC, 0xFF9B59B6);
        if ("nwh".equals(key)) scene.setMerge(NWH_X - 48f, 0xFFD9382E, 0xFF3D8BFF);
        float to = "doom".equals(key) || "nwh".equals(key) ? 1f : 0f;
        if (merge != null) merge.cancel();
        if (!animate || merging == to) {
            setMerging(to);
            return;
        }
        merge = ValueAnimator.ofFloat(merging, to);
        merge.setDuration(SLIDE_MS);
        merge.addUpdateListener(a -> setMerging((float) a.getAnimatedValue()));
        merge.start();
    }

    private void setMerging(float amount) {
        merging = amount;
        scene.setMerging(amount);
        placeLanes(stackAt);
    }

    private void placeLanes(float at) {
        stackAt = at;
        for (int i = 0; i < lanes.length; i++) {
            float away = i - at;
            lanes[i].setTranslationY(dp(away * (away < 0f ? LANE_UP : LANE_GAP)));
            // On Doomsday the other paths give way to their lines, which cross the whole screen into its card.
            lanes[i].setAlpha(laneAlpha(away) * (i == 0 ? 1f : 1f - merging));
            lanes[i].setWalked(Math.max(0f, 1f - Math.abs(away)));
        }
    }

    /** The next path down waits a little dimmer; the one walked past is faint behind the title and trailer, and the one before it gone. */
    private static float laneAlpha(float away) {
        if (away >= 0f) return 1f - 0.4f * Math.min(1f, away);
        if (away >= -1f) return 1f + 0.7f * away;
        return Math.max(0f, 0.3f * (2f + away));
    }

    private void stopPaths() {
        if (slide != null) slide.cancel();
        if (merge != null) merge.cancel();
        scene.stop();
        for (MarvelPathView lane : lanes) lane.stop();
    }

    private MarvelData.Stop current() {
        return order.stops.get(lane().selected());
    }

    private void showTitle(boolean animate) {
        if (!animate) {
            fillTitle();
            return;
        }
        hero.animate().cancel();
        hero.animate().alpha(0f).setStartDelay(0).setDuration(110).withEndAction(() -> {
            fillTitle();
            hero.animate().alpha(1f).setStartDelay(0).setDuration(110).start();
        }).start();
    }

    private void fillTitle() {
        MarvelData.Stop stop = current();
        MarvelData.Title t = stop.title;
        boolean story = order.isChronological();
        if (story) {
            kicker.setText(MarvelData.storyLine(t));
        } else {
            MarvelData.Chapter chapter = order.chapters.get(stop.chapter);
            kicker.setText(chapter.label.toUpperCase(Locale.ROOT) + "  ·  " + (stop.inChapter + 1) + " OF " + chapter.titles.size());
        }
        // Anton is narrow, but the longest names still need a smaller size to fit two lines.
        int length = t.fullName.length();
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, length > 36 ? 34 : length > 24 ? 40 : 44);
        title.setText(t.fullName.toUpperCase(Locale.ROOT));
        boolean hasTrailer = !t.trailerId.isEmpty();
        captionFrom.setText(hasTrailer ? "Trailer from Marvel on YouTube" : "No trailer on Marvel's channels");
        captionMuted.setVisibility(hasTrailer ? View.VISIBLE : View.GONE);
        about.setText(t.about);
        showPlay();
    }

    /** The meta line and the Play button, which depend on where the title streams today. */
    private void showPlay() {
        MarvelData.Title t = current().title;
        MarvelLookup.Link link = linkFor(t);
        StringBuilder line = new StringBuilder(t.kind);
        // A title with no link carries a note saying so, which is out of date once a link has been found for it.
        boolean stale = t.link.isEmpty() && !link.url.isEmpty();
        if (!t.seasons.isEmpty() && !stale) line.append("  ·  ").append(t.seasons);
        line.append("  ·  ").append(order.isChronological() ? "Released " + t.released : String.valueOf(t.released));
        if (!link.onJioHotstar()) line.append("  ·  Not on JioHotstar right now");
        meta.setText(line);
        String action = link.onJioHotstar() && link.url.isEmpty() ? "Open JioHotstar" : "Play on " + link.platform;
        playLabel.setText(action.toUpperCase(Locale.ROOT));
        paintPlay();
    }

    private void paintPlay() {
        playFill.setColor(accent);
        playFill.setStroke(Math.max(1, dp(1)), 0xFFFFFFFF);
        int letters = MarvelLook.onColor(accent);
        playLabel.setTextColor(letters);
        playShape.getPaint().setColor(letters);
        playIcon.invalidate();
    }

    /** Walks the screen's colour over to the phase of the title it is now on. */
    private void moveAccent() {
        int goal = order.chapters.get(current().chapter).color;
        if (goal == accentGoal) return;
        accentGoal = goal;
        if (accentMove != null) accentMove.cancel();
        accentMove = ValueAnimator.ofObject(new ArgbEvaluator(), accent, goal);
        accentMove.setDuration(320);
        accentMove.addUpdateListener(a -> {
            accent = (int) a.getAnimatedValue();
            applyAccent();
        });
        accentMove.start();
    }

    private void applyAccent() {
        scene.setAccent(accent);
        lane().setAccent(accent);
        rimOffset.setBackgroundColor(accent);
        kicker.setBackgroundColor(accent);
        kicker.setTextColor(MarvelLook.onColor(accent));
        paintPlay();
        showTabs();
    }

    private void showTabs() {
        // The two orders are the MCU's: on another universe's path neither is lit.
        for (int i = 0; i < tabViews.length; i++) {
            boolean on = universeIndex == 0 && (i == 0) == order.isChronological();
            if (on) {
                tabViews[i].setBackgroundColor(accent);
                tabViews[i].setTextColor(MarvelLook.onColor(accent));
            } else {
                tabViews[i].setBackground(null);
                tabViews[i].setTextColor(0xB3FFFFFF);
            }
        }
    }

    private void startTrailer(long restMs) {
        MarvelData.Title t = current().title;
        trailer.show(t.trailerId, restMs);
        art.wantBackdrop(t.key);
        art.wantPosters(order, lane().selected());
        main.removeCallbacks(lookup);
        main.postDelayed(lookup, restMs);
    }

    // ---- playing ----------------------------------------------------------------------------------

    private MarvelLookup.Link linkFor(MarvelData.Title t) {
        MarvelLookup.Link known = links.get(t.key);
        return known != null ? known : MarvelLookup.standard(t);
    }

    /** What JustWatch lists for the title right now, once per title per visit. A failed look keeps the link it had. */
    private void lookUpCurrent() {
        MarvelData.Title t = current().title;
        if (lookedUp.containsKey(t.key)) return;
        lookedUp.put(t.key, true);
        String country = getString(R.string.wheel_country);
        new Thread(() -> {
            try {
                List<StreamSearch.Title> found = StreamSearch.justWatch(t.lookupName, country, 3, MarvelData.lookupYear(t));
                MarvelLookup.Link link = MarvelLookup.choose(t, found);
                if (link == null) return;
                main.post(() -> {
                    links.put(t.key, link);
                    if (screen != CHOOSER && current().title.key.equals(t.key)) showPlay();
                });
            } catch (Exception ignored) {
                // no network or a changed answer: the Play button stays as it was
            }
        }, "marvel-lookup").start();
    }

    private void playCurrent() {
        MarvelLookup.Link link = linkFor(current().title);
        // A film needs what memory there is: the trailer's player goes first.
        main.removeCallbacks(lookup);
        trailer.release();
        leftForTitle = true;
        trailerDue = true;
        if (!open(link)) {
            leftForTitle = false;
            trailerDue = false;
            startTrailer(REST_MS);
        }
    }

    /** The title in the app when it takes the link, else the app itself, else its Play Store page. */
    private boolean open(MarvelLookup.Link link) {
        return TitleOpener.open(this, link.pkg, link.url);
    }

    // ---- life -------------------------------------------------------------------------------------

    @Override
    protected void onStart() {
        super.onStart();
        leftForTitle = false;
        if (trailerDue && screen != CHOOSER) {
            trailerDue = false;
            startTrailer(REST_MS);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        main.removeCallbacks(lookup);
        trailer.release();
        // Home, another app or the TV going to sleep: this screen is over. Only JioHotstar comes back to it.
        if (leftForTitle) {
            trailerDue = true;
        } else if (!isFinishing() && !isChangingConfigurations()) {
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_SCREEN, screen == CHOOSER ? CHOOSER : PATH);
        out.putString(STATE_ORDER, order.key);
        out.putInt(STATE_INDEX, lane().selected());
    }

    @Override
    protected void onDestroy() {
        if (intro != null) intro.stop();
        if (accentMove != null) accentMove.cancel();
        art.shutdown();
        stopPaths();
        chooser.stop();
        trailer.shutdown();
        main.removeCallbacksAndMessages(null);
        boolean over = isFinishing();
        open--;
        super.onDestroy();
        // A WebView's native libraries stay in memory for as long as its process lives, and this process is only for this
        // screen, so it ends with it. The short wait lets Android hear that the activity is gone first.
        // Android can put off destroying a screen left with Home until the next one starts in the same process (seen on the
        // TV: the new screen died 0.6 s after opening), so the process only ends if no Marvel screen is open by then.
        if (over) new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (open == 0) Process.killProcess(Process.myPid());
        }, 500);
    }

    // ---- bits -------------------------------------------------------------------------------------

    private FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** dp from the top left of the 960 x 540 screen; -2 for wrap content. */
    private FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
        lp.leftMargin = dp(x);
        lp.topMargin = dp(y);
        return lp;
    }

    private GradientDrawable glass(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(Math.max(1, dp(1)), stroke);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(float sp, int color, int font) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        try {
            Typeface face = UiFont.get(this, font);
            if (face != null) t.setTypeface(face);
        } catch (Exception ignored) {
            // the system font is fine
        }
        return t;
    }

    /** Anton, the heavy poster lettering all the Marvel screens' big words use. */
    private TextView comic(float sp, int color) {
        TextView t = text(sp, color, R.string.font_medium);
        t.setTypeface(anton);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
