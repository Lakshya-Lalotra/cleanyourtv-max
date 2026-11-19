package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The side wheel's sports list: India's matches first, then what is live, then what starts soonest, then the latest
 * results. Matches more than a day from now in either direction are left out.
 */
final class SportsOrder {
    static final long DAY_MS = 24 * 60 * 60 * 1000L;

    private SportsOrder() {
    }

    static List<WheelFeeds.Match> pick(List<WheelFeeds.Match> matches, long now, int limit) {
        List<WheelFeeds.Match> near = new ArrayList<>();
        for (WheelFeeds.Match m : matches) {
            boolean live = "in".equals(m.state);
            if (m.name == null || m.name.isEmpty()) continue;
            if (!live && (m.start == 0 || Math.abs(m.start - now) > DAY_MS)) continue;
            near.add(m);
        }
        Collections.sort(near, (a, b) -> {
            if (a.india != b.india) return a.india ? -1 : 1;
            int ra = rank(a.state), rb = rank(b.state);
            if (ra != rb) return Integer.compare(ra, rb);
            // Upcoming: soonest first. Results: latest first. Live: the one that started last first.
            return "pre".equals(a.state) ? Long.compare(a.start, b.start) : Long.compare(b.start, a.start);
        });
        return near.size() > limit ? new ArrayList<>(near.subList(0, limit)) : near;
    }

    private static int rank(String state) {
        if ("in".equals(state)) return 0;
        if ("pre".equals(state)) return 1;
        return 2;
    }
}
