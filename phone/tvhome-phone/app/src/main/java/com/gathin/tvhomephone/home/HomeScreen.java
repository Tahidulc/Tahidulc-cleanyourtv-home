package com.gathin.tvhomephone.home;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.gathin.tvhomephone.glass.AmbientView;
import com.gathin.tvhomephone.glass.FanCard;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.Icons;
import com.gathin.tvhomephone.glass.LensBar;
import com.gathin.tvhomephone.live.BreakingNews.Story;
import com.gathin.tvhomephone.live.FanFeed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Home's page, in the glass of the family's reference picture: the page's picture glowing softly behind everything
 * (the first title in Continue watching, else the first picked for you), the family's mark with the greeting, and the
 * date with the weather under it; the pill (search, then Home, Films, Shows and Live); a row of frosted cards, one for
 * each section below that has something, with three of its pictures fanned in it (a tap slides the page there); then
 * Continue watching's landscape cards, Favourites (My list), the scores and the clubs' games, films and TV from the
 * playlists, BBC News's and BBC Sport's headlines (each story marked as the BBC's section marks it), the fans' posts
 * about the starred clubs dropping in as they come, what's new in the playlists, the alerts, Recently watched and the
 * TVs' cards. It only lays things out and shows what it is given; HomeFragment reads and gives (and a test can give
 * it made-up titles and draw it).
 */
final class HomeScreen {
    /** What the page's taps do. */
    interface Host {
        /** A title's page. */
        void open(Pick p);

        /** Continue watching's card: on from where it was left (a title on a TV: its page). */
        void resume(Resume.Card k);

        /** Continue watching's card held: taken off. */
        void forget(Resume.Card k);

        void story(Story s);

        void settings();

        /** Watch, on one of its sections (DISCOVER, MOVIES or TV_SHOWS), with its search open when asked. */
        void watch(int section, boolean search);

        /** The Live TV tab. */
        void live();

        /** A fan's post: opened on its own site. */
        default void post(FanFeed.Item p) {
        }
    }

    /** The pill's words; Home is chosen. */
    static final int HOME = 1, FILMS = 2, SHOWS = 3, LIVE = 4;
    /** Watch's sections, as it is asked to open on them. */
    static final int DISCOVER = 0, MOVIES = 1, TV_SHOWS = 2;
    /** How long after another tab opens the pill's lens goes back to Home: that page has slid in by then. */
    private static final long BACK_HOME_MS = 450;

    final Context c;
    final Host host;
    final FrameLayout root;
    final AmbientView ambient;
    final SwipeRefreshLayout swipe;
    final ScrollView scroll;
    final LinearLayout col;
    final Glass.Header header;
    final LensBar pill;
    /** The frosted cards along the top, in their row. */
    final LinearLayout fans;
    /** Continue watching's cards. */
    final RecyclerView resumeRow;
    /** Home's own sports: the section, and the frosted box HomeFragment fills (the scores box, the clubs' games). */
    final LinearLayout sports, sportsBox;
    /** The latest alerts: the section, and the list HomeFragment fills with them. */
    final LinearLayout alerts, alertList;
    /** Recently watched, then the TVs' cards. */
    final LinearLayout recent, tvs;
    private final HorizontalScrollView fanScroll;
    private final Section resumeSection, favSection, films, shows, fresh, newsSection, sportSection, socialSection, note;
    /** The fans' posts about the starred clubs. */
    private final SocialCard social;
    private final Rows.Watching watching;
    private final Rows.Posters favRow, filmRow, showRow, freshRow;
    private final Rows.Stories newsRow, sportRow;
    private final Fan filmFan, showFan, freshFan, favFan, newsFan, sportFan;
    private final List<Fan> fanList = new ArrayList<>();
    private final Runnable backHome;

    private List<Resume.Card> resumeCards = new ArrayList<>();
    private List<Pick> mine = new ArrayList<>();
    private Feed.Result feed;
    private Headlines.Read news;
    /** The line under the date: the weather, once it has been read. */
    private String under = "Manchester";
    /** The status bar's height the refresh spinner was placed under. */
    private int spinnerFor = -1;

    HomeScreen(Context c, Host host) {
        this.c = c;
        this.host = host;
        boolean wide = c.getResources().getConfiguration().screenWidthDp >= 600;
        root = new FrameLayout(c);
        root.setBackgroundColor(Glass.GROUND);

        // The glow behind it all: the page's picture, soft and dim, fading into the warm dark ground.
        ambient = new AmbientView(c);
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));

        swipe = new SwipeRefreshLayout(c);
        swipe.setColorSchemeColors(Glass.TEXT);
        swipe.setProgressBackgroundColorSchemeColor(0xFF26272E);
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        col = column();
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        swipe.addView(scroll);
        root.addView(swipe, new FrameLayout.LayoutParams(-1, -1));
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> ambient.setShift(y));
        // Drawn under the status bar and the floating tab bar: room left for both, the spinner below the status bar
        // (placed again only when the status bar's height changes: placing it stops a refresh that is showing).
        Glass.fit(root, (top, bottom) -> {
            scroll.setPadding(0, top + Glass.dp(c, 12), 0, bottom);
            if (top == spinnerFor) return;
            spinnerFor = top;
            swipe.setProgressViewOffset(false, top - Glass.dp(c, 40), top + Glass.dp(c, 56));
        });

        // The family's mark, the greeting and the name; the date, and the weather under it.
        header = Glass.header(c);
        int side = Glass.dp(c, 20);
        header.view.setPadding(side, 0, side, 0);
        col.addView(header.view, new LinearLayout.LayoutParams(-1, -2));
        header.now(under);

        // The pill: search, then Home (chosen here), Films, Shows and Live, each opening its own place.
        pill = new LensBar(c, LensBar.PILL);
        pill.action(Icons.SEARCH, "Search", () -> host.watch(DISCOVER, true))
                .segment(HOME, "Home").segment(FILMS, "Films").segment(SHOWS, "Shows").segment(LIVE, "Live");
        pill.select(HOME, false);
        pill.setSources(ambient);
        backHome = () -> pill.select(HOME, false);
        pill.setOnPick(id -> {
            if (id == FILMS) host.watch(MOVIES, false);
            else if (id == SHOWS) host.watch(TV_SHOWS, false);
            else if (id == LIVE) host.live();
            else return;
            // Its lens back on Home once the other tab is in, so it is right when the family comes back.
            pill.removeCallbacks(backHome);
            pill.postDelayed(backHome, BACK_HOME_MS);
        });
        // A tablet: the pill no wider than it needs, in the middle.
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(wide ? Glass.dp(c, 520) : -1, -2);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.setMargins(Glass.dp(c, 16), Glass.dp(c, 18), Glass.dp(c, 16), 0);
        col.addView(pill, pp);

        // The frosted cards, one for each section below that has something (filled in as the sections are).
        fanScroll = new HorizontalScrollView(c);
        fanScroll.setHorizontalScrollBarEnabled(false);
        fanScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        fans = new LinearLayout(c);
        fans.setOrientation(LinearLayout.HORIZONTAL);
        fans.setPadding(Glass.dp(c, 16), 0, Glass.dp(c, 16 - Rows.GAP), 0);
        fanScroll.addView(fans, new FrameLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = Glass.dp(c, 18);
        col.addView(fanScroll, fp);
        fanScroll.setVisibility(View.GONE);

        watching = new Rows.Watching(host::resume, host::forget);
        resumeRow = Rows.row(c, watching);
        resumeSection = section("Continue watching", v -> host.watch(DISCOVER, false), resumeRow);
        resumeSection.box.setVisibility(View.GONE);
        col.addView(resumeSection.box);

        favRow = new Rows.Posters(c, host::open);
        favSection = section("Favourites", v -> host.watch(DISCOVER, false), Rows.row(c, favRow));
        favSection.box.setVisibility(View.GONE);
        col.addView(favSection.box);

        sports = column();
        sports.addView(head("Live & today", null));
        sportsBox = column();
        sportsBox.setBackground(Glass.card(c, 20));
        int inside = Glass.dp(c, 12);
        sportsBox.setPadding(inside, inside, inside, inside);
        sports.addView(sportsBox, sides());
        sports.setVisibility(View.GONE);
        col.addView(sports);

        // No playlist yet: what Home needs, on a frosted card, with the way to Settings.
        note = section("Films and TV", null, null);
        LinearLayout noteCard = column();
        noteCard.setBackground(Glass.card(c, 20));
        int pad = Glass.dp(c, 18);
        noteCard.setPadding(pad, pad, pad, pad);
        TextView noteWords = Glass.text(c, "Homefield plays films and TV only from your own playlists. Add yours in "
                + "Settings, Live TV, and the films and shows picked for you come here, with what you're watching.",
                14, Glass.SOFT, Glass.body(c));
        noteWords.setLineSpacing(Glass.dp(c, 3), 1f);
        noteCard.addView(noteWords);
        TextView noteButton = Glass.text(c, "Open Settings", 15, 0xFF101114, Glass.heading(c, 600));
        noteButton.setGravity(Gravity.CENTER);
        noteButton.setPadding(Glass.dp(c, 20), 0, Glass.dp(c, 20), 0);
        noteButton.setBackground(Art.round(0xFFFFFFFF, Glass.dp(c, 21)));
        noteButton.setOnClickListener(v -> host.settings());
        LinearLayout.LayoutParams nb = new LinearLayout.LayoutParams(-2, Glass.dp(c, 42));
        nb.topMargin = Glass.dp(c, 14);
        noteCard.addView(noteButton, nb);
        note.box.addView(noteCard, sides());
        note.box.setVisibility(View.GONE);
        col.addView(note.box);

        filmRow = new Rows.Posters(c, host::open);
        films = section("Films for you", v -> host.watch(MOVIES, false), Rows.row(c, filmRow));
        col.addView(films.box);

        newsRow = new Rows.Stories(c, "BBC News", Art.NEWS, host::story);
        newsSection = section("Top stories", null, Rows.row(c, newsRow));
        col.addView(newsSection.box);

        showRow = new Rows.Posters(c, host::open);
        shows = section("TV for you", v -> host.watch(TV_SHOWS, false), Rows.row(c, showRow));
        col.addView(shows.box);

        sportRow = new Rows.Stories(c, "BBC Sport", Art.SPORT, host::story);
        sportSection = section("Sport headlines", null, Rows.row(c, sportRow));
        col.addView(sportSection.box);

        // What the starred clubs' fans are saying now: hidden until there are posts (or when it is off in Settings).
        social = new SocialCard(c, p -> host.post(p));
        socialSection = section("Fans right now", null, null);
        socialSection.box.addView(social.view, sides());
        socialSection.box.setVisibility(View.GONE);
        col.addView(socialSection.box);

        freshRow = new Rows.Posters(c, host::open);
        fresh = section("New in your playlist", v -> host.watch(DISCOVER, false), Rows.row(c, freshRow));
        fresh.box.setVisibility(View.GONE);
        col.addView(fresh.box);

        alerts = column();
        alerts.addView(head("Latest alerts", null));
        alertList = column();
        alerts.addView(alertList, sides());
        alerts.setVisibility(View.GONE);
        col.addView(alerts);
        recent = column();
        col.addView(recent);
        tvs = column();
        col.addView(tvs);

        // Each card's word is short, as the reference's genres are: a longer one would run under its fanned posters.
        filmFan = fan("Films", "Films for you", films);
        showFan = fan("TV", "TV for you", shows);
        freshFan = fan("New in", "New in your playlist", fresh);
        favFan = fan("My list", "Favourites", favSection);
        newsFan = fan("News", "Top stories", newsSection);
        sportFan = fan("Sport", "Sport headlines", sportSection);
    }

    View view() {
        return root;
    }

    // ---- sections -------------------------------------------------------------------------------------------------

    /** A section: its heading (with a chevron when it leads somewhere), then what it holds. */
    static final class Section {
        final LinearLayout box;
        final TextView title;

        Section(LinearLayout box, TextView title) {
            this.box = box;
            this.title = title;
        }
    }

    /**
     * A section: its heading in the reference's weight, with a round chevron button at the right edge when it leads
     * somewhere (the heading leads there too), then what it holds.
     */
    private Section section(String title, View.OnClickListener more, View content) {
        LinearLayout box = column();
        LinearLayout head = head(title, more);
        box.addView(head, new LinearLayout.LayoutParams(-1, -2));
        if (content != null) box.addView(content, new LinearLayout.LayoutParams(-1, -2));
        return new Section(box, (TextView) head.getChildAt(0));
    }

    /** A section's heading row: its words (first), then the round chevron button when it leads somewhere. */
    private LinearLayout head(String title, View.OnClickListener more) {
        return Glass.sectionRow(c, title, more);
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** Across the page, 16dp in from each side. */
    private LinearLayout.LayoutParams sides() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Glass.dp(c, 16);
        return lp;
    }

    // ---- the cards along the top ----------------------------------------------------------------------------------

    /** One card along the top, for one section: its word, the section's name (read out), and the pictures it shows. */
    private static final class Fan {
        final FanCard card;
        final String word, section;
        String shown;

        Fan(FanCard card, String word, String section) {
            this.card = card;
            this.word = word;
            this.section = section;
        }
    }

    private Fan fan(String word, String section, Section to) {
        FanCard f = new Pressed(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Glass.dp(c, 228), Glass.dp(c, 100));
        lp.rightMargin = Glass.dp(c, Rows.GAP);
        fans.addView(f, lp);
        f.setVisibility(View.GONE);
        f.setOnClickListener(v -> scrollTo(to.box));
        Fan fan = new Fan(f, word, section);
        fanList.add(fan);
        return fan;
    }

    /** A card along the top whose glass brightens while it is pressed (not while a finger only scrolls past it). */
    private static final class Pressed extends FanCard {
        Pressed(Context c) {
            super(c);
        }

        @Override public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            setPicked(pressed);
        }
    }

    /** Slides the page so the section's heading sits just under the status bar. */
    private void scrollTo(View box) {
        if (box.getVisibility() != View.VISIBLE) return;
        scroll.smoothScrollTo(0, Math.max(0, box.getTop()));
    }

    /** The cards along the top: one for each section below that has something, with up to three of its pictures. */
    private void fans() {
        boolean playlists = feed != null && !Feed.NO_PLAYLIST.equals(feed.note);
        Feed.Row f = playlists ? feed.films : null, s = playlists ? feed.shows : null, n = playlists ? feed.fresh : null;
        fan(filmFan, has(f), posters(f == null ? null : f.picks));
        fan(showFan, has(s), posters(s == null ? null : s.picks));
        fan(freshFan, has(n), posters(n == null ? null : n.picks));
        fan(favFan, !mine.isEmpty(), posters(mine));
        List<Story> top = stories(0), sport = stories(1);
        fan(newsFan, !top.isEmpty(), pictures(top));
        fan(sportFan, !sport.isEmpty(), pictures(sport));
        boolean any = false;
        for (Fan x : fanList) any |= x.card.getVisibility() == View.VISIBLE;
        fanScroll.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    private void fan(Fan f, boolean has, List<String> urls) {
        f.card.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) return;
        // The same pictures again: left as they are.
        String sig = String.valueOf(urls);
        if (sig.equals(f.shown)) return;
        f.shown = sig;
        f.card.set(f.word, urls);
        f.card.setContentDescription(f.section);
    }

    private static boolean has(Feed.Row r) {
        return r != null && !r.picks.isEmpty();
    }

    private List<Story> stories(int feedAt) {
        return news == null || news.feeds.size() <= feedAt ? new ArrayList<>() : news.feeds.get(feedAt);
    }

    /** Pure: the first three posters among a row's titles (those that have one). */
    static List<String> posters(List<Pick> picks) {
        List<String> out = new ArrayList<>();
        for (int i = 0; picks != null && i < picks.size() && out.size() < 3; i++) {
            String u = picks.get(i).poster;
            if (u != null && !u.isEmpty()) out.add(u);
        }
        return out;
    }

    /** Pure: the first three pictures among a feed's stories (those that have one). */
    static List<String> pictures(List<Story> stories) {
        List<String> out = new ArrayList<>();
        for (int i = 0; stories != null && i < stories.size() && out.size() < 3; i++) {
            String u = stories.get(i).picture;
            if (u != null && !u.isEmpty()) out.add(u);
        }
        return out;
    }

    // ---- what it is given -----------------------------------------------------------------------------------------

    /** Continue watching's cards (read from this phone at once), with the backdrops and logos read so far (by key). */
    void resume(List<Resume.Card> cards, Map<String, Pick> art) {
        resumeCards = cards == null ? new ArrayList<>() : new ArrayList<>(cards);
        watching.set(resumeCards, art);
        resumeSection.box.setVisibility(resumeCards.isEmpty() ? View.GONE : View.VISIBLE);
        glow();
    }

    /** One Continue watching title's backdrop and logo, read after its card showed. */
    void art(String key, Pick p) {
        if (key == null || p == null) return;
        watching.art(key, p);
        if (!resumeCards.isEmpty() && key.equals(resumeCards.get(0).key())) glow();
    }

    /** The films and TV picks (null: still coming, shimmer). */
    void feed(Feed.Result r) {
        feed = r;
        if (r == null) {
            filmRow.set(null);
            showRow.set(null);
            fans();
            return;
        }
        boolean noPlaylist = Feed.NO_PLAYLIST.equals(r.note);
        note.box.setVisibility(noPlaylist ? View.VISIBLE : View.GONE);
        row(films, filmRow, noPlaylist ? null : r.films);
        row(shows, showRow, noPlaylist ? null : r.shows);
        row(fresh, freshRow, noPlaylist ? null : r.fresh);
        fans();
        glow();
    }

    private static void row(Section s, Rows.Posters adapter, Feed.Row row) {
        boolean has = row != null && !row.picks.isEmpty();
        s.box.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) adapter.set(row.picks);
    }

    /** Favourites: My list's titles, newest first (none: the row hidden). */
    void favourites(List<Pick> list) {
        mine = list == null ? new ArrayList<>() : new ArrayList<>(list);
        if (!mine.isEmpty()) favRow.set(mine);
        favSection.box.setVisibility(mine.isEmpty() ? View.GONE : View.VISIBLE);
        fans();
    }

    /** The headlines (null: still coming, shimmer). */
    void news(Headlines.Read r) {
        news = r;
        if (r == null) {
            newsRow.set(null);
            sportRow.set(null);
            fans();
            return;
        }
        List<Story> n = stories(0), s = stories(1);
        newsRow.set(n);
        sportRow.set(s);
        newsSection.box.setVisibility(n.isEmpty() ? View.GONE : View.VISIBLE);
        sportSection.box.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
        fans();
    }

    /** The fans' posts, newest first, and the clubs they are about (none: the section hidden). */
    void social(List<FanFeed.Item> items, List<String> clubs) {
        boolean has = items != null && !items.isEmpty();
        social.set(has ? items : null, clubs);
        socialSection.box.setVisibility(has ? View.VISIBLE : View.GONE);
    }

    /** The line under the date: the weather ("12° · Manchester"). */
    void weather(String line) {
        under = line == null || line.isEmpty() ? "Manchester" : line;
        header.now(under);
    }

    /** The greeting and the date as they are now, and how long ago each fan posted (the clock's tick). */
    void stamp() {
        header.now(under);
        social.tick(System.currentTimeMillis());
    }

    /** Home on the screen again: the pill's lens on Home. */
    void visible(boolean on) {
        if (!on) return;
        pill.removeCallbacks(backHome);
        pill.select(HOME, false);
    }

    // ---- the glow behind the page ---------------------------------------------------------------------------------

    /** The glow behind the page follows its picture: see glowFor. */
    private void glow() {
        Resume.Card first = resumeCards.isEmpty() ? null : resumeCards.get(0);
        String url = glowFor(first, watching.art(first), feed);
        if (url != null) ambient.setImage(url);
    }

    /**
     * Pure: the picture the page glows with: the first Continue watching title's backdrop, else the first pick's;
     * failing those, that title's poster, else the first pick's; null for none (the glass's own warm glow).
     */
    static String glowFor(Resume.Card first, Pick firstArt, Feed.Result r) {
        if (firstArt != null && firstArt.backdrop != null) return firstArt.backdrop;
        List<Pick> picks = picks(r);
        for (Pick p : picks) if (p.backdrop != null) return p.backdrop;
        if (first != null && first.poster != null && !first.poster.isEmpty()) return first.poster;
        for (Pick p : picks) if (p.poster != null) return p.poster;
        return null;
    }

    /** The picks, best first: the few chosen to lead (each with a backdrop), then films, TV and what's new. */
    private static List<Pick> picks(Feed.Result r) {
        List<Pick> out = new ArrayList<>();
        if (r == null || Feed.NO_PLAYLIST.equals(r.note)) return out;
        out.addAll(r.hero);
        for (Feed.Row row : new Feed.Row[]{r.films, r.shows, r.fresh}) if (row != null) out.addAll(row.picks);
        return out;
    }
}
