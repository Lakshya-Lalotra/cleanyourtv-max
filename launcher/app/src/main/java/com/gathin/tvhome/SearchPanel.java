package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home's search (the top bar's magnifier): type or speak a film or series, and see where it can be watched in the
 * TV's country, each platform a glass chip with its white logo. OK on a chip opens the title in that app, or the app's
 * Play Store page when it is not on the TV. Drawn over Home's wallpaper like the rest of Home: no new screen.
 * Back from the results returns to the field; Back from the field closes it.
 */
final class SearchPanel {
    interface Host {
        /** Search is coming up (true) or going (false): Home fades its own clock, buttons and tiles out or back. */
        void onSearchShown(boolean shown, boolean animate);

        /** Search has closed: focus goes back to where it was opened. */
        void onSearchClosed();
    }

    private final Context context;
    private final Host host;
    private final String country;
    private final float density;
    private final View scrim;
    private final LinearLayout panel, results;
    private final ScrollView scroller;
    private final EditText field;
    private final TextView status;
    private final List<List<View>> chipRows = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile Gemini gemini;
    private volatile boolean geminiChecked;
    private boolean opened, closed;
    private int request;

    SearchPanel(Context context, FrameLayout root, String country, Host host) {
        this.context = context;
        this.host = host;
        this.country = country;
        density = context.getResources().getDisplayMetrics().density;

        scrim = new View(context);
        scrim.setBackgroundColor(0xFF000000);
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(60), dp(40), dp(60), 0);
        panel.setAlpha(0f);
        panel.setVisibility(View.GONE);

        // The field: a glass pill like the top bar's, a magnifier and the words.
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(22), 0, dp(22), 0);
        pill.setBackground(glass(0x29FFFFFF, 0x59FFFFFF, 28));
        ImageView lens = new ImageView(context);
        lens.setImageResource(R.drawable.ic_search);
        lens.setAlpha(0.85f);
        pill.addView(lens, new LinearLayout.LayoutParams(dp(24), dp(24)));
        field = new EditText(context);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setHint("Search films and series");
        field.setHintTextColor(0x80FFFFFF);
        field.setTextColor(0xFFFFFFFF);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        field.setPadding(dp(14), 0, 0, 0);
        field.setDefaultFocusHighlightEnabled(false);
        setFont(field, R.string.font_light);
        field.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE
                    || event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                run();
                return true;
            }
            return false;
        });
        field.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusChip(0, 0);
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (key == KeyEvent.KEYCODE_BACK) {
                close();
                return true;
            }
            return false;
        });
        pill.addView(field, new LinearLayout.LayoutParams(0, dp(58), 1f));
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(dp(760), dp(58));
        pillLp.setMarginStart(dp(8));
        panel.addView(pill, pillLp);

        status = text(13, 0x99FFFFFF, R.string.font_regular);
        status.setPadding(dp(14), dp(12), 0, dp(14));
        panel.addView(status);

        results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        // Room at the sides for a focused chip, which grows past its row's edge.
        results.setPadding(dp(8), 0, dp(8), 0);
        results.setClipChildren(false);
        results.setClipToPadding(false);
        scroller = new ScrollView(context);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFocusable(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(0, 0, 0, dp(40));
        scroller.addView(results);
        panel.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    boolean isOpen() {
        return opened;
    }

    void open() {
        if (opened || closed) return;
        opened = true;
        host.onSearchShown(true, true);
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(0.72f).setDuration(240).start();
        panel.setVisibility(View.VISIBLE);
        panel.setTranslationY(-dp(16));
        panel.animate().alpha(1f).translationY(0f).setDuration(240).start();
        // Every search starts fresh: last time's words and results would only have to be cleared first.
        request++;
        field.setText("");
        results.removeAllViews();
        chipRows.clear();
        status.setText(hint());
        field.requestFocus();
        handler.postDelayed(this::showKeyboard, 120);
    }

    void close() {
        if (!opened) return;
        hideKeyboard();
        opened = false;
        request++;
        scrim.animate().alpha(0f).setDuration(200).withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        panel.animate().alpha(0f).translationY(-dp(16)).setDuration(180)
                .withEndAction(() -> panel.setVisibility(View.GONE)).start();
        host.onSearchShown(false, true);
        host.onSearchClosed();
    }

    void closeNow() {
        if (!opened) return;
        hideKeyboard();
        opened = false;
        request++;
        scrim.animate().cancel();
        panel.animate().cancel();
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        panel.setAlpha(0f);
        panel.setVisibility(View.GONE);
        host.onSearchShown(false, false);
    }

    void shutdown() {
        closeNow();
        closed = true;
        io.shutdownNow();
    }

    private String hint() {
        return "Where to watch in " + new java.util.Locale("", country.toUpperCase(java.util.Locale.ROOT))
                .getDisplayCountry(java.util.Locale.ENGLISH) + "  ·  type, or press the keyboard's mic and speak";
    }

    // ---- searching -------------------------------------------------------------------------------

    private void run() {
        String words = field.getText().toString().trim();
        if (words.isEmpty()) return;
        hideKeyboard();
        int my = ++request;
        status.setText("Searching streaming platforms…");
        results.removeAllViews();
        chipRows.clear();
        io.execute(() -> {
            if (!geminiChecked) {
                gemini = Gemini.load(context);
                geminiChecked = true;
            }
            List<StreamSearch.Title> found = null;
            try {
                found = StreamSearch.search(words, country, gemini);
            } catch (Exception ignored) {
                // said below
            }
            final List<StreamSearch.Title> got = found;
            handler.post(() -> {
                if (my != request || !opened) return;
                if (got == null) {
                    status.setText("Couldn't search right now. Check the internet and try again.");
                    return;
                }
                if (got.isEmpty()) {
                    status.setText("Nothing found for “" + words + "”.");
                    return;
                }
                status.setText((gemini != null ? "Gemini and JustWatch" : "JustWatch")
                        + "  ·  OK on a platform opens it");
                show(got);
                focusChip(0, 0);
            });
        });
    }

    private void show(List<StreamSearch.Title> titles) {
        PackageManager pm = context.getPackageManager();
        for (int r = 0; r < titles.size(); r++) {
            StreamSearch.Title t = titles.get(r);
            LinearLayout block = new LinearLayout(context);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(dp(6), dp(r == 0 ? 4 : 20), 0, 0);
            // A focused chip grows into the padding: let it.
            block.setClipChildren(false);
            block.setClipToPadding(false);
            TextView name = text(26, 0xFFFFFFFF, R.string.font_light);
            name.setText(t.name);
            block.addView(name);
            if (!t.meta.isEmpty()) {
                TextView meta = text(13, 0x99FFFFFF, R.string.font_regular);
                meta.setText(t.meta);
                meta.setPadding(0, dp(4), 0, 0);
                block.addView(meta);
            }
            LinearLayout chips = new LinearLayout(context);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setPadding(0, dp(12), 0, 0);
            chips.setClipChildren(false);
            chips.setClipToPadding(false);
            List<View> row = new ArrayList<>();
            if (t.offers.isEmpty()) {
                TextView none = text(14, 0x80FFFFFF, R.string.font_regular);
                none.setText("Not streaming in this country right now");
                chips.addView(none);
            }
            for (int c = 0; c < t.offers.size(); c++) {
                View chip = chip(t.offers.get(c), installed(pm, t.offers.get(c).pkg), r, c);
                row.add(chip);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(64));
                lp.setMarginEnd(dp(12));
                chips.addView(chip, lp);
            }
            block.addView(chips);
            chipRows.add(row);
            results.addView(block);
        }
    }

    private static boolean installed(PackageManager pm, String pkg) {
        if (pkg.isEmpty()) return false;
        try {
            pm.getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** A glass chip: the platform's white logo (or its name), and how it is offered underneath. */
    private View chip(StreamSearch.Offer offer, boolean here, int row, int column) {
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(16), dp(6), dp(16), dp(6));
        chip.setMinimumWidth(dp(120));
        chip.setFocusable(true);
        chip.setDefaultFocusHighlightEnabled(false);
        GradientDrawable rest = glass(0x1FFFFFFF, 0x33FFFFFF, 18);
        GradientDrawable focused = glass(0x47FFFFFF, 0xD9FFFFFF, 18);
        chip.setBackground(rest);
        chip.setAlpha(here ? 1f : 0.6f);
        int logo = logoFor(offer.platform);
        if (logo != 0) {
            ImageView mark = new ImageView(context);
            mark.setImageResource(logo);
            mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
            chip.addView(mark, new LinearLayout.LayoutParams(dp(74), dp(30)));
        } else {
            TextView name = text(15, 0xFFFFFFFF, R.string.font_medium);
            name.setText(offer.platform);
            name.setGravity(Gravity.CENTER);
            chip.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));
            name.setGravity(Gravity.CENTER_VERTICAL);
        }
        TextView kinds = text(11, 0xB3FFFFFF, R.string.font_regular);
        kinds.setText(here || offer.pkg.isEmpty() ? (offer.kinds.isEmpty() ? "Available" : offer.kinds) : "Get the app");
        kinds.setGravity(Gravity.CENTER);
        // Its own width, so a long line ("Stream · Free (ads)") widens the chip instead of being cut short.
        chip.addView(kinds, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chip.setContentDescription(offer.platform + ", " + kinds.getText());
        chip.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? focused : rest);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(160).start();
            if (has) v.post(() -> scroller.smoothScrollTo(0, Math.max(0, ((View) v.getParent().getParent()).getTop() - dp(8))));
        });
        chip.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (column > 0) focusChip(row, column - 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (column < chipRows.get(row).size() - 1) focusChip(row, column + 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (!focusChipInRow(row - 1, -1, column)) field.requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    focusChipInRow(row + 1, 1, column);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (event.getRepeatCount() == 0) openOffer(offer, here);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    // Back to the words, without the keyboard: Back again closes search, OK types.
                    field.requestFocus();
                    field.selectAll();
                    return true;
                default:
                    return false;
            }
        });
        return chip;
    }

    private static int logoFor(String platform) {
        switch (platform) {
            case "Netflix": return R.drawable.logo_netflix;
            case "Prime Video": return R.drawable.logo_prime_video;
            case "YouTube": return R.drawable.logo_youtube;
            case "JioHotstar": return R.drawable.logo_jiohotstar;
            case "Apple TV": return R.drawable.logo_apple_tv;
            default: return 0;
        }
    }

    private void focusChip(int row, int column) {
        if (row < 0 || row >= chipRows.size() || chipRows.get(row).isEmpty()) return;
        List<View> chips = chipRows.get(row);
        chips.get(Math.max(0, Math.min(column, chips.size() - 1))).requestFocus();
    }

    /** The next row with chips in that direction, at the nearest column; false when there is none. */
    private boolean focusChipInRow(int from, int step, int column) {
        for (int r = from; r >= 0 && r < chipRows.size(); r += step) {
            if (!chipRows.get(r).isEmpty()) {
                focusChip(r, column);
                return true;
            }
        }
        return false;
    }

    /** The title in the platform's app when it takes the link, else the app itself, else its Play Store page. */
    private void openOffer(StreamSearch.Offer offer, boolean here) {
        if (offer.pkg.isEmpty()) return;
        if (!here) {
            start(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + offer.pkg)));
            return;
        }
        PackageManager pm = context.getPackageManager();
        if (!offer.url.isEmpty()) {
            Intent title = new Intent(Intent.ACTION_VIEW, Uri.parse(offer.url)).setPackage(offer.pkg);
            if (pm.resolveActivity(title, 0) != null && start(title)) return;
        }
        Intent app = pm.getLeanbackLaunchIntentForPackage(offer.pkg);
        if (app == null) app = pm.getLaunchIntentForPackage(offer.pkg);
        if (app != null) start(app);
    }

    private boolean start(Intent intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    // ---- bits ------------------------------------------------------------------------------------

    private void showKeyboard() {
        if (!opened || !field.hasFocus()) return;
        InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    private GradientDrawable glass(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(Math.max(1, dp(1)), stroke);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(float sp, int color, int font) {
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        setFont(t, font);
        return t;
    }

    private void setFont(TextView t, int font) {
        try {
            t.setTypeface(UiFont.get(context, font));
        } catch (Exception ignored) {
            // the system font is fine
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
