package com.gathin.homefieldtv;

import android.content.Context;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Parental;

import java.util.List;

/**
 * A channel's lines in the TV guide's long-OK card (GuideMenu), under its name, as the family's TiviMate lists them
 * (DEVICE.md §2.8, 103: Add to Favorites · Block channel · Hide channel), with the editing TiviMate offers after them:
 *
 * <pre>
 *   Add to / Remove from Favourites   the favourites (Stop does the same)
 *   Block channel / Unblock channel   Parental control's lock (the PIN asked before it plays)
 *   Hide channel                      out of every list (Settings › Appearance › TV guide › Hidden channels brings it back)
 *   Rename channel                    a name of the family's own, typed on the keyboard (with Appearance › TV guide ›
 *                                     Channel names editor on, as it comes); Restore channel name once renamed
 *   Change logo                       the new logos found for it, the best first, or the playlist's (LogoScreen)
 *   Move channel                      TiviMate's Manage positions: the row lifts, Up and Down move it, OK puts it down,
 *                                     Back leaves it (the guide's own: MoveMode)
 *   Catch-up                          Homefield's: the channel's archive by day, where the provider keeps one
 * </pre>
 *
 * Kept by the channel's key (ChannelEdits), so they hold across reloads; every list goes by them.
 */
final class ChannelMenu {
    private ChannelMenu() {}

    static final String HIDE = "ch.hide", RENAME = "ch.rename", UNNAME = "ch.unname", LOGO = "ch.logo", MOVE = "ch.move";

    /** The channel's lines; movable: the list on screen can take Move channel (not Recently watched, nor the archive). */
    static void items(Context c, Channel ch, boolean archiveLink, boolean movable, List<GuideMenu.Item> out) {
        out.add(GuideMenu.Item.item(GuideActions.FAVOURITE, Library.get(c).isFavourite(ch) ? "Remove from Favourites" : "Add to Favourites"));
        boolean blocked = Parental.lockedChannels(c).contains(ch.key);
        GuideMenu.Item block = GuideMenu.Item.item(GuideActions.BLOCK, blocked ? "Unblock channel" : "Block channel");
        out.add(blocked ? block : block.warn());
        out.add(GuideMenu.Item.item(HIDE, "Hide channel"));
        if (ChannelEdits.editor(c)) {
            out.add(GuideMenu.Item.item(RENAME, "Rename channel"));
            if (ChannelEdits.renamed(ch)) out.add(GuideMenu.Item.item(UNNAME, "Restore channel name"));
        }
        out.add(GuideMenu.Item.item(LOGO, "Change logo").value(logoWords(ch)));
        if (movable) out.add(GuideMenu.Item.item(MOVE, "Move channel"));
        if (archiveLink && Catchup.source(ch) != null) out.add(GuideMenu.Item.item(GuideActions.ARCHIVE, "Catch-up"));
    }

    /** The logo the channel shows now, for Change logo's line: "BBC Two · tv-logos", or "Playlist's". */
    static String logoWords(Channel ch) {
        String choice = ChannelEdits.logo(ch.key);
        String path = ChannelEdits.LOGO_PLAYLIST.equals(choice) ? null : !choice.isEmpty() ? choice
                : com.gathin.homefieldtv.engine.LogoPack.on() ? com.gathin.homefieldtv.engine.LogoPack.auto(ch) : null;
        if (path == null || path.isEmpty()) return "Playlist's";
        return com.gathin.homefieldtv.engine.LogoMatch.labelOf(path) + " · tv-logos";
    }

    /** Whether a line is one of these (Move channel is the guide's own). */
    static boolean handles(String tag) {
        return tag != null && tag.startsWith("ch.");
    }

    /** What a line does (all but Move channel). */
    static void act(TvActivity tv, String tag, Channel c) {
        if (c == null) return;
        switch (tag) {
            case HIDE:
                hide(tv, c);
                break;
            case RENAME:
                rename(tv, c);
                break;
            case UNNAME:
                ChannelEdits.rename(tv, c, "");
                Toast.makeText(tv, c.name + ": its playlist's name again", Toast.LENGTH_SHORT).show();
                break;
            case LOGO:
                tv.push(new LogoScreen(tv, c));
                break;
            default:
                break;
        }
    }

    /** Hide channel: out of every list at once; Settings › Appearance › TV guide › Hidden channels brings it back. */
    static void hide(TvActivity tv, Channel c) {
        ChannelEdits.setHidden(tv, c.key, true);
        Toast.makeText(tv, ChannelEdits.name(c) + " hidden. Settings › Appearance › TV guide › Hidden channels brings it back",
                Toast.LENGTH_LONG).show();
    }

    /** Rename channel: the keyboard over the screen, the name now in it; nothing typed (or its own) is the playlist's again. */
    static void rename(TvActivity tv, Channel c) {
        String now = ChannelEdits.name(c);
        tv.push(new RenameScreen(tv, "A name for " + now, now, text -> {
            ChannelEdits.rename(tv, c, text);
            String shown = ChannelEdits.name(c);
            Toast.makeText(tv, ChannelEdits.renamed(c) ? c.name + " is now " + shown : shown + ": its playlist's name again",
                    Toast.LENGTH_SHORT).show();
        }));
    }
}
