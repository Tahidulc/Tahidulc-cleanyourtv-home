package com.gathin.tvhomephone.watch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.MainActivity;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.sync.Tvs;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.util.List;

/**
 * Watch: the TVs' three pages, Discover, Movies and TV shows, as tabs along the top, each page's own lists as chips
 * under them, and the list's posters in a grid (three across on a phone, more on a tablet). Search at the top.
 * Pull down to sync with the TVs and read the list again.
 */
public class WatchFragment extends Fragment {
    private static int section, chosen;
    private TabLayout sections;
    private ChipGroup chips;
    private HorizontalScrollView chipScroll;
    private RecyclerView grid;
    private PosterAdapter adapter;
    private SwipeRefreshLayout refresh;
    private TextView note;
    private EditText search;
    private FrameLayout body;
    private List<Lists.Spec> specs;
    private Lists.Spec shown;
    private int gen;
    private final Runnable onSync = this::reshow;

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        Context c = requireContext();
        LinearLayout root = Ui.column(c);
        root.setBackgroundColor(Ui.ground(c));

        search = new EditText(c);
        search.setHint("Search films and series");
        search.setHintTextColor(Ui.DIM);
        search.setTextColor(0xFFFFFFFF);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackground(Ui.glass(c));
        int p = Ui.dp(c, 14);
        search.setPadding(p, Ui.dp(c, 10), p, Ui.dp(c, 10));
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0);
        search.setCompoundDrawablePadding(Ui.dp(c, 8));
        search.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH || e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                runSearch();
                return true;
            }
            return false;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable s) {
                if (s.length() == 0 && shown != null && shown.name.equals("Search")) showList(chosen);
            }
        });
        root.addView(search, Ui.margins(Ui.fill(), c, 16, 14, 16, 4));

        sections = new TabLayout(c);
        sections.setBackgroundColor(Ui.ground(c));
        sections.setTabTextColors(Ui.DIM, 0xFFFFFFFF);
        sections.setTabMode(TabLayout.MODE_FIXED);
        for (String s : new String[]{"Discover", "Movies", "TV shows"}) sections.addTab(sections.newTab().setText(s));
        root.addView(sections, Ui.fill());

        chipScroll = new HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chips = new ChipGroup(c);
        chips.setSingleLine(true);
        chips.setSingleSelection(true);
        chips.setSelectionRequired(true);
        chips.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 2));
        chipScroll.addView(chips);
        root.addView(chipScroll, Ui.fill());

        body = new FrameLayout(c);
        refresh = new SwipeRefreshLayout(c);
        grid = new RecyclerView(c);
        grid.setClipToPadding(false);
        grid.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 24));
        float widthDp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        grid.setLayoutManager(new GridLayoutManager(c, Math.max(3, Math.min(8, (int) (widthDp / 125)))));
        adapter = new PosterAdapter((t, i) -> TitleActivity.open(requireContext(), t), this::hold, 0);
        grid.setAdapter(adapter);
        refresh.addView(grid);
        refresh.setOnRefreshListener(() -> Tvs.syncNow(requireContext(), () -> {
            if (!isAdded()) return;
            refresh.setRefreshing(false);
            reshow();
        }));
        body.addView(refresh, new FrameLayout.LayoutParams(-1, -1));
        note = Ui.text(c, "", 15, Ui.SOFT, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(Ui.dp(c, 32), 0, Ui.dp(c, 32), 0);
        body.addView(note, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        sections.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (section != tab.getPosition()) chosen = 0;
                section = tab.getPosition();
                buildChips();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        TabLayout.Tab start = sections.getTabAt(section);
        if (start != null) start.select();
        buildChips();
        return root;
    }

    @Override public void onStart() {
        super.onStart();
        Tvs.addListener(onSync);
        SyncStore.addListener(onSync);
    }

    @Override public void onStop() {
        Tvs.removeListener(onSync);
        SyncStore.removeListener(onSync);
        super.onStop();
    }

    /** After a sync (or a change here): My list, ratings and the TVs' watching may be different; the list again. */
    private void reshow() {
        if (!isAdded() || shown == null) return;
        if (shown.name.equals("Search")) return;
        String was = shown.name;
        List<Lists.Spec> now = Lists.section(requireContext(), section);
        boolean same = now.size() == specs.size();
        for (int i = 0; same && i < now.size(); i++) same = now.get(i).name.equals(specs.get(i).name);
        // A TMDB list doesn't change with a sync; the chips only when the Because tabs did.
        if (same && shown.special == null) return;
        for (int i = 0; i < now.size(); i++) if (now.get(i).name.equals(was)) chosen = i;
        if (same) load(now.get(chosen));
        else buildChips();
    }

    private void buildChips() {
        Context c = requireContext();
        int accent = Ui.accent(section);
        sections.setSelectedTabIndicatorColor(accent);
        specs = Lists.section(c, section);
        if (chosen >= specs.size()) chosen = 0;
        chips.removeAllViews();
        for (int i = 0; i < specs.size(); i++) {
            Chip chip = new Chip(c);
            chip.setText(specs.get(i).name);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColor(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{0x33FFFFFF, 0x00000000}));
            chip.setChipStrokeColor(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{0xFFFFFFFF, 0x38FFFFFF}));
            chip.setChipStrokeWidth(Ui.dp(c, 1));
            chip.setTextColor(0xFFFFFFFF);
            chip.setId(View.generateViewId());
            int at = i;
            chip.setOnClickListener(v -> showList(at));
            chips.addView(chip);
            if (i == chosen) chip.setChecked(true);
        }
        showList(chosen);
    }

    private void showList(int i) {
        if (!isAdded() || specs == null || specs.isEmpty()) return;
        chosen = Math.max(0, Math.min(i, specs.size() - 1));
        load(specs.get(chosen));
        View chip = chips.getChildAt(chosen);
        if (chip != null) chipScroll.post(() -> chipScroll.smoothScrollTo(Math.max(0, chip.getLeft() - Ui.dp(requireContext(), 24)), 0));
    }

    private void runSearch() {
        String q = search.getText().toString().trim();
        InputMethodManager im = requireContext().getSystemService(InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(search.getWindowToken(), 0);
        if (q.isEmpty()) showList(chosen);
        else load(Lists.search(q));
    }

    private void load(Lists.Spec s) {
        Context c = requireContext();
        shown = s;
        int my = ++gen;
        // What the TVs and this phone keep shows without TMDB; everything else needs its key, which a TV gives.
        boolean local = s.special != null && (s.special.equals("@mylist") || s.special.equals("@resume")
                || s.special.equals("@yourshows") || s.special.equals("@newepisodes"));
        if (!Tmdb.ready(c) && !local) {
            adapter.set(null);
            note.setVisibility(View.GONE);
            body.removeView(body.findViewWithTag("pair"));
            View card = Ui.pairCard(c, () -> {
                if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).open(R.id.tab_settings);
            });
            card.setTag("pair");
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
            lp.setMargins(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), 0);
            body.addView(card, lp);
            return;
        }
        body.removeView(body.findViewWithTag("pair"));
        note.setVisibility(View.VISIBLE);
        note.setText("Loading…");
        adapter.set(null);
        App.io.execute(() -> {
            List<Title> got = null;
            String err = null;
            try {
                got = Lists.load(c, s);
            } catch (IOException e) {
                err = e instanceof Tmdb.Failed ? e.getMessage() : "Couldn't load this list";
            } catch (RuntimeException e) {
                err = "Couldn't load this list";
            }
            List<Title> list = got;
            String why = err;
            App.main.post(() -> {
                if (!isAdded() || my != gen) return;
                adapter.set(list);
                grid.scrollToPosition(0);
                boolean empty = list == null || list.isEmpty();
                note.setVisibility(empty ? View.VISIBLE : View.GONE);
                if (empty) note.setText(why != null ? why : empty(s));
            });
        });
    }

    private static String empty(Lists.Spec s) {
        switch (s.name) {
            case "Continue watching": return "Nothing part-watched on the TVs right now.";
            case "My list": return "Nothing on My list yet. Hold a poster, or press My list on a title's page.";
            case "Your shows": case "New episodes": return "Series watched on the TVs show here once they've synced.";
            case "Coming up": return "Nothing coming in the next fortnight from your shows or My list.";
            default: return "Nothing here right now.";
        }
    }

    /** Hold on a poster: Coming up's pop-up off or on; anywhere else, on or off My list. */
    private void hold(Title t, int position) {
        Context c = requireContext();
        if (shown != null && shown.name.equals("Coming up")) {
            boolean off = ComingUp.toggleMute(c, t);
            Toast.makeText(c, (off ? "Pop-up off for " : "Pop-up on for ") + t.title + " on every device", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean in = SyncStore.get(c, "mylist", t.key()) != null;
        if (in) SyncStore.delete(c, "mylist", t.key());
        else SyncStore.put(c, "mylist", t.key(), t.listData());
        Toast.makeText(c, (in ? "Taken off My list: " : "On My list: ") + t.title, Toast.LENGTH_SHORT).show();
    }
}
