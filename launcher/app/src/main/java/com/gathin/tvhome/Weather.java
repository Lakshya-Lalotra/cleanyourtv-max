package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home's weather (config.xml weather): now, the next ten days and the air quality, from Open-Meteo, which needs no key.
 * The place and °C or °F are the owner's (WeatherPanel and the Settings menu change them; config.xml's latitude and
 * longitude are the first place). Fetched only while Home is showing and at most every 30 minutes, or at once when the
 * place changes; the last answer is kept, so Home shows it straight away. The Home widget and WeatherPanel listen.
 */
final class Weather {
    static final long INTERVAL_MS = 30 * 60_000L;
    /** A reading older than this is not shown at all (the TV was off, or offline, for a long time). */
    static final long STALE_MS = 3 * 60 * 60_000L;
    static final int DAYS = 10;
    private static final String PREFS = "weather";

    static final String SUN = "sun", MOON = "moon", PARTLY = "partly", CLOUD = "cloud", FOG = "fog",
            RAIN = "rain", SNOW = "snow", STORM = "storm";

    interface Listener {
        void onWeather(Weather weather);
    }

    /** One day of the forecast; aqi is that day's highest US AQI, or -1 past the air-quality forecast. */
    static final class Day {
        final String date;
        final int code, rain, aqi;
        final double min, max;

        Day(String date, int code, double min, double max, int rain, int aqi) {
            this.date = date;
            this.code = code;
            this.min = min;
            this.max = max;
            this.rain = rain;
            this.aqi = aqi;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final String defaultLat, defaultLon, defaultPlace;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Runnable tick = this::refresh;
    private boolean active, loading, closed, loaded;

    // The last answer, read on the UI thread.
    long at;
    double temp, feels, wind;
    int humidity = -1, code = -1, aqi = -1;
    boolean day = true;
    double pm25 = -1, pm10 = -1;
    List<Day> days = Collections.emptyList();

    Weather(Context context, String latitude, String longitude) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        defaultLat = latitude.trim();
        defaultLon = longitude.trim();
        defaultPlace = context.getString(R.string.weather_place).trim();
    }

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ---- the owner's choices ---------------------------------------------------------------------

    String latitude() {
        return prefs.getString("lat", defaultLat);
    }

    String longitude() {
        return prefs.getString("lon", defaultLon);
    }

    /** The place's name as picked (for example "Paris, France"), else config.xml's weather_place, which may be "". */
    String place() {
        return prefs.getString("place", defaultPlace);
    }

    boolean fahrenheit() {
        return prefs.getBoolean("fahrenheit", false);
    }

    void setFahrenheit(boolean on) {
        prefs.edit().putBoolean("fahrenheit", on).apply();
        notifyListeners();
    }

    /** A new place: its answer is fetched at once, and the old place's is dropped. */
    void setPlace(String name, double lat, double lon) {
        prefs.edit().putString("place", name)
                .putString("lat", String.format(Locale.ROOT, "%.4f", lat))
                .putString("lon", String.format(Locale.ROOT, "%.4f", lon))
                .remove("cache").remove("at").apply();
        at = 0;
        aqi = code = humidity = -1;
        days = Collections.emptyList();
        notifyListeners();
        fetch();
    }

    // ---- lifecycle -------------------------------------------------------------------------------

    /** Home is showing: the last answer at once, then a fetch if it is 30 minutes old. */
    void start() {
        if (closed || latitude().isEmpty() || longitude().isEmpty()) return;
        active = true;
        if (!loaded) {
            loaded = true;
            parse(prefs.getString("cache", ""), prefs.getLong("at", 0));
        }
        notifyListeners();
        refresh();
    }

    /** Home is hidden: no fetch until it shows again. */
    void stop() {
        active = false;
        handler.removeCallbacks(tick);
    }

    void close() {
        stop();
        closed = true;
        io.shutdownNow();
    }

    boolean hasReading() {
        return at > 0 && age(System.currentTimeMillis(), at) <= STALE_MS;
    }

    private void refresh() {
        handler.removeCallbacks(tick);
        if (!active) return;
        long age = age(System.currentTimeMillis(), at);
        if (age >= INTERVAL_MS) {
            fetch();
            handler.postDelayed(tick, INTERVAL_MS);
        } else {
            handler.postDelayed(tick, INTERVAL_MS - age);
        }
    }

    /** Milliseconds since the last reading; a clock set back counts as due, not as years ahead. */
    static long age(long now, long at) {
        return at <= 0 || now < at ? Long.MAX_VALUE : now - at;
    }

    private void fetch() {
        if (loading || closed) return;
        loading = true;
        String lat = latitude(), lon = longitude();
        String forecast = String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&forecast_days=%d&timezone=auto", lat, lon, DAYS);
        String air = String.format(Locale.ROOT, "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%s"
                + "&longitude=%s&current=us_aqi,pm2_5,pm10&hourly=us_aqi&forecast_days=7&timezone=auto", lat, lon);
        io.execute(() -> {
            String combined = null;
            try {
                JSONObject both = new JSONObject().put("forecast", new JSONObject(get(forecast)));
                try {
                    both.put("air", new JSONObject(get(air)));
                } catch (Exception ignored) {
                    // weather without the air quality
                }
                combined = both.toString();
            } catch (Exception ignored) {
                // offline or a bad answer: keep the last one and try again at the next turn
            }
            final String got = combined;
            handler.post(() -> {
                loading = false;
                if (closed || got == null || !lat.equals(latitude()) || !lon.equals(longitude())) return;
                long now = System.currentTimeMillis();
                if (!parse(got, now)) return;
                prefs.edit().putString("cache", got).putLong("at", now).apply();
                notifyListeners();
            });
        });
    }

    private void notifyListeners() {
        for (Listener l : listeners) l.onWeather(this);
    }

    /** Reads a kept or fresh answer into the fields; false when it cannot be read. */
    private boolean parse(String json, long when) {
        if (json == null || json.isEmpty()) return false;
        try {
            JSONObject both = new JSONObject(json);
            JSONObject f = both.getJSONObject("forecast");
            JSONObject now = f.getJSONObject("current");
            temp = now.getDouble("temperature_2m");
            feels = now.optDouble("apparent_temperature", temp);
            humidity = now.optInt("relative_humidity_2m", -1);
            wind = now.optDouble("wind_speed_10m", 0);
            code = now.optInt("weather_code", -1);
            day = now.optInt("is_day", 1) != 0;
            JSONObject air = both.optJSONObject("air");
            Map<String, Integer> dailyAqi = new LinkedHashMap<>();
            aqi = -1;
            pm25 = pm10 = -1;
            if (air != null) {
                JSONObject cur = air.optJSONObject("current");
                if (cur != null) {
                    aqi = cur.isNull("us_aqi") ? -1 : cur.optInt("us_aqi", -1);
                    pm25 = cur.optDouble("pm2_5", -1);
                    pm10 = cur.optDouble("pm10", -1);
                }
                JSONObject hourly = air.optJSONObject("hourly");
                if (hourly != null) {
                    JSONArray t = hourly.optJSONArray("time"), v = hourly.optJSONArray("us_aqi");
                    List<String> times = new ArrayList<>();
                    List<Integer> values = new ArrayList<>();
                    for (int i = 0; t != null && v != null && i < t.length() && i < v.length(); i++) {
                        times.add(t.optString(i));
                        values.add(v.isNull(i) ? null : v.optInt(i));
                    }
                    dailyAqi = dailyMax(times, values);
                }
            }
            JSONObject d = f.getJSONObject("daily");
            JSONArray dates = d.getJSONArray("time"), codes = d.optJSONArray("weather_code"),
                    hi = d.optJSONArray("temperature_2m_max"), lo = d.optJSONArray("temperature_2m_min"),
                    rain = d.optJSONArray("precipitation_probability_max");
            List<Day> list = new ArrayList<>();
            for (int i = 0; i < dates.length(); i++) {
                String date = dates.getString(i);
                Integer dayAqi = dailyAqi.get(date);
                list.add(new Day(date, codes == null ? -1 : codes.optInt(i, -1), lo == null ? 0 : lo.optDouble(i, 0),
                        hi == null ? 0 : hi.optDouble(i, 0), rain == null || rain.isNull(i) ? -1 : rain.optInt(i, -1),
                        dayAqi == null ? -1 : dayAqi));
            }
            days = list;
            at = when;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The highest hourly value per day ("2026-11-07T16:00" counts for 2026-11-07); missing hours are skipped. */
    static Map<String, Integer> dailyMax(List<String> times, List<Integer> values) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < times.size() && i < values.size(); i++) {
            Integer v = values.get(i);
            String t = times.get(i);
            if (v == null || t == null || t.length() < 10) continue;
            String date = t.substring(0, 10);
            Integer best = out.get(date);
            if (best == null || v > best) out.put(date, v);
        }
        return out;
    }

    private static String get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
                if (out.size() > 512 * 1024) throw new java.io.IOException("answer too large");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    // ---- words, icons and colours ----------------------------------------------------------------

    /** The temperature in the owner's unit. */
    String temperature(double celsius) {
        return format(celsius, fahrenheit());
    }

    /** Whole degrees with the unit: "18°C", "64°F". */
    static String format(double celsius, boolean fahrenheit) {
        return fahrenheit ? Math.round(celsius * 9 / 5 + 32) + "°F" : Math.round(celsius) + "°C";
    }

    /** Whole degrees Celsius: "18°C", "-3°C". */
    static String format(double celsius) {
        return format(celsius, false);
    }

    /** Whole degrees and the degree sign only, for the forecast's highs and lows: "18°". */
    String degrees(double celsius) {
        return (fahrenheit() ? Math.round(celsius * 9 / 5 + 32) : Math.round(celsius)) + "°";
    }

    /** The icon for a WMO weather code (Open-Meteo's weather_code). */
    static String kind(int code, boolean day) {
        if (code == 0) return day ? SUN : MOON;
        if (code == 1 || code == 2) return day ? PARTLY : MOON;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return RAIN;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return SNOW;
        if (code >= 95 && code <= 99) return STORM;
        return CLOUD;
    }

    /** The sky in words, for the weather screen. */
    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67 || code >= 80 && code <= 82) return "Rain";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "Snow";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "";
    }

    static int iconFor(String kind) {
        switch (kind) {
            case SUN: return R.drawable.ic_wx_sun;
            case MOON: return R.drawable.ic_wx_moon;
            case PARTLY: return R.drawable.ic_wx_partly;
            case FOG: return R.drawable.ic_wx_fog;
            case RAIN: return R.drawable.ic_wx_rain;
            case SNOW: return R.drawable.ic_wx_snow;
            case STORM: return R.drawable.ic_wx_storm;
            default: return R.drawable.ic_wx_cloud;
        }
    }

    /** The US AQI band's name. */
    static String aqiLabel(int aqi) {
        if (aqi < 0) return "";
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy for sensitive groups";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very unhealthy";
        return "Hazardous";
    }

    /** The US AQI band's colour, as an ARGB int. */
    static int aqiColor(int aqi) {
        if (aqi < 0) return 0x80FFFFFF;
        if (aqi <= 50) return 0xFF34C759;
        if (aqi <= 100) return 0xFFFFD60A;
        if (aqi <= 150) return 0xFFFF9F0A;
        if (aqi <= 200) return 0xFFFF453A;
        if (aqi <= 300) return 0xFFBF5AF2;
        return 0xFFA2273C;
    }
}
