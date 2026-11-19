package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * fun_wheel (config.xml): a lit crescent on the right edge, the dial (FunDial) waiting off-screen. It springs out now and
 * then as you move along the dock (DialInvite), and stays out on the last dock app, where Right slides it in; Up and Down
 * turn it through the worlds (FunWorlds), and the left of the screen says what the selected one is
 * in big type. OK or Left on a world with its own screen grows the disc over the whole screen and opens it; Back there
 * shrinks it back to the dial. Back on the dial closes it.
 */
final class FunWheel {
    interface Host {
        void onFunShown(boolean shown, boolean animate);

        /** The wheel has closed: focus goes back to the dock. */
        void onFunClosed();

        /** Starts a world's own screen; false when that failed. */
        boolean onOpenWorld(FunWorlds.World world);
    }

    private static final long LABELS_MS = 120, GROW_MS = 520, SHRINK_MS = 420;

    private final Context context;
    private final Host host;
    private final float density;
    private final List<FunWorlds.World> worlds = FunWorlds.all();
    private final View scrim;
    private final FunDial dial;
    private final WorldDisc disc;
    private final LinearLayout words, hintRow;
    private final ImageView logo;
    private final TextView firstLine, secondLine, okKey, hint, explore;
    private final DialInvite invite = new DialInvite();
    private ObjectAnimator breathing;
    /** The dock tile Home last had focus on, and the last one in the dock, which is next to the dial. */
    private int focusedTile = -1, lastDockTile = -1;
    private boolean opened;
    /** The disc is growing, or over the screen, or shrinking back: the dial takes no keys. */
    private boolean inWorld;
    /** The world's own screen has been asked for, so Home going away is expected and coming back means it closed. */
    private boolean worldStarted;
    private Runnable pendingGrow;

    FunWheel(Context context, FrameLayout root, Host host) {
        this.context = context;
        this.host = host;
        density = context.getResources().getDisplayMetrics().density;

        scrim = new View(context);
        scrim.setBackgroundColor(0xFF000000);
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dial = new FunDial(context);
        dial.setWorlds(FunWorlds.names(worlds), FunWorlds.colors(worlds));
        dial.setFocusable(false);
        dial.setDefaultFocusHighlightEnabled(false);   // the pointer is its focus
        dial.setOnKeyListener((v, key, event) -> event.getAction() == KeyEvent.ACTION_DOWN && onDialKey(key, event));
        root.addView(dial, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The big words, on the wallpaper with no box around them.
        words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setAlpha(0f);
        words.setVisibility(View.GONE);
        LinearLayout lockup = new LinearLayout(context);
        lockup.setOrientation(LinearLayout.HORIZONTAL);
        lockup.setGravity(Gravity.CENTER_VERTICAL);
        logo = new ImageView(context);
        logo.setAdjustViewBounds(true);
        LinearLayout.LayoutParams logoAt = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(62));
        logoAt.setMarginEnd(dp(18));
        lockup.addView(logo, logoAt);
        firstLine = text(76, 0xFFFFFFFF, R.string.font_light);
        lockup.addView(firstLine);
        // Wrap content, not the vertical layout's default of its parent's width: that would size the words to the hint.
        words.addView(lockup, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        secondLine = text(76, 0xFFFFFFFF, R.string.font_light);
        words.addView(secondLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hintRow = new LinearLayout(context);
        hintRow.setOrientation(LinearLayout.HORIZONTAL);
        hintRow.setGravity(Gravity.CENTER_VERTICAL);
        okKey = text(13, 0xFFFFFFFF, R.string.font_medium);
        okKey.setText("OK");
        okKey.setPadding(dp(9), dp(4), dp(9), dp(4));
        okKey.setBackground(keyCap());
        LinearLayout.LayoutParams okAt = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okAt.setMarginEnd(dp(12));
        hintRow.addView(okKey, okAt);
        hint = text(18, 0xD9FFFFFF, R.string.font_regular);
        hintRow.addView(hint);
        LinearLayout.LayoutParams hintAt = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintAt.topMargin = dp(26);
        words.addView(hintRow, hintAt);
        FrameLayout.LayoutParams wordsAt = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        wordsAt.setMarginStart(dp(64));
        root.addView(words, wordsAt);

        // Beside the crescent while one press of Right would open it. In words as well as light, and only then.
        explore = text(17, 0xFFFFFFFF, R.string.font_regular);
        explore.setText("Explore worlds");
        explore.setShadowLayer(dp(5), 0f, dp(1), 0xB3000000);
        explore.setAlpha(0f);
        explore.setVisibility(View.GONE);
        FrameLayout.LayoutParams exploreAt = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        exploreAt.setMarginEnd(dp(85));
        root.addView(explore, exploreAt);

        disc = new WorldDisc(context);
        disc.setVisibility(View.GONE);
        root.addView(disc, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    boolean isOpen() {
        return opened;
    }

    /** The closed crescent, hidden while another overlay covers Home. */
    void showTab(boolean visible) {
        dial.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            applyFocus();
        } else {
            dial.restNow();
            showExplore(false);
        }
    }

    // ---- closed: inviting a press of Right --------------------------------------------------------

    /** Home's focus moved to this tile. */
    void onTileFocused(int position, int lastTile) {
        boolean moved = position != focusedTile;
        focusedTile = position;
        lastDockTile = lastTile;
        if (opened || dial.getVisibility() != View.VISIBLE) return;
        applyFocus();
        if (moved && position < lastTile && invite.onMove(SystemClock.elapsedRealtime())) dial.pop();
    }

    /** The focus left the dock for the top bar, where Right no longer leads here. */
    void onFocusLeftDock() {
        focusedTile = -1;
        applyFocus();
    }

    private void applyFocus() {
        boolean next = !opened && focusedTile >= 0 && focusedTile == lastDockTile;
        dial.setInviting(next);
        showExplore(next);
    }

    private void showExplore(boolean show) {
        explore.animate().cancel();
        if (show) {
            explore.setVisibility(View.VISIBLE);
            explore.setTranslationX(dp(14));
            explore.animate().alpha(1f).translationX(0f).setStartDelay(90).setDuration(240).start();
        } else if (explore.getVisibility() == View.VISIBLE) {
            explore.animate().alpha(0f).setStartDelay(0).setDuration(140).withEndAction(() -> explore.setVisibility(View.GONE)).start();
        }
    }

    /** Opens on the world last used, with focus on the dial. */
    void open() {
        if (opened) return;
        opened = true;
        invite.onAccepted(SystemClock.elapsedRealtime());
        dial.setInviting(false);
        showExplore(false);
        host.onFunShown(true, true);
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(0.74f).setDuration(280).start();
        dial.setFocusable(true);
        dial.requestFocus();
        dial.setOpen(true, true);
        fillWords(worlds.get(dial.selected()));
        words.setVisibility(View.VISIBLE);
        words.setTranslationX(-dp(28));
        words.animate().alpha(1f).translationX(0f).setStartDelay(120).setDuration(360).start();
    }

    void close() {
        if (!opened) return;
        opened = false;
        stopBreathing();
        scrim.animate().alpha(0f).setDuration(200).withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        words.animate().alpha(0f).translationX(-dp(20)).setStartDelay(0).setDuration(160)
                .withEndAction(() -> words.setVisibility(View.GONE)).start();
        dial.setOpen(false, true);
        dial.setFocusable(false);
        host.onFunShown(false, true);
        host.onFunClosed();
    }

    /** Home leaving the screen: shut at once, with no animation and no focus change. A world's own screen is not leaving. */
    void closeNow() {
        if (worldStarted) return;
        dial.restNow();
        showExplore(false);
        if (opened) shutNow();
    }

    /** The Home button, from anywhere: shut, whether or not a world's screen was up. */
    void closeForHome() {
        if (opened) shutNow();
    }

    private void shutNow() {
        opened = false;
        inWorld = false;
        worldStarted = false;
        stopBreathing();
        if (pendingGrow != null) disc.removeCallbacks(pendingGrow);
        pendingGrow = null;
        disc.stop();
        disc.setVisibility(View.GONE);
        dial.showOthers(true, 0);
        scrim.animate().cancel();
        words.animate().cancel();
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        words.setAlpha(0f);
        words.setVisibility(View.GONE);
        dial.setOpen(false, false);
        dial.setFocusable(false);
        host.onFunShown(false, false);
    }

    /** Home is back in front: if a world's screen just closed, the disc shrinks back to the dial. */
    void onHomeResumed() {
        if (opened && worldStarted) returnFromWorld();
        else if (!opened && dial.getVisibility() == View.VISIBLE) applyFocus();
    }

    // ---- a world's own screen ---------------------------------------------------------------------

    private void openWorld(FunWorlds.World world) {
        inWorld = true;
        worldStarted = false;
        stopBreathing();
        words.animate().alpha(0f).setStartDelay(0).setDuration(LABELS_MS).start();
        dial.showOthers(false, LABELS_MS);
        disc.setStart(world.name, world.color, dial.openCenterX(), dial.openRadius(), dial.selectedLabelLeft(), dial.selectedLabelSize());
        pendingGrow = () -> {
            pendingGrow = null;
            disc.setVisibility(View.VISIBLE);
            disc.grow(GROW_MS, null);
            // Started now, not when the disc is full: a world's screen takes about a second to appear, in its own process,
            // and the disc covers most of that wait instead of adding to it.
            worldStarted = true;
            if (!host.onOpenWorld(world)) returnFromWorld();
        };
        disc.postDelayed(pendingGrow, LABELS_MS);
    }

    private void returnFromWorld() {
        worldStarted = false;
        disc.setVisibility(View.VISIBLE);
        disc.showFull();
        dial.requestFocus();
        disc.shrink(SHRINK_MS, () -> {
            disc.setVisibility(View.GONE);
            inWorld = false;
            dial.showOthers(true, 160);
            words.animate().alpha(1f).setStartDelay(0).setDuration(240).start();
            startBreathing();
        });
    }

    // ---- keys ------------------------------------------------------------------------------------

    /** A held OK repeats: only its first press acts. */
    private static boolean repeatedOk(int key, KeyEvent event) {
        return (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) && event.getRepeatCount() > 0;
    }

    private boolean onDialKey(int key, KeyEvent event) {
        if (inWorld || repeatedOk(key, event)) return true;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                int by = key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                int next = dial.selected() + by;
                if (next >= 0 && next < worlds.size()) {
                    dial.select(next, true);
                    dial.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    swapWords(worlds.get(next), by);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                FunWorlds.World world = worlds.get(dial.selected());
                if (world.opens) openWorld(world);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            case KeyEvent.KEYCODE_BACK:
                close();
                return true;
            default:
                return false;
        }
    }

    // ---- the big words ----------------------------------------------------------------------------

    /** The words roll with the dial: the old ones leave the way the ring is turning, and the new ones come in behind them. */
    private void swapWords(FunWorlds.World world, int direction) {
        stopBreathing();
        words.animate().cancel();
        words.animate().alpha(0f).translationY(-direction * dp(30)).setStartDelay(0).setDuration(130).withEndAction(() -> {
            fillWords(world);
            words.setTranslationY(direction * dp(30));
            words.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(260).start();
        }).start();
    }

    private void fillWords(FunWorlds.World world) {
        if (world.logo != 0) {
            logo.setImageResource(world.logo);
            logo.setVisibility(View.VISIBLE);
        } else {
            logo.setImageDrawable(null);
            logo.setVisibility(View.GONE);
        }
        firstLine.setText(world.firstLine);
        secondLine.setText(world.secondLine);
        hint.setText(world.hint);
        okKey.setVisibility(world.opens ? View.VISIBLE : View.GONE);
        hint.setTextColor(world.opens ? 0xE6FFFFFF : 0x8CFFFFFF);
        stopBreathing();
        if (world.opens) startBreathing();
    }

    /** The one thing that moves by itself here: the way in, gently asking to be pressed. */
    private void startBreathing() {
        if (breathing != null) return;
        breathing = ObjectAnimator.ofFloat(hintRow, View.ALPHA, 1f, 0.5f);
        breathing.setDuration(1300);
        breathing.setRepeatMode(ValueAnimator.REVERSE);
        breathing.setRepeatCount(ValueAnimator.INFINITE);
        breathing.start();
    }

    private void stopBreathing() {
        if (breathing != null) breathing.cancel();
        breathing = null;
        hintRow.setAlpha(1f);
    }

    // ---- bits ------------------------------------------------------------------------------------

    private GradientDrawable keyCap() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x1FFFFFFF);
        g.setStroke(Math.max(1, dp(1)), 0x80FFFFFF);
        g.setCornerRadius(dp(7));
        return g;
    }

    private TextView text(float sp, int color, int font) {
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        try {
            t.setTypeface(UiFont.get(context, font));
        } catch (Exception ignored) {
            // the system font is fine
        }
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
