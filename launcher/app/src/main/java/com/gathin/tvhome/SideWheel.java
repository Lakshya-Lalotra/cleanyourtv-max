package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * side_wheel (config.xml): the dial on the left edge and the panel beside it. Left on the first dock app opens it; Up
 * and Down turn it, and the panel shows the selected option's list: Movies first lists its topics (Trending Bollywood,
 * Top Horror...), which OK or Right opens. OK on a row searches YouTube for it; Web search opens Google's search.
 * Left goes back one step and Back too, out of the wheel at the end. Lists are fetched only while the wheel shows
 * them and kept for 30 minutes.
 */
final class SideWheel {
    private static final long FRESH_MS = 30 * 60_000L;

    /** Called once the wheel has closed, to put focus back on the dock. */
    interface Host {
        void onWheelClosed();
    }

    private final Context context;
    private final Host host;
    private final float density;
    private final List<WheelFeeds.Section> sections;
    private final View scrim;
    private final WheelView wheel;
    private final LinearLayout panel, rowsBox;
    private final CappedScrollView scroller;
    private final TextView title, note;
    private final List<View> rows = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Map<WheelFeeds.Topic, Cached> cache = new HashMap<>();
    private boolean opened, closed;
    /** The topic whose rows the panel shows, or null while it lists Movies' topics. */
    private WheelFeeds.Topic shownTopic;
    private List<WheelFeeds.Item> shownItems = new ArrayList<>();
    /** Bumped on every request, so a late answer for a list no longer shown is dropped. */
    private int request;
    /** Gemini, looked up on the first fetch (a small file read, off the UI thread). */
    private volatile Gemini gemini;
    private volatile boolean geminiChecked;

    /** A ScrollView no taller than the screen allows; shorter content keeps its own height. */
    private static final class CappedScrollView extends android.widget.ScrollView {
        private final int most;

        CappedScrollView(Context context, int most) {
            super(context);
            this.most = most;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(most, View.MeasureSpec.AT_MOST));
        }
    }

    private static final class Cached {
        final long at;
        final List<WheelFeeds.Item> items;

        Cached(long at, List<WheelFeeds.Item> items) {
            this.at = at;
            this.items = items;
        }
    }

    SideWheel(Context context, FrameLayout root, String country, Host host) {
        this.context = context;
        this.host = host;
        density = context.getResources().getDisplayMetrics().density;
        sections = WheelFeeds.sections(country);

        scrim = new View(context);
        scrim.setBackgroundColor(0xFF000000);
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        wheel = new WheelView(context, false, 210);
        List<String> labels = new ArrayList<>();
        for (WheelFeeds.Section s : sections) labels.add(s.label);
        wheel.setLabels(labels);
        wheel.setFocusable(false);
        wheel.setDefaultFocusHighlightEnabled(false);   // the pill is its focus
        wheel.setOnKeyListener((v, key, event) -> event.getAction() == KeyEvent.ACTION_DOWN && onWheelKey(key, event));
        root.addView(wheel, new FrameLayout.LayoutParams(wheel.wantedWidth(), wheel.wantedHeight(),
                Gravity.START | Gravity.CENTER_VERTICAL));

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(22);
        panel.setPadding(pad, dp(18), pad, dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEB111216);
        bg.setCornerRadius(dp(22));
        bg.setStroke(Math.max(1, dp(1)), 0x26FFFFFF);
        panel.setBackground(bg);
        panel.setAlpha(0f);
        panel.setVisibility(View.GONE);
        title = text(26, 0xFFFFFFFF, R.string.font_light);
        note = text(13, 0x8CFFFFFF, R.string.font_regular);
        note.setPadding(0, dp(4), 0, dp(10));
        panel.addView(title);
        panel.addView(note);
        rowsBox = new LinearLayout(context);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        // Eleven movie topics or ten rows with a second line do not fit a 720p screen: they scroll, focus leading.
        scroller = new CappedScrollView(context, context.getResources().getDisplayMetrics().heightPixels - dp(150));
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFocusable(false);
        scroller.setClipToPadding(false);
        scroller.addView(rowsBox);
        panel.addView(scroller);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        lp.setMarginStart(wheel.wantedWidth() + dp(20));
        root.addView(panel, lp);
    }

    boolean isOpen() {
        return opened;
    }

    /** Opens on the option last used, with focus on the wheel. */
    void open() {
        if (opened || closed) return;
        opened = true;
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(0.62f).setDuration(260).start();
        wheel.setFocusable(true);
        wheel.requestFocus();
        wheel.setOpen(true, true, null);
        panel.setVisibility(View.VISIBLE);
        panel.setTranslationX(-dp(24));
        panel.animate().alpha(1f).translationX(0f).setDuration(260).start();
        preview();
    }

    /** Closes with the animation; focus goes back to the host. */
    void close() {
        if (!opened) return;
        opened = false;
        request++;
        scrim.animate().alpha(0f).setDuration(200).withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        panel.animate().alpha(0f).translationX(-dp(24)).setDuration(180)
                .withEndAction(() -> panel.setVisibility(View.GONE)).start();
        wheel.setOpen(false, true, null);
        wheel.setFocusable(false);
        host.onWheelClosed();
    }

    /** Home leaving the screen: shut at once, with no animation and no focus change. */
    void closeNow() {
        if (!opened) return;
        opened = false;
        request++;
        scrim.animate().cancel();
        panel.animate().cancel();
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        panel.setAlpha(0f);
        panel.setVisibility(View.GONE);
        wheel.setOpen(false, false, null);
        wheel.setFocusable(false);
    }

    void shutdown() {
        closeNow();
        closed = true;
        io.shutdownNow();
    }

    // ---- keys ------------------------------------------------------------------------------------

    /** A held OK repeats: only its first press acts, so holding it cannot open a list and then its first row. */
    private static boolean repeatedOk(int key, KeyEvent event) {
        return (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) && event.getRepeatCount() > 0;
    }

    private boolean onWheelKey(int key, KeyEvent event) {
        if (repeatedOk(key, event)) return true;
        WheelFeeds.Section s = sections.get(wheel.selected());
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                int next = wheel.selected() + (key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                if (next >= 0 && next < sections.size()) {
                    wheel.select(next, true);
                    wheel.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    preview();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (s.isSearch()) webSearch();
                else if (!rows.isEmpty()) rows.get(0).requestFocus();
                return true;
            case KeyEvent.KEYCODE_BACK:
                close();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return true;
            default:
                return false;
        }
    }

    private boolean onRowKey(int index, int key, KeyEvent event) {
        if (repeatedOk(key, event)) return true;
        WheelFeeds.Section s = sections.get(wheel.selected());
        boolean inTopic = s.hasTopics() && shownTopic != null;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (index > 0) rows.get(index - 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (index < rows.size() - 1) rows.get(index + 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (s.hasTopics() && shownTopic == null) showTopic(s.topics.get(index), true);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (s.hasTopics() && shownTopic == null) showTopic(s.topics.get(index), true);
                else if (index < shownItems.size()) openItem(shownItems.get(index));
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BACK:
                if (inTopic) {
                    int topicIndex = s.topics.indexOf(shownTopic);
                    showTopics(s);
                    if (topicIndex >= 0 && topicIndex < rows.size()) rows.get(topicIndex).requestFocus();
                } else {
                    wheel.requestFocus();
                }
                return true;
            default:
                return false;
        }
    }

    // ---- panel -----------------------------------------------------------------------------------

    /** The selected option in the panel: Movies' topics, a list, or the web search line. */
    private void preview() {
        WheelFeeds.Section s = sections.get(wheel.selected());
        if (s.isSearch()) {
            request++;
            shownTopic = null;
            shownItems = new ArrayList<>();
            title.setText(s.label);
            note.setText("Press OK to search with Google, by voice or keyboard");
            setRows(new ArrayList<>(), false);
        } else if (s.hasTopics()) {
            showTopics(s);
        } else {
            showTopic(s.topics.get(0), false);
        }
    }

    private void showTopics(WheelFeeds.Section s) {
        request++;
        shownTopic = null;
        shownItems = new ArrayList<>();
        title.setText(s.label);
        note.setText("Pick a list  ·  OK or › to open");
        List<WheelFeeds.Item> topics = new ArrayList<>();
        for (WheelFeeds.Topic t : s.topics) topics.add(new WheelFeeds.Item(t.label, "", ""));
        setRows(topics, false);
    }

    /** A topic's rows: from the cache when fresh, else fetched; focus moves to the first row when asked. */
    private void showTopic(WheelFeeds.Topic topic, boolean focusFirst) {
        int my = ++request;
        shownTopic = topic;
        title.setText(topic.label);
        Cached hit = cache.get(topic);
        if (hit != null && SystemClock.elapsedRealtime() - hit.at < FRESH_MS) {
            showItems(topic, hit.items, focusFirst);
            return;
        }
        shownItems = new ArrayList<>();
        note.setText("Loading…");
        setRows(new ArrayList<>(), true);
        // Keep focus inside the panel's world while the rows are empty: on the wheel.
        if (focusFirst) wheel.requestFocus();
        io.execute(() -> {
            if (!geminiChecked) {
                gemini = Gemini.load(context);
                geminiChecked = true;
            }
            List<WheelFeeds.Item> items = null;
            String failure = null;
            try {
                items = WheelFeeds.fetch(topic, gemini);
            } catch (Exception e) {
                failure = "Couldn't load this list. Check the internet and try again.";
            }
            final List<WheelFeeds.Item> got = items;
            final String error = failure;
            handler.post(() -> {
                if (my != request || !opened) return;
                if (got == null || got.isEmpty()) {
                    note.setText(error != null ? error : "Nothing to show right now.");
                    return;
                }
                cache.put(topic, new Cached(SystemClock.elapsedRealtime(), got));
                showItems(topic, got, focusFirst);
            });
        });
    }

    private void showItems(WheelFeeds.Topic topic, List<WheelFeeds.Item> items, boolean focusFirst) {
        shownItems = items;
        note.setText(sourceName(topic));
        setRows(items, true);
        if (focusFirst && !rows.isEmpty()) rows.get(0).requestFocus();
    }

    private String sourceName(WheelFeeds.Topic topic) {
        if (gemini != null && topic.gemini != null) return "Gemini with Google Search  ·  OK opens it on YouTube";
        switch (topic.source) {
            case WheelFeeds.ITUNES_MOVIES:
            case WheelFeeds.ITUNES_TV: return "iTunes charts  ·  OK opens it on YouTube";
            case WheelFeeds.ANILIST: return "AniList  ·  OK opens it on YouTube";
            case WheelFeeds.GOOGLE_NEWS: return "Google News  ·  OK opens it on YouTube";
            default: return "ESPN  ·  OK opens it on YouTube";
        }
    }

    private void setRows(List<WheelFeeds.Item> items, boolean ranked) {
        rowsBox.removeAllViews();
        rows.clear();
        scroller.scrollTo(0, 0);
        for (int i = 0; i < items.size(); i++) {
            View row = row(i, items.get(i), ranked);
            rows.add(row);
            rowsBox.addView(row);
        }
    }

    private View row(int index, WheelFeeds.Item item, boolean ranked) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(ranked ? 5 : 6), dp(12), dp(ranked ? 5 : 6));
        row.setFocusable(true);
        row.setDefaultFocusHighlightEnabled(false);
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(0x2EFFFFFF);
        focus.setStroke(Math.max(1, dp(1)), 0x59FFFFFF);
        focus.setCornerRadius(dp(14));
        row.setOnFocusChangeListener((v, has) -> v.setBackground(has ? focus : null));
        row.setOnKeyListener((v, key, event) -> event.getAction() == KeyEvent.ACTION_DOWN && onRowKey(index, key, event));
        if (ranked) {
            TextView rank = text(15, 0x80FFFFFF, R.string.font_medium);
            rank.setText(String.valueOf(index + 1));
            rank.setMinWidth(dp(30));
            row.addView(rank);
        }
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(ranked ? 16 : 17, 0xFFFFFFFF, R.string.font_regular);
        name.setText(item.title);
        words.addView(name);
        if (!item.subtitle.isEmpty()) {
            TextView sub = text(12, 0x8CFFFFFF, R.string.font_regular);
            sub.setText(item.subtitle);
            words.addView(sub);
        }
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!ranked) {
            TextView arrow = text(20, 0x80FFFFFF, R.string.font_light);
            arrow.setText("›");
            row.addView(arrow);
        }
        return row;
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

    // ---- actions ---------------------------------------------------------------------------------

    /** YouTube's results for the row; Google's search with the same words if YouTube is not there. */
    private void openItem(WheelFeeds.Item item) {
        if (item.query.isEmpty()) return;
        Intent youtube = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(item.query)))
                .setPackage("com.google.android.youtube.tv").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(youtube);
        } catch (ActivityNotFoundException e) {
            search(item.query);
        }
    }

    private void webSearch() {
        search(null);
    }

    private void search(String query) {
        Intent i = new Intent("android.search.action.GLOBAL_SEARCH").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (query != null) i.putExtra("query", query);
        try {
            context.startActivity(i);
        } catch (ActivityNotFoundException e) {
            try {
                context.startActivity(new Intent(Intent.ACTION_WEB_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
                note.setText("This TV has no search app.");
            }
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
