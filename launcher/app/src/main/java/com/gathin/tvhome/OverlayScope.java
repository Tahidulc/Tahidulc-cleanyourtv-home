package com.gathin.tvhome;

/**
 * Where the overlays that belong to a channel may show: the scoreboard and the cover over the channel's logo
 * (BoardCover, with all that comes out of them), the kick-off and goal pop-ups, the line-ups and the intros
 * (ClassicIntro), the ad-break screen (AdBreak), the tennis, boxing and UFC boards (SportCover), the overlays button
 * (OverlayPrompt). As the family asked: only in the channel, that is a channel playing full screen in one of the apps
 * they are made for (TiviMate, Kleva TV), never over that app's guide, channel list or menus, and never over Homefield
 * TV, which has its own scoreboard, logo cover and pop-ups. An app that cannot be told is not taken for a player.
 * Plain Java, for its test.
 */
final class OverlayScope {
    private OverlayScope() { }

    static final String TIVIMATE = "ar.tvplayer.tv", KLEVA = "com.plaintextdev.klevaTv";
    /** Homefield TV: never under the launcher's overlays (it draws its own). */
    static final String HOMEFIELD = "com.gathin.homefieldtv";
    /** The apps the overlays are made for. */
    static final String[] PLAYERS = {TIVIMATE, KLEVA};

    static boolean isPlayer(String pkg) {
        if (pkg == null) return false;
        for (String p : PLAYERS) if (p.equals(pkg)) return true;
        return false;
    }

    /**
     * Whether the overlays may show over front (the app in front; null when it cannot be told) now: one they are made
     * for, playing its channel full screen (menuUp: its guide, a list or a menu is on its screen).
     */
    static boolean inChannel(String front, boolean menuUp) {
        return isPlayer(front) && !menuUp;
    }

    /**
     * Pure: whether the player's screen shows its guide, a list or a menu, from when its screen was last read as one
     * (listAt) and as its picture or banner alone (plainAt): the newer reading counts; never read, not.
     */
    static boolean menuUp(long listAt, long plainAt) {
        return listAt > 0 && listAt > plainAt;
    }
}
