package com.gathin.tvhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Middle-earth's titles: the six films in story order for the beacons (the Hobbit's three, then the Lord of the Rings'), and
 * the series and the anime for Mordor. Titles are the Wizarding World's kind, so the same lookup finds where each streams.
 */
final class MiddleEarthData {
    /** Where the Lord of the Rings starts among the films: Bag End's middle lantern goes straight to it. */
    static final int FIRST_RING_FILM = 3;

    private MiddleEarthData() {
    }

    /** One title and the small line over its name, as the beacons and Mordor show it. */
    static final class Entry {
        final String kicker;
        final WizardData.Title title;

        Entry(String kicker, WizardData.Title title) {
            this.kicker = kicker;
            this.title = title;
        }
    }

    private static Entry film(String saga, String key, String name, String lookup, int year, String about) {
        return new Entry(saga, new WizardData.Title(key, name, lookup, "Film", year, about));
    }

    private static final List<Entry> FILMS = Collections.unmodifiableList(Arrays.asList(
            film("The Hobbit", "hob1", "An Unexpected Journey", "The Hobbit: An Unexpected Journey", 2012,
                    "A wizard and thirteen dwarves turn up at Bilbo Baggins's door."),
            film("The Hobbit", "hob2", "The Desolation of Smaug", "The Hobbit: The Desolation of Smaug", 2013,
                    "Through Mirkwood to the Lonely Mountain and the dragon on its gold."),
            film("The Hobbit", "hob3", "The Battle of the Five Armies", "The Hobbit: The Battle of the Five Armies", 2014,
                    "The dragon is loosed, and five armies march on the mountain."),
            film("The Lord of the Rings", "lotr1", "The Fellowship of the Ring", "The Lord of the Rings: The Fellowship of the Ring", 2001,
                    "Nine set out from Rivendell to carry the Ring to the one fire that can end it."),
            film("The Lord of the Rings", "lotr2", "The Two Towers", "The Lord of the Rings: The Two Towers", 2002,
                    "The Fellowship broken, Rohan makes its stand at Helm's Deep."),
            film("The Lord of the Rings", "lotr3", "The Return of the King", "The Lord of the Rings: The Return of the King", 2003,
                    "The beacons are lit, and two hobbits climb Mount Doom.")));

    private static final List<Entry> SHOWS = Collections.unmodifiableList(Arrays.asList(
            new Entry("The Rings of Power", new WizardData.Title("rop1", "Season 1", "The Lord of the Rings: The Rings of Power", "Series", 2022,
                    "The Second Age: the rings are forged, and a dark lord hides in plain sight.")),
            new Entry("The Rings of Power", new WizardData.Title("rop2", "Season 2", "The Lord of the Rings: The Rings of Power", "Series", 2024,
                    "Sauron returns in a fair form, and the rings of the elves are made.")),
            new Entry("Anime, 2024", new WizardData.Title("rohirrim", "The War of the Rohirrim", "The Lord of the Rings: The War of the Rohirrim",
                    "Anime film", 2024, "Helm Hammerhand, King of Rohan, and the siege that named Helm's Deep.")),
            new Entry("Coming in 2027", new WizardData.Title("gollum", "The Hunt for Gollum", "The Lord of the Rings: The Hunt for Gollum",
                    "Film", 0, "The search for the creature who knows where the Ring went."))));

    static List<Entry> films() {
        return FILMS;
    }

    static List<Entry> shows() {
        return SHOWS;
    }
}
