package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.glass.PosterCard;

import java.util.List;

/**
 * What Settings' main page (settings.Sections) says under each of the TV part's own sections, for the family's "make
 * the settings section on the homefield phone app easier to navigate": one plain line of how each is set now ("2
 * playlists · Quality: Adaptive"), read off the main thread; and the 07 glass the page's rows and search sit on. It
 * only reads: every setting is still changed in its own section.
 */
public final class SettingsLines {
    private SettingsLines() {}

    /** "2 playlists · Quality: Adaptive", or "No playlists yet · Quality: Adaptive". */
    public static String live(Context c) {
        int n = Sources.list(c).size();
        String lists = n == 0 ? c.getString(R.string.sn_live_none) : c.getResources().getQuantityString(R.plurals.sn_playlists, n, n);
        return c.getString(R.string.sn_live_line, lists, Quality.label(Quality.choice(c)));
    }

    /** "Homefield • Amber · 24-hour times". */
    public static String appearance(Context c) {
        return c.getString(R.string.sn_look_line, Theme.name(), c.getString(Times.h24() ? R.string.sn_times_24 : R.string.sn_times_12));
    }

    /** "3 reminders · next Today 19:00", or "None set". */
    public static String reminders(Context c) {
        List<Reminders.Kept> all = Reminders.upcoming(c);
        if (all.isEmpty()) return c.getString(R.string.sn_reminders_none);
        long at = all.get(0).at;
        return c.getString(R.string.sn_reminders_line, c.getResources().getQuantityString(R.plurals.sn_reminders, all.size(), all.size()),
                Ui.day(c, at) + " " + Ui.clock(at));
    }

    /** "On · the built-in films", "On · your own video", or "Off". */
    public static String intro(Context c) {
        if (!PlayerIntro.introOn(c)) return c.getString(R.string.sn_intro_off);
        return c.getString(IntroVideo.useOwn(c) ? R.string.sn_intro_own : R.string.sn_intro_builtin);
    }

    /** "One row per channel · By order in playlist". */
    public static String channels(Context c) {
        int sort = ChannelEdits.sort(c);
        String order = sort >= 0 && sort < ChannelEdits.SORT_NAMES.length ? c.getString(ChannelEdits.SORT_NAMES[sort]) : "";
        String rows = c.getString(ChannelEdits.merging(c) ? R.string.sn_channels_merged : R.string.sn_channels_apart);
        return order.isEmpty() ? rows : c.getString(R.string.sn_channels_line, rows, order);
    }

    /** "Portrait posters" or "Landscape cards". */
    public static String posters(Context c) {
        return c.getString(PosterCard.wide(c) ? R.string.sn_posters_landscape : R.string.sn_posters_portrait);
    }

    /** "On · English, French", or "Off". */
    public static String subtitles(Context c) {
        if (!Subtitles.on(c)) return c.getString(R.string.sn_subs_off);
        StringBuilder b = new StringBuilder();
        for (String code : Subtitles.languages(c)) {
            if (b.length() > 0) b.append(", ");
            b.append(Subtitles.languageName(code));
        }
        return b.length() == 0 ? c.getString(R.string.subs_on) : c.getString(R.string.sn_subs_on, b.toString());
    }

    /** "Connected · in use", "Connected · switched off", or "Not set up". */
    public static String torbox(Context c) {
        if (!TorBox.hasKey(c)) return c.getString(R.string.sn_torbox_none);
        return c.getString(TorBox.wanted(c) ? R.string.sn_torbox_on : R.string.sn_torbox_off);
    }

    /** A piece of the Live TV screens' glass (07): a white wash, brighter at its top, its hairline and rim. */
    public static Drawable glass(Context c, float radiusDp) {
        return new TvLook.Pane(c, radiusDp, TvLook.GLASS + 0x0A000000, TvLook.GLASS - 0x08000000, true);
    }

    /** The colour theme's accent (amber, as the phone comes): what a found setting is lit in for a moment. */
    public static int accent() {
        return TvLook.AMBER;
    }

    /** The Live TV screens' writing, Inter: semibold for names, regular for the rest. */
    public static android.graphics.Typeface inter(Context c, boolean semi) {
        return semi ? TvLook.semi(c) : TvLook.inter(c);
    }
}
