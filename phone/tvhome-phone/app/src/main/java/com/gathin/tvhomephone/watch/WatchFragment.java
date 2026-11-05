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
import com.gathin.tvhomephone.live.VodHistory;
import com.gathin.tvhomephone.sync.Tvs;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.util.List;

/**
 * Watch: the TVs' three pages, Discover, Movies and TV shows, as tabs along the top, each page's own lists as chips
 * under them, and the list's posters in a grid (three across on a phone, more on a tablet). Search at the top.
 * Pull down to sync with the TVs and read the list again. Above the grid, what was left part-way from the family's own
 * playlist (VodHistory): tap to carry on, hold to take it off (films on Movies, episodes on TV shows, both on Discover).
 */
public class WatchFragment extends Fragment {
    private static int section, chosen;
    private TabLayout sections;
    private ChipGroup chips;
    private HorizontalScrollView chipScroll;
    private LinearLayout resumeBox, resumeRow;
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
                if (s.length() == 0 && shown != null && shown.name.equals("Search")) {
                    showList(chosen);
                    showResume();
                }
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

        resumeBox = Ui.column(c);
        TextView resumeHead = Ui.text(c, getString(R.string.f1_resume_title), 13, Ui.SOFT, true);
        resumeHead.setLetterSpacing(0.04f);
        resumeBox.addView(resumeHead, Ui.margins(Ui.wrap(), c, 16, 8, 16, 6));
        HorizontalScrollView resumeScroll = new HorizontalScrollView(c);
        resumeScroll.setHorizontalScrollBarEnabled(false);
        resumeRow = Ui.row(c);
        resumeRow.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
        resumeScroll.addView(resumeRow);
        resumeBox.addView(resumeScroll, Ui.fill());
        resumeBox.setVisibility(View.GONE);
        root.addView(resumeBox, Ui.fill());

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
        showResume();
    }

    /** What was left part-way from the playlist, for this section (none while a search shows). */
    private void showResume() {
        if (!isAdded() || resumeRow == null) return;
        Context c = requireContext();
        List<VodHistory.Entry> all = VodHistory.list(c);
        resumeRow.removeAllViews();
        boolean searching = shown != null && shown.name.equals("Search");
        for (VodHistory.Entry e : all) {
            if (section == 1 && e.tv || section == 2 && !e.tv) continue;
            resumeRow.addView(resumeCard(c, e));
        }
        resumeBox.setVisibility(resumeRow.getChildCount() > 0 && !searching ? View.VISIBLE : View.GONE);
    }

    private View resumeCard(Context c, VodHistory.Entry e) {
        LinearLayout card = Ui.row(c);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.glass(c));
        card.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        android.widget.ImageView poster = new android.widget.ImageView(c);
        poster.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(Ui.round(0x33FFFFFF, 8, c));
        poster.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (e.poster != null && !e.poster.isEmpty()) {
            com.bumptech.glide.Glide.with(poster).load(e.poster)
                    .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                            new com.bumptech.glide.load.resource.bitmap.RoundedCorners(Ui.dp(c, 8))).into(poster);
        }
        card.addView(poster, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 64)));
        LinearLayout words = Ui.column(c);
        TextView name = Ui.text(c, e.name.isEmpty() ? e.title : e.name, 14, 0xFFFFFFFF, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(name);
        int left = e.minutesLeft();
        String where = e.tv && e.episode > 0 ? (e.season > 0 ? "S" + e.season + " " : "") + "E" + e.episode : "";
        String rest = left > 0 ? getString(R.string.f1_resume_left, left) : getString(R.string.f1_resume_carry_on);
        TextView sub = Ui.text(c, where.isEmpty() ? rest : where + " · " + rest, 12, Ui.SOFT, false);
        sub.setSingleLine(true);
        words.addView(sub, Ui.margins(Ui.wrap(), c, 0, 2, 0, 6));
        android.widget.ProgressBar bar = new android.widget.ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(Math.round(e.progress() * 1000));
        bar.setProgressTintList(ColorStateList.valueOf(Ui.accent(e.tv ? 2 : 1)));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        words.addView(bar, new LinearLayout.LayoutParams(-1, Ui.dp(c, 4)));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(Ui.dp(c, 160), -2);
        wp.leftMargin = Ui.dp(c, 10);
        card.addView(words, wp);
        card.setContentDescription((e.name.isEmpty() ? e.title : e.name) + (where.isEmpty() ? "" : ", " + where) + ", " + rest);
        card.setOnClickListener(v -> startActivity(com.gathin.tvhomephone.live.VodPlayerActivity.intent(requireContext(), e)));
        card.setOnLongClickListener(v -> {
            VodHistory.remove(requireContext(), e.key);
            Toast.makeText(requireContext(), getString(R.string.f1_resume_removed, e.name.isEmpty() ? e.title : e.name), Toast.LENGTH_SHORT).show();
            showResume();
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(c, 8);
        card.setLayoutParams(lp);
        return card;
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
        showResume();
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
        showResume();
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
