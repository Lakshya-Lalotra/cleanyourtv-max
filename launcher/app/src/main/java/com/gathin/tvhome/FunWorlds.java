package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * fun_wheel's contents: the worlds on the dial, top to bottom, and the two big lines each shows on the left. A logo stands
 * in front of the first line. One with its own screen opens it; the others say Coming soon.
 */
final class FunWorlds {
    private FunWorlds() {
    }

    static final class World {
        final String name, firstLine, secondLine, hint;
        final int color, logo;
        final boolean opens;
        /** The screen it opens, or null for one still to come. */
        final Class<?> screen;

        World(String name, int logo, String firstLine, String secondLine, String hint, int color, Class<?> screen) {
            this.name = name;
            this.logo = logo;
            this.firstLine = firstLine;
            this.secondLine = secondLine;
            this.hint = hint;
            this.color = color;
            this.screen = screen;
            this.opens = screen != null;
        }
    }

    static List<World> all() {
        List<World> worlds = new ArrayList<>();
        worlds.add(new World("Marvel", R.drawable.logo_marvel, "Cinematic", "Universe",
                "Press OK to enter immersive space", MarvelLook.RED, MarvelActivity.class));
        worlds.add(new World("Harry Potter", 0, "Wizarding", "World", "Press OK to enter immersive space", WizardPageView.GOLD,
                WizardActivity.class));
        worlds.add(new World("Lord of the Rings", 0, "Lord of", "the Rings", "Press OK to enter immersive space", 0xFF8FA651,
                MiddleEarthActivity.class));
        worlds.add(new World("One Piece", 0, "One Piece", "Grand Line", "Coming soon", 0xFF2E9BE6, null));
        worlds.add(new World("Narnia", 0, "The Chronicles", "of Narnia", "Coming soon", 0xFF7FC4E8, null));
        return worlds;
    }

    static List<String> names(List<World> worlds) {
        List<String> names = new ArrayList<>();
        for (World w : worlds) names.add(w.name);
        return names;
    }

    static int[] colors(List<World> worlds) {
        int[] colors = new int[worlds.size()];
        for (int i = 0; i < colors.length; i++) colors[i] = worlds.get(i).color;
        return colors;
    }
}
