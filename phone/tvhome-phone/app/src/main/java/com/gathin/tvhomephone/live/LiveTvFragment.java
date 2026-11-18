package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.MainActivity;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.glass.Glass;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/**
 * Live TV as a TV guide: the family's own playlist as a grid of channels down and half hours across, with a red line at
 * now. Along the top: the title, Now (back to the current time), search (channels and programmes), the guide or list
 * view, and More (Manage favourites, Hidden channels, Refresh); then "Your channels" (the favourites' logos: tap one to
 * watch). The categories are down the side (CategoryPanel): a button at the guide's top left, with their glyph and the
 * one showing, slides them in from the left over a scrim (Favourites first, chosen when there are any, then All, Recent,
 * Sport: SportGuide, today's games on the family's channels in place of the grid, and the playlist's own, its favourite
 * ones first); on a tablet on its side they can stay docked beside the guide, the button tucking them away and back as
 * it was last left. Above the grid the chosen
 * programme's details. Held upright, all that sits between the top bar and the guide folds away (Fold): a swipe up
 * gives the guide the whole height and a swipe down at its top brings it back; the picture plays on out of sight, and
 * opens again when a channel is played. Folded, a later programme tapped shows its sheet in place of the details.
 * Tap a channel (or a programme that is on) to watch it in the guide's own picture, with sound (Inline): on a phone held
 * upright a 16:9 picture at the top with the guide still there under it, on a tablet held upright beside the details,
 * and on a tablet on its side in the pane beside the grid. Tap it again, its full screen button or swipe up for full
 * screen, which takes the same stream over and gives it back; turning the phone on its side while it plays goes full
 * screen too, and upright again comes back. A phone on its side goes straight to full screen. The tab opens again on the
 * category chosen last, and the channel played last plays again in the guide's picture as it opens (LastWatch: only
 * waiting, paused until tapped, when its picture was closed with ✕). A later programme shows its details and Remind me; the star makes
 * the channel a favourite; a long press opens the channel's menu (favourite, hide, similar channels, its info). The day
 * button at the grid's corner picks the day. The list view is the Now and Next list. The page fills the whole screen:
 * its black runs up under the status bar and down under the floating tab bar, the guide's rows scrolling on under it.
 */
public class LiveTvFragment extends Fragment {
    private static final String S_GROUP = "group", S_KEY = "key", S_START = "start", S_LEFT = "left", S_UPRIGHT = "upright",
            S_FOLDED = "folded";
    private static final int COMPACT = 0, TOP = 1, SIDE = 2;
    private static final int SEARCH_CHANNELS = 60, SEARCH_PROGRAMMES = 120;

    private final Handler h = new Handler(Looper.getMainLooper());
    private Library lib;
    private Playback pb;
    private int mode;
    private boolean tablet, listView, searching;
    /** Upright phones: the picture is open at the top; turned on its side while it played (so: full screen). */
    private boolean inlineOpen, autoFull;
    /** The phone turned on its side with its rotation locked (watchTurns). */
    private final Turn turn = new Turn();
    private android.view.OrientationEventListener turner;
    /**
     * The header over the guide was folded away before the page was made again (turned round): kept only while the tab
     * is open, carried through a turn on its side, where nothing folds.
     */
    private boolean startFolded;
    /** Made fresh (not turned round): the first start plays the last channel again. */
    private boolean fresh;
    /** The tab has just opened (or come back): the channel played last plays again once the playlist is ready. */
    private boolean autoPlay, started;
    private String group;
    private String selKey;
    private long selStart, restoreLeft = -1;
    private String query = "";
    private int searchSeq;
    private List<String> shownGroups;
    /** The categories' hidden and favourite ones as last drawn (Groups.version). */
    private int shownGroupsVersion = -1;
    private boolean shownFavs, shownRecent;

    private LinearLayout toolbar, searchBar, stripRow, band, side;
    private TextView sub;
    /** The top bar's title: the category chosen. */
    private TextView titleView;
    private EditText search;
    private MaterialButton nowButton;
    private ImageButton viewButton;
    private TextView playlistPill;
    private RecyclerView strip, list, results;
    private final Strip stripAdapter = new Strip();
    private final Rows listAdapter = new Rows(), resultsAdapter = new Rows();
    /**
     * The categories down the side (CategoryPanel, the family's ask): the bar over the guide with its button at the top
     * left (the categories' glyph and the one showing) and how many channels it has; the panel, sliding in over a scrim,
     * or on a tablet on its side docked beside the guide (dock), tucked away and back with the button, as last left.
     */
    private LinearLayout guideBar;
    private TextView catsButton, catsCount;
    private CategoryPanel cats;
    private List<String[]> catItems = new ArrayList<>();
    private boolean dock, dockedOpen, docking;
    /** Docked: the frame the guide's column (main) and the panel share, and that column, which slides beside it. */
    private FrameLayout stage;
    private View guideColumn;
    private OnBackPressedCallback catsBack;
    private FrameLayout body;
    /** Upright: the picture, Your channels, the categories and the details over the guide, folding away (null on its side). */
    private Fold fold;
    private GuideGrid grid;
    private Details details;
    private Inline inline;
    private View state, favHint;
    /** The Sport choice: today's games on the family's channels, in place of the guide while it is chosen. */
    private SportGuide sport;
    /** On its side: the scrolling details beside the grid. */
    private ScrollView sideScroll;
    /** The room at the page's foot that the tab bar and the navigation bar cover (the keyboard's height while it is up). */
    private int foot;
    private OnBackPressedCallback back;

    private final Runnable changed = this::refreshAll;
    private final Runnable runSearch = this::searchNow;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (grid != null) {
                grid.tick();
                Timeline tl = grid.timeline();
                if (tl != null && System.currentTimeMillis() + 3600_000L > tl.end) timeline(false);
            }
            if (details != null) details.refresh();
            if (inline != null) inline.refresh();
            if (sport != null && sport.getVisibility() == View.VISIBLE) sport.load();
            listAdapter.notifyItemRangeChanged(0, listAdapter.getItemCount());
            h.postDelayed(this, 60_000);
        }
    };

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        Context c = requireContext();
        lib = Library.get(c);
        pb = Playback.get(c);
        Configuration cfg = getResources().getConfiguration();
        tablet = cfg.smallestScreenWidthDp >= 600;
        boolean land = cfg.screenWidthDp > cfg.screenHeightDp;
        mode = land ? SIDE : tablet ? TOP : COMPACT;
        listView = ui(c).getBoolean("list", false);
        inlineOpen = ui(c).getBoolean("inline", false);
        fresh = saved == null;
        // A fresh visit (the tabs are made anew each time): the category chosen last time, checked once the playlist is in.
        if (saved == null) group = ui(c).getString(LAST_GROUP, null);
        if (saved != null) {
            group = saved.getString(S_GROUP);
            selKey = saved.getString(S_KEY);
            selStart = saved.getLong(S_START);
            restoreLeft = saved.getLong(S_LEFT, -1);
            startFolded = saved.getBoolean(S_FOLDED, false);
            // A phone turned on its side while its picture played (no one showing it for the moment): full screen.
            autoFull = saved.getBoolean(S_UPRIGHT, false) && mode == SIDE && !tablet && pb.unattended() && pb.live();
        }

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        // The guide's dark look (GuideLook): black, the panels a near-black grey.
        root.setBackgroundColor(GuideLook.GROUND);

        buildToolbar(c, root);
        // While casting: "Casting to …" with Stop, under the top bar (it shows and hides itself).
        root.addView(new CastViews.Bar(c), new LinearLayout.LayoutParams(-1, -2));
        // Held upright (phone or tablet), everything between the top bar and the guide folds away (Fold); on its side,
        // the categories over the grid and the pane beside it stay.
        LinearLayout head = null;
        if (mode != SIDE) {
            head = new LinearLayout(c);
            head.setOrientation(LinearLayout.VERTICAL);
        }
        inline = new Inline(c, lib, mode == COMPACT);
        inline.setListener(inlineListener());
        if (mode == COMPACT) {
            // Upright phones: the picture across the top, over the guide, when something has been played.
            head.addView(inline, new LinearLayout.LayoutParams(-1, -2));
            inline.setVisibility(View.GONE);
        }
        buildStrip(c);
        if (mode == SIDE) {
            // On its side, Your channels sits in the top bar, leaving the height to the grid.
            toolbar.addView(stripRow, 1, new LinearLayout.LayoutParams(0, Ui.dp(c, 52), 1f));
            ((LinearLayout.LayoutParams) toolbar.getChildAt(0).getLayoutParams()).weight = 0;
            toolbar.getChildAt(0).getLayoutParams().width = -2;
        } else {
            head.addView(stripRow, new LinearLayout.LayoutParams(-1, Ui.dp(c, 60)));
        }

        // The categories on the side: the bar at the guide's top (always in sight, the guide, the list or Sport under it)
        // with the button that slides the panel in; docked beside the guide on a tablet on its side with the room.
        guideBar = buildGuideBar(c);
        dock = CategoryPanel.docks(tablet, mode == SIDE, cfg.screenWidthDp, SIDE_TABLET_DP, GUIDE_MIN_DP);
        dockedOpen = dock && ui(c).getBoolean(DOCKED, true);
        cats = new CategoryPanel(c, dock);
        cats.setListener(catsListener());
        if (mode == SIDE) root.addView(guideBar, new LinearLayout.LayoutParams(-1, Ui.dp(c, 52)));

        body = new FrameLayout(c);
        Details.Actions actions = actions();
        if (mode == COMPACT) {
            details = new Details(c, lib, true, 2);
            head.addView(details, new LinearLayout.LayoutParams(-1, -2));
        } else if (mode == TOP) {
            band = new LinearLayout(c);
            band.setBackgroundColor(GuideLook.PANEL);
            band.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), 0, Ui.dp(c, 6));
            // The picture about half the tablet's width (288 to 420dp), 16:9, the details beside it.
            int iw = Math.min(Ui.dp(c, 420), Math.max(Ui.dp(c, 288), getResources().getDisplayMetrics().widthPixels / 2 - Ui.dp(c, 12)));
            band.addView(inline, new LinearLayout.LayoutParams(iw, iw * 9 / 16));
            details = new Details(c, lib, false, 4);
            band.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(band, new LinearLayout.LayoutParams(-1, -2));
        } else {
            LinearLayout content = new LinearLayout(c);
            LinearLayout main = new LinearLayout(c);
            main.setOrientation(LinearLayout.VERTICAL);
            main.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
            guideColumn = main;
            if (dock) {
                // The panel docked at the guide's left, the guide beside it (or across all of it, tucked away).
                stage = new FrameLayout(c);
                FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-1, -1);
                mp.leftMargin = dockedOpen ? Ui.dp(c, CategoryPanel.DOCKED_DP) : 0;
                stage.addView(main, mp);
                stage.addView(cats, new FrameLayout.LayoutParams(Ui.dp(c, CategoryPanel.DOCKED_DP), -1, Gravity.START));
                cats.setVisibility(dockedOpen ? View.VISIBLE : View.GONE);
                if (dockedOpen) cats.open(false);
                content.addView(stage, new LinearLayout.LayoutParams(0, -1, 1f));
            } else {
                content.addView(main, new LinearLayout.LayoutParams(0, -1, 1f));
            }
            side = new LinearLayout(c);
            side.setOrientation(LinearLayout.VERTICAL);
            side.setBackgroundColor(GuideLook.PANEL);
            int sw = Ui.dp(c, tablet ? SIDE_TABLET_DP : 300);
            int pad = Ui.dp(c, 14);
            side.setPadding(pad, pad, pad, 0);
            side.addView(inline, new LinearLayout.LayoutParams(-1, (sw - 2 * pad) * 9 / 16));
            ScrollView ds = new ScrollView(c);
            sideScroll = ds;
            details = new Details(c, lib, false, tablet ? 8 : 5);
            details.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 16));
            ds.addView(details, new FrameLayout.LayoutParams(-1, -2));
            side.addView(ds, new LinearLayout.LayoutParams(-1, 0, 1f));
            content.addView(side, new LinearLayout.LayoutParams(sw, -1));
            root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        details.setActions(actions);
        if (head != null) {
            // Upright: the categories' bar at the top of what stays when the rest folds away, the guide under it.
            LinearLayout guideCol = new LinearLayout(c);
            guideCol.setOrientation(LinearLayout.VERTICAL);
            guideCol.addView(guideBar, new LinearLayout.LayoutParams(-1, Ui.dp(c, 52)));
            guideCol.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
            fold = new Fold(c, head, guideCol);
            fold.setPicture(inline);
            fold.setListener(shown -> {
                centre(favHint);
                centre(state);
            });
            if (startFolded) fold.setShut(true, false);
            root.addView(fold, new LinearLayout.LayoutParams(-1, 0, 1f));
        }

        grid = new GuideGrid(c, lib, tablet);
        grid.setListener(gridListener());
        grid.corner.setOnClickListener(this::pickDay);
        body.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        list = rowsView(c, listAdapter);
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));
        results = rowsView(c, resultsAdapter);
        body.addView(results, new FrameLayout.LayoutParams(-1, -1));
        results.setVisibility(View.GONE);
        favHint = favouritesHint(c);
        body.addView(favHint, new FrameLayout.LayoutParams(-1, -1));
        favHint.setVisibility(View.GONE);
        sport = new SportGuide(c, this::play);
        // Its scrolling folds the header away as the guide's does.
        sport.setNestedScrollingEnabled(fold != null);
        body.addView(sport, new FrameLayout.LayoutParams(-1, -1));
        sport.setVisibility(View.GONE);
        // Over it all, the categories' panel and its scrim (docked, it is in the guide's frame instead).
        FrameLayout frame = new FrameLayout(c);
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));
        if (!dock) frame.addView(cats, new FrameLayout.LayoutParams(-1, -1));
        // The whole screen (MainActivity leaves Live TV the window): the black up under the status bar, the top bar just
        // under it, clear of a navigation bar at a side; at the foot everything scrolls on under the tab bar.
        Glass.fitSides(frame, (left, top, right, bottom) -> {
            root.setPadding(left, top, right, 0);
            foot = bottom;
            feet();
            cats.fit(left, top, bottom);
        });
        return frame;
    }

    /** A tablet's pane beside the guide on its side (the picture and the details), and the least the guide keeps. */
    private static final int SIDE_TABLET_DP = 380, GUIDE_MIN_DP = 560;
    /** Whether the categories stay docked beside the guide on a tablet on its side (as the button last left them). */
    private static final String DOCKED = "cats_docked";

    // ---- the categories on the side ---------------------------------------------------------------------------------

    /** The bar over the guide: the categories' button at its left, how many channels the one showing has at its right. */
    private LinearLayout buildGuideBar(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 16), 0);
        catsButton = Ui.text(c, "", 15, Ui.TEXT, true);
        catsButton.setGravity(Gravity.CENTER_VERTICAL);
        catsButton.setMinHeight(Ui.dp(c, 48));
        catsButton.setMaxLines(1);
        catsButton.setEllipsize(TextUtils.TruncateAt.END);
        catsButton.setMaxWidth(Ui.dp(c, tablet ? 380 : 270));
        android.graphics.drawable.Drawable glyph = androidx.core.content.ContextCompat.getDrawable(c, R.drawable.live_ic_categories);
        if (glyph != null) {
            glyph = glyph.mutate();
            int s = Ui.dp(c, 20);
            glyph.setBounds(0, 0, s, s);
            glyph.setTint(Ui.TEXT);
        }
        catsButton.setCompoundDrawablesRelative(glyph, null, null, null);
        catsButton.setCompoundDrawablePadding(Ui.dp(c, 10));
        catsButton.setOnClickListener(v -> openCats());
        paintCatsButton(c);
        bar.addView(catsButton, new LinearLayout.LayoutParams(-2, -1));
        catsCount = Ui.text(c, "", 13, Ui.DIM, false);
        catsCount.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        catsCount.setMaxLines(1);
        catsCount.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = Ui.dp(c, 12);
        bar.addView(catsCount, cp);
        return bar;
    }

    /** The button as a dark pill, lit (as the chosen chip was) while the docked panel is out beside the guide. */
    private void paintCatsButton(Context c) {
        boolean lit = dock && dockedOpen;
        styleDark(c, catsButton, lit);
        catsButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        int inset = Ui.dp(c, 4);
        catsButton.setPadding(Ui.dp(c, 14), inset, Ui.dp(c, 18), inset);
    }

    /** The button's words: the category showing (its flag or star as the panel has it), and what a tap does. */
    private void showCatsButton() {
        if (catsButton == null || !isAdded()) return;
        String words = CategoryPanel.words(catItems, group);
        catsButton.setText(words);
        int say = !dock ? R.string.cats_button : dockedOpen ? R.string.cats_button_docked : R.string.cats_button_tucked;
        catsButton.setContentDescription(getString(say, words));
        paintCatsButton(requireContext());
    }

    /** The button: the panel slides in over the page (out again if it is in), or the docked one is tucked away or back. */
    private void openCats() {
        if (dock) {
            dockedOpen = !dockedOpen;
            ui(requireContext()).edit().putBoolean(DOCKED, dockedOpen).apply();
            slideDock(dockedOpen);
            showCatsButton();
            return;
        }
        if (cats.isOpen()) {
            cats.close(true);
            return;
        }
        cats.choose(group);
        cats.open(true);
        if (catsBack != null) catsBack.setEnabled(true);
    }

    private CategoryPanel.Listener catsListener() {
        return new CategoryPanel.Listener() {
            @Override public void onPick(String key) {
                pickGroup(key);
                // Shown in the guide: the panel slides away (docked, it stays).
                if (!cats.docked()) cats.close(true);
            }

            @Override public void onHold(View row, String key) {
                groupMenu(row, key);
            }

            @Override public void onManage() {
                if (!cats.docked()) cats.close(false);
                Sheets.categories(requireContext(), lib);
            }

            @Override public void onShut() {
                if (catsBack != null) catsBack.setEnabled(false);
            }
        };
    }

    /**
     * The docked panel out or tucked away, sliding: the panel and the guide's column move together (the column only
     * translated while it moves, laid out again once, at the end of the slide or the start of the tuck, where nothing
     * shows the difference), so the guide is never laid out mid-slide.
     */
    private void slideDock(boolean open) {
        if (stage == null || guideColumn == null) return;
        Context c = requireContext();
        View main = guideColumn, sheet = cats.sheet();
        int w = Ui.dp(c, CategoryPanel.DOCKED_DP);
        FrameLayout.LayoutParams mp = (FrameLayout.LayoutParams) main.getLayoutParams();
        main.animate().cancel();
        sheet.animate().cancel();
        android.view.animation.Interpolator ease = new android.view.animation.DecelerateInterpolator(1.6f);
        docking = true;
        if (open) {
            cats.setVisibility(View.VISIBLE);
            cats.open(false);
            sheet.setTranslationX(-w);
            main.animate().translationX(w).setDuration(CategoryPanel.SLIDE_MS).setInterpolator(ease).withEndAction(() -> {
                mp.leftMargin = w;
                main.setLayoutParams(mp);
                main.setTranslationX(0);
                docking = false;
            }).start();
            sheet.animate().translationX(0).setDuration(CategoryPanel.SLIDE_MS).setInterpolator(ease).withLayer().start();
        } else {
            mp.leftMargin = 0;
            main.setLayoutParams(mp);
            main.setTranslationX(w);
            cats.close(false);
            main.animate().translationX(0).setDuration(CategoryPanel.SLIDE_MS).setInterpolator(ease).start();
            sheet.animate().translationX(-w).setDuration(CategoryPanel.SLIDE_MS).setInterpolator(ease).withLayer().withEndAction(() -> {
                docking = false;
                if (!dockedOpen) {
                    cats.setVisibility(View.GONE);
                    sheet.setTranslationX(0);
                }
            }).start();
        }
    }

    /**
     * The docked panel as it should stand now, at once: out beside the guide when the button left it out and the guide
     * shows (not over a search, nor while there is no playlist); otherwise the guide across all of its frame.
     */
    private void layDock(boolean guideShows) {
        if (!dock || docking || stage == null || guideColumn == null) return;
        boolean out = dockedOpen && guideShows;
        FrameLayout.LayoutParams mp = (FrameLayout.LayoutParams) guideColumn.getLayoutParams();
        int margin = out ? Ui.dp(requireContext(), CategoryPanel.DOCKED_DP) : 0;
        if (mp.leftMargin != margin) {
            mp.leftMargin = margin;
            guideColumn.setLayoutParams(mp);
        }
        guideColumn.setTranslationX(0);
        cats.sheet().setTranslationX(0);
        cats.setVisibility(out ? View.VISIBLE : View.GONE);
        if (out && !cats.isOpen()) cats.open(false);
    }

    /**
     * Each part that scrolls (the guide's rows, the lists, Sport, the details on its side) runs on under the tab bar and
     * stops with its last row clear of it; anything centred in the body is centred in what the bars leave of it.
     */
    private void feet() {
        if (grid != null) grid.rows.setPadding(grid.rows.getPaddingLeft(), grid.rows.getPaddingTop(), grid.rows.getPaddingRight(), foot);
        for (RecyclerView r : new RecyclerView[]{list, results}) {
            if (r != null) r.setPadding(r.getPaddingLeft(), r.getPaddingTop(), r.getPaddingRight(), foot);
        }
        for (ScrollView v : new ScrollView[]{sport, sideScroll}) {
            if (v == null) continue;
            v.setClipToPadding(false);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), foot);
        }
        centre(favHint);
        centre(state);
    }

    /**
     * Something centred in the body (a hint, loading, an error): centred in what shows of it, as the body runs on under
     * the screen's foot by as much of the header as shows, and its foot is under the tab bar.
     */
    private void centre(View v) {
        if (v != null) v.setTranslationY(-((fold == null ? 0 : fold.shown()) + foot) / 2f);
    }

    /** The header folded away (so the programme's details aren't in sight). */
    private boolean folded() {
        return fold != null && fold.isShut();
    }

    /** Whether the Sport choice shows (in place of the guide). */
    private boolean sportShown() {
        return SportGuide.GROUP.equals(group) && !searching;
    }

    private static SharedPreferences ui(Context c) {
        return c.getApplicationContext().getSharedPreferences("live_ui", Context.MODE_PRIVATE);
    }

    /** The category chosen last; the picture closed with ✕ (so the last channel waits instead of playing). */
    private static final String LAST_GROUP = "last_group", STOPPED = "stopped";

    private void stopped(boolean on) {
        if (isAdded()) ui(requireContext()).edit().putBoolean(STOPPED, on).apply();
    }

    private RecyclerView rowsView(Context c, Rows adapter) {
        RecyclerView r = new RecyclerView(c);
        r.setLayoutManager(new LinearLayoutManager(c));
        r.setClipToPadding(false);
        int pad = Ui.dp(c, 12);
        r.setPadding(pad, Ui.dp(c, 4), pad, Ui.dp(c, 16));
        r.setAdapter(adapter);
        r.setItemAnimator(null);
        return r;
    }

    // ---- the top bar ------------------------------------------------------------------------------------------

    private void buildToolbar(Context c, LinearLayout root) {
        toolbar = new LinearLayout(c);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 4), 0);
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        // Homefield's own mark and name in the corner (the category chosen is the lit chip under the picture), the name
        // made smaller as it must to fit beside the buttons, never cut off.
        LinearLayout brand = new LinearLayout(c);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView mark = new android.widget.ImageView(c);
        mark.setImageResource(R.drawable.ic_app);
        mark.setClipToOutline(true);
        int markR = Ui.dp(c, 8);
        mark.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), markR);
            }
        });
        mark.setContentDescription(getString(R.string.app_name));
        brand.addView(mark, new LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)));
        // Sized to the room it is given as it is measured (FitName): smaller as it must, never cut off or under the
        // buttons, and the mark alone where even a small name has no room.
        titleView = new FitName(c, Ui.sp(c, NAME_SP), Ui.sp(c, MIN_NAME_SP));
        titleView.setText(R.string.app_name);
        titleView.setTextColor(Ui.TEXT);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(1);
        titleView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, Ui.dp(c, 30), 1f);
        tp.leftMargin = Ui.dp(c, 9);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        brand.addView(titleView, tp);
        titles.addView(brand, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout under = new LinearLayout(c);
        under.setGravity(Gravity.CENTER_VERTICAL);
        // Which playlists: all together or one, switched here (shown only with two or more).
        playlistPill = Ui.text(c, "", 12, Ui.TEXT, true);
        playlistPill.setSingleLine(true);
        playlistPill.setEllipsize(android.text.TextUtils.TruncateAt.END);
        playlistPill.setMaxWidth(Ui.dp(c, 140));
        playlistPill.setBackground(Ui.touch(c, false, 0x26FFFFFF, 12));
        playlistPill.setPadding(Ui.dp(c, 10), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
        playlistPill.setOnClickListener(this::playlistMenu);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, -2);
        pp.rightMargin = Ui.dp(c, 8);
        under.addView(playlistPill, pp);
        // The channels' count as the name is: a little smaller if it must, else not drawn (never cut off).
        sub = new FitName(c, Ui.sp(c, 12), Ui.sp(c, 10));
        sub.setTextColor(Ui.DIM);
        sub.setMaxLines(1);
        under.addView(sub, new LinearLayout.LayoutParams(0, -2, 1f));
        titles.addView(under);
        toolbar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        nowButton = new MaterialButton(c);
        nowButton.setText(R.string.live_now_button);
        nowButton.setAllCaps(false);
        nowButton.setIconResource(R.drawable.live_ic_now);
        nowButton.setIconSize(Ui.dp(c, 18));
        nowButton.setIconPadding(Ui.dp(c, 6));
        nowButton.setIconTint(ColorStateList.valueOf(Ui.TEXT));
        nowButton.setTextColor(Ui.TEXT);
        nowButton.setBackgroundTintList(ColorStateList.valueOf(GuideLook.PILL));
        nowButton.setStrokeColor(ColorStateList.valueOf(GuideLook.PILL_RIM));
        nowButton.setStrokeWidth(Ui.dp(c, 1));
        nowButton.setCornerRadius(Ui.dp(c, 20));
        nowButton.setInsetTop(Ui.dp(c, 4));
        nowButton.setInsetBottom(Ui.dp(c, 4));
        nowButton.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 14), 0);
        nowButton.setMinWidth(0);
        nowButton.setMinimumWidth(0);
        nowButton.setContentDescription(getString(R.string.live_now_hint));
        nowButton.setOnClickListener(v -> backToNow(true));
        toolbar.addView(nowButton, new LinearLayout.LayoutParams(-2, Ui.dp(c, 48)));
        ImageButton find = Ui.icon(c, R.drawable.live_ic_search, R.string.live_search);
        find.setOnClickListener(v -> openSearch());
        toolbar.addView(round(c, find), roundParams(c));
        viewButton = Ui.icon(c, R.drawable.live_ic_list, R.string.live_view_list);
        viewButton.setOnClickListener(v -> {
            listView = !listView;
            ui(c).edit().putBoolean("list", listView).apply();
            showBody();
            refreshRows();
        });
        toolbar.addView(round(c, viewButton), roundParams(c));
        // Cast: the TVs and devices on the Wi-Fi; the channel playing (or chosen) goes to the one picked.
        ImageButton cast = Ui.icon(c, R.drawable.live_ic_cast, R.string.cast_button);
        cast.setOnClickListener(v -> CastSheet.show(requireActivity(), castChannel()));
        toolbar.addView(round(c, cast), roundParams(c));
        ImageButton more = Ui.icon(c, R.drawable.live_ic_more, R.string.live_more);
        more.setOnClickListener(this::moreMenu);
        toolbar.addView(round(c, more), roundParams(c));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        searchBar = new LinearLayout(c);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 4));
        ImageButton close = Ui.icon(c, R.drawable.live_ic_back, R.string.live_search_close);
        close.setOnClickListener(v -> closeSearch());
        searchBar.addView(close, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        search = new EditText(c);
        search.setHint(R.string.live_search_hint);
        search.setHintTextColor(Ui.DIM);
        search.setTextColor(Ui.TEXT);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackground(Ui.round(c, Ui.GLASS, 22));
        search.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}

            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}

            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                h.removeCallbacks(runSearch);
                h.postDelayed(runSearch, 150);
            }
        });
        search.setOnEditorActionListener((v, id, e) -> {
            keyboard(false);
            return true;
        });
        searchBar.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(c, 46), 1f));
        searchBar.setVisibility(View.GONE);
        root.addView(searchBar, new LinearLayout.LayoutParams(-1, -2));
    }

    /** All playlists, or just one: the choice kept, the channels read again for it. */
    private void playlistMenu(View anchor) {
        Context c = requireContext();
        List<Sources.Entry> all = Sources.list(c);
        PopupMenu m = new PopupMenu(c, anchor);
        Menu menu = m.getMenu();
        String active = Sources.active(c);
        menu.add(0, 0, 0, R.string.live_all_playlists).setCheckable(true).setChecked(active.isEmpty());
        for (int i = 0; i < all.size(); i++) {
            Sources.Entry e = all.get(i);
            int n = lib.countIn(e.id);
            String label = e.name + (active.isEmpty() && n > 0 ? " · " + n : "");
            menu.add(0, i + 1, i + 1, label).setCheckable(true).setChecked(e.id.equals(active));
        }
        menu.setGroupCheckable(0, true, true);
        m.setOnMenuItemClickListener(item -> {
            String id = item.getItemId() == 0 ? Sources.ALL : all.get(item.getItemId() - 1).id;
            if (!id.equals(Sources.active(c))) {
                Sources.setActive(c, id);
                lib.switched();
                refreshAll();
            }
            return true;
        });
        m.show();
    }

    /** The pill's words: "All playlists ▾" or the chosen one's name; gone with only one playlist. */
    private void showPlaylistPill(Context c) {
        List<Sources.Entry> all = Sources.list(c);
        if (all.size() < 2) {
            playlistPill.setVisibility(View.GONE);
            return;
        }
        String active = Sources.active(c);
        Sources.Entry e = active.isEmpty() ? null : Sources.get(c, active);
        playlistPill.setText((e == null ? getString(R.string.live_all_playlists) : e.name) + "  ▾");
        playlistPill.setContentDescription(getString(R.string.live_playlist_choose));
        playlistPill.setVisibility(View.VISIBLE);
    }

    private void moreMenu(View anchor) {
        Context c = requireContext();
        PopupMenu m = new PopupMenu(c, anchor);
        Menu menu = m.getMenu();
        menu.add(0, 1, 0, R.string.live_manage_favourites);
        int hidden = Hidden.keys(c).size();
        menu.add(0, 4, 1, hidden > 0 ? getString(R.string.live_hidden_menu_count, hidden) : getString(R.string.live_hidden_title));
        int hiddenGroups = Groups.hidden(c).size();
        menu.add(0, 5, 2, hiddenGroups > 0 ? getString(R.string.groups_menu_count, hiddenGroups) : getString(R.string.groups_title));
        menu.add(0, 2, 3, listView ? R.string.live_view_grid : R.string.live_view_list);
        menu.add(0, 3, 4, R.string.live_refresh);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) Sheets.favourites(c, lib);
            else if (item.getItemId() == 4) Sheets.hidden(c, lib);
            else if (item.getItemId() == 5) Sheets.categories(c, lib);
            else if (item.getItemId() == 2) viewButton.performClick();
            else lib.refresh(null);
            return true;
        });
        m.show();
    }

    // ---- Your channels ------------------------------------------------------------------------------------------

    private void buildStrip(Context c) {
        stripRow = new LinearLayout(c);
        stripRow.setGravity(Gravity.CENTER_VERTICAL);
        if (mode != SIDE) {
            stripRow.setPadding(Ui.dp(c, 16), 0, 0, 0);
            TextView label = Ui.text(c, getString(R.string.live_your_channels_label), 11, Ui.G_SELECT, true);
            label.setLetterSpacing(0.08f);
            label.setMaxLines(2);
            // A tablet's larger type: a little more room, so "CHANNELS" keeps its last letters.
            label.setWidth(Ui.dp(c, tablet ? 76 : 62));
            stripRow.addView(label);
        }
        strip = new RecyclerView(c);
        strip.setLayoutManager(new LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false));
        strip.setAdapter(stripAdapter);
        strip.setClipToPadding(false);
        strip.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 12), 0);
        strip.setItemAnimator(null);
        strip.setContentDescription(getString(R.string.live_your_channels));
        stripRow.addView(strip, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    private final class Strip extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<Channel> favs = new ArrayList<>();

        @Override public int getItemCount() { return favs.size() + 1; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            FrameLayout tile = new FrameLayout(c);
            boolean small = mode == SIDE;
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.dp(c, small ? 60 : 68), Ui.dp(c, small ? 44 : 48));
            lp.rightMargin = Ui.dp(c, 8);
            lp.topMargin = lp.bottomMargin = Ui.dp(c, small ? 4 : 6);
            tile.setLayoutParams(lp);
            tile.setBackground(Ui.touch(c, false, Ui.GLASS, 10));
            TextView ini = Ui.text(c, "", 12, Ui.SOFT, true);
            ini.setGravity(Gravity.CENTER);
            tile.addView(ini, new FrameLayout.LayoutParams(-1, -1));
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = Ui.dp(c, 6);
            img.setPadding(p, p, p, p);
            tile.addView(img, new FrameLayout.LayoutParams(-1, -1));
            tile.setTag(new View[]{ini, img});
            return new RecyclerView.ViewHolder(tile) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Context c = holder.itemView.getContext();
            View[] v = (View[]) holder.itemView.getTag();
            TextView ini = (TextView) v[0];
            ImageView img = (ImageView) v[1];
            if (position >= favs.size()) {
                // The last tile: Manage favourites.
                Glide.with(img).clear(img);
                img.setImageResource(R.drawable.live_ic_edit);
                img.setImageTintList(ColorStateList.valueOf(Ui.G_SELECT));
                img.setScaleType(ImageView.ScaleType.CENTER);
                ini.setText("");
                holder.itemView.setContentDescription(getString(R.string.live_manage_favourites));
                holder.itemView.setOnClickListener(x -> Sheets.favourites(c, lib));
                holder.itemView.setOnLongClickListener(null);
                return;
            }
            Channel ch = favs.get(position);
            img.setImageTintList(null);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ini.setText(ch.logo.isEmpty() ? Ui.initials(ch.name) : "");
            if (ch.logo.isEmpty()) Glide.with(img).clear(img);
            else Glide.with(img).load(ch.logo).into(img);
            holder.itemView.setContentDescription(getString(R.string.live_watch_named, ch.name));
            holder.itemView.setOnClickListener(x -> play(ch));
            holder.itemView.setOnLongClickListener(x -> {
                menu(ch);
                return true;
            });
        }
    }

    // ---- the groups ---------------------------------------------------------------------------------------------

    /** A dark pill, as in the guide's look (40dp to see, 48dp to touch), lit with a white rim when on. */
    static void styleDark(Context c, TextView t, boolean on) {
        int inset = Ui.dp(c, 4);
        android.graphics.drawable.GradientDrawable g = Ui.ring(c, on ? GuideLook.PILL_ON : GuideLook.PILL,
                on ? Ui.TEXT : GuideLook.PILL_RIM, on ? 1.5f : 1, 20);
        t.setBackground(new android.graphics.drawable.InsetDrawable(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF), g, Ui.round(c, Ui.TEXT, 20)), 0, inset, 0, inset));
        t.setPadding(Ui.dp(c, 16), inset, Ui.dp(c, 16), inset);
        t.setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        t.setSelected(on);
    }

    /** The categories for the panel, in the app's order: Favourites, All, Recent, Sport, then the playlist's own. */
    private void buildCats() {
        Context c = requireContext();
        java.util.Set<String> hiddenGroups = Groups.hidden(c);
        // Favourite categories first (a star), the hidden ones left out.
        catItems = CategoryPanel.items(getString(R.string.live_favourites), getString(R.string.live_all), getString(R.string.live_recent),
                getString(R.string.f1_sport), lib.groups(), hiddenGroups, Groups.favourites(c));
        if (hiddenGroups.contains(group)) group = Library.ALL;
        shownGroups = lib.groups();
        shownGroupsVersion = Groups.version;
        cats.set(catItems, group);
        showCatsButton();
    }

    /** A category held: make it a favourite (first in the panel) or not, hide it, or all the categories. */
    private void groupMenu(View anchor, String g) {
        Context c = requireContext();
        PopupMenu m = new PopupMenu(c, anchor);
        boolean fav = Groups.isFavourite(c, g);
        m.getMenu().add(0, 1, 0, fav ? R.string.groups_unfavourite : R.string.groups_favourite);
        m.getMenu().add(0, 2, 1, R.string.groups_hide);
        m.getMenu().add(0, 3, 2, R.string.groups_manage);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) Groups.setFavourite(c, g, !fav);
            else if (item.getItemId() == 2) {
                Groups.setHidden(c, g, true);
                Toast.makeText(c, getString(R.string.groups_hidden_toast, g), Toast.LENGTH_LONG).show();
            } else Sheets.categories(c, lib);
            return true;
        });
        m.show();
    }

    /** The panel's rows drawn again (the one showing lit) and the button's words. */
    private void refreshCats() {
        cats.choose(group);
        showCatsButton();
    }

    /** The name's size beside the mark: at most NAME_SP, and not shown at all when even MIN_NAME_SP is too big. */
    private static final float NAME_SP = 21f, MIN_NAME_SP = 14f;

    /**
     * Homefield's name beside its mark: as large as fits in the width it is given (at most max px), worked out as it is
     * measured, so the first frame already has it right: never cut off, never under the Now button. With no room for
     * it even at min px (a narrow phone, large display text), it is not drawn, and the mark stands alone.
     */
    static final class FitName extends androidx.appcompat.widget.AppCompatTextView {
        private final float max, min;

        FitName(android.content.Context c, float max, float min) {
            super(c);
            this.max = max;
            this.min = min;
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, max);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int room = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
            if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED && room > 0) {
                float px = fitSize(room, getPaint(), String.valueOf(getText()), max);
                boolean show = px >= min;
                float use = show ? px : min;
                if (Math.abs(getTextSize() - use) > 0.5f) setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, use);
                setAlpha(show ? 1f : 0f);
            }
            super.onMeasure(widthSpec, heightSpec);
        }
    }

    /** Pure: the text size (px) at which words fit in room px, at most max px (sizes scale the width in step). */
    static float fitSize(int room, android.graphics.Paint paint, String words, float max) {
        android.text.TextPaint p = new android.text.TextPaint(paint);
        p.setTextSize(max);
        float w = p.measureText(words);
        if (w <= 0 || w <= room) return max;
        // A hair under, so rounding never leaves the last letter clipped.
        return max * (room / w) * 0.98f;
    }

    /** A top-bar button as a dark circle, as the guide's own buttons. */
    private static ImageButton round(Context c, ImageButton b) {
        b.setBackground(Ui.touch(c, false, GuideLook.PILL, com.gathin.tvhomephone.glass.Wide.tablet(c) ? 24 : 22));
        return b;
    }

    private static LinearLayout.LayoutParams roundParams(Context c) {
        // A little larger to touch on a tablet.
        int s = Ui.dp(c, com.gathin.tvhomephone.glass.Wide.tablet(c) ? 48 : 44);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        lp.leftMargin = Ui.dp(c, 6);
        return lp;
    }

    private void pickGroup(String g) {
        if (g.equals(group)) return;
        group = g;
        ui(requireContext()).edit().putString(LAST_GROUP, g).apply();
        refreshCats();
        refreshRows();
        if (SportGuide.GROUP.equals(g)) sport.load();
        showBody();
        grid.rows.scrollToPosition(0);
        list.scrollToPosition(0);
        List<Channel> rows = grid.channels();
        selectDefault(rows);
    }

    // ---- lifecycle --------------------------------------------------------------------------------------------

    @Override public void onViewCreated(@NonNull View view, Bundle saved) {
        super.onViewCreated(view, saved);
        back = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { closeSearch(); }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), back);
        // The categories' panel over the page: Back slides it away first.
        catsBack = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { cats.close(true); }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), catsBack);
    }

    @Override public void onStart() {
        super.onStart();
        lib.addListener(changed);
        lib.ensure();
        // The picture is ours again: still playing (turned round, or back from full screen), or the last channel paused.
        inline.start();
        // Opened (or back in the app): the channel played last plays again, unless its picture was closed with ✕. Not
        // after a turn, which keeps whatever was playing.
        autoPlay = (started || fresh) && !ui(requireContext()).getBoolean(STOPPED, false);
        started = true;
        refreshAll();
        h.postDelayed(tick, 60_000);
        if (autoFull) {
            autoFull = false;
            if (pb.live() && inline.live()) fullScreen(pb.channel(), true);
        }
    }

    @Override public void onResume() {
        super.onResume();
        watchTurns(true);
    }

    @Override public void onPause() {
        watchTurns(false);
        super.onPause();
    }

    @Override public void onStop() {
        lib.removeListener(changed);
        h.removeCallbacks(tick);
        h.removeCallbacks(runSearch);
        // Leaving the tab (or the app) stops the stream; turning round keeps it playing for the guide that comes next,
        // and full screen has already taken it over.
        inline.leave(requireActivity().isChangingConfigurations());
        super.onStop();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(S_GROUP, group);
        out.putString(S_KEY, selKey);
        out.putLong(S_START, selStart);
        out.putBoolean(S_UPRIGHT, mode == COMPACT);
        out.putBoolean(S_FOLDED, fold != null ? fold.isShut() : startFolded);
        if (grid != null && grid.timeline() != null) out.putLong(S_LEFT, grid.leftTime());
    }

    // ---- what is shown ------------------------------------------------------------------------------------------

    /** The page as the library has it now: no source, loading, an error, or the guide. */
    private void refreshAll() {
        if (getView() == null) return;
        Context c = requireContext();
        boolean source = lib.hasSource(), ready = lib.ready();
        int shown = lib.visibleCount(), hid = lib.count() - shown;
        // Short enough to fit under the name: the channels shown (the hidden ones are in the categories' menu).
        String count = getString(R.string.live_count_short, hid > 0 ? shown : lib.count());
        sub.setText(ready ? count + (lib.busy() ? " · " + getString(R.string.live_loading) : "") : "");
        showPlaylistPill(c);
        if (state != null) {
            body.removeView(state);
            state = null;
        }
        if (!source || !ready) {
            state = !source ? empty(c) : lib.busy() || lib.status().isEmpty() ? loading(c) : failed(c);
            body.addView(state, new FrameLayout.LayoutParams(-1, -1));
            centre(state);
            grid.setVisibility(View.GONE);
            list.setVisibility(View.GONE);
            results.setVisibility(View.GONE);
            favHint.setVisibility(View.GONE);
            sport.setVisibility(View.GONE);
            stripRow.setVisibility(mode == SIDE ? View.INVISIBLE : View.GONE);
            guideBar.setVisibility(View.GONE);
            if (cats.isOpen() && !dock) cats.close(false);
            layDock(false);
            nowButton.setVisibility(View.GONE);
            details.setVisibility(View.GONE);
            if (band != null) band.setVisibility(View.GONE);
            if (side != null) side.setVisibility(View.GONE);
            if (mode == COMPACT && !inline.live()) inline.setVisibility(View.GONE);
            shownGroups = null;
            return;
        }
        inline.rebind();
        boolean again = autoPlay;
        autoPlay = false;
        if (inline.channel() == null || again && !inline.live()) {
            // The channel played last: playing again as the tab opens (LastWatch), else shown paused until tapped.
            Channel last = pb.last(lib);
            if (last != null && !lib.isHidden(last)) {
                if (again && !pb.live()) watchHere(last);
                else if (inline.channel() == null) inline.show(last);
            }
        }
        List<Channel> favs = lib.list(Library.FAVOURITES, "");
        boolean hasRecent = !Picks.recent(c).isEmpty();
        // The category kept from last time, while the playlist still has it.
        if (group != null && !LastWatch.known(group, lib.groups())) group = null;
        if (group == null) group = favs.isEmpty() ? Library.ALL : Library.FAVOURITES;
        if (shownGroups != lib.groups() || shownGroupsVersion != Groups.version) buildCats();
        else if (shownFavs != !favs.isEmpty() || shownRecent != hasRecent) refreshCats();
        shownFavs = !favs.isEmpty();
        shownRecent = hasRecent;
        setStrip(favs);
        timeline(true);
        refreshRows();
        grid.favouritesChanged();
        Channel sel = lib.byKey(selKey);
        if (sel == null || !grid.channels().contains(sel)) selectDefault(grid.channels());
        else select(sel, selStart, false);
        showInline();
        showBody();
        if (searching) searchNow();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setStrip(List<Channel> favs) {
        stripAdapter.favs = favs;
        stripAdapter.notifyDataSetChanged();
        // Upright phones with the picture open: the room goes to the guide (the Favourites chip has them).
        boolean show = !favs.isEmpty() && !searching && !(mode == COMPACT && inlineShown());
        stripRow.setVisibility(show ? View.VISIBLE : mode == SIDE ? View.INVISIBLE : View.GONE);
    }

    /** Upright phones: whether the picture is open at the top. */
    private boolean inlineShown() {
        return mode == COMPACT && (inline.live() || inlineOpen && inline.channel() != null);
    }

    private void showInline() {
        if (mode != COMPACT) return;
        boolean on = inlineShown();
        if (on == (inline.getVisibility() == View.VISIBLE)) return;
        inline.setVisibility(on ? View.VISIBLE : View.GONE);
        setStrip(stripAdapter.favs);
    }

    private void openInline(boolean open) {
        if (inlineOpen == open) return;
        inlineOpen = open;
        ui(requireContext()).edit().putBoolean("inline", open).apply();
    }

    /** The rows for the chosen group, in the grid and the list. */
    private void refreshRows() {
        if (!lib.ready() || group == null) return;
        // Sport: the guide behind it keeps every channel (for the picture's swipes and the selection).
        List<Channel> rows = lib.list(SportGuide.GROUP.equals(group) ? Library.ALL : group, "");
        grid.setChannels(rows);
        List<Object> items = new ArrayList<>(rows);
        if (rows.isEmpty() && !Library.FAVOURITES.equals(group)) items.add(R.string.live_nothing_found);
        listAdapter.set(items);
        // How many channels the category has, at the bar's right (Sport's are its games, not counted here).
        int n = rows.size();
        catsCount.setText(n == 0 || SportGuide.GROUP.equals(group) ? "" : getResources().getQuantityString(R.plurals.groups_channels, n, n));
        favHint.setVisibility(rows.isEmpty() && Library.FAVOURITES.equals(group) && !searching ? View.VISIBLE : View.GONE);
    }

    /** Which of the grid, the list and the search's results is showing, and what goes with each. */
    private void showBody() {
        if (state != null) return;
        boolean sp = sportShown();
        if (sp && sport.getVisibility() != View.VISIBLE) sport.load();
        sport.setVisibility(sp ? View.VISIBLE : View.GONE);
        grid.setVisibility(!searching && !listView && !sp ? View.VISIBLE : View.GONE);
        list.setVisibility(!searching && listView && !sp ? View.VISIBLE : View.GONE);
        results.setVisibility(searching ? View.VISIBLE : View.GONE);
        // The categories' bar over the guide, the list and Sport (not over a search); docked, the panel beside them.
        guideBar.setVisibility(searching ? View.GONE : View.VISIBLE);
        layDock(!searching);
        nowButton.setVisibility(!searching && !listView && !sp ? View.VISIBLE : View.GONE);
        viewButton.setImageResource(listView ? R.drawable.live_ic_guide : R.drawable.live_ic_list);
        String label = getString(listView ? R.string.live_view_grid : R.string.live_view_list);
        viewButton.setContentDescription(label);
        viewButton.setTooltipText(label);
        // Phones held upright: the details only over the grid (the list has its own now and next).
        details.setVisibility(searching || sp || (mode == COMPACT && listView) ? View.GONE : View.VISIBLE);
        // The pane stays while a search runs when it is playing (to watch while looking).
        boolean pane = !searching || inline.live();
        if (band != null) band.setVisibility(pane ? View.VISIBLE : View.GONE);
        if (side != null) side.setVisibility(pane ? View.VISIBLE : View.GONE);
        if (searching) favHint.setVisibility(View.GONE);
    }

    /** The grid's time across, from the guide's span; first time, at now (or where it was before turning). */
    private void timeline(boolean onlyIfChanged) {
        long now = System.currentTimeMillis();
        long[] span = lib.guideSpan();
        long from = span[1] == 0 ? now : Math.max(span[0], now - Guide.BEHIND_MS);
        long to = span[1] == 0 ? now + 3 * 3600_000L : Math.min(span[1], now + 2 * Timeline.DAY);
        float px = getResources().getDisplayMetrics().density * (tablet ? 5.5f : 3.6f);
        Timeline tl = Timeline.span(from, to, now, px, TimeZone.getDefault());
        Timeline old = grid.timeline();
        if (onlyIfChanged && old != null && old.origin == tl.origin && old.end == tl.end) return;
        grid.setTimeline(tl);
        if (old == null) {
            // Now just after the logos' column (GuideGrid.scrollToNow), or where it was left.
            long left = restoreLeft;
            restoreLeft = -1;
            if (left > 0) grid.scrollToTime(left, false);
            else grid.scrollToNow(false);
            grid.corner.setText(Ui.day(requireContext(), left > 0 ? left : now));
        }
    }

    private void backToNow(boolean animate) {
        grid.scrollToNow(animate);
        Channel sel = lib.byKey(selKey);
        if (sel != null) {
            Guide.Schedule s = lib.schedule(sel.key);
            int at = s == null ? -1 : s.at(System.currentTimeMillis());
            select(sel, at < 0 ? 0 : s.start[at], false);
        }
    }

    private void pickDay(View anchor) {
        Timeline tl = grid.timeline();
        if (tl == null) return;
        Context c = requireContext();
        List<Long> days = tl.days(ZoneId.systemDefault());
        PopupMenu m = new PopupMenu(c, anchor);
        for (int i = 0; i < days.size(); i++) m.getMenu().add(0, i, i, Ui.day(c, days.get(i)));
        m.setOnMenuItemClickListener(item -> {
            long now = System.currentTimeMillis();
            long t = tl.dayTarget(days.get(item.getItemId()), now);
            if (t == now) grid.scrollToNow(true);
            else grid.scrollToTime(t, true);
            return true;
        });
        m.show();
    }

    // ---- choosing, watching, favourites ----------------------------------------------------------------------

    private GuideGrid.Listener gridListener() {
        return new GuideGrid.Listener() {
            @Override public void onProgramme(Channel ch, int index) {
                Guide.Schedule s = lib.schedule(ch.key);
                long now = System.currentTimeMillis();
                if (s == null || s.start.length == 0) {
                    // No guide: the channel itself is what's on.
                    select(ch, 0, true);
                    play(ch);
                } else if (index < 0) {
                    select(ch, 0, true);
                } else if (s.start[index] <= now && now < s.stop[index]) {
                    select(ch, s.start[index], true);
                    play(ch);
                } else {
                    select(ch, s.start[index], true);
                    // The details are folded away: the programme's sheet (Remind me, Watch) in their place.
                    if (folded()) Sheets.programme(requireContext(), lib, ch, index, actions());
                }
            }

            @Override public void onChannel(Channel ch) {
                selectNow(ch);
                play(ch);
            }

            @Override public void onFavourite(Channel ch) { favourite(ch); }

            @Override public void onMenu(Channel ch) { menu(ch); }

            @Override public void onDay(long leftTime) {
                if (isAdded()) grid.corner.setText(Ui.day(requireContext(), leftTime));
            }
        };
    }

    private Details.Actions actions() {
        return new Details.Actions() {
            @Override public void watch(Channel ch) { play(ch); }

            @Override public void reminded(Channel ch, long start, boolean on) {
                if (details != null) details.refresh();
            }

            @Override public void favourite(Channel ch) { LiveTvFragment.this.favourite(ch); }

            @Override public void more(Channel ch, int index) {
                Sheets.programme(requireContext(), lib, ch, index, this);
            }
        };
    }

    /** The first channel's programme on now, when nothing (or something no longer listed) was chosen. */
    private void selectDefault(List<Channel> rows) {
        if (rows.isEmpty()) {
            selKey = null;
            selStart = 0;
            grid.select(null, 0);
            details.bind(null, -1);
            return;
        }
        // What plays in the guide's picture, when it is in the list; otherwise the first channel.
        Channel ch = rows.get(0);
        Channel shown = inline.live() ? inline.channel() : null;
        if (shown != null) for (Channel r : rows) if (r.key.equals(shown.key)) ch = r;
        Guide.Schedule s = lib.schedule(ch.key);
        int at = s == null ? -1 : s.at(System.currentTimeMillis());
        select(ch, at < 0 ? 0 : s.start[at], false);
    }

    private void select(Channel ch, long start, boolean touched) {
        selKey = ch.key;
        selStart = start;
        grid.select(ch.key, start);
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s == null || start == 0 ? -1 : s.at(start);
        details.bind(ch, i);
        // Stopped, the pane follows the choice (play to watch it), except to keep the last channel played on offer.
        if (mode != COMPACT && !inline.live()) {
            Channel shown = inline.channel(), last = pb.last(lib);
            if (shown == null || last == null || !last.key.equals(shown.key)) inline.show(ch);
        }
        if (touched && mode != COMPACT) listAdapter.notifyItemRangeChanged(0, listAdapter.getItemCount());
    }

    /** The channel with what is on it now. */
    private void selectNow(Channel ch) {
        Guide.Schedule s = lib.schedule(ch.key);
        int at = s == null ? -1 : s.at(System.currentTimeMillis());
        select(ch, at < 0 ? 0 : s.start[at], true);
    }

    /**
     * Watch a channel: in the guide's own picture (tapped again while it plays there: full screen); a phone on its
     * side goes straight to full screen.
     */
    private void play(Channel ch) {
        if (ch == null) return;
        if (com.gathin.tvhomephone.cast.Casting.active()) {
            // Casting: the channel goes to the TV or device instead.
            com.gathin.tvhomephone.cast.Casting.change(requireContext(), CastSheet.what(requireContext(), ch));
            return;
        }
        if (mode == SIDE && !tablet) {
            fullScreen(ch, false);
            return;
        }
        if (inline.playing(ch)) {
            fullScreen(ch, false);
            return;
        }
        watchHere(ch);
    }

    /** What Cast sends first: the channel playing in the guide's picture, else the one chosen (null: none yet). */
    private Channel castChannel() {
        if (pb.live() && pb.channel() != null) return pb.channel();
        return selKey == null ? null : lib.byKey(selKey);
    }

    private void watchHere(Channel ch) {
        stopped(false);
        if (mode == COMPACT) openInline(true);
        inline.play(ch);
        showInline();
        // Folded away: it opens again to show the picture asked for.
        if (fold != null) fold.setShut(false, true);
    }

    /**
     * Full screen on this channel; the guide gets the stream back after. auto: opened by turning the phone on its side,
     * so it opens on its side; otherwise, from a phone held upright, it opens upright.
     */
    private void fullScreen(Channel ch, boolean auto) {
        if (ch == null || !isAdded()) return;
        stopped(false);
        PlayerActivity.start(requireContext(), ch.key, searching || group == null || SportGuide.GROUP.equals(group) ? Library.ALL : group,
                true, auto, !auto && mode == COMPACT && !tablet);
    }

    // ---- turning the phone on its side ----------------------------------------------------------------------------

    /**
     * With the phone's rotation locked the page doesn't turn round, so turning the phone on its side while a channel
     * plays in the guide's picture is watched for here (Turn) and goes full screen on its side, as it does when the
     * page turns round with rotation on.
     */
    private void watchTurns(boolean on) {
        if (!on || tablet) {
            if (turner != null) turner.disable();
            return;
        }
        Context c = requireContext();
        if (turner == null) {
            turner = new android.view.OrientationEventListener(c, android.hardware.SensorManager.SENSOR_DELAY_UI) {
                @Override public void onOrientationChanged(int angle) {
                    if (turn.feed(angle, android.os.SystemClock.elapsedRealtime()) != Turn.SIDEWAYS) return;
                    if (!isResumed() || mode != COMPACT || rotationOn()) return;
                    if (pb.live() && inline.live() && !pb.paused()) fullScreen(inline.channel(), true);
                }
            };
        }
        if (turner.canDetectOrientation()) turner.enable();
    }

    /** Whether the phone turns the screen round by itself (its auto-rotate is on). */
    private boolean rotationOn() {
        try {
            return android.provider.Settings.System.getInt(requireContext().getContentResolver(),
                    android.provider.Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private Inline.Listener inlineListener() {
        return new Inline.Listener() {
            @Override public void onFullScreen() { fullScreen(inline.channel(), false); }

            @Override public void onStep(int by) { stepInline(by); }

            @Override public void onClose() {
                inline.halt();
                stopped(true);
                if (mode == COMPACT) {
                    openInline(false);
                    showInline();
                }
            }

            @Override public void onShowing(Channel ch) {
                if (lib.ready() && grid.channels().contains(ch)) selectNow(ch);
            }
        };
    }

    /** Swipe on the guide's picture: the next (or previous) channel in the list showing, hidden ones passed over. */
    private void stepInline(int by) {
        Channel at = inline.channel();
        if (at == null || !lib.ready()) return;
        List<Channel> rows = searching || group == null ? lib.list(Library.ALL, "") : grid.channels();
        int i = indexOf(rows, at);
        if (i < 0) {
            rows = lib.list(Library.ALL, "");
            i = indexOf(rows, at);
        }
        List<Channel> list = rows;
        int j = Hidden.step(list.size(), i, by, k -> lib.isHidden(list.get(k)));
        if (j < 0 || j == i) return;
        Channel next = list.get(j);
        watchHere(next);
        int row = grid.channels().indexOf(next);
        if (row >= 0) {
            selectNow(next);
            grid.rows.scrollToPosition(row);
        }
    }

    private static int indexOf(List<Channel> rows, Channel ch) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).key.equals(ch.key)) return i;
        return -1;
    }

    private void favourite(Channel ch) {
        Context c = requireContext();
        boolean on = Picks.toggle(c, ch.key);
        Toast.makeText(c, getString(on ? R.string.live_fav_added : R.string.live_fav_removed, ch.name), Toast.LENGTH_SHORT).show();
        grid.favouritesChanged();
        details.refresh();
        listAdapter.notifyItemRangeChanged(0, listAdapter.getItemCount());
        resultsAdapter.notifyItemRangeChanged(0, resultsAdapter.getItemCount());
    }

    // ---- a channel's menu (a long press) --------------------------------------------------------------------

    private void menu(Channel ch) {
        if (ch == null || !isAdded()) return;
        Context c = requireContext();
        Sheets.channel(c, lib, ch, new Sheets.ChannelMenu() {
            @Override public void favourite(Channel x) { LiveTvFragment.this.favourite(x); }

            @Override public void hide(Channel x) { LiveTvFragment.this.hide(x); }

            @Override public void similar(Channel x) {
                Sheets.similar(c, lib, x, LiveTvFragment.this::play);
            }

            @Override public void info(Channel x) {
                Guide.Schedule s = lib.schedule(x.key);
                int at = s == null ? -1 : s.at(System.currentTimeMillis());
                Sheets.programme(c, lib, x, at, actions());
            }
        });
    }

    /** Hidden on this phone (Undo brings it back; More, Hidden channels lists them all). */
    private void hide(Channel ch) {
        Context c = requireContext();
        String key = Hidden.key(ch);
        Hidden.hide(c, ch);
        View v = getView();
        if (v == null) return;
        Snackbar.make(v, getString(R.string.live_hidden_one, ch.name), Snackbar.LENGTH_LONG)
                .setAction(R.string.live_undo, x -> Hidden.unhide(c, key))
                .show();
    }

    // ---- search -------------------------------------------------------------------------------------------------

    private void openSearch() {
        if (!lib.ready()) return;
        searching = true;
        back.setEnabled(true);
        toolbar.setVisibility(View.GONE);
        searchBar.setVisibility(View.VISIBLE);
        stripRow.setVisibility(View.GONE);
        search.requestFocus();
        keyboard(true);
        showBody();
        searchNow();
    }

    private void closeSearch() {
        searching = false;
        back.setEnabled(false);
        keyboard(false);
        search.setText("");
        searchBar.setVisibility(View.GONE);
        toolbar.setVisibility(View.VISIBLE);
        refreshAll();
    }

    private void keyboard(boolean show) {
        InputMethodManager im = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im == null) return;
        if (show) search.post(() -> im.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT));
        else im.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    /** Channels by name and programmes by title or description, worked out off the main thread; the newest wins. */
    private void searchNow() {
        if (!searching) return;
        String q = query.trim();
        int seq = ++searchSeq;
        if (q.isEmpty()) {
            List<Object> items = new ArrayList<>();
            items.add(R.string.live_search_start);
            resultsAdapter.set(items);
            return;
        }
        long now = System.currentTimeMillis();
        App.io.execute(() -> {
            List<Channel> chans = lib.list(Library.ALL, q);
            List<ProgrammeSearch.Hit> progs = lib.searchProgrammes(q, now, SEARCH_PROGRAMMES);
            App.main.post(() -> {
                if (seq != searchSeq || !searching || getView() == null) return;
                List<Object> items = new ArrayList<>();
                if (!chans.isEmpty()) {
                    items.add(getString(R.string.live_search_channels, chans.size()));
                    items.addAll(chans.size() > SEARCH_CHANNELS ? chans.subList(0, SEARCH_CHANNELS) : chans);
                }
                if (!progs.isEmpty()) {
                    items.add(getString(R.string.live_search_programmes));
                    items.addAll(progs);
                }
                if (items.isEmpty()) items.add(R.string.live_search_none);
                resultsAdapter.set(items);
                results.scrollToPosition(0);
            });
        });
    }

    // ---- the list (Now and Next) and the search's results -------------------------------------------------------

    private final class Rows extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<Object> items = Collections.emptyList();

        @SuppressLint("NotifyDataSetChanged")
        void set(List<Object> list) {
            items = list;
            notifyDataSetChanged();
        }

        @Override public int getItemCount() { return items.size(); }

        @Override public int getItemViewType(int position) {
            Object o = items.get(position);
            return o instanceof Channel ? 1 : o instanceof ProgrammeSearch.Hit ? 2 : o instanceof Integer ? 3 : 0;
        }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            if (type == 1) return new ChannelRow(c);
            if (type == 2) return new ProgrammeRow(c);
            TextView t = Ui.text(c, "", type == 3 ? 15 : 12, type == 3 ? Ui.SOFT : Ui.G_SELECT, true);
            if (type == 3) {
                t.setGravity(Gravity.CENTER);
                t.setPadding(Ui.dp(c, 24), Ui.dp(c, 32), Ui.dp(c, 24), 0);
            } else {
                t.setLetterSpacing(0.12f);
                t.setPadding(Ui.dp(c, 4), Ui.dp(c, 16), 0, Ui.dp(c, 6));
            }
            t.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(t) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object o = items.get(position);
            if (holder instanceof ChannelRow) ((ChannelRow) holder).bind((Channel) o);
            else if (holder instanceof ProgrammeRow) ((ProgrammeRow) holder).bind((ProgrammeSearch.Hit) o);
            else if (o instanceof Integer) ((TextView) holder.itemView).setText((Integer) o);
            else ((TextView) holder.itemView).setText((String) o);
        }
    }

    /** A channel with its logo, the guide's now (with how far in) and next, and the star. */
    private final class ChannelRow extends RecyclerView.ViewHolder {
        final LinearLayout view;
        final TextView initials, name, nowTag, nowTitle, next, uhd;
        final ImageView logo;
        final ProgressBar progress;
        final ImageButton star;
        Channel ch;

        ChannelRow(Context c) {
            super(new LinearLayout(c));
            view = (LinearLayout) itemView;
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10));
            view.setMinimumHeight(Ui.dp(c, 76));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.dp(c, 6);
            view.setLayoutParams(lp);
            FrameLayout box = new FrameLayout(c);
            box.setBackground(Ui.round(c, Ui.GLASS, 12));
            initials = Ui.text(c, "", 14, Ui.SOFT, true);
            initials.setGravity(Gravity.CENTER);
            box.addView(initials, new FrameLayout.LayoutParams(-1, -1));
            logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = Ui.dp(c, 5);
            logo.setPadding(p, p, p, p);
            box.addView(logo, new FrameLayout.LayoutParams(-1, -1));
            // A 4K/UHD channel's mark, at the logo's foot.
            uhd = Ui.uhdBadge(c);
            FrameLayout.LayoutParams ul = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            ul.setMargins(0, 0, Ui.dp(c, 2), Ui.dp(c, 2));
            box.addView(uhd, ul);
            view.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)));
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 6), 0);
            name = Ui.text(c, "", 16, Ui.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            LinearLayout now = new LinearLayout(c);
            now.setGravity(Gravity.CENTER_VERTICAL);
            nowTag = Ui.text(c, getString(R.string.live_now), 11, Ui.RED, true);
            nowTag.setPadding(0, 0, Ui.dp(c, 6), 0);
            now.addView(nowTag);
            nowTitle = Ui.text(c, "", 14, Ui.SOFT, false);
            nowTitle.setSingleLine(true);
            nowTitle.setEllipsize(TextUtils.TruncateAt.END);
            now.addView(nowTitle, new LinearLayout.LayoutParams(0, -2, 1f));
            texts.addView(now);
            progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            progress.setProgressTintList(ColorStateList.valueOf(Ui.RED));
            progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 3));
            pp.topMargin = Ui.dp(c, 4);
            pp.bottomMargin = Ui.dp(c, 3);
            texts.addView(progress, pp);
            next = Ui.text(c, "", 12, Ui.DIM, false);
            next.setSingleLine(true);
            next.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(next);
            view.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
            star = Ui.icon(c, R.drawable.live_ic_star_outline, R.string.live_favourite);
            view.addView(star, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
            view.setOnClickListener(v -> {
                if (ch == null) return;
                if (!searching) selectNow(ch);
                play(ch);
            });
            view.setOnLongClickListener(v -> {
                if (ch != null) menu(ch);
                return true;
            });
            star.setOnClickListener(v -> {
                if (ch != null) favourite(ch);
            });
        }

        void bind(Channel c) {
            ch = c;
            Context ctx = view.getContext();
            view.setBackground(Ui.touch(ctx, mode != COMPACT && !searching && c.key.equals(selKey), 0x14FFFFFF, 14));
            int n = lib.number(c);
            name.setText(n > 0 ? n + "  " + c.name : c.name);
            initials.setText(Ui.initials(c.name));
            // Its playlist's logo, else Sky's for its name (a 4K channel with none of its own shows its channel's).
            String pic = Ui.logo(c);
            if (pic == null) {
                Glide.with(logo).clear(logo);
                initials.setVisibility(View.VISIBLE);
            } else {
                initials.setVisibility(View.INVISIBLE);
                Glide.with(logo).load(pic).into(logo);
            }
            uhd.setVisibility(Names.uhd(c.name) ? View.VISIBLE : View.GONE);
            boolean fav = Picks.isFavourite(ctx, c.key);
            star.setImageResource(fav ? R.drawable.live_ic_star : R.drawable.live_ic_star_outline);
            star.setImageTintList(ColorStateList.valueOf(fav ? Ui.STAR : 0x80FFFFFF));
            star.setContentDescription(getString(fav ? R.string.live_fav_remove : R.string.live_fav_add));
            Guide.Schedule s = lib.schedule(c.key);
            long now = System.currentTimeMillis();
            int at = s == null ? -1 : s.at(now), nx = s == null ? -1 : s.after(now);
            if (at >= 0) {
                nowTag.setVisibility(View.VISIBLE);
                nowTitle.setText(getString(R.string.live_until, s.title[at], Ui.clock(s.stop[at])));
                progress.setVisibility(View.VISIBLE);
                progress.setProgress((int) (1000 * (now - s.start[at]) / Math.max(1, s.stop[at] - s.start[at])));
            } else {
                nowTag.setVisibility(View.GONE);
                nowTitle.setText(c.group);
                progress.setVisibility(View.GONE);
            }
            if (nx >= 0) {
                next.setVisibility(View.VISIBLE);
                next.setText(getString(R.string.live_next_at, Ui.clock(s.start[nx]), s.title[nx]));
            } else next.setVisibility(View.GONE);
        }
    }

    /** A programme the search found: its title, the channel, the day and time, ON NOW when it is on. */
    private final class ProgrammeRow extends RecyclerView.ViewHolder {
        final TextView title, meta, badge, initials;
        final ImageView logo;
        ProgrammeSearch.Hit hit;

        ProgrammeRow(Context c) {
            super(new LinearLayout(c));
            LinearLayout view = (LinearLayout) itemView;
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
            view.setMinimumHeight(Ui.dp(c, 64));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.dp(c, 6);
            view.setLayoutParams(lp);
            view.setBackground(Ui.touch(c, false, 0x14FFFFFF, 14));
            FrameLayout box = new FrameLayout(c);
            box.setBackground(Ui.round(c, Ui.GLASS, 10));
            initials = Ui.text(c, "", 12, Ui.SOFT, true);
            initials.setGravity(Gravity.CENTER);
            box.addView(initials, new FrameLayout.LayoutParams(-1, -1));
            logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = Ui.dp(c, 4);
            logo.setPadding(p, p, p, p);
            box.addView(logo, new FrameLayout.LayoutParams(-1, -1));
            view.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 40)));
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
            title = Ui.text(c, "", 15, Ui.TEXT, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);
            meta = Ui.text(c, "", 13, Ui.G_TIME, false);
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(meta);
            view.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
            badge = Ui.text(c, getString(R.string.live_on_now), 11, Ui.TEXT, true);
            badge.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
            badge.setBackground(Ui.round(c, Ui.RED, 10));
            badge.setLetterSpacing(0.06f);
            view.addView(badge);
            view.setOnClickListener(v -> {
                if (hit == null) return;
                long now = System.currentTimeMillis();
                Guide.Schedule s = hit.schedule;
                int i = hit.programme;
                if (s.start[i] <= now && now < s.stop[i]) play(hit.channel);
                else Sheets.programme(requireContext(), lib, hit.channel, i, actions());
            });
            view.setOnLongClickListener(v -> {
                if (hit != null) menu(hit.channel);
                return true;
            });
        }

        void bind(ProgrammeSearch.Hit x) {
            hit = x;
            Context c = itemView.getContext();
            Channel ch = x.channel;
            Guide.Schedule s = x.schedule;
            int i = x.programme;
            long now = System.currentTimeMillis();
            boolean on = s.start[i] <= now && now < s.stop[i];
            title.setText(x.title().isEmpty() ? ch.name : x.title());
            meta.setText(getString(R.string.live_search_meta, ch.name, Ui.day(c, s.start[i]), Ui.range(s.start[i], s.stop[i])));
            badge.setVisibility(on ? View.VISIBLE : View.GONE);
            initials.setText(ch.logo.isEmpty() ? Ui.initials(ch.name) : "");
            if (ch.logo.isEmpty()) Glide.with(logo).clear(logo);
            else Glide.with(logo).load(ch.logo).into(logo);
        }
    }

    // ---- states --------------------------------------------------------------------------------------------

    private View favouritesHint(Context c) {
        LinearLayout v = centred(c);
        ImageView icon = new ImageView(c);
        icon.setImageResource(R.drawable.live_ic_star_outline);
        icon.setImageTintList(ColorStateList.valueOf(Ui.STAR));
        v.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        TextView t = Ui.text(c, getString(R.string.live_fav_empty), 15, Ui.SOFT, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(c, 12), 0, 0);
        v.addView(t);
        MaterialButton all = new MaterialButton(c);
        all.setText(R.string.live_show_all);
        all.setAllCaps(false);
        all.setTextColor(Ui.G_INK);
        all.setBackgroundTintList(ColorStateList.valueOf(Ui.G_SELECT));
        all.setCornerRadius(Ui.dp(c, 24));
        all.setOnClickListener(x -> pickGroup(Library.ALL));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 52));
        gp.topMargin = Ui.dp(c, 16);
        v.addView(all, gp);
        v.setBackgroundColor(0x00000000);
        v.setClickable(true);
        return v;
    }

    private View empty(Context c) {
        LinearLayout v = centred(c);
        ImageView icon = new ImageView(c);
        icon.setImageResource(R.drawable.live_ic_guide);
        icon.setImageTintList(ColorStateList.valueOf(Ui.SOFT));
        v.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64)));
        TextView t = Ui.text(c, getString(R.string.live_empty_title), 20, Ui.TEXT, true);
        t.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 8));
        v.addView(t);
        TextView b = Ui.text(c, getString(R.string.live_empty_body), 15, Ui.SOFT, false);
        b.setGravity(Gravity.CENTER);
        v.addView(b);
        MaterialButton go = new MaterialButton(c);
        go.setText(R.string.live_empty_button);
        go.setCornerRadius(Ui.dp(c, 24));
        go.setOnClickListener(x -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).open(R.id.tab_settings);
        });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 52));
        gp.topMargin = Ui.dp(c, 20);
        v.addView(go, gp);
        return v;
    }

    private View loading(Context c) {
        LinearLayout v = centred(c);
        ProgressBar p = new ProgressBar(c);
        p.setIndeterminateTintList(ColorStateList.valueOf(Ui.TEXT));
        v.addView(p);
        TextView t = Ui.text(c, getString(R.string.live_loading), 15, Ui.SOFT, false);
        t.setPadding(0, Ui.dp(c, 14), 0, 0);
        v.addView(t);
        return v;
    }

    private View failed(Context c) {
        LinearLayout v = centred(c);
        TextView t = Ui.text(c, lib.status(), 16, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        v.addView(t);
        MaterialButton again = new MaterialButton(c);
        again.setText(R.string.live_try_again);
        again.setCornerRadius(Ui.dp(c, 24));
        again.setOnClickListener(x -> lib.refresh(null));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 52));
        gp.topMargin = Ui.dp(c, 16);
        v.addView(again, gp);
        return v;
    }

    private static LinearLayout centred(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        v.setPadding(Ui.dp(c, 32), 0, Ui.dp(c, 32), Ui.dp(c, 48));
        return v;
    }
}
