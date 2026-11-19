package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarvelDataTest {
    private static List<String> keys(MarvelData.Order order) {
        List<String> keys = new ArrayList<>();
        for (MarvelData.Stop stop : order.stops) keys.add(stop.title.key);
        return keys;
    }

    @Test public void bothMcuPathsHoldTheSameTitlesOnce() {
        List<String> chrono = keys(MarvelData.chronological());
        List<String> phases = keys(MarvelData.phases());
        assertEquals(new HashSet<>(chrono).size(), chrono.size());
        assertEquals(new HashSet<>(phases).size(), phases.size());
        assertEquals(new HashSet<>(chrono), new HashSet<>(phases));
    }

    @Test public void chronologicalFollowsRottenTomatoesOrder() {
        List<String> chrono = keys(MarvelData.chronological());
        assertEquals(Arrays.asList("eow", "cap1", "ac1shot", "ac", "f4", "capm", "im1", "im2", "hulk", "thor"), chrono.subList(0, 10));
        assertEquals(80, chrono.size());
        assertEquals(Arrays.asList("bnd", "doom"), chrono.subList(chrono.size() - 2, chrono.size()));
        // The Netflix-era shows sit season by season where the story puts them.
        assertTrue(chrono.indexOf("dd1") < chrono.indexOf("aou") && chrono.indexOf("aou") < chrono.indexOf("dd2"));
        assertTrue(chrono.indexOf("def") < chrono.indexOf("cw") && chrono.indexOf("dd3") < chrono.indexOf("rag"));
    }

    @Test public void aLaterSeasonIsLookedUpByItsShowsFirstYear() {
        MarvelData.Order chrono = MarvelData.chronological();
        assertEquals(2015, MarvelData.lookupYear(chrono.stops.get(chrono.indexOf("dd3")).title));
        assertEquals(2021, MarvelData.lookupYear(chrono.stops.get(chrono.indexOf("loki2")).title));
        assertEquals(2008, MarvelData.lookupYear(chrono.stops.get(chrono.indexOf("im1")).title));
    }

    @Test public void phasesHaveTheirFullCountsInReleaseOrder() {
        MarvelData.Order phases = MarvelData.phases();
        int[] counts = {6, 11, 21, 18, 15, 9};
        for (int i = 0; i < counts.length; i++) assertEquals(counts[i], phases.chapters.get(i).titles.size());
        int year = 0;
        for (MarvelData.Stop stop : phases.stops) {
            assertTrue(stop.title.fullName + " is out of release order", stop.title.released >= year);
            year = stop.title.released;
        }
    }

    @Test public void everyTitleIsComplete() {
        Set<String> trailers = new HashSet<>();
        for (MarvelData.Stop stop : MarvelData.chronological().stops) {
            MarvelData.Title t = stop.title;
            assertFalse(t.key, t.shortName.isEmpty() || t.fullName.isEmpty() || t.story.isEmpty());
            assertTrue(t.key + " description fits two lines", t.about.length() > 20 && t.about.length() <= 100);
            // Empty only where Marvel's own channels have no trailer for the title: third-party uploads are not used.
            assertTrue(t.key + " trailer id", t.trailerId.isEmpty() || t.trailerId.matches("[A-Za-z0-9_-]{11}"));
            assertTrue(t.key + " has its own trailer", t.trailerId.isEmpty() || trailers.add(t.trailerId));
            assertTrue(t.key + " link", t.link.isEmpty() || t.link.startsWith("https://www.hotstar.com/in/"));
            // Only a /movies/ link can go straight to the film; any other kind stops on the film's page (seen on the TV).
            if ("Film".equals(t.kind)) assertTrue(t.key + " film link", t.link.isEmpty() || t.link.contains("/movies/"));
        }
    }

    @Test public void everyPhaseHasItsOwnColour() {
        for (MarvelData.Order order : new MarvelData.Order[]{MarvelData.chronological(), MarvelData.phases()}) {
            Set<Integer> colours = new HashSet<>();
            for (MarvelData.Chapter chapter : order.chapters) {
                assertTrue(chapter.label + " shares a colour", colours.add(chapter.color));
            }
        }
    }

    @Test public void lettersOnAPhaseColourStayReadable() {
        assertEquals(MarvelLook.INK, MarvelLook.onColor(0xFFD9A441));   // gold takes dark letters
        assertEquals(MarvelLook.INK, MarvelLook.onColor(0xFF2BB3A3));   // and so does teal
        assertEquals(0xFFFFFFFF, MarvelLook.onColor(0xFF7A5CF5));       // violet takes white
        assertEquals(0xFFFFFFFF, MarvelLook.onColor(MarvelLook.RED));
        assertEquals(0xFF808080, MarvelLook.mix(0xFF000000, 0xFFFFFFFF, 0.5f) & 0xFFFFFFFF | 0xFF000000);
    }

    @Test public void aFilmLinkGoesToTheFilmAndASeriesLinkStaysAsItIs() {
        MarvelData.Title film = new MarvelData.Title("a", "A", "A", "A", "Film", 2008, "", "2010", "About",
                "https://www.hotstar.com/in/movies/a/1", "aaaaaaaaaaa");
        MarvelData.Title series = new MarvelData.Title("b", "B", "B", "B", "Series", 2021, "", "2023", "About",
                "https://www.hotstar.com/in/1", "bbbbbbbbbbb");
        MarvelData.Title none = new MarvelData.Title("c", "C", "C", "C", "Special", 2022, "", "2025", "About", "", "ccccccccccc");
        assertEquals("https://www.hotstar.com/in/movies/a/1/watch", MarvelData.playUrl(film));
        assertEquals("https://www.hotstar.com/in/1", MarvelData.playUrl(series));
        assertEquals("", MarvelData.playUrl(none));
    }

    @Test public void theStoryLineReadsAsAYearOrAPlace() {
        MarvelData.Order chrono = MarvelData.chronological();
        assertEquals("SET IN 1260 BC–1896", MarvelData.storyLine(chrono.stops.get(0).title));
        assertEquals("OUTSIDE TIME", MarvelData.storyLine(chrono.stops.get(chrono.indexOf("loki")).title));
        assertEquals("SET IN 2008", MarvelData.storyLine(chrono.stops.get(chrono.indexOf("im1")).title));
        assertEquals("SET IN 1943–45", MarvelData.storyLine(chrono.stops.get(1).title));
    }

    @Test public void switchingOrderFindsTheSameTitle() {
        MarvelData.Order chrono = MarvelData.chronological();
        MarvelData.Order phases = MarvelData.phases();
        for (MarvelData.Stop stop : chrono.stops) {
            assertEquals(stop.title.key, phases.stops.get(phases.indexOf(stop.title.key)).title.key);
        }
    }

    @Test public void xmenPathRunsInStoryOrderThroughTheSharedCardToLogan() {
        List<String> xmen = keys(MarvelData.xmen());
        assertEquals(Arrays.asList("xfc", "xdp", "xow", "xap", "xdpx", "xm1", "x2", "xls", "wol", "dp1", "dp2", "xnm", "dw", "log", "doom"), xmen);
        assertTrue(xmen.contains("dw"));
        assertTrue(xmen.contains("xm1"));
        assertTrue(xmen.contains("log"));
    }

    @Test public void spiderPathEndsAtDoomsday() {
        List<String> spider = keys(MarvelData.spider());
        assertEquals(new HashSet<>(spider).size(), spider.size());
        assertTrue(spider.size() >= 12);
        assertEquals("doom", spider.get(spider.size() - 1));
        assertTrue(spider.contains("sm1"));
        assertTrue(spider.contains("nwh"));
        assertTrue(spider.contains("ven1"));
    }

    @Test public void legacyPathEndsAtDoomsday() {
        List<String> legacy = keys(MarvelData.legacy());
        assertEquals(new HashSet<>(legacy).size(), legacy.size());
        assertTrue(legacy.size() >= 13);
        assertEquals("doom", legacy.get(legacy.size() - 1));
        assertTrue(legacy.contains("bld1"));
        assertTrue(legacy.contains("ff05"));
    }

    @Test public void sharedTitlesAreTheSameObject() {
        MarvelData.Order chrono = MarvelData.chronological();
        MarvelData.Order xmen = MarvelData.xmen();
        MarvelData.Order spider = MarvelData.spider();
        String dwKeyMcu = chrono.stops.get(chrono.indexOf("dw")).title.key;
        String dwKeyXmen = xmen.stops.get(xmen.indexOf("dw")).title.key;
        assertEquals(dwKeyMcu, dwKeyXmen);
        String nwhMcu = chrono.stops.get(chrono.indexOf("nwh")).title.key;
        String nwhSpider = spider.stops.get(spider.indexOf("nwh")).title.key;
        assertEquals(nwhMcu, nwhSpider);
    }

    @Test public void allUniversesAreAccessible() {
        assertEquals(4, MarvelData.UNIVERSES.length);
        for (String key : MarvelData.UNIVERSES) {
            MarvelData.Order o = MarvelData.order(key);
            assertFalse(key + " has stops", o.stops.isEmpty());
            assertFalse(key + " has label", MarvelData.universeLabel(key).isEmpty());
        }
        assertFalse("phases still works", MarvelData.phases().stops.isEmpty());
    }

    @Test public void doomsdayEndsEveryPathButTheXMens() {
        for (String key : MarvelData.UNIVERSES) {
            MarvelData.Order o = MarvelData.order(key);
            assertTrue(key + " contains doom", keys(o).contains("doom"));
        }
    }

    @Test public void hasTitleFindsSharedCards() {
        assertTrue(MarvelData.hasTitle(MarvelData.CHRONOLOGICAL, "dw"));
        assertTrue(MarvelData.hasTitle(MarvelData.XMEN, "dw"));
        assertFalse(MarvelData.hasTitle(MarvelData.SPIDER, "dw"));
        assertTrue(MarvelData.hasTitle(MarvelData.CHRONOLOGICAL, "nwh"));
        assertTrue(MarvelData.hasTitle(MarvelData.SPIDER, "nwh"));
        assertTrue(MarvelData.hasTitle(MarvelData.CHRONOLOGICAL, "doom"));
        assertTrue(MarvelData.hasTitle(MarvelData.XMEN, "doom"));
        assertTrue(MarvelData.hasTitle(MarvelData.SPIDER, "doom"));
        assertTrue(MarvelData.hasTitle(MarvelData.LEGACY, "doom"));
    }

    @Test public void universeLabelMatchesUserDesign() {
        assertEquals("MCU", MarvelData.universeLabel(MarvelData.CHRONOLOGICAL));
        assertEquals("MCU", MarvelData.universeLabel(MarvelData.PHASES));
        assertEquals("X-MEN", MarvelData.universeLabel(MarvelData.XMEN));
        assertEquals("SPIDER-VERSE", MarvelData.universeLabel(MarvelData.SPIDER));
        assertEquals("FOX", MarvelData.universeLabel(MarvelData.LEGACY));
    }
}
