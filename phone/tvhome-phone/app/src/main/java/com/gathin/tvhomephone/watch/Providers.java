package com.gathin.tvhomephone.watch;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

/**
 * Where a title is watched: the licensed streaming app on this phone that carries it in the UK (TMDB's GB providers,
 * as the TVs' Play does), opened at its own search for the title; else that service's website; else a store that rents
 * or sells it; else TMDB's where-to-watch page. Only these licensed services are ever chosen: no debrid, torrent or
 * IPTV app.
 */
public final class Providers {
    private Providers() {}

    /** One choice: the app to open (null: the browser), the address, the service's name, and what kind it is. */
    public static final class Choice {
        public final String pkg, url, name, kind;

        Choice(String pkg, String url, String name, String kind) {
            this.pkg = pkg;
            this.url = url;
            this.name = name;
            this.kind = kind;
        }
    }

    /** TMDB provider id → {name, search address (title appended), Android packages, best first}. */
    static String[] service(String id) {
        switch (id) {
            case "8": return new String[]{"Netflix", "https://www.netflix.com/search?q=", "com.netflix.mediaclient"};
            case "9": case "119": case "10":
                return new String[]{"Prime Video", "https://www.primevideo.com/search/ref=atv_nb_sug?phrase=", "com.amazon.avod.thirdpartyclient"};
            case "337": return new String[]{"Disney+", "https://www.disneyplus.com/search?q=", "com.disney.disneyplus"};
            case "350": case "2": return new String[]{"Apple TV", "https://tv.apple.com/gb/search?term=", "com.apple.atve.android.appletv"};
            case "38": return new String[]{"BBC iPlayer", "https://www.bbc.co.uk/iplayer/search?q=", "bbc.iplayer.android"};
            case "41": case "54": return new String[]{"ITVX", "https://www.itv.com/watch/search?q=", "air.ITVMobilePlayer"};
            case "103": return new String[]{"Channel 4", "https://www.channel4.com/search?q=", "com.channel4.ondemand"};
            case "333": return new String[]{"My5", "https://www.channel5.com/search?q=", "com.mobileiq.demand5"};
            case "39": return new String[]{"NOW", "https://www.nowtv.com/watch/search?q=", "com.nowtv.player", "com.bskyb.nowtv.beta"};
            case "29": return new String[]{"Sky Go", "https://www.sky.com/watch/search?q=", "com.bskyb.skygo"};
            case "531": case "582": return new String[]{"Paramount+", "https://www.paramountplus.com/gb/search/?q=", "com.cbs.ott", "com.cbs.app"};
            case "3": return new String[]{"Google Play", "https://play.google.com/store/search?c=movies&q=", "com.android.vending"};
            default: return null;
        }
    }

    /** The service's name for a provider id ("Netflix"), or null when it isn't one the app knows. */
    public static String name(String id) {
        String[] s = service(id);
        return s == null ? null : s[0];
    }

    /**
     * The choice for a title (pure, for the tests): installed tells which packages are on the phone. Streaming
     * (flatrate, then free and with-ads, like iPlayer and ITVX) before stores (rent, then buy).
     */
    public static Choice choose(JSONObject gb, String title, int id, boolean tv, Predicate<String> installed) {
        String q = enc(title);
        Choice web = null;
        for (String kind : new String[]{"flatrate", "free", "ads"}) {
            JSONArray list = gb == null ? null : gb.optJSONArray(kind);
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i);
                String[] s = p == null ? null : service(String.valueOf(p.optInt("provider_id")));
                if (s == null || s[0].equals("Google Play")) continue;
                for (int k = 2; k < s.length; k++) if (installed.test(s[k])) return new Choice(s[k], s[1] + q, s[0], "stream");
                if (web == null) web = new Choice(null, s[1] + q, s[0], "stream");
            }
        }
        if (web != null) return web;
        for (String kind : new String[]{"rent", "buy"}) {
            JSONArray list = gb == null ? null : gb.optJSONArray(kind);
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i);
                String[] s = p == null ? null : service(String.valueOf(p.optInt("provider_id")));
                if (s == null) continue;
                for (int k = 2; k < s.length; k++) if (installed.test(s[k])) return new Choice(s[k], s[1] + q, s[0], "store");
                return new Choice(null, s[1] + q, s[0], "store");
            }
        }
        return new Choice(null, "https://www.themoviedb.org/" + (tv ? "tv/" : "movie/") + id + "/watch?locale=GB", "TMDB", "web");
    }

    /** "On Netflix", "Rent or buy on Apple TV", "Not streaming in the UK yet": the title page's line. */
    public static String where(JSONObject gb) {
        for (String kind : new String[]{"flatrate", "free", "ads"}) {
            JSONArray list = gb == null ? null : gb.optJSONArray(kind);
            StringBuilder b = new StringBuilder();
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i);
                String n = p == null ? null : name(String.valueOf(p.optInt("provider_id")));
                if (n == null || n.equals("Google Play") || b.indexOf(n) >= 0) continue;
                if (b.length() > 0) b.append(", ");
                b.append(n);
            }
            if (b.length() > 0) return "On " + b;
        }
        for (String kind : new String[]{"rent", "buy"}) {
            JSONArray list = gb == null ? null : gb.optJSONArray(kind);
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i);
                String n = p == null ? null : name(String.valueOf(p.optInt("provider_id")));
                if (n != null) return "Rent or buy on " + n;
            }
        }
        return "Not streaming in the UK yet";
    }

    /** Opens the choice: in its app when it takes the address, else the app itself, else the browser. */
    public static void open(Context c, Choice ch) {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(ch.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (ch.pkg != null) {
            Intent inApp = new Intent(view).setPackage(ch.pkg);
            if (inApp.resolveActivity(c.getPackageManager()) != null) {
                try {
                    c.startActivity(inApp);
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                    // the app itself below
                }
            }
            Intent launch = c.getPackageManager().getLaunchIntentForPackage(ch.pkg);
            if (launch != null) {
                c.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
        }
        try {
            c.startActivity(view);
        } catch (ActivityNotFoundException ignored) {
            // no browser at all
        }
    }

    public static Predicate<String> installedOn(Context c) {
        PackageManager pm = c.getPackageManager();
        return pkg -> {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        };
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }
}
