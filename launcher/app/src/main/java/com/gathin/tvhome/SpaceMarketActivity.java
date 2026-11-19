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
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
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
 */
public final class SpaceMarketActivity extends Activity {
    private static final String PREFS = "fun_spaces", KEY = "spaces";
    private static final int MAX_FIELD = 200;
    private static final int DESIGN_WIDTH = 1280;
    private static final int[] ACCENTS = {0xFFE50914, 0xFFFFB020, 0xFF3B82F6, 0xFF22C55E, 0xFFA855F7, 0xFF14B8A6};

    /** Every saved space, as {name, link}. */
    private final List<String[]> spaces = new ArrayList<>();
    /** The spaces the search box lets through. */
    private final List<String[]> shown = new ArrayList<>();
    private SpaceAdapter adapter;
    private GridView grid;
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
        add.setBackground(fill(0xFFE50914, u(40)));
        add.setFocusable(true);
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
        search.setBackground(fill(0xFF15151B, u(36), 0xFF2A2A33, u(2)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        LinearLayout.LayoutParams searchParams = matchWrap();
        searchParams.topMargin = u(24);
        root.addView(search, searchParams);

        grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setVerticalSpacing(u(24));
        grid.setHorizontalSpacing(u(24));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalScrollBarEnabled(false);
        adapter = new SpaceAdapter();
        grid.setAdapter(adapter);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gridParams.topMargin = u(24);
        root.addView(grid, gridParams);

        emptyBlock = column();
        emptyBlock.setGravity(Gravity.CENTER);
        emptyBlock.addView(text("No fun spaces yet", 40, medium, 0xFFFFFFFF), wrap());
        LinearLayout.LayoutParams emptyLine = wrap();
        emptyLine.topMargin = u(16);
        emptyBlock.addView(text("Press OK on Add a space to add your first one", 22, regular, 0xFF9A9AA3), emptyLine);
        root.addView(emptyBlock, gridParams);

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

    /** Shows the saved spaces whose name or link contains the text, ignoring case. */
    private void filter(String text) {
        String needle = text.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (String[] space : spaces) {
            if (needle.isEmpty() || space[0].toLowerCase(Locale.ROOT).contains(needle)
                    || space[1].toLowerCase(Locale.ROOT).contains(needle)) {
                shown.add(space);
            }
        }
        boolean none = spaces.isEmpty();
        emptyBlock.setVisibility(none ? View.VISIBLE : View.GONE);
        grid.setVisibility(none ? View.GONE : View.VISIBLE);
        if (adapter != null) adapter.notifyDataSetChanged();
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

    /** One space card: rounded dark tile, accent strip, name, link and a tag. Focus shows a white ring. */
    private final class SpaceAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            String[] space = shown.get(position);
            int accent = ACCENTS[Math.abs(space[0].hashCode()) % ACCENTS.length];

            LinearLayout card = row();
            GradientDrawable background = fill(0xFF17171D, u(24));
            card.setBackground(background);
            card.setFocusable(true);
            card.setOnFocusChangeListener((view, focused) -> {
                background.setStroke(focused ? u(4) : 0, 0xFFFFFFFF);
                view.setScaleX(focused ? 1.04f : 1f);
                view.setScaleY(focused ? 1.04f : 1f);
            });
            card.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u(168)));

            View strip = new View(SpaceMarketActivity.this);
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
