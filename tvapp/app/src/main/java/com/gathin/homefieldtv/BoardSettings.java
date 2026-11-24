package com.gathin.homefieldtv;

import android.content.Context;

import com.gathin.broadcast.Sfx;
import com.gathin.homefieldtv.sports.InStep;

import java.util.List;

/**
 * Settings → Other → Scoreboard: the scoreboard over matches in the player (BoardLayer) and the cover over the channel's
 * logo, each with its switch; the look (the Scoreboard Lab's and By competition), the pop-ups' style (in my scoreboard's
 * look, classic or off) and the goal sound, OK stepping on to the next; the alerts, line-ups, the kick-off's card and the
 * swishes. Moving and sizing are done over the match itself (OK held there), as on the launcher.
 */
final class BoardSettings {
    private BoardSettings() {}

    /** The page's rows. */
    static void rows(Context c, List<RowList.Row> out) {
        out.add(RowList.Row.toggle("board", "Scoreboard", "Over a match you're watching, in step with your channel", BoardPrefs.on(c)));
        out.add(RowList.Row.toggle("logo", "Logo cover", "Over the channel's own logo while a match is on", BoardPrefs.logoOn(c)));
        out.add(RowList.Row.toggle("logoonly", "Logo cover only", "The cover over the channel's logo, without the scoreboard",
                BoardPrefs.logoOnly(c)));
        out.add(RowList.Row.item("look", "Look", BoardPrefs.style(c).label + "  ·  OK for the next"));
        // My scoreboards (AddedSettings' choices): the family's own boards, made over a match or on the phone.
        out.add(MoreSettings.pick(AddedSettings.MINE, "My scoreboards", AddedSettings.mineWords(c)));
        out.add(AiSettings.row(c));
        out.add(RowList.Row.header("Alerts and pop-ups"));
        out.add(RowList.Row.toggle("drops", "Alerts beside the board", "Cards, substitutions, VAR, added time, half and full time",
                BoardPrefs.dropsOn(c)));
        out.add(RowList.Row.toggle("var", "VAR explained", "The check, then the decision and why", BoardPrefs.varExplainedOn(c)));
        out.add(RowList.Row.item("popup", "Kick-off and goal pop-ups", BoardPrefs.popupWords(BoardPrefs.popupStyle(c)) + "  ·  OK for the next"));
        out.add(RowList.Row.toggle("lineups", "Line-ups before kick-off", "Both elevens, seven minutes before", BoardPrefs.lineupsOn(c)));
        out.add(RowList.Row.toggle("ask", "Ask at each kick-off", "OK as it kicks off sets the clock with your channel's", InStep.asking(c)));
        out.add(RowList.Row.header("Sounds"));
        out.add(RowList.Row.item("sound", "Goal sound", Sfx.goalName(BoardPrefs.goalSound(c)) + "  ·  OK for the next"));
        out.add(RowList.Row.toggle("swish", "Swishes", "As alerts slide out and the new score pops up", BoardPrefs.swishOn(c)));
        // More scoreboard settings (AddedSettings' page): what the scoreboard's own menu has, these for every match.
        out.add(RowList.Row.item("page:other.scoreboard.more", "More scoreboard settings", "Look, timing, pop-ups, moving and sizing it"));
        out.add(RowList.Row.note("The bar has Scoreboard on every channel, for the scoreboard's menu. Over a match, hold OK for it too: "
                + "the look, My scoreboards, the scoreboard editor (colours, Generate, Ask AI), timing, moving and sizing it and the "
                + "logo cover. Boards saved here or on the phone are in My scoreboards on both."));
    }

    /** A row chosen: what it sets, changed (true), or not one of the page's (false). */
    static boolean chosen(Context c, String tag) {
        switch (tag) {
            case "board": BoardPrefs.setOn(c, !BoardPrefs.on(c)); return true;
            case "logo": BoardPrefs.setLogoOn(c, !BoardPrefs.logoOn(c)); return true;
            case "logoonly": BoardPrefs.setLogoOnly(c, !BoardPrefs.logoOnly(c)); return true;
            case "drops": BoardPrefs.setDropsOn(c, !BoardPrefs.dropsOn(c)); return true;
            case "var": BoardPrefs.setVarExplainedOn(c, !BoardPrefs.varExplainedOn(c)); return true;
            case "popup": BoardPrefs.setPopupStyle(c, BoardPrefs.nextPopup(BoardPrefs.popupStyle(c), 1)); return true;
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
