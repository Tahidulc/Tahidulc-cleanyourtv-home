package com.gathin.homefieldtv;

import android.content.Context;

import com.gathin.broadcast.Sfx;
import com.gathin.homefieldtv.sports.InStep;

import java.util.List;

/**
 * Settings' Scoreboard section: the scoreboard over matches in the player (BoardLayer) and the cover over the channel's
 * logo on or off, the look (the Scoreboard Lab's and By competition), what comes out of it, the pop-ups, the kick-off's
 * card and the sounds; each line's OK changes it (a look steps on to the next). Moving and sizing are done over the match
 * itself (OK held there), as on the launcher.
 */
final class BoardSettings {
    private BoardSettings() {}

    private static String onOff(boolean on) {
        return on ? "On" : "Off";
    }

    /** The section's lines, as the settings list shows them. */
    static void fill(Context c, List<ListPanel.Item> out) {
        out.add(new ListPanel.Item(null, ListPanel.HEADING, "Scoreboard over matches", ""));
        out.add(new ListPanel.Item("board", ListPanel.TEXT, "Scoreboard", onOff(BoardPrefs.on(c))
                + "  ·  Over a match you're watching, in step with your channel"));
        out.add(new ListPanel.Item("logo", ListPanel.TEXT, "Logo cover", onOff(BoardPrefs.logoOn(c))
                + "  ·  Over the channel's own logo while a match is on"));
        out.add(new ListPanel.Item("look", ListPanel.TEXT, "Look", BoardPrefs.style(c).label + "  ·  OK for the next"));
        out.add(new ListPanel.Item("drops", ListPanel.TEXT, "Alerts beside the board", onOff(BoardPrefs.dropsOn(c))
                + "  ·  Cards, substitutions, VAR, added time, half and full time"));
        out.add(new ListPanel.Item("var", ListPanel.TEXT, "VAR explained", onOff(BoardPrefs.varExplainedOn(c))
                + "  ·  The check, then the decision and why"));
        out.add(new ListPanel.Item("popup", ListPanel.TEXT, "Kick-off and goal pop-ups", onOff(BoardPrefs.popupOn(c))
                + "  ·  The kick-off, after a goal and the second half"));
        out.add(new ListPanel.Item("lineups", ListPanel.TEXT, "Line-ups before kick-off", onOff(BoardPrefs.lineupsOn(c))
                + "  ·  Both elevens, seven minutes before"));
        out.add(new ListPanel.Item("ask", ListPanel.TEXT, "Ask at each kick-off", onOff(InStep.asking(c))
                + "  ·  OK as it kicks off sets the clock with your channel's"));
        out.add(new ListPanel.Item("sound", ListPanel.TEXT, "Goal sound", Sfx.goalName(BoardPrefs.goalSound(c)) + "  ·  OK for the next"));
        out.add(new ListPanel.Item("swish", ListPanel.TEXT, "Swishes", onOff(BoardPrefs.swishOn(c))
                + "  ·  As alerts slide out and the new score pops up"));
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "Over a match, hold OK for the scoreboard's menu: the look, timing, moving and sizing it and the logo cover.", ""));
    }

    /** A line chosen: what it sets, changed (true), or not one of the section's (false). */
    static boolean chosen(Context c, String tag) {
        switch (tag) {
            case "board": BoardPrefs.setOn(c, !BoardPrefs.on(c)); return true;
            case "logo": BoardPrefs.setLogoOn(c, !BoardPrefs.logoOn(c)); return true;
            case "drops": BoardPrefs.setDropsOn(c, !BoardPrefs.dropsOn(c)); return true;
            case "var": BoardPrefs.setVarExplainedOn(c, !BoardPrefs.varExplainedOn(c)); return true;
            case "popup": BoardPrefs.setPopupOn(c, !BoardPrefs.popupOn(c)); return true;
            case "lineups": BoardPrefs.setLineupsOn(c, !BoardPrefs.lineupsOn(c)); return true;
            case "ask": InStep.setAsking(c, !InStep.asking(c)); return true;
            case "swish": {
                boolean on = !BoardPrefs.swishOn(c);
                BoardPrefs.setSwishOn(c, on);
                BoardPrefs.setScoreSwishOn(c, on);
                return true;
            }
            case "look": {
                BoardBug.Style[] all = BoardPrefs.looks();
                BoardBug.Style now = BoardPrefs.style(c);
                int i = 0;
                for (int k = 0; k < all.length; k++) if (all[k] == now) i = k;
                BoardPrefs.setStyle(c, all[(i + 1) % all.length]);
                return true;
            }
            case "sound": {
                String next = Sfx.nextGoal(BoardPrefs.goalSound(c));
                BoardPrefs.setGoalSound(c, next);
                Sfx.goal(c, next);
                return true;
            }
            default:
                return false;
        }
    }
}
