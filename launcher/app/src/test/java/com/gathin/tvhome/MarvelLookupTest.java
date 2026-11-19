package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MarvelLookupTest {
    private static final MarvelData.Title FILM = new MarvelData.Title("a", "Echo", "Echo", "Echo", "Film", 2024, "", "2025", "About",
            "https://www.hotstar.com/in/movies/echo/1", "aaaaaaaaaaa");
    private static final MarvelData.Title NO_LINK = new MarvelData.Title("b", "B", "B", "B", "Special", 2022, "", "2025", "About", "", "bbbbbbbbbbb");

    private static StreamSearch.Offer offer(String platform, String kinds, String pkg) {
        return new StreamSearch.Offer(platform, kinds, "https://example.com/movies/x", pkg);
    }

    private static List<StreamSearch.Title> found(String name, String meta, StreamSearch.Offer... offers) {
        return Collections.singletonList(new StreamSearch.Title(name, meta, Arrays.asList(offers)));
    }

    @Test public void stillOnJioHotstarKeepsTheStandardLink() {
        assertNull(MarvelLookup.choose(FILM, found("Echo", "2024  ·  Film", offer("JioHotstar", "Stream", MarvelData.JIOHOTSTAR))));
    }

    @Test public void onlyElsewhereMovesTheButton() {
        MarvelLookup.Link link = MarvelLookup.choose(FILM, found("Echo", "2024  ·  Film", offer("Netflix", "Stream", "com.netflix.ninja")));
        assertEquals("Netflix", link.platform);
        assertEquals("com.netflix.ninja", link.pkg);
    }

    @Test public void anotherTitleOfTheSameNameIsIgnored() {
        assertNull(MarvelLookup.choose(FILM, found("Echo", "2024  ·  Series", offer("Netflix", "Stream", "com.netflix.ninja"))));
        assertNull(MarvelLookup.choose(FILM, found("Echoes", "2024  ·  Film", offer("Netflix", "Stream", "com.netflix.ninja"))));
    }

    @Test public void rentAndBuyNeverReplaceJioHotstar() {
        assertNull(MarvelLookup.choose(FILM, found("Echo", "2024  ·  Film", offer("Apple TV", "Rent  ·  Buy", "com.apple.atve.androidtv.appletv"))));
    }

    @Test public void aTitleWithNoLinkTakesTheOneJustWatchHasNow() {
        MarvelLookup.Link link = MarvelLookup.choose(NO_LINK, found("B", "2022  ·  Film", offer("JioHotstar", "Stream", MarvelData.JIOHOTSTAR)));
        assertEquals("https://example.com/movies/x/watch", link.url);
    }
}
