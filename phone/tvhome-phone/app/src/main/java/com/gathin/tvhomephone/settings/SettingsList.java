package com.gathin.tvhomephone.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.Icons;
import com.gathin.tvhomephone.live.SettingsLines;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings' main page, a phone's whole screen and a tablet's list beside the chosen section: its name, a search for any
 * setting by its words, then the sections under a few plain headings ("Watching", "Sport and matchday", "Your
 * channels", "Films and shows", "TVs and sync", "App"), each a large row with its glyph, its name and a line of how it
 * is set now ("2 playlists · Quality: Adaptive"). While something is typed, the settings found take the headings'
 * place, each with its section; one tapped opens its section there. In the Live TV screens' look (07): glass panels
 * with a bright hairline, Inter, white words with softer greys under them.
 */
final class SettingsList {
    /** What the list asks of the page it is on. */
    interface Host {
        /** A section's row tapped. */
        void open(Sections.Section s);

        /** A setting found, tapped: its section, there. */
        void openAt(SettingsSearch.Hit hit);

        /** A section's page as made so far (null before). */
        View page(Sections.Section s);

        /** The pages not made yet made, one at a time, each then. */
        void makeAll(Runnable each);

        boolean allMade();

        /** Something typed, or the search cleared. */
        void queried(boolean on);
    }

    /** Writing's greys under white (07's), and a section's line. */
    static final int L2 = 0xBDEBEBF5, L3 = 0x85EBEBF5, INK = 0xFF111111, TILE = 0x1FFFFFFF, CHOSEN = 0x24FFFFFF,
            RULE = 0x17FFFFFF;

    final LinearLayout view;
    final EditText search;
    private final Context c;
    private final Host host;
    private final boolean panes;
    private final View clear;
    private final LinearLayout groups, results;
    private final List<View> rows = new ArrayList<>();
    private final List<TextView> lines = new ArrayList<>();
    private final List<FrameLayout> tiles = new ArrayList<>();
    private final List<SectionIcons> glyphs = new ArrayList<>();
    private final String[] said = new String[Sections.ALL.size()];
    private final Runnable run = this::run;
    private boolean lineShown, searched;
    private String chosen;

    SettingsList(Context c, Host host, boolean panes) {
        this.c = c;
        this.host = host;
        this.panes = panes;
        view = new LinearLayout(c);
        view.setOrientation(LinearLayout.VERTICAL);
        TextView title = Glass.text(c, c.getString(R.string.tab_settings), panes ? 28 : 30, Glass.TEXT, Glass.heading(c, 600));
        title.setPadding(Glass.dp(c, 6), Glass.dp(c, panes ? 0 : 10), 0, Glass.dp(c, 16));
        ViewCompat.setAccessibilityHeading(title, true);
        view.addView(title);

        // The search: glass, a lens, the words, a cross to clear them.
        LinearLayout box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(SettingsLines.glass(c, 24));
        box.setPadding(Glass.dp(c, 16), 0, Glass.dp(c, 4), 0);
        ImageView lens = new ImageView(c);
        lens.setImageDrawable(new SectionIcons(c, Icons.SEARCH, Glass.MUTED));
        lens.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(lens, new LinearLayout.LayoutParams(Glass.dp(c, 20), Glass.dp(c, 20)));
        search = new EditText(c);
        search.setBackground(null);
        search.setHint(R.string.sn_search_hint);
        search.setHintTextColor(L3);
        search.setTextColor(Glass.TEXT);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setTypeface(SettingsLines.inter(c, false));
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setPadding(Glass.dp(c, 12), 0, Glass.dp(c, 6), 0);
        box.addView(search, new LinearLayout.LayoutParams(0, -1, 1f));
        ImageView x = new ImageView(c);
        x.setImageDrawable(new SectionIcons(c, Icons.CLOSE, Glass.SOFT));
        int xp = Glass.dp(c, 11);
        x.setPadding(xp, xp, xp, xp);
        x.setContentDescription(c.getString(R.string.sn_search_clear));
        x.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, null));
        x.setVisibility(View.GONE);
        x.setOnClickListener(v -> search.setText(""));
        clear = x;
        box.addView(x, new LinearLayout.LayoutParams(Glass.dp(c, 44), Glass.dp(c, 44)));
        view.addView(box, new LinearLayout.LayoutParams(-1, Glass.dp(c, 50)));

        search.setOnFocusChangeListener((v, has) -> {
            // The pages made as soon as the search is touched, one at a time, so the words are all there to find.
            if (has) host.makeAll(this::again);
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }

            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }

            @Override public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                App.main.removeCallbacks(run);
                if (SettingsSearch.words(s.toString()).length == 0) {
                    run();
                    return;
                }
                host.makeAll(SettingsList.this::again);
                App.main.postDelayed(run, 140);
            }
        });
        search.setOnEditorActionListener((v, id, e) -> {
            if (id != EditorInfo.IME_ACTION_SEARCH) return false;
            App.main.removeCallbacks(run);
            run();
            unfocus();
            return true;
        });

        groups = new LinearLayout(c);
        groups.setOrientation(LinearLayout.VERTICAL);
        for (int g = 0; g < Sections.GROUPS.length; g++) {
            LinearLayout card = card();
            for (int i = 0; i < Sections.ALL.size(); i++) {
                Sections.Section s = Sections.ALL.get(i);
                if (s.group != g) continue;
                if (card.getChildCount() > 0) card.addView(rule(), ruleParams());
                card.addView(row(s, i), new LinearLayout.LayoutParams(-1, -2));
            }
            if (card.getChildCount() == 0) continue;
            groups.addView(heading(c.getString(Sections.GROUPS[g]), g == 0));
            groups.addView(card, new LinearLayout.LayoutParams(-1, -2));
        }
        view.addView(groups, new LinearLayout.LayoutParams(-1, -2));

        results = new LinearLayout(c);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, Glass.dp(c, 14), 0, 0);
        results.setVisibility(View.GONE);
        view.addView(results, new LinearLayout.LayoutParams(-1, -2));
        // Until each line is read, what its section holds (shown once the lines come, or soon after).
        App.main.postDelayed(() -> {
            if (!lineShown) lines(new String[Sections.ALL.size()]);
        }, 700);
    }

    /** A heading over a group of sections. */
    private TextView heading(String words, boolean first) {
        TextView t = Glass.text(c, words, 14, Glass.MUTED, SettingsLines.inter(c, true));
        t.setPadding(Glass.dp(c, 8), Glass.dp(c, first ? 22 : 26), Glass.dp(c, 8), Glass.dp(c, 9));
        ViewCompat.setAccessibilityHeading(t, true);
        return t;
    }

    /** A panel of the Live TV screens' glass, its rows clipped to its corners. */
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(SettingsLines.glass(c, 20));
        card.setClipToOutline(true);
        return card;
    }

    private View rule() {
        View v = new View(c);
        v.setBackgroundColor(RULE);
        return v;
    }

    private LinearLayout.LayoutParams ruleParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, Glass.dp(c, 0.75f)));
        lp.setMarginStart(Glass.dp(c, 68));
        return lp;
    }

    /** A section's glyph on its own small tile of glass. */
    private FrameLayout tile(int glyph, int sizeDp, SectionIcons[] made) {
        FrameLayout t = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(TILE);
        g.setCornerRadius(Glass.dp(c, sizeDp * 0.3f));
        t.setBackground(g);
        ImageView i = new ImageView(c);
        SectionIcons icon = new SectionIcons(c, glyph, Glass.TEXT);
        i.setImageDrawable(icon);
        int s = Glass.dp(c, sizeDp * 0.56f);
        t.addView(i, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (made != null) made[0] = icon;
        return t;
    }

    private static RippleDrawable touch(int fill) {
        return new RippleDrawable(ColorStateList.valueOf(0x24FFFFFF), fill == 0 ? null : new ColorDrawable(fill), new ColorDrawable(0xFFFFFFFF));
    }

    /** A section's row: its glyph, its name, the line of how it is set, and a chevron (a phone's: it opens a page). */
    private View row(Sections.Section s, int i) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Glass.dp(c, 66));
        row.setPadding(Glass.dp(c, 14), Glass.dp(c, 11), Glass.dp(c, 12), Glass.dp(c, 11));
        row.setBackground(touch(0));
        SectionIcons[] icon = new SectionIcons[1];
        FrameLayout tile = tile(s.glyph, 40, icon);
        row.addView(tile, new LinearLayout.LayoutParams(Glass.dp(c, 40), Glass.dp(c, 40)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView name = Glass.text(c, c.getString(s.title), 16, Glass.TEXT, SettingsLines.inter(c, true));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(name);
        TextView line = Glass.text(c, c.getString(s.about), 13, L2, SettingsLines.inter(c, false));
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.END);
        line.setAlpha(0f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
        llp.topMargin = Glass.dp(c, 4);
        words.addView(line, llp);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, -2, 1f);
        wlp.setMarginStart(Glass.dp(c, 14));
        row.addView(words, wlp);
        if (!panes) {
            ImageView chevron = new ImageView(c);
            chevron.setImageDrawable(new SectionIcons(c, Icons.CHEVRON, Glass.FAINT));
            chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Glass.dp(c, 18), Glass.dp(c, 18));
            clp.setMarginStart(Glass.dp(c, 8));
            row.addView(chevron, clp);
        }
        row.setContentDescription(c.getString(s.title));
        row.setOnClickListener(v -> host.open(s));
        rows.add(row);
        lines.add(line);
        tiles.add(tile);
        glyphs.add(icon[0]);
        return row;
    }

    /** Each section's line of how it is set (null: what it holds), shown as it comes. */
    void lines(String[] got) {
        boolean first = !lineShown;
        lineShown = true;
        for (int i = 0; i < lines.size() && i < got.length; i++) {
            String t = got[i] != null && !got[i].isEmpty() ? got[i] : c.getString(Sections.ALL.get(i).about);
            said[i] = t;
            TextView l = lines.get(i);
            if (!t.contentEquals(l.getText())) l.setText(t);
            rows.get(i).setContentDescription(c.getString(Sections.ALL.get(i).title) + ", " + t);
            if (first) l.animate().alpha(1f).setDuration(160).start();
            else l.setAlpha(1f);
        }
    }

    /** A section's line as shown now (null before any). */
    String said(Sections.Section s) {
        int i = Sections.ALL.indexOf(s);
        return i < 0 ? null : said[i];
    }

    /** A tablet's: the section showing beside the list lit (a lighter row, its tile white). */
    void choose(String key) {
        chosen = key;
        for (int i = 0; i < rows.size(); i++) {
            boolean on = Sections.ALL.get(i).key.equals(key);
            rows.get(i).setBackground(touch(on ? CHOSEN : 0));
            rows.get(i).setSelected(on);
            GradientDrawable g = (GradientDrawable) tiles.get(i).getBackground();
            g.setColor(on ? 0xF2FFFFFF : TILE);
            glyphs.get(i).setColour(on ? INK : Glass.TEXT);
        }
    }

    String chosen() {
        return chosen;
    }

    String query() {
        return search.getText().toString();
    }

    void query(String q) {
        search.setText(q);
        search.setSelection(search.length());
    }

    boolean searching() {
        return SettingsSearch.words(query()).length > 0;
    }

    /** The search emptied, the headings back. */
    void clearQuery() {
        search.setText("");
        unfocus();
    }

    /** The keyboard down and the search let go. */
    void unfocus() {
        InputMethodManager im = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null && search.getWindowToken() != null) im.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    /** Again, once more pages are made (soon, not for every one). */
    private void again() {
        if (!searching()) return;
        App.main.removeCallbacks(run);
        App.main.post(run);
    }

    /** What was typed, looked for: the matches in place of the headings, or the headings back. */
    void run() {
        String q = query();
        boolean on = SettingsSearch.words(q).length > 0;
        groups.setVisibility(on ? View.GONE : View.VISIBLE);
        results.setVisibility(on ? View.VISIBLE : View.GONE);
        results.removeAllViews();
        if (on != searched) {
            searched = on;
            host.queried(on);
        }
        if (!on) return;
        List<SettingsSearch.Hit> hits = SettingsSearch.find(c, q, host::page);
        if (!hits.isEmpty()) {
            LinearLayout card = card();
            for (SettingsSearch.Hit h : hits) {
                if (card.getChildCount() > 0) card.addView(rule(), ruleParams());
                card.addView(hit(h), new LinearLayout.LayoutParams(-1, -2));
            }
            results.addView(card, new LinearLayout.LayoutParams(-1, -2));
        }
        String note = !host.allMade() ? c.getString(R.string.sn_looking)
                : hits.isEmpty() ? c.getString(R.string.sn_nothing, q.trim()) : null;
        if (note != null) {
            TextView t = Glass.text(c, note, 14, L2, SettingsLines.inter(c, false));
            t.setPadding(Glass.dp(c, 8), Glass.dp(c, hits.isEmpty() ? 6 : 14), Glass.dp(c, 8), Glass.dp(c, 8));
            results.addView(t);
        }
    }

    /** A match's row: its section's glyph, its words with what was typed lit, its section under them. */
    private View hit(SettingsSearch.Hit h) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Glass.dp(c, 62));
        row.setPadding(Glass.dp(c, 14), Glass.dp(c, 10), Glass.dp(c, 12), Glass.dp(c, 10));
        row.setBackground(touch(0));
        row.addView(tile(h.section.glyph, 36, null), new LinearLayout.LayoutParams(Glass.dp(c, 36), Glass.dp(c, 36)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        boolean whole = h.view == null;
        TextView what = Glass.text(c, shown(h.text, h.spans, SettingsLines.accent()), 15, whole ? Glass.TEXT : L2,
                SettingsLines.inter(c, whole));
        what.setMaxLines(2);
        what.setEllipsize(TextUtils.TruncateAt.END);
        what.setLineSpacing(Glass.dp(c, 2), 1f);
        words.addView(what);
        String section = c.getString(h.section.title);
        String under = whole ? (said(h.section) != null ? said(h.section) : c.getString(R.string.sn_section)) : section;
        TextView where = Glass.text(c, under, 12.5f, whole ? L2 : Glass.MUTED, SettingsLines.inter(c, !whole));
        where.setSingleLine(true);
        where.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Glass.dp(c, 4);
        words.addView(where, lp);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, -2, 1f);
        wlp.setMarginStart(Glass.dp(c, 14));
        row.addView(words, wlp);
        row.setContentDescription(whole ? c.getString(R.string.sn_open, section) : h.text + ", " + section);
        row.setOnClickListener(v -> {
            unfocus();
            host.openAt(h);
        });
        return row;
    }

    /**
     * Pure but for its spans: a match's words as listed: a long line from a little before the first word found (with
     * "…"), the words found lit in white on the accent's wash.
     */
    static CharSequence shown(String text, int[] spans, int accent) {
        int first = text.length();
        for (int k = 0; k < spans.length; k += 2) first = Math.min(first, spans[k]);
        int from = 0;
        if (text.length() > 80 && first > 40) {
            int space = text.lastIndexOf(' ', first - 24);
            from = space < 0 ? 0 : space + 1;
        }
        String body = (from > 0 ? "…" : "") + text.substring(from);
        int shift = (from > 0 ? 1 : 0) - from;
        SpannableString out = new SpannableString(body);
        for (int k = 0; k + 1 < spans.length; k += 2) {
            int s = spans[k] + shift, e = spans[k + 1] + shift;
            if (s < 0 || e > body.length() || s >= e) continue;
            out.setSpan(new ForegroundColorSpan(Glass.TEXT), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new BackgroundColorSpan((accent & 0xFFFFFF) | 0x4D000000), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }

    /** The section rows, in the page's order (tests). */
    List<View> rows() {
        return rows;
    }

    /** The matches' rows as listed now (tests). */
    List<View> hits() {
        List<View> out = new ArrayList<>();
        if (results.getChildCount() == 0 || !(results.getChildAt(0) instanceof LinearLayout)) return out;
        LinearLayout card = (LinearLayout) results.getChildAt(0);
        for (int i = 0; i < card.getChildCount(); i++) if (card.getChildAt(i) instanceof LinearLayout) out.add(card.getChildAt(i));
        return out;
    }
}
