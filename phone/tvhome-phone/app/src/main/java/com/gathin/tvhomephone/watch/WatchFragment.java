package com.gathin.tvhomephone.watch;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.MainActivity;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.glass.AmbientView;
import com.gathin.tvhomephone.glass.BlurBehind;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.GlassDrawable;
import com.gathin.tvhomephone.glass.Icons;
import com.gathin.tvhomephone.glass.LensBar;
import com.gathin.tvhomephone.sync.Tvs;

import java.io.IOException;
import java.util.List;

/**
 * Watch, in glass as the family's reference picture has it: the list's first picture glowing softly behind the page;
 * the greeting and a pill along the top (search, Discover, Movies, TV shows) that stay as the page scrolls under them,
 * frosted; the section's lists as glass cards with three of their posters fanned in each (tap one to show it below);
 * what was left part-way from the family's own playlist as landscape cards; then the list's posters, three across on a
 * phone, more on a tablet. Pull down to sync with the TVs and read the list again. Hold a poster to put it on My list
 * (on Coming up: its pop-up off or on); hold a Continue watching card to take it off.
 */
public class WatchFragment extends Fragment {
    private static int section, chosen;
    /** Asked from elsewhere (Home's pill): open with the search open. */
    private static boolean askSearch;

    /** Opens Watch (next time it shows) on a section: 0 Discover, 1 Movies, 2 TV shows; search to open its search. */
    public static void ask(int s, boolean search) {
        if (s >= 0 && s <= 2) {
            if (s != section) chosen = 0;
            section = s;
        }
        askSearch = search;
    }

    private AmbientView ambient;
    private FrameLayout top;
    private BlurBehind shade;
    private View hairline;
    private LinearLayout topCol, searchBox;
    private Glass.Header header;
    private LensBar pill;
    private EditText search;
    private SwipeRefreshLayout refresh;
    private RecyclerView grid, cardRow;
    private PosterAdapter posters;
    private ListCards cards;
    private ResumeRow resume;
    private TextView heading, note;
    private FrameLayout pairBox;
    /** The room at the head of the page for the top that floats over it (as tall as the top is). */
    private View topSpace;
    private List<Lists.Spec> specs;
    private Lists.Spec shown;
    private int gen, scrolled, bottomRoom;
    private final Runnable onSync = this::reshow;

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        Context c = requireContext();
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(Glass.GROUND);
        ambient = new AmbientView(c);
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));

        // The page: one grid whose first item, the width of the page, holds the cards and Continue watching.
        refresh = new SwipeRefreshLayout(c);
        refresh.setColorSchemeColors(0xFFFFFFFF);
        refresh.setProgressBackgroundColorSchemeColor(0xFF24262E);
        grid = new RecyclerView(c);
        grid.setClipToPadding(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        int cols = columns(c);
        GridLayoutManager lm = new GridLayoutManager(c, cols);
        posters = new PosterAdapter((t, i) -> TitleActivity.open(requireContext(), t), this::hold, 0);
        LinearLayout head = head(c);
        lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int p) {
                return p == 0 ? cols : 1;
            }
        });
        grid.setLayoutManager(lm);
        grid.setAdapter(new ConcatAdapter(new Head(head), posters));
        int side = Glass.dp(c, 10);
        grid.setPadding(side, 0, side, 0);
        refresh.addView(grid);
        refresh.setOnRefreshListener(() -> Tvs.syncNow(requireContext(), () -> {
            if (!isAdded()) return;
            refresh.setRefreshing(false);
            reshow();
        }));
        root.addView(refresh, new FrameLayout.LayoutParams(-1, -1));

        // The top that stays: frosted once the page is under it, the greeting folding away as it scrolls.
        top = new FrameLayout(c);
        shade = new BlurBehind(c, 0, GlassDrawable.BAR);
        shade.setBackground(null);
        shade.setTint(0x8C0E0F14);
        shade.setSources(ambient, refresh);
        shade.setAlpha(0f);
        top.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        hairline = new View(c);
        hairline.setBackgroundColor(0x1FFFFFFF);
        hairline.setAlpha(0f);
        top.addView(hairline, new FrameLayout.LayoutParams(-1, Math.max(1, Glass.dp(c, 0.6f)), Gravity.BOTTOM));
        topCol = new LinearLayout(c);
        topCol.setOrientation(LinearLayout.VERTICAL);
        header = Glass.header(c);
        header.view.setPadding(Glass.dp(c, 20), Glass.dp(c, 10), Glass.dp(c, 20), 0);
        topCol.addView(header.view, new LinearLayout.LayoutParams(-1, -2));
        pill = new LensBar(c, LensBar.PILL);
        pill.action(Icons.SEARCH, "Search", this::openSearch).segment(0, "Discover").segment(1, "Movies").segment(2, "TV shows");
        pill.select(section, false);
        pill.setOnPick(id -> {
            if (id == section) return;
            chosen = 0;
            section = id;
            closeSearch(false);
            buildCards();
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.setMargins(Glass.dp(c, 16), Glass.dp(c, 16), Glass.dp(c, 16), Glass.dp(c, 12));
        topCol.addView(pill, pp);
        searchBox = searchBox(c);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, Glass.dp(c, 46));
        sp.setMargins(Glass.dp(c, 16), 0, Glass.dp(c, 16), Glass.dp(c, 12));
        topCol.addView(searchBox, sp);
        searchBox.setVisibility(View.GONE);
        top.addView(topCol, new FrameLayout.LayoutParams(-1, -2));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // The grid starts under the top, scrolls under it and under the tab bar at the foot.
        top.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) place();
        });
        Glass.fit(root, (t, b) -> {
            topCol.setPadding(0, t, 0, 0);
            bottomRoom = b;
            place();
        });
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                scrolled = Math.max(0, r.computeVerticalScrollOffset());
                fold();
            }
        });
        buildCards();
        if (askSearch) {
            askSearch = false;
            root.post(this::openSearch);
        }
        return root;
    }

    /** The grid's first item: the lists' cards, Continue watching, the list's heading, and its note or pair card. */
    private LinearLayout head(Context c) {
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        topSpace = new View(c);
        head.addView(topSpace, new LinearLayout.LayoutParams(-1, 0));
        cardRow = new RecyclerView(c);
        cardRow.setLayoutManager(new LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false));
        cardRow.setClipToPadding(false);
        cardRow.setHorizontalScrollBarEnabled(false);
        cardRow.setOverScrollMode(View.OVER_SCROLL_NEVER);
        cardRow.setPadding(Glass.dp(c, 16), Glass.dp(c, 4), Glass.dp(c, 4), Glass.dp(c, 4));
        cards = new ListCards(c, this::showList);
        cardRow.setAdapter(cards);
        head.addView(cardRow, new LinearLayout.LayoutParams(-1, Glass.dp(c, 108)));
        resume = new ResumeRow(this);
        head.addView(resume.view, new LinearLayout.LayoutParams(-1, -2));
        heading = Glass.section(c, "", null);
        head.addView(heading);
        pairBox = new FrameLayout(c);
        pairBox.setPadding(Glass.dp(c, 16), Glass.dp(c, 4), Glass.dp(c, 16), 0);
        head.addView(pairBox, new LinearLayout.LayoutParams(-1, -2));
        note = Glass.text(c, "", 15, Glass.MUTED, Glass.body(c));
        note.setGravity(Gravity.CENTER);
        note.setPadding(Glass.dp(c, 32), Glass.dp(c, 36), Glass.dp(c, 32), Glass.dp(c, 36));
        head.addView(note, new LinearLayout.LayoutParams(-1, -2));
        // The head spans the grid's whole width, its padding included, so the cards run to the screen's edges.
        int side = Glass.dp(c, 10);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
        lp.leftMargin = -side;
        lp.rightMargin = -side;
        head.setLayoutParams(lp);
        return head;
    }

    /** The grid's first item, one view kept for the page's life. */
    private static final class Head extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final View view;

        Head(View view) {
            this.view = view;
        }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
            RecyclerView.ViewHolder h = new RecyclerView.ViewHolder(view) { };
            h.setIsRecyclable(false);
            return h;
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) { }

        @Override public int getItemCount() {
            return 1;
        }

        @Override public int getItemViewType(int position) {
            return 7001;
        }
    }

    /** The search field under the pill: glass, a lens, the words, and a cross to close it. */
    private LinearLayout searchBox(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(Glass.card(c, 18));
        box.setPadding(Glass.dp(c, 14), 0, Glass.dp(c, 6), 0);
        ImageView lens = new ImageView(c);
        lens.setImageDrawable(new Icons(c, Icons.SEARCH, Glass.MUTED));
        box.addView(lens, new LinearLayout.LayoutParams(Glass.dp(c, 18), Glass.dp(c, 18)));
        search = new EditText(c);
        search.setBackground(null);
        search.setHint("Search films and series");
        search.setHintTextColor(Glass.FAINT);
        search.setTextColor(Glass.TEXT);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        search.setTypeface(Glass.body(c));
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(Glass.dp(c, 10), 0, Glass.dp(c, 6), 0);
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
                if (s.length() == 0 && searching()) showList(chosen);
            }
        });
        box.addView(search, new LinearLayout.LayoutParams(0, -1, 1f));
        ImageView close = new ImageView(c);
        close.setImageDrawable(new Icons(c, Icons.CLOSE, Glass.SOFT));
        close.setPadding(Glass.dp(c, 9), Glass.dp(c, 9), Glass.dp(c, 9), Glass.dp(c, 9));
        close.setContentDescription("Close search");
        close.setOnClickListener(v -> closeSearch(true));
        box.addView(close, new LinearLayout.LayoutParams(Glass.dp(c, 38), Glass.dp(c, 38)));
        return box;
    }

    private static int columns(Context c) {
        float widthDp = c.getResources().getDisplayMetrics().widthPixels / c.getResources().getDisplayMetrics().density;
        return Math.max(3, Math.min(8, (int) (widthDp / 125)));
    }

    /**
     * Room at the head of the page for the top (as tall as it is now), and at its foot for the tab bar; the pull-to-sync
     * spinner under the top. The room is part of the page's first item, so the page keeps its place as the top changes.
     */
    private void place() {
        if (top == null || grid == null || !isAdded()) return;
        int h = top.getHeight();
        if (h <= 0) return;
        LinearLayout.LayoutParams sp = (LinearLayout.LayoutParams) topSpace.getLayoutParams();
        if (sp.height != h + Glass.dp(requireContext(), 4)) {
            sp.height = h + Glass.dp(requireContext(), 4);
            topSpace.setLayoutParams(sp);
        }
        grid.setPadding(grid.getPaddingLeft(), 0, grid.getPaddingRight(), bottomRoom);
        refresh.setProgressViewOffset(false, h - Glass.dp(requireContext(), 24), h + Glass.dp(requireContext(), 36));
        fold();
    }

    /** As the page scrolls: the greeting folds up under the status bar, the top frosts, the glow drifts. */
    private void fold() {
        if (header == null || top == null || !isAdded()) return;
        float greet = header.view.getHeight();
        float up = Math.min(scrolled, greet);
        top.setTranslationY(-up);
        header.view.setAlpha(greet <= 0 ? 1f : 1f - up / greet);
        float frost = Math.max(0f, Math.min(1f, scrolled / (float) Glass.dp(requireContext(), 28)));
        shade.setAlpha(frost);
        hairline.setAlpha(frost);
        ambient.setShift(scrolled);
    }

    @Override public void onStart() {
        super.onStart();
        Tvs.addListener(onSync);
        SyncStore.addListener(onSync);
        resume.show(section, searching());
        int lists = 0;
        try {
            lists = com.gathin.tvhomephone.live.Sources.inUse(requireContext()).size();
        } catch (RuntimeException ignored) {
            // no playlists read yet
        }
        header.now(lists == 0 ? "" : lists == 1 ? "1 playlist" : lists + " playlists");
    }

    @Override public void onResume() {
        super.onResume();
        // Asked again (Home's pill) while Watch was already made: that section, and its search when asked.
        if (pill != null && pill.selected() != section) {
            pill.select(section, true);
            buildCards();
        }
        if (askSearch) {
            askSearch = false;
            openSearch();
        }
    }

    @Override public void onStop() {
        Tvs.removeListener(onSync);
        SyncStore.removeListener(onSync);
        super.onStop();
    }

    private boolean searching() {
        return shown != null && shown.name.equals("Search");
    }

    private void openSearch() {
        if (!isAdded() || searchBox == null) return;
        searchBox.setVisibility(View.VISIBLE);
        searchBox.setAlpha(0f);
        searchBox.setTranslationY(-Glass.dp(requireContext(), 8));
        searchBox.animate().alpha(1f).translationY(0f).setDuration(220).start();
        search.requestFocus();
        InputMethodManager im = requireContext().getSystemService(InputMethodManager.class);
        if (im != null) im.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
    }

    /** Closes the search field; back to the list (when it was showing results). */
    private void closeSearch(boolean back) {
        if (searchBox == null || searchBox.getVisibility() != View.VISIBLE) return;
        InputMethodManager im = requireContext().getSystemService(InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.setText("");
        searchBox.setVisibility(View.GONE);
        if (back && searching()) showList(chosen);
    }

    /** After a sync (or a change here): My list, ratings and the TVs' watching may be different; the list again. */
    private void reshow() {
        if (!isAdded() || shown == null) return;
        resume.show(section, searching());
        if (searching()) return;
        String was = shown.name;
        List<Lists.Spec> now = Lists.section(requireContext(), section);
        boolean same = now.size() == specs.size();
        for (int i = 0; same && i < now.size(); i++) same = now.get(i).name.equals(specs.get(i).name);
        // A TMDB list doesn't change with a sync; the cards only when the Because tabs did.
        if (same && shown.special == null) return;
        for (int i = 0; i < now.size(); i++) if (now.get(i).name.equals(was)) chosen = i;
        if (same) load(now.get(chosen));
        else buildCards();
    }

    /** The section's lists as cards, the chosen one showing below. */
    private void buildCards() {
        Context c = requireContext();
        specs = Lists.section(c, section);
        if (chosen >= specs.size()) chosen = 0;
        cards.set(section, specs, chosen);
        cardRow.scrollToPosition(chosen);
        showList(chosen);
    }

    private void showList(int i) {
        if (!isAdded() || specs == null || specs.isEmpty()) return;
        chosen = Math.max(0, Math.min(i, specs.size() - 1));
        cards.choose(chosen);
        cardRow.smoothScrollToPosition(chosen);
        load(specs.get(chosen));
        resume.show(section, false);
    }

    private void runSearch() {
        String q = search.getText().toString().trim();
        InputMethodManager im = requireContext().getSystemService(InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(search.getWindowToken(), 0);
        if (q.isEmpty()) {
            showList(chosen);
            return;
        }
        cards.choose(-1);
        resume.show(section, true);
        load(Lists.search(q));
        heading.setText("Results for “" + q + "”");
    }

    private void load(Lists.Spec s) {
        Context c = requireContext();
        shown = s;
        int my = ++gen;
        if (!s.name.equals("Search")) heading.setText(s.name);
        pairBox.removeAllViews();
        // What the TVs and this phone keep shows without TMDB; everything else needs its key, which a TV gives.
        boolean local = s.special != null && (s.special.equals("@mylist") || s.special.equals("@resume")
                || s.special.equals("@yourshows") || s.special.equals("@newepisodes"));
        if (!Tmdb.ready(c) && !local) {
            posters.set(null);
            note.setVisibility(View.GONE);
            View card = Ui.pairCard(c, () -> {
                if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).open(R.id.tab_settings);
            });
            card.setBackground(Glass.card(c, 22));
            pairBox.addView(card, new FrameLayout.LayoutParams(-1, -2));
            return;
        }
        note.setVisibility(View.VISIBLE);
        note.setText("Loading…");
        posters.set(null);
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
                posters.set(list);
                boolean empty = list == null || list.isEmpty();
                note.setVisibility(empty ? View.VISIBLE : View.GONE);
                if (empty) note.setText(why != null ? why : empty(s));
                // The list's first picture glows behind the page.
                if (!empty) {
                    Title first = list.get(0);
                    ambient.setImage(first.backdrop != null ? first.backdrop : first.poster);
                }
            });
        });
    }

    /** For a preview drawn off the phone: these titles as the list showing, and the glow from this picture. */
    void preview(List<Title> list, android.graphics.Bitmap glow) {
        gen++;
        pairBox.removeAllViews();
        note.setVisibility(View.GONE);
        posters.set(list);
        if (glow != null) ambient.setBitmap(glow);
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
