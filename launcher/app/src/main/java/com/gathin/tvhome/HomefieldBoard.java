package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;

/**
 * The football alerts while Homefield TV is in front: offered to its own scoreboard first, to slide out of it as they
 * slide out of the launcher's board over TiviMate (BoardCover.take), as the family asked: "the other games goals
 * notifications coming from the scoreboard ... the same as tivimate ... only when I'm watching it on the full screen in
 * homefield tv app". The launcher stays the one source of alerts (its scores' service, the games followed, each goal held
 * its minute, Settings › Notifications passed first); Homefield TV is where they show while its board is up over a
 * channel playing full screen. Taken (its answer, at once, from its ChannelCardsProvider behind the two apps' signature
 * permission), the launcher shows no card of its own, so a goal never comes twice; not taken (Homefield TV's guide, a
 * list or a menu up, its board resting or switched off, or a Homefield TV too old to answer), the launcher's card shows
 * over it as it shows over TiviMate's guide. Its own game's goals and cards Homefield TV's board says itself: the launcher's
 * are taken and let go there (BoardAlert.decide). Main thread; asks only while Homefield TV is in front, so its process is
 * never started for it.
 */
final class HomefieldBoard {
    private HomefieldBoard() { }

    /** Who answers the offer: Homefield TV's provider (a fake in the tests); null when no Homefield TV answered. */
    interface Asker {
        Bundle offer(Context c, Bundle alert);
    }

    static volatile Asker asker = HomefieldBoard::ask;
    /** Whether Homefield TV is in front now (a fake in the tests). */
    static volatile java.util.function.Predicate<Context> front = HomefieldBoard::inFront;

    private static Bundle ask(Context c, Bundle alert) {
        try {
            return c.getApplicationContext().getContentResolver().call(Uri.parse(com.gathin.broadcast.BoardAlert.HOMEFIELD_CARDS),
                    com.gathin.broadcast.BoardAlert.BOARD_ALERT, null, alert);
        } catch (RuntimeException e) {
            // no Homefield TV here, one from before it took alerts, or not the family's own: the launcher's card shows
            return null;
        }
    }

    private static boolean inFront(Context c) {
        String f = AppSearchService.front();
        if (f == null || f.equals(c.getPackageName())) f = AlertLog.front(c);
        return OverlayScope.HOMEFIELD.equals(f);
    }

    /** Pure: whether Homefield TV took the alert, from its answer (null: none came). */
    static boolean taken(Bundle answer) {
        return answer != null && answer.getBoolean(com.gathin.broadcast.BoardAlert.TAKEN, false);
    }

    /** Pure: a board's alert as handed over, its kind (the switch it passed: NotifyKinds' id) and whether it is the line-ups. */
    static com.gathin.broadcast.BoardAlert of(BoardDrop.Drop d, String kind) {
        com.gathin.broadcast.BoardAlert a = new com.gathin.broadcast.BoardAlert();
        a.gameId = d.gameId;
        a.kind = d.lineups ? "lineups" : kind;
        a.chip = d.chip;
        a.title = d.title;
        a.line = d.line;
        a.line2 = d.line2;
        a.key = d.key;
        a.what = d.what;
        a.tag = d.tag;
        a.colour = d.colour;
        a.homeColour = d.homeColour;
        a.awayColour = d.awayColour;
        a.side = d.side;
        a.tagColour = d.tagColour;
        a.tagMark = d.tagMark;
        a.lineups = d.lineups;
        a.photo = d.photo;
        a.homeCrest = d.homeCrest;
        a.awayCrest = d.awayCrest;
        a.pages = d.pages;
        a.photos = d.photos;
        return a;
    }

    /** Offers d to Homefield TV's board when it is in front; true when taken (no card from the launcher then). Main thread. */
    static boolean take(Context c, BoardDrop.Drop d, String kind) {
        if (d == null || c == null || !front.test(c)) return false;
        Bundle answer;
        try {
            answer = asker.offer(c, of(d, kind).toBundle());
        } catch (RuntimeException e) {
            answer = null;
        }
        boolean took = taken(answer);
        String why = answer == null ? "no answer" : answer.getString(com.gathin.broadcast.BoardAlert.WHY, "");
        AlertLog.note(c, (took ? "alert to Homefield TV's scoreboard: " : "Homefield TV's scoreboard didn't take it (" + why + "): ")
                + d.what);
        return took;
    }

    /** A game's card (ScoreCard) while Homefield TV is in front: offered to its board; true when taken. */
    static boolean take(Context c, ScoreCard.Data d) {
        if (d == null || d.f1 || d.gameId == null || d.homeName == null) return false;
        ScoresTicker.Game g = GoalWatchService.latestGame(d.gameId);
        BoardDrop.Drop drop = BoardCover.dropOf(d, g == null ? 0 : BoardBug.colour(g.homeColor, g.homeAlt),
                g == null ? 0 : BoardBug.colour(g.awayColor, g.awayAlt));
        return take(c, drop, NotifyKinds.ofCard(d.notify, d.kind, !d.podium.isEmpty()));
    }

    /** A football pop-up's line (the older alerts) while Homefield TV is in front: offered to its board; true when taken. */
    static boolean takeLine(Context c, CharSequence head, ScoresTicker.Game g, String sound, String scoredBy, Bitmap home, Bitmap away) {
        if (g == null || g.id == null) return false;
        return take(c, BoardCover.dropOfLine(head == null ? "" : head.toString(), g, sound, scoredBy, home, away),
                "incident".equals(sound) ? "var" : NotifyKinds.ofSound(sound));
    }

    /** A game's line-ups at its kick-off while Homefield TV is in front: both elevens offered to its board; true when taken. */
    static boolean takeLineups(Context c, ScoresTicker.Game g, ScoresTicker.Side[] sides, Bitmap home, Bitmap away) {
        if (g == null || g.id == null || sides == null || sides.length < 2 || sides[0] == null || sides[1] == null) return false;
        if (!BoardCover.lineupsOn(c) || !NotifyPrefs.allowed(c, "kickoff", false, g.slug, g.id)) return false;
        return take(c, BoardCover.lineupsDrop(g, sides, home, away), "kickoff");
    }
}
