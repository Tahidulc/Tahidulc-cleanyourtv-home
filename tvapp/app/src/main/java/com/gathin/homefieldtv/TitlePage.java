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
 * A film's or a series' page in the 07 look (homefield-07-real 10, 12, 13, 14 and clip 3), made for the remote (design
 * px): the page in the title's own colour (its picture's, made deep; a series' its season's, cross-fading as the season
 * changes), its picture across the right fading into that colour, a glow of it at the foot. On the left its logo (TMDB's)
 * or name, its facts, story and credits, then its buttons: Resume (or Play) white with the minutes left after it, From
 * the Start, then round ones for My List (a tick once on it), the trailer and Remove from Continue watching. With Home
 * style Editorial (vertical) the buttons are a menu down the left instead, four lines in sight, Up and Down along it.
 *
 * <p>A series then has its seasons and episodes as the family's screen recording of a series page shows them ("when I'm
 * in the series section on a programme and scrolling through seasons and channels, make it look like this"), in
 * Homefield's own type and 07's materials: the season tabs and the first episodes peek in under the buttons, and Down
 * from them the page rises to the series section, dark, the picture behind faded right down: the show's title art (its
 * TMDB logo, or its name in big lettering) in the middle at the top; under it the season tabs ("Season 1  Season 2
 * Season 3", the season in view bold and white, a white pill behind it while the remote is on the tabs); under them ONE
 * row of every season's episodes, each a 16:9 still with rounded corners (a thin white line on it as far as it was
 * watched, UP NEXT on the one to carry on with) and under it "S2 E1" small and quiet, its name, two lines of its story
 * and "58m  16 Jun 2024". The episode the remote is on lifts (Card07) and its words sit on a frosted rounded panel; as
 * the row goes on into the next season's episodes the tab follows by itself, and Left or Right on the tabs jumps the row
 * to that season's first episode. Under them a Videos row: its trailers, teasers and extras on YouTube (TMDB's) as 16:9
 * stills with their kind on a small badge at the top left (OK: in the TV's YouTube app, as the trailer button). Further
 * down the page rises to About, its genres, the cast's faces and More like this; a film has its cast and More like this
 * under its buttons. Every move glides; only what is in sight is drawn; nothing is made while drawing.
 */
final class TitlePage extends View {
    interface Host {
        /** An episode chosen. */
        void play(Catalog.Episode ep);

        /** The trailer (a series' has it among the buttons too). */
        void trailer();

        /** One of a series' videos (its YouTube key), from the Videos row. */
        void video(String key);

        /** A poster in More like this. */
        void open(Catalog.Title t);

        /** Someone in the cast. */
        void person(String name);

        /** Up from the buttons: on to the tab bar. */
        void up();

        /** How far an episode has been watched, -1 when it hasn't been started. */
        float progress(Catalog.Episode ep);
    }

    /** A button under the title: the main white one (with quieter words after it), a grey one, or a round icon. */
    static final class Button {
        final String label;
        final int icon;
        final boolean main, lit;
        final Runnable run;
        /** Quieter words after the main one's ("18 min left"). */
        String after = "";

        Button(String label, int icon, boolean main, Runnable run) {
            this(label, icon, main, false, run);
        }

        Button(String label, int icon, boolean main, boolean lit, Runnable run) {
            this.label = label;
            this.icon = icon;
            this.main = main;
            this.lit = lit;
            this.run = run;
        }

        /** My List, the trailer and Remove are round icons; the rest pills with their words. */
        boolean round() {
            return !main && (icon == PLUS || icon == TICK || icon == TRAILER || icon == BIN);
        }
    }

    /** The buttons' icons (EPS: More episodes, LIKE: More like this, in the menu down the left). */
    static final int PLAY = 0, PLUS = 1, TICK = 2, RESTART = 3, TRAILER = 4, BIN = 5, EPS = 6, LIKE = 7;
    /** The parts the remote moves through, top to bottom (VIDEOS: a series' trailers and extras). */
    static final int BUTTONS = 0, SEASONS = 1, EPISODES = 2, VIDEOS = 3, CAST = 4, MORE = 5;

    /** Places (design px; the mock-ups' .tp-*). */
    static final float LEFT = 90f, LOGO_TOP = 150f, LOGO_W = 620f, LOGO_H = 170f, FACTS_Y = 353.3f, STORY_TOP = 386f, STORY_W = 920f,
            CREDITS_TOP = 492f, BUTTONS_Y = 575f, ART_X = 420f, ART_W = 1500f, ART_H = 844f, FACE = 140f, FACE_STEP = 200f,
            MORE_W = 196f, MORE_H = 294f, MORE_STEP = 222f, LOWER = 1080f;
    /**
     * A series' section (design px from its top, which is SECTION down the page: the page rises that far to it): the
     * title art's foot, box and the room it has; the tabs' middle, height and the room either side of each tab's word; the
     * episodes' stills (16:9), their step along the row, the gap to their words, the words' room inside the panel and the
     * panel's height; the Videos row's heading and its stills. What is under it (About, the cast, More like this) starts
     * LOWER further down.
     */
    static final float SECTION = 438f, ART_FOOT = 214f, ART_BOX_W = 560f, ART_BOX_H = 120f, TABS_Y = 262f, TAB_H = 50f, TAB_PAD = 24f,
            EP_TOP = 312f, EP_W = 400f, EP_H = 225f, EP_STEP = 432f, WORDS_GAP = 10f, PAD = 16f, PANEL_H = 160f, VIDEOS_TOP = 750f,
            VID_W = 320f, VID_H = 180f, VID_STEP = 344f;
    /** How much the remote's episode's words (and their panel) grow as it lifts. */
    static final float WORDS_GROW = 0.05f;
    /**
     * The menu down the left (Home style Editorial, as the family's other film app has it): its first line's top, a
     * line's step, height, width and corners; four lines in sight at a time; what is under it moved down by MENU_SHIFT.
     */
    static final float MENU_TOP = 548f, MENU_STEP = 74f, MENU_H = 62f, MENU_W = 600f, MENU_R = 18f, MENU_SHIFT = 300f;
    static final int MENU_LINES = 4;
    /** How long the page, a row and the season pill take to glide; the colour's cross-fade. */
    static final long PAGE_MS = 520, ROW_MS = 450, PILL_MS = 450;

    private final Host host;
    private Catalog.Title title;
    private Catalog.Details details;
    private Tmdb.Extras extras;
    private final List<Button> buttons = new ArrayList<>();
    private final List<Catalog.Episode> episodes = new ArrayList<>();
    private final List<Catalog.Title> more = new ArrayList<>();
    /** A series' videos (its Videos row), from TMDB's extras. */
    private final List<Videos.Video> videos = new ArrayList<>();
    private final List<String[]> cast = new ArrayList<>();
    /** Each season's number and its first episode's place in the row. */
    private final List<int[]> seasons = new ArrayList<>();
    private String resumeId = "", backdrop = "";
    private int resumeAt = -1;

    private int part = BUTTONS, buttonAt, epAt, castAt, moreAt, seasonAt, vidAt;
    /** The buttons as a menu down the left (Home style Editorial), and where the menu has slid to (design px). */
    private boolean vertical;
    private final Glide menuY = new Glide(ROW_MS);
    /** Whether the page has the remote (false while it is up on the tab bar: nothing lifted). */
    private boolean active = true;
    private final Glide page = new Glide(PAGE_MS), epX = new Glide(ROW_MS), castX = new Glide(ROW_MS), moreX = new Glide(ROW_MS),
            vidX = new Glide(ROW_MS), pillL = new Glide(PILL_MS), pillR = new Glide(PILL_MS);
    private final Lift07 lift = new Lift07(), buttonLift = new Lift07();
    private final Card07 epCards = new Card07(Look07.R_CARD), moreCards = new Card07(Look07.R_POSTER), vidCards = new Card07(Look07.R_CARD);
    private final Card07.Marks marks = new Card07.Marks();
    /** The page's colour: its ground, and the fades of it over the picture (made when the colour is given). */
    private final Cine.Wash wash = new Cine.Wash();
    private final Paint glowNow = new Paint(Paint.DITHER_FLAG), glowWas = new Paint(Paint.DITHER_FLAG);
    private int glowColour, glowWasColour;
    private int showTint;
    private int[] seasonTint = new int[0];

    // ---- words, laid out in set(), never while drawing --------------------------------------------------------------
    private final Hero07.Mark mark = new Hero07.Mark(64f), sectionMark = new Hero07.Mark(60f);
    private final Hero07.Facts facts = new Hero07.Facts();
    private final TextPaint factsP, storyP, creditsP, tabP, tabQuiet, tabInk, codeP, epNameP, epStoryP, epFactsP, badgeP, aboutP, headP,
            sideHead, sideP, faceName, faceRole, moreName, moreSub, upNextP, initialsP, menuP, menuAfter;
    private StaticLayout story, credits, about;
    private final Paint ground = new Paint(), scrim = new Paint(), art = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG),
            darkLeft = new Paint(Paint.DITHER_FLAG), darkFoot = new Paint(Paint.DITHER_FLAG), white = Ui.fill(Look07.L1),
            fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
            faceP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Shaders shaders = new Shaders();
    private String[] seasonWords = new String[0];
    private float[] seasonX = new float[0], seasonW = new float[0];
    private String meta = "";
    /** Each episode's words: "S2 E1", its name (cut to the panel), "58m  16 Jun 2024"; its story laid out as it comes near. */
    private String[] epCodes = new String[0], epNames = new String[0], epFacts = new String[0], vidNames = new String[0];
    /** How many episodes' stories are kept laid out (those in sight and either side). */
    static final int STORIES = 16;
    private final StaticLayout[] epStories = new StaticLayout[STORIES];
    private final int[] epStoryFor = new int[STORIES];
    private String genresWords = "", onWords = "";
    private String[] castNames = new String[0], castParts = new String[0], castInitials = new String[0];
    private int[] castColours = new int[0];
    private String[] moreNames = new String[0], moreYears = new String[0];
    private final RectF r = new RectF();
    private final Matrix m = new Matrix();
    private final Runnable redraw = this::invalidate, colours = this::colours;

    TitlePage(Context c, Host host) {
        super(c);
        this.host = host;
        Cine.init(c);
        factsP = Type.paint(22f, Type.MEDIUM, Look07.L2);
        storyP = Type.paint(22f, Type.REGULAR, Look07.L2);
        creditsP = Type.paint(19f, Type.REGULAR, Look07.L3);
        tabP = Type.paint(25f, Type.BOLD, Look07.L1);
        tabQuiet = Type.paint(25f, Type.SEMIBOLD, Look07.L3);
        tabInk = Type.paint(25f, Type.BOLD, Look07.INK);
        codeP = Type.paint(17f, Type.MEDIUM, Look07.L3);
        epNameP = Type.paint(22f, Type.SEMIBOLD, Look07.L1);
        epStoryP = Type.paint(18f, Type.REGULAR, Look07.L3);
        epFactsP = Type.paint(17f, Type.MEDIUM, Look07.L3);
        badgeP = Type.paint(14f, Type.EXTRABOLD, Look07.L1);
        badgeP.setLetterSpacing(0.06f);
        aboutP = Type.paint(23f, Type.REGULAR, Look07.L2);
        headP = Type.paint(Type.H3, Type.BOLD, Look07.L1);
        sideHead = Type.paint(26f, Type.BOLD, Look07.L1);
        sideP = Type.paint(21f, Type.REGULAR, Look07.L2);
        faceName = Type.paint(19f, Type.SEMIBOLD, Look07.L1);
        faceRole = Type.paint(16f, Type.REGULAR, Look07.L3);
        moreName = Type.paint(23f, Type.SEMIBOLD, Look07.L2);
        moreSub = Type.paint(19f, Type.MEDIUM, Look07.L3);
        upNextP = Type.paint(14f, Type.EXTRABOLD, Look07.INK);
        upNextP.setLetterSpacing(0.06f);
        initialsP = Type.paint(40f, Type.SEMIBOLD, Look07.L1);
        menuP = Type.paint(26f, Type.MEDIUM, Look07.L1);
        menuAfter = Type.paint(20f, Type.MEDIUM, Look07.INK2);
        Shadows.prepare(Look07.SH_BUTTON, MENU_R);
        ring.setStyle(Paint.Style.STROKE);
        ground.setColor(Look07.GROUND);
        scrim.setColor(0xFF000000);
        java.util.Arrays.fill(epStoryFor, -1);
        Shadows.prepare(Look07.SH_BUTTON, 35f);
        Shadows.prepare(Look07.SH_TILE, FACE / 2f);
        Shadows.prepare(Look07.SH_ROW, FACE / 2f);
        Pills.prepare();
    }

    // ---- what it shows ----------------------------------------------------------------------------------------------

    /** The page for a title: its details (null while they come), its buttons, More like this, the episode to resume. */
    void set(Catalog.Title t, Catalog.Details d, List<Button> bs, List<Catalog.Title> like, String resume) {
        boolean fresh = title != t;
        title = t;
        details = d;
        buttons.clear();
        buttons.addAll(bs);
        buttonAt = Math.min(buttonAt, Math.max(0, buttons.size() - 1));
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
        if (t.series && extras != null) videos.addAll(Videos.row(extras.videos, Tmdb.language()));
        vidNames = new String[videos.size()];
        for (int i = 0; i < videos.size(); i++) {
            Videos.Video v = videos.get(i);
            vidNames[i] = Ui.fit(v.name.isEmpty() ? v.badge() : v.name, moreName, Ui.f(VID_W - 8f));
        }
        vidAt = Math.min(vidAt, Math.max(0, videos.size() - 1));
        seasonWords = new String[seasons.size()];
        for (int i = 0; i < seasons.size(); i++) seasonWords[i] = "Season " + seasons.get(i)[0];
        seasonTint = new int[seasons.size()];
        meta = n == 0 ? "" : n == 1 ? "1 episode" : n + " episodes";
        if (fresh) {
            epAt = Math.max(0, resumeAt);
            seasonAt = seasonOf(epAt);
            part = BUTTONS;
            page.jump(0f);
            castAt = 0;
            buttonAt = 0;
            menuY.jump(0f);
            castX.jump(0f);
            moreX.jump(0f);
            vidX.jump(0f);
            moreAt = 0;
            vidAt = 0;
        }
        epAt = Math.min(epAt, Math.max(0, n - 1));
        epX.jump(epTarget());

        // The cast: TMDB's, with their parts and faces, else the provider's names.
        cast.clear();
        if (extras != null && !extras.cast.isEmpty()) {
            cast.addAll(extras.cast);
        } else if (d != null) {
            for (String name : Billboard.names(d.cast, 10)) cast.add(new String[]{name, "", ""});
        }
        castNames = new String[cast.size()];
        castParts = new String[cast.size()];
        castInitials = new String[cast.size()];
        castColours = new int[cast.size()];
        for (int i = 0; i < cast.size(); i++) {
            castNames[i] = Ui.fit(cast.get(i)[0], faceName, Ui.f(FACE_STEP - 24f));
            castParts[i] = Ui.fit(cast.get(i)[1], faceRole, Ui.f(FACE_STEP - 24f));
            castInitials[i] = Dark.initials(cast.get(i)[0].replace(" (director)", ""));
            castColours[i] = Dark.face(cast.get(i)[0]);
        }
        castAt = Math.min(castAt, Math.max(0, cast.size() - 1));
        // Their photos read after what is on screen.
        int fd = Math.round(Ui.f(FACE));
        for (String[] who : cast) if (who.length > 2 && !who[2].isEmpty()) Pics.prefetch(who[2], fd, fd, Pics.CROP);

        layoutWords();
        colours();
        if (fresh) jumpColour();
        invalidate();
    }

    /** TMDB's extras for the title (its logo, cast, seasons' posters, years and age), when they come. */
    void extras(Tmdb.Extras x) {
        extras = x;
        if (title != null) set(title, details, new ArrayList<>(buttons), new ArrayList<>(more), resumeId);
    }

    /** The words laid out once for what is shown: the mark, facts, story, credits, the season pills, About. */
    private void layoutWords() {
        Catalog.Title t = title;
        Catalog.Details d = details;
        mark.set(t.name, extras == null ? "" : extras.logo, Ui.f(LOGO_W), Ui.f(t.series ? LOGO_H : 120f), Ui.f(1000f));
        sectionMark.set(t.name, extras == null ? "" : extras.logo, Ui.f(ART_BOX_W), Ui.f(ART_BOX_H), Ui.f(1500f));
        facts.set(t, d, extras, 3);
        String plot = d != null && !d.plot.isEmpty() ? d.plot : t.plot;
        story = plot.isEmpty() ? null : Hero07.lines(plot, storyP, Ui.px(STORY_W), 3, Ui.f(22f * 1.45f));
        CharSequence who = credits();
        credits = who.length() == 0 ? null : Hero07.lines(who, creditsP, Ui.px(1500f), 1, Hero07.normal(Ui.f(19f)));
        about = Hero07.lines(plot.isEmpty() ? "No story given for this one yet." : plot, aboutP, Ui.px(1000f), 6, Ui.f(23f * 1.5f));
        // Its genres, and where it is from.
        String g = d == null ? "" : Hero07.Facts.genres(d.genre, 5).replace(", ", " · ");
        if (g.isEmpty()) g = PosterArt.genreOf(t.category);
        genresWords = g;
        StringBuilder on = new StringBuilder(t.torbox() ? Catalog.TORBOX : "Your playlist");
        if (t.series && !seasons.isEmpty()) {
            on.append(" · ").append(seasons.size()).append(seasons.size() == 1 ? " season, " : " seasons, ").append(meta);
        } else if (d != null && d.minutes > 0) {
            on.append(" · ").append(Ui.duration(d.minutes));
        }
        onWords = on.toString();
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

    /** "With A, B, C, D · Created by X" or "Director X", their words in bold. */
    private CharSequence credits() {
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
        List<String> names = new ArrayList<>();
        if (extras != null && !extras.cast.isEmpty()) {
            for (String[] c : extras.cast) if (names.size() < 4 && !c[0].endsWith("(director)")) names.add(c[0]);
        } else if (details != null) {
            for (String n : Billboard.names(details.cast, 4)) names.add(n);
        }
        if (!names.isEmpty()) {
            bold(b, "With");
            b.append(' ').append(String.join(", ", names));
        }
        String dir = details == null || details.director == null ? "" : details.director.trim();
        if (!dir.isEmpty()) {
            if (b.length() > 0) b.append(" · ");
            bold(b, title.series ? "Created by" : "Director");
            b.append(' ').append(dir);
        }
        return b;
    }

    private static void bold(android.text.SpannableStringBuilder b, String words) {
        int at = b.length();
        b.append(words);
        b.setSpan(new android.text.style.ForegroundColorSpan(Look07.L2), at, b.length(), 0);
        b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, b.length(), 0);
    }

    // ---- colours ----------------------------------------------------------------------------------------------------

    /** The picture's colour and each season's (their posters'), as they become known; the page fades to the one wanted. */
    private void colours() {
        if (title == null) return;
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
            case BUTTONS:
                return !buttons.isEmpty();
            case SEASONS:
                return seasons.size() > 1;
            case EPISODES:
                return rowSize() > 0;
            case VIDEOS:
                return lowerPage() && !videos.isEmpty();
            case CAST:
                return !cast.isEmpty();
            default:
                return !more.isEmpty();
        }
    }

    /** Whether the page has a lower part (a series: About, the cast, More like this under the episodes). */
    private boolean lowerPage() {
        return title != null && title.series && rowSize() > 0;
    }

    /**
     * How far the page has risen for the part the remote is on: a series' section (its seasons, episodes and videos)
     * to the top of the screen, what is under it a screen further.
     */
    private float pageTarget() {
        // With the menu down the left, what is under the buttons starts lower: the page rises as much more for it.
        float shift = part == BUTTONS ? 0f : menuShift();
        if (lowerPage()) return part == BUTTONS ? 0f : SECTION + (part >= CAST ? LOWER : 0f) + shift;
        return (part == MORE ? 450f : 0f) + shift;
    }

    /** How far into a series' section the page has risen (0 on the buttons, 1 there and further down). */
    private float into() {
        if (!lowerPage()) return 0f;
        return Math.max(0f, Math.min(1f, page.value() / (SECTION + menuShift())));
    }

    private float menuShift() {
        return vertical ? MENU_SHIFT : 0f;
    }

    /** The menu's first line in sight: the remote's kept second from the top (the first lines from the start). */
    private int menuFirst() {
        return Math.max(0, Math.min(buttons.size() - MENU_LINES, buttonAt - 1));
    }

    /** The buttons as a menu down the left (Home style Editorial), or in a line (Classic). */
    void vertical(boolean on) {
        vertical = on;
        invalidate();
    }

    /** A part of the page by a menu line (More episodes, More like this): the remote there, the page risen to it. */
    void toPart(int p) {
        if (!hasPart(p)) return;
        part = p;
        settle();
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

    private float castTarget() {
        float max = Math.max(0f, cast.size() * FACE_STEP - (1920f - 2 * LEFT));
        return Math.max(0f, Math.min(max, (castAt - 3) * FACE_STEP));
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
        invalidate();
    }

    boolean key(int code) {
        if (vertical && part == BUTTONS) {
            // The menu down the left: Up and Down along it (Up past its top to the tab bar, Down past its foot on down).
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (buttonAt > 0) {
                        buttonAt--;
                        settle();
                        return true;
                    }
                    return move(-1);
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (buttonAt < buttons.size() - 1) {
                        buttonAt++;
                        settle();
                        return true;
                    }
                    return move(1);
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return true;
                default:
                    return false;
            }
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
        while (p >= BUTTONS && p <= MORE && !hasPart(p)) p += dir;
        if (p < BUTTONS) {
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
            case BUTTONS:
                buttonAt = Math.max(0, Math.min(buttons.size() - 1, buttonAt + dir));
                break;
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
            case CAST:
                castAt = Math.max(0, Math.min(cast.size() - 1, castAt + dir));
                break;
            default:
                moreAt = Math.max(0, Math.min(more.size() - 1, moreAt + dir));
                break;
        }
        settle();
    }

    /** Everything sent on its way to where the remote now is. */
    private void settle() {
        page.to(pageTarget());
        menuY.to(menuFirst() * MENU_STEP);
        epX.to(epTarget());
        castX.to(castTarget());
        moreX.to(moreTarget());
        vidX.to(vidTarget());
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
        toColour(target(), false);
        readAhead();
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
            case BUTTONS:
                if (buttonAt < buttons.size()) buttons.get(buttonAt).run.run();
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
            case CAST:
                if (castAt < cast.size()) host.person(cast.get(castAt)[0]);
                break;
            default:
                if (moreAt < more.size()) host.open(more.get(moreAt));
                break;
        }
    }

    /** Back: from further down, up to the buttons first (true); from the buttons, the page closes (false). */
    boolean back() {
        if (part != BUTTONS && hasPart(BUTTONS)) {
            part = BUTTONS;
            settle();
            return true;
        }
        return false;
    }

    /** The remote back from the tab bar. */
    void enter() {
        part = hasPart(BUTTONS) ? BUTTONS : EPISODES;
        settle();
    }

    /** Previews: the remote on a part and along it. */
    void focus(int p, int along) {
        part = p;
        if (p == BUTTONS) buttonAt = along;
        else if (p == SEASONS && !seasons.isEmpty()) {
            seasonAt = Math.min(along, seasons.size() - 1);
            epAt = seasons.get(seasonAt)[1];
        } else if (p == EPISODES) {
            epAt = Math.min(along, Math.max(0, rowSize() - 1));
            if (!seasons.isEmpty()) seasonAt = seasonOf(epAt);
        } else if (p == VIDEOS) vidAt = Math.min(along, Math.max(0, videos.size() - 1));
        else if (p == CAST) castAt = along;
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

    List<String> previewVideos() {
        List<String> out = new ArrayList<>();
        for (Videos.Video v : videos) out.add(v.key);
        return out;
    }

    /** Tests: the buttons' words in order, and whether they are the menu down the left. */
    List<String> previewLabels() {
        List<String> out = new ArrayList<>();
        for (Button b : buttons) out.add(b.label);
        return out;
    }

    boolean previewVertical() {
        return vertical;
    }

    /** Tests: the button the remote is on (its place). */
    int previewButton() {
        return buttonAt;
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
        moving |= castX.step();
        moving |= moreX.step();
        moving |= vidX.step();
        moving |= pillL.step();
        moving |= pillR.step();
        moving |= menuY.step();
        moving |= wash.step();
        Object focus = !active ? null : part == EPISODES ? episode(epAt) : part == MORE && moreAt < more.size() ? more.get(moreAt)
                : part == CAST && castAt < cast.size() ? cast.get(castAt) : part == VIDEOS && vidAt < videos.size() ? videos.get(vidAt)
                : null;
        moving |= lift.step(focus);
        moving |= buttonLift.step(active && part == BUTTONS && buttonAt < buttons.size() ? buttons.get(buttonAt) : null);

        int w = getWidth(), h = getHeight();
        float risen = Ui.f(page.value());
        // The page's colour, its picture across the right fading into it, its shade and its glow: five screens of fill
        // that don't move as the rows do, kept as one picture (Cached) and drawn again only when they change.
        groundPic = backdrop.isEmpty() ? null : Pics.get(backdrop, 1280, 720, Pics.CROP, redraw);
        // A series' section is dark: the picture fades right down and the page darkens as it rises to it.
        float into = into();
        groundArt = Math.round(255 * (1f - 0.78f * into));
        groundDark = Math.round(DARK * into);
        groundFade = wash.fading();
        long key = Cached.key(Cached.key(wash.colour(), groundArt * 256 + groundDark), Cached.key(Math.round(groundFade * 255f), glowWasColour)
                + Cached.key(glowColour, Math.round(Ui.s * 1000f)));
        still.draw(c, w, h, groundPic, key, paintGround, 1f, 0f);
        if (groundFade >= 1f) glowWasColour = 0;

        c.save();
        c.translate(0, -risen);
        // The words and buttons at the top fade as the page rises past them (never under the back line).
        // (With the menu down the left the page rises less far for the episodes: the top is gone by then all the same.)
        int topAlpha = Math.round(255 * Math.max(0f, Math.min(1f, 1f - page.value() / (vertical ? MENU_SHIFT * 0.8f : 320f))));
        if (topAlpha > 0) drawTop(c, topAlpha);
        if (vertical) c.translate(0, Ui.f(MENU_SHIFT));
        if (lowerPage()) {
            // The series' section, then (a screen further down) About, the cast and More like this; each only in sight.
            float sectionTop = Ui.f(SECTION + menuShift()) - risen;
            c.save();
            c.translate(0, Ui.f(SECTION));
            if (sectionTop < h && sectionTop + Ui.f(LOWER) > 0) drawSection(c, into, sectionTop);
            c.translate(0, Ui.f(LOWER));
            if (sectionTop + Ui.f(LOWER) < h) {
                drawAbout(c);
                if (hasPart(CAST)) drawCast(c, Ui.f(380f));
                if (hasPart(MORE)) drawMore(c, Ui.f(690f));
            }
            c.restore();
        } else {
            if (hasPart(CAST)) drawCast(c, Ui.f(660f));
            if (hasPart(MORE)) drawMore(c, Ui.f(hasPart(CAST) ? 944f : 660f));
        }
        c.restore();
        if (moving) postInvalidateOnAnimation();
    }

    private final Cached still = new Cached(true);
    private final Cached.Painter paintGround = this::paintGround;
    /** What the kept ground was drawn with: the picture, its opacity (lower on the page's lower part), the glow's fade. */
    private Bitmap groundPic;
    private int groundArt = 255, groundDark;
    private float groundFade = 1f;
    /** How dark a series' section is made over the page's colour (of 255). */
    static final int DARK = 170;

    /** The page's ground: its colour, the picture across the right fading into it, its shade and its glow. */
    private void paintGround(Canvas c) {
        int w = getWidth(), h = getHeight();
        ground.setColor(wash.colour());
        c.drawRect(0, 0, w, h, ground);
        Bitmap b = groundPic;
        if (b != null) {
            art.setAlpha(groundArt);
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
        if (groundDark > 0) {
            scrim.setAlpha(groundDark);
            c.drawRect(0, 0, w, h, scrim);
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        still.forget();
    }

    private void drawTop(Canvas c, int a) {
        float x = Ui.f(LEFT);
        // Its logo (or name) on the foot of its box, its facts, story and credits.
        float boxTop = Ui.f(title.series ? LOGO_TOP : 170f), boxH = Ui.f(title.series ? LOGO_H : 120f);
        float mh = mark.height(redraw);
        mark.draw(c, x, boxTop + Math.max(0f, boxH - mh), a, redraw);
        facts.draw(c, x, Ui.f(FACTS_Y), a, factsP);
        if (story != null) {
            float lh = Ui.f(22f * 1.45f);
            storyP.setAlpha((Look07.L2 >>> 24) * a / 255);
            Ui.draw(c, story, x, Ui.f(STORY_TOP) + (lh - (storyP.descent() - storyP.ascent())) / 2f);
        }
        if (credits != null) {
            creditsP.setAlpha((Look07.L3 >>> 24) * a / 255);
            Ui.draw(c, credits, x, Ui.f(CREDITS_TOP + 2f));
        }
        if (vertical) {
            drawMenu(c, a);
            return;
        }
        // The buttons: the main one white (lifted 1.07 where the remote is), grey pills, round icons.
        float bx = x, cy = Ui.f(BUTTONS_Y);
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            boolean here = active && part == BUTTONS && i == buttonAt;
            float up = Math.max(0f, Math.min(1.1f, buttonLift.of(b)));
            float g = 1f + 0.07f * up;
            if (b.round()) {
                float cx = bx + Ui.f(35f);
                c.save();
                c.scale(g, g, cx, cy);
                int icon = b.icon == TICK ? Icons07.CHECK : b.icon == PLUS ? Icons07.PLUS : b.icon == TRAILER ? Icons07.FILM : Icons07.X;
                Hero07.circle(c, cx, cy, icon, here, b.lit, a);
                c.restore();
                bx += Ui.f(70f) + Ui.f(18f);
            } else {
                int icon = b.icon == PLAY ? Icons07.PLAY : b.icon == RESTART ? Icons07.RESTART : -1;
                float bw = Hero07.buttonWidth(b.label, b.main ? b.after : "", icon);
                c.save();
                c.scale(g, g, bx + bw / 2f, cy);
                Hero07.button(c, bx, cy, b.label, b.main ? b.after : "", icon, b.main || here, a);
                c.restore();
                bx += bw + Ui.f(18f);
            }
        }
    }

    /**
     * The buttons as a menu down the left, four lines in sight (the list sliding as the remote goes down it): each its icon
     * and its words, the remote's on a white rounded bar, lifted a little; the others plain on the page.
     */
    private void drawMenu(Canvas c, int a) {
        float x = Ui.f(LEFT) - Ui.f(14f), top = Ui.f(MENU_TOP), step = Ui.f(MENU_STEP), h = Ui.f(MENU_H), w = Ui.f(MENU_W);
        float rad = Ui.f(MENU_R), off = Ui.f(menuY.value()), last = top + (MENU_LINES - 1) * step;
        for (int i = 0; i < buttons.size(); i++) {
            float y = top + i * step - off;
            // A line sliding out of the four in sight fades as it goes.
            float k = y < top ? 1f - (top - y) / step : y > last ? 1f - (y - last) / step : 1f;
            if (k <= 0.01f) continue;
            int la = Math.round(a * Math.min(1f, k));
            Button b = buttons.get(i);
            boolean here = active && part == BUTTONS && i == buttonAt;
            float cy = y + h / 2f;
            c.save();
            if (here) {
                float g = 1f + 0.02f * Math.max(0f, Math.min(1.1f, buttonLift.of(b)));
                c.scale(g, g, x, cy);
                r.set(x, y, x + w, y + h);
                Shadows.draw(c, r, rad, Look07.SH_BUTTON, la / 255f);
                fill.setColor(Palette.alpha(Look07.L1, la));
                c.drawRoundRect(r, rad, rad, fill);
            }
            int ink = here ? Look07.INK : Look07.L1;
            Icons07.draw(c, menuIcon(b.icon), x + Ui.f(36f), cy, Ui.f(26f), Palette.alpha(ink, la));
            menuP.setColor(ink);
            menuP.setAlpha(la);
            float tx = x + Ui.f(74f);
            c.drawText(b.label, tx, Type.centred(menuP, cy), menuP);
            if (b.main && !b.after.isEmpty()) {
                menuAfter.setColor(here ? Look07.INK2 : Look07.L3);
                menuAfter.setAlpha(((here ? Look07.INK2 : Look07.L3) >>> 24) * la / 255);
                c.drawText(b.after, tx + menuP.measureText(b.label) + Ui.f(16f), Type.centred(menuAfter, cy), menuAfter);
            }
            c.restore();
        }
    }

    private static int menuIcon(int icon) {
        switch (icon) {
            case PLAY: return Icons07.PLAY;
            case TRAILER: return Icons07.FILM;
            case EPS: return Icons07.LIST;
            case PLUS: return Icons07.PLUS;
            case TICK: return Icons07.CHECK;
            case LIKE: return Icons07.GRID;
            case RESTART: return Icons07.RESTART;
            default: return Icons07.X;
        }
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

    /** About (its story), its genres and where it is from (the lower page, its top at the page's 0). */
    private void drawAbout(Canvas c) {
        float x = Ui.f(LEFT), top = Ui.f(150f);
        c.drawText("About", x, Hero07.baseline(top, Hero07.normal(Ui.f(30f)), Ui.f(30f)), headP);
        if (about != null) Ui.draw(c, about, x, top + Hero07.normal(Ui.f(30f)) + Ui.f(10f));
        float gx = Ui.f(1220f), gy = top;
        if (!genresWords.isEmpty()) {
            c.drawText("Genres", gx, Hero07.baseline(gy, Ui.f(26f * 1.6f), Ui.f(26f)), sideHead);
            gy += Ui.f(26f * 1.6f) + Ui.f(6f);
            c.drawText(genresWords, gx, Hero07.baseline(gy, Ui.f(21f * 1.6f), Ui.f(21f)), sideP);
            gy += Ui.f(21f * 1.6f) + Ui.f(18f);
        }
        c.drawText("On", gx, Hero07.baseline(gy, Ui.f(26f * 1.6f), Ui.f(26f)), sideHead);
        gy += Ui.f(26f * 1.6f) + Ui.f(6f);
        c.drawText(onWords, gx, Hero07.baseline(gy, Ui.f(21f * 1.6f), Ui.f(21f)), sideP);
    }

    /** The cast as round faces with the parts they play; the remote's grown with a white ring. */
    private void drawCast(Canvas c, float top) {
        float x0 = Ui.f(LEFT) - Ui.f(castX.value());
        c.drawText("Cast", Ui.f(LEFT), Hero07.baseline(top, Hero07.normal(Ui.f(30f)), Ui.f(30f)), headP);
        float d = Ui.f(FACE), cy = top + Ui.f(54f) + d / 2f, step = Ui.f(FACE_STEP);
        int fd = Math.round(d);
        for (int i = 0; i < cast.size(); i++) {
            float cx = x0 + i * step + Ui.f(85f);
            if (cx - step > getWidth() || cx + step < 0) continue;
            float up = part == CAST ? Math.max(0f, Math.min(1.2f, lift.of(cast.get(i)))) : 0f;
            float g = 1f + 0.12f * up, rr = d / 2f * g;
            r.set(cx - rr, cy - rr, cx + rr, cy + rr);
            Shadows.draw(c, r, rr, Look07.SH_TILE, 1f - Math.min(1f, up));
            if (up > 0.01f) Shadows.draw(c, r, rr, Look07.SH_ROW, Math.min(1f, up));
            String url = cast.get(i).length > 2 ? cast.get(i)[2] : "";
            Bitmap b = url.isEmpty() ? null : Pics.get(url, fd, fd, Pics.CROP, redraw);
            if (b != null) {
                Paint p = shaders.paint(b, 2 * rr, 2 * rr, 0f);
                c.save();
                c.translate(cx - rr, cy - rr);
                c.drawCircle(rr, rr, rr, p);
                c.restore();
            } else {
                fill.setColor(castColours[i]);
                c.drawCircle(cx, cy, rr, fill);
                float iw = initialsP.measureText(castInitials[i]);
                c.drawText(castInitials[i], cx - iw / 2f, Type.centred(initialsP, cy), initialsP);
            }
            ring.setStrokeWidth(up > 0.01f ? Ui.f(4f) : Math.max(1f, Ui.f(2f)));
            ring.setColor(up > 0.01f ? Palette.alpha(Look07.L1, Math.round(255 * Math.min(1f, up))) : 0x24FFFFFF);
            c.drawCircle(cx, cy, rr + ring.getStrokeWidth() / 2f, ring);
            float ty = cy + d / 2f + Ui.f(12f) + Ui.f(12f) * Math.min(1f, up);
            float nh = Hero07.normal(Ui.f(19f));
            float nw = faceName.measureText(castNames[i]);
            c.drawText(castNames[i], cx - nw / 2f, Hero07.baseline(ty, nh, Ui.f(19f)), faceName);
            if (!castParts[i].isEmpty()) {
                float pw = faceRole.measureText(castParts[i]);
                c.drawText(castParts[i], cx - pw / 2f, Hero07.baseline(ty + nh + Ui.f(2f), Hero07.normal(Ui.f(16f)), Ui.f(16f)), faceRole);
            }
        }
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
