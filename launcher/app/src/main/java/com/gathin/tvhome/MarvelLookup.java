package com.gathin.tvhome;

import java.util.List;
import java.util.Locale;

/** Which app and link a title's Play button uses: JioHotstar's own unless JustWatch now lists the title only elsewhere. */
final class MarvelLookup {
    private MarvelLookup() {
    }

    static final class Link {
        final String platform, url, pkg;

        Link(String platform, String url, String pkg) {
            this.platform = platform;
            this.url = url;
            this.pkg = pkg;
        }

        boolean onJioHotstar() {
            return MarvelData.JIOHOTSTAR.equals(pkg);
        }
    }

    static Link standard(MarvelData.Title title) {
        return new Link("JioHotstar", MarvelData.playUrl(title), MarvelData.JIOHOTSTAR);
    }

    /**
     * The link JustWatch's answer calls for, or null to keep the standard one: when the answer is about another title of
     * the same name, or lists nothing to stream. Rent and buy never replace JioHotstar.
     */
    static Link choose(MarvelData.Title title, List<StreamSearch.Title> found) {
        boolean series = title.isSeries();
        for (StreamSearch.Title candidate : found) {
            if (!same(title.lookupName, candidate.name)) continue;
            if (series != candidate.meta.contains("Series")) continue;
            Link elsewhere = null;
            for (StreamSearch.Offer offer : candidate.offers) {
                if (offer.pkg.isEmpty() || !streams(offer)) continue;
                if (MarvelData.JIOHOTSTAR.equals(offer.pkg)) {
                    // Still there. A title with no link of its own gets the one JustWatch has now.
                    if (!title.link.isEmpty()) return null;
                    String url = offer.url.contains("/movies/") ? offer.url + "/watch" : offer.url;
                    return new Link("JioHotstar", url, offer.pkg);
                }
                if (elsewhere == null) elsewhere = new Link(offer.platform, offer.url, offer.pkg);
            }
            return elsewhere;
        }
        return null;
    }

    private static boolean streams(StreamSearch.Offer offer) {
        return offer.kinds.contains("Stream") || offer.kinds.contains("Free");
    }

    private static boolean same(String a, String b) {
        return letters(a).equals(letters(b));
    }

    private static String letters(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
