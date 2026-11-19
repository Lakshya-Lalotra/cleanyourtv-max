package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DialInviteTest {
    @Test public void oneOrTwoMovesAreNotEnough() {
        DialInvite invite = new DialInvite();
        assertFalse(invite.onMove(1000));
        assertFalse(invite.onMove(2000));
        assertTrue(invite.onMove(3000));
    }

    @Test public void itLeavesALongGapAfterPopping() {
        DialInvite invite = new DialInvite();
        invite.onMove(0);
        invite.onMove(0);
        assertTrue(invite.onMove(0));
        for (int i = 0; i < 20; i++) assertFalse(invite.onMove(DialInvite.MIN_GAP_MS - 1));
        assertTrue(invite.onMove(DialInvite.MIN_GAP_MS));
    }

    @Test public void waitingLongEnoughStillNeedsAFewMoves() {
        DialInvite invite = new DialInvite();
        invite.onMove(0);
        invite.onMove(0);
        invite.onMove(0);
        assertFalse(invite.onMove(DialInvite.MIN_GAP_MS * 10));
        assertFalse(invite.onMove(DialInvite.MIN_GAP_MS * 10));
        assertTrue(invite.onMove(DialInvite.MIN_GAP_MS * 10));
    }

    @Test public void usingTheDialCountsAsAccepting() {
        DialInvite invite = new DialInvite();
        invite.onAccepted(1000);
        invite.onMove(2000);
        invite.onMove(3000);
        assertFalse(invite.onMove(4000));
    }
}
