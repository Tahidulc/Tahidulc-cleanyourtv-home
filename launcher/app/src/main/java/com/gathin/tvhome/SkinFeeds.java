package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Handler;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The Home styles' SkinHost over launcher v2's engine (HomeV2): its rows as Items, its pictures and caches, the guide's
 * channels and Sky's schedules, and what OK does, exactly as on the launcher's own Home.
 */
final class SkinFeeds implements SkinHost {
    private final HomeV2 h;
    /** The section pages' tabs and readers (made once; a page's object holds no views until it is opened). */
    private final Map<String, PagesV2.PosterPage> pages = new HashMap<>();
    /** Because you watched: the title it is because of, once its row has been read. */
    private volatile String becauseName;

    SkinFeeds(HomeV2 h) {
        this.h = h;
    }

    @Override public Context context() {
        return h.a;
    }

    @Override public int px(float design) {
        return h.px(design);
    }

    @Override public Typeface regular() {
        return h.regular;
    }

    @Override public Typeface semi() {
        return h.semi;
    }

    @Override public Typeface display() {
        return h.displaySemi;
    }

    @Override public Handler main() {
        return h.main;
    }

    @Override public Executor io() {
        return h.io;
    }

    @Override public void image(ImageView v, String url, int width) {
        h.image(v, url, width);
    }

    @Override public void image(ImageView v, String url, int width, Runnable then) {
        h.image(v, url, width, then);
    }

    // ---- rows -------------------------------------------------------------------------------------------------------

    @Override public List<Skin.Item> row(String which) throws Exception {
        String k = h.tmdbKey;
        int hash = which.indexOf('#');
        if (hash > 0) {
            PagesV2.PosterPage p = page(which.substring(0, hash));
            int tab = Integer.parseInt(which.substring(hash + 1));
            if (p == null || tab < 0 || tab >= p.tabNames().length) return new ArrayList<>();
            List<Skin.Item> out = new ArrayList<>();
            for (PagesV2.Row r : p.rowsOf(tab)) out.addAll(titles(r.items, r.numbered));
            return out;
        }
        switch (which) {
            case Skin.ROW_CONTINUE: return resume(WatchHistory.titles(h.a, null));
            case Skin.ROW_CONTINUE_TV: return resume(WatchHistory.titles(h.a, true));
            case Skin.ROW_CONTINUE_FILMS: return resume(WatchHistory.titles(h.a, false));
            case Skin.ROW_LIVE: return tiles(h.tiles("ar.tvplayer.tv"));
            case Skin.ROW_SPORT: {
                List<HomeV2.Tile> got = h.sportTiles;
                if (got == null || got.isEmpty()) got = h.sportNow();
                return tiles(got);
            }
            case Skin.ROW_TOP_TV: return k.isEmpty() ? new ArrayList<>() : titles(PagesV2.tmdb(k, "trending/tv/week", true), true);
            case Skin.ROW_TOP_FILMS: return k.isEmpty() ? new ArrayList<>() : titles(PagesV2.tmdb(k, "trending/movie/week", false), true);
            case Skin.ROW_FITS: return titles(FitsTonight.row(h).items, false);
            case Skin.ROW_YOUR_SHOWS: return titles(YourShows.row(h).items, false);
            case Skin.ROW_NEW_EPISODES: return titles(YourShows.fresh(h).items, false);
            case Skin.ROW_DEBRID: {
                String pkg = h.debridPackage();
                return tiles(h.tiles(pkg != null ? pkg : "movies"));
            }
            case Skin.ROW_BECAUSE: {
                List<Because.Source> src = Because.sources(h.a);
                if (src.isEmpty()) return new ArrayList<>();
                becauseName = Because.words(src.get(0));
                return titles(Because.row(h, src.get(0)).items, false);
            }
            case Skin.ROW_COMING: return k.isEmpty() ? new ArrayList<>() : titles(PagesV2.tmdbRow(k, "movie/upcoming?region=GB", false), false);
            case Skin.ROW_TRAILERS: return titles(TrailerReel.row(h, TrailerReel.ALL).items, false);
            case Skin.ROW_KIDS_TV: return row("kids#4");
            case Skin.ROW_KIDS_FILMS: return row("kids#6");
            case Skin.ROW_APPS: return tiles(h.tiles("apps"));
            case Skin.ROW_DOCK: return dock();
            case Skin.ROW_MY_LIST: return titles(MyList.titles(h.a), false);
            case Skin.ROW_YOUTUBE: return tiles(h.tiles("com.google.android.youtube.tv"));
            case Skin.ROW_TRENDING: return k.isEmpty() ? new ArrayList<>() : titles(PagesV2.tmdbRow(k, "trending/all/day", false), false);
            case Skin.ROW_NEW: {
                List<Skin.Item> out = tiles(h.tiles("movies"));
                out.addAll(tiles(h.tiles("shows")));
                return out;
            }
            case Skin.ROW_NEWS: return news();
            default: return new ArrayList<>();
        }
    }

    @Override public String rowName(String which) {
        if (Skin.ROW_BECAUSE.equals(which)) return becauseName;
        return null;
    }

    @Override public List<String> tabs(String page) {
        PagesV2.PosterPage p = page(page);
        List<String> out = new ArrayList<>();
        if (p != null) for (String t : p.tabNames()) out.add(t);
        return out;
    }

    private synchronized PagesV2.PosterPage page(String name) {
        PagesV2.PosterPage p = pages.get(name);
        if (p != null) return p;
        switch (name) {
            case "movies": p = PagesV2.movies(h); break;
            case "shows": p = PagesV2.shows(h); break;
            case "kids": p = PagesV2.kids(h); break;
            case "discover": p = PagesV2.discover(h); break;
            default: return null;
        }
        pages.put(name, p);
        return p;
    }

    /** Continue watching, with how far each got. */
    private List<Skin.Item> resume(List<PagesV2.Title> in) {
        List<Skin.Item> out = titles(in, false);
        Map<Integer, Float> far = WatchHistory.progress(h.a);
        for (Skin.Item it : out) {
            Float f = far.get(it.tmdbId);
            if (f != null) it.progress = f;
            if (it.app != null) it.line = appName(it.app);
        }
        return out;
    }

    static List<Skin.Item> titles(List<PagesV2.Title> in, boolean ranked) {
        List<Skin.Item> out = new ArrayList<>();
        if (in == null) return out;
        int n = 0;
        for (PagesV2.Title t : in) {
            Skin.Item it = new Skin.Item();
            it.src = t;
            it.name = t.title;
            it.poster = t.poster;
            it.backdrop = t.backdrop != null && !t.backdrop.startsWith("mood:") ? t.backdrop : null;
            it.overview = t.overview;
            it.year = t.year;
            it.vote = t.vote;
            it.tmdbId = t.id;
            it.tv = t.tv;
            it.app = t.app;
            it.flag = t.badge;
            it.runtime = t.runtime;
            it.sub = t.foot != null ? t.foot : t.what;
            if (t.season >= 0) it.progress = t.season;
            if (ranked) it.rank = ++n;
            out.add(it);
        }
        return out;
    }

    static List<Skin.Item> tiles(List<HomeV2.Tile> in) {
        List<Skin.Item> out = new ArrayList<>();
        if (in == null) return out;
        for (HomeV2.Tile t : in) {
            Skin.Item it = new Skin.Item();
            it.src = t;
            it.kind = t.channel != null && t.group == null ? Skin.Item.LIVE : t.group != null ? Skin.Item.SPORT
                    : t.icon != null ? Skin.Item.APP : t.video ? Skin.Item.VIDEO : Skin.Item.TITLE;
            it.name = t.title;
            it.sub = t.sub;
            it.line = t.heroMeta;
            it.backdrop = t.heroImage != null ? t.heroImage : t.image;
            it.logo = t.logo != null ? t.logo : t.heroLogo;
            it.mark = t.compLogo != null ? t.compLogo : t.badge;
            it.overview = t.heroText;
            it.progress = t.progress;
            it.icon = t.icon;
            it.tmdbId = t.tmdbId;
            it.tv = t.tv;
            it.start = t.start;
            it.channel = t.channel;
            it.flag = t.chip;
            out.add(it);
        }
        return out;
    }

    /** The dock's apps, in its order, each with its icon. */
    private List<Skin.Item> dock() {
        PackageManager pm = h.a.getPackageManager();
        List<Skin.Item> out = new ArrayList<>();
        for (String pkg : h.dockApps()) {
            if (!h.installed(pkg)) continue;
            try {
                Skin.Item it = new Skin.Item();
                it.kind = Skin.Item.APP;
                it.app = pkg;
                it.name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (i == null) i = pm.getLaunchIntentForPackage(pkg);
                List<ResolveInfo> r = i == null ? null : pm.queryIntentActivities(i, 0);
                it.icon = r != null && !r.isEmpty() ? r.get(0).loadIcon(pm) : pm.getApplicationIcon(pkg);
                out.add(it);
            } catch (PackageManager.NameNotFoundException ignored) {
                // gone since the dock was set
            }
        }
        return out;
    }

    private String appName(String pkg) {
        try {
            PackageManager pm = h.a.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static final java.util.regex.Pattern DISTRESSING = java.util.regex.Pattern.compile(
            "(?i)\\b(rape[ds]?|raping|murder(s|ed|er)?|kill(s|ed|ing)?|dies|died|death|dead|stabb(ed|ing)|abuse[ds]?|assault(s|ed)?"
                    + "|terror\\w*|shooting|shot dead|paedophile|grooming|suicide|massacre|war crimes?)\\b");

    /** BBC News and BBC Sport's headlines, each an item (none about violence or abuse). */
    private static List<Skin.Item> news() {
        List<Skin.Item> out = new ArrayList<>();
        String[][] feeds = {{"https://feeds.bbci.co.uk/news/rss.xml", "BBC NEWS"}, {"https://feeds.bbci.co.uk/sport/rss.xml", "BBC SPORT"}};
        for (String[] f : feeds) {
            for (String line : NewsTicker.headlines(f[0], 8)) {
                // Home is for the family: the headlines about violence and abuse are left to the news itself.
                if (DISTRESSING.matcher(line).find()) continue;
                Skin.Item it = new Skin.Item();
                it.kind = Skin.Item.NEWS;
                it.name = line;
                it.sub = f[1];
                out.add(it);
            }
        }
        return out;
    }

    // ---- the guide --------------------------------------------------------------------------------------------------

    @Override public List<String[]> channels() {
        return h.channels;
    }

    @Override public Map<String, List<GuideV2.Show>> schedules(List<String[]> channels, long from) {
        Map<String, List<GuideV2.Show>> out = new HashMap<>();
        for (int i = 0; i < channels.size(); i += 20)
            out.putAll(GuideV2.schedules(channels.subList(i, Math.min(channels.size(), i + 20)), from));
        return out;
    }

    @Override public String channelLogo(String sid) {
        return GuideV2.logoUrl(sid);
    }

    // ---- actions ----------------------------------------------------------------------------------------------------

    @Override public void ok(Skin.Item it) {
        if (it.src instanceof HomeV2.Tile) {
            HomeV2.Tile t = (HomeV2.Tile) it.src;
            if (t.ok != null) t.ok.run();
            return;
        }
        if (it.src instanceof PagesV2.Title) {
            PagesV2.Title t = (PagesV2.Title) it.src;
            // Part-watched: on in the app it was being watched in; anything else with its own action does that.
            if (t.app != null || t.ok != null) { play(t); return; }
            h.showTitle(t, () -> play(t), false);
            return;
        }
        if (Skin.Item.APP.equals(it.kind) && it.app != null) h.openApp(it.app);
    }

    @Override public void details(Skin.Item it) {
        if (it.src instanceof PagesV2.Title) {
            PagesV2.Title t = (PagesV2.Title) it.src;
            if (t.hold != null) { t.hold.run(); return; }
            h.showTitle(t, () -> play(t), true);
            return;
        }
        if (it.src instanceof HomeV2.Tile && it.tmdbId > 0) {
            PagesV2.Title t = new PagesV2.Title();
            t.id = it.tmdbId;
            t.tv = it.tv;
            t.title = it.name;
            t.backdrop = it.backdrop;
            t.overview = it.overview;
            HomeV2.Tile tile = (HomeV2.Tile) it.src;
            h.showTitle(t, () -> { if (tile.ok != null) tile.ok.run(); }, true);
            return;
        }
        ok(it);
    }

    private void play(PagesV2.Title t) {
        if (t.ok != null) { t.ok.run(); return; }
        h.playLikePages(t);
    }

    @Override public void tune(String[] channel) {
        if (channel == null || channel.length < 2) return;
        h.tune(channel[1], GuideV2.logoUrl(channel[0]));
    }

    @Override public void open(String what) {
        switch (what) {
            case Skin.OPEN_CONTROL_CENTRE: h.openControlCentre(); break;
            case Skin.OPEN_SEARCH: h.openSearch(); break;
            case Skin.OPEN_STYLES: HomeStyle.pick(h); break;
            case Skin.OPEN_NOTIFICATIONS: h.openNotifications(); break;
            case Skin.OPEN_WEATHER: h.openWeather(); break;
            default: break;
        }
    }

    @Override public String weather() {
        return h.weatherWords();
    }

    @Override public List<String[]> ticker() {
        List<String[]> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        List<HomeV2.Tile> sport = h.sportTiles;
        if (sport != null) {
            for (HomeV2.Tile t : sport) {
                if (t.title == null) continue;
                boolean on = t.start > 0 && t.start <= now && now - t.start < 3 * 3600_000L;
                if (on) out.add(new String[]{"LIVE", t.title + (t.channel != null ? " · " + t.channel : "")});
                else if (t.start > now && t.start - now < 6 * 3600_000L)
                    out.add(new String[]{Skin.hm(t.start), t.title + (t.channel != null ? " · " + t.channel : "")});
                if (out.size() >= 4) break;
            }
        }
        for (Skin.Item n : news()) {
            out.add(new String[]{n.sub.contains("SPORT") ? "SPORT" : "NEWS", n.name});
            if (out.size() >= 8) break;
        }
        return out;
    }

    @Override public int bedMinutes() {
        return FitsTonight.bedMinutes(h.a);
    }

    @Override public boolean lite() {
        return h.lite;
    }

    /** The style's name for a toast ("SWIPTV style"). */
    static String label(String style) {
        return HomeStyle.name(style).toLowerCase(Locale.ROOT);
    }
}
