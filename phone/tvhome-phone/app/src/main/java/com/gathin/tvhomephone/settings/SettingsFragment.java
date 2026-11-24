package com.gathin.tvhomephone.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.Icons;
import com.gathin.tvhomephone.glass.Wide;
import com.gathin.tvhomephone.live.SettingsLines;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings, made "easier to navigate" as the family asked: it opens on a short main page (SettingsList), the sections
 * under a few plain headings, each a large row with its glyph, its name and a line of how it is set now; one tapped
 * opens its own page, with its name and a back arrow at the top, and Android's back (or the arrow) comes back to the
 * list where it was. A search at the top finds any setting by its words on every section's page, and one found opens
 * its section there, lit for a moment. Each section's page is its part's own (the TVs and sync, the alerts and clubs,
 * Live TV's playlists, the look, the posters, the films' subtitles, the app's updates), made only when it is first
 * opened, or when the search is first used (one a moment, so the page stays smooth).
 *
 * On a tablet with the room (Wide.panes), the same list down the left on a card of its own, with its search, and the
 * chosen section's page beside it in a column that reads well; the one chosen last opens again.
 */
public class SettingsFragment extends Fragment implements SettingsList.Host {
    /** Set before Settings opens from a playlist's notification: Live TV's section, at its playlists, once. */
    public static boolean toPlaylists;

    /** The sections' keys; LIVE is where a playlist's notification opens. */
    static final String INTRO = "intro", TVS = "tvs", SPORTS = "sports", LIVE = "live", APPEARANCE = "appearance",
            CHANNELS = "channels", REMINDERS = "reminders", POSTERS = "posters", TORBOX = "torbox", SUBTITLES = "subtitles",
            UPDATES = "updates";
    /** In the main page's order (Sections.ALL's). */
    static final String[] KEYS = {LIVE, APPEARANCE, REMINDERS, SPORTS, INTRO, CHANNELS, POSTERS, SUBTITLES, TORBOX, TVS, UPDATES};
    private static final String PREFS = "settings_ui", CHOSEN = "section";
    private static final String OPEN = "open", OPEN_Y = "open_y", MAIN_Y = "main_y", QUERY = "query";
    /** A page's slide in or out (07's second ease). */
    static final long SLIDE_MS = 280;
    private static final Interpolator EASE = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);

    /**
     * A section's page as made: its section; what shows or hides it (a phone's whole page, a tablet's holder in the
     * column); the section's own view; where it scrolls; and where a setting found is lit (its column's frame, which has
     * room either side).
     */
    static final class Page {
        final Sections.Section section;
        final ViewGroup root;
        final View content;
        final ScrollView scroll;
        final ViewGroup canvas;

        Page(Sections.Section section, ViewGroup root, View content, ScrollView scroll, ViewGroup canvas) {
            this.section = section;
            this.root = root;
            this.content = content;
            this.scroll = scroll;
            this.canvas = canvas;
        }
    }

    private final Map<String, Page> made = new HashMap<>();
    private View root;
    private boolean panes, making;
    private Runnable each;
    private SettingsList list;
    private OnBackPressedCallback back;
    // A phone: the main page, and the section's page over it.
    private FrameLayout stage;
    private ScrollView mainScroll;
    private Page shown;
    // A tablet: the chosen section's page beside the list.
    private ScrollView pageScroll;
    private LinearLayout pageCol;
    private ViewGroup pageCanvas;

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        Context c = requireContext();
        made.clear();
        shown = null;
        making = false;
        panes = Wide.panes(c);
        list = new SettingsList(c, this, panes);
        root = panes ? panes(c) : phone(c);
        if (panes) {
            Sections.Section last = Sections.of(prefs(c).getString(CHOSEN, LIVE));
            choose(last != null ? last : Sections.ALL.get(0), null, false, false);
        }
        // Where it was (turned on its side, or made again in a new colour theme), unless a playlist's note says where.
        if (state != null) restore(state);
        if (toPlaylists) {
            toPlaylists = false;
            playlists();
        }
        return root;
    }

    @Override public void onViewCreated(@NonNull View view, Bundle state) {
        super.onViewCreated(view, state);
        back = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                if (shown != null) close(true);
                else if (list != null) list.clearQuery();
                backOn();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), back);
        backOn();
    }

    @Override public void onResume() {
        super.onResume();
        // A playlist's note tapped while Settings was already open.
        if (toPlaylists && root != null) {
            toPlaylists = false;
            playlists();
        }
        freshLines();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (root == null || list == null) return;
        out.putString(QUERY, list.query());
        if (panes) {
            out.putInt(OPEN_Y, pageScroll.getScrollY());
            return;
        }
        out.putInt(MAIN_Y, mainScroll.getScrollY());
        if (shown != null) {
            out.putString(OPEN, shown.section.key);
            out.putInt(OPEN_Y, shown.scroll.getScrollY());
        }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        root = null;
        made.clear();
        shown = null;
        making = false;
        each = null;
    }

    private void restore(Bundle state) {
        String q = state.getString(QUERY, "");
        if (!q.isEmpty()) list.query(q);
        int y = state.getInt(OPEN_Y, 0);
        if (panes) {
            after(pageScroll, () -> pageScroll.scrollTo(0, y));
            return;
        }
        int mainY = state.getInt(MAIN_Y, 0);
        after(mainScroll, () -> mainScroll.scrollTo(0, mainY));
        Sections.Section s = Sections.of(state.getString(OPEN, ""));
        if (s == null) return;
        open(s, null, false, false);
        Page p = shown;
        after(p.scroll, () -> p.scroll.scrollTo(0, y));
    }

    private void backOn() {
        if (back != null) back.setEnabled(shown != null || list != null && list.searching());
    }

    /** Each section's line read afresh (off the main thread), for the list. */
    private void freshLines() {
        SettingsList l = list;
        if (l == null || !isAdded()) return;
        Sections.lines(requireContext(), got -> {
            if (l == list && root != null) l.lines(got);
        });
    }

    // ---- a phone: the main page, and each section's own page over it ----------------------------------------------

    private View phone(Context c) {
        stage = new FrameLayout(c);
        mainScroll = new ScrollView(c);
        mainScroll.setClipToPadding(false);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Glass.dp(c, 8), 0, Glass.dp(c, 28));
        col.addView(list.view, new LinearLayout.LayoutParams(-1, -2));
        // A tablet held upright: the list in a column no wider than reads well, in the middle.
        mainScroll.addView(centred(c, col, Glass.dp(c, 16)), new FrameLayout.LayoutParams(-1, -2));
        stage.addView(mainScroll, new FrameLayout.LayoutParams(-1, -1));
        return stage;
    }

    /** A section's own page: its name and a back arrow at the top (a rule under them once it scrolls), then the section. */
    private Page phonePage(Context c, Sections.Section s, boolean quietly) {
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(com.gathin.tvhomephone.live.Theme.page());
        // Nothing under it takes a touch while it shows (or slides).
        page.setClickable(true);
        page.setVisibility(View.GONE);
        if (quietly) page.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        String name = getString(s.title);
        ViewCompat.setAccessibilityPaneTitle(page, name);

        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(Glass.dp(c, 60));
        bar.setPadding(Glass.dp(c, 10), Glass.dp(c, 6), Glass.dp(c, 20), Glass.dp(c, 6));
        ImageView up = new ImageView(c);
        up.setImageDrawable(new SectionIcons(c, Icons.BACK, Glass.TEXT));
        int pad = Glass.dp(c, 12);
        up.setPadding(pad, pad, pad, pad);
        up.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF), SettingsLines.glass(c, 22), null));
        up.setContentDescription(getString(R.string.sn_back));
        up.setOnClickListener(v -> {
            if (shown != null && shown.root == page) close(true);
        });
        bar.addView(up, new LinearLayout.LayoutParams(Glass.dp(c, 44), Glass.dp(c, 44)));
        TextView title = Glass.text(c, name, 22, Glass.TEXT, Glass.heading(c, 600));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(Glass.dp(c, 14), 0, 0, 0);
        ViewCompat.setAccessibilityHeading(title, true);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        View rule = new View(c);
        rule.setBackgroundColor(0x1FFFFFFF);
        rule.setAlpha(0f);
        page.addView(rule, new LinearLayout.LayoutParams(-1, Math.max(1, Glass.dp(c, 0.75f))));

        ScrollView sv = new ScrollView(c);
        sv.setClipToPadding(false);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Glass.dp(c, 32));
        FrameLayout frame = centred(c, col, Glass.dp(c, 20));
        sv.addView(frame, new FrameLayout.LayoutParams(-1, -2));
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> rule.setAlpha(y > 0 ? 1f : 0f));
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));

        View content = s.build.make(this, col);
        if (quietly) unfocus(content);
        ownHeading(c, content, name);
        col.addView(content, new LinearLayout.LayoutParams(-1, -2));
        return new Page(s, page, content, sv, frame);
    }

    /**
     * A section's page over the main page, sliding in from the right (animate), at its top or at target (lit for a
     * moment when light).
     */
    private void open(Sections.Section s, View target, boolean animate, boolean light) {
        Page p = page(s, false);
        list.unfocus();
        if (shown == p) {
            if (target != null) after(p.root, () -> show(p, target, light));
            return;
        }
        Page before = shown;
        shown = p;
        p.root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        View found = target == null ? null : SettingsSearch.locate(p.content, target, textOf(target));
        if (found == null) p.scroll.scrollTo(0, 0);
        p.root.animate().cancel();
        p.root.bringToFront();
        p.root.setVisibility(View.VISIBLE);
        int w = stage.getWidth();
        if (animate && w > 0) {
            p.root.setTranslationX(w);
            after(p.root, () -> {
                if (found != null) reveal(p, found);
                p.root.animate().translationX(0f).setDuration(SLIDE_MS).setInterpolator(EASE).withEndAction(() -> {
                    if (shown != p) return;
                    mainScroll.setVisibility(View.INVISIBLE);
                    if (before != null) before.root.setVisibility(View.GONE);
                    if (found != null && light) light(p, found);
                }).start();
            });
        } else {
            p.root.setTranslationX(0f);
            mainScroll.setVisibility(View.INVISIBLE);
            if (before != null) before.root.setVisibility(View.GONE);
            if (found != null) after(p.root, () -> show(p, found, light));
        }
        backOn();
    }

    /** Back to the main page, where it was: the section's page slides away to the right. */
    private void close(boolean animate) {
        Page p = shown;
        if (p == null) return;
        shown = null;
        list.unfocus();
        android.view.inputmethod.InputMethodManager im =
                (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null && p.root.getWindowToken() != null) im.hideSoftInputFromWindow(p.root.getWindowToken(), 0);
        // The list just where it was, whatever takes the focus as the page goes (with a remote or a keyboard, the
        // section's own row).
        int y = mainScroll.getScrollY();
        mainScroll.setVisibility(View.VISIBLE);
        int at = Sections.ALL.indexOf(p.section);
        if (!mainScroll.isInTouchMode() && at >= 0) list.rows().get(at).requestFocus();
        // What was found, looked for again (the section may have changed while it was open).
        if (list.searching()) list.run();
        after(mainScroll, () -> mainScroll.scrollTo(0, y));
        int w = stage.getWidth();
        p.root.animate().cancel();
        if (animate && w > 0) {
            p.root.animate().translationX(w).setDuration(SLIDE_MS - 40).setInterpolator(EASE).withEndAction(() -> {
                if (shown == p) return;
                p.root.setVisibility(View.GONE);
                p.root.setTranslationX(0f);
            }).start();
        } else {
            p.root.setVisibility(View.GONE);
        }
        backOn();
        freshLines();
    }

    // ---- a tablet: the list and the page side by side ---------------------------------------------------------------

    private View panes(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        boolean roomy = getResources().getConfiguration().screenWidthDp >= 1100;

        // The list, down the left, on a card of its own: Settings' name, the search, the sections under their headings.
        ScrollView listScroll = new ScrollView(c);
        listScroll.setVerticalScrollBarEnabled(false);
        GradientDrawable card = new GradientDrawable();
        // The navy's deeper shade, or the colour theme's panel (live.Theme).
        card.setColor(com.gathin.tvhomephone.live.Theme.card());
        card.setCornerRadius(Glass.dp(c, 24));
        card.setStroke(Math.max(1, Glass.dp(c, 1)), 0x1AFFFFFF);
        listScroll.setBackground(card);
        listScroll.setClipToOutline(true);
        LinearLayout inCard = new LinearLayout(c);
        inCard.setOrientation(LinearLayout.VERTICAL);
        int p = Glass.dp(c, 14);
        inCard.setPadding(p, Glass.dp(c, 24), p, Glass.dp(c, 24));
        inCard.addView(list.view, new LinearLayout.LayoutParams(-1, -2));
        listScroll.addView(inCard, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(Glass.dp(c, roomy ? 380 : 320), -1);
        lsp.setMargins(Glass.dp(c, 16), Glass.dp(c, 12), 0, Glass.dp(c, 4));
        row.addView(listScroll, lsp);

        // The page: each section made when first chosen (each keeps what it is listening to), only the chosen one
        // showing, in a column no wider than reads well, in the middle of what is left.
        pageScroll = new ScrollView(c);
        pageScroll.setClipToPadding(false);
        pageCol = new LinearLayout(c);
        pageCol.setOrientation(LinearLayout.VERTICAL);
        pageCol.setPadding(0, Glass.dp(c, 8), 0, Glass.dp(c, 32));
        pageCanvas = centred(c, pageCol, Glass.dp(c, 32));
        pageScroll.addView(pageCanvas, new FrameLayout.LayoutParams(-1, -2));
        row.addView(pageScroll, new LinearLayout.LayoutParams(0, -1, 1f));
        return row;
    }

    private Page tabletPage(Context c, Sections.Section s, boolean quietly) {
        FrameLayout holder = new FrameLayout(c);
        holder.setVisibility(View.GONE);
        if (quietly) holder.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        pageCol.addView(holder, new LinearLayout.LayoutParams(-1, -2));
        View content = s.build.make(this, pageCol);
        if (quietly) unfocus(content);
        holder.addView(content, new FrameLayout.LayoutParams(-1, -2));
        return new Page(s, holder, content, pageScroll, pageCanvas);
    }

    /** That section's page shown beside the list (from its top, or at target, lit when light), its row lit; kept for next time. */
    private void choose(Sections.Section s, View target, boolean light, boolean keep) {
        Page p = page(s, false);
        for (Page q : made.values()) q.root.setVisibility(q == p ? View.VISIBLE : View.GONE);
        p.root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        list.choose(s.key);
        View found = target == null ? null : SettingsSearch.locate(p.content, target, textOf(target));
        if (found == null) pageScroll.scrollTo(0, 0);
        else after(pageScroll, () -> show(p, found, light));
        if (keep && isAdded()) prefs(requireContext()).edit().putString(CHOSEN, s.key).apply();
        freshLines();
    }

    private SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Pure: a section's place in the list (the first for one not known). */
    static int keyAt(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return 0;
    }

    // ---- the list's asks --------------------------------------------------------------------------------------------

    @Override public void open(Sections.Section s) {
        if (panes) choose(s, null, false, true);
        else open(s, null, true, false);
    }

    @Override public void openAt(SettingsSearch.Hit hit) {
        Page p = page(hit.section, false);
        View at = hit.view == null ? null : SettingsSearch.locate(p.content, hit.view, hit.text);
        if (panes) choose(hit.section, at, true, true);
        else open(hit.section, at, true, true);
    }

    @Override public View page(Sections.Section s) {
        Page p = made.get(s.key);
        return p == null ? null : p.content;
    }

    @Override public boolean allMade() {
        return made.size() >= Sections.ALL.size();
    }

    @Override public void makeAll(Runnable after) {
        each = after;
        if (making || allMade()) return;
        making = true;
        View now = root;
        App.main.post(new Runnable() {
            @Override public void run() {
                if (root != now || root == null || !isAdded()) {
                    making = false;
                    return;
                }
                for (Sections.Section s : Sections.ALL) {
                    if (made.containsKey(s.key)) continue;
                    page(s, true);
                    if (each != null) each.run();
                    App.main.post(this);
                    return;
                }
                making = false;
                if (each != null) each.run();
            }
        });
    }

    @Override public void queried(boolean on) {
        backOn();
    }

    /** A section's page, made the first time it is wanted (quietly: for the search, not to be shown yet). */
    private Page page(Sections.Section s, boolean quietly) {
        Page p = made.get(s.key);
        if (p != null) return p;
        Context c = requireContext();
        p = panes ? tabletPage(c, s, quietly) : phonePage(c, s, quietly);
        made.put(s.key, p);
        return p;
    }

    // ---- where on a page: the playlists, a setting found ------------------------------------------------------------

    /** Live TV's section, at its playlists (where a playlist's notification points). */
    private void playlists() {
        Sections.Section live = Sections.of(LIVE);
        Page p = page(live, false);
        View at = playlistsOn(requireContext(), p.content);
        if (panes) choose(live, at, false, false);
        else open(live, at, false, false);
    }

    /**
     * Where Live TV's section shows its playlists: the first of Use (shown with two or more), a playlist's name, or Add
     * playlist, as the page has them now; null when none shows.
     */
    static View playlistsOn(Context c, View content) {
        Set<String> names = new HashSet<>();
        names.add(c.getString(R.string.live_use));
        for (com.gathin.tvhomephone.live.Sources.Entry e : com.gathin.tvhomephone.live.Sources.list(c)) names.add(e.name);
        names.add(c.getString(R.string.live_add));
        for (SettingsSearch.Found f : SettingsSearch.collect(content)) if (names.contains(f.text)) return f.view;
        return null;
    }

    /** A setting on its page: scrolled to, then lit for a moment when light. */
    private void show(Page p, View target, boolean light) {
        reveal(p, target);
        if (light) light(p, target);
    }

    /** The page scrolled so target sits near its top (and a row of chips sideways to it). */
    private void reveal(Page p, View target) {
        View inside = p.scroll.getChildAt(0);
        int y = topIn(target, inside);
        if (y < 0) return;
        p.scroll.scrollTo(0, Math.max(0, y - Glass.dp(requireContext(), 24)));
        int x = 0;
        for (View at = target; at != null && at != inside; ) {
            ViewParent up = at.getParent();
            if (up instanceof HorizontalScrollView) {
                ((HorizontalScrollView) up).scrollTo(Math.max(0, x + at.getLeft() - Glass.dp(requireContext(), 16)), 0);
                break;
            }
            x += at.getLeft();
            at = up instanceof View ? (View) up : null;
        }
    }

    /** Pure but for the views: v's top in the coordinates of inside (which holds it); -1 when it doesn't. */
    static int topIn(View v, View inside) {
        int y = 0;
        for (View at = v; at != inside; ) {
            if (at == null) return -1;
            y += at.getTop();
            ViewParent up = at.getParent();
            at = up instanceof View ? (View) up : null;
        }
        return y;
    }

    /** The row a setting's words are on: a switch's whole line, or the button or chip itself. */
    static View rowOf(View v, View page) {
        if (v.isClickable()) return v;
        View at = v;
        for (int up = 0; up < 2; up++) {
            ViewParent parent = at.getParent();
            if (!(parent instanceof LinearLayout) || parent == page) break;
            LinearLayout l = (LinearLayout) parent;
            if (l.getParent() instanceof HorizontalScrollView) break;
            if (l.getOrientation() == LinearLayout.HORIZONTAL) return l;
            at = l;
        }
        return v;
    }

    /**
     * What a setting's light covers: its row; and a short name over a row of choices (Rewind's, Picture quality's)
     * with those choices under it.
     */
    static View[] extent(View row) {
        if (!(row instanceof TextView) || row.isClickable() || SettingsSearch.plain(((TextView) row).getText()).length() > 48
                || !(row.getParent() instanceof ViewGroup)) return new View[]{row};
        ViewGroup in = (ViewGroup) row.getParent();
        int i = in.indexOfChild(row);
        View next = i + 1 < in.getChildCount() ? in.getChildAt(i + 1) : null;
        if (next == null || next.getVisibility() != View.VISIBLE) return new View[]{row};
        if (next instanceof HorizontalScrollView) return new View[]{row, next};
        if (next instanceof LinearLayout && ((LinearLayout) next).getOrientation() == LinearLayout.HORIZONTAL
                && ((LinearLayout) next).getChildCount() > 0) {
            LinearLayout l = (LinearLayout) next;
            for (int k = 0; k < l.getChildCount(); k++) if (!l.getChildAt(k).isClickable()) return new View[]{row};
            return new View[]{row, next};
        }
        return new View[]{row};
    }

    /** v's box in the coordinates of in (which holds it); null when it doesn't. */
    static Rect boxIn(View v, View in) {
        Rect r = new Rect(0, 0, v.getWidth(), v.getHeight());
        for (View at = v; at != in; ) {
            if (at == null) return null;
            r.offset(at.getLeft() - (at != v ? at.getScrollX() : 0), at.getTop() - (at != v ? at.getScrollY() : 0));
            ViewParent up = at.getParent();
            at = up instanceof View ? (View) up : null;
        }
        return r;
    }

    /** A setting found, lit for a moment: the accent's soft wash behind its row and a rim round it, coming and going. */
    private void light(Page p, View target) {
        Context c = requireContext();
        Rect r = null;
        for (View v : extent(rowOf(target, p.content))) {
            Rect b = boxIn(v, p.canvas);
            if (b == null) return;
            if (r == null) r = b;
            else r.union(b);
        }
        if (r == null) return;
        r.inset(-Glass.dp(c, 10), -Glass.dp(c, 5));
        r.left = Math.max(0, r.left);
        r.right = Math.min(p.canvas.getWidth(), r.right);
        if (lit instanceof Glow) ((Glow) lit).done();
        Glow glow = new Glow(c, SettingsLines.accent(), r);
        // Behind the section's own writing: the frame round its column draws it as its ground.
        p.canvas.setBackground(glow);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(LIGHT_MS);
        a.setInterpolator(new android.view.animation.LinearInterpolator());
        a.addUpdateListener(v -> glow.setAlpha(Math.round(255 * Glow.level(v.getAnimatedFraction()))));
        glow.animator = a;
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (p.canvas.getBackground() == glow) p.canvas.setBackground(null);
            }
        });
        a.start();
        lit = glow;
    }

    /** How long a setting found stays lit. */
    static final long LIGHT_MS = 1800;
    /** The last setting lit (tests). */
    Drawable lit;

    /** The accent's soft wash and its rim, rounded, at its place in the frame it is the ground of. */
    static final class Glow extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        final Rect at = new Rect();
        private final float radius;
        private final int fillAlpha, rimAlpha;
        private int alpha;
        ValueAnimator animator;

        /** Pure: how lit, through its time (0 to 1): in quickly, held, then out slowly. */
        static float level(float t) {
            if (t <= 0f || t >= 1f) return 0f;
            if (t < 0.12f) return t / 0.12f;
            return t > 0.62f ? (1f - t) / 0.38f : 1f;
        }

        /** How lit it is now (0 to 255). */
        int level() {
            return alpha;
        }

        /** Out at once (another lit in its place). */
        void done() {
            if (animator != null) animator.end();
        }

        Glow(Context c, int accent, Rect where) {
            at.set(where);
            radius = Glass.dp(c, 14);
            fill.setColor(accent);
            fillAlpha = 0x30;
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1f, Glass.dp(c, 1.5f)));
            rim.setColor(accent);
            rimAlpha = 0xC8;
            setAlpha(0);
        }

        @Override public void draw(@NonNull Canvas cv) {
            box.set(at);
            cv.drawRoundRect(box, radius, radius, fill);
            box.inset(rim.getStrokeWidth() / 2, rim.getStrokeWidth() / 2);
            cv.drawRoundRect(box, radius, radius, rim);
        }

        @Override public void setAlpha(int alpha) {
            this.alpha = alpha;
            fill.setAlpha(fillAlpha * alpha / 255);
            rim.setAlpha(rimAlpha * alpha / 255);
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
            rim.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private static String textOf(View v) {
        if (v instanceof android.widget.EditText) {
            CharSequence h = ((android.widget.EditText) v).getHint();
            return h == null ? "" : SettingsSearch.plain(h);
        }
        return v instanceof TextView ? SettingsSearch.plain(((TextView) v).getText()) : "";
    }

    /**
     * A section made for the search only: nothing in it holds the focus (Live TV's form asks for it when there is no
     * playlist), so the search keeps it.
     */
    private static void unfocus(View content) {
        View f = content.findFocus();
        if (f != null) f.clearFocus();
    }

    /**
     * The section's own heading, when it only says the section's name again (the page's title says it): hidden, with
     * the room over it. Nothing else on the page changes.
     */
    static void ownHeading(Context c, View content, String name) {
        if (!(content instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) content;
        if (g.getChildCount() == 0 || !(g.getChildAt(0) instanceof TextView)) return;
        TextView t = (TextView) g.getChildAt(0);
        if (!SettingsSearch.plain(t.getText()).equalsIgnoreCase(name)) return;
        t.setVisibility(View.GONE);
        g.setPadding(g.getPaddingLeft(), Math.min(g.getPaddingTop(), Glass.dp(c, 4)), g.getPaddingRight(), g.getPaddingBottom());
    }

    /** Runs once v (and what holds it) has been laid out again. */
    static void after(View v, Runnable r) {
        v.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                v.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                r.run();
            }
        });
        v.requestLayout();
    }

    /**
     * A column in the middle of what it is given, as wide as it may be (Wide.TEXT_MAX_DP) with at least least either
     * side: the room either side worked out as it is measured, so the first frame has it right.
     */
    static FrameLayout centred(Context c, View column, int least) {
        int most = Glass.dp(c, Wide.TEXT_MAX_DP);
        FrameLayout centre = new FrameLayout(c) {
            @Override protected void onMeasure(int ws, int hs) {
                // The column's own padding counts towards the room either side.
                int side = Math.max(0, Wide.side(MeasureSpec.getSize(ws), most, least) - column.getPaddingLeft());
                if (getPaddingLeft() != side) setPadding(side, 0, side, 0);
                super.onMeasure(ws, hs);
            }
        };
        centre.addView(column, new FrameLayout.LayoutParams(-1, -2));
        return centre;
    }

    // ---- for the tests ----------------------------------------------------------------------------------------------

    SettingsList list() {
        return list;
    }

    /** The section whose page shows (a phone's over the main page, a tablet's beside the list); null for the main page. */
    String showing() {
        if (panes) return list == null ? null : list.chosen();
        return shown == null ? null : shown.section.key;
    }

    /** That section's page as made (null before). */
    Page made(String key) {
        return made.get(key);
    }

    ScrollView mainScroll() {
        return mainScroll;
    }

    List<Sections.Section> sections() {
        return Sections.ALL;
    }
}
