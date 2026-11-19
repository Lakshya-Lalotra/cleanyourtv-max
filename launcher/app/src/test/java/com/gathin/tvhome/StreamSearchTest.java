package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class StreamSearchTest {
    @Test public void oneNamePerPlatform() {
        assertEquals("Prime Video", StreamSearch.platformName("Amazon Prime Video"));
        assertEquals("Prime Video", StreamSearch.platformName("Amazon Prime Video with Ads"));
        assertEquals("Prime Video", StreamSearch.platformName("Amazon Video"));
        assertEquals("JioHotstar", StreamSearch.platformName("Jio Hotstar"));
        assertEquals("Apple TV", StreamSearch.platformName("Apple TV Store"));
        assertEquals("Netflix", StreamSearch.platformName("Netflix basic with Ads"));
        assertEquals("Zee5", StreamSearch.platformName("Zee5 with Ads"));
    }

    @Test public void offersInWords() {
        assertEquals("Stream", StreamSearch.kind("FLATRATE"));
        assertEquals("Rent", StreamSearch.kind("RENT"));
        assertEquals("Buy", StreamSearch.kind("BUY"));
        assertEquals("Free (ads)", StreamSearch.kind("ADS"));
        assertEquals("", StreamSearch.kind("CINEMA"));
    }

    @Test public void platformsToTheirTvApps() {
        assertEquals("com.netflix.ninja", StreamSearch.packageFor("Netflix"));
        assertEquals("com.amazon.amazonvideo.livingroom", StreamSearch.packageFor("Amazon Video"));
        assertEquals("in.startv.hotstar", StreamSearch.packageFor("JioHotstar"));
        assertEquals("com.apple.atve.androidtv.appletv", StreamSearch.packageFor("Apple TV Store"));
        assertEquals("", StreamSearch.packageFor("Movie Sphere+"));
    }

    @Test public void geminiKeyFromABareFileOrADotEnv() {
        assertEquals("AIzaKey123", Gemini.parseKey("AIzaKey123\n"));
        assertEquals("AIzaKey123", Gemini.parseKey("# my key\nGEMINI_API_KEY=AIzaKey123\r\nOTHER=x"));
        assertEquals("AIzaKey123", Gemini.parseKey("export GEMINI_API_KEY='AIzaKey123'"));
        assertEquals("AIzaKey123", Gemini.parseKey("GEMINI_API_KEY = \"AIzaKey123\""));
        assertEquals("", Gemini.parseKey("\n# nothing\n"));
        assertEquals("", Gemini.parseKey(null));
    }

    @Test public void streamingBeforeRentAndBuy() {
        StreamSearch.Offer apple = new StreamSearch.Offer("Apple TV", "Rent  ·  Buy", "", "");
        StreamSearch.Offer netflix = new StreamSearch.Offer("Netflix", "Stream", "", "");
        StreamSearch.Offer free = new StreamSearch.Offer("MX Player", "Free (ads)", "", "");
        List<StreamSearch.Offer> out = StreamSearch.sorted(Arrays.asList(apple, netflix, free));
        assertEquals(Arrays.asList(netflix, free, apple), out);
    }
}
