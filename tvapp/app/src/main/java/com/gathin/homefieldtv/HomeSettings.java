package com.gathin.homefieldtv;

import com.gathin.homefieldtv.prefs.TrailerPrefs;

import java.util.List;

/**
 * Settings › Appearance › Home, for Home, Movies and TV Shows: Trailers (a title's trailer playing in the poster the
 * remote rests on: On as it comes) and their Sound (Off as it comes: muted). MoreSettings lists the page under Home
 * style; it is one of EditSettings' Appearance pages.
 */
final class HomeSettings {
    private HomeSettings() {}

    static final String PAGE = "appearance.home";

    static boolean owns(String page) {
        return PAGE.equals(page);
    }

    /** The page's line on Appearance: "Trailers on, muted". */
    static String value(TvActivity tv) {
        if (!TrailerPrefs.on(tv)) return "Trailers off";
        return TrailerPrefs.sound(tv) ? "Trailers on, with sound" : "Trailers on, muted";
    }

    static void rows(TvActivity tv, List<RowList.Row> out) {
        out.add(RowList.Row.toggle("home:trailers", "Trailers", "Rest on a poster and its trailer plays in it", TrailerPrefs.on(tv)));
        out.add(RowList.Row.toggle("home:sound", "Sound", TrailerPrefs.sound(tv) ? "The trailers play with their sound" : "The trailers play muted",
                TrailerPrefs.sound(tv)));
        out.add(RowList.Row.note("Home, Movies and TV Shows: when the remote rests on a poster for a moment, its trailer plays inside "
                + "it (YouTube's own player), and the poster comes back as you move on. Nothing plays once you leave Homefield TV."));
    }

    /** A row chosen: changed (true), or not one of the page's (false). */
    static boolean chosen(TvActivity tv, String tag) {
        if (tag.equals("home:trailers")) {
            TrailerPrefs.setOn(tv, !TrailerPrefs.on(tv));
            return true;
        }
        if (tag.equals("home:sound")) {
            TrailerPrefs.setSound(tv, !TrailerPrefs.sound(tv));
            return true;
        }
        return false;
    }
}
