package com.gathin.tvhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** The Wizarding World page, top to bottom: the eight Harry Potter films, the three Fantastic Beasts, and the series to come. */
final class WizardData {
    private WizardData() {
    }

    static final class Title {
        final String key, name, lookupName, kind, about;
        /** The year it came out, or 0 for one that has not. */
        final int year;

        Title(String key, String name, String lookupName, String kind, int year, String about) {
            this.key = key;
            this.name = name;
            this.lookupName = lookupName;
            this.kind = kind;
            this.year = year;
            this.about = about;
        }

        boolean soon() {
            return year == 0;
        }

        boolean isSeries() {
            return "Series".equals(kind);
        }
    }

    private static final List<Title> ALL = Collections.unmodifiableList(Arrays.asList(
            new Title("hp1", "The Philosopher's Stone", "Harry Potter and the Philosopher's Stone", "Film", 2001,
                    "On his eleventh birthday an orphan learns he is a wizard. At Hogwarts he finds friends, a home, and "
                            + "the first trace of the dark wizard who killed his parents."),
            new Title("hp2", "The Chamber of Secrets", "Harry Potter and the Chamber of Secrets", "Film", 2002,
                    "Something is turning students to stone, and a voice only Harry hears leads him to a chamber hidden "
                            + "under the school for a thousand years."),
            new Title("hp3", "The Prisoner of Azkaban", "Harry Potter and the Prisoner of Azkaban", "Film", 2004,
                    "A murderer has escaped the wizard prison and is said to be hunting Harry, while the prison's guards "
                            + "drain the warmth from everything they pass."),
            new Title("hp4", "The Goblet of Fire", "Harry Potter and the Goblet of Fire", "Film", 2005,
                    "Harry's name comes out of the Goblet for a tournament he never entered, and its last task ends in a "
                            + "graveyard where an old enemy returns."),
            new Title("hp5", "The Order of the Phoenix", "Harry Potter and the Order of the Phoenix", "Film", 2007,
                    "The Ministry denies that Voldemort is back and sends a teacher to keep Hogwarts quiet, so Harry "
                            + "trains his friends to fight in secret."),
            new Title("hp6", "The Half-Blood Prince", "Harry Potter and the Half-Blood Prince", "Film", 2009,
                    "Dumbledore shows Harry the memories that explain how Voldemort cannot die, as an old schoolbook "
                            + "makes Harry suspiciously good at potions."),
            new Title("hp7", "The Deathly Hallows: Part 1", "Harry Potter and the Deathly Hallows: Part 1", "Film", 2010,
                    "On the run with no school to return to, Harry, Ron and Hermione hunt the hidden pieces of "
                            + "Voldemort's soul and learn of three legendary objects."),
            new Title("hp8", "The Deathly Hallows: Part 2", "Harry Potter and the Deathly Hallows: Part 2", "Film", 2011,
                    "The hunt ends where it began. Hogwarts becomes a battlefield, and Harry walks into the forest to "
                            + "face Voldemort for the last time."),
            new Title("fb1", "Fantastic Beasts and Where to Find Them", "Fantastic Beasts and Where to Find Them", "Film", 2016,
                    "New York, 1926. Newt Scamander's case of magical creatures springs open, just as something far "
                            + "darker is tearing through the city."),
            new Title("fb2", "The Crimes of Grindelwald", "Fantastic Beasts: The Crimes of Grindelwald", "Film", 2018,
                    "The dark wizard Grindelwald escapes and gathers followers in Paris, and a young Dumbledore sends "
                            + "Newt where he cannot go himself."),
            new Title("fb3", "The Secrets of Dumbledore", "Fantastic Beasts: The Secrets of Dumbledore", "Film", 2022,
                    "Dumbledore cannot move against Grindelwald himself, so he sends Newt and an unlikely team to stop "
                            + "him taking the wizarding world by election."),
            new Title("series", "Harry Potter: The Series", "Harry Potter", "Series", 0,
                    "The seven books told again from the beginning, a season for each, with a new cast. Hedwig will "
                            + "bring word when it arrives.")));

    static List<Title> all() {
        return ALL;
    }

    /** The two ways into the world: everything that is a film, and everything that is a series. */
    static List<Title> films() {
        return ALL.subList(0, ALL.size() - 1);
    }

    static List<Title> series() {
        return ALL.subList(ALL.size() - 1, ALL.size());
    }

    /**
     * The app to open a title in, from what JustWatch lists for the country now: the first one that streams it and has a TV
     * app. Null when the answer is about another title of the same name, or nothing streams it.
     */
    static StreamSearch.Offer pick(Title title, List<StreamSearch.Title> found) {
        for (StreamSearch.Title candidate : found) {
            if (!letters(title.lookupName).equals(letters(candidate.name))) continue;
            if (title.isSeries() != candidate.meta.contains("Series")) continue;
            for (StreamSearch.Offer offer : candidate.offers) {
                boolean streams = offer.kinds.contains("Stream") || offer.kinds.contains("Free");
                if (streams && !offer.pkg.isEmpty()) return offer;
            }
            return null;
        }
        return null;
    }

    private static String letters(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
