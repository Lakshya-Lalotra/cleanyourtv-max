package com.gathin.tvhome;

import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * side_wheel's lists. Movies, shows and anime come from Gemini with Google Search when the owner has a key (Gemini),
 * and otherwise from free sources that need no key or account: the iTunes charts by genre, AniList. Headlines and
 * scores always come from Google News and ESPN, whose live feeds are more exact than a summary of them. Called off the
 * UI thread; fetch throws when nothing could be read, and the wheel says so.
 */
final class WheelFeeds {
    static final int LIMIT = 10;

    /** Where a topic's rows come from without Gemini (and always, for news and sports). */
    static final int ITUNES_MOVIES = 0, ITUNES_TV = 1, ANILIST = 2, GOOGLE_NEWS = 3, ESPN = 4;

    /** One list on the wheel: its name, what Gemini is asked for, and the keyless source. */
    static final class Topic {
        final String label, gemini, querySuffix;
        final int source;
        final String country, genre;

        Topic(String label, String gemini, String querySuffix, int source, String country, String genre) {
            this.label = label;
            this.gemini = gemini;
            this.querySuffix = querySuffix;
            this.source = source;
            this.country = country;
            this.genre = genre;
        }
    }

    /** A wheel option: one list, several topics to pick from first (Movies), or a web search (no topics). */
    static final class Section {
        final String label;
        final List<Topic> topics;

        Section(String label, List<Topic> topics) {
            this.label = label;
            this.topics = topics;
        }

        boolean isSearch() {
            return topics.isEmpty();
        }

        boolean hasTopics() {
            return topics.size() > 1;
        }
    }

    /** The wheel, top to bottom. country is the iTunes and Google News country ("in"). */
    static List<Section> sections(String country) {
        List<Topic> movies = new ArrayList<>();
        movies.add(movie("Trending Bollywood", "trending Bollywood (Hindi) movies in India", country, "4431"));
        movies.add(movie("Trending Hollywood", "trending Hollywood movies", "us", ""));
        movies.add(movie("South & Regional", "trending South Indian and regional Indian movies", country, "4432"));
        movies.add(movie("Top Action", "trending action movies", country, "4401"));
        movies.add(movie("Top Horror", "trending horror movies", country, "4408"));
        movies.add(movie("Top Romance", "trending romance movies", country, "4412"));
        movies.add(movie("Top Sci-Fi", "trending science fiction and fantasy movies", country, "4413"));
        movies.add(movie("Top Mystery & Thriller", "trending mystery and thriller movies", country, "4416"));
        movies.add(movie("Top Comedy", "trending comedy movies", country, "4404"));
        movies.add(movie("Top Drama", "trending drama movies", country, "4406"));
        movies.add(movie("Top Animation", "trending animated movies", country, "4402"));
        List<Section> out = new ArrayList<>();
        out.add(new Section("Movies", movies));
        out.add(new Section("Shows", Collections.singletonList(new Topic("Trending shows",
                "trending TV shows and web series in India", "trailer", ITUNES_TV, "us", ""))));
        out.add(new Section("Anime", Collections.singletonList(new Topic("Anime airing now",
                "most popular anime series airing this season", "anime trailer", ANILIST, country, ""))));
        out.add(new Section("News", Collections.singletonList(new Topic("Top news", null, "", GOOGLE_NEWS, country, ""))));
        out.add(new Section("Sports", Collections.singletonList(new Topic("Sports right now", null, "", ESPN, country, ""))));
        out.add(new Section("Web search", Collections.emptyList()));
        return out;
    }

    private static Topic movie(String label, String gemini, String country, String genre) {
        return new Topic(label, gemini, "trailer", ITUNES_MOVIES, country, genre);
    }

    /** The topic's rows: Gemini's when it has a key and answers, else the keyless source's. */
    static List<Item> fetch(Topic topic, Gemini gemini) throws Exception {
        if (gemini != null && topic.gemini != null) {
            try {
                List<Item> rows = gemini.list(topic.gemini, topic.querySuffix, LIMIT);
                if (!rows.isEmpty()) return rows;
            } catch (Exception ignored) {
                // over the free quota, offline or an odd reply: the keyless source below
            }
        }
        switch (topic.source) {
            case ITUNES_MOVIES: return itunes(topic.country, "topmovies", topic.genre);
            case ITUNES_TV: return itunes(topic.country, "toptvseasons", topic.genre);
            case ANILIST: return anime();
            case GOOGLE_NEWS: return news(topic.country);
            case ESPN: return sports();
            default: return Collections.emptyList();
        }
    }
    /** A response is cut off past this, so a source that changes its format cannot fill the TV's memory. */
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    /** One row of a list: what it shows, and what YouTube is searched for when it is opened. */
    static final class Item {
        final String title, subtitle, query;

        Item(String title, String subtitle, String query) {
            this.title = title;
            this.subtitle = subtitle;
            this.query = query;
        }
    }

    private WheelFeeds() {
    }

    // ---- movies and shows: the iTunes Store's charts, by country and genre -----------------------

    /** kind is topmovies or toptvseasons; genre an iTunes genre id ("4431" Bollywood), or empty for the whole chart. */
    private static List<Item> itunes(String country, String kind, String genre) throws Exception {
        JSONObject feed = new JSONObject(get("https://itunes.apple.com/" + country.toLowerCase(Locale.ROOT)
                + "/rss/" + kind + "/limit=" + LIMIT + (genre.isEmpty() ? "" : "/genre=" + genre) + "/json"))
                .getJSONObject("feed");
        // A chart of one entry comes as an object, not an array.
        JSONArray entries = feed.optJSONArray("entry");
        if (entries == null) entries = feed.optJSONObject("entry") == null ? new JSONArray() : new JSONArray().put(feed.getJSONObject("entry"));
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < entries.length() && out.size() < LIMIT; i++) {
            JSONObject e = entries.getJSONObject(i);
            String title = e.getJSONObject("im:name").getString("label");
            String category = e.optJSONObject("category") == null ? ""
                    : e.getJSONObject("category").getJSONObject("attributes").optString("label");
            String released = e.optJSONObject("im:releaseDate") == null ? ""
                    : e.getJSONObject("im:releaseDate").getJSONObject("attributes").optString("label");
            out.add(new Item(title, join(category, year(released)), title + " trailer"));
        }
        return out;
    }

    /** The year at the end of an iTunes release date ("30 July 2026"), or "" when there is none. */
    static String year(String released) {
        String r = released == null ? "" : released.trim();
        return r.length() >= 4 && r.substring(r.length() - 4).matches("\\d{4}") ? r.substring(r.length() - 4) : "";
    }

    // ---- anime: AniList's trending series that are airing now ------------------------------------

    private static List<Item> anime() throws Exception {
        String query = "{\"query\":\"{ Page(perPage: " + LIMIT + ") { media(type: ANIME, status: RELEASING, "
                + "sort: TRENDING_DESC, isAdult: false) { title { english romaji } averageScore "
                + "nextAiringEpisode { episode } } } }\"}";
        JSONArray media = new JSONObject(post("https://graphql.anilist.co", query))
                .getJSONObject("data").getJSONObject("Page").getJSONArray("media");
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < media.length(); i++) {
            JSONObject m = media.getJSONObject(i);
            JSONObject t = m.getJSONObject("title");
            String title = t.isNull("english") ? t.optString("romaji") : t.optString("english");
            JSONObject next = m.optJSONObject("nextAiringEpisode");
            String episode = next == null ? "" : "Episode " + Math.max(1, next.optInt("episode") - 1) + " out";
            String score = m.isNull("averageScore") ? "" : m.optInt("averageScore") + "% liked";
            out.add(new Item(title, join(episode, score), title + " anime trailer"));
        }
        return out;
    }

    // ---- news: Google News' top stories for the country ------------------------------------------

    private static List<Item> news(String country) throws Exception {
        String cc = country.toUpperCase(Locale.ROOT);
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new StringReader(get("https://news.google.com/rss?hl=en-" + cc + "&gl=" + cc + "&ceid=" + cc + ":en")));
        List<Item> out = new ArrayList<>();
        String title = null, source = null, tag = null;
        boolean inItem = false;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT && out.size() < LIMIT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) {
                tag = p.getName();
                if ("item".equals(tag)) {
                    inItem = true;
                    title = source = null;
                }
            } else if (ev == XmlPullParser.TEXT && inItem) {
                if ("title".equals(tag)) title = p.getText();
                else if ("source".equals(tag)) source = p.getText();
            } else if (ev == XmlPullParser.END_TAG) {
                if ("item".equals(p.getName()) && title != null) {
                    String headline = headline(title, source);
                    out.add(new Item(headline, source == null ? "" : source, headline));
                    inItem = false;
                }
                tag = null;
            }
        }
        return out;
    }

    /** Google News ends each title with " - Source"; the source has its own line. */
    static String headline(String title, String source) {
        String t = title.trim();
        if (source != null && t.endsWith(" - " + source.trim())) t = t.substring(0, t.length() - source.trim().length() - 3);
        return t.trim();
    }

    // ---- sports: ESPN's cricket across every series, and the day's football --------------------

    /** One match, as sorted for the list (SportsOrder). */
    static final class Match {
        final String name, league, state, detail, score;
        final long start;
        final boolean india;

        Match(String name, String league, String state, String detail, String score, long start, boolean india) {
            this.name = name;
            this.league = league;
            this.state = state;
            this.detail = detail;
            this.score = score;
            this.start = start;
            this.india = india;
        }
    }

    private static List<Item> sports() throws Exception {
        List<Match> all = new ArrayList<>();
        Exception last = null;
        try {
            JSONArray sportsArr = new JSONObject(get("https://site.web.api.espn.com/apis/v2/scoreboard/header"
                    + "?sport=cricket&lang=en&region=in")).getJSONArray("sports");
            for (int s = 0; s < sportsArr.length(); s++) {
                JSONArray leagues = sportsArr.getJSONObject(s).optJSONArray("leagues");
                for (int l = 0; leagues != null && l < leagues.length(); l++) {
                    JSONObject league = leagues.getJSONObject(l);
                    JSONArray events = league.optJSONArray("events");
                    for (int e = 0; events != null && e < events.length(); e++) {
                        all.add(cricket(events.getJSONObject(e), league.optString("name")));
                    }
                }
            }
        } catch (Exception e) {
            last = e;
        }
        for (String league : new String[]{"ind.1", "eng.1"}) {
            try {
                JSONObject board = new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                        + league + "/scoreboard"));
                String name = board.optJSONArray("leagues") == null ? ""
                        : board.getJSONArray("leagues").getJSONObject(0).optString("name");
                JSONArray events = board.optJSONArray("events");
                for (int e = 0; events != null && e < events.length(); e++) all.add(football(events.getJSONObject(e), name));
            } catch (Exception e) {
                last = e;
            }
        }
        if (all.isEmpty() && last != null) throw last;
        List<Item> out = new ArrayList<>();
        for (Match m : SportsOrder.pick(all, System.currentTimeMillis(), LIMIT)) {
            String when = "in".equals(m.state) ? "Live" : "pre".equals(m.state) ? m.detail : "Result";
            out.add(new Item(m.name, join(when, m.score.isEmpty() ? m.detail : m.score, m.league), m.name + ("in".equals(m.state) ? " live" : " highlights")));
        }
        return out;
    }

    private static Match cricket(JSONObject e, String league) {
        StringBuilder score = new StringBuilder();
        JSONArray teams = e.optJSONArray("competitors");
        boolean india = false;
        for (int i = 0; teams != null && i < teams.length(); i++) {
            JSONObject t = teams.optJSONObject(i);
            if (t == null) continue;
            india |= "India".equals(t.optString("displayName"));
            String s = t.optString("score");
            if (!s.isEmpty()) score.append(score.length() > 0 ? "  " : "").append(t.optString("abbreviation")).append(' ').append(s);
        }
        JSONObject full = e.optJSONObject("fullStatus");
        String summary = full != null && !full.optString("summary").isEmpty() ? full.optString("summary") : e.optString("summary");
        String state = e.optString("status");
        String detail = "pre".equals(state) ? startTime(e.optString("date")) : summary;
        String line = "in".equals(state) && !summary.isEmpty() ? summary : score.toString();
        return new Match(e.optString("name"), league, state, detail, line, time(e.optString("date")), india);
    }

    private static Match football(JSONObject e, String league) {
        JSONObject status = e.optJSONObject("status");
        JSONObject type = status == null ? null : status.optJSONObject("type");
        String state = type == null ? "" : type.optString("state");
        String detail = type == null ? "" : type.optString("shortDetail");
        StringBuilder score = new StringBuilder();
        JSONArray comps = e.optJSONArray("competitions");
        JSONArray teams = comps == null || comps.length() == 0 ? null : comps.optJSONObject(0).optJSONArray("competitors");
        for (int i = 0; teams != null && i < teams.length() && !"pre".equals(state); i++) {
            JSONObject t = teams.optJSONObject(i);
            JSONObject team = t == null ? null : t.optJSONObject("team");
            if (team == null) continue;
            score.append(score.length() > 0 ? "  " : "").append(team.optString("abbreviation"))
                    .append(' ').append(t.optString("score"));
        }
        return new Match(e.optString("name"), league, state, "pre".equals(state) ? startTime(e.optString("date")) : detail,
                score.toString(), time(e.optString("date")), false);
    }

    /** ESPN's ISO time ("2026-11-07T02:00Z" or with seconds) in milliseconds; 0 when it cannot be read. */
    static long time(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        String s = iso.matches(".*T\\d\\d:\\d\\dZ$") ? iso.replace("Z", ":00Z") : iso;
        try {
            return Instant.parse(s).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String startTime(String iso) {
        long t = time(iso);
        if (t == 0) return "";
        return new java.text.SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(new java.util.Date(t));
    }

    /** The parts that are there, joined with a middle dot. */
    static String join(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            if (b.length() > 0) b.append("  \u00B7  ");
            b.append(p.trim());
        }
        return b.toString();
    }

    // ---- http ------------------------------------------------------------------------------------

    private static HttpURLConnection open(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) TvHome");
        return c;
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = open(address);
        try {
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static String post(String address, String json) throws Exception {
        HttpURLConnection c = open(address);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        try {
            try (OutputStream out = c.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = stream.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
                if (out.size() > MAX_BYTES) throw new java.io.IOException("response too large");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
