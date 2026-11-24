package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import com.gathin.broadcast.AiKey;
import com.gathin.broadcast.AskAi;
import com.gathin.broadcast.Claude;
import com.gathin.broadcast.LogoArt;
import com.gathin.broadcast.LogoDesigner;
import com.gathin.broadcast.LogoPacks;
import com.gathin.broadcast.LogoSpec;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.LogoMatch;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.MadeLogos;
import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Change logo, from a channel's menu in the TV guide: a frosted card beside the menu (from x 760, the whole height, in
 * the 07 look: rounded 36, "CHANGE LOGO" over the channel's name) with four tabs along its top, as the phone's logo
 * picker has them:
 *
 * <pre>
 *   Found for it   the new logos that may be the channel's, the best first, each on its tile as the guide draws it, with
 *                  its name, its country and kind and how sure the match is, then the playlist's own; the one shown now
 *                  ticked (OK keeps one: the best match as "automatic", so a newer list can still change it)
 *   Catalogue      the free logo packs searched by the channel's name (the on-screen keyboard for other words): TV Logos
 *                  and iptv-org's (LogoCatalogue, LogoPacks' weekly index, the generic user agent), the results grouped
 *                  by pack, each logo on its own cell colour as the guide's cells have them; Left and Right along a
 *                  pack, OK uses it
 *   Make my own    logos drawn here from the channel's name (LogoDesigner: wordmarks, monograms, badges, roundels), big
 *                  with the others in a strip under it; Left and Right flip through them (fresh ones after the last), OK
 *                  keeps it
 *   Ask AI         quick asks or the family's own words, typed; Claude designs one (a LogoSpec, with the family's own
 *                  key: Settings › Other › Ask AI) and LogoArt draws it; OK on Use this logo keeps it
 * </pre>
 *
 * A logo made here (either way) is kept as a see-through PNG in the app's own files (MadeLogos) and chosen for the
 * channel; one from the catalogue by its address. Either goes on every version of the channel (one row per channel, and
 * a merge's kept channel), through ChannelEdits as every choice does, and shows wherever the channel's logo does: the
 * guide, the info card, the mini guide, the reminders, the launcher's cards. Up from the top of a tab goes to the tabs,
 * Left and Right there change the tab, Down comes back; Back leaves it as it was.
 */
final class LogoScreen extends Screen {
    /** The card (design pixels): its left and top, its width and height, its padding. */
    static final float CARD_LEFT = 760f, CARD_TOP = 48f, CARD_W = 1112f, CARD_H = 984f, PAD = 28f;
    /** Its head (CHANGE LOGO, the name), the tabs' strip, and where a tab's page starts (design pixels). */
    static final float HEAD = 104f, TABS_H = 56f, BODY = HEAD + TABS_H + 22f;
    /** A found logo's line (84 and 6 between) and its tile (design pixels). */
    static final float ROW = 90f, TILE_W = 112f, TILE_H = 62f;
    /** The catalogue's field, a pack's header, its cells (and the name under each), the gap between cells. */
    static final float FIELD_H = 64f, PACK_HEAD = 34f, CELL_W = 184f, CELL_H = 108f, CELL_NAME = 30f, CELL_GAP = 14f, PACK_GAP = 14f;
    /** The big logo's tile (Make my own, Ask AI), the strip's tiles, an ask's line. */
    static final float BIG_W = 640f, BIG_H = 290f, SMALL_W = 160f, SMALL_H = 92f, ASK_ROW = 58f;
    /** The apps' dark tile a logo with no ground sits on (LogoSpec.under's); the card's deep ground under its frost. */
    static final int DARK_TILE = 0xFF20232B, BASE = 0xE60E0E12;
    static final String PLAYLIST = ChannelEdits.LOGO_PLAYLIST;
    static final String NONE = "No new logo matches this channel's name. A channel renamed is matched by its new name, "
            + "then by the playlist's. Try the catalogue, or make your own.";

    static final int FOUND = 0, CATALOGUE = 1, MAKE = 2, ASK = 3;
    static final String[] TABS = {"Found for it", "Catalogue", "Make my own", "Ask AI"};
    /** Ask AI's quick asks (the last: the family's own words, typed), as the phone's. */
    static final String[] ASKS = {"A classic sports channel look", "Bold and modern", "Retro, like an 80s channel", "Clean and minimal",
            "In a circle with a star", "Type my own…"};
    /** How many of Make my own's logos at a time. */
    static final int MADE = 6;

    private final Channel ch;
    /** Its name without the playlist's tags ("UK: Kestrel Sports 1 FHD" to "Kestrel Sports 1"): the catalogue's first search. */
    private final String plainName;
    /** Opened beside the channel's menu, which stays on screen with its scrim (08c): no scrim of its own then. */
    boolean overMenu;
    private Card card;

    // ---- where the remote is ----------------------------------------------------------------------------------------
    private int tab = FOUND;
    private boolean onTabs;

    // ---- Found for it -----------------------------------------------------------------------------------------------
    /** The logos found (null while they are looked for). */
    private List<LogoMatch.Found> found;
    private final List<String[]> rows = new ArrayList<>();
    private int focus;
    private float scroll;

    // ---- Catalogue --------------------------------------------------------------------------------------------------
    private String query;
    private LogoCatalogue.Result cat;
    private boolean searching;
    /** The packs with logos found, in order, and their logos. */
    private final List<LogoPacks.Pack> packs = new ArrayList<>();
    private final List<List<LogoPacks.Logo>> packLogos = new ArrayList<>();
    /** 0: the field; 1 on: a pack's row. Each row's cell. */
    private int crow;
    private int[] ccol = new int[0];
    private int searchTicket;

    // ---- Make my own ------------------------------------------------------------------------------------------------
    private List<LogoSpec> made = new ArrayList<>();
    private List<Bitmap> madeArt = new ArrayList<>();
    private int madeAt, round;
    private boolean making;

    // ---- Ask AI -----------------------------------------------------------------------------------------------------
    private LogoSpec ai;
    private Bitmap aiArt;
    private boolean asking;
    private String aiNote = "";
    private int askAt, aiTicket;
    private boolean saving;

    LogoScreen(TvActivity tv, Channel ch) {
        super(tv);
        this.ch = ch;
        this.plainName = LogoDesigner.plain(ChannelEdits.name(ch));
        this.query = plainName;
    }

    @Override View build() {
        card = new Card(tv);
        return card;
    }

    @Override boolean overlay() {
        return true;
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override void shown() {
        if (found != null) return;
        layOut();
        // Looked for off the main thread: it reads more than one channel's match does.
        Exec.io(() -> {
            List<LogoMatch.Found> f = LogoPack.candidates(ch, 12);
            Exec.main(() -> {
                found = f;
                layOut();
            });
        });
    }

    @Override void hidden() {
        // Closed (not just the keyboard over it): what it read and drew let go of.
        if (tv.top() != this) release();
    }

    @Override boolean closing(Runnable done) {
        release();
        return false;
    }

    private void release() {
        searchTicket++;
        aiTicket++;
        LogoCatalogue.release();
        made = new ArrayList<>();
        madeArt = new ArrayList<>();
        aiArt = null;
    }

    /** The rows: each logo found (its path), then the playlist's; the remote on the one shown now. */
    private void layOut() {
        rows.clear();
        String now = shownNow();
        int at = -1;
        if (found != null) {
            String auto = LogoPack.auto(ch);
            for (LogoMatch.Found f : found) {
                String sure = f.logo.path.equals(auto) ? "Best match" : f.sure >= LogoMatch.SURE ? "Good match" : "Possible match";
                rows.add(new String[]{f.logo.path, f.logo.label(), f.logo.about() + "  ·  " + sure});
                if (f.logo.path.equals(now)) at = rows.size() - 1;
            }
        }
        rows.add(new String[]{PLAYLIST, "Playlist's logo", ch.logo.isEmpty() ? "The playlist gives none" : "As the playlist gives it"});
        if (PLAYLIST.equals(now)) at = rows.size() - 1;
        focus = Math.max(0, at);
        settle();
        if (card != null) card.invalidate();
    }

    /** What the channel shows now: a new logo's path, the playlist's, or its own choice (the catalogue's, one made). */
    private String shownNow() {
        String choice = ChannelEdits.logo(ch);
        if (!choice.isEmpty()) return choice;
        String auto = LogoPack.on() ? LogoPack.auto(ch) : null;
        return auto == null ? PLAYLIST : auto;
    }

    // ---- the remote -------------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        boolean up = code == KeyEvent.KEYCODE_DPAD_UP, down = code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (onTabs) {
            if (left || right) tab(Math.max(0, Math.min(TABS.length - 1, tab + (right ? 1 : -1))));
            else if (down) onTabs = false;
            card.invalidate();
            return true;
        }
        switch (tab) {
            case FOUND:
                if (up && focus == 0) onTabs = true;
                else if (up) focus--;
                else if (down && focus < rows.size() - 1) focus++;
                settle();
                break;
            case CATALOGUE:
                if (up && crow == 0) onTabs = true;
                else if (up) crow--;
                else if (down && crow < packs.size()) crow++;
                else if ((left || right) && crow > 0) {
                    int n = packLogos.get(crow - 1).size();
                    ccol[crow - 1] = Math.max(0, Math.min(n - 1, ccol[crow - 1] + (right ? 1 : -1)));
                }
                break;
            case MAKE:
                if (up) onTabs = true;
                else if (left || right) flip(right ? 1 : -1);
                break;
            default:
                if (up && askAt == 0) onTabs = true;
                else if (up) askAt--;
                else if (down && askAt < askRows() - 1) askAt++;
                break;
        }
        card.invalidate();
        // Nothing reaches the screen under it.
        return true;
    }

    /** Another tab: its page made ready (the catalogue searched, the logos made) the first time. */
    void tab(int t) {
        tab = t;
        if (t == CATALOGUE && cat == null && !searching) search(query);
        if (t == MAKE && made.isEmpty() && !making) make();
        if (card != null) card.invalidate();
    }

    @Override void ok() {
        if (onTabs) {
            onTabs = false;
            card.invalidate();
            return;
        }
        switch (tab) {
            case FOUND:
                if (focus >= 0 && focus < rows.size()) choose(rows.get(focus)[0]);
                return;
            case CATALOGUE:
                if (crow == 0) {
                    tv.push(new RenameScreen(tv, "Search the logo packs (a channel's name)", query, words -> {
                        if (!words.trim().isEmpty()) search(words.trim());
                    }));
                } else if (crow - 1 < packLogos.size()) {
                    List<LogoPacks.Logo> l = packLogos.get(crow - 1);
                    if (!l.isEmpty()) chooseUrl(l.get(Math.min(ccol[crow - 1], l.size() - 1)).url);
                }
                return;
            case MAKE:
                if (!made.isEmpty()) keepMade(made.get(madeAt));
                return;
            default:
                okAsk();
        }
    }

    // ---- keeping one --------------------------------------------------------------------------------------------------

    /** One of the found or the playlist's kept for the channel: the best match as automatic, the playlist's as itself unless it would show anyway. */
    void choose(String path) {
        String auto = LogoPack.auto(ch);
        String keep;
        if (PLAYLIST.equals(path)) keep = LogoPack.on() && auto != null ? PLAYLIST : "";
        else keep = LogoPack.on() && path.equals(auto) ? "" : path;
        // A channel in several qualities: its logo on every version (one row per channel).
        ChannelEdits.setLogo(tv, ch, keep);
        prune();
        tv.pop();
    }

    /** One from the catalogue kept: TV Logos' by its path (as the new logos are, so Refresh logos reaches it), else its address. */
    void chooseUrl(String url) {
        String keep = url;
        if (url.startsWith(LogoPack.RAW) && url.indexOf('%') < 0 && url.indexOf('?') < 0) keep = url.substring(LogoPack.RAW.length());
        ChannelEdits.setLogo(tv, ch, keep);
        prune();
        Toast.makeText(tv, "Logo set for " + ChannelEdits.name(ch), Toast.LENGTH_SHORT).show();
        tv.pop();
    }

    /** A logo made here drawn as a see-through PNG and kept for the channel (off the main thread), then the card closes. */
    void keepMade(LogoSpec spec) {
        if (saving || spec == null) return;
        saving = true;
        Context c = tv;
        Exec.io(() -> {
            String choice = null;
            try {
                Bitmap b = LogoArt.render(c, spec, MadeLogos.HEIGHT);
                choice = MadeLogos.save(c, b);
                b.recycle();
            } catch (java.io.IOException | RuntimeException | OutOfMemoryError e) {
                choice = null;
            }
            String got = choice;
            Exec.main(() -> {
                saving = false;
                if (got == null) {
                    Toast.makeText(tv, "Couldn't keep that logo on this TV", Toast.LENGTH_SHORT).show();
                    return;
                }
                ChannelEdits.setLogo(tv, ch, got);
                prune();
                Toast.makeText(tv, "Logo set for " + ChannelEdits.name(ch), Toast.LENGTH_SHORT).show();
                if (tv.top() == this) tv.pop();
            });
        });
    }

    /** The logos made before that no channel uses any more let go of (off the main thread). */
    private void prune() {
        Context c = tv;
        java.util.Set<String> keep = ChannelEdits.logoChoices(c);
        Exec.io(() -> MadeLogos.prune(c, keep));
    }

    // ---- Catalogue ----------------------------------------------------------------------------------------------------

    /** The packs searched for these words (off the main thread); the results grouped by pack. */
    void search(String words) {
        query = words;
        searching = true;
        int mine = ++searchTicket;
        Context c = tv;
        if (card != null) card.invalidate();
        Exec.io(() -> {
            LogoCatalogue.Result r = LogoCatalogue.search(c, words);
            Exec.main(() -> {
                if (mine != searchTicket) return;
                searching = false;
                took(r);
            });
        });
    }

    private void took(LogoCatalogue.Result r) {
        cat = r;
        packs.clear();
        packLogos.clear();
        for (Map.Entry<LogoPacks.Pack, List<LogoPacks.Logo>> e : r.found.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            packs.add(e.getKey());
            packLogos.add(e.getValue());
            // Read ahead, at the cell's size.
            for (LogoPacks.Logo l : e.getValue()) Pics.prefetch(l.url, Ui.px(CELL_W - 28f), Ui.px(CELL_H - 24f), Pics.CELL_COLOUR);
        }
        ccol = new int[packs.size()];
        crow = Math.min(crow, packs.size());
        if (card != null) card.invalidate();
    }

    /** What the catalogue says under its field. */
    String catalogueLine() {
        if (searching) return "Looking through the packs…";
        if (cat == null) return "";
        if (packs.isEmpty()) {
            return cat.problem.isEmpty() ? "No logo in the packs for \"" + query + "\". Try fewer words, or make your own."
                    : cat.problem + ". Try again later, or make your own.";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < packs.size(); i++) {
            if (b.length() > 0) b.append("  ·  ");
            b.append(packs.get(i).label).append(": ").append(packLogos.get(i).size());
        }
        if (!cat.problem.isEmpty()) b.append("  ·  ").append(cat.problem);
        return b.toString();
    }

    // ---- Make my own ----------------------------------------------------------------------------------------------------

    /** Logos for the channel from its name and group (round 0, 1, 2… for fresh ones), drawn off the main thread. */
    private void make() {
        making = true;
        List<LogoSpec> specs = LogoDesigner.suggest(ChannelEdits.name(ch), ch.group, MADE, round);
        Context c = tv;
        Exec.work(() -> {
            List<Bitmap> art = new ArrayList<>();
            for (LogoSpec s : specs) art.add(LogoArt.render(c, s, Ui.px(BIG_H - 60f)));
            Exec.main(() -> {
                making = false;
                made = specs;
                madeArt = art;
                madeAt = Math.max(0, Math.min(madeAt, specs.size() - 1));
                if (card != null) card.invalidate();
            });
        });
    }

    /** Left and Right: the next or the one before; after the last, fresh ones. */
    private void flip(int step) {
        if (made.isEmpty() || making) return;
        int n = madeAt + step;
        if (n >= made.size()) {
            round++;
            madeAt = 0;
            make();
        } else if (n < 0) {
            if (round > 0) {
                round--;
                madeAt = MADE - 1;
                make();
            } else {
                madeAt = made.size() - 1;
            }
        } else {
            madeAt = n;
        }
    }

    // ---- Ask AI ---------------------------------------------------------------------------------------------------------

    /** Ask AI's lines: Use this logo (once there is one), then the asks. */
    int askRows() {
        return (ai != null ? 1 : 0) + ASKS.length;
    }

    private void okAsk() {
        int i = askAt - (ai != null ? 1 : 0);
        if (i < 0) {
            keepMade(ai);
            return;
        }
        if (asking) return;
        if (!AiKey.has(tv)) {
            aiNote = AiSettings.NO_KEY;
            card.invalidate();
            return;
        }
        if (i == ASKS.length - 1) {
            tv.push(new RenameScreen(tv, "What would you like? (style, colours, a mark)", "", words -> {
                if (!words.trim().isEmpty()) ask(words.trim());
            }));
        } else {
            ask(ASKS[i]);
        }
    }

    /** Claude asked for a logo in the family's words (off the main thread), drawn by LogoArt when it comes. */
    void ask(String words) {
        if (asking) return;
        Context c = tv;
        String key = AiKey.get(c), model = AiKey.model(c), name = ch.name, group = ch.group;
        asking = true;
        aiNote = "Claude is designing it… this can take a minute";
        int mine = ++aiTicket;
        if (card != null) card.invalidate();
        AiSettings.run(() -> {
            LogoSpec got = null;
            Bitmap art = null;
            String why = null;
            try {
                got = AskAi.designLogo(AiSettings.claude.get(), key, model, words, name, group);
                art = LogoArt.render(c, got, Ui.px(BIG_H - 60f));
            } catch (Claude.Failure f) {
                why = f.getMessage();
            } catch (RuntimeException | OutOfMemoryError e) {
                why = "Something went wrong drawing Claude's design. Try again.";
            }
            LogoSpec g = got;
            Bitmap a = art;
            String w = why;
            Exec.main(() -> {
                if (mine != aiTicket) return;
                answered(g, a, w);
            });
        });
    }

    /** Claude's answer (a design and its picture), or why there is none. */
    void answered(LogoSpec spec, Bitmap art, String why) {
        asking = false;
        if (spec != null && art != null) {
            ai = spec;
            aiArt = art;
            aiNote = "\"" + spec.text + (spec.sub.isEmpty() ? "" : " " + spec.sub) + "\": a " + spec.layout.label.toLowerCase(Locale.UK)
                    + " in " + spec.font.label + ", drawn by the TV. OK on Use this logo keeps it; ask again for another.";
            // The remote on Use this logo.
            askAt = 0;
            onTabs = false;
        } else {
            aiNote = why == null ? "Claude sent no design back. Try again." : why;
        }
        if (card != null) card.invalidate();
    }

    // ---- what the tests and previews look at ------------------------------------------------------------------------------

    int count() {
        return rows.size();
    }

    int focus() {
        return focus;
    }

    String path(int i) {
        return rows.get(i)[0];
    }

    int tabNow() {
        return tab;
    }

    boolean onTabs() {
        return onTabs;
    }

    List<LogoPacks.Pack> packsFound() {
        return packs;
    }

    List<LogoSpec> made() {
        return made;
    }

    int madeAt() {
        return madeAt;
    }

    LogoSpec ai() {
        return ai;
    }

    String aiNote() {
        return aiNote;
    }

    /** Previews: the remote on a catalogue's cell (row 1 on), or the field (0). */
    void catalogueAt(int row, int col) {
        crow = Math.max(0, Math.min(packs.size(), row));
        if (crow > 0) ccol[crow - 1] = Math.max(0, Math.min(packLogos.get(crow - 1).size() - 1, col));
    }

    // ---- laying out -------------------------------------------------------------------------------------------------------

    /** The found list slides so the remote's row is in sight. */
    private void settle() {
        float room = CARD_H - BODY - PAD, y0 = focus * ROW, y1 = y0 + ROW, total = rows.size() * ROW;
        scroll = RowList.scrollFor(scroll, y0, y1, room, total);
    }

    // ---- the card ---------------------------------------------------------------------------------------------------

    private final class Card extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
                bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final TextPaint headP = Type.label(14f, Look07.L3), nameP = Type.paint(26f, Type.BOLD, Look07.L1);
        private final TextPaint titleP = Type.paint(20f, Type.SEMIBOLD, Look07.L1), aboutP = Type.paint(16f, Type.MEDIUM, Look07.L3);
        private final TextPaint tabP = Type.paint(20f, Type.SEMIBOLD, Look07.L2), bigP = Type.paint(26f, Type.BOLD, Look07.L1);
        private final TextPaint cellP = Type.paint(15f, Type.MEDIUM, Look07.L2), packP = Type.label(14f, Look07.L2);
        private final Frost frost = new Frost(Look07.MAT_DARK);
        private final RectF r = new RectF(), box = new RectF();
        private final Runnable redraw = this::invalidate;
        private StaticLayout none, note;
        private String noteFor;

        Card(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            Shadows.prepare(Look07.SH_ROW, 20f);
        }

        @Override protected void onDraw(Canvas c) {
            if (!overMenu) {
                fill.setColor(Look07.SCRIM);
                c.drawRect(0, 0, getWidth(), getHeight(), fill);
            }
            float top0 = CARD_TOP;
            r.set(Ui.f(CARD_LEFT), Ui.f(top0), Ui.f(CARD_LEFT + CARD_W), Ui.f(top0 + CARD_H));
            // A card this big over the guide: a deep ground under its frost, so the guide's words never read through it.
            fill.setColor(BASE);
            c.drawRoundRect(r, Ui.f(Look07.R_LOGO_CARD), Ui.f(Look07.R_LOGO_CARD), fill);
            frost.draw(c, r, Ui.f(Look07.R_LOGO_CARD), 1f);
            float tx = CARD_LEFT + PAD;
            c.drawText("CHANGE LOGO", Ui.f(tx), Ui.f(top0 + PAD + 18f), headP);
            c.drawText(Ui.fit(ChannelEdits.name(ch), nameP, Ui.f(CARD_W - 2 * PAD - 140f)), Ui.f(tx), Ui.f(top0 + PAD + 56f), nameP);
            // The logo shown now, at the head's right.
            float nx = CARD_LEFT + CARD_W - PAD - TILE_W, ny = top0 + PAD;
            r.set(Ui.f(nx), Ui.f(ny), Ui.f(nx + TILE_W), Ui.f(ny + TILE_H));
            Logos.draw(c, LogoPack.url(ch), r, redraw);
            drawTabs(c, top0 + HEAD);
            float body = top0 + BODY;
            c.save();
            c.clipRect(Ui.f(CARD_LEFT), Ui.f(body - 8f), Ui.f(CARD_LEFT + CARD_W), Ui.f(top0 + CARD_H - 10f));
            switch (tab) {
                case FOUND: drawFound(c, body); break;
                case CATALOGUE: drawCatalogue(c, body); break;
                case MAKE: drawMake(c, body); break;
                default: drawAsk(c, body);
            }
            c.restore();
        }

        private void drawTabs(Canvas c, float y) {
            float x = CARD_LEFT + PAD;
            for (int i = 0; i < TABS.length; i++) {
                float w = tabP.measureText(TABS[i]) / Ui.f(1f) + 48f;
                r.set(Ui.f(x), Ui.f(y), Ui.f(x + w), Ui.f(y + TABS_H - 8f));
                float rad = r.height() / 2f;
                boolean lit = onTabs && i == tab, on = i == tab;
                if (lit) {
                    fill.setColor(Look07.FOCUS_FILL);
                    c.drawRoundRect(r, rad, rad, fill);
                } else if (on) {
                    fill.setColor(Look07.SELECTED);
                    c.drawRoundRect(r, rad, rad, fill);
                }
                tabP.setColor(lit ? Look07.INK : on ? Look07.L1 : Look07.L3);
                c.drawText(TABS[i], Ui.f(x + 24f), Type.centred(tabP, r.centerY()), tabP);
                x += w + 10f;
            }
        }

        // ---- Found for it ------------------------------------------------------------------------------------------

        private void drawFound(Canvas c, float body) {
            float tx = CARD_LEFT + PAD;
            float top = body - scroll;
            for (int i = 0; i < rows.size(); i++) {
                float y = top + i * ROW;
                if (y > CARD_TOP + CARD_H || y + ROW < body - 8f) continue;
                drawRow(c, rows.get(i), y, !onTabs && i == focus);
            }
            if (found == null) {
                c.drawText("Looking for logos…", Ui.f(tx), Ui.f(top + rows.size() * ROW + 40f), aboutP);
            } else if (found.isEmpty()) {
                if (none == null) none = Ui.layout(NONE, aboutP, Ui.px(CARD_W - 2 * PAD - 12f), 3);
                Ui.draw(c, none, Ui.f(tx), Ui.f(top + rows.size() * ROW + 12f));
            }
        }

        private void drawRow(Canvas c, String[] row, float y, boolean lit) {
            boolean now = row[0].equals(shownNow());
            float left = CARD_LEFT + PAD, right = CARD_LEFT + CARD_W - PAD;
            r.set(Ui.f(left), Ui.f(y), Ui.f(right), Ui.f(y + ROW - 6f));
            float rad = Ui.f(20f);
            if (lit) {
                // Where the remote is: white, its words dark.
                Shadows.draw(c, r, rad, Look07.SH_ROW, 1f);
                fill.setColor(Look07.FOCUS_FILL);
                c.drawRoundRect(r, rad, rad, fill);
            } else if (now) {
                // The one shown now: a light ring (white at 14 % inside a 2 px ring at 70 %).
                fill.setColor(0x24FFFFFF);
                c.drawRoundRect(r, rad, rad, fill);
                lightRing(c, r, rad);
            }
            float tileX = left + 14f, cy = y + (ROW - 6f) / 2f, tileY = cy - TILE_H / 2f;
            String url = PLAYLIST.equals(row[0]) ? ch.logo : LogoPack.urlOf(row[0]);
            r.set(Ui.f(tileX), Ui.f(tileY), Ui.f(tileX + TILE_W), Ui.f(tileY + TILE_H));
            if (!Logos.draw(c, url, r, redraw) && (url.isEmpty() || Pics.failed(url))) {
                Icons07.draw(c, Icons07.TV, r.centerX(), r.centerY(), Ui.f(32f), lit ? Look07.INK3 : Look07.L3);
            }
            float textX = tileX + TILE_W + 16f, room = right - 14f - textX - (now ? 40f : 0f);
            titleP.setColor(lit ? Look07.INK : Look07.L1);
            c.drawText(Ui.fit(row[1], titleP, Ui.f(room)), Ui.f(textX), Ui.f(cy - 4f), titleP);
            aboutP.setColor(lit ? Look07.INK2 : Look07.L3);
            c.drawText(Ui.fit(row[2], aboutP, Ui.f(room)), Ui.f(textX), Ui.f(cy + 20f), aboutP);
            if (now) Icons07.draw(c, Icons07.CHECK, Ui.f(right - 30f), Ui.f(cy), Ui.f(26f), lit ? Look07.INK : Look07.GREEN);
            aboutP.setColor(Look07.L3);
        }

        private void lightRing(Canvas c, RectF b, float rad) {
            float sw = Math.max(1f, Ui.f(2f));
            ring.setStrokeWidth(sw);
            ring.setColor(0xB3FFFFFF);
            c.drawRoundRect(b.left + sw / 2f, b.top + sw / 2f, b.right - sw / 2f, b.bottom - sw / 2f, rad, rad, ring);
        }

        private void focusRing(Canvas c, RectF b, float rad) {
            float sw = Math.max(2f, Ui.f(3f)), k = sw / 2f + Ui.f(3f);
            ring.setStrokeWidth(sw);
            ring.setColor(0xFFFFFFFF);
            c.drawRoundRect(b.left - k, b.top - k, b.right + k, b.bottom + k, rad + k, rad + k, ring);
        }

        // ---- Catalogue ----------------------------------------------------------------------------------------------

        private void drawCatalogue(Canvas c, float body) {
            float left = CARD_LEFT + PAD, right = CARD_LEFT + CARD_W - PAD;
            // The field: what is searched for, OK to type other words.
            r.set(Ui.f(left), Ui.f(body), Ui.f(right), Ui.f(body + FIELD_H));
            float rad = r.height() / 2f;
            boolean lit = !onTabs && crow == 0;
            fill.setColor(lit ? Look07.FOCUS_FILL : Look07.BUTTON);
            c.drawRoundRect(r, rad, rad, fill);
            Icons07.draw(c, Icons07.SEARCH, Ui.f(left + 36f), r.centerY(), Ui.f(26f), lit ? Look07.INK : Look07.L2);
            titleP.setColor(lit ? Look07.INK : Look07.L1);
            c.drawText(Ui.fit(query, titleP, Ui.f(CARD_W - 2 * PAD - 360f)), Ui.f(left + 66f), Type.centred(titleP, r.centerY()), titleP);
            aboutP.setColor(lit ? Look07.INK2 : Look07.L3);
            String hint = "OK to search other words";
            c.drawText(hint, Ui.f(right - 28f) - aboutP.measureText(hint), Type.centred(aboutP, r.centerY()), aboutP);
            aboutP.setColor(Look07.L3);
            c.drawText(Ui.fit(catalogueLine(), aboutP, Ui.f(CARD_W - 2 * PAD)), Ui.f(left + 6f), Ui.f(body + FIELD_H + 32f), aboutP);
            float y = body + FIELD_H + 56f;
            for (int p = 0; p < packs.size(); p++) {
                LogoPacks.Pack pack = packs.get(p);
                List<LogoPacks.Logo> logos = packLogos.get(p);
                String head = pack.label.toUpperCase(Locale.UK) + "  ·  " + logos.size();
                c.drawText(head, Ui.f(left + 6f), Ui.f(y + 18f), packP);
                aboutP.setColor(Look07.L4);
                c.drawText("from " + pack.credit, Ui.f(left + 16f) + packP.measureText(head), Ui.f(y + 18f), aboutP);
                aboutP.setColor(Look07.L3);
                float cy = y + PACK_HEAD;
                int col = ccol[p], perRow = (int) ((CARD_W - 2 * PAD + CELL_GAP) / (CELL_W + CELL_GAP));
                int first = Math.max(0, Math.min(col - perRow + 2, logos.size() - perRow));
                for (int i = first; i < logos.size() && i < first + perRow; i++) {
                    float cx = left + (i - first) * (CELL_W + CELL_GAP);
                    drawCell(c, logos.get(i), cx, cy, !onTabs && crow == p + 1 && i == col);
                }
                if (first + perRow < logos.size()) Ui.chevron(c, Ui.f(right + 10f), Ui.f(cy + CELL_H / 2f), Ui.f(14f), Ui.RIGHT, packP);
                if (first > 0) Ui.chevron(c, Ui.f(left - 12f), Ui.f(cy + CELL_H / 2f), Ui.f(14f), Ui.LEFT, packP);
                y = cy + CELL_H + CELL_NAME + PACK_GAP;
            }
            aboutP.setColor(Look07.L4);
            c.drawText("Free logo packs; each logo stays its channel's own. Their licences: Settings › About › Logo packs' licences",
                    Ui.f(left + 6f), Ui.f(CARD_TOP + CARD_H - PAD), aboutP);
            aboutP.setColor(Look07.L3);
        }

        /** A pack's logo on its own cell colour (as the guide's cells have it), its name and country under it. */
        private void drawCell(Canvas c, LogoPacks.Logo l, float x, float y, boolean lit) {
            box.set(Ui.f(x), Ui.f(y), Ui.f(x + CELL_W), Ui.f(y + CELL_H));
            float rad = Ui.f(Look07.R_CELL);
            Logos.Tint t = Logos.tint(l.url);
            cellGround(c, box, rad, t == null ? Logos.Tint.WAITING : t);
            if (lit) focusRing(c, box, rad);
            r.set(box.left + Ui.f(14f), box.top + Ui.f(12f), box.right - Ui.f(14f), box.bottom - Ui.f(12f));
            Bitmap b = Pics.get(l.url, Math.round(r.width()), Math.round(r.height()), Pics.CELL_COLOUR, redraw);
            if (b != null) {
                c.drawBitmap(b, Math.round(r.centerX() - b.getWidth() / 2f), Math.round(r.centerY() - b.getHeight() / 2f), bmp);
            } else if (Pics.failed(l.url)) {
                Icons07.draw(c, Icons07.TV, r.centerX(), r.centerY(), Ui.f(28f), Look07.L3);
            }
            cellP.setColor(lit ? Look07.L1 : Look07.L3);
            String name = l.country.isEmpty() ? l.name : l.name + " · " + l.country.toUpperCase(Locale.UK);
            c.drawText(Ui.fit(name, cellP, Ui.f(CELL_W)), Ui.f(x + 2f), Ui.f(y + CELL_H + 22f), cellP);
        }

        /** A cell's ground in a logo's row colours: a line of light along its top, then its fall (ChannelLook's). */
        private void cellGround(Canvas c, RectF b, float rad, Logos.Tint t) {
            float sw = Math.max(1f, Ui.f(1f)), h = Math.max(1f, b.height() - sw);
            fill.setShader(null);
            fill.setColor(Theme.mix(t.rowTop, 0xFFFFFFFF, (Look07.CHANNEL_TOP_LIGHT >>> 24) / 255f) | 0xFF000000);
            c.drawRoundRect(b, rad, rad, fill);
            fill.setShader(t.rowFall);
            fill.setColor(0xFF000000);
            c.save();
            c.translate(b.left, b.top + sw);
            c.scale(1f, h);
            c.drawRoundRect(0, 0, b.width(), 1f, rad, rad / h, fill);
            c.restore();
            fill.setShader(null);
        }

        // ---- Make my own ------------------------------------------------------------------------------------------

        private void drawMake(Canvas c, float body) {
            float left = CARD_LEFT + PAD, right = CARD_LEFT + CARD_W - PAD;
            float y = body + 6f;
            Bitmap big = madeAt < madeArt.size() ? madeArt.get(madeAt) : null;
            drawBig(c, left, y, big, making ? "Drawing logos for " + plainName + "…" : "");
            float ix = left + BIG_W + 32f, room = right - ix;
            if (madeAt < made.size()) {
                LogoSpec s = made.get(madeAt);
                c.drawText(Ui.fit(s.layout.label, bigP, Ui.f(room)), Ui.f(ix), Ui.f(y + 40f), bigP);
                aboutP.setColor(Look07.L2);
                c.drawText(Ui.fit((madeAt + 1) + " of " + made.size() + "  ·  " + s.font.label
                        + (s.mark == LogoSpec.Mark.NONE ? "" : "  ·  " + cap(s.mark.name())), aboutP, Ui.f(room)), Ui.f(ix), Ui.f(y + 74f), aboutP);
                aboutP.setColor(Look07.L3);
            }
            c.drawText("Left and Right: the next one", Ui.f(ix), Ui.f(y + 150f), aboutP);
            c.drawText("(fresh ones after the last)", Ui.f(ix), Ui.f(y + 176f), aboutP);
            c.drawText(Ui.fit("OK: use it for " + plainName, aboutP, Ui.f(room)), Ui.f(ix), Ui.f(y + 214f), aboutP);
            c.drawText("Drawn crisp at any size, kept on this TV", Ui.f(ix), Ui.f(y + 240f), aboutP);
            // The strip of all of them.
            float sy = y + BIG_H + 28f;
            for (int i = 0; i < madeArt.size(); i++) {
                float sx = left + i * (SMALL_W + 19f);
                box.set(Ui.f(sx), Ui.f(sy), Ui.f(sx + SMALL_W), Ui.f(sy + SMALL_H));
                float rad = Ui.f(Look07.R_TILE);
                fill.setColor(DARK_TILE);
                c.drawRoundRect(box, rad, rad, fill);
                fit(c, madeArt.get(i), box, Ui.f(10f));
                if (i == madeAt) focusRing(c, box, rad);
                cellP.setColor(i == madeAt ? Look07.L1 : Look07.L3);
                if (i < made.size()) c.drawText(made.get(i).layout.label, Ui.f(sx + 4f), Ui.f(sy + SMALL_H + 24f), cellP);
            }
        }

        /** The big tile (Make my own's, Claude's): the apps' dark tile, the logo fitted in it, or words while there is none. */
        private void drawBig(Canvas c, float x, float y, Bitmap art, String words) {
            box.set(Ui.f(x), Ui.f(y), Ui.f(x + BIG_W), Ui.f(y + BIG_H));
            float rad = Ui.f(24f);
            fill.setColor(DARK_TILE);
            c.drawRoundRect(box, rad, rad, fill);
            lightRing(c, box, rad);
            if (art != null) {
                fit(c, art, box, Ui.f(30f));
            } else if (!words.isEmpty()) {
                aboutP.setColor(Look07.L2);
                c.drawText(Ui.fit(words, aboutP, box.width() - Ui.f(40f)), box.centerX() - Math.min(aboutP.measureText(words),
                        box.width() - Ui.f(40f)) / 2f, Type.centred(aboutP, box.centerY()), aboutP);
                aboutP.setColor(Look07.L3);
            } else {
                Icons07.draw(c, Icons07.IMAGE, box.centerX(), box.centerY(), Ui.f(48f), Look07.L4);
            }
        }

        /** A picture fitted in a box with a margin, at its own shape. */
        private void fit(Canvas c, Bitmap b, RectF in, float margin) {
            float w = in.width() - 2 * margin, h = in.height() - 2 * margin;
            float k = Math.min(w / b.getWidth(), h / b.getHeight());
            float dw = b.getWidth() * k, dh = b.getHeight() * k;
            r.set(in.centerX() - dw / 2f, in.centerY() - dh / 2f, in.centerX() + dw / 2f, in.centerY() + dh / 2f);
            c.drawBitmap(b, null, r, bmp);
        }

        // ---- Ask AI -----------------------------------------------------------------------------------------------

        private void drawAsk(Canvas c, float body) {
            float left = CARD_LEFT + PAD, right = CARD_LEFT + CARD_W - PAD;
            float y = body + 6f;
            drawBig(c, left, y, aiArt, asking ? "Claude is designing it…" : ai == null ? "Your logo from Claude shows here" : "");
            float ix = left + BIG_W + 32f, room = right - ix;
            c.drawText(ai != null ? "Claude's design" : "Ask Claude", Ui.f(ix), Ui.f(y + 40f), bigP);
            String n = aiNote.isEmpty() ? (AiKey.has(tv) ? "Pick a quick ask, or type your own words: Claude designs a logo for "
                    + plainName + " and the TV draws it." : AiSettings.NO_KEY) : aiNote;
            if (note == null || !n.equals(noteFor)) {
                note = Ui.layout(n, aboutP, Ui.px(room), 7);
                noteFor = n;
            }
            aboutP.setColor(Look07.L2);
            Ui.draw(c, note, Ui.f(ix), Ui.f(y + 58f));
            aboutP.setColor(Look07.L3);
            float ry = y + BIG_H + 24f;
            int k = 0;
            if (ai != null) {
                drawAskRow(c, "Use this logo for " + plainName, ry, k == askAt && !onTabs, true);
                ry += ASK_ROW;
                k++;
            }
            for (int i = 0; i < ASKS.length; i++, k++) {
                drawAskRow(c, (i == ASKS.length - 1 ? "" : "Ask:  ") + ASKS[i], ry, k == askAt && !onTabs, false);
                ry += ASK_ROW;
            }
        }

        private void drawAskRow(Canvas c, String words, float y, boolean lit, boolean use) {
            float left = CARD_LEFT + PAD, right = CARD_LEFT + CARD_W - PAD;
            r.set(Ui.f(left), Ui.f(y), Ui.f(right), Ui.f(y + ASK_ROW - 8f));
            float rad = Ui.f(Look07.R_ROW);
            if (lit) {
                fill.setColor(Look07.FOCUS_FILL);
                c.drawRoundRect(r, rad, rad, fill);
            } else if (use) {
                fill.setColor(Look07.SELECTED);
                c.drawRoundRect(r, rad, rad, fill);
            }
            if (use) Icons07.draw(c, Icons07.CHECK, Ui.f(left + 28f), r.centerY(), Ui.f(24f), lit ? Look07.INK : Look07.GREEN);
            titleP.setColor(lit ? Look07.INK : Look07.L1);
            c.drawText(Ui.fit(words, titleP, Ui.f(CARD_W - 2 * PAD - 80f)), Ui.f(left + (use ? 56f : 22f)), Type.centred(titleP, r.centerY()), titleP);
        }
    }

    /** Pure: a mark's name in words ("CHEVRON" to "Chevron"). */
    static String cap(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase(Locale.UK);
    }
}
