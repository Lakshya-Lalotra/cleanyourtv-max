package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WizardDataTest {
    @Test public void thePageRunsFromHarryPotterThroughTheBeastsToTheSeries() {
        List<String> keys = new ArrayList<>();
        for (WizardData.Title t : WizardData.all()) keys.add(t.key);
        assertEquals(Arrays.asList("hp1", "hp2", "hp3", "hp4", "hp5", "hp6", "hp7", "hp8", "fb1", "fb2", "fb3", "series"), keys);
        assertEquals(keys.size(), new HashSet<>(keys).size());
    }

    @Test public void everyTitleIsCompleteAndOnlyTheSeriesIsStillToCome() {
        int year = 0;
        for (WizardData.Title t : WizardData.all()) {
            assertTrue(t.key, !t.name.isEmpty() && !t.lookupName.isEmpty());
            assertTrue(t.key + " description fits its block", t.about.length() > 60 && t.about.length() <= 190);
            assertEquals(t.key, "series".equals(t.key), t.soon());
            if (!t.soon()) {
                assertTrue(t.key + " is out of order", t.year >= year);
                year = t.year;
            }
        }
    }

    @Test public void theAppIsTheFirstThatStreamsTheSameTitle() {
        WizardData.Title film = WizardData.all().get(0);
        StreamSearch.Offer rent = new StreamSearch.Offer("Apple TV", "Rent  ·  Buy", "https://a", "com.apple.atve.androidtv.appletv");
        StreamSearch.Offer noApp = new StreamSearch.Offer("Somewhere", "Stream", "https://s", "");
        StreamSearch.Offer stream = new StreamSearch.Offer("JioHotstar", "Stream", "https://h", "in.startv.hotstar");
        StreamSearch.Title other = new StreamSearch.Title("Harry Potter 20th Anniversary: Return to Hogwarts", "2022  ·  Film",
                Collections.singletonList(stream));
        StreamSearch.Title same = new StreamSearch.Title("Harry Potter and the Philosopher's Stone", "2001  ·  Film",
                Arrays.asList(rent, noApp, stream));
        assertEquals(stream, WizardData.pick(film, Arrays.asList(other, same)));
        assertNull(WizardData.pick(film, Collections.singletonList(other)));
        assertNull(WizardData.pick(film, Collections.singletonList(
                new StreamSearch.Title(film.lookupName, "2001  ·  Film", Collections.singletonList(rent)))));
    }
}
