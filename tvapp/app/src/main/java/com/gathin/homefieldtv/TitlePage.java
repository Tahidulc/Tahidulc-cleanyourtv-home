package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Tmdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A film's or a series' page, as the family's "TV show card" has it, made for the remote (design pixels, 1920 x 1080):
 * the title's picture across the whole page, fading at the left and the foot into the page's colour, which is the
 * picture's own colour made deep (a series': its season's, changing as the remote goes from season to season). Over it,
 * its logo (or its name in big lettering), its rating, years, age and picture in a line, the red Play (or Resume) pill
 * and grey buttons. A series then has the phone's seasons: Season 1, Season 2… with the white pill behind the season
 * the remote is in, following it, and under them one row of every episode of every season (its trailer first), the
 * one the remote is on grown and named. Further down, as the remote goes there, the page rises: What it's about and its
 * genres side by side, its cast as round faces with the parts they play, and More like this, the poster the remote is
 * on grown. Every move glides (Glide): the page, each row, the season pill; the colour fades. Only what is on screen is
 * drawn.
 */
final class TitlePage extends View {
    interface Host {
        /** An episode chosen. */
        void play(Catalog.Episode ep);

        /** The Trailer tile. */
        void trailer();

        /** A poster in More like this. */
        void open(Catalog.Title t);

        /** Someone in the cast. */
        void person(String name);

        /** Up from the buttons: on to the top bar. */
        void up();

        /** How far an episode has been watched, -1 when it hasn't been started. */
        float progress(Catalog.Episode ep);
    }

    /** A button under the title: the red pill (main), or grey with an icon. */
    static final class Button {
        final String label;
        final int icon;
        final boolean main, lit;
        final Runnable run;

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
    }

    /** The buttons' icons. */
    static final int PLAY = 0, PLUS = 1, TICK = 2, RESTART = 3, TRAILER = 4, BIN = 5;
    /** The parts the remote moves through, top to bottom. */
    static final int BUTTONS = 0, SEASONS = 1, EPISODES = 2, CAST = 3, MORE = 4;

    static final float LEFT = TopBar.LEFT, LOGO_TOP = 190f, LOGO_W = 840f, LOGO_H = 280f, FACTS_Y = 532f, BUTTONS_TOP = 580f,
            BUTTON_H = 78f, PILLS_Y = 724f, PILL_H = 54f, EP_TOP = 796f, EP_W = 372f, EP_H = 209f, EP_GAP = 40f, GROW = 1.22f,
            FACE = 112f, CELL = 400f, MORE_W = 220f, MORE_H = 330f, MORE_GAP = 30f, HEAD = 64f;

    private final Host host;
    private Catalog.Title title;
    private Catalog.Details details;
    private Tmdb.Extras extras;
    private final List<Button> buttons = new ArrayList<>();
    private final List<Catalog.Episode> episodes = new ArrayList<>();
    private final List<Catalog.Title> more = new ArrayList<>();
    private final List<String[]> cast = new ArrayList<>();
    /** Each season's number and its first episode's place in the row (the trailer counted). */
    private final List<int[]> seasons = new ArrayList<>();
    private boolean trailerTile;
    private String resumeId = "", backdrop = "";

    private int part = BUTTONS, buttonAt, epAt, castAt, moreAt, seasonAt;
    private final Glide page = new Glide(PAGE_MS), epX = new Glide(ROW_MS), castX = new Glide(ROW_MS), moreX = new Glide(ROW_MS),
            pillL = new Glide(PILL_MS), pillR = new Glide(PILL_MS), pillsX = new Glide(ROW_MS);
    /** How long the page, a row and the season pill take to glide to where the remote sends them. */
    static final long PAGE_MS = 340, ROW_MS = 280, PILL_MS = 240;
    private final Lift lift = new Lift();
    private final Cine.Wash wash = new Cine.Wash(), curtain = new Cine.Wash();
    private int showTint;
    private TopBar bar;

    // ---- words, laid out in set(), never while drawing --------------------------------------------------------------
    private final Lettering lettering = new Lettering();
    private final TextPaint buttonP, buttonInk, pillP, pillInk, epNo, epName, headP, bodyP, genreP,
            nameP, partP, moreName, badgeP, initialsP;
    private final Paint ground = new Paint(), bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG),
            hero = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG),
            red = Ui.fill(Cine.RED), grey = Ui.fill(Cine.BUTTON), white = Ui.fill(Cine.WHITE),
            ring = Ui.stroke(Cine.WHITE, 3f), track = Ui.fill(0x59FFFFFF), place = Ui.fill(0xFF2A2C33), star = Ui.fill(0xFFFFC94D),
            iconStroke = Ui.stroke(Cine.TEXT, 3.4f), iconFill = Ui.fill(Cine.TEXT), tileFoot = new Paint(), shade = new Paint();
    private final FactsLine facts = new FactsLine(26f);
    private final List<Float> buttonW = new ArrayList<>();
    private String[] seasonWords = new String[0];
    private float[] seasonX = new float[0], seasonW = new float[0];
    private String[] epNos = new String[0], epNames = new String[0];
    private StaticLayout overview;
    private final List<String> genres = new ArrayList<>();
    private String[] castNames = new String[0], castParts = new String[0], castInitials = new String[0];
    private int[] castColours = new int[0];
    private Paint[] faces = new Paint[0];
    private String[] moreNames = new String[0];
    private int[] seasonTint = new int[0];
    private final RectF r = new RectF();
    private final Runnable redraw = this::invalidate, colours = this::colours;

    TitlePage(Context c, Host host) {
        super(c);
        this.host = host;
        Cine.init(c);
        buttonP = Cine.text(28f, Cine.interSemi(), Cine.TEXT);
        buttonInk = Cine.text(28f, Cine.interSemi(), Cine.INK);
        pillP = Cine.text(27f, Cine.outfit(600), 0xB3FFFFFF);
        pillInk = Cine.text(27f, Cine.outfit(600), Cine.INK);
        epNo = Cine.text(28f, Cine.outfit(700), Cine.TEXT);
        epName = Cine.text(28f, Cine.outfit(700), Cine.TEXT);
        epNo.setShadowLayer(Ui.f(8f), 0, Ui.f(2f), 0xB3000000);
        epName.setShadowLayer(Ui.f(8f), 0, Ui.f(2f), 0xB3000000);
        headP = Cine.text(34f, Cine.outfit(700), Cine.TEXT);
        bodyP = Cine.text(25f, Cine.inter(), Cine.MUTED);
        genreP = Cine.text(25f, Cine.inter(), Cine.MUTED);
        nameP = Cine.text(27f, Cine.outfit(600), Cine.TEXT);
        partP = Cine.text(22f, Cine.inter(), Cine.FAINT);
        moreName = Cine.text(26f, Cine.outfit(700), Cine.TEXT);
        badgeP = Cine.text(17f, Cine.interSemi(), Cine.INK);
        badgeP.setLetterSpacing(0.06f);
        initialsP = Cine.text(40f, Cine.outfit(600), Cine.TEXT);
        ground.setColor(Cine.GROUND);
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
        for (int i = 0; i < more.size(); i++) moreNames[i] = Ui.fit(more.get(i).name, moreName, Ui.f(MORE_W - 28f));
        moreAt = Math.min(moreAt, Math.max(0, more.size() - 1));

        // The episodes, every season's in one row, the trailer first.
        String trailer = d == null ? "" : TitleScreen.trailerId(d.trailer);
        trailerTile = t.series && !trailer.isEmpty();
        episodes.clear();
        seasons.clear();
        if (t.series && d != null) {
            int at = trailerTile ? 1 : 0;
            for (Catalog.Season s : d.seasons) {
                if (s.episodes.isEmpty()) continue;
                seasons.add(new int[]{s.number, at + episodes.size()});
                episodes.addAll(s.episodes);
            }
        }
        int n = episodes.size() + (trailerTile ? 1 : 0);
        epNos = new String[n];
        epNames = new String[n];
        for (int i = 0; i < n; i++) {
            Catalog.Episode e = episode(i);
            epNos[i] = e == null ? "Trailer" : String.format(Locale.UK, "%02d", e.number);
        }
        seasonWords = new String[seasons.size()];
        for (int i = 0; i < seasons.size(); i++) seasonWords[i] = "Season " + seasons.get(i)[0];
        seasonTint = new int[seasons.size()];
        if (fresh) {
            int start = 0;
            if (!resumeId.isEmpty()) {
                for (int i = 0; i < n; i++) {
                    Catalog.Episode e = episode(i);
                    if (e != null && e.id.equals(resumeId)) start = i;
                }
            } else if (trailerTile && n > 1) {
                start = 1;
            }
            epAt = start;
            seasonAt = seasonOf(epAt);
            part = BUTTONS;
            page.jump(0f);
            epX.jump(epTarget());
            castAt = 0;
            castX.jump(0f);
            moreX.jump(0f);
            moreAt = 0;
        }
        epAt = Math.min(epAt, Math.max(0, n - 1));

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
        faces = new Paint[cast.size()];
        for (int i = 0; i < cast.size(); i++) {
            castNames[i] = Ui.fit(cast.get(i)[0], nameP, Ui.f(CELL - FACE - 44f));
            castParts[i] = Ui.fit(cast.get(i)[1], partP, Ui.f(CELL - FACE - 44f));
            castInitials[i] = Dark.initials(cast.get(i)[0].replace(" (director)", ""));
            castColours[i] = Dark.face(cast.get(i)[0]);
        }
        castAt = Math.min(castAt, Math.max(0, cast.size() - 1));

        // About it, and its genres.
        String plot = d != null && !d.plot.isEmpty() ? d.plot : t.plot;
        overview = Ui.layout(plot.isEmpty() ? "No story given for this one yet." : plot, bodyP, Ui.px(1080f), 5);
        genres.clear();
        String g = d == null ? "" : d.genre;
        for (String s : g.split("\\s*[,/]\\s*")) if (!s.trim().isEmpty() && genres.size() < 4) genres.add(s.trim());
        if (genres.isEmpty() && !t.category.isEmpty()) genres.add(PosterArt.genreOf(t.category));

        layoutWords();
        colours();
        if (fresh) {
            wash.jump(target());
            curtain.jump(target());
            ground.setColor(wash.colour());
        }
        nameFocused();
        invalidate();
    }

    /** The top bar, to fill with the page's colour as the page rises under it. */
    void bar(TopBar b) {
        bar = b;
    }

    /** TMDB's extras for the title (its logo, cast, seasons' posters, years and age), when they come. */
    void extras(Tmdb.Extras x) {
        extras = x;
        if (title != null) set(title, details, new ArrayList<>(buttons), new ArrayList<>(more), resumeId);
        if (x != null && !x.logo.isEmpty()) {
            // Shown once read, so the lettering gives way to it at once (never a blank between).
            Runnable show = () -> {
                lettering.logo(x.logo);
                invalidate();
            };
            if (Pics.get(x.logo, Ui.px(LOGO_W), Ui.px(LOGO_H), Pics.FIT, show) != null) show.run();
        }
    }

    /** The facts line, the buttons' and seasons' widths: measured once for what is shown. */
    private void layoutWords() {
        Catalog.Title t = title;
        Catalog.Details d = details;
        lettering.set(t.name, Ui.f(LOGO_W), Ui.f(LOGO_H), Ui.f(150f));
        facts.set(t, d, extras);
        buttonW.clear();
        for (Button b : buttons) buttonW.add(buttonP.measureText(b.label) + Ui.f(b.icon >= 0 ? 112f : 72f));
        seasonX = new float[seasonWords.length];
        seasonW = new float[seasonWords.length];
        float x = 0;
        for (int i = 0; i < seasonWords.length; i++) {
            seasonX[i] = x;
            seasonW[i] = pillP.measureText(seasonWords[i]);
            x += seasonW[i] + Ui.f(58f);
        }
        if (seasonWords.length > 0) {
            pillL.jump(pillLeft(seasonAt));
            pillR.jump(pillRight(seasonAt));
            pillsX.jump(pillsTarget());
        }
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
        wash.set(target());
        curtain.set(target());
        invalidate();
    }

    /** The page's colour now: the season the remote is in (its poster's), else the title's picture's. */
    private int target() {
        int art = seasonAt >= 0 && seasonAt < seasonTint.length && seasonTint[seasonAt] != 0 ? seasonTint[seasonAt] : showTint;
        return Cine.shade(art, 0.62f, 0.15f);
    }

    // ---- places ------------------------------------------------------------------------------------------------------

    private Catalog.Episode episode(int i) {
        int k = trailerTile ? i - 1 : i;
        return k >= 0 && k < episodes.size() ? episodes.get(k) : null;
    }

    private int rowSize() {
        return episodes.size() + (trailerTile ? 1 : 0);
    }

    /** The season (its place among them) an episode in the row is in (the trailer: the first). */
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
            case CAST:
                return !cast.isEmpty();
            default:
                return !more.isEmpty();
        }
    }

    /** Where the parts start on the page (design pixels; the page's top is 0). */
    private float overviewTop() {
        return title != null && title.series && rowSize() > 0 ? EP_TOP + EP_H + 96f : BUTTONS_TOP + BUTTON_H + 112f;
    }

    private float castTop() {
        return overviewTop() + 286f;
    }

    private float moreTop() {
        return hasPart(CAST) ? castTop() + 256f : castTop();
    }

    /** How far the page has risen for the part the remote is on. */
    private float pageTarget() {
        switch (part) {
            case CAST:
                return overviewTop() - 150f;
            case MORE:
                return hasPart(CAST) ? castTop() - 150f : overviewTop() - 150f;
            default:
                return 0f;
        }
    }

    private float pitch() {
        return EP_W + EP_GAP;
    }

    /** The episode row's offset: the remote's episode kept second from the left (the first ones from the start). */
    private float epTarget() {
        float max = Math.max(0f, rowSize() * pitch() - EP_GAP - (1920f - 2 * LEFT));
        return Math.max(0f, Math.min(max, (epAt - 1) * pitch()));
    }

    private float castTarget() {
        float max = Math.max(0f, cast.size() * CELL - (1920f - 2 * LEFT));
        return Math.max(0f, Math.min(max, (castAt - 1) * CELL));
    }

    private float moreTarget() {
        float p = MORE_W + MORE_GAP, max = Math.max(0f, more.size() * p - MORE_GAP - (1920f - 2 * LEFT));
        return Math.max(0f, Math.min(max, (moreAt - 2) * p));
    }

    private float pillLeft(int i) {
        return i < seasonX.length ? seasonX[i] - Ui.f(26f) : 0f;
    }

    private float pillRight(int i) {
        return i < seasonX.length ? seasonX[i] + seasonW[i] + Ui.f(26f) : 0f;
    }

    /** The pills' offset (screen pixels), so the season the remote is in shows. */
    private float pillsTarget() {
        if (seasonX.length == 0) return 0f;
        float room = Ui.f(1920f - 2 * LEFT), end = seasonX[seasonX.length - 1] + seasonW[seasonX.length - 1] + Ui.f(30f);
        if (end <= room) return 0f;
        return Math.max(0f, Math.min(end - room, pillRight(seasonAt) - room * 0.6f));
    }

    // ---- the remote -------------------------------------------------------------------------------------------------

    int part() {
        return part;
    }

    boolean key(int code) {
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
                // The pills follow the row.
                if (!seasons.isEmpty()) seasonAt = seasonOf(epAt);
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
        epX.to(epTarget());
        castX.to(castTarget());
        moreX.to(moreTarget());
        if (seasonWords.length > 0) {
            pillL.to(pillLeft(seasonAt));
            pillR.to(pillRight(seasonAt));
            pillsX.to(pillsTarget());
        }
        nameFocused();
        wash.set(target());
        curtain.set(target());
        invalidate();
    }

    /** The name the episode the remote is on shows, cut to its tile (made once for each episode it comes to). */
    private void nameFocused() {
        if (part != EPISODES || epAt >= epNames.length || epNames[epAt] != null) return;
        Catalog.Episode e = episode(epAt);
        epNames[epAt] = e == null ? "Trailer" : Ui.fit(e.title.isEmpty() ? "Episode " + e.number : e.title, epName, Ui.f(EP_W) - Ui.f(36f));
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
                if (e == null) host.trailer();
                else host.play(e);
                break;
            }
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

    /** The remote back from the top bar. */
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
        } else if (p == CAST) castAt = along;
        else moreAt = along;
        settle();
    }

    /** How far the page has risen (the top bar fills with its colour as it does). */
    float risen() {
        return page.value();
    }

    int colour() {
        return wash.colour();
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        wash.clear();
        float W = w, H = Ui.f(1080f);
        // The picture's left into the page's colour, and its foot.
        wash.add(0, 0, Ui.f(1320f), H, 0, 0, Ui.f(1320f), 0, 0.9f, 0.5f, 0.42f, 0f);
        wash.add(0, Ui.f(360f), W, H + 2, 0, Ui.f(360f), 0, H, 0f, 0.62f, 0.55f, 1f);
        // The rows' tiles fading away into the page's colour as they slide out past its left edge.
        curtain.clear();
        curtain.add(0, 0, Ui.f(LEFT), h, 0, 0, Ui.f(LEFT), 0, 1f, 0.8f, 0.45f, 0f);
        tileFoot.setShader(new LinearGradient(0, Ui.f(EP_H * 0.45f), 0, Ui.f(EP_H), 0x00000000, 0xC7000000, Shader.TileMode.CLAMP));
        shade.setShader(new LinearGradient(0, Ui.f(MORE_H * 0.55f), 0, Ui.f(MORE_H), 0x00000000, 0xB3000000, Shader.TileMode.CLAMP));
        if (title != null) {
            layoutWords();
            colours();
            wash.jump(target());
            curtain.jump(target());
        }
    }

    @Override protected void onDraw(Canvas c) {
        if (title == null) return;
        boolean moving = page.step();
        moving |= epX.step();
        moving |= castX.step();
        moving |= moreX.step();
        moving |= pillL.step();
        moving |= pillR.step();
        moving |= pillsX.step();
        moving |= wash.step();
        moving |= curtain.step();
        Object focus = part == EPISODES ? episodeKey(epAt) : part == MORE && moreAt < more.size() ? more.get(moreAt) : null;
        moving |= lift.step(focus);

        int w = getWidth();
        float risen = Ui.f(page.value());
        ground.setColor(wash.colour());
        if (bar != null) bar.setBacking(wash.colour(), Math.min(1f, page.value() / 260f));
        c.drawRect(0, 0, w, getHeight(), ground);

        // The picture, rising at half the page's speed, its left and foot in the page's colour.
        float heroY = -risen * 0.5f;
        if (heroY > -Ui.f(1080f)) {
            Bitmap b = Pics.get(backdrop, 1280, 720, Pics.CROP, redraw);
            c.save();
            c.translate(0, heroY);
            if (b != null) {
                // Going as the page rises, so what it's about and the cast sit on the page's colour.
                hero.setAlpha(Math.round(255 * (0.16f + 0.84f * Math.max(0f, 1f - page.value() / 520f))));
                r.set(0, 0, w, Ui.f(1080f));
                c.drawBitmap(b, null, r, hero);
            }
            wash.draw(c);
            c.restore();
        }

        c.save();
        c.translate(0, -risen);
        drawTop(c);
        if (!seasons.isEmpty()) drawSeasons(c);
        if (hasPart(EPISODES)) drawEpisodes(c);
        drawAbout(c);
        if (hasPart(CAST)) drawCast(c);
        if (hasPart(MORE)) drawMore(c);
        c.restore();
        curtain.draw(c);
        if (moving) postInvalidateOnAnimation();
    }

    private Object episodeKey(int i) {
        Catalog.Episode e = episode(i);
        return e == null ? this : e;
    }

    private void drawTop(Canvas c) {
        float x = Ui.f(LEFT);
        lettering.draw(c, x, Ui.f(LOGO_TOP), 255, redraw);
        // The facts: rating, years, age, seasons or length, picture and sound.
        facts.draw(c, x, Ui.f(FACTS_Y), 255);
        // The buttons: the red pill, then grey ones; the remote's lit (the red brighter in a white ring, grey turned white).
        float bx = Ui.f(LEFT), top = Ui.f(BUTTONS_TOP), h = Ui.f(BUTTON_H);
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            float bw = buttonW.get(i);
            boolean on = part == BUTTONS && i == buttonAt;
            r.set(bx, top, bx + bw, top + h);
            Paint fill = b.main ? red : on ? white : grey;
            if (b.main) red.setColor(on ? Cine.RED_LIT : Cine.RED);
            c.drawRoundRect(r, Ui.f(8f), Ui.f(8f), fill);
            if (on && b.main) {
                r.inset(-Ui.f(5f), -Ui.f(5f));
                c.drawRoundRect(r, Ui.f(12f), Ui.f(12f), ring);
            }
            int ink = on && !b.main ? Cine.INK : Cine.TEXT;
            float tx = bx + Ui.f(36f), cy = top + h / 2f;
            if (b.icon >= 0) {
                icon(c, b.icon, tx + Ui.f(14f), cy, ink);
                tx += Ui.f(46f);
            }
            c.drawText(b.label, tx, Cine.mid(buttonP, cy), on && !b.main ? buttonInk : buttonP);
            bx += bw + Ui.f(22f);
        }
    }

    private void icon(Canvas c, int kind, float cx, float cy, int colour) {
        iconStroke.setColor(colour);
        iconFill.setColor(colour);
        float s = Ui.f(30f);
        switch (kind) {
            case PLAY:
                Cine.play(c, cx, cy, s, iconFill);
                break;
            case PLUS:
                Cine.plus(c, cx, cy, s * 0.9f, iconStroke);
                break;
            case TICK:
                Cine.tick(c, cx, cy, s * 0.9f, iconStroke);
                break;
            case RESTART:
                RoundButton.glyph(c, Dark.RESTART, cx, cy, s * 1.1f, colour);
                break;
            case TRAILER:
                RoundButton.glyph(c, Dark.VIDEO, cx, cy, s * 1.1f, colour);
                break;
            default:
                RoundButton.glyph(c, Dark.BIN, cx, cy, s * 1.1f, colour);
                break;
        }
    }

    /** The seasons, as the phone has them: the white pill behind the season the remote is in. */
    private void drawSeasons(Canvas c) {
        float x0 = Ui.f(LEFT) - pillsX.value(), cy = Ui.f(PILLS_Y), h = Ui.f(PILL_H);
        r.set(x0 + pillL.value(), cy - h / 2f, x0 + pillR.value(), cy + h / 2f);
        c.drawRoundRect(r, h / 2f, h / 2f, white);
        if (part == SEASONS) {
            r.inset(-Ui.f(6f), -Ui.f(6f));
            c.drawRoundRect(r, h / 2f + Ui.f(6f), h / 2f + Ui.f(6f), ring);
        }
        float base = Cine.mid(pillP, cy);
        for (int i = 0; i < seasonWords.length; i++) {
            float x = x0 + seasonX[i];
            if (x > getWidth() || x + seasonW[i] < 0) continue;
            // Ink where the pill is under the word, white elsewhere.
            boolean under = x0 + pillL.value() < x + seasonW[i] && x0 + pillR.value() > x;
            c.drawText(seasonWords[i], x, base, under ? pillInk : pillP);
        }
    }

    private void drawEpisodes(Canvas c) {
        int n = rowSize();
        float pitch = Ui.f(pitch()), w = Ui.f(EP_W), h = Ui.f(EP_H), top = Ui.f(EP_TOP), x0 = Ui.f(LEFT) - Ui.f(epX.value());
        int first = Math.max(0, (int) Math.floor((Ui.f(epX.value()) - Ui.f(LEFT)) / pitch) - 1);
        int last = Math.min(n - 1, first + (int) Math.ceil(getWidth() / pitch) + 2);
        Object leaving = lift.leaving();
        int away = -1;
        for (int i = first; i <= last; i++) {
            Object k = episodeKey(i);
            if (part == EPISODES && i == epAt) continue;
            if (k == leaving) {
                away = i;
                continue;
            }
            tile(c, i, x0 + i * pitch, top, w, h, 0f);
        }
        // The one settling back, then the remote's, over their neighbours.
        if (away >= 0) tile(c, away, x0 + away * pitch, top, w, h, lift.of(leaving));
        if (part == EPISODES && epAt >= first && epAt <= last) tile(c, epAt, x0 + epAt * pitch, top, w, h, lift.of(episodeKey(epAt)));
    }

    private void tile(Canvas c, int i, float x, float y, float w, float h, float up) {
        Catalog.Episode e = episode(i);
        float k = 1f + (GROW - 1f) * up;
        c.save();
        c.translate(x + w / 2f, y + h / 2f);
        c.scale(k, k);
        c.translate(-w / 2f, -h / 2f);
        String pic = e == null || e.still.isEmpty() ? backdrop : e.still;
        Bitmap b = Pics.get(pic, Math.round(w * GROW), Math.round(h * GROW), Pics.CROP, redraw);
        r.set(0, 0, w, h);
        if (b != null) c.drawBitmap(b, null, r, bmp);
        else c.drawRect(r, place);
        c.drawRect(r, tileFoot);
        boolean named = up > 0.5f && part == EPISODES;
        String label = named && i < epNames.length && epNames[i] != null ? epNames[i] : i < epNos.length ? epNos[i] : "";
        if (e == null) {
            iconFill.setColor(Cine.TEXT);
            Cine.play(c, Ui.f(34f), h - Ui.f(30f), Ui.f(24f), iconFill);
            c.drawText(label, Ui.f(58f), h - Ui.f(18f), epNo);
        } else {
            c.drawText(label, Ui.f(18f), h - Ui.f(18f), named ? epName : epNo);
        }
        float p = e == null ? -1f : host.progress(e);
        if (p >= 0f) {
            float bh = Ui.f(5f);
            c.drawRect(0, h - bh, w, h, track);
            c.drawRect(0, h - bh, w * Math.max(0.02f, p), h, red);
        }
        float bx = Ui.f(14f);
        if (e != null && e.torbox) {
            SourcePicker.badge(c, bx + Ui.f(16f), Ui.f(26f), Ui.f(32f), 255);
            bx += Ui.f(42f);
        }
        if (e != null && e.id.equals(resumeId)) {
            String up1 = "UP NEXT";
            float bw = badgeP.measureText(up1) + Ui.f(18f);
            r.set(bx, Ui.f(12f), bx + bw, Ui.f(40f));
            c.drawRoundRect(r, Ui.f(5f), Ui.f(5f), white);
            c.drawText(up1, bx + Ui.f(9f), Cine.mid(badgeP, r.centerY()), badgeP);
        }
        if (up > 0.02f) {
            ring.setAlpha(Math.round(255 * Math.min(1f, up)));
            r.set(0, 0, w, h);
            c.drawRect(r, ring);
            ring.setAlpha(255);
        }
        c.restore();
    }

    private void drawAbout(Canvas c) {
        float top = Ui.f(overviewTop()), x = Ui.f(LEFT);
        if (top - Ui.f(page.value()) > getHeight()) return;
        c.drawText("Overview", x, top + Ui.f(34f), headP);
        if (overview != null) Ui.draw(c, overview, x, top + Ui.f(HEAD));
        float gx = Ui.f(1340f);
        if (!genres.isEmpty()) {
            c.drawText("Genres", gx, top + Ui.f(34f), headP);
            float gy = top + Ui.f(HEAD) - genreP.ascent();
            for (String g : genres) {
                c.drawText(g, gx, gy, genreP);
                gy += Ui.f(38f);
            }
        }
    }

    private void drawCast(Canvas c) {
        float top = Ui.f(castTop()), x0 = Ui.f(LEFT) - Ui.f(castX.value());
        if (top - Ui.f(page.value()) > getHeight()) return;
        c.drawText("Cast", Ui.f(LEFT), top + Ui.f(34f), headP);
        float d = Ui.f(FACE), cy = top + Ui.f(HEAD) + d / 2f, cell = Ui.f(CELL);
        for (int i = 0; i < cast.size(); i++) {
            float cx = x0 + i * cell + d / 2f;
            if (cx + cell < 0 || cx - d > getWidth()) continue;
            String[] who = cast.get(i);
            Paint face = faces[i];
            if (face == null && !who[2].isEmpty()) face = face(i, who[2], Math.round(d));
            if (face != null) {
                c.save();
                c.translate(cx - d / 2f, cy - d / 2f);
                c.drawCircle(d / 2f, d / 2f, d / 2f, face);
                c.restore();
            } else {
                place.setColor(castColours[i]);
                c.drawCircle(cx, cy, d / 2f, place);
                place.setColor(0xFF2A2C33);
                float iw = initialsP.measureText(castInitials[i]);
                c.drawText(castInitials[i], cx - iw / 2f, Cine.mid(initialsP, cy), initialsP);
            }
            boolean on = part == CAST && i == castAt;
            if (on) c.drawCircle(cx, cy, d / 2f + Ui.f(6f), ring);
            float tx = cx + d / 2f + Ui.f(22f);
            nameP.setColor(on ? Cine.TEXT : Cine.SOFT);
            c.drawText(castNames[i], tx, castParts[i].isEmpty() ? Cine.mid(nameP, cy) : cy - Ui.f(4f), nameP);
            if (!castParts[i].isEmpty()) c.drawText(castParts[i], tx, cy + Ui.f(30f), partP);
        }
        nameP.setColor(Cine.TEXT);
    }

    /** A cast member's photo as a round face: its shader made once, when the photo has been read. */
    private Paint face(int i, String url, int d) {
        Bitmap b = Pics.get(url, d, d, Pics.CROP, redraw);
        if (b == null) return null;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(d / (float) b.getWidth(), d / (float) b.getHeight());
        s.setLocalMatrix(m);
        p.setShader(s);
        faces[i] = p;
        return p;
    }

    private void drawMore(Canvas c) {
        float top = Ui.f(moreTop());
        if (top - Ui.f(page.value()) > getHeight()) return;
        c.drawText("More like this", Ui.f(LEFT), top + Ui.f(34f), headP);
        float w = Ui.f(MORE_W), h = Ui.f(MORE_H), pitch = Ui.f(MORE_W + MORE_GAP), y = top + Ui.f(HEAD), x0 = Ui.f(LEFT) - Ui.f(moreX.value());
        Object leaving = lift.leaving();
        int away = -1;
        for (int i = 0; i < more.size(); i++) {
            float x = x0 + i * pitch;
            if (x + w < 0 || x > getWidth()) continue;
            if (part == MORE && i == moreAt) continue;
            if (more.get(i) == leaving) {
                away = i;
                continue;
            }
            poster(c, i, x, y, w, h, 0f);
        }
        if (away >= 0) poster(c, away, x0 + away * pitch, y, w, h, lift.of(leaving));
        if (part == MORE && moreAt < more.size()) poster(c, moreAt, x0 + moreAt * pitch, y, w, h, lift.of(more.get(moreAt)));
    }

    private void poster(Canvas c, int i, float x, float y, float w, float h, float up) {
        Catalog.Title t = more.get(i);
        float k = 1f + (GROW - 1f) * up;
        c.save();
        c.translate(x + w / 2f, y + h / 2f);
        c.scale(k, k);
        c.translate(-w / 2f, -h / 2f);
        r.set(0, 0, w, h);
        Bitmap b = Pics.get(t.poster, Math.round(w * GROW), Math.round(h * GROW), Pics.CROP, redraw);
        if (b != null) {
            c.drawBitmap(b, null, r, bmp);
        } else {
            c.drawRect(r, place);
            c.drawRect(r, shade);
            c.drawText(moreNames[i], Ui.f(14f), h - Ui.f(20f), moreName);
        }
        if (t.torbox()) SourcePicker.badge(c, Ui.f(30f), Ui.f(30f), Ui.f(34f), 255);
        if (up > 0.02f) {
            ring.setAlpha(Math.round(255 * Math.min(1f, up)));
            c.drawRect(r, ring);
            ring.setAlpha(255);
        }
        c.restore();
    }

    // ---- pure, for the tests ----------------------------------------------------------------------------------------

    /** Pure: an episode's facts under its name: "1h 7m, 9 Oct 2022", "54m", "9 Oct 2022" or "". */
    static String episodeFacts(int minutes, String aired) {
        String len = Ui.duration(minutes);
        String day = "";
        if (aired != null && aired.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                java.time.LocalDate d = java.time.LocalDate.parse(aired);
                day = d.getDayOfMonth() + " " + d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK) + " " + d.getYear();
            } catch (RuntimeException ignored) {
                day = "";
            }
        }
        if (len.isEmpty()) return day;
        return day.isEmpty() ? len : len + ", " + day;
    }
}
