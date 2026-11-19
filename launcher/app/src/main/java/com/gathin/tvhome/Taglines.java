package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * app_taglines (config.xml): the line shown above the dock for a focused app instead of its name. Several lines for
 * one package take turns, one step on each return to Home.
 */
final class Taglines {
    private final Map<String, List<String>> lines;

    private Taglines(Map<String, List<String>> lines) {
        this.lines = lines;
    }

    /** "package=text" entries, in order; an entry without both halves is skipped. */
    static Taglines parse(List<String> entries) {
        Map<String, List<String>> out = new HashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String pkg = entry.substring(0, eq).trim(), text = entry.substring(eq + 1).trim();
            if (pkg.isEmpty() || text.isEmpty()) continue;
            List<String> list = out.get(pkg);
            if (list == null) out.put(pkg, list = new ArrayList<>());
            list.add(text);
        }
        for (Map.Entry<String, List<String>> e : out.entrySet()) e.setValue(Collections.unmodifiableList(e.getValue()));
        return new Taglines(out);
    }

    /** This turn's line for the package, or null when it has none. */
    String pick(String pkg, int turn) {
        List<String> list = pkg == null ? null : lines.get(pkg);
        if (list == null) return null;
        return list.get(Math.floorMod(turn, list.size()));
    }
}
