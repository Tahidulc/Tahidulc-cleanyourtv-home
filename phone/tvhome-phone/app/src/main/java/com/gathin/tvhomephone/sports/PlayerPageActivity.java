package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.gathin.tvhomephone.home.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's page, opened from the club page's middle card: on the club's ground (its colour, grain and vignette),
 * the club's crest and name at the top, then the player as PlayerPage draws him. Swipe (or the arrows either side) for
 * the one before or after him in the squad as the club page showed it, its pills and all. Going back, the club page's
 * carousel is on the last one shown. Nothing is read again: the club page as last shown is used; only when the app was
 * closed in between is the club read once more.
 */
public class PlayerPageActivity extends AppCompatActivity {
    /** The player last shown, given back to the club page (his name and number: SquadFilter.key). */
    static final String SHOWN_KEY = "shownKey";
    private static final String SLUG = "slug", ID = "id", NAME = "name", LOGO = "logo", STAFF = "staff", LINES = "lines", KEY = "key";

    private String slug, id, key;
    private boolean management;
    private int lines;
    private TeamData data;
    private List<TeamData.Player> list = new ArrayList<>();
    private int colour = ClubLook.NAVY, ground = ClubLook.ground(ClubLook.NAVY);
    private boolean wide;

    private FrameLayout root;
    private PageBits.Top top;
    private PagePager pager;
    private TextView prev, next, note;
    private int barTop, barBottom, barLeft, barRight;

    static Intent intent(Context c, String slug, String id, String club, String logo, boolean management, int lines, String key) {
        return new Intent(c, PlayerPageActivity.class).putExtra(SLUG, slug).putExtra(ID, id).putExtra(NAME, club).putExtra(LOGO, logo)
                .putExtra(STAFF, management).putExtra(LINES, lines).putExtra(KEY, key);
    }

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(com.gathin.tvhomephone.glass.Wide.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        slug = in.getStringExtra(SLUG);
        id = in.getStringExtra(ID);
        key = state != null && state.getString(KEY) != null ? state.getString(KEY) : in.getStringExtra(KEY);
        management = in.getBooleanExtra(STAFF, false);
        lines = in.getIntExtra(LINES, 0);
        if (slug == null || id == null) {
            finish();
            return;
        }
        Configuration cfg = getResources().getConfiguration();
        wide = PageBits.wide(cfg.screenWidthDp, cfg.screenHeightDp);
        build(in.getStringExtra(NAME), in.getStringExtra(LOGO));

        TeamData had = TeamData.lastShown(slug, id);
        if (had == null) had = TeamData.kept(slug, id);
        if (had != null) {
            show(had);
            return;
        }
        note.setText("Reading the squad…");
        TeamData.read(this, slug, id, in.getStringExtra(NAME), in.getStringExtra(LOGO), (t, done) -> {
            if (isFinishing() || isDestroyed() || !done) return;
            if (t == null) {
                note.setText("The squad can't be read just now");
                return;
            }
            t.done = true;
            TeamData.shown(t);
            show(t);
        });
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(KEY, key);
    }

    private void build(String club, String logo) {
        root = new FrameLayout(this);
        root.setBackground(new PageArt.Ground(colour, getResources().getDisplayMetrics().density, wide ? 0.27f : 0.5f, wide ? 0.5f : 0.3f));
        pager = new PagePager(this);
        root.addView(pager, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        note = PageBits.text(this, "", 15, 0xB3FFFFFF, Type.inter(this));
        note.setGravity(Gravity.CENTER);
        root.addView(note, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        top = PageBits.top(this);
        top.title.setText(club == null ? "" : club);
        PageBits.mark(top, logo);
        root.addView(top.row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        prev = PageBits.arrow(this, true);
        next = PageBits.arrow(this, false);
        prev.setOnClickListener(v -> pager.goTo(pager.current() - 1));
        next.setOnClickListener(v -> pager.goTo(pager.current() + 1));
        root.addView(prev, new FrameLayout.LayoutParams(PageBits.dp(this, 40), PageBits.dp(this, 64), Gravity.START | Gravity.TOP));
        root.addView(next, new FrameLayout.LayoutParams(PageBits.dp(this, 40), PageBits.dp(this, 64), Gravity.END | Gravity.TOP));
        prev.setVisibility(View.GONE);
        next.setVisibility(View.GONE);
        setContentView(root);
        PageBits.edgeToEdge(this, root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, ins) -> {
            Insets s = ins.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            barTop = s.top;
            barBottom = s.bottom;
            barLeft = s.left;
            barRight = s.right;
            pad();
            return WindowInsetsCompat.CONSUMED;
        });
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot || r - l != or - ol) v.post(this::pad);
        });
    }

    /** The top row under the status bar, the pages under it, the arrows beside the card. */
    private void pad() {
        int side = PageBits.dp(this, 12);
        top.row.setPadding(barLeft + PageBits.dp(this, 4), barTop + PageBits.dp(this, 4), barRight + side, PageBits.dp(this, 4));
        top.row.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, root.getWidth()), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int head = top.row.getMeasuredHeight();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pager.getLayoutParams();
        if (lp.topMargin != head || lp.leftMargin != barLeft || lp.rightMargin != barRight) {
            lp.topMargin = head;
            lp.leftMargin = barLeft;
            lp.rightMargin = barRight;
            pager.setLayoutParams(lp);
        }
        int arrowRoom = PageBits.dp(this, wide ? 60 : 0);
        for (int i = 0; i < 3; i++) {
            View page = pager.getChildAt(i);
            if (page instanceof PlayerPage) ((PlayerPage) page).setInsets(arrowRoom, barBottom);
        }
        int h = root.getHeight() - head - barBottom;
        int w = root.getWidth() - barLeft - barRight;
        // Beside the card's middle: on its side and on a tablet, half way down; upright, half way down the photo.
        float p = wide ? 0 : Math.min(w * 0.6f, (root.getHeight() - head) * 0.44f / 1.78f);
        int y = head + (wide ? h / 2 : Math.round(0.9f * p)) - PageBits.dp(this, 32);
        for (TextView a : new TextView[]{prev, next}) {
            FrameLayout.LayoutParams ap = (FrameLayout.LayoutParams) a.getLayoutParams();
            ap.topMargin = Math.max(head, y);
            ap.leftMargin = barLeft + PageBits.dp(this, 10);
            ap.rightMargin = barRight + PageBits.dp(this, 10);
            a.setLayoutParams(ap);
        }
    }

    private void show(TeamData t) {
        data = t;
        colour = ClubLook.pick(TeamData.colour(t.color, 0), TeamData.colour(t.alternate, 0));
        ground = ClubLook.ground(colour);
        root.setBackground(new PageArt.Ground(colour, getResources().getDisplayMetrics().density, wide ? 0.27f : 0.5f, wide ? 0.5f : 0.3f));
        top.title.setText(t.name);
        PageBits.mark(top, t.logo);
        list = SquadFilter.shown(t.squad, management, lines);
        if (list.isEmpty()) {
            note.setText("No one to show");
            return;
        }
        note.setVisibility(View.GONE);
        int at = key == null ? 0 : Math.max(0, SquadFilter.indexOf(list, key));
        pager.setPages(new PagePager.Pages() {
            @Override public int count() {
                return list.size();
            }

            @Override public View make() {
                PlayerPage page = new PlayerPage(PlayerPageActivity.this, wide);
                page.setColours(colour, ground);
                page.setInsets(PageBits.dp(PlayerPageActivity.this, wide ? 60 : 0), barBottom);
                return page;
            }

            @Override public void bind(View page, int index) {
                ((PlayerPage) page).bind(list.get(index), data.season);
            }

            @Override public void moved(float pos) {
            }

            @Override public void settled(int index) {
                arrive(index);
            }
        }, at);
        pad();
    }

    private void arrive(int index) {
        if (index < 0 || index >= list.size()) return;
        key = SquadFilter.key(list.get(index));
        setResult(RESULT_OK, new Intent().putExtra(SHOWN_KEY, key));
        String group = management ? "Management" : lines == 0 ? "Squad" : PlayerFacts.lineLabel(list.get(index).line).charAt(0)
                + PlayerFacts.lineLabel(list.get(index).line).substring(1).toLowerCase(java.util.Locale.ROOT);
        if (!management && Integer.bitCount(lines) > 1) group = "Squad";
        top.line.setText(group + "  ·  " + (index + 1) + " of " + list.size());
        prev.setVisibility(index > 0 ? View.VISIBLE : View.GONE);
        next.setVisibility(index < list.size() - 1 ? View.VISIBLE : View.GONE);
    }
}
