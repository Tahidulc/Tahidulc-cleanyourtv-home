package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which Home the launcher shows: its own (launcher v2's), or the SWIPTV or UHF style drawn over the same engine. Chosen
 * from the Control Centre (and each style's own rail), kept on this TV, and changed at once without a restart: the
 * styles are only screens, so the rows, panels and alerts are the same in each.
 */
final class HomeStyle {
    static final String HOME = "home", SWIPTV = "swiptv", UHF = "uhf";
    static final String[] ALL = {HOME, SWIPTV, UHF};
    private static final String PREFS = "home_style", KEY = "style";

    private HomeStyle() {
    }

    /** The style chosen on this TV, else the theme's "home_style", else Home. */
    static String chosen(Context c) {
        String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        if (s == null) s = Config.themeString(c, "home_style", HOME);
        return of(s);
    }

    /** A style's name as given (any case, spaces) as one of ALL; anything else is Home. */
    static String of(String s) {
        String t = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        for (String a : ALL) if (a.equals(t)) return a;
        return HOME;
    }

    static void choose(Context c, String style) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, of(style)).apply();
    }

    /** What the Control Centre and the picker call it. */
    static String name(String style) {
        switch (of(style)) {
            case SWIPTV: return "SWIPTV style";
            case UHF: return "UHF style";
            default: return "Home";
        }
    }

    /** Its line under the name in the picker. */
    static String line(String style) {
        switch (of(style)) {
            case SWIPTV: return "Icon rail, big posters, a library of categories";
            case UHF: return "The title's picture fills the screen, glass buttons";
            default: return "The launcher as you know it, the dock and boxes";
        }
    }

    /** The one after it, round the three (the quick switch). */
    static String next(String style) {
        String s = of(style);
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(s)) return ALL[(i + 1) % ALL.length];
        return HOME;
    }

    // ---- the picker -------------------------------------------------------------------------------------------------

    private static Picker open;

    static boolean isOpen() {
        return open != null;
    }

    static boolean key(KeyEvent e) {
        return open != null && open.key(e);
    }

    static void close() {
        if (open != null) open.hide();
    }

    /** The three styles side by side over the screen: Left and Right choose, OK changes to it at once, Back leaves it. */
    static void pick(HomeV2 h) {
        if (open != null) return;
        open = new Picker(h);
        open.show();
    }

    private static final class Picker {
        private final HomeV2 h;
        private final Context c;
        private FrameLayout overlay;
        private final List<View> cards = new ArrayList<>();
        private int at;
        /** 0: the styles; 1: the Behind logos line under them (ChannelTiles). */
        private int row;
        private TextView logos;
        /** Behind logos changed: the style's screen is built again as the picker goes (not under it). */
        private boolean logosChanged;

        Picker(HomeV2 h) {
            this.h = h;
            this.c = h.a;
            String now = chosen(c);
            for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(now)) at = i;
        }

        void show() {
            overlay = new FrameLayout(c);
            overlay.setClickable(true);
            overlay.setBackgroundColor(0xE6060609);
            TextView head = text(44, h.displaySemi, 0xFFFFFFFF);
            head.setText("Launcher style");
            add(overlay, head, 0, 170, 1920, 56, Gravity.CENTER_HORIZONTAL);
            TextView sub = text(22, h.regular, 0xB3FFFFFF);
            sub.setText("Each one has the same rows, guide and Control Centre. Switch back any time from here.");
            add(overlay, sub, 0, 240, 1920, 30, Gravity.CENTER_HORIZONTAL);
            int w = 500, gap = 40, x0 = (1920 - (3 * w + 2 * gap)) / 2;
            String now = chosen(c);
            for (int i = 0; i < ALL.length; i++) {
                View card = card(ALL[i], ALL[i].equals(now));
                final int idx = i;
                card.setOnClickListener(v -> { at = idx; apply(); });
                add(overlay, card, x0 + i * (w + gap), 340, w, 470, Gravity.NO_GRAVITY);
                cards.add(card);
            }
            // What is behind the channels' logos (ChannelTiles), in the guides, channel tiles and What's on of every style.
            logos = text(22, h.semi, 0xFFFFFFFF);
            logos.setGravity(Gravity.CENTER);
            logos.setBackground(fill(26, 0x26FFFFFF));
            logos.setOnClickListener(v -> { row = 1; flipLogos(); });
            add(overlay, logos, (1920 - 940) / 2, 846, 940, 52, Gravity.NO_GRAVITY);
            TextView foot = text(19, h.regular, 0x99FFFFFF);
            foot.setText("◀ ▶ to choose  ·  OK to switch  ·  ▼ behind logos  ·  Back to keep this one");
            add(overlay, foot, 0, 930, 1920, 28, Gravity.CENTER_HORIZONTAL);
            h.root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(200).start();
            paint();
        }

        void hide() {
            if (logosChanged) {
                logosChanged = false;
                h.restyle();
            }
            if (overlay != null) {
                View o = overlay;
                o.animate().alpha(0f).setDuration(160).withEndAction(() -> h.root.removeView(o)).start();
            }
            overlay = null;
            open = null;
        }

        private void paint() {
            if (logos != null) {
                logos.setText(behindLine(ChannelTiles.behind(c)));
                logos.setForeground(row == 1 ? ring(26, 0xFFFFFFFF, 3) : null);
            }
            for (int i = 0; i < cards.size(); i++) {
                View v = cards.get(i);
                boolean f = i == at && row == 0;
                v.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(160).start();
                v.setForeground(f ? ring(28, 0xFFFFFFFF, 4) : null);
            }
        }

        private void apply() {
            String style = ALL[at];
            choose(c, style);
            hide();
            h.applyStyle(style);
            android.widget.Toast.makeText(c, name(style) + " on", android.widget.Toast.LENGTH_SHORT).show();
        }

        /** Behind logos' choices in the line's order, and their words. */
        private static final int[] BEHIND = {ChannelTiles.COLOUR, ChannelTiles.NONE, ChannelTiles.TILE};
        private static final String[] BEHIND_WORDS = {"Logo's colour (default)", "None", "Black tile"};

        /** Behind logos: Logo's colour (the default), None or Black tile, the chosen one lit and underlined. */
        private static CharSequence behindLine(int behind) {
            StringBuilder s = new StringBuilder("Behind logos:   ");
            int[] from = new int[BEHIND.length];
            for (int i = 0; i < BEHIND.length; i++) {
                if (i > 0) s.append("   |   ");
                from[i] = s.length();
                s.append(BEHIND_WORDS[i]);
            }
            android.text.SpannableString t = new android.text.SpannableString(s);
            t.setSpan(new android.text.style.ForegroundColorSpan(0x99FFFFFF), 0, from[0], 0);
            for (int i = 0; i < BEHIND.length; i++) {
                int end = from[i] + BEHIND_WORDS[i].length();
                if (BEHIND[i] == behind) t.setSpan(new android.text.style.UnderlineSpan(), from[i], end, 0);
                else t.setSpan(new android.text.style.ForegroundColorSpan(0x73FFFFFF), from[i], end, 0);
            }
            return t;
        }

        private static int place(int behind) {
            for (int i = 0; i < BEHIND.length; i++) if (BEHIND[i] == behind) return i;
            return 0;
        }

        /** Behind logos moved on to the next choice (OK), the style's screen built again with it. */
        private void flipLogos() {
            pickLogos(BEHIND[(place(ChannelTiles.behind(c)) + 1) % BEHIND.length]);
        }

        /** Behind logos: the choice one along to the left or right on its line. */
        private void stepLogos(int by) {
            int at = place(ChannelTiles.behind(c)) + by;
            if (at >= 0 && at < BEHIND.length) pickLogos(BEHIND[at]);
        }

        private void pickLogos(int behind) {
            if (behind == ChannelTiles.behind(c)) return;
            ChannelTiles.set(c, behind);
            logosChanged = !logosChanged;
            paint();
            String said = behind == ChannelTiles.TILE ? "Behind logos: black tiles"
                    : behind == ChannelTiles.NONE ? "Behind logos: none, the logos as they are" : "Behind logos: each logo's own colour";
            android.widget.Toast.makeText(c, said, android.widget.Toast.LENGTH_SHORT).show();
        }

        boolean key(KeyEvent e) {
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) hide();
                return true;
            }
            if (Skin.isOk(code)) {
                if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                    if (row == 1) flipLogos();
                    else apply();
                }
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) row = 1;
            else if (code == KeyEvent.KEYCODE_DPAD_UP) row = 0;
            else if (row == 1 && code == KeyEvent.KEYCODE_DPAD_LEFT) stepLogos(-1);
            else if (row == 1 && code == KeyEvent.KEYCODE_DPAD_RIGHT) stepLogos(1);
            else if (row == 0 && code == KeyEvent.KEYCODE_DPAD_LEFT && at > 0) at--;
            else if (row == 0 && code == KeyEvent.KEYCODE_DPAD_RIGHT && at < ALL.length - 1) at++;
            paint();
            return true;
        }

        /** A style's card: a small drawing of its screen, its name and line, and a tick on the one in use. */
        private View card(String style, boolean current) {
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(fill(28, 0xFF1C1C21));
            card.setPadding(px(22), px(22), px(22), px(22));
            card.setClipToOutline(true);
            FrameLayout art = new FrameLayout(c);
            art.setBackground(fill(18, 0xFF000000));
            art.setClipToOutline(true);
            drawArt(art, style);
            card.addView(art, new LinearLayout.LayoutParams(px(456), px(256)));
            TextView n = text(30, h.displaySemi, 0xFFFFFFFF);
            n.setText(name(style) + (current ? "  ✓" : ""));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
            nl.topMargin = px(24);
            card.addView(n, nl);
            TextView l = text(19, h.regular, 0xB3FFFFFF);
            l.setSingleLine(false);
            l.setMaxLines(2);
            l.setLineSpacing(px(4), 1f);
            l.setText(line(style));
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
            ll.topMargin = px(10);
            card.addView(l, ll);
            TextView tag = text(16, h.semi, current ? 0xFF111111 : 0xCCFFFFFF);
            tag.setText(current ? "IN USE" : "OK TO SWITCH");
            tag.setLetterSpacing(0.08f);
            tag.setBackground(fill(10, current ? 0xFFFFFFFF : 0x26FFFFFF));
            tag.setPadding(px(12), px(7), px(12), px(6));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.topMargin = px(18);
            card.addView(tag, tl);
            return card;
        }

        /** Each style's look in a few shapes: Home's navy and dock, SWIPTV's orange rail and posters, UHF's art and glass. */
        private void drawArt(FrameLayout art, String style) {
            switch (style) {
                case SWIPTV: {
                    art.setBackground(fill(18, 0xFF141519));
                    View mark = new View(c);
                    mark.setBackground(fill(5, 0xFFFF6B1A));
                    add(art, mark, 14, 14, 18, 18, Gravity.NO_GRAVITY);
                    for (int i = 0; i < 6; i++) {
                        View dot = new View(c);
                        dot.setBackground(fill(8, i == 0 ? 0x66FFFFFF : 0x26FFFFFF));
                        add(art, dot, 16, 60 + i * 30, 14, 14, Gravity.NO_GRAVITY);
                    }
                    for (int i = 0; i < 5; i++) {
                        View p = new View(c);
                        p.setBackground(fill(8, i == 0 ? 0xFFFF6B1A : 0x33FFFFFF));
                        add(art, p, 52 + i * 80, 40, 70, 104, Gravity.NO_GRAVITY);
                    }
                    for (int i = 0; i < 4; i++) {
                        View p = new View(c);
                        p.setBackground(fill(8, 0x1FFFFFFF));
                        add(art, p, 52 + i * 100, 168, 92, 54, Gravity.NO_GRAVITY);
                    }
                    break;
                }
                case UHF: {
                    GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF6B4A3A, 0xFF2A1E1A, 0xFF0B0B0D});
                    g.setCornerRadius(px(18));
                    art.setBackground(g);
                    for (int i = 0; i < 6; i++) {
                        View b = new View(c);
                        b.setBackground(fill(9, 0x55FFFFFF));
                        add(art, b, 12, 18 + i * 34, 18, 18, Gravity.NO_GRAVITY);
                    }
                    View title = new View(c);
                    title.setBackground(fill(4, 0xE6FFFFFF));
                    add(art, title, 48, 74, 170, 28, Gravity.NO_GRAVITY);
                    View pill = new View(c);
                    pill.setBackground(fill(11, 0xFFFFFFFF));
                    add(art, pill, 48, 118, 110, 22, Gravity.NO_GRAVITY);
                    int[] cols = {0xFFC8102E, 0xFF0F7D78, 0xFF2A62D6, 0xFF5B2CA0};
                    for (int i = 0; i < 4; i++) {
                        View t = new View(c);
                        t.setBackground(fill(8, cols[i]));
                        add(art, t, 48 + i * 100, 176, 92, 56, Gravity.NO_GRAVITY);
                    }
                    break;
                }
                default: {
                    GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1D2378, 0xFF11154A});
                    g.setCornerRadius(px(18));
                    art.setBackground(g);
                    View hero = new View(c);
                    hero.setBackground(fill(6, 0x33FFFFFF));
                    add(art, hero, 24, 26, 200, 26, Gravity.NO_GRAVITY);
                    for (int i = 0; i < 4; i++) {
                        View t = new View(c);
                        t.setBackground(fill(10, 0x29FFFFFF));
                        add(art, t, 24 + i * 104, 96, 96, 64, Gravity.NO_GRAVITY);
                    }
                    View dock = new View(c);
                    dock.setBackground(fill(16, 0x33FFFFFF));
                    add(art, dock, 60, 196, 336, 36, Gravity.NO_GRAVITY);
                    break;
                }
            }
        }

        private TextView text(float size, Typeface face, int colour) {
            TextView t = new TextView(c);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
            t.setTypeface(face);
            t.setTextColor(colour);
            t.setIncludeFontPadding(false);
            return t;
        }

        private GradientDrawable fill(float r, int colour) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(r));
            g.setColor(colour);
            return g;
        }

        private GradientDrawable ring(float r, int colour, float w) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(r));
            g.setStroke(Math.max(1, px(w)), colour);
            return g;
        }

        private void add(FrameLayout parent, View v, int x, int y, int w, int hgt, int gravity) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(w), px(hgt));
            lp.leftMargin = px(x);
            lp.topMargin = px(y);
            if (v instanceof TextView && gravity != Gravity.NO_GRAVITY) ((TextView) v).setGravity(gravity);
            parent.addView(v, lp);
        }

        private int px(float d) {
            return h.px(d);
        }
    }
}
