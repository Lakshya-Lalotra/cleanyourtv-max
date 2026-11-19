package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Google's Gemini API, for any feature that wants an answer about the world right now: search today. Answers are
 * grounded with Google Search. The key is the owner's own, from Google AI Studio, and never in the source: it is
 * dropped off over ADB as gemini_key.txt in the launcher's external files folder (a bare key or a .env line,
 * GEMINI_API_KEY=...), and the first load moves it into the launcher's private storage, which only the launcher can
 * read (no backups: allowBackup is false), and deletes the drop-off. config.xml's gemini_api_key is the last resort.
 * Without a key, load() is null and callers use their keyless sources. Called off the UI thread.
 */
final class Gemini {
    /** The drop-off, in getExternalFilesDir: readable over ADB, so it is moved and deleted on first use. */
    static final String KEY_FILE = "gemini_key.txt";
    /** Where the key lives after that, in getFilesDir: the launcher's alone. */
    private static final String PRIVATE_FILE = "gemini.key";
    private final String key, model;
    /** config.xml gemini_grounding: ask with Google Search behind the answers (the free tier refuses it). */
    private final boolean grounding;

    private Gemini(String key, String model, boolean grounding) {
        this.key = key;
        this.model = model;
        this.grounding = grounding;
    }

    /** The owner's Gemini, or null when no key is set. */
    static Gemini load(Context context) {
        File dir = context.getExternalFilesDir(null);
        File dropOff = dir == null ? null : new File(dir, KEY_FILE);
        if (dropOff != null && dropOff.isFile()) {
            String dropped = dropOff.length() < 2048 ? parseKey(readText(dropOff)) : "";
            if (!dropped.isEmpty()) {
                try (OutputStream out = context.openFileOutput(PRIVATE_FILE, Context.MODE_PRIVATE)) {
                    out.write(dropped.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                    // kept in the drop-off for the next try
                    dropped = "";
                }
            }
            if (!dropped.isEmpty() || dropOff.length() >= 2048) dropOff.delete();
        }
        String key = parseKey(readText(new File(context.getFilesDir(), PRIVATE_FILE)));
        if (key.isEmpty()) key = parseKey(context.getString(R.string.gemini_api_key));
        String model = context.getString(R.string.gemini_model).trim();
        return key.isEmpty() || model.isEmpty() ? null
                : new Gemini(key, model, context.getResources().getBoolean(R.bool.gemini_grounding));
    }

    /**
     * The key in a bare key file or a .env file: the first line that is not blank or a # comment, with any NAME=
     * before it and quotes around it taken off ("GEMINI_API_KEY=abc", "export GEMINI_API_KEY='abc'", "abc").
     */
    static String parseKey(String text) {
        if (text == null) return "";
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("export ")) line = line.substring(7).trim();
            int eq = line.indexOf('=');
            if (eq > 0 && line.substring(0, eq).trim().matches("[A-Za-z_][A-Za-z0-9_]*")) line = line.substring(eq + 1).trim();
            if (line.length() >= 2 && (line.startsWith("\"") && line.endsWith("\"") || line.startsWith("'") && line.endsWith("'"))) {
                line = line.substring(1, line.length() - 1).trim();
            }
            if (!line.isEmpty()) return line;
        }
        return "";
    }

    private static String readText(File file) {
        if (!file.isFile()) return "";
        try (InputStream in = new FileInputStream(file)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Google Search behind the answers, when gemini_grounding asks for it. The free tier refuses it (429 on every
     * grounded request while plain ones are answered), so a refusal turns it off for an hour and the question is asked
     * again without it.
     */
    private static volatile long groundingOffUntil;
    private static final long GROUNDING_RETRY_MS = 60 * 60_000L;

    /** One question; the reply's text. */
    String ask(String prompt) throws Exception {
        return ask(prompt, null);
    }

    /**
     * One question; the reply's text. With a schema (Gemini's OpenAPI subset) the reply is JSON in that shape, which
     * keeps a title a title; Google Search answers can't be held to one, so a grounded request goes without it.
     */
    String ask(String prompt, JSONObject schema) throws Exception {
        boolean grounded = grounding && System.currentTimeMillis() >= groundingOffUntil;
        try {
            return ask(prompt, grounded, schema);
        } catch (Refused e) {
            if (!grounded) throw e;
            groundingOffUntil = System.currentTimeMillis() + GROUNDING_RETRY_MS;
            return ask(prompt, false, schema);
        }
    }

    /** A 429: over the quota, which for grounded requests means the tier has no Google Search. */
    private static final class Refused extends java.io.IOException {
        Refused() {
            super("Gemini answered 429");
        }
    }

    private String ask(String prompt, boolean grounded, JSONObject schema) throws Exception {
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))));
        if (grounded) body.put("tools", new JSONArray().put(new JSONObject().put("google_search", new JSONObject())));
        else if (schema != null) body.put("generationConfig", new JSONObject()
                .put("responseMimeType", "application/json").put("responseSchema", schema));
        HttpURLConnection c = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent").openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("x-goog-api-key", key);
        try {
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            String reply = in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
            if (code == 429) throw new Refused();
            if (code >= 400) throw new java.io.IOException("Gemini answered " + code);
            JSONArray parts = new JSONObject(reply).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts");
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) text.append(parts.getJSONObject(i).optString("text"));
            return text.toString();
        } finally {
            c.disconnect();
        }
    }

    /**
     * A ranked list about the topic, as wheel rows. Gemini is asked for a JSON array of title and info; the first
     * array in the reply is used, so a sentence around it does no harm.
     */
    List<WheelFeeds.Item> list(String topic, String querySuffix, int limit) throws Exception {
        String reply = ask("Using Google Search for the latest information, list the " + limit + " " + topic
                + " right now. Reply with only a JSON array, no other text: [{\"title\": \"...\", \"info\": \"...\"}]."
                + " info is at most 6 words (year, language, where to watch, or status). No numbering in the title.");
        return items(reply, querySuffix, limit);
    }

    /** The first JSON array in a reply, as rows; an array that cannot be read gives an empty list. */
    static List<WheelFeeds.Item> items(String reply, String querySuffix, int limit) throws Exception {
        int start = reply.indexOf('['), end = reply.lastIndexOf(']');
        List<WheelFeeds.Item> out = new ArrayList<>();
        if (start < 0 || end <= start) return out;
        JSONArray array = new JSONArray(reply.substring(start, end + 1));
        for (int i = 0; i < array.length() && out.size() < limit; i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) continue;
            String title = o.optString("title").trim();
            if (title.isEmpty()) continue;
            out.add(new WheelFeeds.Item(title, o.optString("info").trim(), (title + " " + querySuffix).trim()));
        }
        return out;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = stream.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
                if (out.size() > 1024 * 1024) throw new java.io.IOException("reply too large");
            }
            return out.toByteArray();
        }
    }
}
