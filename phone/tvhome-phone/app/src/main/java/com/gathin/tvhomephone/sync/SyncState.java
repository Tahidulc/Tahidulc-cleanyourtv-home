package com.gathin.tvhomephone.sync;

/**
 * One TV's sync bookkeeping (pure, for the tests): what to send it (this phone's changes after the last push that
 * went through) and what to ask it for (its changes after the "now" it gave last time). A failed sync changes neither,
 * so nothing is lost; a change made while a sync is on the way goes at the next one.
 */
final class SyncState {
    /** The TV's clock: its changes after this are asked for. */
    long since;
    /** This phone's clock: its changes after this are sent. */
    long pushed;

    SyncState(long since, long pushed) {
        this.since = since;
        this.pushed = pushed;
    }

    /** A sync starting now (this phone's clock): the phone's changes after pushed go; returns when it started. */
    long begin(long phoneNow) {
        return phoneNow;
    }

    /** It went through: the TV's "now" becomes since, and what was sent counts as pushed up to when it started. */
    void done(long started, long tvNow) {
        if (tvNow > since) since = tvNow;
        // A little before the start: an item written in the same millisecond as the read still goes next time.
        if (started - 1 > pushed) pushed = started - 1;
    }

    /** The TV forgot this phone (paired again, or reset): everything again, both ways. */
    void reset() {
        since = 0;
        pushed = 0;
    }
}
