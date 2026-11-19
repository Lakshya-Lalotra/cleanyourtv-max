package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HomeLookTest {
    @Test public void taglinesTakeTurnsPerPackage() {
        Taglines t = Taglines.parse(Arrays.asList(
                "com.netflix.ninja=Bazinga.", "com.netflix.ninja=Second line",
                "com.amazon.amazonvideo.livingroom=Run, Barry, run.", "broken", "=no package", "pkg="));
        assertEquals("Bazinga.", t.pick("com.netflix.ninja", 0));
        assertEquals("Second line", t.pick("com.netflix.ninja", 1));
        assertEquals("Bazinga.", t.pick("com.netflix.ninja", 2));
        assertEquals("Run, Barry, run.", t.pick("com.amazon.amazonvideo.livingroom", 7));
        // Home starts its count at -1 before the first start: still a line, not a crash.
        assertEquals("Second line", t.pick("com.netflix.ninja", -1));
        assertNull(t.pick("com.google.android.youtube.tv", 0));
        assertNull(t.pick(null, 0));
    }

    @Test public void weatherCodesMapToIcons() {
        assertEquals(Weather.SUN, Weather.kind(0, true));
        assertEquals(Weather.MOON, Weather.kind(0, false));
        assertEquals(Weather.PARTLY, Weather.kind(2, true));
        assertEquals(Weather.CLOUD, Weather.kind(3, true));
        assertEquals(Weather.FOG, Weather.kind(45, true));
        assertEquals(Weather.RAIN, Weather.kind(61, true));
        assertEquals(Weather.RAIN, Weather.kind(81, false));
        assertEquals(Weather.SNOW, Weather.kind(73, true));
        assertEquals(Weather.STORM, Weather.kind(95, true));
        assertEquals(Weather.CLOUD, Weather.kind(-1, true));
    }

    @Test public void temperatureIsWholeDegreesCelsius() {
        assertEquals("18°C", Weather.format(17.6));
        assertEquals("-3°C", Weather.format(-3.2));
        assertEquals("0°C", Weather.format(-0.4));
    }

    @Test public void fahrenheitWhenTheOwnerPicksIt() {
        assertEquals("77°F", Weather.format(25.0, true));
        assertEquals("32°F", Weather.format(0.0, true));
        assertEquals("25°C", Weather.format(25.0, false));
    }

    @Test public void dailyAirQualityIsEachDaysWorstHour() {
        java.util.Map<String, Integer> days = Weather.dailyMax(
                Arrays.asList("2026-11-07T00:00", "2026-11-07T13:00", "2026-11-08T05:00", "2026-11-08T06:00", "bad"),
                Arrays.asList(90, 106, null, 116, 300));
        assertEquals(2, days.size());
        assertEquals(Integer.valueOf(106), days.get("2026-11-07"));
        assertEquals(Integer.valueOf(116), days.get("2026-11-08"));
    }

    @Test public void airQualityBands() {
        assertEquals("Good", Weather.aqiLabel(42));
        assertEquals("Moderate", Weather.aqiLabel(100));
        assertEquals("Unhealthy for sensitive groups", Weather.aqiLabel(102));
        assertEquals("Hazardous", Weather.aqiLabel(420));
        assertEquals("", Weather.aqiLabel(-1));
        assertEquals("Clear", Weather.describe(0));
        assertEquals("Thunderstorm", Weather.describe(95));
    }

    @Test public void settingsWordsForSizesNetworksAndBands() {
        assertEquals("", SettingsPanel.sizeOf(0));
        assertEquals("1.5 MB", SettingsPanel.sizeOf(1_572_864L).replace(',', '.'));
        assertEquals("54 MB", SettingsPanel.sizeOf(56_623_104L));
        assertEquals("2.0 GB", SettingsPanel.sizeOf(2_147_483_648L).replace(',', '.'));
        assertEquals("16 GB", SettingsPanel.sizeOf(17_179_869_184L));
        assertEquals(true, SettingsPanel.secured("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"));
        assertEquals(true, SettingsPanel.secured("[RSN-SAE-CCMP][ESS]"));
        assertEquals(false, SettingsPanel.secured("[ESS]"));
        assertEquals("2.4 GHz", SettingsPanel.band(2412));
        assertEquals("5 GHz", SettingsPanel.band(5180));
        assertEquals("6 GHz", SettingsPanel.band(5955));
        assertEquals("", SettingsPanel.band(0));
    }

    @Test public void forecastDayNames() {
        assertEquals("Today", WeatherPanel.dayName("2026-11-07", 0));
        assertEquals("", WeatherPanel.dayName("not a date", 3));
    }

    @Test public void aReadingIsDueAfterThirtyMinutesOrAClockSetBack() {
        long now = 10_000_000L;
        assertEquals(Long.MAX_VALUE, Weather.age(now, 0));
        assertEquals(Long.MAX_VALUE, Weather.age(now, now + 1));
        assertEquals(60_000L, Weather.age(now, now - 60_000L));
    }

    @Test public void rowsUnderTheDockSpreadEdgeToEdgeOnAShallowArc() {
        // Five columns: the dock (0-4) as laid out, then Media, Play Store and RC Upgrade (5-7).
        assertEquals(3f, ArcRow.visualColumn(3, 8, 5), 0f);
        assertEquals(0f, ArcRow.visualColumn(5, 8, 5), 0f);
        assertEquals(2f, ArcRow.visualColumn(6, 8, 5), 0f);
        assertEquals(4f, ArcRow.visualColumn(7, 8, 5), 0f);
        assertEquals(2f, ArcRow.visualColumn(5, 6, 5), 0f);     // a lone tile sits in the middle
        assertEquals(1f, ArcRow.lift(2f, 5), 0f);
        assertEquals(0f, ArcRow.lift(0f, 5), 0f);
        assertEquals(0f, ArcRow.lift(4f, 5), 0f);
        assertEquals(0.75f, ArcRow.lift(1f, 5), 1e-6f);
    }

    @Test public void upAndDownGoToTheTileDrawnNearest() {
        assertEquals(6, ArcRow.nearestInRow(1, ArcRow.visualColumn(2, 8, 5), 8, 5));   // YouTube down: Play Store
        assertEquals(7, ArcRow.nearestInRow(1, ArcRow.visualColumn(4, 8, 5), 8, 5));   // Apple TV down: RC Upgrade
        assertEquals(4, ArcRow.nearestInRow(0, ArcRow.visualColumn(7, 8, 5), 8, 5));   // RC Upgrade up: Apple TV
        assertEquals(5, ArcRow.nearestInRow(1, ArcRow.visualColumn(1, 8, 5), 8, 5));   // Prime down: Media
        assertEquals(-1, ArcRow.nearestInRow(2, 0f, 8, 5));
    }

    @Test public void dockGlowStyleIsKnown() {
        assertEquals(Config.GLOW_DOCK, Config.glowStyle(" Dock "));
        assertEquals(Config.GLOW_HORIZON, Config.glowStyle("horizon"));
        assertEquals(Config.GLOW_RADIAL, Config.glowStyle("other"));
        assertEquals(Config.GLOW_RADIAL, Config.glowStyle(null));
    }
}
