package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "+" on the fun wheel: search the fun spaces saved on this TV and add one of your own. The marketplace will be a
 * separate web app; until then only spaces added here are listed. The launcher has no network access.
 *
 * Sizes are written for a 1280 px wide screen and scaled to the TV's real width, so the layout matches the design.
 * The cards are plain focusable views in rows of three, so the remote moves between them like any other button.
 */
public final class SpaceMarketActivity extends Activity {
    private static final String PREFS = "fun_spaces", KEY = "spaces";
    private static final int MAX_FIELD = 200;
    private static final int DESIGN_WIDTH = 1280;
    private static final int COLUMNS = 3;
    private static final int[] ACCENTS = {0xFFE50914, 0xFFFFB020, 0xFF3B82F6, 0xFF22C55E, 0xFFA855F7, 0xFF14B8A6};

    /** Every saved space, as {name, link}. */
    private final List<String[]> spaces = new ArrayList<>();
    private ScrollView scroll;
    private LinearLayout rows;
    private LinearLayout emptyBlock;
    private float scale;
    private Typeface medium, regular;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        scale = getResources().getDisplayMetrics().widthPixels / (float) DESIGN_WIDTH;
        medium = getResources().getFont(R.font.outfit_medium);
        regular = getResources().getFont(R.font.outfit_regular);

        LinearLayout root = column();
        root.setBackground(fill(0xFF050507, 0));
        root.setPadding(u(72), u(48), u(72), u(32));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column();
        titles.addView(text("Fun spaces", 44, medium, 0xFFFFFFFF), wrap());
        titles.addView(text("Find a fun space or add your own", 22, regular, 0xFF9A9AA3), wrap());
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView add = text("+  Add a space", 24, medium, 0xFFFFFFFF);
        add.setGravity(Gravity.CENTER);
        add.setPadding(u(32), u(18), u(32), u(18));
        GradientDrawable addBackground = fill(0xFFE50914, u(40));
        add.setBackground(addBackground);
        add.setFocusable(true);
        add.setOnFocusChangeListener((view, focused) -> focusRing(addBackground, focused));
        add.setOnClickListener(v -> showAddDialog());
        header.addView(add, wrap());
        root.addView(header, matchWrap());

        EditText search = new EditText(this);
        search.setHint("Search spaces by name or link");
        search.setHintTextColor(0xFF6F6F7A);
        search.setTextColor(0xFFFFFFFF);
        search.setTypeface(regular);
        search.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24 * scale);
        search.setSingleLine(true);
        search.setPadding(u(32), u(20), u(32), u(20));
        GradientDrawable searchBackground = fill(0xFF15151B, u(36), 0xFF2A2A33, u(2));
        search.setBackground(searchBackground);
        search.setOnFocusChangeListener((view, focused) ->
                searchBackground.setStroke(focused ? u(4) : u(2), focused ? 0xFFFFFFFF : 0xFF2A2A33));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        LinearLayout.LayoutParams searchParams = matchWrap();
        searchParams.topMargin = u(24);
        root.addView(search, searchParams);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        rows = column();
        scroll.addView(rows, matchWrap());
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listParams.topMargin = u(24);
        root.addView(scroll, listParams);

        emptyBlock = column();
        emptyBlock.setGravity(Gravity.CENTER);
        emptyBlock.addView(text("No fun spaces yet", 40, medium, 0xFFFFFFFF), wrap());
        LinearLayout.LayoutParams emptyLine = wrap();
        emptyLine.topMargin = u(16);
        emptyBlock.addView(text("Press OK on Add a space to add your first one", 22, regular, 0xFF9A9AA3), emptyLine);
        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        emptyParams.topMargin = u(24);
        root.addView(emptyBlock, emptyParams);

        TextView footer = text("OK  open     Back  close", 18, regular, 0xFF6F6F7A);
        LinearLayout.LayoutParams footerParams = wrap();
        footerParams.topMargin = u(16);
        root.addView(footer, footerParams);

        setContentView(root);
        load();
        filter("");
    }

    private void showAddDialog() {
        LinearLayout form = column();
        form.setPadding(u(24), u(24), u(24), 0);
        EditText name = new EditText(this);
        name.setHint("Name");
        name.setSingleLine(true);
        form.addView(name);
        EditText link = new EditText(this);
        link.setHint("Link or ID");
        link.setSingleLine(true);
        form.addView(link);

        new AlertDialog.Builder(this)
                .setTitle("Add a space")
                .setView(form)
                .setPositiveButton("Save", (dialog, which) -> {
                    String n = name.getText().toString().trim();
                    String l = link.getText().toString().trim();
                    if (n.isEmpty() || l.isEmpty()) {
                        Toast.makeText(this, "Enter a name and a link", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (n.length() > MAX_FIELD || l.length() > MAX_FIELD) {
                        Toast.makeText(this, "Keep each field under " + MAX_FIELD + " characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    spaces.add(new String[] {n, l});
                    save();
                    filter("");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Shows the saved spaces whose name or link contains the text, ignoring case, in rows of three cards. */
    private void filter(String text) {
        String needle = text.trim().toLowerCase(Locale.ROOT);
        List<String[]> shown = new ArrayList<>();
        for (String[] space : spaces) {
            if (needle.isEmpty() || space[0].toLowerCase(Locale.ROOT).contains(needle)
                    || space[1].toLowerCase(Locale.ROOT).contains(needle)) {
                shown.add(space);
            }
        }

        rows.removeAllViews();
        for (int start = 0; start < shown.size(); start += COLUMNS) {
            LinearLayout line = row();
            for (int column = 0; column < COLUMNS; column++) {
                int index = start + column;
                View slot = index < shown.size() ? card(shown.get(index)) : new View(this);
                LinearLayout.LayoutParams slotParams = new LinearLayout.LayoutParams(0, u(168), 1f);
                if (column > 0) slotParams.leftMargin = u(24);
                line.addView(slot, slotParams);
            }
            LinearLayout.LayoutParams lineParams = matchWrap();
            if (start > 0) lineParams.topMargin = u(24);
            rows.addView(line, lineParams);
        }

        boolean none = spaces.isEmpty();
        emptyBlock.setVisibility(none ? View.VISIBLE : View.GONE);
        scroll.setVisibility(none ? View.GONE : View.VISIBLE);
    }

    /** One space card: rounded dark tile, accent strip, name, link and a tag. Focus shows a white ring. */
    private View card(String[] space) {
        int accent = ACCENTS[Math.abs(space[0].hashCode()) % ACCENTS.length];
        LinearLayout card = row();
        GradientDrawable background = fill(0xFF17171D, u(24));
        card.setBackground(background);
        // The accent strip is square; clipping to the rounded background keeps it inside the corners.
        card.setClipToOutline(true);
        // The ring is drawn in front of the strip and text, so the strip can't cover its left side.
        GradientDrawable ring = fill(0x00000000, u(24));
        card.setForeground(ring);
        card.setFocusable(true);
        card.setOnFocusChangeListener((view, focused) -> focusRing(ring, focused));

        View strip = new View(this);
        strip.setBackgroundColor(accent);
        card.addView(strip, new LinearLayout.LayoutParams(u(8), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = column();
        content.setPadding(u(28), u(26), u(24), u(24));
        content.addView(text(space[0], 28, medium, 0xFFFFFFFF), wrap());
        LinearLayout.LayoutParams linkParams = wrap();
        linkParams.topMargin = u(8);
        content.addView(text(space[1], 18, regular, 0xFF8A8A95), linkParams);
        LinearLayout.LayoutParams tagParams = wrap();
        tagParams.topMargin = u(8);
        content.addView(text("Added on this TV", 16, medium, accent), tagParams);
        card.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return card;
    }

    private void load() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        spaces.clear();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                spaces.add(new String[] {o.optString("name"), o.optString("link")});
            }
        } catch (JSONException e) {
            spaces.clear();
        }
    }

    private void save() {
        JSONArray array = new JSONArray();
        try {
            for (String[] space : spaces) {
                array.put(new JSONObject().put("name", space[0]).put("link", space[1]));
            }
        } catch (JSONException e) {
            return;
        }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply();
    }

    /** The focused card or button gets a white ring, so the remote's position is clear from across the room. */
    private void focusRing(GradientDrawable background, boolean focused) {
        background.setStroke(focused ? u(4) : 0, 0xFFFFFFFF);
    }

    private int u(int designPixels) {
        return Math.round(designPixels * scale);
    }

    private TextView text(String value, int designSize, Typeface face, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, designSize * scale);
        view.setTypeface(face);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static GradientDrawable fill(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static GradientDrawable fill(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = fill(color, radius);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }
}
