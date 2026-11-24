package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.gathin.broadcast.LogoColour;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Homefield TV's live TV cards in Home's row while the dock is on Homefield TV (launcher v2), in the row TiviMate's
 * channels on now have and in their look: the programme's picture filling the card, the channel's logo on a chip of its
 * own colour, the title, its times and a bar for how far through; the hero above with the picture, the logo, how long is
 * left, what is next and what it is about. A programme with no picture: the channel's logo on its own colour instead.
 *
 * <p>What they are, from Homefield TV itself (its ChannelCardsProvider, a signature permission both apps declare): the
 * channels watched last on this TV (newest first), the favourites, then the sport on now, a channel once. OK on a card
 * opens Homefield TV straight on that channel (its PLAY_CHANNEL intent, by the channel's key, its name and number as
 * fallbacks): no search, no accessibility helper, nothing typed, as TiviMate's cards need.
 *
 * <p>Quick: read off the main thread when Home comes up and when the dock comes near Homefield TV, so the row is ready
 * when the dock lands on it; read again every REFRESH_MS while it shows (the bars move on in place); pictures fetched
 * ahead and decoded at the size they are drawn (FIT). A Homefield TV too old to have the cards (or none on the TV, or a
 * provider that says no): null, and the row is the plain app's, as before.
 */
final class HomefieldCards {
    static final String PKG = "com.gathin.homefieldtv";
    static final String AUTHORITY = "com.gathin.homefieldtv.cards";
    /** Its cards, all of them (made when asked: nothing Android's when the class is loaded, so the tests load it). */
    static Uri uri() {
        return Uri.parse("content://" + AUTHORITY + "/cards");
    }

    static final String ACTION = "com.gathin.homefieldtv.action.PLAY_CHANNEL";
    static final String EXTRA_KEY = "com.gathin.homefieldtv.extra.CHANNEL_KEY", EXTRA_NAME = "com.gathin.homefieldtv.extra.CHANNEL_NAME",
            EXTRA_NUMBER = "com.gathin.homefieldtv.extra.CHANNEL_NUMBER", EXTRA_AT = "com.gathin.homefieldtv.extra.AT";
    static final String RECENT = "recent", FAVOURITE = "favourite", SPORT = "sport";
    /** The most cards in the row. */
    static final int MAX = 30;
    /** A picture decoded to the very width it is drawn at (HomeV2.fetch). */
    static final String FIT = "#fit";
    /** A card drawn for a programme with no picture: "hfchan:top,foot|logo|name" (HomeV2.fetch draws it: art). */
    static final String ART = "hfchan:";
    /** Read again at most this often when the dock comes near; every REFRESH_MS while the row shows. */
    static final long FRESH_MS = 10_000L, REFRESH_MS = 30_000L;

    // ---- pure: the cards ---------------------------------------------------------------------------------------------------

    /** One card: its section, the channel (key, name, number, logo, logo colours), and what is on now and next. */
    static final class Card {
        String section = "", key = "", name = "", logo = "";
        int number;
        /** The logo's tile colours {top, foot} from Homefield TV, or null when it hasn't worked them out yet. */
        int[] colours;
        String nowTitle, nowPicture, nowDesc, nextTitle, nextPicture;
        long nowStart, nowEnd, nextStart, nextEnd;

        boolean hasNow() {
            return nowTitle != null && !nowTitle.isEmpty() && nowEnd > nowStart;
        }

        boolean hasPicture() {
            return nowPicture != null && nowPicture.startsWith("http");
        }
    }

    /** The provider's rows, one at a time (a Cursor on the TV, a list in the tests). */
    interface Rows {
        boolean next();

        String str(String column);

        long num(String column);
    }

    /**
     * The cards in the provider's rows: recent, then favourites, then sport, as it gives them; a channel once (its first
     * place kept), MAX at most; null when there are no rows to read (no provider: the plain app's row instead).
     */
    static List<Card> parse(Rows rows) {
        if (rows == null) return null;
        List<Card> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (out.size() < MAX && rows.next()) {
            Card c = new Card();
            c.key = str(rows.str("channel_key"));
            if (c.key.isEmpty() || !seen.add(c.key)) continue;
            c.section = str(rows.str("section"));
            c.name = str(rows.str("channel_name"));
            c.number = (int) rows.num("channel_number");
            c.logo = str(rows.str("logo_url"));
            int top = (int) rows.num("logo_colour"), foot = (int) rows.num("logo_colour_foot");
            c.colours = top != 0 || foot != 0 ? new int[]{top, foot} : null;
            c.nowTitle = rows.str("now_title");
            c.nowStart = rows.num("now_start");
            c.nowEnd = rows.num("now_end");
            c.nowPicture = rows.str("now_picture");
            c.nowDesc = rows.str("now_description");
            c.nextTitle = rows.str("next_title");
            c.nextStart = rows.num("next_start");
            c.nextEnd = rows.num("next_end");
            c.nextPicture = rows.str("next_picture");
            out.add(c);
        }
        return out;
    }

    private static String str(String s) {
        return s == null ? "" : s.trim();
    }

    /** What the card's section is called in the hero's line. */
    static String sectionWords(String section) {
        return RECENT.equals(section) ? "Recently watched" : FAVOURITE.equals(section) ? "Favourite" : SPORT.equals(section) ? "Sport on now" : "";
    }

    /** The card's title: the programme on now, else the channel's name. */
    static String title(Card c) {
        return c.hasNow() ? c.nowTitle : c.name;
    }

    /** Under the title on the card: "On now · 20:00 – 21:00" as TiviMate's cards, else the section ("Recently watched"). */
    static String sub(Card c, ZoneId zone) {
        if (!c.hasNow()) return sectionWords(c.section);
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm").withZone(zone);
        return "On now · " + hm.format(Instant.ofEpochMilli(c.nowStart)) + " – " + hm.format(Instant.ofEpochMilli(c.nowEnd));
    }

    /** How far through the programme on now (0 to 1), or -1 with none. */
    static float progress(Card c, long now) {
        if (!c.hasNow()) return -1f;
        return Math.max(0f, Math.min(1f, (now - c.nowStart) / (float) Math.max(1, c.nowEnd - c.nowStart)));
    }

    /** The hero's line: "Recently watched · Kestrel One · 23 min left · Next: Harbour Lights". */
    static String heroMeta(Card c, long now) {
        StringBuilder b = new StringBuilder(sectionWords(c.section));
        if (!c.name.isEmpty()) b.append(b.length() > 0 ? " · " : "").append(c.name);
        if (c.hasNow() && now < c.nowEnd) b.append(" · ").append(Math.max(1, (c.nowEnd - now + 59_999L) / 60_000L)).append(" min left");
        if (c.nextTitle != null && !c.nextTitle.isEmpty()) b.append(" · Next: ").append(c.nextTitle);
        return b.toString();
    }

    /** What OK sends Homefield TV: {name, value} pairs, the number as a number (Integer) and when as a Long. */
    static List<Object[]> extras(Card c, long now) {
        List<Object[]> out = new ArrayList<>();
        out.add(new Object[]{EXTRA_KEY, c.key});
        if (!c.name.isEmpty()) out.add(new Object[]{EXTRA_NAME, c.name});
        if (c.number > 0) out.add(new Object[]{EXTRA_NUMBER, c.number});
        out.add(new Object[]{EXTRA_AT, now});
        return out;
    }

    /** The card's picture: the programme's, decoded at its size; else the logo on its colour, drawn here (art). */
    static String picture(Card c) {
        return c.hasPicture() ? c.nowPicture + FIT : artSpec(c);
    }

    /** The hero's picture: the programme's larger copy (Sky's 1280 for its 640), else the logo on its colour. */
    static String heroPicture(Card c) {
        return c.hasPicture() ? c.nowPicture.replace("/16-9/640", "/16-9/1280") : artSpec(c);
    }

    static String artSpec(Card c) {
        String cols = c.colours == null ? "0,0" : Integer.toHexString(c.colours[0]) + "," + Integer.toHexString(c.colours[1]);
        return ART + cols + "|" + c.logo.replace("|", "%7C") + "|" + c.name;
    }

    /** Whether two rows are the same cards with the same programmes (only their bars would move): kept as they are. */
    static boolean same(List<HomeV2.Tile> a, List<HomeV2.Tile> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            HomeV2.Tile x = a.get(i), y = b.get(i);
            if (!eq(x.key, y.key) || !eq(x.title, y.title) || !eq(x.image, y.image) || !eq(x.sub, y.sub)) return false;
        }
        return true;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    // ---- on the TV -------------------------------------------------------------------------------------------------------

    private final HomeV2 h;
    /** When the cards were last read (elapsed), and whether a read is under way. Main thread. */
    private long readAt = -FRESH_MS;
    private boolean reading;
    /** Whether Homefield TV's row is the one showing (its refresh runs). */
    private boolean showing;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!showing) return;
            read(true);
            h.main.postDelayed(this, REFRESH_MS);
        }
    };

    HomefieldCards(HomeV2 h) {
        this.h = h;
    }

    /** Whether Homefield TV is on the dock (and so on the TV). */
    boolean onDock() {
        for (View v : h.railViews) if (PKG.equals(v.getTag())) return true;
        return false;
    }

    /** Home came up: the cards read again (a channel watched meanwhile is first now); the refresh again if they show. */
    void resume() {
        read(true);
        if (showing) {
            h.main.removeCallbacks(refresh);
            h.main.postDelayed(refresh, REFRESH_MS);
        }
    }

    /** Home went: nothing read until it is back. */
    void stop() {
        h.main.removeCallbacks(refresh);
    }

    /** The dock's focus moved to the app at place i: Homefield TV's cards read ahead when it is next to it (or it). */
    void near(int i) {
        for (int k = i - 1; k <= i + 1; k++) {
            if (k < 0 || k >= h.railViews.size()) continue;
            if (PKG.equals(h.railViews.get(k).getTag())) {
                read(false);
                return;
            }
        }
    }

    /** The row now shows m's tiles: Homefield TV's kept fresh while they show. */
    void rowFor(String m) {
        boolean now = PKG.equals(m);
        if (now == showing) return;
        showing = now;
        h.main.removeCallbacks(refresh);
        if (now) h.main.postDelayed(refresh, REFRESH_MS);
    }

    /** The cards read off the main thread (unless read a moment ago), handed to Home's row, their pictures fetched ahead. */
    private void read(boolean force) {
        if (!onDock()) return;
        long t = SystemClock.elapsedRealtime();
        if (reading || !force && t - readAt < FRESH_MS) return;
        reading = true;
        h.io.execute(() -> {
            List<HomeV2.Tile> tiles = tiles();
            h.main.post(() -> {
                reading = false;
                readAt = SystemClock.elapsedRealtime();
                if (tiles != null) h.homefieldCards(tiles);
            });
            if (tiles == null) return;
            // The first cards' pictures ready before the row is shown, at the width the row draws them.
            for (int i = 0; i < Math.min(5, tiles.size()); i++) h.prefetch(tiles.get(i).image, h.px(h.TILE_W));
        });
    }

    /** Off the main thread: Homefield TV's cards as Home's tiles; null when it can't say (the plain app's row then). */
    List<HomeV2.Tile> tiles() {
        List<Card> cards = query(h.a.getContentResolver());
        if (cards == null) return null;
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        List<HomeV2.Tile> out = new ArrayList<>();
        for (Card c : cards) out.add(tile(c, now, zone));
        return out;
    }

    private static List<Card> query(ContentResolver cr) {
        try (Cursor c = cr.query(uri(), null, null, null, null)) {
            if (c == null) return null;
            return parse(new Rows() {
                @Override public boolean next() {
                    return c.moveToNext();
                }

                @Override public String str(String column) {
                    int i = c.getColumnIndex(column);
                    return i < 0 || c.isNull(i) ? null : c.getString(i);
                }

                @Override public long num(String column) {
                    int i = c.getColumnIndex(column);
                    return i < 0 || c.isNull(i) ? 0 : c.getLong(i);
                }
            });
        } catch (RuntimeException e) {
            // No such provider (an older Homefield TV), or not allowed (another key): the plain row, quietly.
            return null;
        }
    }

    /** A card as Home's tile, as TiviMate's channels on now are made (HomeV2.liveNow). */
    private HomeV2.Tile tile(Card c, long now, ZoneId zone) {
        HomeV2.Tile t = new HomeV2.Tile();
        t.key = c.key;
        t.title = title(c);
        t.image = picture(c);
        t.heroImage = heroPicture(c);
        t.sub = sub(c, zone);
        // The logo on a chip of its own colour under the picture; on the drawn card the logo is the picture itself.
        if (c.hasPicture() && !c.logo.isEmpty()) {
            t.badge = c.colours != null ? c.logo : ChannelTiles.colourUrl(c.logo);
            t.badgeColours = c.colours;
        }
        t.heroTop = c.logo.startsWith("http") ? c.logo : null;
        t.heroTitle = t.title;
        t.heroMeta = heroMeta(c, now);
        t.heroText = c.nowDesc == null || c.nowDesc.isEmpty() ? null : c.nowDesc;
        t.progress = progress(c, now);
        t.channel = c.name;
        t.ok = () -> play(c);
        return t;
    }

    /** OK on a card: Homefield TV straight on its channel; Homefield TV opened as usual if it can't be asked. */
    void play(Card c) {
        Intent i = new Intent(ACTION).setPackage(PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        for (Object[] e : extras(c, System.currentTimeMillis())) {
            if (e[1] instanceof Integer) i.putExtra((String) e[0], (int) (Integer) e[1]);
            else if (e[1] instanceof Long) i.putExtra((String) e[0], (long) (Long) e[1]);
            else i.putExtra((String) e[0], String.valueOf(e[1]));
        }
        try {
            h.a.startActivity(i);
            GoalWatchService.onAppOpened(h.a, PKG);
        } catch (ActivityNotFoundException | SecurityException e) {
            h.openApp(PKG);
        }
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------------

    /**
     * The logo's chip in a card's caption (fillCaption): a rounded tile of the logo's own colours, as the launcher's
     * channel tiles have them (ChannelTiles); its colours worked out from the logo as it comes when Homefield TV hadn't.
     */
    static void chip(HomeV2 h, ImageView v, LinearLayout.LayoutParams lp, HomeV2.Tile t) {
        int[] cols = t.badgeColours != null ? t.badgeColours : ChannelTiles.colourOf(t.badge);
        v.setBackground(ChannelTiles.tile(cols != null ? cols : ChannelTiles.waiting(), h.px(8)));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setPadding(h.px(10), h.px(6), h.px(10), h.px(6));
        v.setMaxHeight(h.px(36));
        v.setMaxWidth(h.px(180));
        lp.height = h.px(36);
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    /** The chip's colours once the logo is in (when they were worked out from it). */
    static void chipLoaded(HomeV2 h, ImageView v, HomeV2.Tile t) {
        if (t.badgeColours != null) return;
        int[] got = ChannelTiles.colourOf(t.badge);
        if (got != null) v.setBackground(ChannelTiles.tile(got, h.px(8)));
    }

    /**
     * A card for a programme with no picture (artSpec), width px wide at 16:9: the logo's colours falling from top to
     * foot (worked out from the logo when not given), a soft light across the top, the logo in the middle at its own
     * shape (the channel's name in its place when it has none). Off the main thread; logo fetches the logo at a width.
     */
    static Bitmap art(String spec, int width, java.util.function.BiFunction<String, Integer, Bitmap> logo) {
        String[] p = spec.substring(ART.length()).split("\\|", 3);
        int[] cols = colours(p[0]);
        String url = p.length > 1 ? p[1].replace("%7C", "|") : "", name = p.length > 2 ? p[2] : "";
        int w = Math.max(64, width), hgt = Math.round(w * 9f / 16f);
        Bitmap mark = url.isEmpty() ? null : logo.apply(url, Math.round(w * 0.5f));
        if (cols == null) cols = mark != null ? LogoColour.tile(mark) : LogoColour.waiting();
        Bitmap out = Bitmap.createBitmap(w, hgt, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        fill.setShader(new LinearGradient(0, 0, 0, hgt, cols[0], cols[1], Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, hgt, fill);
        fill.setShader(new LinearGradient(0, 0, 0, hgt * 0.45f, 0x24FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, hgt * 0.45f, fill);
        if (mark != null) {
            float bw = w * 0.46f, bh = hgt * 0.4f, k = Math.min(bw / mark.getWidth(), bh / mark.getHeight());
            float dw = mark.getWidth() * k, dh = mark.getHeight() * k, x = (w - dw) / 2f, y = (hgt - dh) / 2f - hgt * 0.04f;
            cv.drawBitmap(mark, null, new RectF(x, y, x + dw, y + dh), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            mark.recycle();
        } else if (!name.isEmpty()) {
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(Color.WHITE);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setFakeBoldText(true);
            tp.setTextSize(hgt * 0.16f);
            float fit = tp.measureText(name);
            if (fit > w * 0.8f) tp.setTextSize(tp.getTextSize() * w * 0.8f / fit);
            cv.drawText(name, w / 2f, hgt / 2f - (tp.ascent() + tp.descent()) / 2f - hgt * 0.04f, tp);
        }
        return out;
    }

    /** "top,foot" in hex ("0,0": not known), or null. */
    static int[] colours(String s) {
        String[] p = s == null ? new String[0] : s.split(",");
        if (p.length < 2) return null;
        try {
            int top = (int) Long.parseLong(p[0].trim(), 16), foot = (int) Long.parseLong(p[1].trim(), 16);
            return top == 0 && foot == 0 ? null : new int[]{top, foot};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
