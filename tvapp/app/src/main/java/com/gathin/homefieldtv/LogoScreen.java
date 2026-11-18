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

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.LogoMatch;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.util.ArrayList;
import java.util.List;

/**
 * Change logo, from a channel's menu in the TV guide: the new logos that may be the channel's, the best first, each on
 * its tile as the guide draws it (Logos), with its name, its country and kind ("United Kingdom  ·  HD") and how sure the
 * match is, then the playlist's own logo; the one shown now ticked. In the 07 look (still 08): a frosted card 552 wide
 * beside the channel's menu, from (760, 432) or higher when it has more logos, rounded 36, "CHANGE LOGO" over the
 * channel's name, each logo's line 84 high, rounded 20; the one shown now marked with a light ring and a green tick, the
 * remote's line white. Up and Down move, OK keeps one for this channel (ChannelEdits: the best match is kept as
 * "automatic", so a newer list of logos can still change it), Back leaves it as it was.
 */
final class LogoScreen extends Screen {
    /** The card (the mock-ups' .ep-logos): its left, its lowest top, its width, its foot's margin (design pixels). */
    static final float CARD_W = 552f, CARD_LEFT = 760f, CARD_TOP = 432f, CARD_FOOT = 1044f, CARD_MAX_H = 1080f - 2 * 48f;
    /** Its head (CHANGE LOGO, the name), each line (84 and 6 between) and each logo's tile (design pixels). */
    static final float HEAD = 104f, ROW = 90f, TILE_W = 112f, TILE_H = 62f, PAD = 22f;
    static final String PLAYLIST = ChannelEdits.LOGO_PLAYLIST;
    static final String NONE = "No new logo matches this channel's name. A channel renamed is matched by its new name, "
            + "then by the playlist's.";

    private final Channel ch;
    /** The logos found (null while they are looked for). */
    private List<LogoMatch.Found> found;
    private final List<String[]> rows = new ArrayList<>();
    private int focus;
    private float scroll;
    private Card card;

    LogoScreen(TvActivity tv, Channel ch) {
        super(tv);
        this.ch = ch;
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

    /** What the channel shows now: a new logo's path, or the playlist's. */
    private String shownNow() {
        String choice = ChannelEdits.logo(ch.key);
        if (!choice.isEmpty()) return choice;
        String auto = LogoPack.on() ? LogoPack.auto(ch) : null;
        return auto == null ? PLAYLIST : auto;
    }

    // ---- the remote -------------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_UP && focus > 0) focus--;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && focus < rows.size() - 1) focus++;
        else return true;
        settle();
        card.invalidate();
        return true;
    }

    @Override void ok() {
        if (focus < 0 || focus >= rows.size()) return;
        choose(rows.get(focus)[0]);
    }

    /** One kept for the channel: the best match as automatic, the playlist's as itself unless it would show anyway. */
    void choose(String path) {
        String auto = LogoPack.auto(ch);
        String keep;
        if (PLAYLIST.equals(path)) keep = LogoPack.on() && auto != null ? PLAYLIST : "";
        else keep = LogoPack.on() && path.equals(auto) ? "" : path;
        ChannelEdits.setLogo(tv, ch.key, keep);
        tv.pop();
    }

    /** The card's height for its lines (design px), at most CARD_MAX_H; its top, as low as 432 and never past the foot. */
    private float cardH() {
        int n = rows.size() + (found == null || found.isEmpty() ? 1 : 0);
        return Math.min(CARD_MAX_H, HEAD + n * ROW + PAD);
    }

    private float cardTop() {
        return Math.min(CARD_TOP, CARD_FOOT - cardH());
    }

    /** The list slides so the remote's row is in sight. */
    private void settle() {
        float room = cardH() - HEAD - PAD, y0 = focus * ROW, y1 = y0 + ROW, total = rows.size() * ROW;
        scroll = RowList.scrollFor(scroll, y0, y1, room, total);
    }

    // ---- what the tests and previews look at ------------------------------------------------------------------------

    int count() {
        return rows.size();
    }

    int focus() {
        return focus;
    }

    String path(int i) {
        return rows.get(i)[0];
    }

    // ---- the card ---------------------------------------------------------------------------------------------------

    private final class Card extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint headP = Type.label(14f, Look07.L3), nameP = Type.paint(26f, Type.BOLD, Look07.L1);
        private final TextPaint titleP = Type.paint(20f, Type.SEMIBOLD, Look07.L1), aboutP = Type.paint(16f, Type.MEDIUM, Look07.L3);
        private final Frost frost = new Frost(Look07.MAT_DARK);
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate;
        private StaticLayout none;

        Card(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            Shadows.prepare(Look07.SH_ROW, 20f);
        }

        @Override protected void onDraw(Canvas c) {
            fill.setColor(Look07.SCRIM);
            c.drawRect(0, 0, getWidth(), getHeight(), fill);
            float top0 = cardTop(), h = cardH();
            r.set(Ui.f(CARD_LEFT), Ui.f(top0), Ui.f(CARD_LEFT + CARD_W), Ui.f(top0 + h));
            frost.draw(c, r, Ui.f(Look07.R_LOGO_CARD), 1f);
            float tx = CARD_LEFT + PAD + 6f;
            c.drawText("CHANGE LOGO", Ui.f(tx), Ui.f(top0 + PAD + 22f), headP);
            c.drawText(Ui.fit(ChannelEdits.name(ch), nameP, Ui.f(CARD_W - 2 * PAD - 12f)), Ui.f(tx), Ui.f(top0 + PAD + 60f), nameP);
            c.save();
            c.clipRect(Ui.f(CARD_LEFT), Ui.f(top0 + HEAD - 6f), Ui.f(CARD_LEFT + CARD_W), Ui.f(top0 + h - 8f));
            float top = top0 + HEAD - scroll;
            for (int i = 0; i < rows.size(); i++) {
                float y = top + i * ROW;
                if (y > top0 + h || y + ROW < top0 + HEAD) continue;
                drawRow(c, rows.get(i), y, i == focus);
            }
            if (found == null) {
                c.drawText("Looking for logos…", Ui.f(tx), Ui.f(top + rows.size() * ROW + 40f), aboutP);
            } else if (found.isEmpty()) {
                if (none == null) none = Ui.layout(NONE, aboutP, Ui.px(CARD_W - 2 * PAD - 12f), 3);
                Ui.draw(c, none, Ui.f(tx), Ui.f(top + rows.size() * ROW + 12f));
            }
            c.restore();
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
                float sw = Math.max(1f, Ui.f(2f));
                ring.setStrokeWidth(sw);
                ring.setColor(0xB3FFFFFF);
                c.drawRoundRect(r.left + sw / 2f, r.top + sw / 2f, r.right - sw / 2f, r.bottom - sw / 2f, rad, rad, ring);
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
    }
}
