package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
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
 */
public final class SpaceMarketActivity extends Activity {
    private static final String PREFS = "fun_spaces", KEY = "spaces";
    private static final int MAX_FIELD = 200;

    /** Every saved space, as {name, link}. */
    private final List<String[]> spaces = new ArrayList<>();
    /** The spaces the search box lets through, as shown in the list. */
    private final List<String[]> shown = new ArrayList<>();
    private ArrayAdapter<String[]> adapter;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        int pad = Math.round(48 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Fun spaces");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        root.addView(title);

        EditText search = new EditText(this);
        search.setHint("Search spaces");
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFF888888);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        root.addView(search);

        adapter = new ArrayAdapter<String[]>(this, android.R.layout.simple_list_item_1, shown) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                String[] space = getItem(position);
                row.setText(space[0] + "  " + space[1]);
                row.setTextColor(Color.WHITE);
                return row;
            }
        };
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button add = new Button(this);
        add.setText("Add a space");
        add.setOnClickListener(v -> showAddDialog());
        root.addView(add);

        setContentView(root);
        load();
        filter("");
    }

    private void showAddDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, 0);
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
}
