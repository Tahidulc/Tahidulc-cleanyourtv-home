package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Formats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cover B, the family's chosen page for a film or a series ("use B and use a pop in animation when I go onto this page
 * for the poster. The background colour should depend on the background colour I want to use in the settings"): the
 * first screen of a title's page (TitleScreen, under TitlePage's series section and More like this), and the cover an
 * episode chosen opens on before it plays (VodActivity):
 *
 * <pre>
 *   the page        Settings › Colour theme's ground (Palette.GROUND; darkened if a lighter one would make the words hard
 *                   to read), a soft glow of the poster's own colour behind it and the theme's lighter material at the
 *                   foot right, from light at the top left to darker at the foot right; it stays as the page scrolls
 *   the poster      520 x 780 at (140, 140), rounded 26, a big soft shadow and a glow in its colours (a series' own
 *                   poster for an episode); a dark card with its name until the picture comes, which then fades in
 *   on the right    from x 760: the title as its own logo ("the title for movie and TV show should be logo for each
 *                   movie or tv show": TMDB's, in the family's language, else English, else one with no words; fitted
 *                   in 760 x 220, at its foot's left, drawn at its own pixels), the title in words (74 px extra-bold)
 *                   while it comes or without one; an episode's show's logo with "S2 E4 · Fog" under it (in words alone
 *                   without a logo); the rating ★, year, length (a series' seasons) and certificate, and at that line's
 *                   right the page's buttons (pills: Trailer, My List, More like this, Remove; on a line of their own
 *                   when they don't fit beside it); the story (the episode's own), the cast (round photos with names;
 *                   their initials on a tint without one)
 *   the rows        70 high, rounded 20, the remote's white with dark words and lifted: first Resume (or Play, a series'
 *                   episode: the theme's accent tile with a play shape, "S2 E5 · 23 min left" or "1h 12m left", the
 *                   copy it carries on from under it, how far it was watched at the right) and From the start (a
 *                   restart shape, its length), then PLAY FROM: every source, TorBox's each file of it: TorBox's green
 *                   tile with its cloud or the playlists' blue tile with a television, the picture (4K, 1080p, 720p,
 *                   SD), where it is from with its HDR (Dolby Vision, HDR10+, HDR10, HLG, SDR), its sound (Dolby
 *                   Atmos, TrueHD 7.1, DTS-HD MA 7.1, Dolby Digital+ 5.1, 5.1, Stereo), codec and release, its file's
 *                   name under it (a series pack's episode file), its size (58.2 GB, or ≈ 4.1 GB worked out from its
 *                   bitrate), the best first marked BEST; a long list scrolls as one (PLAY FROM at the top once
 *                   Resume and From the start are past); a series' page has its two rows and no sources
 * </pre>
 *
 * As it opens the poster pops in (from 0.85 and clear, past 1.03 and back to 1 in 450 ms) with its glow blooming after
 * it, then the words and the rows slide in from the right one after another, 40 ms apart; on the Lite motion tier
 * (Motion.lite) everything fades and grows a little at once. The remote starts on the first row (Resume where it was
 * left part-way, else the best source): Up and Down go along the rows, Up from the first to the buttons (Left and Right
 * along them), Up again and Down past the last row to the host (the title page's tab bar and what is under the cover).
 * OK on Resume carries on from where it was left, on From the start from its start, on a source from where it was left
 * (each choice's copy first, the rest kept as its backups). A frame makes nothing: the words are laid out (and cut to
 * fit) when they are given, the shades when the poster's colour is known, and only opacities and places move.
 */
final class CoverPage extends View {
    interface Host {
        /** OK on a source: its place in the list given, from where it was left. */
        void play(int index);

        /** OK on one of the rows above the sources (its place: Resume or Play, then From the start). */
        void action(int index);

        /** Back (the player's cover; the title page takes Back itself). */
        default void back() {}

        /** OK on one of the buttons beside the facts (its place). */
        default void button(int index) {}

        /** Up past the buttons (or the first row without any): the title page's tab bar. */
        default void up() {}

        /** Down past the last row: what is under the cover on the title page. */
        default void down() {}
    }

    /**
     * A row above the sources: Resume or Play (the theme's accent tile with a play shape), or From the start (restart: a
     * restart shape); its name, its words, and how far it was watched (0: no bar).
     */
    static final class Action {
        final String name, words;
        final float watched;
        final boolean restart;
        float nameW = -1f;

        Action(String name, String words, float watched, boolean restart) {
            this.name = name == null ? "" : name;
            this.words = words == null ? "" : words;
            this.watched = Math.max(0f, Math.min(1f, watched));
            this.restart = restart;
        }
    }

    /** A button beside the facts: its icon (Icons07) and its words. */
    static final class Pill {
        final int icon;
        final String words;

        Pill(int icon, String words) {
            this.icon = icon;
            this.words = words == null ? "" : words;
        }
    }

    static final String RESUME_NAME = "Resume", PLAY_NAME = "Play", FROM_START_NAME = "From the start";

    static final float POSTER_X = 140f, POSTER_Y = 140f, POSTER_W = 520f, POSTER_H = 780f, POSTER_R = 26f;
    static final float TEXT_X = 760f, TEXT_TOP = 150f, TEXT_W = 1040f, GAP = 20f, ROW_H = 70f, ROW_GAP = 10f, ROW_R = 20f,
            FOOT = 1030f, FACE = 84f, FACE_W = 110f, FACE_GAP = 26f;
    /** The buttons beside the facts: their height, padding, icon, the gap after it, the gap between them. */
    static final float PILL_H = 44f, PILL_PAD = 18f, PILL_ICON = 20f, PILL_ICON_GAP = 8f, PILLS_GAP = 12f;
    /** The poster's pop (ms), the words' stagger and slide, and Lite's one fade. */
    static final long POP_MS = 450, WORDS_FROM = 300, STAGGER = 40, WORDS_MS = 320, LITE_MS = 250, GLOW_FROM = 250, GLOW_MS = 450,
            ART_MS = 300;
    static final float SLIDE = 48f;
    /** The logo's box (design px): it is fitted inside, at its foot's left. */
    static final float LOGO_W = 760f, LOGO_H = 220f;
    /** The poster's shadow: 0 40 100 black 70 %. */
    static final float[] SHADOW = {40f, 100f, 0f, 178f};
    /**
     * The title page's trailer card: the poster widens into it as its trailer plays (16:9, 640 x 360 on the poster's
     * middle, 40 short of the words), rounded as the trailer's window, and back as it stops; WIDEN_MS each way.
     */
    static final float CARD_W = 640f, CARD_H = 360f, CARD_X = POSTER_X + POSTER_W / 2f - CARD_W / 2f,
            CARD_Y = POSTER_Y + POSTER_H / 2f - CARD_H / 2f;
    static final long WIDEN_MS = 320;
    /** How far the page under it rises (design px) before everything on it but the ground has faded away. */
    static final float FADE_SCROLL = 400f;
    /** Previews: the page as it is this many ms after opening (-1: settled, as tests see it). */
    static long previewElapsed = -1;

    private final Host host;
    private boolean open;
    private long openedAt, posterAt;
    // What it shows.
    private String title = "", plot = "", posterUrl = "", rating = "", facts = "", certificate = "", status = "";
    /** TMDB's logo for the title (a series' for an episode; "" for none), the show's name, an episode's line under it. */
    private String logoUrl = "", show = "", episodeLine = "";
    private long logoAt;
    private StaticLayout episodeL;
    private float titleBox;
    private final List<Catalog.Play> plays = new ArrayList<>();
    private final List<String[]> rows = new ArrayList<>();
    private final List<String[]> people = new ArrayList<>();
    /** The rows above the sources, and the copy Resume carries on from (said under the first). */
    private final List<Action> actions = new ArrayList<>();
    private String resumeCopy = "";
    /** The buttons beside the facts, and each one's width (screen px, made as they are given). */
    private final List<Pill> pills = new ArrayList<>();
    private float[] pillW = new float[0];
    /**
     * The remote's row and the first in sight, counted down the whole list (the rows above the sources, then the
     * sources); -1: on the buttons, the one at pillAt.
     */
    private int focus, first, pillAt;
    /** Whether the remote is on the cover (false: up on the tab bar, or down the title page under it: nothing lit). */
    private boolean active = true;
    /** Whether it lists sources (a series' page doesn't: its episodes have theirs). */
    private boolean withSources = true;
    /** How far the page under it has scrolled (design px): everything but the ground goes up with it. */
    private float scroll;
    /** The poster widened into the trailer's card (0: the poster, 1: the card with the trailer in it). */
    private final Glide wide = new Glide(WIDEN_MS, CARD_W);
    private final RectF posterBox = new RectF(), cardBox = new RectF();
    // Laid out when given.
    private StaticLayout titleL, plotL, posterNameL;
    private float factsY, plotY, castY, labelY, rowsY, actionsY, pillsY;
    /** Whether the buttons are at the facts' line's right (else on a line of their own under it). */
    private boolean pillsBeside = true;
    private boolean laid;
    // Colours: the page's ground and its glows.
    private int base = Palette.GROUND, glow = 0xFF8A5A2E, glowFor = 0;
    private final Paint ground = new Paint(), glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG), footGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint posterGlow = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint art = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleP = Type.paint(74f, Type.EXTRABOLD, Look07.L1), factsP = Type.paint(21f, Type.REGULAR, 0xCCFFFFFF);
    private final TextPaint ratingP = Type.paint(21f, Type.BOLD, 0xFFFFD166), certP = Type.paint(16f, Type.BOLD, 0xCCFFFFFF);
    private final TextPaint plotP = Type.paint(22f, Type.REGULAR, 0xBDFFFFFF), faceP = Type.paint(26f, Type.BOLD, 0xE6FFFFFF);
    private final TextPaint nameP = Type.paint(16f, Type.SEMIBOLD, Look07.L1), labelP = Type.label(16f, 0x80FFFFFF);
    private final TextPaint qualityP = Type.paint(22f, Type.BOLD, Look07.L1), pillP = Type.paint(17f, Type.SEMIBOLD, Look07.L1);
    private final TextPaint whatP = Type.paint(19f, Type.REGULAR, Look07.L1), sizeP = Type.paint(19f, Type.SEMIBOLD, Look07.L1);
    private final TextPaint bestP = Type.paint(15f, Type.EXTRABOLD, Look07.L1), statusP = Type.paint(21f, Type.MEDIUM, Look07.L2);
    private final TextPaint posterNameP = Type.paint(40f, Type.BOLD, Look07.L2), episodeP = Type.paint(36f, Type.BOLD, Look07.L1);
    private final TextPaint fileP = Type.paint(15f, Type.MEDIUM, Look07.L1), moreP = Type.label(15f, 0x80FFFFFF);
    private final Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG), glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final Matrix m = new Matrix();
    private float shadesFor = -1f;
    private final Runnable redraw = this::invalidate;

    CoverPage(Context c, Host host) {
        super(c);
        this.host = host;
        titleP.setLetterSpacing(-0.02f);
        bestP.setLetterSpacing(0.06f);
        Shadows.prepare(SHADOW, POSTER_R);
        Shadows.prepare(Look07.SH_ROW, ROW_R);
        Shadows.prepare(Look07.SH_ROW, PILL_H / 2f);
        line.setStyle(Paint.Style.STROKE);
        glyph.setStyle(Paint.Style.STROKE);
        glyph.setStrokeCap(Paint.Cap.ROUND);
        glyph.setStrokeJoin(Paint.Join.ROUND);
        setVisibility(GONE);
    }

    boolean isOpen() {
        return open;
    }

    // ---- what it shows -------------------------------------------------------------------------------------------------

    /**
     * Opened (or its words made again, if already open) for a film or a series (episode null) or an episode: its poster
     * (the series' for an episode), title, facts and story. The pop starts only as it opens.
     */
    void show(Catalog.Title t, Catalog.Episode e, Catalog.Details d) {
        if (t == null) return;
        title = e == null ? t.name : "S" + e.season + " E" + e.number + (e.title.isEmpty() ? "" : "  ·  " + e.title);
        show = t.name;
        episodeLine = e == null ? "" : title;
        String poster = d != null && !d.poster.isEmpty() ? d.poster : t.poster;
        if (!poster.equals(posterUrl)) {
            posterUrl = poster;
            posterAt = 0;
        }
        rating = d != null && !d.rating.isEmpty() ? d.rating : t.rating;
        facts = facts(t, e, d);
        certificate = d == null ? "" : d.age;
        plot = e != null && !e.plot.isEmpty() ? e.plot : d != null && !d.plot.isEmpty() ? d.plot : t.plot;
        if (people.isEmpty() && d != null && !d.cast.isEmpty()) {
            List<String[]> names = new ArrayList<>();
            for (String n : d.cast.split(",")) if (!n.trim().isEmpty() && names.size() < 6) names.add(new String[]{n.trim(), ""});
            cast(names);
        }
        laid = false;
        if (!open) {
            open = true;
            openedAt = SystemClock.uptimeMillis();
            base = base(Palette.GROUND);
            shadesFor = -1f;
            setVisibility(VISIBLE);
        }
        invalidate();
    }

    /**
     * Pure: the line under the title: the year (an episode's day), its length (a series' seasons); the rating and
     * certificate drawn apart.
     */
    static String facts(Catalog.Title t, Catalog.Episode e, Catalog.Details d) {
        List<String> f = new ArrayList<>();
        String year = e != null && e.aired.length() >= 4 ? e.aired.substring(0, 4)
                : d != null && d.released.length() >= 4 ? d.released.substring(0, 4) : t.year;
        if (year != null && !year.isEmpty()) f.add(year);
        int minutes = e != null ? e.minutes : d != null && !t.series ? d.minutes : 0;
        if (minutes > 0) f.add(Ui.duration(minutes));
        if (e == null && t.series && d != null) {
            int n = 0;
            for (Catalog.Season s : d.seasons) if (!s.episodes.isEmpty()) n++;
            if (n > 0) f.add(n == 1 ? "1 season" : n + " seasons");
        }
        return TextUtils.join("  ·  ", f);
    }

    /** The cast: {name, photo ("" for none)}, six at most. */
    void cast(List<String[]> who) {
        people.clear();
        for (String[] p : who) {
            if (people.size() >= 6 || p == null || p.length == 0 || p[0] == null || p[0].trim().isEmpty()) continue;
            String name = p[0].trim();
            people.add(new String[]{name, p.length > 1 && p[1] != null ? p[1] : "", Dark.initials(name), Ui.fit(name, nameP, Ui.f(FACE_W))});
        }
        laid = false;
        invalidate();
    }

    /** TMDB's logo for the title (a series' for an episode), "" for none: the words stay until it comes. */
    void logo(String url) {
        String u = url == null ? "" : url;
        if (u.equals(logoUrl)) return;
        logoUrl = u;
        logoAt = 0;
        laid = false;
        invalidate();
    }

    /** Whether the logo is drawn in the title's place (false: the title in words). */
    boolean showsLogo() {
        return logoBitmap() != null;
    }

    private Bitmap logoBitmap() {
        return logoUrl.isEmpty() ? null : Pics.get(logoUrl, Math.round(Ui.f(LOGO_W)), Math.round(Ui.f(LOGO_H)), Pics.FIT, redraw);
    }

    /** The sources listed. */
    int rowCount() {
        return rows.size();
    }

    /** The first row in sight and how many are, counted down the whole list (the rows above the sources first). */
    int firstShown() {
        return first;
    }

    int rowsShown() {
        if (!laid) layOut();
        return shown();
    }

    /** How many rows are above the sources (Resume or Play, From the start). */
    int actionCount() {
        return actions.size();
    }

    String actionName(int i) {
        return i >= 0 && i < actions.size() ? actions.get(i).name : "";
    }

    String actionWords(int i) {
        return i >= 0 && i < actions.size() ? actions.get(i).words : "";
    }

    /**
     * Every source, the best first: the remote on it, or where it was (Resume, From the start, the buttons, or the same
     * copy where they came again in a new order: their facts read since).
     */
    void sources(List<Catalog.Play> copies) {
        Catalog.Play on = focus > actions.size() && focus - actions.size() < plays.size() ? plays.get(focus - actions.size()) : null;
        plays.clear();
        plays.addAll(copies);
        rows.clear();
        for (int i = 0; i < plays.size(); i++) rows.add(rowWords(plays.get(i), show));
        if (focus >= 0) {
            int was = on == null ? -1 : plays.indexOf(on);
            focus = was >= 0 ? actions.size() + was : Math.min(focus, actions.size());
            first = Math.min(first, focus);
        }
        status = "";
        label = plays.size() > 1 ? "PLAY FROM  ·  " + plays.size() + " SOURCES" : "PLAY FROM";
        fits.clear();
        moreFor = -1;
        laid = false;
        invalidate();
    }

    /** Whether it lists sources (false: a series' page, whose episodes have their own). */
    void withSources(boolean on) {
        withSources = on;
        invalidate();
    }

    /**
     * The rows above the sources: Resume or Play, and From the start (none: not started). The remote goes to the first
     * as they first come (or come again in a different number); given again with the same number, their words alone
     * change.
     */
    void actions(List<Action> list) {
        boolean changed = list.size() != actions.size();
        actions.clear();
        actions.addAll(list);
        for (Action a : actions) a.nameW = qualityP.measureText(a.name);
        if (actions.isEmpty() || actions.get(0).restart) resumeCopy = "";
        if (changed && focus >= 0) {
            focus = 0;
            first = 0;
        }
        actionsFor = -1f;
        moreFor = -1;
        invalidate();
    }

    /** The copy Resume carries on from (its place among the sources), said under it: "On TorBox  ·  4K". */
    void resumeFrom(int index) {
        resumeCopy = index >= 0 && index < plays.size() && !actions.isEmpty() && !actions.get(0).restart ? copyWords(plays.get(index)) : "";
        actionsFor = -1f;
        invalidate();
    }

    String resumeCopyWords() {
        return resumeCopy;
    }

    /** The buttons beside the facts (none: the player's cover). The remote stays on the one it was on. */
    void buttons(List<Pill> list) {
        pills.clear();
        pills.addAll(list);
        pillW = new float[pills.size()];
        for (int i = 0; i < pills.size(); i++) {
            pillW[i] = Ui.f(2 * PILL_PAD + PILL_ICON + PILL_ICON_GAP) + pillP.measureText(pills.get(i).words);
        }
        pillAt = Math.max(0, Math.min(pillAt, pills.size() - 1));
        if (pills.isEmpty() && focus < 0) focus = 0;
        laid = false;
        invalidate();
    }

    int pillCount() {
        return pills.size();
    }

    String pillWords(int i) {
        return i >= 0 && i < pills.size() ? pills.get(i).words : "";
    }

    int pillIcon(int i) {
        return i >= 0 && i < pills.size() ? pills.get(i).icon : -1;
    }

    /** Whether the remote is on the buttons (and which: pillAt). */
    boolean onButtons() {
        return focus < 0;
    }

    int pillAt() {
        return pillAt;
    }

    /** Whether the buttons are at the facts' line's right (else on a line of their own). */
    boolean buttonsBeside() {
        if (!laid) layOut();
        return pillsBeside;
    }

    /** The remote on a row (counted down the whole list), or on a button (row -1, the button's place). */
    void focusRow(int row, int pill) {
        if (row < 0 && !pills.isEmpty()) {
            focus = -1;
            pillAt = Math.max(0, Math.min(pills.size() - 1, pill));
        } else {
            focus = Math.max(0, row);
            first = Math.min(first, focus);
            if (!laid) layOut();
            keepInSight();
        }
        invalidate();
    }

    /** The remote on the cover (true) or elsewhere: up on the tab bar or down the page (nothing lit). */
    void setActive(boolean on) {
        if (on == active) return;
        active = on;
        invalidate();
    }

    boolean active() {
        return active;
    }

    /** How far the page under it has scrolled (design px): all but the ground goes up with it. */
    void scroll(float designPx) {
        if (designPx == scroll) return;
        scroll = designPx;
        invalidate();
    }

    float scrolled() {
        return scroll;
    }

    /**
     * The trailer's window over the card (screen px), once the poster has popped in and the page under it is at its top;
     * false meanwhile (the trailer waits).
     */
    boolean trailerSpot(RectF out) {
        if (!open || scroll != 0f || !Exec.inline && previewElapsed < 0 && elapsed() < POP_MS) return false;
        out.set(Ui.f(CARD_X), Ui.f(CARD_Y), Ui.f(CARD_X + CARD_W), Ui.f(CARD_Y + CARD_H));
        return true;
    }

    /** The trailer plays in the card (the poster widening into it; Lite: at once), or no longer (the poster back). */
    void trailerShown(boolean on) {
        if (Motion.lite()) wide.jump(on ? 1f : 0f);
        else wide.to(on ? 1f : 0f);
        invalidate();
    }

    /** How far the poster has widened into the trailer's card (0 to 1). */
    float widened() {
        return wide.value();
    }

    /** Pure: Resume's words: the episode ("S2 E5", "" for a film), then what is left ("23 min left", "1h 12m left"). */
    static String resumeWords(String episode, long position, long length) {
        String left = length > position && position > 0 ? LiveText.mins(length - position) + " left" : "";
        return join(episode, left);
    }

    /** Pure: From the start's words: the episode and its whole length ("S1 E1  ·  46 min", "2h 5m"). */
    static String startWords(String episode, long length) {
        return join(episode, length > 0 ? LiveText.mins(length) : "");
    }

    private static String join(String a, String b) {
        String x = a == null ? "" : a;
        return x.isEmpty() ? b : b.isEmpty() ? x : x + "  ·  " + b;
    }

    /** Pure: where a copy plays from and its picture: "On TorBox  ·  4K", "On Home  ·  1080p". */
    static String copyWords(Catalog.Play p) {
        String q = Catalog.pictureName(p.picture);
        return "On " + (p.torbox ? "TorBox" : p.playlist.isEmpty() ? "your playlist" : p.playlist) + (q.isEmpty() ? "" : "  ·  " + q);
    }

    /** What it says while there are no sources yet ("Looking in your TorBox…"), or why there are none. */
    void status(String words) {
        status = words == null ? "" : words;
        invalidate();
    }

    void close() {
        open = false;
        setVisibility(GONE);
    }

    /**
     * Pure: a source's row: TorBox's (T) or a playlist's (P), its picture ("4K", "1080p", "720p", "SD"), what it is (where
     * it is from, then Formats.labels: its HDR, sound, codec and release), its size ("58.2 GB", "≈ 4.1 GB" worked out
     * from its bitrate), and the file's name under it (TorBox's: a series pack's episode file; a playlist's only when it
     * says more than the title).
     */
    static String[] rowWords(Catalog.Play p, String titleName) {
        String q = Catalog.pictureName(p.picture);
        List<String> what = new ArrayList<>();
        what.add(p.torbox ? "TorBox" : p.playlist.isEmpty() ? "Playlist" : p.playlist);
        what.addAll(Formats.labels(p.name + " " + p.item, p.hdr, p.sound));
        if (p.kind != null && !p.kind.isEmpty() && q.isEmpty()) what.add(p.kind);
        String file = p.name == null ? "" : p.name.trim();
        if (!p.torbox && file.equalsIgnoreCase(titleName == null ? "" : titleName.trim())) file = "";
        String size = p.size <= 0 ? "" : (p.sizeGuessed ? "≈ " : "") + SourcePicker.size(p.size);
        return new String[]{p.torbox ? "T" : "P", q.isEmpty() ? "Copy" : q, TextUtils.join("  ·  ", what), size, file};
    }

    // ---- the page's colours ----------------------------------------------------------------------------------------------

    /** Pure: WCAG's relative luminance of a colour (0 black to 1 white). */
    static double luminance(int c) {
        return 0.2126 * lin((c >> 16) & 0xFF) + 0.7152 * lin((c >> 8) & 0xFF) + 0.0722 * lin(c & 0xFF);
    }

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** Pure: the contrast between two colours (1 to 21). */
    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** The darkest the page's ground may be lightened to: white words keep 7:1 on it, and 4.5:1 with the glow over it. */
    static final double GROUND_MAX = 0.08, GLOW_MAX = 0.25;
    /** How much of the glow reaches the words at most (the poster's glow fades out where they start). */
    static final float GLOW_AT_WORDS = 0.32f;

    /** Pure: the page's ground from Settings' (Palette.GROUND), darkened until white words read well on it. */
    static int base(int ground) {
        int c = ground | 0xFF000000;
        for (int i = 0; i < 40 && luminance(c) > GROUND_MAX; i++) c = Theme.mix(c, 0xFF000000, 0.12f);
        return c;
    }

    /** Pure: a glow's colour from a picture's: its hue, not too pale or too bright to keep the words readable. */
    static int toned(int colour) {
        float[] h = new float[3];
        Color.colorToHSV(colour | 0xFF000000, h);
        h[1] = Math.max(0.35f, Math.min(0.85f, h[1]));
        h[2] = Math.max(0.35f, Math.min(0.8f, h[2]));
        int c = Color.HSVToColor(h);
        for (int i = 0; i < 40 && luminance(c) > GLOW_MAX; i++) c = Theme.mix(c, 0xFF000000, 0.1f);
        return c;
    }

    /** Pure: where the words begin, the ground with the most of the glow over it (what their contrast is checked on). */
    static int underWords(int base, int glow) {
        return Theme.mix(base, glow | 0xFF000000, GLOW_AT_WORDS);
    }

    /** Pure: a picture's most vivid colour: its pixels weighted by how vivid they are (a plain average without any). */
    static int vivid(int[] pixels) {
        float[] h = new float[3];
        double r = 0, g = 0, b = 0, w = 0, ar = 0, ag = 0, ab = 0;
        for (int p : pixels) {
            int pr = (p >> 16) & 0xFF, pg = (p >> 8) & 0xFF, pb = p & 0xFF;
            ar += pr;
            ag += pg;
            ab += pb;
            Color.colorToHSV(p | 0xFF000000, h);
            double k = h[1] * h[1] * h[2] * (h[2] > 0.2f ? 1 : 0.1);
            r += pr * k;
            g += pg * k;
            b += pb * k;
            w += k;
        }
        if (pixels.length == 0) return 0xFF8A5A2E;
        if (w < 1e-3) return 0xFF000000 | (int) (ar / pixels.length) << 16 | (int) (ag / pixels.length) << 8 | (int) (ab / pixels.length);
        return 0xFF000000 | (int) Math.min(255, r / w) << 16 | (int) Math.min(255, g / w) << 8 | (int) Math.min(255, b / w);
    }

    /** The poster come: its colour read once (a grid of its pixels) for the glows. */
    private void posterCame(Bitmap b) {
        int[] px = new int[12 * 18];
        int n = 0;
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 12; x++) {
                px[n++] = b.getPixel(Math.min(b.getWidth() - 1, (int) ((x + 0.5f) * b.getWidth() / 12f)),
                        Math.min(b.getHeight() - 1, (int) ((y + 0.5f) * b.getHeight() / 18f)));
            }
        }
        glow = toned(vivid(px));
        shadesFor = -1f;
    }

    /** The page's shades, made again only for a new size or colour. */
    private void shades(int w, int h) {
        if (shadesFor == h && glowFor == glow) return;
        shadesFor = h;
        glowFor = glow;
        ground.setShader(new LinearGradient(0, 0, w, h, Theme.mix(base, 0xFFFFFFFF, 0.04f), Theme.mix(base, 0xFF000000, 0.35f),
                Shader.TileMode.CLAMP));
        // The poster's glow: an ellipse 1100 x 700 at a quarter across and two fifths down, its colour at 45 % fading out.
        RadialGradient g = new RadialGradient(0, 0, 1f, new int[]{Palette.alpha(glow, 0x73), Palette.alpha(glow, 0x2E), 0},
                new float[]{0f, 0.4f, 0.7f}, Shader.TileMode.CLAMP);
        m.reset();
        m.setScale(Ui.f(1100f), Ui.f(700f));
        m.postTranslate(Ui.f(480f), Ui.f(432f));
        g.setLocalMatrix(m);
        glowPaint.setShader(g);
        // The theme's lighter material at the foot right (the mock-up's cool light), 900 x 600 at 35 %.
        int cool = toned(Palette.HEADER);
        RadialGradient f = new RadialGradient(0, 0, 1f, new int[]{Palette.alpha(cool, 0x59), 0}, new float[]{0f, 0.7f}, Shader.TileMode.CLAMP);
        m.reset();
        m.setScale(Ui.f(900f), Ui.f(600f));
        m.postTranslate(Ui.f(1632f), Ui.f(972f));
        f.setLocalMatrix(m);
        footGlow.setShader(f);
        // Round the poster itself: its colour bleeding out past its edges.
        posterGlow.setShader(new RadialGradient(Ui.f(POSTER_X + POSTER_W / 2f), Ui.f(POSTER_Y + POSTER_H / 2f), Ui.f(560f),
                new int[]{Palette.alpha(glow, 0x55), Palette.alpha(glow, 0x22), 0}, new float[]{0.55f, 0.8f, 1f}, Shader.TileMode.CLAMP));
    }

    int baseColour() {
        return base;
    }

    int glowColour() {
        return glow;
    }

    // ---- laying out ---------------------------------------------------------------------------------------------------

    private void layOut() {
        laid = true;
        int w = Math.round(Ui.f(TEXT_W));
        // With a logo to come, its box kept (the words at its foot meanwhile, so nothing jumps when it comes); an
        // episode's show by its logo, its episode's line under it.
        boolean logo = !logoUrl.isEmpty();
        titleL = layout(logo && !episodeLine.isEmpty() ? show : title, titleP, w, 2, 1f);
        titleBox = logo ? Math.max(LOGO_H, titleL.getHeight() / Ui.f(1f)) : titleL.getHeight() / Ui.f(1f);
        float y = TEXT_TOP + titleBox;
        episodeL = logo && !episodeLine.isEmpty() ? layout(episodeLine, episodeP, w, 1, 1f) : null;
        if (episodeL != null) y += 14f + episodeL.getHeight() / Ui.f(1f);
        factsY = y + GAP + 24f;
        y = factsY + 8f;
        // The buttons at the facts' right where they fit beside them, else on a line of their own under them.
        float factsW = 0f, pillsW = 0f;
        if (!rating.isEmpty()) factsW += Ui.f(26f) + ratingP.measureText(rating) + Ui.f(16f);
        if (!facts.isEmpty()) factsW += factsP.measureText(facts) + Ui.f(16f);
        if (!certificate.isEmpty()) factsW += certP.measureText(certificate) + Ui.f(18f);
        for (float pw : pillW) pillsW += pw;
        if (pillW.length > 1) pillsW += Ui.f(PILLS_GAP) * (pillW.length - 1);
        pillsBeside = factsW + Ui.f(32f) + pillsW <= Ui.f(TEXT_W);
        pillsY = factsY - 7.5f;
        if (!pills.isEmpty() && !pillsBeside) {
            pillsY = y + GAP + PILL_H / 2f;
            y = pillsY + PILL_H / 2f;
        }
        plotL = plot.isEmpty() ? null : layout(plot, plotP, w, people.isEmpty() ? 5 : 4, 1.24f);
        plotY = y + GAP;
        if (plotL != null) y = plotY + plotL.getHeight() / Ui.f(1f);
        castY = people.isEmpty() ? -1f : y + GAP + 4f;
        if (castY > 0) y = castY + FACE + 8f + 22f;
        labelY = y + GAP + 16f;
        rowsY = labelY + 14f;
        // The rows above the sources at the top of the list, where PLAY FROM's line would start.
        actionsY = labelY - LABEL_CAP;
        posterNameL = layout(title, posterNameP, Math.round(Ui.f(POSTER_W - 80f)), 3, 1f);
    }

    /** A row and the gap under it; PLAY FROM's line's height over its foot; its foot under the last row above it. */
    static final float PITCH = ROW_H + ROW_GAP, LABEL_CAP = 12f, LABEL_UNDER = 28f, LABEL_TO_ROWS = 14f;

    /** The sources counted in the list (none on a series' page). */
    private int sourceRows() {
        return withSources ? rows.size() : 0;
    }

    /**
     * Where row i (counted down the whole list) starts as the list is scrolled now (design px): the rows above the
     * sources from the top, then PLAY FROM's line and the sources under it; once those rows are scrolled past, PLAY FROM's
     * line and the sources in their places as without them.
     */
    private float rowTop(int i) {
        int a = actions.size();
        if (first >= a) return rowsY + (i - first) * PITCH;
        if (i < a) return actionsY + (i - first) * PITCH;
        return labelFoot() + LABEL_TO_ROWS + (i - a) * PITCH;
    }

    /** PLAY FROM's line's foot (design px) as the list is scrolled now. */
    private float labelFoot() {
        int a = actions.size();
        return first >= a ? labelY : actionsY + (a - first) * PITCH - ROW_GAP + LABEL_UNDER;
    }

    /** How many rows are in sight from the first (one at least). */
    private int shown() {
        int n = 0, total = actions.size() + sourceRows();
        for (int i = first; i < total && rowTop(i) + ROW_H <= FOOT; i++) n++;
        return Math.max(1, n);
    }

    /** The list scrolled as little as keeps the remote's row in sight. */
    private void keepInSight() {
        if (focus < first) first = focus;
        while (first < focus && rowTop(focus) + ROW_H > FOOT) first++;
    }

    private static StaticLayout layout(String text, TextPaint p, int w, int lines, float spacing) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), p, Math.max(1, w)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, spacing).build();
    }

    // ---- the pop -------------------------------------------------------------------------------------------------------

    /** How long it has been open (ms): a preview's moment, or settled in tests. */
    private long elapsed() {
        if (previewElapsed >= 0) return previewElapsed;
        if (Exec.inline) return 60_000L;
        return SystemClock.uptimeMillis() - openedAt;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float easeOut(float t) {
        float u = 1f - clamp(t);
        return 1f - u * u * u;
    }

    /** Pure: the poster's size at t ms: 0.85 to 1.03 by 300 ms, back to 1 by 450 (Lite: 0.95 to 1 in 250). */
    static float popScale(long t, boolean lite) {
        if (lite) return 0.95f + 0.05f * easeOut(t / (float) LITE_MS);
        if (t < 300) return 0.85f + 0.18f * easeOut(t / 300f);
        float k = clamp((t - 300) / (float) (POP_MS - 300));
        return 1.03f - 0.03f * k * k * (3f - 2f * k);
    }

    /** Pure: the k-th word or row's share in at t ms (0 out, 1 in): 40 ms after the one before (Lite: all at once). */
    static float wordsIn(long t, int k, boolean lite) {
        if (lite) return clamp(t / (float) LITE_MS);
        return easeOut((t - WORDS_FROM - STAGGER * k) / (float) WORDS_MS);
    }

    /** Whether the pop is over at t ms with n parts sliding in. */
    static boolean settled(long t, int n, boolean lite) {
        return lite ? t >= LITE_MS : t >= Math.max(GLOW_FROM + GLOW_MS, WORDS_FROM + STAGGER * n + WORDS_MS);
    }

    // ---- drawing -------------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        if (!open) return;
        int w = getWidth(), h = getHeight();
        if (!laid) layOut();
        long t = elapsed();
        boolean lite = Motion.lite();
        // The poster first: its colour shades the page.
        Bitmap poster = posterUrl.isEmpty() ? null : Pics.get(posterUrl, Math.round(Ui.f(POSTER_W)), Math.round(Ui.f(POSTER_H)), Pics.CROP, redraw);
        if (poster != null && posterAt == 0) {
            posterAt = SystemClock.uptimeMillis();
            posterCame(poster);
        }
        shades(w, h);
        c.drawRect(0, 0, w, h, ground);
        float bloom = lite ? clamp(t / (float) LITE_MS) : clamp((t - GLOW_FROM) / (float) GLOW_MS);
        glowPaint.setAlpha(Math.round(255 * bloom));
        c.drawRect(0, 0, w, h, glowPaint);
        footGlow.setAlpha(Math.round(255 * clamp(t / 400f)));
        c.drawRect(0, 0, w, h, footGlow);

        // Everything but the ground goes up as the page under it scrolls, fading as it goes (never under the back line).
        float fade = clamp(1f - scroll / FADE_SCROLL);
        int part = 0;
        if (fade > 0f) {
            c.save();
            c.translate(0f, -Ui.f(scroll));
            drawPoster(c, poster, t, lite, bloom * fade, fade);
            drawTitle(c, wordsIn(t, part++, lite) * fade);
            drawFacts(c, wordsIn(t, part++, lite) * fade);
            if (plotL != null) drawPlot(c, wordsIn(t, part++, lite) * fade);
            if (castY > 0) drawCast(c, wordsIn(t, part++, lite) * fade);
            part = drawSources(c, t, lite, part, fade);
            c.restore();
        }
        boolean art = !Exec.inline && (poster != null && SystemClock.uptimeMillis() - posterAt < ART_MS
                || logoAt != 0 && SystemClock.uptimeMillis() - logoAt < ART_MS);
        if (!settled(t, part, lite) && previewElapsed < 0 && !Exec.inline || art || widening) postInvalidateOnAnimation();
    }

    /** Whether the poster is still widening into the trailer's card (or narrowing back) this frame. */
    private boolean widening;

    private void drawPoster(Canvas c, Bitmap b, long t, boolean lite, float bloom, float fade) {
        float x = Ui.f(POSTER_X), y = Ui.f(POSTER_Y), pw = Ui.f(POSTER_W), ph = Ui.f(POSTER_H);
        float shown = (lite ? clamp(t / (float) LITE_MS) : clamp(t / 200f)) * fade, s = popScale(t, lite);
        widening = wide.step();
        // The poster, or the trailer's card it widens into (the window plays over it), or between the two.
        float k = clamp(wide.value());
        posterBox.set(x, y, x + pw, y + ph);
        cardBox.set(Ui.f(CARD_X), Ui.f(CARD_Y), Ui.f(CARD_X + CARD_W), Ui.f(CARD_Y + CARD_H));
        r.set(posterBox.left + (cardBox.left - posterBox.left) * k, posterBox.top + (cardBox.top - posterBox.top) * k,
                posterBox.right + (cardBox.right - posterBox.right) * k, posterBox.bottom + (cardBox.bottom - posterBox.bottom) * k);
        float rad = Ui.f(POSTER_R + (Editorial.CARD_R - POSTER_R) * k);
        c.save();
        c.scale(s, s, x + pw / 2f, y + ph / 2f);
        posterGlow.setAlpha(Math.round(255 * bloom));
        c.drawRect(x - Ui.f(300f), y - Ui.f(300f), x + pw + Ui.f(300f), y + ph + Ui.f(300f), posterGlow);
        Shadows.draw(c, r, rad, SHADOW, shown);
        fill.setColor(Look07.CARD_FRAME);
        fill.setAlpha(Math.round(255 * shown));
        c.drawRoundRect(r, rad, rad, fill);
        shown *= 1f - k;
        if (shown <= 0f) {
            c.restore();
            return;
        }
        if (b == null) {
            // Its name on the dark card until the picture comes.
            posterNameP.setColor(Palette.alpha(Look07.L2, Math.round(0xBD * shown)));
            c.save();
            c.translate(x + Ui.f(40f), y + (ph - posterNameL.getHeight()) / 2f);
            posterNameL.draw(c);
            c.restore();
        } else {
            float in = Exec.inline || posterAt == 0 ? 1f : clamp((SystemClock.uptimeMillis() - posterAt) / (float) ART_MS);
            // Cut to the card as it widens (never squashed), fading as it goes.
            clip.reset();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            art.setAlpha(Math.round(255 * shown * in));
            c.drawBitmap(b, null, posterBox, art);
            c.restore();
        }
        c.restore();
    }

    /** A part sliding in from the right: the canvas moved, its opacity for the paints given back. */
    private int slide(Canvas c, float k) {
        c.save();
        c.translate(Ui.f(SLIDE) * (1f - k), 0f);
        return Math.round(255 * k);
    }

    private void drawTitle(Canvas c, float k) {
        int a = slide(c, k);
        Bitmap logo = logoBitmap();
        if (logo != null && logoAt == 0) logoAt = SystemClock.uptimeMillis();
        float in = logo == null ? 0f : Exec.inline ? 1f : clamp((SystemClock.uptimeMillis() - logoAt) / (float) ART_MS);
        float x = Ui.f(TEXT_X), boxBottom = Ui.f(TEXT_TOP + titleBox);
        if (in < 1f) {
            // The title in words, at the foot of the logo's box, until the logo has come.
            titleP.setColor(Palette.alpha(Look07.L1, Math.round(a * (1f - in))));
            c.save();
            c.translate(x, boxBottom - titleL.getHeight());
            titleL.draw(c);
            c.restore();
        }
        if (logo != null) {
            // At its own pixels (crisp), its foot on the box's foot, at the left.
            art.setAlpha(Math.round(a * in));
            c.drawBitmap(logo, x, boxBottom - logo.getHeight(), art);
        }
        if (episodeL != null) {
            episodeP.setColor(Palette.alpha(Look07.L1, a));
            c.save();
            c.translate(x, boxBottom + Ui.f(14f));
            episodeL.draw(c);
            c.restore();
        }
        c.restore();
    }

    private void drawFacts(Canvas c, float k) {
        int a = slide(c, k);
        float x = Ui.f(TEXT_X), base = Ui.f(factsY), cy = base - factsP.getTextSize() * 0.36f;
        if (!rating.isEmpty()) {
            fill.setColor(Palette.alpha(0xFFFFD166, a));
            Ui.drawStar(c, x + Ui.f(10f), cy, Ui.f(10f), fill);
            ratingP.setColor(Palette.alpha(0xFFFFD166, a));
            c.drawText(rating, x + Ui.f(26f), base, ratingP);
            x += Ui.f(26f) + ratingP.measureText(rating) + Ui.f(16f);
        }
        factsP.setColor(Palette.alpha(0xFFFFFFFF, Math.round(0xCC * k)));
        if (!facts.isEmpty()) {
            c.drawText(facts, x, base, factsP);
            x += factsP.measureText(facts) + Ui.f(16f);
        }
        if (!certificate.isEmpty()) {
            float tw = certP.measureText(certificate);
            r.set(x, cy - Ui.f(14f), x + tw + Ui.f(18f), cy + Ui.f(14f));
            line.setColor(Palette.alpha(0xFFFFFFFF, Math.round(0x8C * k)));
            line.setStrokeWidth(Math.max(1f, Ui.f(1.5f)));
            c.drawRoundRect(r, Ui.f(6f), Ui.f(6f), line);
            certP.setColor(Palette.alpha(0xFFFFFFFF, Math.round(0xE6 * k)));
            c.drawText(certificate, x + Ui.f(9f), Type.centred(certP, cy), certP);
        }
        if (!pills.isEmpty()) drawPills(c, pillsBeside ? cy : Ui.f(pillsY), k, a);
        c.restore();
    }

    /**
     * The buttons, in the rows' look: on a pill each (white at 12 %), its icon and words; the remote's white with dark
     * ones, lifted on its shadow. At the facts' right, else from the left on their own line.
     */
    private void drawPills(Canvas c, float cy, float k, int a) {
        float total = 0f;
        for (float pw : pillW) total += pw;
        total += Ui.f(PILLS_GAP) * Math.max(0, pillW.length - 1);
        float x = pillsBeside ? Ui.f(TEXT_X + TEXT_W) - total : Ui.f(TEXT_X), h = Ui.f(PILL_H);
        for (int i = 0; i < pills.size(); i++) {
            Pill p = pills.get(i);
            boolean lit = active && focus < 0 && i == pillAt;
            r.set(x, cy - h / 2f, x + pillW[i], cy + h / 2f);
            c.save();
            if (lit) {
                c.scale(1.04f, 1.04f, r.centerX(), r.centerY());
                Shadows.draw(c, r, h / 2f, Look07.SH_ROW, k);
                fill.setColor(Palette.alpha(0xFFFFFFFF, a));
            } else {
                fill.setColor(Palette.alpha(0xFFFFFFFF, Math.round(0x1F * k)));
            }
            c.drawRoundRect(r, h / 2f, h / 2f, fill);
            int ink = lit ? 0xFF0D0D10 : 0xFFFFFFFF;
            float ix = x + Ui.f(PILL_PAD), is = Ui.f(PILL_ICON);
            Icons07.draw(c, p.icon, ix + is / 2f, cy, is, Palette.alpha(ink, a));
            pillP.setColor(Palette.alpha(ink, Math.round((lit ? 0xFF : 0xE6) * k)));
            c.drawText(p.words, ix + is + Ui.f(PILL_ICON_GAP), Type.centred(pillP, cy), pillP);
            c.restore();
            x += pillW[i] + Ui.f(PILLS_GAP);
        }
    }

    private void drawPlot(Canvas c, float k) {
        slide(c, k);
        plotP.setColor(Palette.alpha(0xFFFFFFFF, Math.round(0xBD * k)));
        c.translate(Ui.f(TEXT_X), Ui.f(plotY));
        plotL.draw(c);
        c.restore();
    }

    private void drawCast(Canvas c, float k) {
        int a = slide(c, k);
        float cy = Ui.f(castY + FACE / 2f), rad = Ui.f(FACE / 2f);
        for (int i = 0; i < people.size(); i++) {
            String[] p = people.get(i);
            float cx = Ui.f(TEXT_X + i * (FACE_W + FACE_GAP) + FACE_W / 2f);
            Bitmap b = p[1].isEmpty() ? null : Pics.get(p[1], Math.round(Ui.f(FACE)), Math.round(Ui.f(FACE)), Pics.CROP, redraw);
            if (b != null) {
                clip.reset();
                clip.addCircle(cx, cy, rad, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                r.set(cx - rad, cy - rad, cx + rad, cy + rad);
                art.setAlpha(a);
                c.drawBitmap(b, null, r, art);
                c.restore();
            } else {
                // Their initials on a tint of their own.
                fill.setColor(Palette.alpha(Theme.mix(Dark.face(p[0]), 0xFF000000, 0.45f), a));
                c.drawCircle(cx, cy, rad, fill);
                faceP.setColor(Palette.alpha(0xFFFFFFFF, Math.round(0xE6 * k)));
                c.drawText(p[2], cx - faceP.measureText(p[2]) / 2f, Type.centred(faceP, cy), faceP);
            }
            nameP.setColor(Palette.alpha(Look07.L1, a));
            c.drawText(p[3], cx - nameP.measureText(p[3]) / 2f, cy + rad + Ui.f(8f + 16f), nameP);
        }
        c.restore();
    }

    /**
     * The rows above the sources, PLAY FROM and the sources, each row its own part sliding in, as far as the list is in
     * sight; the parts drawn so far.
     */
    private int drawSources(Canvas c, long t, boolean lite, int part, float fade) {
        int a = actions.size(), last = Math.min(a + sourceRows(), first + shown());
        for (int i = first; i < Math.min(a, last); i++) {
            float rk = wordsIn(t, part++, lite) * fade;
            drawAction(c, i, Ui.f(rowTop(i)), rk, Math.round(255 * rk));
        }
        if (!withSources) return part;
        float k = wordsIn(t, part++, lite) * fade, foot = Ui.f(labelFoot());
        slide(c, k);
        labelP.setColor(Palette.alpha(0xFFFFFFFF, Math.round(0x80 * k)));
        c.drawText(label, Ui.f(TEXT_X), foot, labelP);
        String more = moreWords(last);
        if (!more.isEmpty()) {
            // A long list: where it is in it.
            moreP.setColor(Palette.alpha(0xFFFFFFFF, Math.round(0x80 * k)));
            c.drawText(more, Ui.f(TEXT_X + TEXT_W) - moreP.measureText(more), foot, moreP);
        }
        if (rows.isEmpty()) {
            statusP.setColor(Palette.alpha(Look07.L2, Math.round(0xBD * k)));
            String s = status.isEmpty() ? "Finding where it plays from…" : status;
            c.drawText(s, Ui.f(TEXT_X), foot + Ui.f(LABEL_TO_ROWS + 44f), statusP);
        }
        c.restore();
        for (int i = Math.max(first, a); i < last; i++) {
            float rk = wordsIn(t, part++, lite) * fade;
            drawRow(c, i - a, Ui.f(rowTop(i)), rk, Math.round(255 * rk));
        }
        return part;
    }

    /** "PLAY FROM · 14 SOURCES", made when the sources come. */
    private String label = "PLAY FROM", more = "";
    private int moreFor = -1;

    /** "3 ABOVE · 5 MORE BELOW": the sources out of sight (last: the row after the last in sight), made only as it moves. */
    private String moreWords(int last) {
        int key = first * 1000 + last;
        if (key != moreFor) {
            moreFor = key;
            int a = actions.size(), above = Math.max(0, first - a), below = Math.max(0, a + rows.size() - Math.max(last, a));
            more = (above > 0 ? above + " ABOVE" : "") + (above > 0 && below > 0 ? "  ·  " : "") + (below > 0 ? below + " MORE BELOW" : "");
        }
        return more;
    }

    /** The rows' words cut to their room (what, then under), kept until the room or the words change. */
    private final String[] actionFits = new String[4];
    private float actionsFor = -1f;

    private String actionFitted(int row, int part, String words, TextPaint p, float room) {
        if (room != actionsFor) {
            Arrays.fill(actionFits, null);
            actionsFor = room;
        }
        int at = Math.min(1, row) * 2 + part;
        if (actionFits[at] == null) actionFits[at] = Ui.fit(words, p, room);
        return actionFits[at];
    }

    /** The bar at Resume's right of how far it was watched (design px). */
    static final float WATCHED_W = 120f;

    /**
     * A row above the sources, in a source's row's shape and lift: its mark (Resume's and Play's the theme's accent with a
     * play shape, From the start's a quiet tile with a restart one), its name, its words (and, under Resume's, the copy
     * it carries on from), and at Resume's right how far it was watched.
     */
    private void drawAction(Canvas c, int i, float top, float k, int a) {
        Action act = actions.get(i);
        boolean lit = active && i == focus;
        c.save();
        c.translate(Ui.f(SLIDE) * (1f - k), 0f);
        float x = Ui.f(TEXT_X), right = x + Ui.f(TEXT_W), cy = top + Ui.f(ROW_H) / 2f;
        r.set(x, top, right, top + Ui.f(ROW_H));
        if (lit) {
            c.scale(1.02f, 1.02f, r.centerX(), r.centerY());
            Shadows.draw(c, r, Ui.f(ROW_R), Look07.SH_ROW, k);
            fill.setColor(Palette.alpha(0xFFFFFFFF, a));
        } else {
            fill.setColor(Palette.alpha(0xFFFFFFFF, Math.round(0x14 * k)));
        }
        c.drawRoundRect(r, Ui.f(ROW_R), Ui.f(ROW_R), fill);
        int ink = lit ? 0xFF0D0D10 : 0xFFFFFFFF;
        float mx = x + Ui.f(22f), ms = Ui.f(34f);
        box.set(mx, cy - ms / 2f, mx + ms, cy + ms / 2f);
        boolean play = !act.restart;
        tile.setColor(play ? Palette.alpha(Palette.ACCENT, a) : lit ? Palette.alpha(0xFF0D0D10, Math.round(0x1F * k))
                : Palette.alpha(0xFFFFFFFF, Math.round(0x2E * k)));
        c.drawRoundRect(box, ms * 0.29f, ms * 0.29f, tile);
        Icons07.draw(c, play ? Icons07.PLAY : Icons07.RESTART, mx + ms / 2f, cy, ms * (play ? 0.62f : 0.56f),
                Palette.alpha(play ? Palette.ACCENT_INK : ink, a));
        float tx = mx + ms + Ui.f(16f);
        qualityP.setColor(Palette.alpha(ink, a));
        c.drawText(act.name, tx, Type.centred(qualityP, cy), qualityP);
        tx += act.nameW + Ui.f(16f);
        float end = right - Ui.f(22f);
        if (act.watched > 0f) {
            // How far it was watched: white over white (dark over dark on the remote's white row), 6 high.
            float bw = Ui.f(WATCHED_W), bh = Ui.f(6f);
            r.set(end - bw, cy - bh / 2f, end, cy + bh / 2f);
            fill.setColor(lit ? Palette.alpha(0xFF0D0D10, Math.round(0x1F * k)) : Palette.alpha(0xFFFFFFFF, Math.round(0x38 * k)));
            c.drawRoundRect(r, bh / 2f, bh / 2f, fill);
            r.right = r.left + Math.max(bh, bw * act.watched);
            fill.setColor(Palette.alpha(ink, a));
            c.drawRoundRect(r, bh / 2f, bh / 2f, fill);
            end -= bw + Ui.f(16f);
        }
        String under = i == 0 ? resumeCopy : "";
        float room = Math.max(0f, end - tx);
        boolean two = !under.isEmpty();
        if (!act.words.isEmpty()) {
            whatP.setColor(Palette.alpha(ink, Math.round(0xB8 * k)));
            c.drawText(actionFitted(i, 0, act.words, whatP, room), tx, two ? cy - Ui.f(3f) : Type.centred(whatP, cy), whatP);
        }
        if (two) {
            fileP.setColor(Palette.alpha(ink, Math.round(0x8C * k)));
            c.drawText(actionFitted(i, 1, under, fileP, room), tx, act.words.isEmpty() ? Type.centred(fileP, cy) : cy + Ui.f(20f), fileP);
        }
        c.restore();
    }

    /** A source's row (i: its place among the sources). */
    private void drawRow(Canvas c, int i, float top, float k, int a) {
        String[] w = rows.get(i);
        boolean lit = active && actions.size() + i == focus;
        c.save();
        c.translate(Ui.f(SLIDE) * (1f - k), 0f);
        float x = Ui.f(TEXT_X), right = x + Ui.f(TEXT_W), cy = top + Ui.f(ROW_H) / 2f;
        r.set(x, top, right, top + Ui.f(ROW_H));
        if (lit) {
            // The remote's: white, a little larger, lifted on its shadow.
            c.scale(1.02f, 1.02f, r.centerX(), r.centerY());
            Shadows.draw(c, r, Ui.f(ROW_R), Look07.SH_ROW, k);
            fill.setColor(Palette.alpha(0xFFFFFFFF, a));
        } else {
            fill.setColor(Palette.alpha(0xFFFFFFFF, Math.round(0x14 * k)));
        }
        c.drawRoundRect(r, Ui.f(ROW_R), Ui.f(ROW_R), fill);
        int ink = lit ? 0xFF0D0D10 : 0xFFFFFFFF;
        float mx = x + Ui.f(22f), ms = Ui.f(34f);
        mark(c, w[0].equals("T"), mx + ms / 2f, cy, ms, a);
        float tx = mx + ms + Ui.f(16f);
        qualityP.setColor(Palette.alpha(ink, a));
        c.drawText(w[1], tx, Type.centred(qualityP, cy), qualityP);
        tx += Ui.f(120f) + Ui.f(16f);
        // At the right: BEST on the first of several, then the size before it.
        float end = right - Ui.f(22f);
        if (i == 0 && rows.size() > 1) {
            float bw = bestP.measureText("BEST") + Ui.f(24f);
            r.set(end - bw, cy - Ui.f(15f), end, cy + Ui.f(15f));
            fill.setColor(lit ? Palette.alpha(0xFF0D0D10, Math.round(0x1F * k)) : Palette.alpha(0xFFFFFFFF, Math.round(0x1F * k)));
            c.drawRoundRect(r, Ui.f(12f), Ui.f(12f), fill);
            bestP.setColor(Palette.alpha(ink, a));
            c.drawText("BEST", r.left + Ui.f(12f), Type.centred(bestP, cy), bestP);
            end -= bw + Ui.f(16f);
        }
        if (!w[3].isEmpty()) {
            sizeP.setColor(Palette.alpha(ink, Math.round(0xB8 * k)));
            float sw = sizeP.measureText(w[3]);
            c.drawText(w[3], end - sw, Type.centred(sizeP, cy), sizeP);
            end -= sw + Ui.f(16f);
        }
        whatP.setColor(Palette.alpha(ink, Math.round(0xB8 * k)));
        float room = Math.max(0f, end - tx);
        boolean two = !w[4].isEmpty();
        c.drawText(fitted(i, 2, w[2], whatP, room), tx, two ? cy - Ui.f(3f) : Type.centred(whatP, cy), whatP);
        if (two) {
            fileP.setColor(Palette.alpha(ink, Math.round(0x8C * k)));
            c.drawText(fitted(i, 4, w[4], fileP, room), tx, cy + Ui.f(20f), fileP);
        }
        c.restore();
    }

    /** A row's words cut to fit their room, kept until the room or the rows change (no frame cuts them again). */
    private final List<String[]> fits = new ArrayList<>();
    private final List<Float> fitsFor = new ArrayList<>();

    private String fitted(int row, int part, String words, TextPaint p, float room) {
        if (fits.size() != rows.size()) {
            fits.clear();
            fitsFor.clear();
            for (int i = 0; i < rows.size(); i++) {
                fits.add(new String[5]);
                fitsFor.add(-1f);
            }
        }
        String[] f = fits.get(row);
        if (fitsFor.get(row) != room) {
            Arrays.fill(f, null);
            fitsFor.set(row, room);
        }
        if (f[part] == null) f[part] = Ui.fit(words, p, room);
        return f[part];
    }

    /**
     * A source's mark, a tile s across centred on (cx, cy): TorBox's own green with its cloud, or the playlists' blue
     * with a television on its stand and its aerial.
     */
    private void mark(Canvas c, boolean torbox, float cx, float cy, float s, int a) {
        box.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f);
        tile.setColor(Palette.alpha(torbox ? SourcePicker.TORBOX : SourcePicker.IPTV, a));
        c.drawRoundRect(box, s * 0.29f, s * 0.29f, tile);
        if (torbox) {
            glyph.setStyle(Paint.Style.FILL);
            glyph.setColor(Palette.alpha(0xFFFFFFFF, a));
            SourcePicker.Mark.cloud(c, cx, cy + s * 0.02f, s * 0.62f, glyph, r);
            glyph.setStyle(Paint.Style.STROKE);
            return;
        }
        glyph.setColor(Palette.alpha(0xFFFFFFFF, a));
        glyph.setStrokeWidth(Math.max(1f, s * 0.07f));
        // The screen, its stand, and the aerial's two arms.
        r.set(cx - s * 0.28f, cy - s * 0.12f, cx + s * 0.28f, cy + s * 0.22f);
        c.drawRoundRect(r, s * 0.05f, s * 0.05f, glyph);
        c.drawLine(cx - s * 0.12f, cy + s * 0.31f, cx + s * 0.12f, cy + s * 0.31f, glyph);
        c.drawLine(cx, cy - s * 0.12f, cx - s * 0.14f, cy - s * 0.3f, glyph);
        c.drawLine(cx, cy - s * 0.12f, cx + s * 0.14f, cy - s * 0.3f, glyph);
    }

    // ---- the remote ----------------------------------------------------------------------------------------------------

    /** The remote's row, counted down the whole list (-1: on the buttons). */
    int focus() {
        return focus;
    }

    String titleWords() {
        return title;
    }

    String plotWords() {
        return plot;
    }

    /** Whether the remote is on the best source (the first) and it is marked BEST. */
    boolean bestLit() {
        return focus == actions.size() && rows.size() > 1 && withSources;
    }

    /** A key while open (the player's cover: the volume keys go on to the TV). */
    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (e.getRepeatCount() == 0) ok();
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (e.getRepeatCount() == 0) host.back();
                return true;
            default:
                move(code);
                return true;
        }
    }

    /**
     * Up, Down, Left and Right: along the rows (the list scrolling to keep the remote's in sight), Up from the first to
     * the buttons and along them, past the top or the last row to the host. True when it was one of them.
     */
    boolean move(int code) {
        if (!laid) layOut();
        int total = actions.size() + sourceRows();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (focus < 0) {
                    host.up();
                } else if (focus == 0) {
                    if (pills.isEmpty()) host.up();
                    else focus = -1;
                } else {
                    focus--;
                    keepInSight();
                }
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (focus < 0) {
                    if (total > 0) {
                        focus = first;
                        keepInSight();
                    } else {
                        host.down();
                    }
                } else if (focus >= total - 1) {
                    host.down();
                } else {
                    focus++;
                    keepInSight();
                }
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focus < 0) {
                    pillAt = Math.max(0, Math.min(pills.size() - 1, pillAt + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
                    invalidate();
                }
                return true;
            default:
                return false;
        }
    }

    /** OK: the button, the row above the sources or the source the remote is on. */
    void ok() {
        if (focus < 0) {
            if (pillAt < pills.size()) host.button(pillAt);
            return;
        }
        if (focus < actions.size()) {
            host.action(focus);
            return;
        }
        int s = focus - actions.size();
        if (withSources && s < plays.size()) host.play(s);
    }
}
