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
 * watch), then the groups as chips (Favourites first, chosen when there are any, then All, Recent and the playlist's own
 * groups). Above the grid the chosen programme's details.
 * Tap a channel (or a programme that is on) to watch it in the guide's own picture, with sound (Inline): on a phone held
 * upright a 16:9 picture at the top with the guide still there under it, on a tablet held upright beside the details,
 * and on a tablet on its side in the pane beside the grid. Tap it again, its full screen button or swipe up for full
 * screen, which takes the same stream over and gives it back; turning the phone on its side while it plays goes full
 * screen too, and upright again comes back. A phone on its side goes straight to full screen. The last channel is kept
 * for coming back to the tab (paused until tapped). A later programme shows its details and Remind me; the star makes
 * the channel a favourite; a long press opens the channel's menu (favourite, hide, similar channels, its info). The day
 * button at the grid's corner picks the day. The list view is the Now and Next list.
 */
public class LiveTvFragment extends Fragment {
    private static final String S_GROUP = "group", S_KEY = "key", S_START = "start", S_LEFT = "left", S_UPRIGHT = "upright";
    private static final int COMPACT = 0, TOP = 1, SIDE = 2;
    private static final int SEARCH_CHANNELS = 60, SEARCH_PROGRAMMES = 120;

    private final Handler h = new Handler(Looper.getMainLooper());
    private Library lib;
    private Playback pb;
    private int mode;
    private boolean tablet, listView, searching;
    /** Upright phones: the picture is open at the top; turned on its side while it played (so: full screen). */
    private boolean inlineOpen, autoFull;
    private String group;
    private String selKey;
    private long selStart, restoreLeft = -1;
    private String query = "";
    private int searchSeq;
    private List<String> shownGroups;
    private boolean shownFavs, shownRecent;

    private LinearLayout toolbar, searchBar, stripRow, band, side;
    private TextView sub;
    private EditText search;
    private MaterialButton nowButton;
    private ImageButton viewButton;
    private RecyclerView strip, chipsView, list, results;
    private final Strip stripAdapter = new Strip();
    private final Chips chipsAdapter = new Chips();
    private final Rows listAdapter = new Rows(), resultsAdapter = new Rows();
    private FrameLayout body;
    private GuideGrid grid;
    private Details details;
    private Inline inline;
    private View state, favHint;
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
        if (saved != null) {
            group = saved.getString(S_GROUP);
            selKey = saved.getString(S_KEY);
            selStart = saved.getLong(S_START);
            restoreLeft = saved.getLong(S_LEFT, -1);
            // A phone turned on its side while its picture played (no one showing it for the moment): full screen.
            autoFull = saved.getBoolean(S_UPRIGHT, false) && mode == SIDE && !tablet && pb.unattended() && pb.live();
        }

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.guideGround());

        buildToolbar(c, root);
        inline = new Inline(c, lib, mode == COMPACT);
        inline.setListener(inlineListener());
        if (mode == COMPACT) {
            // Upright phones: the picture across the top, over the guide, when something has been played.
            root.addView(inline, new LinearLayout.LayoutParams(-1, -2));
            inline.setVisibility(View.GONE);
        }
        buildStrip(c);
        if (mode == SIDE) {
            // On its side, Your channels sits in the top bar, leaving the height to the grid.
            toolbar.addView(stripRow, 1, new LinearLayout.LayoutParams(0, Ui.dp(c, 52), 1f));
            ((LinearLayout.LayoutParams) toolbar.getChildAt(0).getLayoutParams()).weight = 0;
            toolbar.getChildAt(0).getLayoutParams().width = -2;
        } else {
            root.addView(stripRow, new LinearLayout.LayoutParams(-1, Ui.dp(c, 60)));
        }

        chipsView = new RecyclerView(c);
        chipsView.setLayoutManager(new LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false));
        chipsView.setAdapter(chipsAdapter);
        chipsView.setClipToPadding(false);
        chipsView.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
        chipsView.setItemAnimator(null);
        root.addView(chipsView, new LinearLayout.LayoutParams(-1, Ui.dp(c, 52)));

        LinearLayout content = new LinearLayout(c);
        LinearLayout main = new LinearLayout(c);
        main.setOrientation(LinearLayout.VERTICAL);
        content.addView(main, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        Details.Actions actions = actions();
        if (mode == COMPACT) {
            details = new Details(c, lib, true, 2);
            main.addView(details, new LinearLayout.LayoutParams(-1, -2));
        } else if (mode == TOP) {
            band = new LinearLayout(c);
            band.setBackgroundColor(Ui.G_PANEL);
            band.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), 0, Ui.dp(c, 6));
            band.addView(inline, new LinearLayout.LayoutParams(Ui.dp(c, 320), Ui.dp(c, 180)));
            details = new Details(c, lib, false, 4);
            band.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
            main.addView(band, new LinearLayout.LayoutParams(-1, -2));
        } else {
            side = new LinearLayout(c);
            side.setOrientation(LinearLayout.VERTICAL);
            side.setBackgroundColor(Ui.G_PANEL);
            int sw = Ui.dp(c, tablet ? 380 : 300);
            int pad = Ui.dp(c, 14);
            side.setPadding(pad, pad, pad, 0);
            side.addView(inline, new LinearLayout.LayoutParams(-1, (sw - 2 * pad) * 9 / 16));
            ScrollView ds = new ScrollView(c);
            details = new Details(c, lib, false, tablet ? 8 : 5);
            details.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 16));
            ds.addView(details, new FrameLayout.LayoutParams(-1, -2));
            side.addView(ds, new LinearLayout.LayoutParams(-1, 0, 1f));
            content.addView(side, new LinearLayout.LayoutParams(sw, -1));
        }
        details.setActions(actions);

        body = new FrameLayout(c);
        main.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
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
        return root;
    }

    private static SharedPreferences ui(Context c) {
        return c.getApplicationContext().getSharedPreferences("live_ui", Context.MODE_PRIVATE);
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
        titles.addView(Ui.text(c, getString(R.string.live_title), 22, Ui.TEXT, true));
        sub = Ui.text(c, "", 12, Ui.DIM, false);
        sub.setSingleLine(true);
        titles.addView(sub);
        toolbar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        nowButton = new MaterialButton(c);
        nowButton.setText(R.string.live_now_button);
        nowButton.setAllCaps(false);
        nowButton.setIconResource(R.drawable.live_ic_now);
        nowButton.setIconSize(Ui.dp(c, 18));
        nowButton.setIconPadding(Ui.dp(c, 6));
        nowButton.setIconTint(ColorStateList.valueOf(Ui.G_INK));
        nowButton.setTextColor(Ui.G_INK);
        nowButton.setBackgroundTintList(ColorStateList.valueOf(Ui.G_SELECT));
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
        toolbar.addView(find, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        viewButton = Ui.icon(c, R.drawable.live_ic_list, R.string.live_view_list);
        viewButton.setOnClickListener(v -> {
            listView = !listView;
            ui(c).edit().putBoolean("list", listView).apply();
            showBody();
            refreshRows();
        });
        toolbar.addView(viewButton, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        ImageButton more = Ui.icon(c, R.drawable.live_ic_more_vert, R.string.live_more);
        more.setOnClickListener(this::moreMenu);
        toolbar.addView(more, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
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

    private void moreMenu(View anchor) {
        Context c = requireContext();
        PopupMenu m = new PopupMenu(c, anchor);
        Menu menu = m.getMenu();
        menu.add(0, 1, 0, R.string.live_manage_favourites);
        int hidden = Hidden.keys(c).size();
        menu.add(0, 4, 1, hidden > 0 ? getString(R.string.live_hidden_menu_count, hidden) : getString(R.string.live_hidden_title));
        menu.add(0, 2, 2, listView ? R.string.live_view_grid : R.string.live_view_list);
        menu.add(0, 3, 3, R.string.live_refresh);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) Sheets.favourites(c, lib);
            else if (item.getItemId() == 4) Sheets.hidden(c, lib);
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
            label.setWidth(Ui.dp(c, 62));
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

    private final class Chips extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final List<String[]> items = new ArrayList<>();

        @Override public int getItemCount() { return items.size(); }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            TextView t = Ui.guideChip(c, "", false);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-2, -1);
            lp.rightMargin = Ui.dp(c, 8);
            t.setLayoutParams(lp);
            return new RecyclerView.ViewHolder(t) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            String[] it = items.get(position);
            TextView t = (TextView) holder.itemView;
            t.setText(it[0]);
            Ui.styleChip(t.getContext(), t, it[1].equals(group));
            t.setOnClickListener(v -> pickGroup(it[1]));
        }
    }

    private void buildChips() {
        chipsAdapter.items.clear();
        chipsAdapter.items.add(new String[]{getString(R.string.live_favourites), Library.FAVOURITES});
        chipsAdapter.items.add(new String[]{getString(R.string.live_all), Library.ALL});
        chipsAdapter.items.add(new String[]{getString(R.string.live_recent), Library.RECENT});
        for (String g : lib.groups()) chipsAdapter.items.add(new String[]{g, g});
        shownGroups = lib.groups();
        refreshChips();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void refreshChips() {
        chipsAdapter.notifyDataSetChanged();
    }

    private void pickGroup(String g) {
        if (g.equals(group)) return;
        group = g;
        refreshChips();
        refreshRows();
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
    }

    @Override public void onStart() {
        super.onStart();
        lib.addListener(changed);
        lib.ensure();
        // The picture is ours again: still playing (turned round, or back from full screen), or the last channel paused.
        inline.start();
        refreshAll();
        h.postDelayed(tick, 60_000);
        if (autoFull) {
            autoFull = false;
            if (pb.live() && inline.live()) fullScreen(pb.channel(), true);
        }
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
        if (grid != null && grid.timeline() != null) out.putLong(S_LEFT, grid.leftTime());
    }

    // ---- what is shown ------------------------------------------------------------------------------------------

    /** The page as the library has it now: no source, loading, an error, or the guide. */
    private void refreshAll() {
        if (getView() == null) return;
        Context c = requireContext();
        boolean source = lib.hasSource(), ready = lib.ready();
        int shown = lib.visibleCount(), hid = lib.count() - shown;
        String count = hid > 0 ? getString(R.string.live_count_hidden, shown, hid) : getString(R.string.live_count, lib.count());
        sub.setText(ready ? count + (lib.busy() ? " · " + getString(R.string.live_loading) : "") : "");
        if (state != null) {
            body.removeView(state);
            state = null;
        }
        if (!source || !ready) {
            state = !source ? empty(c) : lib.busy() || lib.status().isEmpty() ? loading(c) : failed(c);
            body.addView(state, new FrameLayout.LayoutParams(-1, -1));
            grid.setVisibility(View.GONE);
            list.setVisibility(View.GONE);
            results.setVisibility(View.GONE);
            favHint.setVisibility(View.GONE);
            stripRow.setVisibility(mode == SIDE ? View.INVISIBLE : View.GONE);
            chipsView.setVisibility(View.GONE);
            nowButton.setVisibility(View.GONE);
            details.setVisibility(View.GONE);
            if (band != null) band.setVisibility(View.GONE);
            if (side != null) side.setVisibility(View.GONE);
            if (mode == COMPACT && !inline.live()) inline.setVisibility(View.GONE);
            shownGroups = null;
            return;
        }
        inline.rebind();
        if (inline.channel() == null) {
            // The channel played last (paused until tapped).
            Channel last = pb.last(lib);
            if (last != null && !lib.isHidden(last)) inline.show(last);
        }
        List<Channel> favs = lib.list(Library.FAVOURITES, "");
        boolean hasRecent = !Picks.recent(c).isEmpty();
        if (group == null) group = favs.isEmpty() ? Library.ALL : Library.FAVOURITES;
        if (shownGroups != lib.groups()) buildChips();
        else if (shownFavs != !favs.isEmpty() || shownRecent != hasRecent) refreshChips();
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
        List<Channel> rows = lib.list(group, "");
        grid.setChannels(rows);
        List<Object> items = new ArrayList<>(rows);
        if (rows.isEmpty() && !Library.FAVOURITES.equals(group)) items.add(R.string.live_nothing_found);
        listAdapter.set(items);
        favHint.setVisibility(rows.isEmpty() && Library.FAVOURITES.equals(group) && !searching ? View.VISIBLE : View.GONE);
    }

    /** Which of the grid, the list and the search's results is showing, and what goes with each. */
    private void showBody() {
        if (state != null) return;
        grid.setVisibility(!searching && !listView ? View.VISIBLE : View.GONE);
        list.setVisibility(!searching && listView ? View.VISIBLE : View.GONE);
        results.setVisibility(searching ? View.VISIBLE : View.GONE);
        chipsView.setVisibility(searching ? View.GONE : View.VISIBLE);
        nowButton.setVisibility(!searching && !listView ? View.VISIBLE : View.GONE);
        viewButton.setImageResource(listView ? R.drawable.live_ic_guide : R.drawable.live_ic_list);
        String label = getString(listView ? R.string.live_view_grid : R.string.live_view_list);
        viewButton.setContentDescription(label);
        viewButton.setTooltipText(label);
        // Phones held upright: the details only over the grid (the list has its own now and next).
        details.setVisibility(searching || (mode == COMPACT && listView) ? View.GONE : View.VISIBLE);
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
            long left = restoreLeft > 0 ? restoreLeft : Timeline.floorHalfHour(now, TimeZone.getDefault());
            restoreLeft = -1;
            grid.scrollToTime(left, false);
            grid.corner.setText(Ui.day(requireContext(), left));
        }
    }

    private void backToNow(boolean animate) {
        grid.scrollToTime(Timeline.floorHalfHour(System.currentTimeMillis(), TimeZone.getDefault()), animate);
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
            if (t == now) t = Timeline.floorHalfHour(now, TimeZone.getDefault());
            grid.scrollToTime(t, true);
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

    private void watchHere(Channel ch) {
        if (mode == COMPACT) openInline(true);
        inline.play(ch);
        showInline();
    }

    /** Full screen on this channel; the guide gets the stream back after (auto: opened by turning on its side). */
    private void fullScreen(Channel ch, boolean auto) {
        if (ch == null || !isAdded()) return;
        PlayerActivity.start(requireContext(), ch.key, searching || group == null ? Library.ALL : group, true, auto);
    }

    private Inline.Listener inlineListener() {
        return new Inline.Listener() {
            @Override public void onFullScreen() { fullScreen(inline.channel(), false); }

            @Override public void onStep(int by) { stepInline(by); }

            @Override public void onClose() {
                inline.halt();
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
