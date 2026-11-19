package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MiddleEarthDataTest {
    private static List<String> keys(List<MiddleEarthData.Entry> entries) {
        List<String> keys = new ArrayList<>();
        for (MiddleEarthData.Entry e : entries) keys.add(e.title.key);
        return keys;
    }

    @Test public void theBeaconsRunThroughTheFilmsInStoryOrder() {
        assertEquals(Arrays.asList("hob1", "hob2", "hob3", "lotr1", "lotr2", "lotr3"), keys(MiddleEarthData.films()));
        assertEquals("lotr1", MiddleEarthData.films().get(MiddleEarthData.FIRST_RING_FILM).title.key);
    }

    @Test public void mordorHoldsTheSeriesTheAnimeAndWhatIsToCome() {
        assertEquals(Arrays.asList("rop1", "rop2", "rohirrim", "gollum"), keys(MiddleEarthData.shows()));
    }

    @Test public void everyTitleIsCompleteAndOnlyGollumIsStillToCome() {
        Set<String> seen = new HashSet<>();
        List<MiddleEarthData.Entry> all = new ArrayList<>(MiddleEarthData.films());
        all.addAll(MiddleEarthData.shows());
        for (MiddleEarthData.Entry e : all) {
            WizardData.Title t = e.title;
            assertTrue(t.key + " twice", seen.add(t.key));
            assertTrue(t.key, !e.kicker.isEmpty() && !t.name.isEmpty() && !t.lookupName.isEmpty());
            assertTrue(t.key + " line fits under the title", t.about.length() > 30 && t.about.length() <= 90);
            assertEquals(t.key, "gollum".equals(t.key), t.soon());
            assertEquals(t.key, t.key.startsWith("rop"), t.isSeries());
        }
    }
}
