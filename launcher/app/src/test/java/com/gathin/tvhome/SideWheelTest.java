package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SideWheelTest {
    private static final long NOW = WheelFeeds.time("2026-11-07T09:00Z");
    private static final long HOUR = 60 * 60 * 1000L;

    private static WheelFeeds.Match match(String name, String state, long start, boolean india) {
        return new WheelFeeds.Match(name, "League", state, "", "", start, india);
    }

    @Test public void indiaFirstThenLiveThenSoonestThenLatestResult() {
        List<WheelFeeds.Match> picked = SportsOrder.pick(Arrays.asList(
                match("Late result", "post", NOW - 2 * HOUR, false),
                match("Early result", "post", NOW - 9 * HOUR, false),
                match("Tonight", "pre", NOW + 6 * HOUR, false),
                match("Soon", "pre", NOW + HOUR, false),
                match("Live football", "in", NOW - HOUR, false),
                match("New Zealand v India", "in", NOW - 7 * HOUR, true),
                match("Next week", "pre", NOW + 7 * 24 * HOUR, false),
                match("", "in", NOW, false)), NOW, 10);
        String[] names = new String[picked.size()];
        for (int i = 0; i < names.length; i++) names[i] = picked.get(i).name;
        assertEquals(Arrays.asList("New Zealand v India", "Live football", "Soon", "Tonight", "Late result", "Early result"),
                Arrays.asList(names));
    }

    @Test public void theListStopsAtTheLimit() {
        assertEquals(1, SportsOrder.pick(Arrays.asList(match("A", "in", NOW, false), match("B", "in", NOW, false)), NOW, 1).size());
    }

    @Test public void wheelOptionsInOrderWithMoviesByTopic() {
        List<WheelFeeds.Section> s = WheelFeeds.sections("in");
        assertEquals(6, s.size());
        assertEquals("Movies", s.get(0).label);
        assertTrue(s.get(0).hasTopics());
        assertEquals("Trending Bollywood", s.get(0).topics.get(0).label);
        assertEquals("4431", s.get(0).topics.get(0).genre);
        assertEquals("us", s.get(0).topics.get(1).country);     // Trending Hollywood: the US chart
        assertFalse(s.get(1).hasTopics());
        assertEquals("Web search", s.get(5).label);
        assertTrue(s.get(5).isSearch());
        // Headlines and scores never come from Gemini.
        assertEquals(null, s.get(3).topics.get(0).gemini);
        assertEquals(null, s.get(4).topics.get(0).gemini);
    }

    @Test public void googleNewsTitlesLoseTheirSource() {
        assertEquals("Sonia Gandhi interview", WheelFeeds.headline("Sonia Gandhi interview - The Hindu", "The Hindu"));
        assertEquals("No source here", WheelFeeds.headline("No source here", null));
        assertEquals("Ends - with a dash", WheelFeeds.headline("Ends - with a dash", "NDTV"));
    }

    @Test public void releaseYearAndJoinedLines() {
        assertEquals("2026", WheelFeeds.year("30 July 2026"));
        assertEquals("", WheelFeeds.year("soon"));
        assertEquals("Horror  ·  2026", WheelFeeds.join("Horror", "", null, "2026"));
    }

    @Test public void espnTimesWithAndWithoutSeconds() {
        assertEquals(WheelFeeds.time("2026-11-07T02:00:00Z"), WheelFeeds.time("2026-11-07T02:00Z"));
        assertEquals(0, WheelFeeds.time("not a time"));
    }
}
