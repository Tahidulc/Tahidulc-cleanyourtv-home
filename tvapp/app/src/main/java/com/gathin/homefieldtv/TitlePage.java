package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Tmdb;
import com.gathin.homefieldtv.engine.Videos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A film's or a series' page below its first screen, over the cover it starts with (CoverPage, the family's cover B:
 * the poster, logo, facts and buttons, story, cast, Resume and From the start, a film's sources), made for the remote
 * (design px). The cover lies under this view and goes up with it as the page rises; this one draws what is further
 * down, in order:
 *
 * <pre>
 *   a series     its seasons and episodes as the family's screen recording of a series page shows them ("when I'm in
 *                the series section on a programme and scrolling through seasons and channels, make it look like
 *                this"), a screen down (SECTION): dark, its ground (the page in the title's own colour, its season's
 *                cross-fading as the season changes, its picture faded right down) coming over the cover as the page
 *                rises to it; the show's title art (its TMDB logo, or its name in big lettering) in the middle at the
 *                top; the season tabs ("Season 1  Season 2  Season 3", the season in view bold and white, a white pill
 *                behind it while the remote is on the tabs); ONE row of every season's episodes, each a 16:9 still
 *                with rounded corners (a thin white line on it as far as it was watched, UP NEXT on the one to carry
 *                on with) and under it "S2 E1" small and quiet, its name, two lines of its story and "58m  16 Jun
 *                2024"; the episode the remote is on lifts (Card07) and its words sit on a frosted rounded panel; as
 *                the row goes on into the next season's episodes the tab follows by itself, and Left or Right on the
 *                tabs jumps the row to that season's first episode; then a Videos row: its trailers, teasers and
 *                extras on YouTube (TMDB's) as 16:9 stills with their kind on a small badge (OK: in the TV's YouTube
 *                app, as the trailer button)
 *   More like    at the foot of it all, a screen under the cover for a film, under the series' section for a series:
 *   this         posters that lift and tilt with their names and years; the page rises a whole screen to it, so nothing
 *                above it is left under the back line
 * </pre>
 *
 * The remote goes down the page part by part: the cover's rows and buttons (the cover takes the keys while the remote is
 * on it), then the seasons, episodes and videos, then More like this; Up comes back the same way, and Back from further
 * down comes back up to the cover. Every move glides; only what is in sight is drawn; nothing is made while drawing.
 */
final class TitlePage extends View {
    interface Host {
        /** An episode chosen. */
        void play(Catalog.Episode ep);

        /** One of a series' videos (its YouTube key), from the Videos row. */
        void video(String key);

        /** A poster in More like this. */
        void open(Catalog.Title t);

        /** Up from the top of the page: on to the tab bar. */
        void up();

        /** The remote moved on the page (part() says where it is now). */
        void moved();

        /** How far an episode has been watched, -1 when it hasn't been started. */
        float progress(Catalog.Episode ep);
    }

    /** The parts the remote moves through, top to bottom (VIDEOS: a series' trailers and extras). */
    static final int COVER = 0, SEASONS = 1, EPISODES = 2, VIDEOS = 3, MORE = 4;

    /** Places (design px): the page's left margin, the picture the series' ground has, More like this's posters. */
    static final float LEFT = 90f, ART_X = 420f, ART_W = 1500f, ART_H = 844f, MORE_W = 196f, MORE_H = 294f, MORE_STEP = 222f;
    /**
     * A series' section (design px from its top, which is SECTION down the page, under the cover: the page rises that
     * far to it): the title art's foot, box and the room it has; the tabs' middle, height and the room either side of
     * each tab's word; the episodes' stills (16:9), their step along the row, the gap to their words, the words' room
     * inside the panel and the panel's height; the Videos row's heading and its stills; its height (LOWER).
     */
    static final float SECTION = 1080f, ART_FOOT = 214f, ART_BOX_W = 560f, ART_BOX_H = 120f, TABS_Y = 262f, TAB_H = 50f, TAB_PAD = 24f,
            EP_TOP = 312f, EP_W = 400f, EP_H = 225f, EP_STEP = 432f, WORDS_GAP = 10f, PAD = 16f, PANEL_H = 160f, VIDEOS_TOP = 750f,
            VID_W = 320f, VID_H = 180f, VID_STEP = 344f, LOWER = 1080f;
    /** The cover's height; where More like this's heading comes to on the screen, a screen under what is above it. */
    static final float COVER_H = 1080f, MORE_AT = 300f;
    /** How much the remote's episode's words (and their panel) grow as it lifts. */
    static final float WORDS_GROW = 0.05f;
    /** How long the page, a row and the season pill take to glide; the colour's cross-fade. */
    static final long PAGE_MS = 520, ROW_MS = 450, PILL_MS = 450;

    private final Host host;
    private final CoverPage cover;
    private Catalog.Title title;
    private Catalog.Details details;
    private Tmdb.Extras extras;
    private final List<Catalog.Episode> episodes = new ArrayList<>();
    private final List<Catalog.Title> more = new ArrayList<>();
    /** A series' videos (its Videos row), from TMDB's extras. */
    private final List<Videos.Video> videos = new ArrayList<>();
    /** Each season's number and its first episode's place in the row. */
    private final List<int[]> seasons = new ArrayList<>();
    private String resumeId = "", backdrop = "";
    private int resumeAt = -1;

    private int part = COVER, epAt, moreAt, seasonAt, vidAt;
    /** Whether the page has the remote (false while it is up on the tab bar: nothing lifted). */
    private boolean active = true;
    private final Glide page = new Glide(PAGE_MS), epX = new Glide(ROW_MS), moreX = new Glide(ROW_MS), vidX = new Glide(ROW_MS),
            pillL = new Glide(PILL_MS), pillR = new Glide(PILL_MS);
    private final Lift07 lift = new Lift07();
    private final Card07 epCards = new Card07(Look07.R_CARD), moreCards = new Card07(Look07.R_POSTER), vidCards = new Card07(Look07.R_CARD);
    private final Card07.Marks marks = new Card07.Marks();
    /** The series' ground's colour: its ground, and the fades of it over the picture (made when the colour is given). */
    private final Cine.Wash wash = new Cine.Wash();
    private final Paint glowNow = new Paint(Paint.DITHER_FLAG), glowWas = new Paint(Paint.DITHER_FLAG);
    private int glowColour, glowWasColour;
    private int showTint;
    private int[] seasonTint = new int[0];

    // ---- words, laid out in set(), never while drawing --------------------------------------------------------------
    private final Hero07.Mark sectionMark = new Hero07.Mark(60f);
    private final TextPaint tabP, tabQuiet, tabInk, codeP, epNameP, epStoryP, epFactsP, badgeP, headP, moreName, upNextP;
    private final Paint ground = new Paint(), scrim = new Paint(), art = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG),
            darkLeft = new Paint(Paint.DITHER_FLAG), darkFoot = new Paint(Paint.DITHER_FLAG), white = Ui.fill(Look07.L1),
            fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String[] seasonWords = new String[0];
    private float[] seasonX = new float[0], seasonW = new float[0];
    /** Each episode's words: "S2 E1", its name (cut to the panel), "58m  16 Jun 2024"; its story laid out as it comes near. */
    private String[] epCodes = new String[0], epNames = new String[0], epFacts = new String[0], vidNames = new String[0];
    /** How many episodes' stories are kept laid out (those in sight and either side). */
    static final int STORIES = 16;
    private final StaticLayout[] epStories = new StaticLayout[STORIES];
    private final int[] epStoryFor = new int[STORIES];
    private String[] moreNames = new String[0], moreYears = new String[0];
    private final RectF r = new RectF();
    private final Matrix m = new Matrix();
    private final Runnable redraw = this::invalidate, colours = this::colours;

    TitlePage(Context c, Host host, CoverPage cover) {
        super(c);
        this.host = host;
        this.cover = cover;
        Cine.init(c);
        tabP = Type.paint(25f, Type.BOLD, Look07.L1);
        tabQuiet = Type.paint(25f, Type.SEMIBOLD, Look07.L3);
        tabInk = Type.paint(25f, Type.BOLD, Look07.INK);
        codeP = Type.paint(17f, Type.MEDIUM, Look07.L3);
        epNameP = Type.paint(22f, Type.SEMIBOLD, Look07.L1);
        epStoryP = Type.paint(18f, Type.REGULAR, Look07.L3);
        epFactsP = Type.paint(17f, Type.MEDIUM, Look07.L3);
        badgeP = Type.paint(14f, Type.EXTRABOLD, Look07.L1);
        badgeP.setLetterSpacing(0.06f);
        headP = Type.paint(Type.H3, Type.BOLD, Look07.L1);
        moreName = Type.paint(23f, Type.SEMIBOLD, Look07.L2);
        upNextP = Type.paint(14f, Type.EXTRABOLD, Look07.INK);
        upNextP.setLetterSpacing(0.06f);
        ground.setColor(Look07.GROUND);
        scrim.setColor(0xFF000000);
        java.util.Arrays.fill(epStoryFor, -1);
        Pills.prepare();
    }

    // ---- what it shows ----------------------------------------------------------------------------------------------

    /** The page for a title: its details (null while they come), More like this, the episode to resume. */
    void set(Catalog.Title t, Catalog.Details d, List<Catalog.Title> like, String resume) {
        boolean fresh = title != t;
        title = t;
        details = d;
        resumeId = resume == null ? "" : resume;
        backdrop = d != null && !d.backdrop.isEmpty() ? d.backdrop : !t.backdrop.isEmpty() ? t.backdrop : t.poster;

        more.clear();
        more.addAll(like);
        moreNames = new String[more.size()];
        moreYears = new String[more.size()];
        for (int i = 0; i < more.size(); i++) {
            moreNames[i] = Ui.fit(more.get(i).name, moreName, Ui.f(MORE_W - 8f));
            moreYears[i] = more.get(i).year;
        }
        moreAt = Math.min(moreAt, Math.max(0, more.size() - 1));

        // The episodes, every season's in one row.
        episodes.clear();
        seasons.clear();
        if (t.series && d != null) {
            for (Catalog.Season s : d.seasons) {
                if (s.episodes.isEmpty()) continue;
                seasons.add(new int[]{s.number, episodes.size()});
                episodes.addAll(s.episodes);
            }
        }
        int n = episodes.size();
        resumeAt = -1;
        epCodes = new String[n];
        epNames = new String[n];
        epFacts = new String[n];
        float room = Ui.f(EP_W - 2 * PAD);
        for (int i = 0; i < n; i++) {
            Catalog.Episode e = episodes.get(i);
            if (e.id.equals(resumeId)) resumeAt = i;
            epCodes[i] = "S" + e.season + " E" + e.number;
            epNames[i] = Ui.fit(e.title.isEmpty() ? "Episode " + e.number : e.title, epNameP, room);
            epFacts[i] = episodeLine(e.minutes, e.aired);
        }
        java.util.Arrays.fill(epStoryFor, -1);
        // Its videos (TMDB's, with its extras): trailers first, the family's language first.
        videos.clear();
        if (t.series && extras != null) videos.addAll(Videos.row(extras.videos, Tmdb.language(getContext())));
        vidNames = new String[videos.size()];
        for (int i = 0; i < videos.size(); i++) {
            Videos.Video v = videos.get(i);
            vidNames[i] = Ui.fit(v.name.isEmpty() ? v.badge() : v.name, moreName, Ui.f(VID_W - 8f));
        }
        vidAt = Math.min(vidAt, Math.max(0, videos.size() - 1));
        seasonWords = new String[seasons.size()];
        for (int i = 0; i < seasons.size(); i++) seasonWords[i] = "Season " + seasons.get(i)[0];
        seasonTint = new int[seasons.size()];
        if (fresh) {
            epAt = Math.max(0, resumeAt);
            seasonAt = seasonOf(epAt);
            part = COVER;
            page.jump(0f);
            moreX.jump(0f);
            vidX.jump(0f);
            moreAt = 0;
            vidAt = 0;
        }
        epAt = Math.min(epAt, Math.max(0, n - 1));
        epX.jump(epTarget());
        // More like this's posters read after what is on screen.
        int pw = moreCards.picW(Ui.f(MORE_W)), ph = moreCards.picH(Ui.f(MORE_H));
        for (int i = 0; i < Math.min(8, more.size()); i++) Pics.prefetch(more.get(i).poster, pw, ph, Pics.CROP);

        layoutWords();
        colours();
        if (fresh) jumpColour();
        if (part == COVER) cover.setActive(active);
        invalidate();
    }

    /** TMDB's extras for the title (its logo, seasons' posters and videos), when they come. */
    void extras(Tmdb.Extras x) {
        extras = x;
        if (title != null) set(title, details, new ArrayList<>(more), resumeId);
    }

    /** The words laid out once for what is shown: the section's title art, the season tabs. */
    private void layoutWords() {
        sectionMark.set(title.name, extras == null ? "" : extras.logo, Ui.f(ART_BOX_W), Ui.f(ART_BOX_H), Ui.f(1500f));
        // The season tabs: each TAB_PAD either side of its words (as wide as in bold), side by side.
        seasonX = new float[seasonWords.length];
        seasonW = new float[seasonWords.length];
        float x = 0;
        for (int i = 0; i < seasonWords.length; i++) {
            seasonX[i] = x;
            seasonW[i] = tabP.measureText(seasonWords[i]) + Ui.f(2 * TAB_PAD);
            x += seasonW[i];
        }
        if (seasonWords.length > 0) {
            pillL.jump(seasonX[Math.min(seasonAt, seasonX.length - 1)]);
            pillR.jump(seasonX[Math.min(seasonAt, seasonX.length - 1)] + seasonW[Math.min(seasonAt, seasonW.length - 1)]);
        }
    }

    // ---- colours ----------------------------------------------------------------------------------------------------

    /** The picture's colour and each season's (their posters'), as they become known; the page fades to the one wanted. */
    private void colours() {
        if (title == null || !lowerPage()) return;
        int c = Tint.of(backdrop, colours);
        if (c == 0 && !title.poster.equals(backdrop)) c = Tint.of(title.poster, colours);
        showTint = c;
        for (int i = 0; i < seasons.size(); i++) {
            String poster = extras == null ? null : extras.seasons.get(seasons.get(i)[0]);
            seasonTint[i] = poster == null ? 0 : Tint.of(poster, colours);
        }
        toColour(target(), false);
        invalidate();
    }

    /** Pure: a picture's colour as a page's: its hue and saturation (a little less at most), at value 0.32. */
    static int deep(int art) {
        if (art == 0) return 0xFF1A1F2C;
        float[] hsv = new float[3];
        Cine.hsv(art, hsv);
        return Cine.colour(hsv[0], Math.min(hsv[1], 0.86f), 0.32f);
    }

    /** The page's colour now: the season the remote is in (its poster's), else the title's picture's. */
    private int target() {
        int a = seasonAt >= 0 && seasonAt < seasonTint.length && seasonTint[seasonAt] != 0 ? seasonTint[seasonAt] : showTint;
        return deep(a);
    }

    private void jumpColour() {
        int c = target();
        wash.jump(c);
        glow(c, true);
    }

    /** To a colour: the fades' shaders and the glow's made now (never while drawing). */
    private void toColour(int c, boolean now) {
        wash.set(c);
        glow(c, now);
    }

    /** The glow of the colour at the page's foot left (radial, 1000 x 520 at 12 % across, clear by 70 %). */
    private void glow(int c, boolean now) {
        c |= 0xFF000000;
        if (c == glowColour && !now) return;
        glowWasColour = now ? 0 : glowColour;
        if (glowWasColour != 0) glowWas.setShader(glowNow.getShader());
        glowColour = c;
        RadialGradient g = new RadialGradient(Ui.f(1920f * 0.12f), Ui.f(1080f), Ui.f(1000f), new int[]{c, Palette.alpha(c, 0)},
                new float[]{0f, 0.7f}, Shader.TileMode.CLAMP);
        Matrix gm = new Matrix();
        gm.setScale(1f, 0.52f, Ui.f(1920f * 0.12f), Ui.f(1080f));
        g.setLocalMatrix(gm);
        glowNow.setShader(g);
    }

    // ---- places ------------------------------------------------------------------------------------------------------

    private Catalog.Episode episode(int i) {
        return i >= 0 && i < episodes.size() ? episodes.get(i) : null;
    }

    private int rowSize() {
        return episodes.size();
    }

    /** The season (its place among them) an episode in the row is in. */
    private int seasonOf(int i) {
        int s = 0;
        for (int k = 0; k < seasons.size(); k++) if (seasons.get(k)[1] <= i) s = k;
        return s;
    }

    private boolean hasPart(int p) {
        switch (p) {
            case COVER:
                return true;
            case SEASONS:
                return seasons.size() > 1;
            case EPISODES:
                return rowSize() > 0;
            case VIDEOS:
                return lowerPage() && !videos.isEmpty();
            default:
                return !more.isEmpty();
        }
    }

    /** Whether the page has a series' section under the cover (its seasons, episodes and videos). */
    private boolean lowerPage() {
        return title != null && title.series && rowSize() > 0;
    }

    /** Where what is under it all starts (design px): a screen under a series' section, else under the cover. */
    private float foot() {
        return lowerPage() ? SECTION + LOWER : COVER_H;
    }

    /** Where More like this's heading is on the page (design px). */
    private float moreTop() {
        return foot() + MORE_AT;
    }

    /**
     * How far the page has risen for the part the remote is on: none on the cover; a series' section to the top of the
     * screen; More like this a screen further, what is above it all gone (nothing under the back line).
     */
    private float pageTarget() {
        if (part == COVER) return 0f;
        if (part == MORE) return foot();
        return SECTION;
    }

    /** How far into a series' section the page has risen (0 on the cover, 1 there and further down). */
    private float into() {
        if (!lowerPage()) return 0f;
        return Math.max(0f, Math.min(1f, page.value() / SECTION));
    }

    /** A part of the page (More like this, from the cover's button): the remote there, the page risen to it. */
    void toPart(int p) {
        if (!hasPart(p)) return;
        part = p;
        settle();
    }

    /** Down from the cover's last row (or its buttons): on to what is under it. */
    void below() {
        move(1);
    }

    /** An episode's place in the row (design px from its start): one row, every season's after the one before. */
    private static float epPlace(int i) {
        return i * EP_STEP;
    }

    /**
     * The episode row's offset: the remote's episode kept second from the left, the one before it peeking in at the
     * screen's edge (the first from the start; the last ones with the row's end at the right margin).
     */
    private float epTarget() {
        float max = Math.max(0f, rowSize() * EP_STEP - (EP_STEP - EP_W) - (1920f - 2 * LEFT));
        return Math.max(0f, Math.min(max, epPlace(epAt) - EP_STEP));
    }

    private float vidTarget() {
        float max = Math.max(0f, videos.size() * VID_STEP - (VID_STEP - VID_W) - (1920f - 2 * LEFT));
        return Math.max(0f, Math.min(max, (vidAt - 2) * VID_STEP));
    }

    private float moreTarget() {
        float max = Math.max(0f, more.size() * MORE_STEP - (1920f - 2 * LEFT));
        return Math.max(0f, Math.min(max, (moreAt - 3) * MORE_STEP));
    }

    // ---- the remote -------------------------------------------------------------------------------------------------

    int part() {
        return part;
    }

    /** The page has the remote (true), or the tab bar above it has (nothing on the page lifted). */
    void setActive(boolean on) {
        active = on;
        cover.setActive(on && part == COVER);
        invalidate();
    }

    boolean key(int code) {
        if (part == COVER) {
            // The cover's rows and buttons; past its top to the tab bar, past its last row on down (its host).
            boolean took = cover.move(code);
            host.moved();
            return took;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return move(-1);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return move(1);
            case KeyEvent.KEYCODE_DPAD_LEFT:
                along(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                along(1);
                return true;
            default:
                return false;
        }
    }

    private boolean move(int dir) {
        int p = part + dir;
        while (p >= COVER && p <= MORE && !hasPart(p)) p += dir;
        if (p < COVER) {
            host.up();
            return true;
        }
        if (p > MORE) return true;
        if (part == SEASONS && p == EPISODES && seasonOf(epAt) != seasonAt && seasonAt < seasons.size()) {
            epAt = seasons.get(seasonAt)[1];
        }
        part = p;
        settle();
        return true;
    }

    private void along(int dir) {
        switch (part) {
            case SEASONS:
                if (seasons.isEmpty()) break;
                seasonAt = Math.max(0, Math.min(seasons.size() - 1, seasonAt + dir));
                // The row follows the pills: to the season's first episode.
                epAt = seasons.get(seasonAt)[1];
                break;
            case EPISODES:
                epAt = Math.max(0, Math.min(rowSize() - 1, epAt + dir));
                // The tabs follow the row.
                if (!seasons.isEmpty()) seasonAt = seasonOf(epAt);
                break;
            case VIDEOS:
                vidAt = Math.max(0, Math.min(videos.size() - 1, vidAt + dir));
                break;
            case MORE:
                moreAt = Math.max(0, Math.min(more.size() - 1, moreAt + dir));
                break;
            default:
                break;
        }
        settle();
    }

    /** Everything sent on its way to where the remote now is. */
    private void settle() {
        page.to(pageTarget());
        epX.to(epTarget());
        moreX.to(moreTarget());
        vidX.to(vidTarget());
        cover.setActive(active && part == COVER);
        if (seasonWords.length > 0) {
            // The white pill glides along the tabs while the remote is on them; it waits under the season in view.
            int s = Math.min(seasonAt, seasonX.length - 1);
            if (part == SEASONS) {
                pillL.to(seasonX[s]);
                pillR.to(seasonX[s] + seasonW[s]);
            } else {
                pillL.jump(seasonX[s]);
                pillR.jump(seasonX[s] + seasonW[s]);
            }
        }
        storiesNear();
        if (lowerPage()) toColour(target(), false);
        readAhead();
        host.moved();
        invalidate();
    }

    /** An episode's two lines of story, laid out once (STORIES kept: those in sight and either side). */
    private StaticLayout storyOf(int i) {
        int slot = Math.floorMod(i, STORIES);
        if (epStoryFor[slot] == i) return epStories[slot];
        Catalog.Episode e = episode(i);
        if (e == null) return null;
        epStoryFor[slot] = i;
        epStories[slot] = e.plot.isEmpty() ? null : Hero07.lines(e.plot, epStoryP, Ui.px(EP_W - 2 * PAD), 2, Ui.f(STORY_LINE));
        return epStories[slot];
    }

    /** A story's line height (design px). */
    static final float STORY_LINE = 18f * 1.36f;

    /** The stories of the episodes in sight where the row is and where it is going, laid out now (not while drawing). */
    private void storiesNear() {
        if (rowSize() == 0) return;
        float from = Math.min(epX.value(), epTarget()), to = Math.max(epX.value(), epTarget());
        int first = Math.max(0, (int) Math.floor((from - LEFT) / EP_STEP) - 1);
        int last = Math.min(rowSize() - 1, (int) Math.ceil((to + 1920f) / EP_STEP));
        // Never more than are kept at once: those around the remote's first.
        if (last - first >= STORIES) {
            first = Math.max(first, epAt - STORIES / 2 + 1);
            last = Math.min(last, first + STORIES - 1);
        }
        for (int i = first; i <= last; i++) storyOf(i);
    }

    /**
     * What a held key brings on next, read after what is on screen (Pics.prefetch): the episodes' stills or the posters
     * just past either edge of the row the remote is in, at the size they are drawn.
     */
    private void readAhead() {
        if (part == EPISODES || part == SEASONS) {
            int w = epCards.picW(Ui.f(EP_W)), h = epCards.picH(Ui.f(EP_H));
            for (int i = Math.max(0, epAt - 2), end = Math.min(rowSize() - 1, epAt + 5); i <= end; i++) {
                Catalog.Episode e = episode(i);
                if (e != null && !e.still.isEmpty()) Pics.prefetch(e.still, w, h, Pics.CROP);
            }
        } else if (part == VIDEOS) {
            int w = vidCards.picW(Ui.f(VID_W)), h = vidCards.picH(Ui.f(VID_H));
            for (int i = Math.max(0, vidAt - 1), end = Math.min(videos.size() - 1, vidAt + 6); i <= end; i++) {
                Pics.prefetch(Videos.thumb(videos.get(i).key), w, h, Pics.CROP);
            }
        } else if (part == MORE) {
            int w = moreCards.picW(Ui.f(MORE_W)), h = moreCards.picH(Ui.f(MORE_H));
            for (int i = Math.max(0, moreAt - 2), end = Math.min(more.size() - 1, moreAt + 8); i <= end; i++) {
                Pics.prefetch(more.get(i).poster, w, h, Pics.CROP);
            }
        }
    }

    void ok() {
        switch (part) {
            case COVER:
                cover.ok();
                break;
            case SEASONS:
                move(1);
                break;
            case EPISODES: {
                Catalog.Episode e = episode(epAt);
                if (e != null) host.play(e);
                break;
            }
            case VIDEOS:
                if (vidAt < videos.size()) host.video(videos.get(vidAt).key);
                break;
            default:
                if (moreAt < more.size()) host.open(more.get(moreAt));
                break;
        }
    }

    /** Back: from further down, up to the cover first (true); on the cover, the page closes (false). */
    boolean back() {
        if (part != COVER) {
            part = COVER;
            settle();
            return true;
        }
        return false;
    }

    /** The remote back from the tab bar: on the cover. */
    void enter() {
        part = COVER;
        settle();
    }

    /** Previews: the remote on a part and along it (the cover: its row, -1 for its first button). */
    void focus(int p, int along) {
        part = p;
        if (p == COVER) cover.focusRow(along, 0);
        else if (p == SEASONS && !seasons.isEmpty()) {
            seasonAt = Math.min(along, seasons.size() - 1);
            epAt = seasons.get(seasonAt)[1];
        } else if (p == EPISODES) {
            epAt = Math.min(along, Math.max(0, rowSize() - 1));
            if (!seasons.isEmpty()) seasonAt = seasonOf(epAt);
        } else if (p == VIDEOS) vidAt = Math.min(along, Math.max(0, videos.size() - 1));
        else moreAt = along;
        settle();
    }

    /** Tests: the season tab in view (its place), the episode the remote is on (its place in the row), the video's. */
    int previewSeason() {
        return seasonAt;
    }

    int previewEpisode() {
        return epAt;
    }

    int previewVideo() {
        return vidAt;
    }

    int previewMore() {
        return moreAt;
    }

    /** Tests: each season's first episode's place in the row. */
    int previewFirstOf(int season) {
        return season < seasons.size() ? seasons.get(season)[1] : -1;
    }

    /** Tests: how lifted an episode is now (0 resting, 1 the remote's). */
    float previewLift(int i) {
        return lift.of(episode(i));
    }

    /** Tests: an episode's words as its panel holds them: its name (cut to fit) and its story's lines, laid out. */
    String previewName(int i) {
        return i >= 0 && i < epNames.length ? epNames[i] : "";
    }

    StaticLayout previewStory(int i) {
        return storyOf(i);
    }

    /** Tests: the room the words have inside the panel (screen px), and the paint the name is drawn in. */
    float previewRoom() {
        return Ui.f(EP_W - 2 * PAD);
    }

    TextPaint previewNamePaint() {
        return epNameP;
    }

    /** Tests: how far down the page a series' section is (design px), and its videos' keys. */
    static float previewSection() {
        return SECTION;
    }

    /** Tests: how far the page rises for More like this (design px). */
    float previewMoreRise() {
        return foot();
    }

    List<String> previewVideos() {
        List<String> out = new ArrayList<>();
        for (Videos.Video v : videos) out.add(v.key);
        return out;
    }

    /** How far the page has risen. */
    float risen() {
        return page.value();
    }

    int colour() {
        return wash.colour();
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        wash.clear();
        float ax = Ui.f(ART_X), aw = Ui.f(ART_W), ah = Ui.f(ART_H);
        // The picture's left into the page's colour (transparent, .6 at 24 %, whole at 50 %), and its foot (whole at 38 %).
        wash.add(ax, 0, ax + aw * 0.5f, ah, ax, 0, ax + aw * 0.5f, 0, 1f, 0.4f, 0.48f, 0f);
        wash.add(ax, ah * 0.62f, ax + aw, ah + 1f, 0, ah, 0, ah * 0.62f, 1f, 0.5f, 0.5f, 0f);
        // The page's shade: darker at the left and the foot (.tp-dark), whatever its colour.
        darkLeft.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xB8000000, 0x80000000, 0x14000000, 0x00000000},
                new float[]{0f, 0.38f, 0.62f, 0.75f}, Shader.TileMode.CLAMP));
        darkFoot.setShader(new LinearGradient(0, h, 0, h * 0.55f, 0x8C000000, 0x00000000, Shader.TileMode.CLAMP));
        if (title != null) {
            layoutWords();
            jumpColour();
        }
    }

    @Override protected void onDraw(Canvas c) {
        if (title == null) return;
        boolean moving = page.step();
        moving |= epX.step();
        moving |= moreX.step();
        moving |= vidX.step();
        moving |= pillL.step();
        moving |= pillR.step();
        moving |= wash.step();
        Object focus = !active ? null : part == EPISODES ? episode(epAt) : part == MORE && moreAt < more.size() ? more.get(moreAt)
                : part == VIDEOS && vidAt < videos.size() ? videos.get(vidAt) : null;
        moving |= lift.step(focus);

        int w = getWidth(), h = getHeight();
        float risen = Ui.f(page.value());
        // The cover under this view goes up with the page (its ground stays); once a series' section has come right
        // over it, it isn't drawn at all.
        cover.scroll(page.value());
        float into = into();
        int shown = into >= 1f ? INVISIBLE : VISIBLE;
        if (cover.getVisibility() != shown && cover.isOpen()) cover.setVisibility(shown);
        if (into > 0f) {
            // A series' section is dark: its ground (the page's colour, its picture faded right down, its shade and its
            // glow: kept as one picture, drawn again only when they change) comes over the cover as the page rises to it.
            groundPic = backdrop.isEmpty() ? null : Pics.get(backdrop, 1280, 720, Pics.CROP, redraw);
            groundFade = wash.fading();
            long key = Cached.key(Cached.key(wash.colour(), 0), Cached.key(Math.round(groundFade * 255f), glowWasColour)
                    + Cached.key(glowColour, Math.round(Ui.s * 1000f)));
            still.draw(c, w, h, groundPic, key, paintGround, into, 0f);
            if (groundFade >= 1f) glowWasColour = 0;
        }

        c.save();
        c.translate(0, -risen);
        if (lowerPage()) {
            // The series' section, a screen down; each part only in sight.
            float sectionTop = Ui.f(SECTION) - risen;
            if (sectionTop < h && sectionTop + Ui.f(LOWER) > 0) {
                c.save();
                c.translate(0, Ui.f(SECTION));
                drawSection(c, into, sectionTop);
                c.restore();
            }
        }
        if (hasPart(MORE) && Ui.f(moreTop()) - risen < h) drawMore(c, Ui.f(moreTop()));
        c.restore();
        if (moving) postInvalidateOnAnimation();
    }

    private final Cached still = new Cached(true);
    private final Cached.Painter paintGround = this::paintGround;
    /** What the kept ground was drawn with: the picture, the glow's fade. */
    private Bitmap groundPic;
    private float groundFade = 1f;
    /** How dark a series' section is made over the page's colour (of 255), and how much of its picture shows. */
    static final int DARK = 170, SECTION_ART = 56;

    /** A series' section's ground: its colour, the picture across the right faded right down, its shade, its glow. */
    private void paintGround(Canvas c) {
        int w = getWidth(), h = getHeight();
        ground.setColor(wash.colour());
        c.drawRect(0, 0, w, h, ground);
        Bitmap b = groundPic;
        if (b != null) {
            art.setAlpha(SECTION_ART);
            float ax = Ui.f(ART_X), aw = Ui.f(ART_W), ah = Ui.f(ART_H);
            float s = Math.max(aw / b.getWidth(), ah / b.getHeight());
            m.setScale(s, s);
            m.postTranslate(ax + (aw - b.getWidth() * s) / 2f, (ah - b.getHeight() * s) * 0.3f);
            c.save();
            c.clipRect(ax, 0, ax + aw, ah);
            c.drawBitmap(b, m, art);
            c.restore();
            wash.draw(c);
        }
        // The shades only where they shade (the left's clear from 75 % across, the foot's above 55 % down).
        c.drawRect(0, 0, w * 0.75f, h, darkLeft);
        c.drawRect(0, h * 0.55f, w, h, darkFoot);
        // The glow only where it glows (1000 x 520 at the foot, 12 % across, clear by 70 %).
        float k = groundFade, gr = Ui.f(940f), gt = h - Ui.f(372f);
        if (glowWasColour != 0 && k < 1f) {
            glowWas.setAlpha(Math.round(255 * (1f - k)));
            c.drawRect(0, gt, gr, h, glowWas);
        }
        glowNow.setAlpha(glowWasColour != 0 ? Math.round(255 * k) : 255);
        c.drawRect(0, gt, gr, h, glowNow);
        scrim.setAlpha(DARK);
        c.drawRect(0, 0, w, h, scrim);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        still.forget();
    }

    /**
     * A series' section (its top at the canvas's 0; screenTop: where that is on the screen now): the title art in the
     * middle coming in as the page rises to it, the season tabs, the row of episodes and, where in sight, the Videos row.
     */
    private void drawSection(Canvas c, float into, float screenTop) {
        int la = Math.round(255 * Math.max(0f, Math.min(1f, (into - 0.45f) / 0.55f)));
        if (la > 0) {
            float mw = sectionMark.width(redraw), mh = sectionMark.height(redraw);
            sectionMark.draw(c, (getWidth() - mw) / 2f, Ui.f(ART_FOOT) - mh, la, redraw);
        }
        if (!seasons.isEmpty()) drawTabs(c);
        if (hasPart(EPISODES)) drawEpisodes(c);
        if (hasPart(VIDEOS) && screenTop + Ui.f(VIDEOS_TOP) < getHeight()) drawVideos(c);
    }

    /**
     * The season tabs: the season in view bold and white, the others quieter; while the remote is on them a white pill
     * behind the one it is on (gliding to the next), its word in ink.
     */
    private void drawTabs(Canvas c) {
        float x0 = Ui.f(LEFT), cy = Ui.f(TABS_Y), h = Ui.f(TAB_H);
        boolean on = active && part == SEASONS;
        if (on) {
            r.set(x0 + pillL.value(), cy - h / 2f, x0 + pillR.value(), cy + h / 2f);
            Shadows.draw(c, r, h / 2f, Look07.SH_PILL, 1f);
            c.drawRoundRect(r, h / 2f, h / 2f, white);
        }
        for (int i = 0; i < seasonWords.length; i++) {
            float x = x0 + seasonX[i];
            TextPaint p;
            if (on) {
                // Ink where the pill is under the word, quiet elsewhere.
                float mid = x + seasonW[i] / 2f;
                p = x0 + pillL.value() < mid && x0 + pillR.value() > mid ? tabInk : tabQuiet;
            } else {
                p = i == seasonAt ? tabP : tabQuiet;
            }
            c.drawText(seasonWords[i], x + Ui.f(TAB_PAD), Type.centred(p, cy), p);
        }
    }

    private void drawEpisodes(Canvas c) {
        int n = rowSize();
        float w = Ui.f(EP_W), h = Ui.f(EP_H), top = Ui.f(EP_TOP), step = Ui.f(EP_STEP), x0 = Ui.f(LEFT) - Ui.f(epX.value());
        int lifted = -1;
        for (int i = Math.max(0, (int) Math.floor((-x0 - w) / step) - 1); i < n; i++) {
            float x = x0 + i * step;
            if (x > getWidth()) break;
            if (x + w < -Ui.f(60f)) continue;
            if (active && part == EPISODES && i == epAt) {
                lifted = i;
                continue;
            }
            episodeCard(c, i, x, top, w, h, lift.of(episode(i)));
        }
        if (lifted >= 0) episodeCard(c, lifted, x0 + lifted * step, top, w, h, lift.of(episode(lifted)));
    }

    /**
     * An episode: its still (lifting where the remote is), a thin white line on it as far as it was watched, UP NEXT on
     * the one to carry on with; under it its words, on a frosted panel as it lifts.
     */
    private void episodeCard(Canvas c, int i, float x, float y, float w, float h, float up) {
        Catalog.Episode e = episode(i);
        if (e == null) return;
        String pic = e.still.isEmpty() ? backdrop : e.still;
        Bitmap b = pic.isEmpty() ? null : Pics.get(pic, epCards.picW(w), epCards.picH(h), Pics.CROP, redraw);
        boolean seen = resumeAt >= 0 && i < resumeAt;
        float p = seen ? 1f : host.progress(e);
        marks.clear();
        marks.torbox = e.torbox;
        epCards.begin(c, x, y, w, h, up, b);
        epCards.marks(c, w, h, marks);
        if (p > 0f) {
            // How far it was watched: a thin white line across the still's foot, on a faint one.
            float lx = Ui.f(18f), lw = w - 2 * lx, ly = h - Ui.f(18f), lt = Math.max(2f, Ui.f(4f));
            r.set(lx, ly - lt / 2f, lx + lw, ly + lt / 2f);
            fill.setColor(0x52FFFFFF);
            c.drawRoundRect(r, lt / 2f, lt / 2f, fill);
            r.right = lx + lw * Math.min(1f, p);
            fill.setColor(Look07.L1);
            c.drawRoundRect(r, lt / 2f, lt / 2f, fill);
        }
        if (i == resumeAt) {
            // UP NEXT on the one to carry on with.
            float bw = upNextP.measureText("UP NEXT") + Ui.f(20f);
            r.set(Ui.f(12f), Ui.f(12f), Ui.f(12f) + bw, Ui.f(40f));
            fill.setColor(Look07.L1);
            c.drawRoundRect(r, Ui.f(8f), Ui.f(8f), fill);
            c.drawText("UP NEXT", r.left + Ui.f(10f), Type.centred(upNextP, r.centerY()), upNextP);
        }
        epCards.end(c);
        words(c, i, x, y + h, w, h, up);
    }

    /**
     * An episode's words under its still: "S2 E1" small and quiet, its name, two lines of its story, its length and the
     * day it was first shown; as it lifts they move down with the still's foot, grow a little and sit on frosted glass.
     */
    private void words(Canvas c, int i, float x, float foot, float w, float h, float up) {
        float k = Math.max(0f, Math.min(1f, up));
        float top = foot + (Look07.CARD_SCALE - 1f) * h / 2f * k + Ui.f(WORDS_GAP);
        float g = 1f + WORDS_GROW * k;
        c.save();
        c.scale(g, g, x + w / 2f, top);
        if (k > 0.01f) {
            r.set(x, top, x + w, top + Ui.f(PANEL_H));
            Frost.glass(c, r, Ui.f(18f), k);
        }
        float tx = x + Ui.f(PAD), ty = top + Ui.f(12f);
        float ch = Hero07.normal(Ui.f(17f)), nh = Hero07.normal(Ui.f(22f)), lh = Ui.f(STORY_LINE);
        c.drawText(epCodes[i], tx, Hero07.baseline(ty, ch, Ui.f(17f)), codeP);
        ty += ch + Ui.f(4f);
        epNameP.setColor(Theme.mix(Look07.L2 | 0xFF000000, Look07.L1, k));
        epNameP.setAlpha(Math.round(0xD8 + (0xFF - 0xD8) * k));
        c.drawText(epNames[i], tx, Hero07.baseline(ty, nh, Ui.f(22f)), epNameP);
        ty += nh + Ui.f(6f);
        StaticLayout s = storyOf(i);
        if (s != null) {
            // Quiet, and a little brighter on the panel.
            epStoryP.setColor(Palette.alpha(Look07.L2, Math.round((Look07.L3 >>> 24) + ((Look07.L2 >>> 24) - (Look07.L3 >>> 24)) * k)));
            Ui.draw(c, s, tx, ty + (lh - (epStoryP.descent() - epStoryP.ascent())) / 2f);
        }
        ty += 2 * lh + Ui.f(6f);
        if (!epFacts[i].isEmpty()) c.drawText(epFacts[i], tx, Hero07.baseline(ty, ch, Ui.f(17f)), epFactsP);
        c.restore();
    }

    /** The Videos row: each a 16:9 still (YouTube's) with its kind on a small badge at its top left, its name under it. */
    private void drawVideos(Canvas c) {
        float x = Ui.f(LEFT), top = Ui.f(VIDEOS_TOP);
        c.drawText("Videos", x, Hero07.baseline(top, Hero07.normal(Ui.f(30f)), Ui.f(30f)), headP);
        float w = Ui.f(VID_W), h = Ui.f(VID_H), step = Ui.f(VID_STEP), y = top + Ui.f(48f), x0 = x - Ui.f(vidX.value());
        int lifted = -1;
        for (int i = 0; i < videos.size(); i++) {
            float vx = x0 + i * step;
            if (vx + w < 0 || vx > getWidth()) continue;
            if (active && part == VIDEOS && i == vidAt) {
                lifted = i;
                continue;
            }
            video(c, i, vx, y, w, h, lift.of(videos.get(i)));
        }
        if (lifted >= 0) video(c, lifted, x0 + lifted * step, y, w, h, lift.of(videos.get(lifted)));
    }

    private void video(Canvas c, int i, float x, float y, float w, float h, float up) {
        Videos.Video v = videos.get(i);
        String url = Videos.thumb(v.key);
        Bitmap b = url.isEmpty() ? null : Pics.get(url, vidCards.picW(w), vidCards.picH(h), Pics.CROP, redraw);
        vidCards.begin(c, x, y, w, h, up, b);
        if (b == null) {
            fill.setColor(PosterArt.blank(v.name));
            c.drawRoundRect(0, 0, w, h, Ui.f(Look07.R_CARD), Ui.f(Look07.R_CARD), fill);
        }
        // Its kind, small, on a dark chip at the top left.
        String kind = v.badge().toUpperCase(Locale.UK);
        float bw = badgeP.measureText(kind) + Ui.f(18f);
        r.set(Ui.f(12f), Ui.f(12f), Ui.f(12f) + bw, Ui.f(38f));
        fill.setColor(0xB3000000);
        c.drawRoundRect(r, Ui.f(7f), Ui.f(7f), fill);
        c.drawText(kind, r.left + Ui.f(9f), Type.centred(badgeP, r.centerY()), badgeP);
        vidCards.end(c);
        vidCards.caption(c, x, y + h, w, up, vidNames[i], "");
    }

    /** More like this: posters that lift and tilt, their names and years under them. */
    private void drawMore(Canvas c, float top) {
        c.drawText("More like this", Ui.f(LEFT), Hero07.baseline(top, Hero07.normal(Ui.f(30f)), Ui.f(30f)), headP);
        float w = Ui.f(MORE_W), h = Ui.f(MORE_H), step = Ui.f(MORE_STEP), y = top + Ui.f(52f), x0 = Ui.f(LEFT) - Ui.f(moreX.value());
        int lifted = -1;
        for (int i = 0; i < more.size(); i++) {
            float x = x0 + i * step;
            if (x + w < 0 || x > getWidth()) continue;
            if (part == MORE && i == moreAt) {
                lifted = i;
                continue;
            }
            poster(c, i, x, y, w, h, lift.of(more.get(i)));
        }
        if (lifted >= 0) poster(c, lifted, x0 + lifted * step, y, w, h, lift.of(more.get(lifted)));
    }

    private void poster(Canvas c, int i, float x, float y, float w, float h, float up) {
        Catalog.Title t = more.get(i);
        Bitmap b = t.poster.isEmpty() ? null : Pics.get(t.poster, moreCards.picW(w), moreCards.picH(h), Pics.CROP, redraw);
        marks.clear();
        marks.torbox = t.torbox();
        moreCards.begin(c, x, y, w, h, up, b);
        if (b == null) {
            fill.setColor(PosterArt.blank(t.name));
            c.drawRoundRect(0, 0, w, h, Ui.f(Look07.R_POSTER), Ui.f(Look07.R_POSTER), fill);
        }
        moreCards.marks(c, w, h, marks);
        moreCards.end(c);
        moreCards.caption(c, x, y + h, w, up, moreNames[i], moreYears[i]);
    }

    // ---- pure, for the tests ----------------------------------------------------------------------------------------

    /** Pure: an episode's last line on the series page: "58m  16 Jun 2024", "1h 7m", "16 Jun 2024" or "". */
    static String episodeLine(int minutes, String aired) {
        String len = Ui.duration(minutes);
        String day = episodeDay(aired);
        if (len.isEmpty()) return day;
        return day.isEmpty() ? len : len + "  " + day;
    }

    /** Pure: an episode's facts under its name: "1h 7m, 9 Oct 2022", "54m", "9 Oct 2022" or "". */
    static String episodeFacts(int minutes, String aired) {
        String len = Ui.duration(minutes);
        String day = episodeDay(aired);
        if (len.isEmpty()) return day;
        return day.isEmpty() ? len : len + ", " + day;
    }

    /** Pure: the day an episode was first shown, "9 Oct 2022", or "". */
    static String episodeDay(String aired) {
        if (aired != null && aired.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                java.time.LocalDate d = java.time.LocalDate.parse(aired);
                return d.getDayOfMonth() + " " + d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK) + " " + d.getYear();
            } catch (RuntimeException ignored) {
                return "";
            }
        }
        return "";
    }
}
