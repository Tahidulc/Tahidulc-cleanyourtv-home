package com.gathin.tvhomephone.watch;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;

/**
 * Where a title is watched, by the rule agreed for the TVs (after the Netflix test): a title on one of the streaming
 * apps on this phone that carry it in the UK (TMDB's GB providers) opens through Debrid Stream when it is on the phone
 * (it hands the title to that service), else in that app at its own search; a title on none of them never goes to
 * Debrid Stream: that service's website, else a store that rents or sells it, else TMDB's where-to-watch page. Never an
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

    /** The choice for a title on this phone: choose(), then through Debrid Stream when the rule says so. */
    public static Choice choose(Context c, JSONObject gb, String title, int id, boolean tv) {
        return viaDebrid(choose(gb, title, id, tv, installedOn(c)), title, debridPackage(c));
    }

    /**
     * The agreed rule (pure, for the tests): only a title streaming on an app that is on this phone (kind "stream" with
     * its package) goes through Debrid Stream, and only when Debrid Stream is here; anything else stays as it is.
     */
    public static Choice viaDebrid(Choice ch, String title, String debridPkg) {
        if (debridPkg == null || ch == null || !"stream".equals(ch.kind) || ch.pkg == null) return ch;
        return new Choice(debridPkg, title == null ? "" : title, "Debrid Stream", "debrid");
    }

    /** Debrid Stream's own package (the TVs' app, which also runs on a phone). */
    public static final String DEBRID = "com.debridstream.tv";

    /** Debrid Stream's package on this phone (its own name, else any launcher app whose package names it), or null. */
    public static String debridPackage(Context c) {
        PackageManager pm = c.getPackageManager();
        try {
            pm.getPackageInfo(DEBRID, 0);
            return DEBRID;
        } catch (PackageManager.NameNotFoundException ignored) {
            // another build of it, by name below
        }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        for (android.content.pm.ResolveInfo r : apps) {
            String p = r.activityInfo == null ? null : r.activityInfo.packageName;
            if (p != null && p.toLowerCase(java.util.Locale.ROOT).contains("debrid")) return p;
        }
        return null;
    }

    /**
     * Play: through Debrid Stream as the rule says. When the title is on one of this phone's streaming apps but Debrid
     * Stream isn't on the phone, it asks instead of opening that app straight away: Play on TV (through Debrid Stream
     * there; onTv null: not offered), the service's own app, or nothing.
     */
    public static void play(android.app.Activity a, Choice ch, Runnable onTv) {
        if (!"stream".equals(ch.kind) || ch.pkg == null) {
            open(a, ch);
            return;
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder b = new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                .setTitle("Debrid Stream isn't on this phone")
                .setMessage("Install Debrid Stream on this phone and Play opens titles in it, as on the TVs."
                        + (onTv != null ? " For now, play it on a TV through Debrid Stream, or open " + ch.name + " here." : ""))
                .setNeutralButton("Open " + ch.name, (d, w) -> open(a, ch))
                .setNegativeButton("Cancel", null);
        if (onTv != null) b.setPositiveButton("Play on TV", (d, w) -> onTv.run());
        b.show();
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
        if ("debrid".equals(ch.kind)) {
            // Debrid Stream at its search for the title when it takes one; else the app, the title ready to paste.
            Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(ch.pkg).putExtra(SearchManager.QUERY, ch.url)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (search.resolveActivity(c.getPackageManager()) != null) {
                try {
                    c.startActivity(search);
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                    // the app itself below
                }
            }
            Intent launch = c.getPackageManager().getLaunchIntentForPackage(ch.pkg);
            if (launch == null) launch = c.getPackageManager().getLeanbackLaunchIntentForPackage(ch.pkg);
            if (launch != null) {
                ClipboardManager cb = c.getSystemService(ClipboardManager.class);
                if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("title", ch.url));
                android.widget.Toast.makeText(c, "In Debrid Stream: search for " + ch.url + " (copied, paste it in)", android.widget.Toast.LENGTH_LONG).show();
                c.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return;
        }
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
