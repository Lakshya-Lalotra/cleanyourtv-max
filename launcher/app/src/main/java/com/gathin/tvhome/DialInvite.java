package com.gathin.tvhome;

/**
 * When the closed dial springs out as you move along the dock: only sometimes. It waits for a few moves, so it answers
 * someone browsing rather than a single press, and then leaves a long gap, because an invitation that keeps repeating
 * turns into something to ignore. Using the dial counts as having accepted it.
 */
final class DialInvite {
    static final int MOVES = 3;
    static final long MIN_GAP_MS = 45_000;

    private int moves;
    private long lastPop = Long.MIN_VALUE / 2;

    /** The focus moved to another dock app at this time: true when the dial should spring out now. */
    boolean onMove(long now) {
        moves++;
        if (moves < MOVES || now - lastPop < MIN_GAP_MS) return false;
        moves = 0;
        lastPop = now;
        return true;
    }

    /** The dial was just opened or is already out: no need to invite for a while. */
    void onAccepted(long now) {
        moves = 0;
        lastPop = now;
    }
}
