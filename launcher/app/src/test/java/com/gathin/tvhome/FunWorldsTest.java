package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FunWorldsTest {
    @Test public void theWorldsInOrder() {
        assertEquals(Arrays.asList("Marvel", "Harry Potter", "Lord of the Rings", "One Piece", "Narnia"),
                FunWorlds.names(FunWorlds.all()));
    }

    @Test public void theFirstThreeWorldsOpenAndTheRestAreToCome() {
        List<FunWorlds.World> worlds = FunWorlds.all();
        assertEquals(MarvelActivity.class, worlds.get(0).screen);
        assertEquals(WizardActivity.class, worlds.get(1).screen);
        assertEquals(MiddleEarthActivity.class, worlds.get(2).screen);
        assertTrue(worlds.get(0).opens && worlds.get(1).opens && worlds.get(2).opens);
        for (FunWorlds.World w : worlds.subList(3, worlds.size())) {
            assertFalse(w.name, w.opens);
            assertEquals("Coming soon", w.hint);
        }
    }

    @Test public void marvelReadsAsItsLogoThenCinematicUniverse() {
        FunWorlds.World marvel = FunWorlds.all().get(0);
        assertEquals(R.drawable.logo_marvel, marvel.logo);
        assertEquals("Cinematic Universe", marvel.firstLine + " " + marvel.secondLine);
        assertEquals("Press OK to enter immersive space", marvel.hint);
    }

    @Test public void everyWorldHasItsOwnColour() {
        int[] colors = FunWorlds.colors(FunWorlds.all());
        assertEquals(5, Arrays.stream(colors).distinct().count());
    }
}
