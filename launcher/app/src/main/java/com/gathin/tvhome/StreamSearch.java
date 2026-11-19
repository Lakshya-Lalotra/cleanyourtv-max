package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Home's own search: where a film or a series can be watched in the TV's country. JustWatch (no key) supplies what is
 * on which platform, with a link to the title there. With a Gemini key, Gemini and Google Search first work out which
 * titles the words mean (a description, a misspelling, a Hindi name), and JustWatch is asked about each; a title
 * JustWatch does not know keeps the platforms Gemini found. Called off the UI thread.
 */
final class StreamSearch {
    static final int MAX_TITLES = 5;

    /** One platform for a title: its name, how ("Stream", "Rent  ·  Buy"), a link to the title, and the TV app. */
    static final class Offer {
        final String platform;
        String kinds;
        final String url;
        final String pkg;

        Offer(String platform, String kinds, String url, String pkg) {
            this.platform = platform;
            this.kinds = kinds;
            this.url = url;
            this.pkg = pkg;
        }
    }

    static final class Title {
        final String name, meta;
        final List<Offer> offers;

        Title(String name, String meta, List<Offer> offers) {
            this.name = name;
            this.meta = meta;
            this.offers = offers;
        }
    }

    private StreamSearch() {
    }

    static List<Title> search(String words, String country, Gemini gemini) throws Exception {
        if (gemini != null) {
            try {
                List<Title> found = viaGemini(words, country, gemini);
                if (!found.isEmpty()) return found;
            } catch (Exception ignored) {
                // over the free quota, offline or an odd reply: JustWatch alone below
            }
        }
        return justWatch(words, country, MAX_TITLES, 0);
    }

    // ---- Gemini: which titles the words mean ----------------------------------------------------

    private static List<Title> viaGemini(String words, String country, Gemini gemini) throws Exception {
        String reply = gemini.ask("A TV viewer in " + countryName(country) + " searched for: \"" + words.replace("\"", "'")
                + "\". Find the films or series they most likely mean (at most " + MAX_TITLES
                + ", best match first) and where each can be watched in " + countryName(country) + ". "
                + "title is the official English title only; info is at most 8 words. Reply with only a JSON array:"
                + " [{\"title\": \"...\", \"year\": 2021, \"type\": \"movie or show\", \"info\": \"...\", \"platforms\": [\"Netflix\"]}].",
                titlesSchema());
        int start = reply.indexOf('['), end = reply.lastIndexOf(']');
        if (start < 0 || end <= start) return new ArrayList<>();
        JSONArray array = new JSONArray(reply.substring(start, end + 1));
        List<Title> out = new ArrayList<>();
        for (int i = 0; i < array.length() && out.size() < MAX_TITLES; i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null || o.optString("title").trim().isEmpty()) continue;
            String name = o.optString("title").trim();
            int year = o.optInt("year", 0);
            String type = "show".equalsIgnoreCase(o.optString("type")) ? "Series" : "Film";
            List<Title> exact = new ArrayList<>();
            try {
                exact = justWatch(name, country, 1, year);
            } catch (Exception ignored) {
                // Gemini's platforms below
            }
            if (!exact.isEmpty() && !exact.get(0).offers.isEmpty()) {
                Title t = exact.get(0);
                out.add(new Title(t.name, WheelFeeds.join(t.meta, o.optString("info")), t.offers));
                continue;
            }
            List<Offer> offers = new ArrayList<>();
            JSONArray platforms = o.optJSONArray("platforms");
            for (int p = 0; platforms != null && p < platforms.length(); p++) {
                // Today's name for what Gemini may remember by an old one ("Disney+ Hotstar" is JioHotstar).
                String platform = platformName(platforms.optString(p));
                if (!platform.isEmpty()) offers.add(new Offer(platform, "", "", packageFor(platform)));
            }
            out.add(new Title(name, WheelFeeds.join(year > 0 ? String.valueOf(year) : "", type, o.optString("info")), offers));
        }
        return out;
    }

    /** The shape Gemini answers in: an array of title, year, type, info and platforms, each always there. */
    private static JSONObject titlesSchema() throws Exception {
        JSONObject string = new JSONObject().put("type", "STRING");
        JSONObject item = new JSONObject().put("type", "OBJECT")
                .put("properties", new JSONObject()
                        .put("title", string)
                        .put("year", new JSONObject().put("type", "INTEGER"))
                        .put("type", new JSONObject().put("type", "STRING").put("enum", new JSONArray().put("movie").put("show")))
                        .put("info", string)
                        .put("platforms", new JSONObject().put("type", "ARRAY").put("items", string)))
                .put("required", new JSONArray().put("title").put("year").put("type").put("info").put("platforms"))
                .put("propertyOrdering", new JSONArray().put("title").put("year").put("type").put("info").put("platforms"));
        return new JSONObject().put("type", "ARRAY").put("items", item);
    }

    // ---- JustWatch: what is on which platform ---------------------------------------------------

    /** Up to first titles for the words; year, when not 0, must match within a year. */
    static List<Title> justWatch(String words, String country, int first, int year) throws Exception {
        JSONObject variables = new JSONObject()
                .put("country", country.toUpperCase(Locale.ROOT))
                .put("language", "en")
                .put("first", year > 0 ? 5 : first)
                .put("filter", new JSONObject().put("searchQuery", words));
        JSONObject body = new JSONObject().put("variables", variables).put("query",
                "query S($country: Country!, $language: Language!, $first: Int!, $filter: TitleFilter) {"
                        + " popularTitles(country: $country, first: $first, filter: $filter) { edges { node {"
                        + " objectType content(country: $country, language: $language) { title originalReleaseYear }"
                        + " offers(country: $country, platform: WEB) { monetizationType standardWebURL"
                        + " package { clearName technicalName } } } } } }");
        JSONArray edges = new JSONObject(post("https://apis.justwatch.com/graphql", body.toString()))
                .getJSONObject("data").getJSONObject("popularTitles").getJSONArray("edges");
        List<Title> out = new ArrayList<>();
        for (int i = 0; i < edges.length() && out.size() < first; i++) {
            JSONObject node = edges.getJSONObject(i).getJSONObject("node");
            JSONObject content = node.getJSONObject("content");
            int released = content.optInt("originalReleaseYear", 0);
            if (year > 0 && released > 0 && Math.abs(released - year) > 1) continue;
            Map<String, Offer> byPlatform = new LinkedHashMap<>();
            JSONArray offers = node.optJSONArray("offers");
            for (int o = 0; offers != null && o < offers.length(); o++) {
                JSONObject offer = offers.getJSONObject(o);
                JSONObject pack = offer.optJSONObject("package");
                if (pack == null) continue;
                String platform = platformName(pack.optString("clearName"));
                String kind = kind(offer.optString("monetizationType"));
                if (platform.isEmpty() || kind.isEmpty()) continue;
                Offer known = byPlatform.get(platform);
                if (known == null) {
                    byPlatform.put(platform, new Offer(platform, kind, offer.optString("standardWebURL"), packageFor(platform)));
                } else if (!known.kinds.contains(kind)) {
                    known.kinds = known.kinds + "  ·  " + kind;
                }
            }
            String type = "SHOW".equals(node.optString("objectType")) ? "Series" : "Film";
            out.add(new Title(content.optString("title"), WheelFeeds.join(released > 0 ? String.valueOf(released) : "", type),
                    sorted(new ArrayList<>(byPlatform.values()))));
        }
        return out;
    }

    /** Streaming and free first, then rent and buy; within each, the order JustWatch gave. */
    static List<Offer> sorted(List<Offer> offers) {
        List<Offer> out = new ArrayList<>();
        for (Offer o : offers) if (o.kinds.startsWith("Stream") || o.kinds.startsWith("Free")) out.add(o);
        for (Offer o : offers) if (!out.contains(o)) out.add(o);
        return out;
    }

    /** JustWatch's monetizationType as a word; "" for kinds not worth a chip (cinema tickets). */
    static String kind(String monetization) {
        switch (monetization) {
            case "FLATRATE": return "Stream";
            case "FREE": return "Free";
            case "ADS": return "Free (ads)";
            case "RENT": return "Rent";
            case "BUY": return "Buy";
            default: return "";
        }
    }

    /** One name per platform: "Amazon Prime Video with Ads" and "Amazon Video" (rentals) are Prime Video's. */
    static String platformName(String clearName) {
        String n = clearName == null ? "" : clearName.trim();
        String l = n.toLowerCase(Locale.ROOT);
        if (l.contains("prime video") || l.equals("amazon video")) return "Prime Video";
        if (l.contains("hotstar") || l.contains("jiocinema")) return "JioHotstar";
        if (l.contains("apple tv")) return "Apple TV";
        if (l.startsWith("netflix")) return "Netflix";
        if (l.startsWith("youtube")) return "YouTube";
        return n.replaceAll("(?i)\\s+with ads$", "");
    }

    /** The Android TV app for a platform name, or "" when there is none this launcher knows. */
    static String packageFor(String platform) {
        String l = platformName(platform).toLowerCase(Locale.ROOT);
        if (l.equals("netflix")) return "com.netflix.ninja";
        if (l.equals("prime video")) return "com.amazon.amazonvideo.livingroom";
        if (l.equals("jiohotstar")) return "in.startv.hotstar";
        if (l.equals("apple tv")) return "com.apple.atve.androidtv.appletv";
        if (l.equals("youtube")) return "com.google.android.youtube.tv";
        if (l.startsWith("zee5")) return "com.graymatrix.did";
        if (l.startsWith("sony liv") || l.startsWith("sonyliv")) return "com.sonyliv";
        if (l.startsWith("sun nxt")) return "com.suntv.sunnxt";
        if (l.startsWith("mx player")) return "com.mxtech.videoplayer.television";
        if (l.startsWith("aha")) return "ahaflix.tv";
        return "";
    }

    private static String countryName(String country) {
        return new Locale("", country.toUpperCase(Locale.ROOT)).getDisplayCountry(Locale.ENGLISH);
    }

    private static String post(String address, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) TvHome");
        try {
            try (OutputStream out = c.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                for (int n; (n = in.read(b)) > 0; ) {
                    buf.write(b, 0, n);
                    if (buf.size() > 1024 * 1024) throw new java.io.IOException("reply too large");
                }
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
