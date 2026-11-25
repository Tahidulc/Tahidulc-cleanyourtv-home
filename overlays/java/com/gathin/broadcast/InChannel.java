package com.gathin.broadcast;

/**
 * Where the family's overlays over a channel may show (the scoreboard and what comes out of it, the cover over the
 * channel's logo, the kick-off and goal pop-ups, the line-ups), the one rule the TV launcher keeps over TiviMate
 * (OverlayScope) and Homefield TV over its own player (BoardGate): only in the channel, that is the live picture of a
 * channel full screen in the app it is made for, with nothing of that app's over it. Never over its guide or the guide's
 * preview window, its channel list, the OK overlay, its settings or menus, a question or a number being typed, a
 * programme from the archive (catch-up) or a picture moved back behind live, a film or an episode, or a channel in a
 * small window. While any of those is up everything goes at once (the game is still followed underneath), and it comes
 * back with the plain picture. Pure, for both apps' tests.
 */
public final class InChannel {
    private InChannel() {}

    /**
     * Pure: whether the overlays show now: the app they are made for is in front playing (front), its picture fills the
     * screen (fullScreen), the picture is live (live: not from the archive, not moved back behind live), and nothing of
     * the app's is over it (covered: its guide, a list, a panel, a menu, a question, a number being typed).
     */
    public static boolean shows(boolean front, boolean fullScreen, boolean live, boolean covered) {
        return front && fullScreen && live && !covered;
    }
}
