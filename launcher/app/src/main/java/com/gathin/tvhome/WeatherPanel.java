package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The weather screen (OK on Home's weather): now, the air quality, the next ten days, and the place and unit, which
 * can be changed here as from the Settings menu. Drawn over Home's wallpaper like search: glass cards, white type,
 * the AQI in its band's colour. A new place is found by name through Open-Meteo's geocoding, which needs no key.
 */
final class WeatherPanel implements Weather.Listener {
    interface Host {
        void onWeatherShown(boolean shown, boolean animate);

        void onWeatherClosed();
    }

    private final Context context;
    private final Weather weather;
    private final Host host;
    private final float density;
    private final View scrim;
    private final FrameLayout layer;
    private final LinearLayout main, finder, finderRows;
    private final TextView place, temp, sky, details, aqiValue, aqiBand, aqiParts, updated, finderStatus;
    private final ImageView icon;
    private final LinearLayout daysRow;
    private final List<View> dayCards = new ArrayList<>();
    private final TextView locationChip, unitChip;
    private final EditText finderField;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<View> finderResults = new ArrayList<>();
    private boolean opened, closed, finding;
    private int request;

    WeatherPanel(Context context, FrameLayout root, Weather weather, Host host) {
        this.context = context;
        this.weather = weather;
        this.host = host;
        density = context.getResources().getDisplayMetrics().density;

        scrim = new View(context);
        scrim.setBackgroundColor(0xFF000000);
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        layer = new FrameLayout(context);
        layer.setAlpha(0f);
        layer.setVisibility(View.GONE);
        layer.setClipChildren(false);
        root.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- the main view --------------------------------------------------------------------
        main = column();
        main.setPadding(dp(60), dp(36), dp(60), dp(24));
        main.setClipChildren(false);
        main.setClipToPadding(false);
        layer.addView(main, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = row();
        place = text(13, 0xB3FFFFFF, R.string.font_medium);
        place.setLetterSpacing(0.18f);
        place.setAllCaps(true);
        top.addView(place, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        updated = text(12, 0x73FFFFFF, R.string.font_regular);
        top.addView(updated);
        main.addView(top);

        // Now, and the air quality beside it.
        LinearLayout hero = row();
        hero.setPadding(0, dp(14), 0, dp(18));
        icon = new ImageView(context);
        hero.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        temp = text(78, 0xFFFFFFFF, R.string.font_extralight);
        temp.setPadding(dp(18), 0, dp(24), 0);
        hero.addView(temp);
        LinearLayout words = column();
        sky = text(24, 0xFFFFFFFF, R.string.font_light);
        details = text(14, 0x99FFFFFF, R.string.font_regular);
        details.setPadding(0, dp(8), 0, 0);
        words.addView(sky);
        words.addView(details);
        hero.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout aqiCard = column();
        aqiCard.setPadding(dp(20), dp(14), dp(20), dp(14));
        aqiCard.setBackground(glass(0x1AFFFFFF, 0x33FFFFFF, 22));
        TextView aqiTitle = text(11, 0x99FFFFFF, R.string.font_medium);
        aqiTitle.setText("AIR QUALITY  ·  US AQI");
        aqiTitle.setLetterSpacing(0.14f);
        aqiValue = text(40, 0xFFFFFFFF, R.string.font_light);
        aqiValue.setPadding(0, dp(6), 0, dp(2));
        aqiBand = text(14, 0xFFFFFFFF, R.string.font_medium);
        aqiParts = text(12, 0x8CFFFFFF, R.string.font_regular);
        aqiParts.setPadding(0, dp(6), 0, 0);
        aqiCard.addView(aqiTitle);
        aqiCard.addView(aqiValue);
        aqiCard.addView(aqiBand);
        aqiCard.addView(aqiParts);
        hero.addView(aqiCard, new LinearLayout.LayoutParams(dp(250), ViewGroup.LayoutParams.WRAP_CONTENT));
        main.addView(hero);

        TextView next = text(12, 0x99FFFFFF, R.string.font_medium);
        next.setText("NEXT " + Weather.DAYS + " DAYS");
        next.setLetterSpacing(0.18f);
        next.setPadding(dp(2), 0, 0, dp(10));
        main.addView(next);
        daysRow = row();
        daysRow.setClipChildren(false);
        daysRow.setClipToPadding(false);
        main.addView(daysRow);

        // The place and the unit, changeable here as from the Settings menu.
        LinearLayout settings = row();
        settings.setPadding(0, dp(22), 0, 0);
        settings.setClipChildren(false);
        locationChip = chip();
        unitChip = chip();
        settings.addView(locationChip, chipParams());
        settings.addView(unitChip, chipParams());
        main.addView(settings);
        locationChip.setOnKeyListener((v, key, e) -> e.getAction() == KeyEvent.ACTION_DOWN && settingsKey(key, e, 0));
        unitChip.setOnKeyListener((v, key, e) -> e.getAction() == KeyEvent.ACTION_DOWN && settingsKey(key, e, 1));

        // ---- the place finder -----------------------------------------------------------------
        finder = column();
        finder.setPadding(dp(60), dp(40), dp(60), dp(24));
        finder.setVisibility(View.GONE);
        LinearLayout pill = row();
        pill.setPadding(dp(22), 0, dp(22), 0);
        pill.setBackground(glass(0x29FFFFFF, 0x59FFFFFF, 28));
        ImageView lens = new ImageView(context);
        lens.setImageResource(R.drawable.ic_search);
        pill.addView(lens, new LinearLayout.LayoutParams(dp(24), dp(24)));
        finderField = new EditText(context);
        finderField.setBackground(null);
        finderField.setSingleLine(true);
        finderField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        finderField.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        finderField.setHint("City or town");
        finderField.setHintTextColor(0x80FFFFFF);
        finderField.setTextColor(0xFFFFFFFF);
        finderField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        finderField.setPadding(dp(14), 0, 0, 0);
        finderField.setDefaultFocusHighlightEnabled(false);
        setFont(finderField, R.string.font_light);
        finderField.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE
                    || event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                findPlaces();
                return true;
            }
            return false;
        });
        finderField.setOnKeyListener((v, key, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && !finderResults.isEmpty()) {
                finderResults.get(0).requestFocus();
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (key == KeyEvent.KEYCODE_BACK) {
                showMain(locationChip);
                return true;
            }
            return false;
        });
        pill.addView(finderField, new LinearLayout.LayoutParams(0, dp(58), 1f));
        finder.addView(pill, new LinearLayout.LayoutParams(dp(640), dp(58)));
        finderStatus = text(13, 0x99FFFFFF, R.string.font_regular);
        finderStatus.setPadding(dp(8), dp(12), 0, dp(12));
        finder.addView(finderStatus);
        finderRows = column();
        finder.addView(finderRows, new LinearLayout.LayoutParams(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT));
        layer.addView(finder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        weather.addListener(this);
    }

    boolean isOpen() {
        return opened;
    }

    /** Opens on today. */
    void open() {
        if (!show()) return;
        showMain(null);
    }

    /** Opens on the place finder (the Settings menu's Weather location row). */
    void openFinder() {
        if (!show()) return;
        showFinder();
    }

    private boolean show() {
        if (closed) return false;
        if (!opened) {
            opened = true;
            host.onWeatherShown(true, true);
            scrim.setVisibility(View.VISIBLE);
            scrim.animate().alpha(0.74f).setDuration(240).start();
            layer.setVisibility(View.VISIBLE);
            layer.setTranslationY(-dp(16));
            layer.animate().alpha(1f).translationY(0f).setDuration(240).start();
            bind();
        }
        return true;
    }

    void close() {
        if (!opened) return;
        hideKeyboard();
        opened = false;
        request++;
        scrim.animate().alpha(0f).setDuration(200).withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        layer.animate().alpha(0f).translationY(-dp(16)).setDuration(180)
                .withEndAction(() -> layer.setVisibility(View.GONE)).start();
        host.onWeatherShown(false, true);
        host.onWeatherClosed();
    }

    void closeNow() {
        if (!opened) return;
        hideKeyboard();
        opened = false;
        request++;
        scrim.animate().cancel();
        layer.animate().cancel();
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        layer.setAlpha(0f);
        layer.setVisibility(View.GONE);
        host.onWeatherShown(false, false);
    }

    void shutdown() {
        closeNow();
        closed = true;
        weather.removeListener(this);
        io.shutdownNow();
    }

    @Override
    public void onWeather(Weather w) {
        if (opened) bind();
    }

    // ---- main view -------------------------------------------------------------------------------

    private void showMain(View focus) {
        finding = false;
        hideKeyboard();
        finder.setVisibility(View.GONE);
        main.setVisibility(View.VISIBLE);
        bind();
        View target = focus != null ? focus : !dayCards.isEmpty() ? dayCards.get(0) : locationChip;
        target.requestFocus();
    }

    /** Fills the main view from the last answer; the day cards are rebuilt only when the days changed. */
    private void bind() {
        String name = weather.place();
        place.setText(name.isEmpty() ? "Your location" : name);
        locationChip.setText("Location  ·  " + (name.isEmpty() ? "Default" : name) + "   ›");
        unitChip.setText("Units  ·  " + (weather.fahrenheit() ? "°F" : "°C") + "   ⇄");
        if (!weather.hasReading()) {
            icon.setImageDrawable(null);
            temp.setText("–");
            sky.setText("Getting the weather…");
            details.setText("");
            aqiValue.setText("–");
            aqiBand.setText("");
            aqiParts.setText("");
            updated.setText("");
            buildDays();
            return;
        }
        icon.setImageResource(Weather.iconFor(Weather.kind(weather.code, weather.day)));
        temp.setText(weather.degrees(weather.temp));
        sky.setText(Weather.describe(weather.code));
        List<String> bits = new ArrayList<>();
        bits.add("Feels like " + weather.degrees(weather.feels));
        if (weather.humidity >= 0) bits.add("Humidity " + weather.humidity + "%");
        bits.add("Wind " + Math.round(weather.wind) + " km/h");
        details.setText(TextUtils.join("  ·  ", bits));
        if (weather.aqi >= 0) {
            aqiValue.setText(String.valueOf(weather.aqi));
            aqiValue.setTextColor(Weather.aqiColor(weather.aqi));
            aqiBand.setText(Weather.aqiLabel(weather.aqi));
            List<String> parts = new ArrayList<>();
            if (weather.pm25 >= 0) parts.add("PM2.5 " + Math.round(weather.pm25));
            if (weather.pm10 >= 0) parts.add("PM10 " + Math.round(weather.pm10));
            aqiParts.setText(TextUtils.join("  ·  ", parts) + (parts.isEmpty() ? "" : " µg/m³"));
        } else {
            aqiValue.setText("–");
            aqiValue.setTextColor(0xFFFFFFFF);
            aqiBand.setText("Not available here");
            aqiParts.setText("");
        }
        updated.setText("Updated " + new java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(new java.util.Date(weather.at)));
        buildDays();
    }

    private void buildDays() {
        int focused = -1;
        for (int i = 0; i < dayCards.size(); i++) if (dayCards.get(i).hasFocus()) focused = i;
        daysRow.removeAllViews();
        dayCards.clear();
        List<Weather.Day> days = weather.hasReading() ? weather.days : new ArrayList<>();
        for (int i = 0; i < days.size() && i < Weather.DAYS; i++) {
            View card = dayCard(days.get(i), i);
            dayCards.add(card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart(dp(6));
            daysRow.addView(card, lp);
        }
        if (focused >= 0 && focused < dayCards.size()) dayCards.get(focused).requestFocus();
    }

    private View dayCard(Weather.Day d, int index) {
        LinearLayout card = column();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(4), dp(12), dp(4), dp(12));
        card.setFocusable(true);
        card.setDefaultFocusHighlightEnabled(false);
        GradientDrawable rest = glass(0x14FFFFFF, 0x26FFFFFF, 18), focus = glass(0x40FFFFFF, 0xD9FFFFFF, 18);
        card.setBackground(rest);
        // Every line centred under the icon: a column's text views fill its width.
        TextView name = centred(text(14, 0xFFFFFFFF, R.string.font_medium));
        name.setText(dayName(d.date, index));
        card.addView(name);
        ImageView mark = new ImageView(context);
        mark.setImageResource(Weather.iconFor(Weather.kind(d.code, true)));
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        markLp.setMargins(0, dp(10), 0, dp(8));
        card.addView(mark, markLp);
        TextView hi = centred(text(19, 0xFFFFFFFF, R.string.font_regular));
        hi.setText(weather.degrees(d.max));
        card.addView(hi);
        TextView lo = centred(text(14, 0x8CFFFFFF, R.string.font_regular));
        lo.setText(weather.degrees(d.min));
        card.addView(lo);
        TextView rain = centred(text(11, 0x99FFFFFF, R.string.font_regular));
        rain.setText(d.rain >= 0 ? "Rain " + d.rain + "%" : "");
        rain.setPadding(0, dp(8), 0, 0);
        card.addView(rain);
        LinearLayout air = row();
        air.setGravity(Gravity.CENTER);
        air.setPadding(0, dp(6), 0, 0);
        if (d.aqi >= 0) {
            View dot = new View(context);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Weather.aqiColor(d.aqi));
            dot.setBackground(circle);
            air.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        }
        TextView aqi = text(11, 0xB3FFFFFF, R.string.font_regular);
        aqi.setText(d.aqi >= 0 ? "AQI " + d.aqi : "AQI –");
        aqi.setPadding(dp(5), 0, 0, 0);
        air.addView(aqi);
        card.addView(air);
        card.setContentDescription(name.getText() + ", " + Weather.describe(d.code) + ", high " + hi.getText() + ", low " + lo.getText());
        card.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? focus : rest);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(160).start();
        });
        card.setOnKeyListener((v, key, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (index > 0) dayCards.get(index - 1).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (index < dayCards.size() - 1) dayCards.get(index + 1).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    (index >= dayCards.size() / 2 ? unitChip : locationChip).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    close();
                    return true;
                default:
                    return false;
            }
        });
        return card;
    }

    /** "Today", "Tomorrow", then the weekday ("Mon"). */
    static String dayName(String isoDate, int index) {
        if (index == 0) return "Today";
        try {
            return LocalDate.parse(isoDate).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
        } catch (Exception e) {
            return "";
        }
    }

    private boolean settingsKey(int key, KeyEvent e, int which) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (which == 1) locationChip.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (which == 0) unitChip.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (!dayCards.isEmpty()) dayCards.get(which == 0 ? 0 : dayCards.size() - 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (e.getRepeatCount() > 0) return true;
                if (which == 0) showFinder();
                else weather.setFahrenheit(!weather.fahrenheit());
                return true;
            case KeyEvent.KEYCODE_BACK:
                close();
                return true;
            default:
                return false;
        }
    }

    // ---- the place finder ------------------------------------------------------------------------

    private void showFinder() {
        finding = true;
        request++;
        main.setVisibility(View.GONE);
        finder.setVisibility(View.VISIBLE);
        finderField.setText("");
        finderRows.removeAllViews();
        finderResults.clear();
        finderStatus.setText("Type a city or town, then press search on the keyboard");
        finderField.requestFocus();
        handler.postDelayed(this::showKeyboard, 120);
    }

    private void findPlaces() {
        String words = finderField.getText().toString().trim();
        if (words.isEmpty()) return;
        hideKeyboard();
        int my = ++request;
        finderStatus.setText("Looking for places…");
        finderRows.removeAllViews();
        finderResults.clear();
        io.execute(() -> {
            List<String[]> found = null;
            try {
                found = places(words);
            } catch (Exception ignored) {
                // said below
            }
            final List<String[]> got = found;
            handler.post(() -> {
                if (my != request || !opened || !finding) return;
                if (got == null) {
                    finderStatus.setText("Couldn't look that up. Check the internet and try again.");
                    return;
                }
                if (got.isEmpty()) {
                    finderStatus.setText("No place called “" + words + "”.");
                    return;
                }
                finderStatus.setText("Pick the place");
                for (int i = 0; i < got.size(); i++) addPlace(got.get(i), i);
                finderResults.get(0).requestFocus();
            });
        });
    }

    /** Open-Meteo's matches: {name for the screen, region line, latitude, longitude}. */
    private static List<String[]> places(String words) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&format=json&name="
                + java.net.URLEncoder.encode(words, "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        String body;
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
            body = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
        JSONArray results = new JSONObject(body).optJSONArray("results");
        List<String[]> out = new ArrayList<>();
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String name = r.optString("name"), region = r.optString("admin1"), district = r.optString("admin2"),
                    country = r.optString("country");
            String shown = region.isEmpty() || region.equals(name) ? name : name + ", " + region;
            String line = WheelFeeds.join(district.equals(name) ? "" : district, region, country);
            out.add(new String[]{shown, line, String.valueOf(r.optDouble("latitude")), String.valueOf(r.optDouble("longitude"))});
        }
        return out;
    }

    private void addPlace(String[] p, int index) {
        LinearLayout row = column();
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        row.setFocusable(true);
        row.setDefaultFocusHighlightEnabled(false);
        GradientDrawable focus = glass(0x40FFFFFF, 0xD9FFFFFF, 16);
        row.setOnFocusChangeListener((v, has) -> v.setBackground(has ? focus : null));
        TextView name = text(18, 0xFFFFFFFF, R.string.font_regular);
        name.setText(p[0]);
        TextView line = text(12, 0x8CFFFFFF, R.string.font_regular);
        line.setText(p[1]);
        row.addView(name);
        row.addView(line);
        row.setOnKeyListener((v, key, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (index > 0) finderResults.get(index - 1).requestFocus();
                    else finderField.requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (index < finderResults.size() - 1) finderResults.get(index + 1).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (e.getRepeatCount() > 0) return true;
                    try {
                        weather.setPlace(p[0], Double.parseDouble(p[2]), Double.parseDouble(p[3]));
                    } catch (NumberFormatException ignored) {
                        return true;
                    }
                    showMain(locationChip);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    finderField.requestFocus();
                    return true;
                default:
                    return false;
            }
        });
        finderResults.add(row);
        finderRows.addView(row);
    }

    // ---- bits ------------------------------------------------------------------------------------

    private TextView chip() {
        TextView chip = text(15, 0xFFFFFFFF, R.string.font_medium);
        chip.setPadding(dp(18), dp(10), dp(18), dp(10));
        chip.setFocusable(true);
        chip.setDefaultFocusHighlightEnabled(false);
        GradientDrawable rest = glass(0x1FFFFFFF, 0x38FFFFFF, 21), focus = glass(0xF5FFFFFF, 0xF5FFFFFF, 21);
        chip.setBackground(rest);
        chip.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? focus : rest);
            ((TextView) v).setTextColor(has ? 0xFF111111 : 0xFFFFFFFF);
        });
        return chip;
    }

    private static TextView centred(TextView t) {
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(12));
        return lp;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
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

    private void showKeyboard() {
        if (!opened || !finderField.hasFocus()) return;
        InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(finderField, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(finderField.getWindowToken(), 0);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
