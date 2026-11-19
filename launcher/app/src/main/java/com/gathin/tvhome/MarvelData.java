package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Marvel screen's content. Written by work/scripts/gen_marvel.py from what JustWatch listed in India: change that, not this.
 * The chronological order is Rotten Tomatoes' (14 October 2026), season by season; the Phases are filled by release date.
 * Story years are Marvel's official timeline where it gives one, else the timeline Wikipedia keeps from on-screen dates.
 */
final class MarvelData {
    static final String JIOHOTSTAR = "in.startv.hotstar";

    private MarvelData() {
    }

    static final class Title {
        final String key, shortName, fullName, lookupName, kind;
        final int released;
        /** seasons is the line's middle part: "2 seasons", or a note such as "Not streaming yet". */
        final String seasons, story, about, link, trailerId;

        Title(String key, String shortName, String fullName, String lookupName, String kind, int released, String seasons,
                String story, String about, String link, String trailerId) {
            this.key = key;
            this.shortName = shortName;
            this.fullName = fullName;
            this.lookupName = lookupName;
            this.kind = kind;
            this.released = released;
            this.seasons = seasons;
            this.story = story;
            this.about = about;
            this.link = link;
            this.trailerId = trailerId;
        }

        boolean isSeries() {
            return "Series".equals(kind);
        }
    }

    static final class Chapter {
        final String label;
        final int color;
        final List<Title> titles;

        Chapter(String label, int color, Title... titles) {
            this.label = label;
            this.color = color;
            this.titles = Collections.unmodifiableList(Arrays.asList(titles));
        }
    }

    /** One title's place on a path. */
    static final class Stop {
        final Title title;
        final int chapter, inChapter;

        Stop(Title title, int chapter, int inChapter) {
            this.title = title;
            this.chapter = chapter;
            this.inChapter = inChapter;
        }
    }

    static final class Order {
        final String key;
        final List<Chapter> chapters;
        final List<Stop> stops;

        Order(String key, Chapter[] chapters) {
            this.key = key;
            this.chapters = Collections.unmodifiableList(Arrays.asList(chapters));
            List<Stop> all = new ArrayList<>();
            for (int c = 0; c < chapters.length; c++) {
                for (int k = 0; k < chapters[c].titles.size(); k++) all.add(new Stop(chapters[c].titles.get(k), c, k));
            }
            stops = Collections.unmodifiableList(all);
        }

        boolean isChronological() {
            return CHRONOLOGICAL.equals(key) || XMEN.equals(key);
        }

        /** Where a title sits on this path, or 0 when it is not on it. */
        int indexOf(String titleKey) {
            for (int i = 0; i < stops.size(); i++) if (stops.get(i).title.key.equals(titleKey)) return i;
            return 0;
        }
    }

    static final String CHRONOLOGICAL = "chrono";
    static final String PHASES = "phases";
    static final String XMEN = "xmen";
    static final String SPIDER = "spider";
    static final String LEGACY = "legacy";

    /** The four universe paths navigable with Up/Down. PHASES is a sub-mode of MCU, not its own row. */
    static final String[] UNIVERSES = {CHRONOLOGICAL, XMEN, SPIDER, LEGACY};

    static Order order(String key) {
        switch (key) {
            case PHASES: return phases();
            case XMEN: return xmen();
            case SPIDER: return spider();
            case LEGACY: return legacy();
            default: return chronological();
        }
    }

    static String universeLabel(String key) {
        switch (key) {
            case CHRONOLOGICAL: return "MCU";
            case PHASES: return "MCU";
            case XMEN: return "X-MEN";
            case SPIDER: return "SPIDER-VERSE";
            case LEGACY: return "FOX";
            default: return key.toUpperCase(java.util.Locale.ROOT);
        }
    }

    /** Whether a title key appears on the given order. */
    static boolean hasTitle(String orderKey, String titleKey) {
        Order o = order(orderKey);
        return o.indexOf(titleKey) > 0 || (!o.stops.isEmpty() && o.stops.get(0).title.key.equals(titleKey));
    }

    /** The link that starts a title. A film's own link stops on its page; /watch goes straight to the film. A series link already does. */
    static String playUrl(Title title) {
        if (title.link.isEmpty()) return "";
        return title.link.contains("/movies/") ? title.link + "/watch" : title.link;
    }

    /** The year to look a title up by: its show's first, since a later season has its own. */
    static int lookupYear(Title title) {
        int year = title.released;
        for (Stop stop : chronological().stops) {
            if (stop.title.lookupName.equals(title.lookupName)) year = Math.min(year, stop.title.released);
        }
        return year;
    }

    /** The line above a title on the chronological path: a year reads as Set in 1943, anything else as it stands. */
    static String storyLine(Title title) {
        boolean year = !title.story.isEmpty() && Character.isDigit(title.story.charAt(0));
        return year ? "SET IN " + title.story : title.story.toUpperCase(java.util.Locale.ROOT);
    }

    private static final Title IM1 = new Title("im1", "Iron Man", "Iron Man", "Iron Man", "Film",
            2008, "", "2008",
            "A weapons maker builds a suit of armour to escape captivity, then decides to keep it.",
            "https://www.hotstar.com/in/movies/iron-man/1660000038", "");
    private static final Title HULK = new Title("hulk", "The Incredible Hulk", "The Incredible Hulk", "The Incredible Hulk", "Film",
            2008, "", "2010",
            "Bruce Banner hides from the army while hunting for a cure for what he becomes.",
            "https://www.hotstar.com/in/movies/the-incredible-hulk/1000046231", "");
    private static final Title IM2 = new Title("im2", "Iron Man 2", "Iron Man 2", "Iron Man 2", "Film",
            2010, "", "2010",
            "The world wants Tony Stark's suit, and a rival with a grudge has built his own.",
            "https://www.hotstar.com/in/movies/iron-man-2/1660000039", "");
    private static final Title THOR = new Title("thor", "Thor", "Thor", "Thor", "Film",
            2011, "", "2010",
            "Banished to Earth without his hammer, a god has to learn what makes him worthy of it.",
            "https://www.hotstar.com/in/movies/thor/1660000044", "JOddp-nlNvQ");
    private static final Title CAP1 = new Title("cap1", "The First Avenger", "Captain America: The First Avenger", "Captain America: The First Avenger", "Film",
            2011, "", "1943–45",
            "A frail volunteer from Brooklyn becomes the Allies' super soldier in the Second World War.",
            "https://www.hotstar.com/in/movies/captain-america-the-first-avenger/1660000034", "JerVrbLldXw");
    private static final Title AVG = new Title("avg", "The Avengers", "The Avengers", "The Avengers", "Film",
            2012, "", "2012",
            "Six heroes who can barely stand each other must stop an alien army over New York.",
            "https://www.hotstar.com/in/movies/marvels-the-avengers/1660000015", "eOrNdBpGMv8");
    private static final Title IM3 = new Title("im3", "Iron Man 3", "Iron Man 3", "Iron Man 3", "Film",
            2013, "", "2013–14",
            "Shaken after New York, Tony loses his home to a new enemy and rebuilds with what he has.",
            "https://www.hotstar.com/in/movies/iron-man-3/1660000042", "YLorLVa95Xo");
    private static final Title TDW = new Title("tdw", "The Dark World", "Thor: The Dark World", "Thor: The Dark World", "Film",
            2013, "", "2013",
            "An ancient enemy wakes, and Thor needs his treacherous brother to stop it.",
            "https://www.hotstar.com/in/movies/thor-the-dark-world/1260018142", "npvJ9FTgZbM");
    private static final Title TWS = new Title("tws", "The Winter Soldier", "Captain America: The Winter Soldier", "Captain America: The Winter Soldier", "Film",
            2014, "", "2014",
            "Steve Rogers finds the agency he serves is rotten, and its assassin is someone he knows.",
            "https://www.hotstar.com/in/movies/captain-america-the-winter-soldier/1260016410", "7SlILk2WMTI");
    private static final Title GOTG = new Title("gotg", "Guardians of the Galaxy", "Guardians of the Galaxy", "Guardians of the Galaxy", "Film",
            2014, "", "2014",
            "A thief and four other misfits end up guarding a stone that could destroy a world.",
            "https://www.hotstar.com/in/movies/guardians-of-the-galaxy/1260018294", "d96cjJhvlMA");
    private static final Title AOU = new Title("aou", "Age of Ultron", "Avengers: Age of Ultron", "Avengers: Age of Ultron", "Film",
            2015, "", "2015",
            "Tony's peacekeeping AI decides the way to save the planet is to wipe out its people.",
            "https://www.hotstar.com/in/movies/marvels-avengers-age-of-ultron/1260018315", "JAUoeqvedMo");
    private static final Title ANT = new Title("ant", "Ant-Man", "Ant-Man", "Ant-Man", "Film",
            2015, "", "2015",
            "A burglar fresh out of prison gets a suit that shrinks him, and one heist to pull off.",
            "https://www.hotstar.com/in/movies/ant-man/1260018141", "pWdKf3MneyI");
    private static final Title CW = new Title("cw", "Civil War", "Captain America: Civil War", "Captain America: Civil War", "Film",
            2016, "", "2016",
            "A law to control heroes splits the Avengers, with Cap and Iron Man on opposite sides.",
            "https://www.hotstar.com/in/movies/captain-america-civil-war/1260016768", "dKrVegVI0Us");
    private static final Title DRS = new Title("drs", "Doctor Strange", "Doctor Strange", "Doctor Strange", "Film",
            2016, "", "2016–17",
            "A surgeon who has lost the use of his hands looks for a cure in Nepal and finds sorcery.",
            "https://www.hotstar.com/in/movies/doctor-strange/1260018179", "HSzx-zryEgM");
    private static final Title GOTG2 = new Title("gotg2", "Guardians Vol. 2", "Guardians of the Galaxy Vol. 2", "Guardians of the Galaxy Vol. 2", "Film",
            2017, "", "2014",
            "The Guardians meet Peter Quill's long-lost father, who is a lot more than a man.",
            "https://www.hotstar.com/in/movies/guardians-of-the-galaxy-vol-2/1260018295", "wUn05hdkhjM");
    private static final Title SMH = new Title("smh", "Homecoming", "Spider-Man: Homecoming", "Spider-Man: Homecoming", "Film",
            2017, "", "2016",
            "A teenager fresh from fighting beside the Avengers goes back to school and a very local villain.",
            "https://www.hotstar.com/in/movies/spider-man-homecoming/1271667616", "39udgGPyYMg");
    private static final Title RAG = new Title("rag", "Ragnarok", "Thor: Ragnarok", "Thor: Ragnarok", "Film",
            2017, "", "2017",
            "Stranded on a junk planet and forced to fight as a gladiator, Thor races to save Asgard.",
            "https://www.hotstar.com/in/movies/thor-ragnarok/1660010577", "ue80QwXMRHg");
    private static final Title BP = new Title("bp", "Black Panther", "Black Panther", "Black Panther", "Film",
            2018, "", "2016",
            "A new king of Wakanda is challenged by a rival with his own claim to the throne.",
            "https://www.hotstar.com/in/movies/black-panther/1660010672", "xjDjIWPwcPU");
    private static final Title IW = new Title("iw", "Infinity War", "Avengers: Infinity War", "Avengers: Infinity War", "Film",
            2018, "", "2018",
            "Thanos comes for the Infinity Stones, and every hero on Earth and beyond stands in the way.",
            "https://www.hotstar.com/in/movies/avengers-infinity-war/1660010677", "6ZfuNTqbHE8");
    private static final Title ANTW = new Title("antw", "Ant-Man and the Wasp", "Ant-Man and the Wasp", "Ant-Man and the Wasp", "Film",
            2018, "", "2018",
            "Under house arrest, Scott Lang joins Hope van Dyne on a rescue into the quantum realm.",
            "https://www.hotstar.com/in/movies/ant-man-and-the-wasp/1660010696", "8_rTIAOohas");
    private static final Title CAPM = new Title("capm", "Captain Marvel", "Captain Marvel", "Captain Marvel", "Film",
            2019, "", "1995",
            "A Kree soldier crash-lands in 1990s Los Angeles and starts remembering who she was.",
            "https://www.hotstar.com/in/movies/captain-marvel/1260014878", "Z1BCujX3pw8");
    private static final Title END = new Title("end", "Endgame", "Avengers: Endgame", "Avengers: Endgame", "Film",
            2019, "", "2018–2023",
            "After their worst defeat, the surviving Avengers attempt one last, impossible plan.",
            "https://www.hotstar.com/in/movies/avengers-endgame/1260013556", "TcMBFSGVi1c");
    private static final Title FFH = new Title("ffh", "Far From Home", "Spider-Man: Far From Home", "Spider-Man: Far From Home", "Film",
            2019, "", "2024",
            "Peter Parker's school trip to Europe turns into a fight against elemental monsters.",
            "https://www.hotstar.com/in/movies/spider-man-far-from-home/1271663449", "LFoz8ZJWmPs");
    private static final Title WV = new Title("wv", "WandaVision", "WandaVision", "WandaVision", "Series",
            2021, "", "2023",
            "Wanda and Vision live an odd sitcom life in a small town where nothing is quite right.",
            "https://www.hotstar.com/in/1260051735", "sj9J2ecsSpo");
    private static final Title FWS = new Title("fws", "Falcon and Winter Soldier", "The Falcon and the Winter Soldier", "The Falcon and the Winter Soldier", "Series",
            2021, "", "2024",
            "Sam Wilson and Bucky Barnes hunt a new threat while the world argues over Captain America's shield.",
            "https://www.hotstar.com/in/1260056499", "IWBsDaFWyTE");
    private static final Title LOKI = new Title("loki", "Loki S1", "Loki Season 1", "Loki", "Series",
            2021, "", "Outside time",
            "A runaway Loki is caught by a bureau that polices time, and put to work fixing what he broke.",
            "https://www.hotstar.com/in/1260063525", "nW948Va-l10");
    private static final Title BW = new Title("bw", "Black Widow", "Black Widow", "Black Widow", "Film",
            2021, "", "2016",
            "On the run, Natasha Romanoff returns to the family and the programme that made her.",
            "https://www.hotstar.com/in/movies/black-widow/1260067485", "ybji16u608U");
    private static final Title WHATIF = new Title("whatif", "What If...? S1", "What If...? Season 1", "What If...?", "Series",
            2021, "", "Multiverse",
            "One small change at a time, a watcher shows how the heroes' stories could have gone.",
            "https://www.hotstar.com/in/1260066060", "x9D0uUKJ5KI");
    private static final Title SC = new Title("sc", "Shang-Chi", "Shang-Chi and the Legend of the Ten Rings", "Shang-Chi and the Legend of the Ten Rings", "Film",
            2021, "", "2024",
            "A San Francisco valet is pulled back to the father and the ancient rings he ran away from.",
            "https://www.hotstar.com/in/movies/shang-chi-and-the-legend-of-the-ten-rings/1260072682", "8YjFbMbfXaQ");
    private static final Title ET = new Title("et", "Eternals", "Eternals", "Eternals", "Film",
            2021, "", "2024",
            "Immortals who have quietly guarded Earth for millennia reunite to face what is coming.",
            "https://www.hotstar.com/in/movies/eternals/1260077949", "x_me3xsvDgk");
    private static final Title HAWK = new Title("hawk", "Hawkeye", "Hawkeye", "Hawkeye", "Series",
            2021, "", "2024",
            "At Christmas in New York, Clint Barton and a young archer are hunted over his old past.",
            "https://www.hotstar.com/in/1260073689", "5VYb3B1ETlk");
    private static final Title NWH = new Title("nwh", "No Way Home", "Spider-Man: No Way Home", "Spider-Man: No Way Home", "Film",
            2021, "", "2024",
            "With his identity exposed, Peter asks for a spell that goes badly wrong across universes.",
            "https://www.hotstar.com/in/movies/spider-man-no-way-home/1271661204", "ZYzbalQ6Lg8");
    private static final Title MK = new Title("mk", "Moon Knight", "Moon Knight", "Moon Knight", "Series",
            2022, "", "2025",
            "A gift-shop worker finds he shares a body with a mercenary who serves an Egyptian god.",
            "https://www.hotstar.com/in/1260089807", "x7Krla_UxRg");
    private static final Title MOM = new Title("mom", "Multiverse of Madness", "Doctor Strange in the Multiverse of Madness", "Doctor Strange in the Multiverse of Madness", "Film",
            2022, "", "2024",
            "Strange protects a girl who can cross universes from a very powerful pursuer.",
            "https://www.hotstar.com/in/movies/doctor-strange-in-the-multiverse-of-madness/1260103761", "aWzlQ2N6qqg");
    private static final Title MSM = new Title("msm", "Ms. Marvel", "Ms. Marvel", "Ms. Marvel", "Series",
            2022, "", "2025",
            "A Pakistani-American teenager from Jersey City gets the powers she used to draw in fan art.",
            "https://www.hotstar.com/in/1260098460", "m9EX0f6V11Y");
    private static final Title LT = new Title("lt", "Love and Thunder", "Thor: Love and Thunder", "Thor: Love and Thunder", "Film",
            2022, "", "2025",
            "Thor leaves retirement to stop a killer of gods, and meets an old love holding his hammer.",
            "https://www.hotstar.com/in/movies/thor-love-and-thunder/1260110008", "Go8nTmfrQd8");
    private static final Title SHE = new Title("she", "She-Hulk", "She-Hulk: Attorney at Law", "She-Hulk: Attorney at Law", "Series",
            2022, "", "2025",
            "A lawyer who gains the Hulk's powers would still much rather just practise law.",
            "https://www.hotstar.com/in/1260103791", "u7JsKhI2An0");
    private static final Title WBN = new Title("wbn", "Werewolf by Night", "Werewolf by Night", "Werewolf by Night", "Special",
            2022, "", "2025",
            "Monster hunters gather at a dead man's mansion to compete for a powerful relic.",
            "", "bLEFqhS5WmI");
    private static final Title WF = new Title("wf", "Wakanda Forever", "Black Panther: Wakanda Forever", "Black Panther: Wakanda Forever", "Film",
            2022, "", "2025",
            "Wakanda mourns its king while an undersea empire rises to challenge it.",
            "https://www.hotstar.com/in/movies/black-panther-wakanda-forever/1260118821", "_Z3QKkl1WyM");
    private static final Title HOL = new Title("hol", "Holiday Special", "The Guardians of the Galaxy Holiday Special", "The Guardians of the Galaxy Holiday Special", "Special",
            2022, "", "2025",
            "To cheer up Peter Quill, Mantis and Drax fly to Earth to kidnap him a Christmas present.",
            "", "OYhFFQl4fLs");
    private static final Title QM = new Title("qm", "Quantumania", "Ant-Man and the Wasp: Quantumania", "Ant-Man and the Wasp: Quantumania", "Film",
            2023, "", "2026",
            "Scott Lang's family is dragged into the quantum realm and the man who rules it.",
            "https://www.hotstar.com/in/movies/ant-man-and-the-wasp-quantumania/1260140795", "ZlNFpri-Y40");
    private static final Title GOTG3 = new Title("gotg3", "Guardians Vol. 3", "Guardians of the Galaxy Vol. 3", "Guardians of the Galaxy Vol. 3", "Film",
            2023, "", "2026",
            "To save Rocket, the Guardians go after the man who made him.",
            "https://www.hotstar.com/in/movies/guardians-of-the-galaxy-vol-3/1260143699", "u3V5KDHRQvk");
    private static final Title SI = new Title("si", "Secret Invasion", "Secret Invasion", "Secret Invasion", "Series",
            2023, "", "2026",
            "Nick Fury comes home to stop shape-shifting aliens hiding among Earth's most powerful people.",
            "https://www.hotstar.com/in/1260111199", "Tp_YZNqNBhw");
    private static final Title TM = new Title("tm", "The Marvels", "The Marvels", "The Marvels", "Film",
            2023, "", "2026",
            "Three heroes find their powers tangled, swapping places every time they use them.",
            "https://www.hotstar.com/in/movies/the-marvels/1260167860", "wS_qbDztgVY");
    private static final Title ECHO = new Title("echo", "Echo", "Echo", "Echo", "Series",
            2024, "", "2025",
            "Maya Lopez goes home to Oklahoma with a crime boss on her trail and her family to face.",
            "https://www.hotstar.com/in/1260160875", "AFUKnherhuw");
    private static final Title DW = new Title("dw", "Deadpool & Wolverine", "Deadpool & Wolverine", "Deadpool & Wolverine", "Film",
            2024, "", "2024",
            "Deadpool drags a reluctant Wolverine into a job to save his own corner of the multiverse.",
            "https://www.hotstar.com/in/movies/deadpool-and-wolverine/1271305185", "73_1biulkYk");
    private static final Title AGATHA = new Title("agatha", "Agatha All Along", "Agatha All Along", "Agatha All Along", "Series",
            2024, "", "2026",
            "A disgraced witch gathers a coven for the Witches' Road to win back her powers.",
            "https://www.hotstar.com/in/1271321225", "R9pXbNz6Vbw");
    private static final Title BA = new Title("ba", "Born Again S1", "Daredevil: Born Again Season 1", "Daredevil: Born Again", "Series",
            2025, "", "2026–27",
            "Lawyer Matt Murdock and crime boss Wilson Fisk circle each other in a changed New York.",
            "https://www.hotstar.com/in/1271337450", "7xALolZzhSM");
    private static final Title BNW = new Title("bnw", "Brave New World", "Captain America: Brave New World", "Captain America: Brave New World", "Film",
            2025, "", "2027",
            "Sam Wilson, now Captain America, is caught in an international incident with the US president.",
            "https://www.hotstar.com/in/movies/captain-america-brave-new-world/1271337438", "1pHDWnXmK7Y");
    private static final Title TB = new Title("tb", "Thunderbolts*", "Thunderbolts*", "Thunderbolts*", "Film",
            2025, "", "2027",
            "A crew of expendable antiheroes walks into a trap and has to work out how to be a team.",
            "https://www.hotstar.com/in/movies/thunderbolts/1271337437", "-sAOWhvheK8");
    private static final Title IRON = new Title("iron", "Ironheart", "Ironheart", "Ironheart", "Series",
            2025, "", "2025",
            "Riri Williams, a genius who builds her own armour, takes shady money to fund it.",
            "https://www.hotstar.com/in/1271341040", "WpW36ldAqnM");
    private static final Title F4 = new Title("f4", "First Steps", "The Fantastic Four: First Steps", "The Fantastic 4: First Steps", "Film",
            2025, "", "1964",
            "On a retro-future Earth, Marvel's first family faces a planet-eating giant.",
            "https://www.hotstar.com/in/movies/the-fantastic-four-first-steps/1271406275", "pAsmrKyMqaA");
    private static final Title EOW = new Title("eow", "Eyes of Wakanda", "Eyes of Wakanda", "Eyes of Wakanda", "Series",
            2025, "", "1260 BC–1896",
            "Through the centuries, Wakanda's warriors go out into the world to recover stolen vibranium.",
            "https://www.hotstar.com/in/1271341047", "ODHh6oe4MlE");
    private static final Title MZ = new Title("mz", "Marvel Zombies", "Marvel Zombies", "Marvel Zombies", "Series",
            2025, "", "2023",
            "In a universe overrun by zombie heroes, a handful of survivors search for a cure.",
            "https://www.hotstar.com/in/1271435441", "twHYF506-9Y");
    private static final Title WM = new Title("wm", "Wonder Man", "Wonder Man", "Wonder Man", "Series",
            2026, "", "2025–27",
            "A struggling actor with hidden powers auditions for a superhero remake.",
            "https://www.hotstar.com/in/1271460126", "wHuWmjXsReU");
    private static final Title VQ = new Title("vq", "VisionQuest", "VisionQuest", "VisionQuest", "Series",
            2026, "", "2028",
            "Vision sets out to discover who he is and what he has become.",
            "https://www.hotstar.com/in/1271652473", "oSVTAUKyrL8");
    private static final Title AC1SHOT = new Title("ac1shot", "Agent Carter One-Shot", "Marvel One-Shot: Agent Carter", "Marvel One-Shot: Agent Carter", "One-Shot",
            2013, "", "1946",
            "A year after the war, Peggy Carter takes a mission her desk-bound bosses never meant for her.",
            "https://www.hotstar.com/in/movies/marvel-one-shot-agent-carter/1260091482", "");
    private static final Title AC = new Title("ac", "Agent Carter", "Agent Carter", "Marvel's Agent Carter", "Series",
            2015, "2 seasons", "1946–47",
            "Peggy Carter runs secret missions for Howard Stark while her own agency underestimates her.",
            "https://www.hotstar.com/in/1260027685", "");
    private static final Title GROOT = new Title("groot", "I Am Groot", "I Am Groot", "I Am Groot", "Series",
            2022, "2 seasons", "2014",
            "Baby Groot grows up among the stars, getting into trouble a few minutes at a time.",
            "", "D7eFpRf4tac");
    private static final Title DD1 = new Title("dd1", "Daredevil S1", "Daredevil Season 1", "Marvel's Daredevil", "Series",
            2015, "", "2015",
            "Blind lawyer Matt Murdock starts fighting Hell's Kitchen's crime at night, and meets Wilson Fisk.",
            "https://www.hotstar.com/in/1260098114", "ZdNK8L3ZdmE");
    private static final Title DD2 = new Title("dd2", "Daredevil S2", "Daredevil Season 2", "Marvel's Daredevil", "Series",
            2016, "", "2015",
            "The Punisher's war on the gangs and the return of Elektra pull Matt Murdock apart.",
            "https://www.hotstar.com/in/1260098115", "nLS3ejfv-tI");
    private static final Title DD3 = new Title("dd3", "Daredevil S3", "Daredevil Season 3", "Marvel's Daredevil", "Series",
            2018, "", "2017",
            "Presumed dead, Matt Murdock comes back as Wilson Fisk talks his way out of prison.",
            "https://www.hotstar.com/in/1260096445", "");
    private static final Title JJ1 = new Title("jj1", "Jessica Jones S1", "Jessica Jones Season 1", "Marvel's Jessica Jones", "Series",
            2015, "", "2015",
            "A private eye with super strength is hunted by the man who once controlled her mind.",
            "https://www.hotstar.com/in/1260089836", "g6c3oTYcuhA");
    private static final Title JJ2 = new Title("jj2", "Jessica Jones S2", "Jessica Jones Season 2", "Marvel's Jessica Jones", "Series",
            2018, "", "2017",
            "Jessica digs into the experiments that gave her powers, and finds family she thought was gone.",
            "https://www.hotstar.com/in/1260089857", "-u2AV8z_CX0");
    private static final Title JJ3 = new Title("jj3", "Jessica Jones S3", "Jessica Jones Season 3", "Marvel's Jessica Jones", "Series",
            2019, "", "2018",
            "A clever killer sets out to prove Jessica a fraud while Trish takes justice into her own hands.",
            "https://www.hotstar.com/in/1260089839", "");
    private static final Title LC1 = new Title("lc1", "Luke Cage S1", "Luke Cage Season 1", "Marvel's Luke Cage", "Series",
            2016, "", "2015",
            "A fugitive with unbreakable skin is pushed into defending Harlem from its crime bosses.",
            "https://www.hotstar.com/in/1260091291", "ORa4hPhGrpo");
    private static final Title LC2 = new Title("lc2", "Luke Cage S2", "Luke Cage Season 2", "Marvel's Luke Cage", "Series",
            2018, "", "2017",
            "Now Harlem's famous hero, Luke faces a rival every bit as strong as he is.",
            "https://www.hotstar.com/in/1260098098", "");
    private static final Title IF1 = new Title("if1", "Iron Fist S1", "Iron Fist Season 1", "Marvel's Iron Fist", "Series",
            2017, "", "2016",
            "Danny Rand returns to New York after fifteen years, with a mystical fist and a company to reclaim.",
            "https://www.hotstar.com/in/1260091304", "ll50ZpwmhNs");
    private static final Title IF2 = new Title("if2", "Iron Fist S2", "Iron Fist Season 2", "Marvel's Iron Fist", "Series",
            2018, "", "2017",
            "Danny guards the city in Daredevil's absence while an old friend comes for his power.",
            "https://www.hotstar.com/in/1260096401", "");
    private static final Title DEF = new Title("def", "The Defenders", "The Defenders", "Marvel's The Defenders", "Series",
            2017, "", "2016",
            "Daredevil, Jessica Jones, Luke Cage and Iron Fist reluctantly team up to save New York.",
            "https://www.hotstar.com/in/1260091251", "MAbjq7ydKDI");
    private static final Title PUN1 = new Title("pun1", "The Punisher S1", "The Punisher Season 1", "Marvel's The Punisher", "Series",
            2017, "", "2016",
            "Frank Castle finds the conspiracy behind his family's murder goes far beyond the gangs.",
            "https://www.hotstar.com/in/1260096415", "");
    private static final Title PUN2 = new Title("pun2", "The Punisher S2", "The Punisher Season 2", "Marvel's The Punisher", "Series",
            2019, "", "2018",
            "Frank protects a girl hunted by a ruthless killer while an old enemy rebuilds himself.",
            "https://www.hotstar.com/in/1260098164", "Ed2xg6zvxi4");
    private static final Title YFNSM = new Title("yfnsm", "Friendly Neighborhood Spider-Man", "Your Friendly Neighborhood Spider-Man", "Your Friendly Neighborhood Spider-Man", "Series",
            2025, "", "2016",
            "In another universe, Peter Parker's first year as Spider-Man has Norman Osborn for a mentor.",
            "https://www.hotstar.com/in/1271340193", "N3J2JRQg040");
    private static final Title LOKI2 = new Title("loki2", "Loki S2", "Loki Season 2", "Loki", "Series",
            2023, "", "Outside time",
            "Slipping through time, Loki races to save the bureau and every timeline with it.",
            "https://www.hotstar.com/in/1260148333", "dug56u8NN7g");
    private static final Title WHATIF2 = new Title("whatif2", "What If...? S2", "What If...? Season 2", "What If...?", "Series",
            2023, "", "Multiverse",
            "The Watcher shows more roads not taken, and Captain Carter crosses universes again.",
            "https://www.hotstar.com/in/1260122647", "TiEVqZ2Bc_c");
    private static final Title WHATIF3 = new Title("whatif3", "What If...? S3", "What If...? Season 3", "What If...?", "Series",
            2024, "", "Multiverse",
            "The last strange worlds of the multiverse, and what the Watcher risks by stepping in.",
            "https://www.hotstar.com/in/1271338345", "umiKiW4En9g");
    private static final Title BA2 = new Title("ba2", "Born Again S2", "Daredevil: Born Again Season 2", "Daredevil: Born Again", "Series",
            2026, "", "2027",
            "With Mayor Fisk ruling New York by force, Matt Murdock builds a resistance from the shadows.",
            "https://www.hotstar.com/in/1271524546", "U1MqJBVn8Rk");
    private static final Title PUNOLK = new Title("punolk", "Punisher: One Last Kill", "The Punisher: One Last Kill", "The Punisher: One Last Kill", "Special",
            2026, "", "2027",
            "Frank Castle looks for a life beyond revenge until something drags him back into the fight.",
            "https://www.hotstar.com/in/movies/the-punisher-one-last-kill/1271614656", "Svgro7SYy-A");
    private static final Title BND = new Title("bnd", "Brand New Day", "Spider-Man: Brand New Day", "Spider-Man: Brand New Day", "Film",
            2026, "Not streaming yet", "2028",
            "In a world that has forgotten Peter Parker, Spider-Man fights crime full-time and alone.",
            "", "tLeBDumanoc");
    private static final Title DOOM = new Title("doom", "Doomsday", "Avengers: Doomsday", "Avengers: Doomsday", "Film",
            2026, "", "2028",
            "Doctor Doom rewrites reality and every universe's heroes face the end of the multiverse.",
            "", "");
    private static final Title XM1 = new Title("xm1", "X-Men", "X-Men", "X-Men", "Film",
            2000, "", "2003",
            "Mutants fight for acceptance in a world that fears them, led by two old friends turned enemies.",
            "", "");
    private static final Title X2 = new Title("x2", "X-Men United", "X2: X-Men United", "X2: X-Men United", "Film",
            2003, "", "2003",
            "A military scientist attacks the school while Magneto makes his own ruthless move.",
            "", "");
    private static final Title XLS = new Title("xls", "The Last Stand", "X-Men: The Last Stand", "X-Men: The Last Stand", "Film",
            2006, "", "2006",
            "A cure for mutation splits the mutants, and a dead friend returns with terrible power.",
            "", "");
    private static final Title XOW = new Title("xow", "Origins: Wolverine", "X-Men Origins: Wolverine", "X-Men Origins: Wolverine", "Film",
            2009, "", "1845–1979",
            "Wolverine's long life: his brother, the experiment that lined his bones, and the betrayal after.",
            "", "");
    private static final Title XFC = new Title("xfc", "First Class", "X-Men: First Class", "X-Men: First Class", "Film",
            2011, "", "1962",
            "In the Cuban Missile Crisis, Charles Xavier and Erik Lehnsherr start as friends and end as enemies.",
            "", "");
    private static final Title WOL = new Title("wol", "The Wolverine", "The Wolverine", "The Wolverine", "Film",
            2013, "", "2013",
            "In Japan, a mortal Logan is drawn into a power struggle and faces his own wish to die.",
            "", "");
    private static final Title XDP = new Title("xdp", "Days of Future Past", "X-Men: Days of Future Past", "X-Men: Days of Future Past", "Film",
            2014, "", "1973 / 2023",
            "Wolverine's mind is sent back to 1973 to stop the event that dooms mutants and humans alike.",
            "", "");
    private static final Title DP1 = new Title("dp1", "Deadpool", "Deadpool", "Deadpool", "Film",
            2016, "", "2016",
            "A mercenary with accelerated healing hunts the man who left him disfigured, breaking every rule.",
            "", "");
    private static final Title XAP = new Title("xap", "Apocalypse", "X-Men: Apocalypse", "X-Men: Apocalypse", "Film",
            2016, "", "1983",
            "The first and most powerful mutant wakes after millennia and recruits four horsemen to cleanse Earth.",
            "", "");
    private static final Title LOG = new Title("log", "Logan", "Logan", "Logan", "Film",
            2017, "", "2029",
            "An ageing Wolverine cares for a sick Charles Xavier and a girl who is very much like him.",
            "", "");
    private static final Title DP2 = new Title("dp2", "Deadpool 2", "Deadpool 2", "Deadpool 2", "Film",
            2018, "", "2018",
            "Deadpool assembles a team to protect a young mutant from a soldier sent from the future.",
            "", "");
    private static final Title XDPX = new Title("xdpx", "Dark Phoenix", "Dark Phoenix", "Dark Phoenix", "Film",
            2019, "", "1992",
            "Jean Grey absorbs a cosmic force that corrupts her and turns the X-Men against each other.",
            "", "");
    private static final Title XNM = new Title("xnm", "The New Mutants", "The New Mutants", "The New Mutants", "Film",
            2020, "", "2019",
            "Five young mutants held in a secret facility discover it is not trying to help them.",
            "", "");
    private static final Title SM1 = new Title("sm1", "Spider-Man", "Spider-Man", "Spider-Man", "Film",
            2002, "", "2002",
            "A high-school student bitten by a spider learns that power comes with responsibility.",
            "", "");
    private static final Title SM2 = new Title("sm2", "Spider-Man 2", "Spider-Man 2", "Spider-Man 2", "Film",
            2004, "", "2004",
            "Peter gives up being Spider-Man, but a scientist fused to mechanical arms needs stopping.",
            "", "");
    private static final Title SM3 = new Title("sm3", "Spider-Man 3", "Spider-Man 3", "Spider-Man 3", "Film",
            2007, "", "2007",
            "An alien suit makes Spider-Man stronger and meaner while two villains close in.",
            "", "");
    private static final Title ASM1 = new Title("asm1", "The Amazing Spider-Man", "The Amazing Spider-Man", "The Amazing Spider-Man", "Film",
            2012, "", "2012",
            "Another Peter Parker, another spider bite, a lizard in the sewers and a girl named Gwen.",
            "", "");
    private static final Title ASM2 = new Title("asm2", "The Amazing Spider-Man 2", "The Amazing Spider-Man 2", "The Amazing Spider-Man 2", "Film",
            2014, "", "2014",
            "Peter faces Electro, reconnects with Harry Osborn, and pays a terrible price.",
            "", "");
    private static final Title VEN1 = new Title("ven1", "Venom", "Venom", "Venom", "Film",
            2018, "", "2018",
            "A journalist becomes the host of an alien symbiote with a taste for eating people.",
            "", "");
    private static final Title VEN2 = new Title("ven2", "Let There Be Carnage", "Venom: Let There Be Carnage", "Venom: Let There Be Carnage", "Film",
            2021, "", "2021",
            "Venom faces a serial killer who bonds with his own, more vicious symbiote.",
            "", "");
    private static final Title MORB = new Title("morb", "Morbius", "Morbius", "Morbius", "Film",
            2022, "", "2022",
            "A doctor cures his rare blood disease but turns himself into a living vampire.",
            "", "");
    private static final Title MWEB = new Title("mweb", "Madame Web", "Madame Web", "Madame Web", "Film",
            2024, "", "2003",
            "A paramedic develops visions of the future and must protect three young women.",
            "", "");
    private static final Title VEN3 = new Title("ven3", "The Last Dance", "Venom: The Last Dance", "Venom: The Last Dance", "Film",
            2024, "", "2024",
            "Venom and Eddie flee a government agent while alien creatures hunt the symbiote.",
            "", "");
    private static final Title KRAV = new Title("krav", "Kraven the Hunter", "Kraven the Hunter", "Kraven the Hunter", "Film",
            2024, "", "2024",
            "A man given animal powers by his father becomes the world's greatest and most brutal hunter.",
            "", "");
    private static final Title BLD1 = new Title("bld1", "Blade", "Blade", "Blade", "Film",
            1998, "", "1998",
            "A half-vampire hunts the undead in a world where vampires run the night.",
            "", "");
    private static final Title BLD2 = new Title("bld2", "Blade II", "Blade II", "Blade II", "Film",
            2002, "", "2002",
            "Blade teams up with his enemies to fight a new breed of vampire that feeds on both.",
            "", "");
    private static final Title DD03 = new Title("dd03", "Daredevil", "Daredevil", "Daredevil", "Film",
            2003, "", "2003",
            "A blind lawyer fights crime at night in Hell's Kitchen, long before the MCU found him.",
            "", "");
    private static final Title HLK03 = new Title("hlk03", "Hulk", "Hulk", "Hulk", "Film",
            2003, "", "2003",
            "A scientist's buried past explodes when an experiment turns him into a giant made of rage.",
            "", "");
    private static final Title PUN04 = new Title("pun04", "The Punisher", "The Punisher", "The Punisher", "Film",
            2004, "", "2004",
            "An FBI agent whose family is murdered wages a one-man war on the man responsible.",
            "", "");
    private static final Title BLD3 = new Title("bld3", "Blade: Trinity", "Blade: Trinity", "Blade: Trinity", "Film",
            2004, "", "2004",
            "Blade is framed by vampires and joins two hunters to fight the oldest vampire of all.",
            "", "");
    private static final Title ELK = new Title("elk", "Elektra", "Elektra", "Elektra", "Film",
            2005, "", "2005",
            "Brought back from death, an assassin is hired to kill a man and his daughter.",
            "", "");
    private static final Title FF05 = new Title("ff05", "Fantastic Four", "Fantastic Four", "Fantastic Four", "Film",
            2005, "", "2005",
            "Four astronauts gain powers from a cosmic storm and face their fifth, who came out worse.",
            "", "");
    private static final Title GR1 = new Title("gr1", "Ghost Rider", "Ghost Rider", "Ghost Rider", "Film",
            2007, "", "2007",
            "A stunt motorcyclist who sold his soul becomes a flaming spirit of vengeance at night.",
            "", "");
    private static final Title FF07 = new Title("ff07", "Rise of the Silver Surfer", "Fantastic Four: Rise of the Silver Surfer", "Fantastic Four: Rise of the Silver Surfer", "Film",
            2007, "", "2007",
            "The Four face a herald who surfs the cosmos ahead of a planet-eating giant.",
            "", "");
    private static final Title PUN08 = new Title("pun08", "Punisher: War Zone", "Punisher: War Zone", "Punisher: War Zone", "Film",
            2008, "", "2008",
            "Frank Castle takes on a crime boss he disfigured, now calling himself Jigsaw.",
            "", "");
    private static final Title GR2 = new Title("gr2", "Spirit of Vengeance", "Ghost Rider: Spirit of Vengeance", "Ghost Rider: Spirit of Vengeance", "Film",
            2012, "", "2012",
            "In Eastern Europe, the Rider protects a boy from the devil who wants his soul.",
            "", "");
    private static final Title FF15 = new Title("ff15", "Fant4stic", "Fantastic Four", "Fantastic Four (2015)", "Film",
            2015, "", "2015",
            "Young scientists discover another dimension and are transformed, but one of them turns hostile.",
            "", "");

    static Order chronological() {
        return new Order(CHRONOLOGICAL, new Chapter[]{
                new Chapter("Long ago", 0xFFB98A3E, EOW),
                new Chapter("1940s", 0xFF8FA651, CAP1, AC1SHOT, AC),
                new Chapter("1960s", 0xFF4FB3E8, F4),
                new Chapter("1990s", 0xFF2BB3A3, CAPM),
                new Chapter("2008–2012", 0xFFE23B3B, IM1, IM2, HULK, THOR, AVG),
                new Chapter("2013–2015", 0xFFE58A2B, TDW, IM3, TWS, GOTG, GROOT, GOTG2, DD1, JJ1, AOU, ANT, DD2, LC1),
                new Chapter("2016–2017", 0xFF7A5CF5, IF1, DEF, CW, YFNSM, BW, BP, SMH, PUN1, DRS, JJ2, LC2, IF2, DD3, RAG),
                new Chapter("2018–2023", 0xFFC850C0, PUN2, JJ3, ANTW, IW, END, MZ, WV),
                new Chapter("2024", 0xFF35B36B, LOKI, DW, SC, FWS, FFH, ET, NWH, MOM, HAWK, WHATIF),
                new Chapter("2025", 0xFF3D8BFF, MK, WF, ECHO, SHE, MSM, LT, WBN, IRON, HOL, WM),
                new Chapter("2026", 0xFFD9468F, QM, GOTG3, SI, TM, LOKI2, WHATIF2, AGATHA, WHATIF3, BA),
                new Chapter("2027–2028", 0xFFE6C229, BNW, TB, BA2, PUNOLK, VQ, BND),
                new Chapter("Doomsday", 0xFF5FD35F, DOOM),
        });
    }

    static Order phases() {
        return new Order(PHASES, new Chapter[]{
                new Chapter("Phase One", 0xFFE23B3B, IM1, HULK, IM2, THOR, CAP1, AVG),
                new Chapter("Phase Two", 0xFFE58A2B, IM3, AC1SHOT, TDW, TWS, GOTG, AC, DD1, AOU, ANT, JJ1, DD2),
                new Chapter("Phase Three", 0xFF7A5CF5, CW, LC1, DRS, IF1, GOTG2, SMH, DEF, RAG, PUN1, BP, JJ2, IW, LC2, ANTW, IF2, DD3, PUN2, CAPM, END, JJ3, FFH),
                new Chapter("Phase Four", 0xFF2BB3A3, WV, FWS, LOKI, BW, WHATIF, SC, ET, HAWK, NWH, MK, MOM, MSM, LT, GROOT, SHE, WBN, WF, HOL),
                new Chapter("Phase Five", 0xFFD9468F, QM, GOTG3, SI, LOKI2, TM, WHATIF2, ECHO, DW, AGATHA, WHATIF3, YFNSM, BNW, BA, TB, IRON),
                new Chapter("Phase Six", 0xFF3D8BFF, F4, EOW, MZ, WM, BA2, DOOM, PUNOLK, BND, VQ),
        });
    }

    static Order xmen() {
        return new Order(XMEN, new Chapter[]{
                new Chapter("First Class", 0xFFE58A2B, XFC, XDP, XOW),
                new Chapter("1980s–1990s", 0xFFD97A1E, XAP, XDPX),
                new Chapter("The X-Men", 0xFFC06A15, XM1, X2, XLS, WOL),
                new Chapter("Deadpool & Logan", 0xFFE58A2B, DP1, DP2, XNM, DW, LOG, DOOM),
        });
    }

    static Order spider() {
        return new Order(SPIDER, new Chapter[]{
                new Chapter("Raimi Trilogy", 0xFFD9382E, SM1, SM2, SM3),
                new Chapter("Amazing", 0xFF3D8BFF, ASM1, ASM2),
                new Chapter("Venom & SSU", 0xFF999999, VEN1, VEN2, MORB, MWEB, VEN3, KRAV),
                new Chapter("Multiverse", 0xFFCCCCCC, NWH, DOOM),
        });
    }

    static Order legacy() {
        return new Order(LEGACY, new Chapter[]{
                new Chapter("Blade", 0xFF9B59B6, BLD1, BLD2, BLD3),
                new Chapter("Early 2000s", 0xFF8E44AD, DD03, HLK03, PUN04, ELK),
                new Chapter("Fantastic Four & Ghost Rider", 0xFF7D3C98, FF05, GR1, FF07, PUN08),
                new Chapter("Late Era", 0xFF6C3483, GR2, FF15),
                new Chapter("Convergence", 0xFF9B59B6, DOOM),
        });
    }
}
