package com.gathin.tvhomephone.home;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.gathin.tvhomephone.live.BreakingNews.Story;

import java.util.ArrayList;
import java.util.List;

/**
 * Home's page, in the TV launcher's style: a navy ground under a soft blurred copy of the hero's picture, the hero
 * (with the date, the weather and the clock over it), then rows with the launcher's headings: Continue watching, the
 * scores, films and TV from the playlists, BBC News's and BBC Sport's headlines, the alerts, Recently watched and the
 * TVs' cards. It only lays things out and shows what it is given; HomeFragment reads and gives (and a test can give
 * it made-up titles and draw it).
 */
final class HomeScreen {
    /** What the page's taps do. */
    interface Host {
        void play(Pick p);

        void open(Pick p);

        void resume(Resume.Card k);

        void forget(Resume.Card k);

        void story(Story s);

        void settings();
    }

    final Context c;
    final Host host;
    final FrameLayout root;
    final SwipeRefreshLayout swipe;
    final ScrollView scroll;
    final LinearLayout col;
    final TextView date, clock, weather;
    final Hero hero;
    /** Home's own sports (the scores box, the clubs' games), the alerts, Recently watched, the TVs' cards. */
    final LinearLayout sports, alerts, recent, watch;
    private final ImageView backdrop;
    private final int heroHeight;
    private final Section resumeSection, films, shows, fresh, newsSection, sportSection, note;
    private final Rows.Resumes resumes;
    private final Rows.Posters filmRow, showRow, freshRow;
    private final Rows.Stories newsRow, sportRow;
    private final TextView noteWords, noteButton;

    private List<Resume.Card> resumeCards = new ArrayList<>();
    private Pick resumeArt;
    private Feed.Result feed;
    private Headlines.Read news;
    private boolean feedLoaded, newsLoaded;
    private String heroSig = "", backdropFor = "";

    HomeScreen(Context c, Host host) {
        this.c = c;
        this.host = host;
        root = new FrameLayout(c);
        root.setBackgroundColor(Art.GROUND);
        int screenW = c.getResources().getDisplayMetrics().widthPixels;
        int screenH = c.getResources().getDisplayMetrics().heightPixels;
        boolean wide = c.getResources().getConfiguration().screenWidthDp >= 600;
        // Upright: tall, about 1.2 times the width. Sideways: what shows above the tab bar, a row's top peeking under it.
        heroHeight = wide ? Math.max(Art.dp(c, 250), Math.min(screenH - Art.dp(c, 110), Math.round(screenW * 0.56f)))
                : Math.max(Art.dp(c, 420), Math.min(Art.dp(c, 580), Math.round(screenW * 1.22f)));

        // The soft blurred backdrop: the hero's picture read tiny and stretched, faded into the navy.
        backdrop = new ImageView(c);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        backdrop.setAlpha(0.85f);
        int glow = heroHeight + Art.dp(c, 460);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, glow, Gravity.TOP));
        View fade = new View(c);
        fade.setBackground(Art.vertical(0x400D0F2C, 0xD90D0F2C, 0xFF0D0F2C));
        root.addView(fade, new FrameLayout.LayoutParams(-1, glow, Gravity.TOP));

        swipe = new SwipeRefreshLayout(c);
        swipe.setColorSchemeColors(Art.FILM, Art.SHOW, Art.SPORT);
        swipe.setProgressBackgroundColorSchemeColor(0xFF1A1F57);
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Art.dp(c, 28));
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        swipe.addView(scroll);
        root.addView(swipe, new FrameLayout.LayoutParams(-1, -1));
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> scrolled(y));

        // The hero, with the date, the weather and the clock over its top.
        hero = new Hero(c, heroHeight);
        hero.onPage(this::pageUp);
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        int side = Art.dp(c, wide ? 28 : 20);
        top.setPadding(side, Art.dp(c, 14), side, 0);
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        date = Rows.text(c, 12, Art.TEXT, Type.interSemi(c), 1);
        date.setLetterSpacing(0.12f);
        left.addView(date);
        weather = Rows.text(c, 13, Art.SOFT, Type.inter(c), 1);
        weather.setText("Manchester");
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.topMargin = Art.dp(c, 4);
        left.addView(weather, wl);
        top.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        clock = Rows.text(c, 38, Art.TEXT, Type.interLight(c), 1);
        top.addView(clock);
        for (TextView t : new TextView[]{date, weather, clock}) t.setShadowLayer(Art.dp(c, 6), 0, 0, 0x80000000);
        hero.box.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        col.addView(hero.box, new LinearLayout.LayoutParams(-1, heroHeight));

        resumes = new Rows.Resumes(c, k -> host.resume(k), k -> host.forget(k));
        resumeSection = section("Continue watching", "Pick up where you left off", Rows.row(c, resumes));
        resumeSection.box.setVisibility(View.GONE);
        col.addView(resumeSection.box);

        sports = column();
        col.addView(sports);

        note = section("Films and TV", "From your own playlists", null);
        LinearLayout noteCard = column();
        noteCard.setBackground(Art.glass(c, 18));
        int pad = Art.dp(c, 18);
        noteCard.setPadding(pad, pad, pad, pad);
        noteWords = Rows.text(c, 14, Art.SOFT, Type.inter(c), 0);
        noteWords.setLineSpacing(Art.dp(c, 3), 1f);
        noteCard.addView(noteWords);
        noteButton = Rows.text(c, 15, 0xFF10121F, Type.outfit(c, 600), 1);
        noteButton.setGravity(Gravity.CENTER);
        noteButton.setPadding(Art.dp(c, 20), 0, Art.dp(c, 20), 0);
        noteButton.setBackground(Art.round(0xFFFFFFFF, Art.dp(c, 21)));
        noteButton.setText("Open Settings");
        noteButton.setOnClickListener(v -> host.settings());
        LinearLayout.LayoutParams nb = new LinearLayout.LayoutParams(-2, Art.dp(c, 42));
        nb.topMargin = Art.dp(c, 14);
        noteCard.addView(noteButton, nb);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.leftMargin = nl.rightMargin = Art.dp(c, 16);
        note.box.addView(noteCard, nl);
        note.box.setVisibility(View.GONE);
        col.addView(note.box);

        filmRow = new Rows.Posters(c, p -> host.open(p));
        films = section("Recommended films", "Picked from what you watch", Rows.row(c, filmRow));
        col.addView(films.box);

        newsRow = new Rows.Stories(c, "BBC News", Art.NEWS, s -> host.story(s));
        newsSection = section("Top stories", "BBC News", Rows.row(c, newsRow));
        col.addView(newsSection.box);

        showRow = new Rows.Posters(c, p -> host.open(p));
        shows = section("Recommended TV shows", "Picked from what you watch", Rows.row(c, showRow));
        col.addView(shows.box);

        sportRow = new Rows.Stories(c, "BBC Sport", Art.SPORT, s -> host.story(s));
        sportSection = section("Sport headlines", "BBC Sport", Rows.row(c, sportRow));
        col.addView(sportSection.box);

        freshRow = new Rows.Posters(c, p -> host.open(p));
        fresh = section("New in your playlist", "", Rows.row(c, freshRow));
        fresh.box.setVisibility(View.GONE);
        col.addView(fresh.box);

        alerts = column();
        col.addView(alerts);
        recent = column();
        col.addView(recent);
        watch = column();
        col.addView(watch);
        hero.set(null);
    }

    View view() {
        return root;
    }

    // ---- sections -------------------------------------------------------------------------------------------------

    /** A section: its heading in Outfit, a dim line under it, then what it holds. */
    static final class Section {
        final LinearLayout box;
        final TextView title, sub;

        Section(LinearLayout box, TextView title, TextView sub) {
            this.box = box;
            this.title = title;
            this.sub = sub;
        }

        void set(String t, String s) {
            title.setText(t);
            sub.setText(s == null ? "" : s);
            sub.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    Section section(String title, String sub, View content) {
        LinearLayout box = column();
        TextView head = heading(c, title);
        box.addView(head);
        TextView line = Rows.text(c, 12, Art.DIM, Type.inter(c), 1);
        line.setPadding(Art.dp(c, 16), Art.dp(c, 4), Art.dp(c, 16), 0);
        box.addView(line);
        View gap = new View(c);
        box.addView(gap, new LinearLayout.LayoutParams(1, Art.dp(c, 12)));
        if (content != null) box.addView(content, new LinearLayout.LayoutParams(-1, -2));
        Section s = new Section(box, head, line);
        s.set(title, sub);
        return s;
    }

    /** A heading as the launcher's rows have them: Outfit, white, a little letter-spaced. */
    static TextView heading(Context c, String title) {
        TextView head = Rows.text(c, 19, Art.TEXT, Type.outfit(c, 600), 1);
        head.setText(title);
        head.setLetterSpacing(0.01f);
        head.setPadding(Art.dp(c, 16), Art.dp(c, 26), Art.dp(c, 16), 0);
        return head;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    // ---- what it is given -----------------------------------------------------------------------------------------

    /** Continue watching's cards (read from this phone at once), and the first one's backdrop and logo when read. */
    void resume(List<Resume.Card> cards, Pick art) {
        resumeCards = cards == null ? new ArrayList<>() : cards;
        resumeArt = art;
        resumes.set(resumeCards);
        resumeSection.box.setVisibility(resumeCards.isEmpty() ? View.GONE : View.VISIBLE);
        heroPages();
    }

    /** The films and TV picks (null: still coming, shimmer). */
    void feed(Feed.Result r) {
        feed = r;
        feedLoaded = r != null;
        if (r == null) {
            filmRow.set(null);
            showRow.set(null);
            return;
        }
        boolean noPlaylist = Feed.NO_PLAYLIST.equals(r.note);
        note.box.setVisibility(noPlaylist ? View.VISIBLE : View.GONE);
        if (noPlaylist) noteWords.setText("Homefield plays films and TV only from your own playlists. Add yours in Settings, "
                + "Live TV, and the films and shows picked for you come here, with what you're watching.");
        row(films, filmRow, noPlaylist ? null : r.films);
        row(shows, showRow, noPlaylist ? null : r.shows);
        row(fresh, freshRow, noPlaylist ? null : r.fresh);
        heroPages();
    }

    private static void row(Section s, Rows.Posters adapter, Feed.Row row) {
        boolean has = row != null && !row.picks.isEmpty();
        s.box.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) return;
        s.set(row.title, row.sub);
        adapter.set(row.picks);
    }

    /** The headlines (null: still coming, shimmer). */
    void news(Headlines.Read r) {
        news = r;
        newsLoaded = r != null;
        if (r == null) {
            newsRow.set(null);
            sportRow.set(null);
            return;
        }
        List<Story> n = r.feeds.size() > 0 ? r.feeds.get(0) : new ArrayList<>();
        List<Story> s = r.feeds.size() > 1 ? r.feeds.get(1) : new ArrayList<>();
        newsRow.set(n);
        sportRow.set(s);
        newsSection.box.setVisibility(n.isEmpty() ? View.GONE : View.VISIBLE);
        sportSection.box.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
        heroPages();
    }

    void visible(boolean on) {
        hero.visible(on);
    }

    private void scrolled(int y) {
        hero.parallax(y);
        backdrop.setTranslationY(-y * 0.5f);
    }

    // ---- the hero's pages -----------------------------------------------------------------------------------------

    /**
     * What the hero shows: the first title in Continue watching, then the films and series picked (by turns); with
     * fewer than two, BBC News's and BBC Sport's top stories; with nothing at all once everything has come, a welcome.
     */
    private void heroPages() {
        List<Hero.Page> pages = new ArrayList<>();
        if (!resumeCards.isEmpty()) pages.add(resumePage(resumeCards.get(0), resumeArt));
        if (feed != null) for (Pick p : feed.hero) if (pages.size() < 5) pages.add(pickPage(p));
        if (pages.size() < 2 && news != null) {
            for (int i = 0; i < news.feeds.size() && i < 2; i++) {
                List<Story> list = news.feeds.get(i);
                for (Story s : list) {
                    if (s.picture.isEmpty()) continue;
                    pages.add(storyPage(s, i == 0 ? "BBC News" : "BBC Sport", i == 0 ? Art.NEWS : Art.SPORT));
                    break;
                }
            }
        }
        if (pages.isEmpty() && feedLoaded && newsLoaded) pages.add(welcome());
        StringBuilder sig = new StringBuilder();
        for (Hero.Page p : pages) sig.append(p.title).append('|').append(p.image).append('|').append(p.logo).append('|')
                .append(p.progress).append(';');
        if (sig.toString().equals(heroSig)) return;
        heroSig = sig.toString();
        hero.set(pages);
    }

    private Hero.Page resumePage(Resume.Card k, Pick art) {
        Hero.Page p = new Hero.Page();
        p.accent = k.tv ? Art.SHOW : Art.FILM;
        p.kicker = "Continue watching" + (k.badge != null && !k.badge.isEmpty() && !"ON TV".equals(k.badge) ? " · " + k.badge : "");
        p.title = k.title;
        p.image = art != null && art.backdrop != null ? art.backdrop : k.poster;
        p.logo = art == null ? null : art.logo;
        p.facts = art == null ? "" : art.facts();
        p.cert = art == null ? "" : art.cert;
        p.progress = Math.max(0, k.progress);
        p.left = k.line;
        p.play = k.badge != null && k.badge.startsWith("NEXT") ? "Play next" : "Resume";
        p.onPlay = () -> host.resume(k);
        if (k.tmdb > 0) {
            Pick open = art != null ? art : new Pick();
            open.id = k.tmdb;
            open.tv = k.tv;
            if (open.title.isEmpty()) open.title = k.title;
            if (open.poster == null) open.poster = k.poster;
            p.more = "Details";
            p.onMore = () -> host.open(open);
        }
        return p;
    }

    private Hero.Page pickPage(Pick k) {
        Hero.Page p = new Hero.Page();
        p.accent = k.tv ? Art.SHOW : Art.FILM;
        p.kicker = (k.tv ? "TV show" : "Film") + (k.why.isEmpty() ? "" : " · " + k.why);
        p.title = k.title;
        p.image = k.backdrop != null ? k.backdrop : k.poster;
        p.logo = k.logo;
        p.facts = k.facts();
        p.cert = k.cert;
        p.overview = k.overview;
        p.onPlay = () -> host.play(k);
        p.onMore = () -> host.open(k);
        return p;
    }

    private Hero.Page storyPage(Story s, String source, int accent) {
        Hero.Page p = new Hero.Page();
        p.accent = accent;
        p.kicker = "Top story · " + source;
        p.title = s.title;
        p.image = s.picture;
        p.facts = Headlines.ago(s.at, System.currentTimeMillis());
        p.more = "Read the story";
        p.onMore = () -> host.story(s);
        return p;
    }

    private Hero.Page welcome() {
        Hero.Page p = new Hero.Page();
        p.accent = Art.RESUME;
        p.kicker = "Homefield";
        p.title = "Films, TV and sport, all in one place";
        p.overview = "Add your playlist in Settings for films and TV picked for you, and pair a TV for its posters.";
        p.play = "Open Settings";
        p.onPlay = host::settings;
        return p;
    }

    /** The page up in the hero: its picture, read tiny and stretched, becomes the soft backdrop behind Home. */
    private void pageUp(Hero.Page p) {
        String url = p.image;
        if (url == null || url.equals(backdropFor)) return;
        backdropFor = url;
        try {
            Glide.with(backdrop).load(url).override(48, 27).centerCrop().placeholder(backdrop.getDrawable())
                    .transition(DrawableTransitionOptions.withCrossFade(600)).into(backdrop);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
    }
}
