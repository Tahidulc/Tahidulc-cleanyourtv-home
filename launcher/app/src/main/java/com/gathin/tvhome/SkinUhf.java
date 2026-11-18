package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The UHF style (after UHF on Apple TV): the focused title's picture fills the screen behind everything and changes as
 * the focus moves, darkened from the left and the foot; round glass buttons down the left that open into a frosted list
 * with names; a billboard (its pill, the title's logo, the facts, Resume, More info and a round next button, with the
 * favourite channels under it) that gives way to a preview of whatever is focused once Down goes into the rows. Movies,
 * TV shows, Kids and Sports are pages of the same kind; the TV guide is UHF's (SkinUhfGuide); Apps and My list are grids.
 * White marks the focus everywhere; red is for what is live.
 */
final class SkinUhf extends Skin {
    // ---- look -------------------------------------------------------------------------------------------------------
    static final int BG = 0xFF0B0B0D, TILE = 0xFF1C1C20, GLASS = 0x8C46464C, GLASS_OPEN = 0x80A0A0A8, GLASS_HI = 0x29FFFFFF;
    static final int TEXT = 0xFFFFFFFF, TEXT2 = 0xA8EBEBF5, TEXT3 = 0x66EBEBF5, RED = 0xFFFF3B30, HOT = 0xFFFF4A1C, DARK = 0xFF111111;

    /** The content's left edge (clear of the rail's buttons) and right edge. */
    static final int LEFT = 176, RIGHT = 1864;

    // Row kinds: their tile's size (design pixels) and the height a row of them takes under its heading.
    static final int CHAN = 0, WIDE = 1, FX = 2, POSTER = 3, TXT = 4, APP = 5;
    private static final int[] KW = {320, 400, 400, 210, 400, 300};
    private static final int[] KH = {180, 225, 225, 315, 225, 170};
    /** A caption under the tile (name and line): wide, fixture and poster tiles. */
    private static final boolean[] CAPTION = {false, true, true, true, false, false};
    private static final int GAP = 24, HEAD = 48, ROW_GAP = 50, CAP_H = 62;

    // Zones: where the focus is.
    private static final int Z_RAIL = 0, Z_HERO = 1, Z_ROWS = 2, Z_GRID = 3, Z_GUIDE = 4;

    static {
        SkinIcons.add("tick", "M4.5 12.5l5 5L19.5 7", null);
    }

    // ---- the rail ---------------------------------------------------------------------------------------------------
    private static final String[][] NAV = {
            {"home", "home", "Home"}, {"search", "search", "Search"},
            {"mylist", "heart", "My List"}, {"guide", "live", "TV Guide"}, {"shows", "shows", "TV Shows"}, {"movies", "movies", "Movies"},
            {"sports", "sports", "Sports"}, {"kids", "kids", "Kids"}, {"apps", "apps", "Apps"},
            {"cc", "sliders", "Control Centre"}, {"style", "style", "Launcher style"}};
    /** A gap above these (the rail's groups). */
    private static final int[] NAV_GAP_BEFORE = {2, 9};

    private FrameLayout root, content, railView, bgA, bgB;
    private View scrim, scrim2, wash, railSheet;
    private final List<RailItem> rail = new ArrayList<>();
    private int railAt, railFrom = Z_HERO;
    private int zone = Z_HERO;
    private String page = "home";
    private boolean held;

    // The status corner.
    private TextView clock, weather, tickTag, tickText;
    private List<String[]> tickLines = new ArrayList<>();
    private int tickAt;

    // The pages.
    private RowPage rows;
    private GridPage grid;
    private SkinUhfGuide guide;
    private final java.util.Map<String, RowPage> kept = new java.util.HashMap<>();

    SkinUhf(SkinHost host) {
        super(host);
    }

    // ---- building ---------------------------------------------------------------------------------------------------

    @Override View build() {
        root = new FrameLayout(c);
        root.setBackgroundColor(BG);
        root.setClipChildren(false);
        bgA = layer();
        bgB = layer();
        root.addView(bgA, full());
        root.addView(bgB, full());
        scrim = new View(c);
        scrim.setBackground(new LayerShade(new Shade(0, 0, 1, 0, new int[]{0xF20B0B0D, 0xCC0B0B0D, 0x380B0B0D, 0x000B0B0D}, new float[]{0, 0.24f, 0.52f, 0.7f}),
                new Shade(0, 1, 0, 0, new int[]{0xF70B0B0D, 0x990B0B0D, 0x000B0B0D}, new float[]{0, 0.22f, 0.42f})));
        root.addView(scrim, full());
        scrim2 = new View(c);
        scrim2.setBackground(new Shade(0, 1, 0, 0, new int[]{0xFF0B0B0D, 0xF00B0B0D, 0x730B0B0D, 0x330B0B0D}, new float[]{0, 0.46f, 0.66f, 1f}));
        scrim2.setAlpha(0f);
        root.addView(scrim2, full());
        wash = new View(c);
        wash.setBackground(new LayerShade(new Shade(1, 0, 0.3f, 0.7f, new int[]{0x22FF4A1C, 0x00FF4A1C}, new float[]{0, 1}),
                new Shade(0, 1, 0.5f, 0.3f, new int[]{0x145A5AFF, 0x005A5AFF}, new float[]{0, 1})));
        wash.setAlpha(0f);
        root.addView(wash, full());
        content = new FrameLayout(c);
        content.setClipChildren(false);
        root.addView(content, full());
        buildStatus();
        buildRail();
        open("home", false);
        return root;
    }

    private FrameLayout layer() {
        FrameLayout f = new FrameLayout(c);
        f.setAlpha(0f);
        return f;
    }

    static FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void buildStatus() {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.END);
        clock = text(36, host.display(), TEXT);
        clock.setGravity(Gravity.END);
        clock.setShadowLayer(px(10), 0, px(2), 0x99000000);
        box.addView(clock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout w = new LinearLayout(c);
        w.setGravity(Gravity.CENTER_VERTICAL);
        ImageView cloud = new ImageView(c);
        cloud.setImageDrawable(SkinIcons.get("cloud", TEXT2));
        w.addView(cloud, new LinearLayout.LayoutParams(px(22), px(22)));
        weather = text(19, host.regular(), TEXT2);
        weather.setShadowLayer(px(8), 0, px(1), 0x99000000);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wl.leftMargin = px(8);
        w.addView(weather, wl);
        LinearLayout.LayoutParams wbl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wbl.topMargin = px(8);
        box.addView(w, wbl);
        LinearLayout t = new LinearLayout(c);
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        tickTag = tag("LIVE", RED);
        t.addView(tickTag, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tickText = text(17, host.regular(), TEXT2);
        tickText.setShadowLayer(px(8), 0, px(1), 0x99000000);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = px(10);
        t.addView(tickText, tl);
        tickText.setMaxWidth(px(560));
        LinearLayout.LayoutParams tbl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tbl.topMargin = px(10);
        box.addView(t, tbl);
        statusBox = box;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(700), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        lp.topMargin = px(40);
        lp.rightMargin = px(1920 - RIGHT);
        root.addView(box, lp);
        t.setVisibility(View.INVISIBLE);
        tickRow = t;
        updClock();
    }

    private View statusBox, tickRow;

    /** A small capitals tag on a colour ("LIVE", "TONIGHT", "4K"). */
    TextView tag(String words, int colour) {
        TextView t = text(12, host.semi(), TEXT);
        t.setText(words);
        t.setLetterSpacing(0.06f);
        t.setBackground(fill(6, colour));
        t.setPadding(px(7), px(5), px(7), px(4));
        return t;
    }

    private void updClock() {
        Calendar cal = Calendar.getInstance();
        clock.setText(String.format(Locale.ROOT, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)));
        String w = host.weather();
        weather.setText(w == null || w.isEmpty() ? "Manchester" : w.replace("  ", " "));
    }

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (!live()) return;
            updClock();
            Calendar cal = Calendar.getInstance();
            host.main().postDelayed(this, (60 - cal.get(Calendar.SECOND)) * 1000L + 50);
        }
    };

    private final Runnable tickTurn = new Runnable() {
        @Override public void run() {
            if (!live()) return;
            if (!tickLines.isEmpty()) {
                String[] l = tickLines.get(tickAt++ % tickLines.size());
                tickTag.setText(l[0]);
                tickTag.setBackground(fill(6, "LIVE".equals(l[0]) ? RED : l[0].matches("\\d\\d:\\d\\d") ? HOT : 0x40FFFFFF));
                tickText.setText(l[1]);
                tickRow.setVisibility(View.VISIBLE);
            }
            host.main().postDelayed(this, 5000);
        }
    };

    private final Runnable tickRead = new Runnable() {
        @Override public void run() {
            if (!live()) return;
            host.io().execute(() -> {
                List<String[]> got;
                try {
                    got = host.ticker();
                } catch (Exception e) {
                    got = null;
                }
                List<String[]> done = got;
                host.main().post(() -> {
                    if (done != null && !done.isEmpty() && live()) {
                        tickLines = done;
                        if (tickRow.getVisibility() != View.VISIBLE) {
                            host.main().removeCallbacks(tickTurn);
                            host.main().post(tickTurn);
                        }
                    }
                });
            });
            host.main().postDelayed(this, 10 * 60_000L);
        }
    };

    // ---- the rail ---------------------------------------------------------------------------------------------------

    private final class RailItem {
        final String id;
        final FrameLayout view;
        final View pill, circle;
        final ImageView icon;
        final TextView label;
        final SkinIcons.Icon drawable;

        RailItem(String id, String icon, String name, float y) {
            this.id = id;
            view = new FrameLayout(c);
            pill = new View(c);
            pill.setBackground(fill(32, 0xFFFFFFFF));
            pill.setAlpha(0f);
            view.addView(pill, new FrameLayout.LayoutParams(px(332), px(64)));
            circle = new View(c);
            circle.setBackground(circle(GLASS));
            view.addView(circle, new FrameLayout.LayoutParams(px(64), px(64)));
            this.icon = new ImageView(c);
            drawable = (SkinIcons.Icon) SkinIcons.get(icon, TEXT, 2f);
            this.icon.setImageDrawable(drawable);
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(px(26), px(26));
            il.leftMargin = px(19);
            il.topMargin = px(19);
            view.addView(this.icon, il);
            label = text(23, host.semi(), TEXT);
            label.setText(name);
            label.setAlpha(0f);
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(px(240), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
            ll.leftMargin = px(80);
            view.addView(label, ll);
            add(railView, view, 40, y, 332, 64);
            view.setOnClickListener(v -> {
                railAt = rail.indexOf(this);
                railFrom = zone == Z_RAIL ? railFrom : zone;
                zone = Z_RAIL;
                railOk();
            });
        }
    }

    GradientDrawable circle(int colour) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(colour);
        return g;
    }

    private void buildRail() {
        railView = new FrameLayout(c);
        railSheet = new View(c);
        GradientDrawable sheet = fill(36, 0xE01C1C1F);
        sheet.setStroke(Math.max(1, px(1)), 0x14FFFFFF);
        railSheet.setBackground(sheet);
        railSheet.setAlpha(0f);
        railSheet.setTranslationX(-px(24));
        add(railView, railSheet, 20, 20, 372, 1040);
        float y = 56;
        for (int i = 0; i < NAV.length; i++) {
            for (int g : NAV_GAP_BEFORE) if (g == i) y += 30;
            rail.add(new RailItem(NAV[i][0], NAV[i][1], NAV[i][2], y));
            y += 76;
        }
        root.addView(railView, new FrameLayout.LayoutParams(px(420), ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void paintRail() {
        boolean open = zone == Z_RAIL;
        railSheet.animate().alpha(open ? 1f : 0f).translationX(open ? 0 : -px(24)).setDuration(260).start();
        for (int i = 0; i < rail.size(); i++) {
            RailItem r = rail.get(i);
            boolean focus = open && i == railAt, here = r.id.equals(page);
            r.pill.animate().alpha(focus ? 1f : open && here ? 0.1f : 0f).setDuration(160).start();
            r.circle.setBackground(circle(open ? 0x00000000 : here ? GLASS_OPEN : GLASS));
            r.label.animate().alpha(open ? 1f : 0f).setDuration(220).start();
            r.label.setTextColor(focus ? DARK : TEXT);
            r.drawable.colour(focus ? DARK : TEXT);
        }
    }

    private void toRail() {
        railFrom = zone;
        zone = Z_RAIL;
        railAt = 0;
        for (int i = 0; i < rail.size(); i++) if (rail.get(i).id.equals(page)) railAt = i;
        paint();
    }

    private void railOk() {
        String id = rail.get(railAt).id;
        switch (id) {
            case "search": host.open(OPEN_SEARCH); return;
            case "cc": host.open(OPEN_CONTROL_CENTRE); return;
            case "style": host.open(OPEN_STYLES); return;
            default: open(id, true);
        }
    }

    // ---- pages ------------------------------------------------------------------------------------------------------

    /** A page on screen: Home and the row pages keep their rows (and focus) while the style is up. */
    private void open(String id, boolean fromRail) {
        if (rows != null) rows.letGo();
        page = id;
        content.removeAllViews();
        if (guide != null) { guide.close(); guide = null; }
        rows = null;
        grid = null;
        boolean plain = false;
        switch (id) {
            case "guide":
                guide = new SkinUhfGuide(this);
                content.addView(guide.build(), full());
                zone = Z_GUIDE;
                plain = true;
                break;
            case "apps":
                grid = new GridPage("Apps", ROW_APPS, APP, 4, 380, 170);
                zone = Z_GRID;
                plain = true;
                break;
            case "mylist":
                grid = new GridPage("My List", ROW_MY_LIST, POSTER, 6, 230, 345);
                zone = Z_GRID;
                plain = true;
                break;
            default: {
                RowPage p = kept.get(id);
                if (p == null) {
                    p = new RowPage(id);
                    kept.put(id, p);
                }
                rows = p;
                rows.show();
                zone = Z_HERO;
                break;
            }
        }
        wash.animate().alpha(plain ? 1f : 0f).setDuration(300).start();
        scrim.animate().alpha(plain ? 0f : 1f).setDuration(300).start();
        statusBox.animate().alpha(plain ? 0f : 1f).setDuration(200).start();
        if (plain) setBg(null);
        paint();
    }

    /** Everything that follows the focus, after a move. */
    private void paint() {
        paintRail();
        if (rows != null) rows.paint();
        if (grid != null) grid.paint();
        if (guide != null) guide.paint(zone == Z_GUIDE);
    }

    // ---- the picture behind everything -------------------------------------------------------------------------------

    private String bgKey = "";
    private boolean bgFrontA;
    private Item bgWant;
    private final Runnable bgLater = () -> showBg(bgWant);

    private static String keyOf(Item it) {
        return it == null ? "" : it.kind + "|" + it.name + "|" + it.tmdbId + "|" + it.backdrop + "|" + it.channel;
    }

    /** The focused item's picture across the whole screen (lite: once the focus rests there). */
    void setBg(Item it) {
        bgWant = it;
        host.main().removeCallbacks(bgLater);
        if (keyOf(it).equals(bgKey)) return;
        host.main().postDelayed(bgLater, host.lite() ? 420 : 90);
    }

    private void showBg(Item it) {
        String k = keyOf(it);
        if (k.equals(bgKey) || !live()) return;
        bgKey = k;
        FrameLayout in = bgFrontA ? bgB : bgA, out = bgFrontA ? bgA : bgB;
        bgFrontA = !bgFrontA;
        in.removeAllViews();
        if (it != null) {
            if (it.backdrop != null) {
                Pic p = new Pic(c, 0, BG);
                p.alignY(0.22f);
                in.addView(p, full());
                host.image(p, it.backdrop, 1280);
            } else {
                in.addView(panel(it, true), full());
            }
        }
        int ms = host.lite() ? 180 : 600;
        in.animate().cancel();
        out.animate().cancel();
        // The new picture over the old one, both still under the shades and the content.
        root.removeView(in);
        root.addView(in, 1, full());
        in.setAlpha(0f);
        in.animate().alpha(it == null ? 0f : 1f).setDuration(ms).start();
        out.animate().alpha(0f).setDuration(ms).start();
    }

    /**
     * A picture made of colour and type for something with no picture of its own: a game in its clubs' colours with their
     * letters, a channel in its colour with its logo, an app in its colours, a headline on charcoal.
     */
    View panel(Item it, boolean big) {
        float radius = big ? 0 : 16;
        FrameLayout f = new FrameLayout(c);
        if (Item.SPORT.equals(it.kind)) {
            String[] sides = teams(it.name);
            int a = club(sides[0])[0], b = club(sides[1])[0];
            f.setBackground(new Split(a, b, big ? 0.5f : 0.58f, px(radius)));
            View dim = new View(c);
            dim.setBackground(fill(radius, big ? 0x66000000 : 0x47000000));
            f.addView(dim, full());
            if (big) {
                TextView ab = text(170, host.display(), 0xEBFFFFFF);
                ab.setText(abbr(sides[0]) + "  " + abbr(sides[1]));
                ab.setLetterSpacing(-0.03f);
                ab.setShadowLayer(px(24), 0, px(4), 0x59000000);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.END | Gravity.TOP);
                lp.rightMargin = px(100);
                lp.topMargin = px(190);
                f.addView(ab, lp);
            } else {
                for (int i = 0; i < 2; i++) {
                    TextView ab = text(76, host.display(), 0xF2FFFFFF);
                    ab.setText(abbr(sides[i]));
                    ab.setLetterSpacing(-0.04f);
                    ab.setShadowLayer(px(12), 0, px(4), 0x59000000);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                            (i == 0 ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL);
                    if (i == 0) lp.leftMargin = px(26); else lp.rightMargin = px(26);
                    lp.topMargin = -px(6);
                    f.addView(ab, lp);
                }
            }
            return f;
        }
        // A live channel's tile (ChannelTiles): in its logo's own colour (the default), black, or nothing behind its logo
        // (Behind logos None); a channel with no logo (or anything else without a picture) in its colour.
        boolean live = Item.LIVE.equals(it.kind);
        int behind = live && it.mark != null ? ChannelTiles.behind(c) : -1;
        boolean black = behind == ChannelTiles.TILE, bare = behind == ChannelTiles.NONE, own = behind == ChannelTiles.COLOUR;
        int colour = black ? ChannelTiles.GROUND : live ? channelColour(it.channel) : Item.APP.equals(it.kind) ? appColour(it.name) : 0xFF2A2A2E;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{colour, mix(colour, 0xFF050505, 0.7f)});
        g.setCornerRadius(px(radius));
        String ownUrl = own ? ChannelTiles.colourUrl(it.mark) : null;
        if (own) {
            int[] known = ChannelTiles.colourOf(ownUrl);
            f.setBackground(ChannelTiles.tile(known != null ? known : ChannelTiles.waiting(), px(radius)));
        } else if (!bare) {
            f.setBackground(g);
        }
        String word = live ? shortName(it.channel) : Item.NEWS.equals(it.kind) ? (it.sub != null ? it.sub : "NEWS") : it.name;
        if (live && it.mark != null) {
            ImageView logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setAlpha(big ? 0.3f : 1f);
            FrameLayout.LayoutParams lp = big ? new FrameLayout.LayoutParams(px(640), px(300), Gravity.END | Gravity.CENTER_VERTICAL)
                    : new FrameLayout.LayoutParams(px(200), px(70), Gravity.CENTER);
            if (big) { lp.rightMargin = px(160); lp.topMargin = -px(90); }
            f.addView(logo, lp);
            if (own) {
                host.image(logo, ownUrl, big ? 640 : 240, () -> {
                    int[] got = ChannelTiles.colourOf(ownUrl);
                    if (got != null) f.setBackground(ChannelTiles.tile(got, px(radius)));
                });
            } else {
                host.image(logo, big ? it.mark : black ? ChannelTiles.url(it.mark) : ChannelTiles.haloUrl(it.mark), big ? 640 : 240);
            }
        } else if (word != null) {
            TextView t = text(big ? 170 : 64, host.display(), big ? 0x1FFFFFFF : 0x2EFFFFFF);
            t.setText(word);
            t.setLetterSpacing(-0.04f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END | Gravity.BOTTOM);
            lp.rightMargin = px(big ? 80 : 18);
            lp.bottomMargin = px(big ? 340 : 14);
            f.addView(t, lp);
        }
        return f;
    }

    // ---- a billboard and rows: Home, Movies, TV shows, Kids, Sports ------------------------------------------------------

    /** One row on a page: what it reads, its heading and kind, and its tiles once read. */
    private final class Row {
        final String id, more;
        String title, sub;
        final int kind;
        List<Item> items = new ArrayList<>();
        boolean loaded, loading;
        final FrameLayout view;
        final LinearLayout track;
        final TextView head, subView;
        final List<Cell> cells = new ArrayList<>();
        int ci, top;
        float shift;
        boolean gone;

        Row(String id, String title, String sub, String more, int kind) {
            this.id = id;
            this.title = title;
            this.sub = sub;
            this.more = more;
            this.kind = kind;
            view = new FrameLayout(c);
            view.setClipChildren(false);
            LinearLayout h = new LinearLayout(c);
            h.setGravity(Gravity.BOTTOM);
            head = text(26, host.semi(), TEXT);
            head.setShadowLayer(px(10), 0, px(2), 0x80000000);
            head.setText(title);
            h.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            subView = text(19, host.regular(), TEXT2);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.leftMargin = px(12);
            h.addView(subView, sl);
            subView.setText(sub == null ? "" : sub);
            view.addView(h, new FrameLayout.LayoutParams(px(1400), px(36)));
            if (more != null) {
                TextView m = text(19, host.regular(), TEXT2);
                m.setText(more);
                FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END);
                ml.rightMargin = px(1920 - RIGHT);
                ml.topMargin = px(8);
                view.addView(m, ml);
            }
            track = new LinearLayout(c);
            track.setClipChildren(false);
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(px(6 * (KW[kind] + GAP)), px(KH[kind] + (CAPTION[kind] ? CAP_H : 0)));
            tl.topMargin = px(HEAD);
            view.addView(track, tl);
            for (int i = 0; i < 6; i++) track.addView(skeleton(kind));
        }

        int height() {
            return HEAD + KH[kind] + (CAPTION[kind] ? CAP_H : 0) + ROW_GAP;
        }

        void fill(List<Item> got) {
            items = got;
            track.removeAllViews();
            cells.clear();
            for (Item it : got) {
                Cell cell = cell(it, kind);
                cells.add(cell);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(KW[kind]), px(KH[kind] + (CAPTION[kind] ? CAP_H : 0)));
                lp.rightMargin = px(GAP);
                track.addView(cell.view, lp);
                final Row self = this;
                final int at = cells.size() - 1;
                cell.view.setOnClickListener(v -> {
                    if (rows == null) return;
                    int r = rows.vis().indexOf(self);
                    if (r < 0) return;
                    zone = Z_ROWS;
                    rows.ri = r;
                    ci = at;
                    paint();
                    host.ok(it);
                });
            }
            ViewGroup.LayoutParams tl = track.getLayoutParams();
            tl.width = px(Math.max(6, got.size()) * (KW[kind] + GAP));
            track.setLayoutParams(tl);
            if (ci >= got.size()) ci = Math.max(0, got.size() - 1);
        }
    }

    /** A tile's empty stand-in while its row is read. */
    private View skeleton(int kind) {
        View v = new View(c);
        v.setBackground(fill(16, 0x14FFFFFF));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(KW[kind]), px(KH[kind]));
        lp.rightMargin = px(GAP);
        v.setLayoutParams(lp);
        return v;
    }

    /** A page of a billboard over rows. */
    private final class RowPage {
        final String id;
        final List<Row> all = new ArrayList<>();
        /** The billboard's items and their pills: {Item, label}. */
        final List<Object[]> heroes = new ArrayList<>();
        int heroAt, hb, ri;
        /** The billboard was turned (by hand or by itself): its item is kept when the rows under it change. */
        boolean heroPicked;
        final FrameLayout heroView = new FrameLayout(c), pvView = new FrameLayout(c), rowsView = new FrameLayout(c);
        private String pvKey = "";
        private boolean built;

        RowPage(String id) {
            this.id = id;
            heroView.setClipChildren(false);
            rowsView.setClipChildren(false);
            switch (id) {
                case "home": {
                    int bed = host.bedMinutes();
                    all.add(new Row(ROW_LIVE, "Favourite channels", null, "view all", CHAN));
                    all.add(new Row(ROW_CONTINUE, "Continue watching", null, null, WIDE));
                    all.add(new Row(ROW_SPORT, "Match Day", "live and coming up", null, FX));
                    all.add(new Row(ROW_TOP_TV, "Top 10 TV shows in the UK today", null, null, POSTER));
                    all.add(new Row(ROW_FITS, "Fits tonight", String.format(Locale.ROOT, "ends before bed at %02d:%02d", bed / 60, bed % 60), null, POSTER));
                    all.add(new Row(ROW_YOUR_SHOWS, "Your shows", "new episodes", null, WIDE));
                    all.add(new Row(ROW_DEBRID, "New on Debrid Stream", null, null, POSTER));
                    all.add(new Row(ROW_BECAUSE, "Because you watched", null, null, POSTER));
                    all.add(new Row(ROW_TOP_FILMS, "Top 10 films in the UK today", null, null, POSTER));
                    all.add(new Row(ROW_COMING, "Coming soon", null, null, POSTER));
                    all.add(new Row(ROW_TRAILERS, "Trailers", "on YouTube", null, WIDE));
                    all.add(new Row(ROW_KIDS_TV, "Kids & family", null, null, POSTER));
                    all.add(new Row(ROW_DOCK, "Your apps", null, null, APP));
                    break;
                }
                case "sports":
                    all.add(new Row(ROW_SPORT, "Live and coming up", "on your channels", null, FX));
                    all.add(new Row(ROW_LIVE, "Sport on now", null, null, CHAN));
                    all.add(new Row(ROW_NEWS, "Headlines", "BBC News and BBC Sport", null, TXT));
                    break;
                default: {
                    // Movies, TV shows, Kids: each of the launcher's tabs for the page as a row.
                    List<String> tabs = host.tabs(id);
                    for (int i = 0; i < tabs.size(); i++) {
                        String name = tabs.get(i);
                        boolean wide = name.startsWith("Continue") || name.equals("Trailers") || name.equals("Your shows") || name.equals("New episodes");
                        all.add(new Row(id + "#" + i, name, null, null, wide ? WIDE : POSTER));
                    }
                    break;
                }
            }
        }

        List<Row> vis() {
            List<Row> out = new ArrayList<>();
            for (Row r : all) if (!(r.loaded && r.items.isEmpty())) out.add(r);
            return out;
        }

        void show() {
            if (!built) {
                built = true;
                for (Row r : all) rowsView.addView(r.view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(r.height())));
                for (int i = 0; i < Math.min(id.equals("home") ? all.size() : 5, all.size()); i++) read(all.get(i));
            }
            ViewGroup p = (ViewGroup) heroView.getParent();
            if (p != null) p.removeView(heroView);
            p = (ViewGroup) pvView.getParent();
            if (p != null) p.removeView(pvView);
            p = (ViewGroup) rowsView.getParent();
            if (p != null) p.removeView(rowsView);
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(px(960), px(792));
            hl.leftMargin = px(LEFT);
            content.addView(heroView, hl);
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(px(1040), px(450));
            pl.leftMargin = px(LEFT);
            content.addView(pvView, pl);
            FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(px(1920 - LEFT), ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.leftMargin = px(LEFT);
            content.addView(rowsView, rl);
            ViewGroup.LayoutParams rvl = rowsView.getLayoutParams();
            rvl.height = px(totalHeight());
            rowsView.setLayoutParams(rvl);
            place();
            List<Row> v = vis();
            boolean browse = zone == Z_ROWS;
            if (!v.isEmpty()) rowsView.setTranslationY(px(browse ? 500 - v.get(Math.min(ri, v.size() - 1)).top : 830 - v.get(0).top));
            renderHero(false);
        }

        int totalHeight() {
            int y = 0;
            for (Row r : vis()) y += r.height();
            return Math.max(y, 1080);
        }

        void read(Row r) {
            if (r.loaded || r.loading) return;
            r.loading = true;
            load(r.id, got -> {
                r.loading = false;
                r.loaded = true;
                List<Item> use = got;
                if ("sports".equals(id) && ROW_LIVE.equals(r.id)) use = sportChannels(got);
                if (ROW_BECAUSE.equals(r.id)) {
                    String n = host.rowName(ROW_BECAUSE);
                    if (n != null) { r.title = n; r.head.setText(n); }
                }
                r.fill(use);
                if (use.isEmpty()) r.view.setVisibility(View.GONE);
                place();
                heroesFrom();
                if (rows == this) paint();
            });
        }

        /** Each row's place down the page (empty rows taken out). */
        void place() {
            int y = 0;
            for (Row r : all) {
                if (r.loaded && r.items.isEmpty()) continue;
                r.top = y;
                r.view.setTranslationY(px(y));
                y += r.height();
            }
            ViewGroup.LayoutParams rvl = rowsView.getLayoutParams();
            if (rvl != null) {
                rvl.height = px(Math.max(y, 1080));
                rowsView.setLayoutParams(rvl);
            }
            List<Row> v = vis();
            if (ri >= v.size()) ri = Math.max(0, v.size() - 1);
        }

        /** The billboard's items, from the rows read so far. */
        void heroesFrom() {
            Item had = heroAt < heroes.size() ? (Item) heroes.get(heroAt)[0] : null;
            heroes.clear();
            if (id.equals("home")) {
                Row cw = find(ROW_CONTINUE), sport = find(ROW_SPORT), liveRow = find(ROW_LIVE), tv = find(ROW_TOP_TV), films = find(ROW_TOP_FILMS);
                pick(cw, 0, "Continue watching");
                pick(sport, 0, null);
                pick(cw, 1, "Continue watching");
                if (liveRow != null) {
                    List<Item> sp = sportChannels(liveRow.items);
                    if (!sp.isEmpty() && sp != liveRow.items) heroes.add(new Object[]{sp.get(0), "Live now"});
                    else pick(liveRow, 0, "Live now");
                }
                pick(tv, 1, "#2 in TV shows today");
                pick(films, 1, "#2 in films today");
            } else if (id.equals("sports")) {
                Row s = find(ROW_SPORT);
                for (int i = 0; s != null && i < Math.min(4, s.items.size()); i++) pick(s, i, null);
            } else {
                for (Row r : all) {
                    if (heroes.size() >= 5) break;
                    if (!r.loaded || r.items.isEmpty()) continue;
                    for (Item it : r.items) {
                        if (it.backdrop == null) continue;
                        heroes.add(new Object[]{it, r.title.startsWith("Continue") ? "Continue watching" : r.title});
                        break;
                    }
                }
            }
            heroAt = 0;
            for (int i = 0; heroPicked && had != null && i < heroes.size(); i++) if (heroes.get(i)[0] == had) heroAt = i;
            renderHero(false);
        }

        private Row find(String rowId) {
            for (Row r : all) if (r.id.equals(rowId) && r.loaded) return r;
            return null;
        }

        private void pick(Row r, int i, String label) {
            if (r == null || i >= r.items.size()) return;
            Item it = r.items.get(i);
            heroes.add(new Object[]{it, label != null ? label : sportPill(it)});
        }

        void renderHero(boolean swap) {
            heroView.removeAllViews();
            if (heroes.isEmpty()) return;
            Object[] h = heroes.get(Math.min(heroAt, heroes.size() - 1));
            Item it = (Item) h[0];
            LinearLayout blk = block(it, (String) h[1], true);
            blk.setClipChildren(false);
            blk.setClipToPadding(false);
            LinearLayout btns = new LinearLayout(c);
            btns.setClipChildren(false);
            btns.setPadding(px(4), 0, 0, 0);
            btns.setGravity(Gravity.CENTER_VERTICAL);
            String[] labels = {primary(it), second(it)};
            String[] icons = {"play", "info"};
            for (int i = 0; i < 2; i++) {
                View b = pillButton(icons[i], labels[i], hb == i && zone == Z_HERO && rows == this);
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(66));
                bl.rightMargin = px(16);
                btns.addView(b, bl);
                final int which = i;
                b.setOnClickListener(v -> { zone = Z_HERO; hb = which; heroOk(); });
            }
            View next = roundButton("chev", hb == 2 && zone == Z_HERO && rows == this);
            next.setOnClickListener(v -> { zone = Z_HERO; hb = 2; heroOk(); });
            btns.addView(next, new LinearLayout.LayoutParams(px(66), px(66)));
            LinearLayout dots = new LinearLayout(c);
            dots.setGravity(Gravity.CENTER_VERTICAL);
            for (int i = 0; i < heroes.size(); i++) {
                View d = new View(c);
                d.setBackground(fill(5, i == heroAt ? TEXT : 0x59FFFFFF));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(i == heroAt ? 26 : 9), px(9));
                dl.rightMargin = px(8);
                dots.addView(d, dl);
            }
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.leftMargin = px(26);
            btns.addView(dots, dlp);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.topMargin = px(26);
            blk.addView(btns, bl);
            heroView.addView(blk, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
            if (swap) {
                blk.setAlpha(0f);
                blk.setTranslationX(-px(16));
                blk.animate().alpha(1f).translationX(0).setDuration(host.lite() ? 200 : 420).start();
            }
            if (rows == this && zone != Z_ROWS) setBg(it);
        }

        void renderPv(Item it) {
            String k = keyOf(it);
            if (k.equals(pvKey)) return;
            pvKey = k;
            pvView.removeAllViews();
            if (it == null) return;
            Row r = vis().get(ri);
            String label = it.rank > 0 ? "#" + it.rank + " in " + (r.title.contains("films") ? "films" : "TV shows") + " today"
                    : Item.LIVE.equals(it.kind) ? null : Item.SPORT.equals(it.kind) ? sportPill(it) : it.line != null && it.app != null ? it.line : r.title;
            LinearLayout blk = block(it, label, false);
            pvView.addView(blk, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
        }

        void paint() {
            List<Row> v = vis();
            boolean browse = zone == Z_ROWS || (zone == Z_RAIL && railFrom == Z_ROWS);
            heroView.animate().alpha(browse ? 0f : 1f).translationY(browse ? -px(40) : 0).setDuration(380).start();
            pvView.animate().alpha(browse ? 1f : 0f).setDuration(300).start();
            scrim2.animate().alpha(browse ? 1f : 0f).setDuration(400).start();
            if (v.isEmpty()) return;
            if (ri >= v.size()) ri = v.size() - 1;
            float y = browse ? 500 - v.get(ri).top : 830 - v.get(0).top;
            rowsView.animate().translationY(px(y)).setDuration(420).start();
            for (int i = 0; i < v.size(); i++) {
                Row r = v.get(i);
                boolean gone = browse && i < ri;
                if (r.gone != gone) {
                    r.gone = gone;
                    r.view.animate().alpha(gone ? 0f : 1f).setDuration(260).start();
                }
                r.view.setTranslationZ(i == ri ? 1 : 0);
                float w = KW[r.kind] + GAP;
                float total = r.items.size() * w - GAP;
                float shift = Math.max(0, Math.min(r.ci * w, total - (RIGHT - LEFT)));
                if (shift != r.shift) {
                    r.shift = shift;
                    r.track.animate().translationX(-px(shift)).setDuration(380).start();
                }
                boolean near = browse ? i >= ri - 1 && i <= ri + 2 : i <= 1;
                int first = (int) Math.floor(shift / w), count = (int) Math.ceil((RIGHT - LEFT) / w) + 1;
                for (int j = 0; j < r.cells.size(); j++) {
                    Cell cell = r.cells.get(j);
                    cell.want(near && j >= first - 1 && j <= first + count + 1);
                    cell.focus(zone == Z_ROWS && i == ri && j == r.ci);
                    float a = j * w < shift - 10 ? 0f : 1f;
                    if (cell.view.getAlpha() != a) cell.view.animate().alpha(a).setDuration(200).start();
                }
            }
            if (browse) {
                for (int i = ri; i < Math.min(v.size(), ri + 4); i++) read(v.get(i));
                Row r = v.get(ri);
                Item it = r.ci < r.items.size() ? r.items.get(r.ci) : null;
                if (it != null) {
                    renderPv(it);
                    setBg(it);
                }
            } else if (!heroes.isEmpty()) {
                renderHero(false);
            }
        }

        /** Off screen: every tile's main picture let go (they are read again when the page comes back). */
        void letGo() {
            for (Row r : all) for (Cell cell : r.cells) cell.want(false);
        }

        Item focused() {
            List<Row> v = vis();
            if (ri >= v.size()) return null;
            Row r = v.get(ri);
            return r.ci < r.items.size() ? r.items.get(r.ci) : null;
        }

        void heroOk() {
            if (heroes.isEmpty()) return;
            Item it = (Item) heroes.get(heroAt)[0];
            if (hb == 2) { turn(); return; }
            if (hb == 0) { host.ok(it); return; }
            if (Item.LIVE.equals(it.kind) || Item.SPORT.equals(it.kind)) { open("guide", false); return; }
            host.details(it);
        }

        void turn() {
            if (heroes.isEmpty()) return;
            heroPicked = true;
            heroAt = (heroAt + 1) % heroes.size();
            renderHero(true);
        }

        /** Resume: the rows that age read again. */
        void refresh() {
            for (Row r : all) {
                if (!r.loaded) continue;
                if (!(r.id.equals(ROW_CONTINUE) || r.id.equals(ROW_LIVE) || r.id.equals(ROW_SPORT) || r.id.endsWith("#0"))) continue;
                load(r.id, got -> {
                    List<Item> use = "sports".equals(id) && ROW_LIVE.equals(r.id) ? sportChannels(got) : got;
                    r.fill(use);
                    r.view.setVisibility(use.isEmpty() ? View.GONE : View.VISIBLE);
                    place();
                    heroesFrom();
                    if (rows == this) SkinUhf.this.paint();
                });
            }
        }
    }

    /** The billboard's or the preview's words: pill, logo or name, facts, two lines, and the line in white. */
    private LinearLayout block(Item it, String label, boolean big) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        if (label != null || Item.LIVE.equals(it.kind)) {
            LinearLayout pill = new LinearLayout(c);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable g = fill(12, 0xB81E1E21);
            g.setStroke(Math.max(1, px(1)), 0x1AFFFFFF);
            pill.setBackground(g);
            pill.setPadding(px(14), px(8), px(14), px(8));
            boolean liveNow = Item.LIVE.equals(it.kind) || (label != null && label.startsWith("Live"));
            if (liveNow) {
                View dot = new View(c);
                dot.setBackground(circle(RED));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(10), px(10));
                dl.rightMargin = px(9);
                pill.addView(dot, dl);
            }
            TextView t = text(19, host.semi(), TEXT);
            t.setText(Item.LIVE.equals(it.kind) ? "Live · " + nz(it.channel) : label);
            pill.addView(t);
            b.addView(pill, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        String name = Item.LIVE.equals(it.kind) ? nz(it.name).replaceFirst("^(?i)live:? ", "") : it.name;
        FrameLayout headBox = new FrameLayout(c);
        TextView title = text(big ? 74 : 52, host.display(), TEXT);
        title.setText(name);
        title.setLetterSpacing(-0.02f);
        title.setSingleLine(false);
        title.setMaxLines(2);
        title.setLineSpacing(0, 0.98f);
        title.setMaxWidth(px(big ? 920 : 1000));
        title.setShadowLayer(px(14), 0, px(2), 0x66000000);
        headBox.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        if (it.logo != null) {
            ImageView logo = new ImageView(c);
            logo.setAdjustViewBounds(true);
            logo.setScaleType(ImageView.ScaleType.FIT_START);
            logo.setMaxWidth(px(big ? 600 : 460));
            logo.setMaxHeight(px(big ? 210 : 140));
            logo.setVisibility(View.INVISIBLE);
            headBox.addView(logo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
            host.image(logo, it.logo, big ? 600 : 460, () -> {
                logo.setVisibility(View.VISIBLE);
                title.setVisibility(View.GONE);
            });
        }
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = px(big ? 18 : 14);
        b.addView(headBox, hl);
        View meta = meta(it, big ? 23 : 21);
        if (meta != null) {
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ml.topMargin = px(big ? 22 : 16);
            b.addView(meta, ml);
        }
        String words = Item.NEWS.equals(it.kind) ? null : it.overview;
        if (words != null && !words.trim().isEmpty()) {
            TextView d = text(big ? 21 : 20, host.regular(), 0xCCFFFFFF);
            d.setSingleLine(false);
            d.setMaxLines(2);
            d.setLineSpacing(px(8), 1f);
            d.setMaxWidth(px(820));
            d.setText(words.trim());
            d.setShadowLayer(px(8), 0, px(1), 0x80000000);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(820), ViewGroup.LayoutParams.WRAP_CONTENT);
            dl.topMargin = px(big ? 12 : 10);
            b.addView(d, dl);
        }
        String ex = extra(it);
        if (ex != null) {
            TextView e = text(big ? 20 : 19, host.semi(), TEXT);
            e.setText(ex);
            e.setShadowLayer(px(8), 0, px(1), 0x80000000);
            LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            el.topMargin = px(10);
            b.addView(e, el);
        }
        return b;
    }

    /** The facts line: year, the rating with its star, series or film and its length, the age rating; a game's when and where. */
    private View meta(Item it, float size) {
        // Each part is words, or a Float: the rating, drawn as a star and its number.
        List<Object> parts = new ArrayList<>();
        if (Item.TITLE.equals(it.kind) || Item.VIDEO.equals(it.kind)) {
            if (it.year != null && !it.year.isEmpty()) parts.add(it.year);
            if (it.vote > 0) parts.add((float) it.vote);
            parts.add(it.tv ? "Series" : "Film");
            if (!it.tv && it.runtime > 0) parts.add(span(it.runtime));
            if (it.cert != null && !it.cert.isEmpty()) parts.add(it.cert);
        } else if (Item.LIVE.equals(it.kind)) {
            if (it.sub != null) parts.add(it.sub.replace("On now · ", ""));
            if (it.end > 0) parts.add(Math.max(1, (it.end - System.currentTimeMillis()) / 60_000L) + " min left");
        } else if (Item.SPORT.equals(it.kind)) {
            if (it.line != null && it.sub != null && it.line.contains(it.sub)) parts.add(it.line);
            else {
                if (it.sub != null) parts.add(it.sub);
                if (it.line != null) parts.add(it.line);
            }
        } else if (it.sub != null) {
            parts.add(it.sub);
        }
        if (parts.isEmpty()) return null;
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) addDot(row, size);
            Object o = parts.get(i);
            if (o instanceof Float) {
                ImageView star = new ImageView(c);
                star.setImageDrawable(SkinIcons.get("star", TEXT));
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(px(size * 0.82f), px(size * 0.82f));
                sl.rightMargin = px(6);
                row.addView(star, sl);
                o = String.format(Locale.ROOT, "%.1f", (Float) o);
            }
            TextView t = text(size, host.regular(), TEXT);
            t.setText((String) o);
            t.setShadowLayer(px(8), 0, px(1), 0x80000000);
            row.addView(t);
        }
        return row;
    }

    private void addDot(LinearLayout row, float size) {
        TextView d = text(size, host.regular(), TEXT3);
        d.setText("·");
        d.setPadding(px(12), 0, px(12), 0);
        row.addView(d);
    }

    /** The line in white under a title: how far through it is, when a film would end, when it comes. */
    private String extra(Item it) {
        if (Item.TITLE.equals(it.kind)) {
            if (it.app != null && it.sub != null) return it.sub;
            if (it.sub != null && (it.sub.contains("ends") || it.sub.startsWith("In cinemas") || it.sub.matches("S\\d+:E\\d+.*"))) return it.sub;
            return it.flag;
        }
        if (Item.SPORT.equals(it.kind)) return it.flag;
        if (Item.LIVE.equals(it.kind) && it.line != null && (it.line.contains("left") || it.line.contains("Next"))) return it.line;
        return null;
    }

    private String primary(Item it) {
        if (Item.LIVE.equals(it.kind)) return "Watch live";
        if (Item.SPORT.equals(it.kind)) {
            String f = it.flag == null ? "" : it.flag.toUpperCase(Locale.ROOT);
            return f.contains("LIVE") || f.contains("TONIGHT") ? "Watch" : "Remind me";
        }
        if (Item.APP.equals(it.kind)) return "Open";
        if (it.app != null || it.progress > 0) {
            String ep = it.sub != null && it.sub.matches("S\\d+:E\\d+.*") ? " " + it.sub.split(" ")[0] : "";
            return "Resume" + ep;
        }
        return "Play";
    }

    private String second(Item it) {
        return Item.LIVE.equals(it.kind) || Item.SPORT.equals(it.kind) ? "TV Guide" : "More info";
    }

    /** The pill over a game: when it is, and the competition. */
    private static String sportPill(Item it) {
        String f = it.flag == null ? "" : it.flag;
        if (f.toUpperCase(Locale.ROOT).contains("LIVE")) return "Live now";
        if (it.sub != null && it.sub.startsWith("Tonight")) return "Tonight · " + it.sub.substring(8).split(" · ")[0];
        return f.isEmpty() ? "Coming up" : f.charAt(0) + f.substring(1).toLowerCase(Locale.ROOT);
    }

    /** A glass pill button, white when focused. */
    private View pillButton(String icon, String label, boolean focus) {
        LinearLayout b = new LinearLayout(c);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(px(28), 0, px(34), 0);
        b.setBackground(fill(33, focus ? 0xFFFFFFFF : 0x29FFFFFF));
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(SkinIcons.get(icon, focus ? DARK : TEXT, 2.2f));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(px(24), px(24));
        il.rightMargin = px(12);
        b.addView(ic, il);
        TextView t = text(23, host.semi(), focus ? DARK : TEXT);
        t.setText(label);
        b.addView(t);
        if (focus) {
            b.setScaleX(1.06f);
            b.setScaleY(1.06f);
        }
        return b;
    }

    private View roundButton(String icon, boolean focus) {
        FrameLayout b = new FrameLayout(c);
        b.setBackground(circle(focus ? 0xFFFFFFFF : 0x29FFFFFF));
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(SkinIcons.get(icon, focus ? DARK : TEXT, 2.6f));
        b.addView(ic, new FrameLayout.LayoutParams(px(26), px(26), Gravity.CENTER));
        if (focus) {
            b.setScaleX(1.06f);
            b.setScaleY(1.06f);
        }
        return b;
    }

    // ---- tiles ------------------------------------------------------------------------------------------------------

    /**
     * What a tile has to read: its main picture (read when the tile comes near the focus, let go again when it is far,
     * so a long page holds only the pictures around the focus) and its small ones (logos: read once, kept).
     */
    private static final class Load {
        Pic pic;
        String url;
        int w;
        float alignY = 0.5f;
        final List<Runnable> once = new ArrayList<>();
    }

    /** The tile being made: its builder's pictures go into it (null: read at once). */
    private Load building;

    /** A tile's main picture, read once the tile is near the focus (Cell.want). */
    private void mainPic(Pic p, String url, int w, float alignY) {
        if (building == null) { host.image(p, url, w); return; }
        building.pic = p;
        building.url = url;
        building.w = w;
        building.alignY = alignY;
    }

    /** A tile's small picture (a logo), read the first time the tile is near the focus. */
    private void later(Runnable job) {
        if (building == null) job.run(); else building.once.add(job);
    }

    /** A tile: its picture box (grows and takes the white ring when focused) and, for some kinds, its caption under it. */
    final class Cell {
        final Item it;
        final FrameLayout view, box;
        final View ringView;
        final View cap;
        final TextView capA, capB;
        final Load load;
        boolean on, picOn, onceDone;

        Cell(Item it, FrameLayout view, FrameLayout box, View cap, TextView a, TextView b, int radius, Load load) {
            this.it = it;
            this.view = view;
            this.box = box;
            this.cap = cap;
            this.load = load;
            capA = a;
            capB = b;
            ringView = new View(c);
            ringView.setBackground(ring(radius, 0xFFFFFFFF, 4));
            ringView.setVisibility(View.INVISIBLE);
            box.addView(ringView, full());
        }

        /** Near the focus: its pictures read; far from it: its main picture let go (a new, empty one in its place). */
        void want(boolean w) {
            if (w) {
                if (!onceDone) {
                    onceDone = true;
                    for (Runnable r : load.once) r.run();
                }
                if (!picOn && load.pic != null && load.url != null) {
                    picOn = true;
                    host.image(load.pic, load.url, load.w);
                }
                return;
            }
            if (!picOn || load.pic == null) return;
            picOn = false;
            int idx = box.indexOfChild(load.pic);
            ViewGroup.LayoutParams lp = load.pic.getLayoutParams();
            box.removeView(load.pic);
            Pic fresh = new Pic(c, px(16), TILE);
            fresh.alignY(load.alignY);
            box.addView(fresh, idx, lp);
            load.pic = fresh;
        }

        void focus(boolean f) {
            if (f == on) return;
            on = f;
            box.animate().scaleX(f ? 1.07f : 1f).scaleY(f ? 1.07f : 1f).setDuration(220).start();
            ringView.setVisibility(f ? View.VISIBLE : View.INVISIBLE);
            view.setTranslationZ(f ? 2 : 0);
            if (cap != null) cap.animate().translationY(f ? px(12) : 0).setDuration(220).start();
            if (capA != null) capA.setTextColor(f ? TEXT : TEXT2);
            if (capB != null) capB.setTextColor(f ? TEXT2 : TEXT3);
        }
    }

    Cell cell(Item it, int kind) {
        FrameLayout v = new FrameLayout(c);
        v.setClipChildren(false);
        FrameLayout box = new FrameLayout(c);
        box.setClipChildren(false);
        v.addView(box, new FrameLayout.LayoutParams(px(KW[kind]), px(KH[kind])));
        int radius = 16;
        building = new Load();
        switch (kind) {
            case CHAN: chanTile(box, it); break;
            case FX: fxTile(box, it); break;
            case TXT: txtTile(box, it); break;
            case APP: appTile(box, it); break;
            case POSTER: posterTile(box, it); break;
            default: wideTile(box, it); break;
        }
        View cap = null;
        TextView a = null, b = null;
        if (CAPTION[kind]) {
            LinearLayout cl = new LinearLayout(c);
            cl.setOrientation(LinearLayout.VERTICAL);
            a = text(19, host.semi(), TEXT2);
            b = text(16, host.regular(), TEXT3);
            String[] words = caption(it, kind);
            a.setText(words[0]);
            cl.addView(a);
            if (words[1] != null) {
                b.setText(starred(words[1], 16, TEXT3));
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bl.topMargin = px(4);
                cl.addView(b, bl);
            }
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(px(KW[kind]), px(CAP_H));
            cp.topMargin = px(KH[kind] + 14);
            v.addView(cl, cp);
            cap = cl;
        }
        Load ld = building;
        building = null;
        return new Cell(it, v, box, cap, a, b, radius, ld);
    }

    /** Stands for the rating's star in a caption: drawn as SkinIcons' star (Inter has none). */
    private static final String STAR = "\u2605";

    /** Words with STAR drawn as the star icon, the size of the text. */
    private CharSequence starred(String words, float size, int colour) {
        if (words == null || !words.contains(STAR)) return words;
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(words);
        for (int i = words.indexOf(STAR); i >= 0; i = words.indexOf(STAR, i + 1)) {
            Drawable d = SkinIcons.get("star", colour);
            int sz = px(size * 0.85f);
            d.setBounds(0, 0, sz, sz);
            b.setSpan(new android.text.style.ImageSpan(d, android.text.style.ImageSpan.ALIGN_BASELINE), i, i + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.insert(i + 1, "\u2009");
            words = b.toString();
        }
        return b;
    }

    /** Name and line under a tile. */
    private String[] caption(Item it, int kind) {
        if (Item.SPORT.equals(it.kind)) return new String[]{nz(it.name), it.sub};
        if (Item.VIDEO.equals(it.kind)) return new String[]{nz(it.sub != null ? it.sub : it.name), "Trailer · YouTube"};
        String line = it.sub;
        if (line == null) {
            List<String> p = new ArrayList<>();
            if (it.vote > 0) p.add(STAR + String.format(Locale.ROOT, "%.1f", it.vote));
            if (it.year != null && !it.year.isEmpty()) p.add(it.year);
            line = p.isEmpty() ? null : String.join(" · ", p);
        } else if (it.line != null && it.app != null) {
            line = it.line + " · " + line;
        }
        return new String[]{nz(it.name), line};
    }

    private void wideTile(FrameLayout box, Item it) {
        Pic p = new Pic(c, px(16), TILE);
        p.alignY(0.3f);
        box.addView(p, full());
        if (it.backdrop != null) mainPic(p, it.backdrop, host.lite() ? 400 : 640, 0.3f);
        View scr = new View(c);
        scr.setBackground(new RoundShade(px(16), new Shade(0, 0, 0, 1, new int[]{0x00000000, 0x00000000, 0xB8000000}, new float[]{0, 0.45f, 1})));
        box.addView(scr, full());
        TextView nm = text(24, host.display(), TEXT);
        nm.setText(it.name);
        nm.setSingleLine(false);
        nm.setMaxLines(2);
        nm.setShadowLayer(px(8), 0, px(2), 0xB3000000);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(px(KW[WIDE] - 36), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        nl.leftMargin = px(18);
        nl.bottomMargin = px(it.progress >= 0 ? 28 : 18);
        box.addView(nm, nl);
        if (it.logo != null) {
            ImageView logo = new ImageView(c);
            logo.setAdjustViewBounds(true);
            logo.setScaleType(ImageView.ScaleType.FIT_START);
            logo.setMaxWidth(px(250));
            logo.setMaxHeight(px(90));
            logo.setVisibility(View.INVISIBLE);
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            ll.leftMargin = px(18);
            ll.bottomMargin = px(it.progress >= 0 ? 30 : 20);
            box.addView(logo, ll);
            later(() -> host.image(logo, it.logo, 300, () -> { logo.setVisibility(View.VISIBLE); nm.setVisibility(View.GONE); }));
        }
        if (Item.VIDEO.equals(it.kind)) {
            TextView yt = tag("YOUTUBE", RED);
            FrameLayout.LayoutParams yl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            yl.leftMargin = px(14);
            yl.topMargin = px(12);
            box.addView(yt, yl);
        } else if (it.flag != null) {
            TextView f = tag(it.flag.toUpperCase(Locale.ROOT), RED);
            FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            fl.leftMargin = px(12);
            fl.topMargin = px(12);
            box.addView(f, fl);
        }
        if (it.progress >= 0) progress(box, it.progress, 18, 12, KW[WIDE] - 36);
    }

    private void progress(FrameLayout box, float p, float x, float bottom, float w) {
        FrameLayout bar = new FrameLayout(c);
        bar.setBackground(fill(3, 0x47FFFFFF));
        View in = new View(c);
        in.setBackground(fill(3, TEXT));
        bar.addView(in, new FrameLayout.LayoutParams(Math.max(px(6), Math.round(px(w) * Math.max(0, Math.min(1, p)))), ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(px(w), px(5), Gravity.BOTTOM);
        bl.leftMargin = px(x);
        bl.bottomMargin = px(bottom);
        box.addView(bar, bl);
    }

    private void posterTile(FrameLayout box, Item it) {
        Pic p = new Pic(c, px(16), TILE);
        box.addView(p, full());
        String pic = it.poster != null ? it.poster : it.backdrop;
        if (pic != null) mainPic(p, pic, host.lite() ? 210 : 300, 0.5f);
        if (pic == null) {
            TextView nm = text(26, host.display(), TEXT);
            nm.setText(it.name);
            nm.setSingleLine(false);
            nm.setMaxLines(4);
            FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(px(KW[POSTER] - 36), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            nl.leftMargin = px(18);
            nl.bottomMargin = px(20);
            box.addView(nm, nl);
        }
        if (it.rank > 0) {
            TextView r = text(19, host.display(), DARK);
            r.setText(String.valueOf(it.rank));
            r.setGravity(Gravity.CENTER);
            r.setBackground(circle(0xFFFFFFFF));
            FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(px(40), px(40));
            rl.leftMargin = px(10);
            rl.topMargin = px(10);
            box.addView(r, rl);
        } else if (it.flag != null) {
            TextView f = tag(it.flag.toUpperCase(Locale.ROOT), RED);
            FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            fl.leftMargin = px(10);
            fl.topMargin = px(10);
            box.addView(f, fl);
        }
        if (it.progress > 0) progress(box, it.progress, 14, 12, KW[POSTER] - 28);
    }

    /** A channel tile's top in its logo's own colours (ChannelTiles, LogoColour): a soft gradient, top to foot. */
    private RoundShade ownTop(int[] colours) {
        return new RoundShade(px(16), true, new Shade(0, 0, 0, 1, new int[]{colours[0], colours[1]}, new float[]{0, 1}));
    }

    /** UHF's channel tile: the channel's colour with its logo, the live badge, how far through, and what is on under it. */
    private void chanTile(FrameLayout box, Item it) {
        View base = new View(c);
        base.setBackground(fill(16, TILE));
        box.addView(base, full());
        FrameLayout top = new FrameLayout(c);
        // Behind logos (ChannelTiles): the logo's own colour (the default), black, or nothing behind the logo on the card
        // (None); a channel with no logo its name on its colour.
        int behind = ChannelTiles.behind(c);
        boolean black = behind == ChannelTiles.TILE, own = behind == ChannelTiles.COLOUR && it.mark != null;
        String ownUrl = own ? ChannelTiles.colourUrl(it.mark) : null;
        if (own) {
            int[] known = ChannelTiles.colourOf(ownUrl);
            top.setBackground(ownTop(known != null ? known : ChannelTiles.waiting()));
        } else if (black || it.mark == null) {
            int col = black ? ChannelTiles.GROUND : channelColour(it.channel);
            top.setBackground(new RoundShade(px(16), true, new Shade(0, 0, 1, 1, new int[]{col, mix(col, 0xFF000000, 0.38f)}, new float[]{0, 1})));
        }
        box.addView(top, new FrameLayout.LayoutParams(px(KW[CHAN]), px(112)));
        TextView name = text(32, host.display(), TEXT);
        name.setText(shortName(it.channel));
        name.setGravity(Gravity.CENTER);
        name.setLetterSpacing(-0.02f);
        top.addView(name, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        if (it.mark != null) {
            ImageView logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setVisibility(View.INVISIBLE);
            top.addView(logo, new FrameLayout.LayoutParams(px(190), px(62), Gravity.CENTER));
            String mark = black ? ChannelTiles.url(it.mark) : own ? ownUrl : ChannelTiles.haloUrl(it.mark);
            later(() -> host.image(logo, mark, 240, () -> {
                logo.setVisibility(View.VISIBLE);
                name.setVisibility(View.GONE);
                int[] got = own ? ChannelTiles.colourOf(ownUrl) : null;
                if (got != null) top.setBackground(ownTop(got));
            }));
        }
        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(fill(8, RED));
        ImageView bi = new ImageView(c);
        bi.setImageDrawable(SkinIcons.get("live", TEXT, 2.2f));
        badge.addView(bi, new FrameLayout.LayoutParams(px(18), px(18), Gravity.CENTER));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(px(28), px(28), Gravity.END);
        bl.topMargin = px(10);
        bl.rightMargin = px(10);
        top.addView(badge, bl);
        if (it.progress >= 0) {
            View track = new View(c);
            track.setBackgroundColor(0x40FFFFFF);
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(4), Gravity.BOTTOM);
            top.addView(track, tl);
            View done = new View(c);
            done.setBackgroundColor(TEXT);
            top.addView(done, new FrameLayout.LayoutParams(Math.round(px(KW[CHAN]) * Math.max(0.02f, Math.min(1, it.progress))), px(4), Gravity.BOTTOM));
        }
        TextView t = text(18, host.semi(), TEXT);
        t.setText(nz(it.name).replaceFirst("^(?i)live:? ", "Live: "));
        FrameLayout.LayoutParams l1 = new FrameLayout.LayoutParams(px(KW[CHAN] - 32), ViewGroup.LayoutParams.WRAP_CONTENT);
        l1.leftMargin = px(16);
        l1.topMargin = px(124);
        box.addView(t, l1);
        TextView s = text(14, host.regular(), TEXT2);
        String when = it.sub == null ? "" : it.sub.replace("On now · ", "");
        s.setText(when + (it.channel != null ? (when.isEmpty() ? "" : " · ") + it.channel : ""));
        FrameLayout.LayoutParams l2 = new FrameLayout.LayoutParams(px(KW[CHAN] - 32), ViewGroup.LayoutParams.WRAP_CONTENT);
        l2.leftMargin = px(16);
        l2.topMargin = px(150);
        box.addView(s, l2);
    }

    /** A game: its picture, or its clubs' colours and letters; the competition and TONIGHT or LIVE on its corner. */
    private void fxTile(FrameLayout box, Item it) {
        if (it.backdrop != null) {
            Pic p = new Pic(c, px(16), TILE);
            box.addView(p, full());
            mainPic(p, it.backdrop, host.lite() ? 400 : 640, 0.5f);
            View scr = new View(c);
            scr.setBackground(new RoundShade(px(16), new Shade(0, 0, 0, 1, new int[]{0x66000000, 0x00000000, 0x99000000}, new float[]{0, 0.4f, 1})));
            box.addView(scr, full());
        } else {
            box.addView(panel(it, false), full());
        }
        LinearLayout tagRow = new LinearLayout(c);
        tagRow.setGravity(Gravity.CENTER_VERTICAL);
        String f = it.flag == null ? null : it.flag.toUpperCase(Locale.ROOT);
        if (f != null) {
            tagRow.addView(tag(f, f.contains("LIVE") || f.contains("TONIGHT") ? RED : 0x66000000));
        }
        TextView league = text(13, host.semi(), TEXT);
        league.setLetterSpacing(0.06f);
        league.setShadowLayer(px(4), 0, px(1), 0x99000000);
        String comp = it.line != null ? it.line.split(" · ")[0] : null;
        if (comp != null && (comp.matches(".*\\d\\d:\\d\\d.*") || comp.startsWith("On now") || comp.startsWith("Live") || comp.equalsIgnoreCase(it.flag))) comp = null;
        league.setText(comp == null ? "" : comp.toUpperCase(Locale.ROOT));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.leftMargin = px(f != null ? 8 : 0);
        tagRow.addView(league, ll);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = px(14);
        tl.topMargin = px(12);
        box.addView(tagRow, tl);
        if (it.mark != null && it.backdrop != null) {
            ImageView m = new ImageView(c);
            m.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(px(56), px(56), Gravity.END);
            ml.rightMargin = px(12);
            ml.topMargin = px(10);
            box.addView(m, ml);
            later(() -> host.image(m, it.mark, 120));
        }
    }

    private void txtTile(FrameLayout box, Item it) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(fill(16, 0x14FFFFFF));
        l.setPadding(px(22), px(20), px(22), px(20));
        TextView k = text(14, host.semi(), HOT);
        k.setLetterSpacing(0.1f);
        k.setText(nz(it.sub).toUpperCase(Locale.ROOT));
        l.addView(k);
        TextView v = text(24, host.display(), TEXT);
        v.setSingleLine(false);
        v.setMaxLines(4);
        v.setLineSpacing(px(4), 1f);
        v.setText(it.name);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vl.topMargin = px(10);
        l.addView(v, vl);
        box.addView(l, full());
    }

    private void appTile(FrameLayout box, Item it) {
        int col = appColour(it.name);
        View bg = new View(c);
        bg.setBackground(new RoundShade(px(16), new Shade(0, 0, 1, 1, new int[]{col, mix(col, 0xFF000000, 0.55f)}, new float[]{0, 1})));
        box.addView(bg, full());
        if (it.icon != null) {
            ImageView ic = new ImageView(c);
            ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ic.setImageDrawable(it.icon);
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(px(64), px(64), Gravity.END);
            il.topMargin = px(16);
            il.rightMargin = px(18);
            box.addView(ic, il);
        } else {
            TextView mono = text(44, host.display(), 0x38FFFFFF);
            mono.setText(monogram(it.name));
            mono.setLetterSpacing(-0.04f);
            FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END);
            ml.topMargin = px(14);
            ml.rightMargin = px(18);
            box.addView(mono, ml);
        }
        TextView n = text(25, host.display(), TEXT);
        n.setText(it.name);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(px(KW[APP] - 44), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        nl.leftMargin = px(22);
        nl.bottomMargin = px(it.sub != null ? 44 : 20);
        box.addView(n, nl);
        if (it.sub != null) {
            TextView s = text(15, host.regular(), 0xB8FFFFFF);
            s.setText(it.sub);
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(px(KW[APP] - 44), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            sl.leftMargin = px(22);
            sl.bottomMargin = px(20);
            box.addView(s, sl);
        }
    }

    // ---- grids: Apps, My list ----------------------------------------------------------------------------------------

    private final class GridPage {
        final int kind, cols, cw, ch;
        final FrameLayout view = new FrameLayout(c), gridBox = new FrameLayout(c);
        final List<Cell> cells = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        int at;

        GridPage(String title, String rowId, int kind, int cols, int cw, int ch) {
            this.kind = kind;
            this.cols = cols;
            this.cw = cw;
            this.ch = ch;
            TextView h = text(44, host.display(), TEXT);
            h.setText(title);
            add(view, h, LEFT, 56, 900, 56);
            FrameLayout clip = new FrameLayout(c);
            clip.setClipChildren(true);
            clip.addView(gridBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            add(view, clip, LEFT - 20, 140, RIGHT - LEFT + 40, 940);
            gridBox.setClipChildren(false);
            content.addView(view, full());
            load(rowId, got -> {
                items = got;
                cells.clear();
                gridBox.removeAllViews();
                int rowH = rowH();
                for (int i = 0; i < got.size(); i++) {
                    Item it = got.get(i);
                    Cell cell = cell(it, kind);
                    resize(cell);
                    cells.add(cell);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(cw), px(ch + (CAPTION[kind] ? CAP_H : 0)));
                    lp.leftMargin = px(20 + (i % cols) * (cw + 26));
                    lp.topMargin = px(20 + (i / cols) * rowH);
                    gridBox.addView(cell.view, lp);
                    final int idx = i;
                    cell.view.setOnClickListener(v -> { zone = Z_GRID; at = idx; SkinUhf.this.paint(); host.ok(it); });
                }
                ViewGroup.LayoutParams gl = gridBox.getLayoutParams();
                gl.height = px(40 + ((got.size() + cols - 1) / cols) * rowH);
                gridBox.setLayoutParams(gl);
                if (got.isEmpty()) {
                    TextView none = text(24, host.regular(), TEXT2);
                    none.setText(kind == POSTER ? "Nothing saved yet. Hold OK on a film or series to add it to My List." : "No apps found.");
                    gridBox.addView(none, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                SkinUhf.this.paint();
            });
        }

        int rowH() {
            return ch + (CAPTION[kind] ? CAP_H + 30 : 34);
        }

        /** A tile at the grid's own size (Apps are wider, My list's posters taller). */
        private void resize(Cell cell) {
            ViewGroup.LayoutParams bl = cell.box.getLayoutParams();
            bl.width = px(cw);
            bl.height = px(ch);
            cell.box.setLayoutParams(bl);
            for (int i = 0; i < cell.box.getChildCount(); i++) {
                View v = cell.box.getChildAt(i);
                ViewGroup.LayoutParams l = v.getLayoutParams();
                if (l.width == px(KW[kind])) l.width = px(cw);
                if (l.height == px(KH[kind])) l.height = px(ch);
                if (l.width == px(KW[kind] - 44)) l.width = px(cw - 44);
                v.setLayoutParams(l);
            }
            if (cell.cap != null) {
                FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) cell.cap.getLayoutParams();
                cp.width = px(cw);
                cp.topMargin = px(ch + 14);
                cell.cap.setLayoutParams(cp);
            }
        }

        void paint() {
            int rowN = at / cols;
            for (int i = 0; i < cells.size(); i++) {
                cells.get(i).want(i / cols >= rowN - 2 && i / cols <= rowN + 4);
                cells.get(i).focus(zone == Z_GRID && i == at);
            }
            gridBox.animate().translationY(-px(Math.max(0, rowN - 1) * rowH())).setDuration(380).start();
        }

        boolean move(int dx, int dy) {
            if (items.isEmpty()) { if (dx < 0) toRail(); return true; }
            if (dx < 0 && at % cols == 0) { toRail(); return true; }
            int n = at + dx + dy * cols;
            if (n < 0 || n >= items.size()) return true;
            at = n;
            SkinUhf.this.paint();
            return true;
        }
    }

    // ---- keys -------------------------------------------------------------------------------------------------------

    @Override boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN, up = e.getAction() == KeyEvent.ACTION_UP;
        if (code == KeyEvent.KEYCODE_BACK) {
            if (up && !e.isCanceled()) back();
            return true;
        }
        if (isOk(code)) {
            if (down && e.getRepeatCount() > 0) {
                if (!held) { held = true; hold(); }
                return true;
            }
            if (down) { held = false; return true; }
            if (up) {
                if (held) { held = false; return true; }
                if (!e.isCanceled()) ok();
            }
            return true;
        }
        int dx = 0, dy = 0;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: dx = -1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dx = 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: dy = -1; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dy = 1; break;
            case KeyEvent.KEYCODE_MENU:
                if (up) host.open(OPEN_CONTROL_CENTRE);
                return true;
            default: return false;
        }
        if (down) move(dx, dy);
        return true;
    }

    private void move(int dx, int dy) {
        host.main().removeCallbacks(heroTurn);
        if (zone == Z_HERO && "home".equals(page) && !host.lite()) host.main().postDelayed(heroTurn, 9000);
        switch (zone) {
            case Z_RAIL:
                if (dy != 0) { railAt = Math.max(0, Math.min(rail.size() - 1, railAt + dy)); paint(); return; }
                if (dx > 0) {
                    zone = guide != null ? Z_GUIDE : grid != null ? Z_GRID : railFrom == Z_ROWS && rows != null ? Z_ROWS : Z_HERO;
                    paint();
                }
                return;
            case Z_HERO:
                if (rows == null) return;
                if (dx < 0) { if (rows.hb == 0) toRail(); else { rows.hb--; rows.renderHero(false); } return; }
                if (dx > 0) { if (rows.hb < 2) { rows.hb++; rows.renderHero(false); } return; }
                if (dy > 0 && !rows.vis().isEmpty()) { zone = Z_ROWS; rows.ri = 0; paint(); }
                return;
            case Z_ROWS: {
                List<Row> v = rows.vis();
                if (v.isEmpty()) { zone = Z_HERO; paint(); return; }
                if (dy != 0) {
                    int n = rows.ri + dy;
                    if (n < 0) { zone = Z_HERO; paint(); return; }
                    if (n >= v.size()) return;
                    rows.ri = n;
                    paint();
                    return;
                }
                Row r = v.get(rows.ri);
                if (dx < 0 && r.ci == 0) { toRail(); return; }
                r.ci = Math.max(0, Math.min(r.items.size() - 1, r.ci + dx));
                paint();
                return;
            }
            case Z_GRID:
                if (grid != null) grid.move(dx, dy);
                return;
            case Z_GUIDE:
                if (guide != null && !guide.move(dx, dy)) toRail();
                return;
            default:
        }
    }

    private void ok() {
        switch (zone) {
            case Z_RAIL: railOk(); return;
            case Z_HERO: if (rows != null) rows.heroOk(); return;
            case Z_ROWS: {
                Item it = rows.focused();
                if (it != null) host.ok(it);
                return;
            }
            case Z_GRID:
                if (grid != null && grid.at < grid.items.size()) host.ok(grid.items.get(grid.at));
                return;
            case Z_GUIDE:
                if (guide != null) guide.ok();
                return;
            default:
        }
    }

    private void hold() {
        Item it = zone == Z_ROWS && rows != null ? rows.focused() : zone == Z_GRID && grid != null && grid.at < grid.items.size() ? grid.items.get(grid.at)
                : zone == Z_HERO && rows != null && !rows.heroes.isEmpty() ? (Item) rows.heroes.get(rows.heroAt)[0] : null;
        if (it != null) host.details(it);
    }

    private void back() {
        switch (zone) {
            case Z_ROWS: zone = Z_HERO; paint(); return;
            case Z_HERO:
            case Z_GRID:
            case Z_GUIDE:
                if (zone == Z_GUIDE && guide != null && guide.back()) return;
                toRail();
                return;
            case Z_RAIL:
            default:
                if (!"home".equals(page)) open("home", false); else home();
        }
    }

    // ---- Home, resume, stop -------------------------------------------------------------------------------------------

    @Override void home() {
        if (!"home".equals(page)) open("home", false);
        zone = Z_HERO;
        if (rows != null) {
            rows.heroAt = 0;
            rows.heroPicked = false;
            rows.hb = 0;
            rows.ri = 0;
            for (Row r : rows.all) r.ci = 0;
            rows.renderHero(false);
        }
        paint();
        host.main().removeCallbacks(heroTurn);
        if (!host.lite()) host.main().postDelayed(heroTurn, 9000);
    }

    private final Runnable heroTurn = new Runnable() {
        @Override public void run() {
            if (!live()) return;
            if (zone == Z_HERO && rows != null && "home".equals(page) && !host.lite() && rows.heroes.size() > 1) rows.turn();
            if (!host.lite()) host.main().postDelayed(this, 9000);
        }
    };

    @Override void resume() {
        host.main().removeCallbacks(clockTick);
        host.main().post(clockTick);
        host.main().removeCallbacks(tickRead);
        host.main().post(tickRead);
        host.main().removeCallbacks(tickTurn);
        host.main().postDelayed(tickTurn, 600);
        host.main().removeCallbacks(heroTurn);
        if (!host.lite()) host.main().postDelayed(heroTurn, 9000);
        for (RowPage p : kept.values()) if (p.built) p.refresh();
        if (guide != null) guide.refresh();
    }

    @Override void stop() {
        host.main().removeCallbacks(clockTick);
        host.main().removeCallbacks(tickRead);
        host.main().removeCallbacks(tickTurn);
        host.main().removeCallbacks(heroTurn);
        host.main().removeCallbacks(bgLater);
    }

    @Override void destroy() {
        stop();
        if (guide != null) guide.close();
        super.destroy();
    }

    // ---- words, colours ---------------------------------------------------------------------------------------------

    static String nz(String s) {
        return s == null ? "" : s;
    }

    /** "Sky Sports Main Event UHD" → "Main Event"; "BBC One HD" → "BBC One". */
    static String shortName(String channel) {
        if (channel == null) return "";
        String s = channel.replaceAll("(?i)\\s+(uhd|fhd|hd|4k)$", "").trim();
        s = s.replaceFirst("(?i)^sky sports\\s+(?!news)", "");
        return s.length() > 14 ? s.substring(0, 14).trim() : s;
    }

    /** The channels' own colours, as UHF's tiles show them, else one of a palette (the same each time for a name). */
    static int channelColour(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("bbc news")) return 0xFFB80F12;
        if (n.contains("bbc one")) return 0xFFB3262D;
        if (n.contains("bbc two")) return 0xFF0F7D78;
        if (n.contains("bbc three")) return 0xFF8E1F6B;
        if (n.contains("bbc four")) return 0xFF2F3A8F;
        if (n.contains("cbeebies")) return 0xFFC9A815;
        if (n.contains("cbbc")) return 0xFF5FA82A;
        if (n.contains("itv")) return 0xFF2A62D6;
        if (n.matches(".*\\b(channel 4|e4|more4|film4)\\b.*")) return 0xFF5B2CA0;
        if (n.matches(".*\\b(channel 5|5usa|5star|5action)\\b.*")) return 0xFFC0157A;
        if (n.contains("main event")) return 0xFFC8102E;
        if (n.contains("sky sports f1") || n.endsWith(" f1") || n.contains(" f1 ")) return 0xFF8A0E18;
        if (n.contains("premier league")) return 0xFF4B1D6E;
        if (n.contains("sky sports news")) return 0xFFB01E23;
        if (n.contains("sky sports")) return 0xFFA3122A;
        if (n.contains("tnt sports")) return 0xFF3A3A3F;
        if (n.contains("sky news")) return 0xFFB01E23;
        if (n.contains("sky atlantic")) return 0xFF0F4C81;
        if (n.contains("sky cinema")) return 0xFF6A1B9A;
        if (n.contains("sky")) return 0xFF1565C0;
        if (n.contains("eurosport")) return 0xFF1A237E;
        if (n.contains("premier sports")) return 0xFF0D47A1;
        if (n.contains("cnn")) return 0xFFCC0000;
        if (n.contains("al jazeera")) return 0xFFC48F00;
        int[] pal = {0xFF7A2E2E, 0xFF2E5E7A, 0xFF4A2E7A, 0xFF2E7A55, 0xFF7A5A2E, 0xFF5E2E7A, 0xFF2E6B7A, 0xFF7A2E5E};
        return pal[Math.abs(n.hashCode()) % pal.length];
    }

    static int appColour(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("netflix")) return 0xFF8F0B12;
        if (n.contains("youtube")) return 0xFFC4161C;
        if (n.contains("disney")) return 0xFF163A8F;
        if (n.contains("prime")) return 0xFF1B6FB8;
        if (n.contains("tivimate")) return 0xFF3D2AA8;
        if (n.contains("debrid")) return 0xFF0F8A7A;
        if (n.contains("nord")) return 0xFF2D5FD6;
        if (n.contains("kleva")) return 0xFF7A1FA8;
        if (n.contains("iplayer")) return 0xFFC2187A;
        if (n.contains("itvx")) return 0xFF1B7A3E;
        if (n.contains("spotify")) return 0xFF1A8F4A;
        int[] pal = {0xFF3A3A40, 0xFF2E4A7A, 0xFF4A2E6A, 0xFF2E6A55, 0xFF6A4A2E};
        return pal[Math.abs(n.hashCode()) % pal.length];
    }

    static String monogram(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] w = name.replaceAll("[^A-Za-z0-9 ]", "").trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String s : w) if (!s.isEmpty() && b.length() < 2) b.append(Character.toUpperCase(s.charAt(0)));
        return b.toString();
    }

    /** "Everton v Coventry City" → {"Everton", "Coventry City"}. */
    static String[] teams(String name) {
        String n = nz(name).replaceFirst("^(?i)live:? ", "");
        int at = n.indexOf(" v ");
        if (at < 0) at = n.indexOf(" vs ");
        if (at < 0) return new String[]{n, ""};
        return new String[]{n.substring(0, at).trim(), n.substring(n.indexOf(' ', at + 1) + 1).trim()};
    }

    /** {colour, letters} for a club by name (the Premier League's and the Scottish Premiership's), else a palette colour. */
    private static final Object[][] CLUBS = {
            {"everton", 0xFF1B3FA8, "EVE"}, {"coventry", 0xFF5FA8D8, "COV"}, {"leeds", 0xFFF0C419, "LEE"}, {"tottenham", 0xFF132257, "TOT"},
            {"spurs", 0xFF132257, "TOT"}, {"arsenal", 0xFFDB0007, "ARS"}, {"hull", 0xFFF5A12D, "HUL"}, {"fulham", 0xFF2B2B2B, "FUL"},
            {"newcastle", 0xFF241F20, "NEW"}, {"forest", 0xFFDD0000, "NFO"}, {"man city", 0xFF6CABDD, "MCI"}, {"manchester city", 0xFF6CABDD, "MCI"},
            {"man utd", 0xFFDA291C, "MUN"}, {"manchester united", 0xFFDA291C, "MUN"}, {"brighton", 0xFF0057B8, "BHA"}, {"brentford", 0xFFE30613, "BRE"},
            {"palace", 0xFF1B458F, "CRY"}, {"liverpool", 0xFFC8102E, "LIV"}, {"ipswich", 0xFF3A64A3, "IPS"}, {"bournemouth", 0xFFB50E12, "BOU"},
            {"sunderland", 0xFFEB172B, "SUN"}, {"chelsea", 0xFF034694, "CHE"}, {"villa", 0xFF670E36, "AVL"}, {"west ham", 0xFF7A263A, "WHU"},
            {"wolves", 0xFFFDB913, "WOL"}, {"burnley", 0xFF6C1D45, "BUR"}, {"celtic", 0xFF018749, "CEL"}, {"rangers", 0xFF1B458F, "RAN"},
            {"aberdeen", 0xFFC8102E, "ABE"}, {"hibernian", 0xFF00704A, "HIB"}, {"hibs", 0xFF00704A, "HIB"}, {"hearts", 0xFF7A1C2E, "HEA"},
            {"dundee utd", 0xFFF47920, "DUN"}, {"dundee", 0xFF1A2A5A, "DUN"}, {"motherwell", 0xFFF5A12D, "MOT"}, {"kilmarnock", 0xFF1A3A7A, "KIL"},
            {"st mirren", 0xFF2B2B2B, "STM"}, {"st johnstone", 0xFF1A4A9A, "STJ"}, {"livingston", 0xFFF5C518, "LIV"}};

    static int[] club(String name) {
        String n = nz(name).toLowerCase(Locale.ROOT);
        for (Object[] c : CLUBS) if (n.contains((String) c[0])) return new int[]{(Integer) c[1]};
        int[] pal = {0xFF8A1C1C, 0xFF1C3A8A, 0xFF1C6A3A, 0xFF6A1C6A, 0xFF8A6A1C, 0xFF1C6A6A};
        return new int[]{pal[Math.abs(n.hashCode()) % pal.length]};
    }

    static String abbr(String name) {
        String n = nz(name).toLowerCase(Locale.ROOT);
        for (Object[] c : CLUBS) if (n.contains((String) c[0])) return (String) c[2];
        String s = nz(name).replaceAll("[^A-Za-z]", "");
        return s.length() >= 3 ? s.substring(0, 3).toUpperCase(Locale.ROOT) : s.toUpperCase(Locale.ROOT);
    }

    /** Sport's channels among those on now (the sports page's row), else all of them. */
    static List<Item> sportChannels(List<Item> all) {
        List<Item> out = new ArrayList<>();
        for (Item it : all) {
            String n = nz(it.channel).toLowerCase(Locale.ROOT);
            if (n.matches(".*(sport|tnt|premier|f1|eurosport|dazn|racing|golf|cricket|football|nba|nfl|boxing).*")) out.add(it);
        }
        return out.isEmpty() ? all : out;
    }

    static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    // ---- drawables --------------------------------------------------------------------------------------------------

    /** A linear gradient across its bounds, from (x0, y0) to (x1, y1) as fractions of them, with these stops. */
    static final class Shade extends Drawable {
        private final float x0, y0, x1, y1;
        private final int[] cols;
        private final float[] pos;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Shade(float x0, float y0, float x1, float y1, int[] cols, float[] pos) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.cols = cols;
            this.pos = pos;
        }

        @Override protected void onBoundsChange(Rect b) {
            paint.setShader(new LinearGradient(b.left + x0 * b.width(), b.top + y0 * b.height(), b.left + x1 * b.width(), b.top + y1 * b.height(),
                    cols, pos, Shader.TileMode.CLAMP));
        }

        @Override public void draw(Canvas canvas) {
            canvas.drawRect(getBounds(), paint);
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Shades drawn one over another. */
    static final class LayerShade extends Drawable {
        private final Drawable[] parts;

        LayerShade(Drawable... parts) {
            this.parts = parts;
        }

        @Override protected void onBoundsChange(Rect b) {
            for (Drawable d : parts) d.setBounds(b);
        }

        @Override public void draw(Canvas canvas) {
            for (Drawable d : parts) d.draw(canvas);
        }

        @Override public void setAlpha(int alpha) {
            for (Drawable d : parts) d.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** A Shade cut to a rounded rectangle (all corners, or only the top two). */
    static final class RoundShade extends Drawable {
        private final float r;
        private final boolean topOnly;
        private final Shade shade;
        private final android.graphics.RectF box = new android.graphics.RectF();

        RoundShade(float r, Shade shade) {
            this(r, false, shade);
        }

        RoundShade(float r, boolean topOnly, Shade shade) {
            this.r = r;
            this.topOnly = topOnly;
            this.shade = shade;
        }

        @Override protected void onBoundsChange(Rect b) {
            shade.setBounds(b);
            box.set(b);
        }

        @Override public void draw(Canvas canvas) {
            canvas.drawRoundRect(box, r, r, shade.paint);
            if (topOnly) canvas.drawRect(box.left, box.bottom - r, box.right, box.bottom, shade.paint);
        }

        @Override public void setAlpha(int alpha) {
            shade.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Two colours split by a slanted line (a game's two clubs), each darker towards its foot. */
    static final class Split extends Drawable {
        private final int a, b;
        private final float at, r;
        private final Paint pa = new Paint(Paint.ANTI_ALIAS_FLAG), pb = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path left = new Path(), shape = new Path();

        Split(int a, int b, float at, float r) {
            this.a = a;
            this.b = b;
            this.at = at;
            this.r = r;
        }

        @Override protected void onBoundsChange(Rect r) {
            pa.setShader(new LinearGradient(0, r.top, 0, r.bottom, a, mix(a, 0xFF000000, 0.35f), Shader.TileMode.CLAMP));
            pb.setShader(new LinearGradient(0, r.top, 0, r.bottom, b, mix(b, 0xFF000000, 0.35f), Shader.TileMode.CLAMP));
            left.reset();
            float w = r.width();
            left.moveTo(r.left, r.top);
            left.lineTo(r.left + w * (at + 0.06f), r.top);
            left.lineTo(r.left + w * (at - 0.06f), r.bottom);
            left.lineTo(r.left, r.bottom);
            left.close();
            shape.reset();
            shape.addRoundRect(new android.graphics.RectF(r), this.r, this.r, Path.Direction.CW);
        }

        @Override public void draw(Canvas canvas) {
            int save = canvas.save();
            if (r > 0) canvas.clipPath(shape);
            canvas.drawRect(getBounds(), pb);
            canvas.drawPath(left, pa);
            canvas.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) {
            pa.setAlpha(alpha);
            pb.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
