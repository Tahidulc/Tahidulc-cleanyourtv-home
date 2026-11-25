package com.gathin.homefieldtv;

/**
 * When the scoreboard over a match (BoardLayer: the board, the alerts beside it, the cover over the channel's logo, its
 * pop-ups) may show: only while a channel plays full screen with nothing over it. The live screen on top and in front
 * (TvActivity.showing), the picture on the whole screen, and none of the live screen's own panels up (the info card, the
 * OK overlay, the bar, the channel list, the description, the rows of channels, a number being typed, a question).
 * Anything else that covers or shrinks the picture (the guide's preview, Home's mini TV, the channel list in its
 * window, Settings, History, any other screen) takes the live screen off the top, which stops the board (Screen.hidden)
 * until it is back (Screen.shown).
 *
 * <p>Covered, everything of the board goes at once (its moment's pop-up let go, its menu closed); it comes back when
 * plain playback does. Its pop-ups due meanwhile wait for then, and are skipped when stale: a kick-off, second-half or
 * full-time pop-up held more than MOMENT_MS, a goal's (restart) more than RESTART_MS; alerts beside it queued more than
 * DROP_MS before it came back are let go.
 */
final class BoardGate {
    private BoardGate() {}

    /** How long a moment's pop-up (kick-off, second half, full time), a goal's, and an alert may wait for a clear picture. */
    static final long MOMENT_MS = 2 * 60_000L, RESTART_MS = 90_000L, DROP_MS = 2 * 60_000L;

    /** Pure: plain full-screen playback: shown and in front, the whole screen, no panel of the live screen's, nothing typed or asked. */
    static boolean plain(boolean showing, boolean fullScreen, boolean noPanel, boolean typing, boolean asking) {
        return showing && fullScreen && noPanel && !typing && !asking;
    }

    /** Pure: whether a pop-up held back since heldAt is still worth showing at now ("restart": a goal's). */
    static boolean fresh(String kind, long heldAt, long now) {
        long age = now - heldAt;
        if (age < 0) return true;
        return age <= ("restart".equals(kind) ? RESTART_MS : MOMENT_MS);
    }

    /** Pure: whether an alert queued at queuedAt (null: not known) is too old to slide out at now. */
    static boolean stale(Long queuedAt, long now) {
        return queuedAt != null && now - queuedAt > DROP_MS;
    }

    /** A moment's pop-up held back while the picture was covered: what it was for, and since when. */
    static final class Held {
        final String kind, scorer, minute;
        final int[] score;
        final long at;

        Held(String kind, int[] score, String scorer, String minute, long at) {
            this.kind = kind;
            this.score = score;
            this.scorer = scorer;
            this.minute = minute;
            this.at = at;
        }
    }
}
