package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.home.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An F1 driver's page, opened from a driver in Sports' Formula 1: DriverPage for each driver in the championship's
 * order, swiped through (or the arrows either side), the page's colour turning to the next team's as it goes. The
 * countdown to the next Grand Prix goes on minute by minute. The standings and the next Grand Prix are those Sports read
 * (read here only when the app was closed in between); a driver's Grands Prix and podiums are one read of his results
 * once the page rests on him, the drivers either side read after him and nothing further.
 */
public class DriverActivity extends AppCompatActivity {
    private static final String ID = "driverId", NAME = "name";

    private boolean wide;
    private String driverId, name;
    private List<F1.Standing> drivers = new ArrayList<>();
    private F1.Weekend next;
    private final List<DriverPage> pages = new ArrayList<>();
    private final Set<String> reading = new HashSet<>();
    private final Map<Integer, PageArt.Ground> grounds = new HashMap<>();
    private final PageArt.Fade fade = new PageArt.Fade();

    private FrameLayout root;
    private PageBits.Top top;
    private PagePager pager;
    private TextView prev, nextArrow, note;
    private int barTop, barBottom, barLeft, barRight;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            countdown();
        }
    };

    /** The page for a driver (his Jolpica id, else his name), the rest of the championship either side. */
    public static Intent intent(Context c, String driverId, String name) {
        return new Intent(c, DriverActivity.class).putExtra(ID, driverId).putExtra(NAME, name);
    }

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(com.gathin.tvhomephone.glass.Wide.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        driverId = state != null && state.getString(ID) != null ? state.getString(ID) : getIntent().getStringExtra(ID);
        name = getIntent().getStringExtra(NAME);
        Configuration cfg = getResources().getConfiguration();
        wide = PageBits.wide(cfg.screenWidthDp, cfg.screenHeightDp);
        build();
        List<F1.Standing> had = F1.keptDrivers();
        next = F1.keptNext();
        if (had != null && !had.isEmpty()) {
            show(had);
            if (next == null) readNext();
            return;
        }
        note.setText("Reading the championship…");
        App.io.execute(() -> {
            List<F1.Standing> got = F1.drivers();
            F1.Weekend w = F1.next();
            App.main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                next = w;
                if (got.isEmpty()) {
                    note.setText("The championship can't be read just now");
                    return;
                }
                show(got);
            });
        });
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(ID, driverId);
    }

    @Override protected void onStart() {
        super.onStart();
        countdown();
    }

    @Override protected void onStop() {
        super.onStop();
        App.main.removeCallbacks(tick);
    }

    @Override protected void onDestroy() {
        App.main.removeCallbacks(tick);
        for (DriverPage p : pages) p.release();
        super.onDestroy();
    }

    private void readNext() {
        App.io.execute(() -> {
            F1.Weekend w = F1.next();
            App.main.post(() -> {
                if (isDestroyed() || w == null) return;
                next = w;
                for (DriverPage p : pages) p.setNext(w);
                countdown();
            });
        });
    }

    private void build() {
        root = new FrameLayout(this);
        root.setBackground(fade);
        int start = F1.colour("");
        fade.set(ground(start), null, 0f);
        pager = new PagePager(this);
        root.addView(pager, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        note = PageBits.text(this, "", 15, 0xB3FFFFFF, Type.inter(this));
        note.setGravity(Gravity.CENTER);
        root.addView(note, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        top = PageBits.top(this);
        top.title.setText("Drivers' championship");
        PageBits.mark(top, Competitions.F1_LOGO);
        root.addView(top.row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        prev = PageBits.arrow(this, true);
        nextArrow = PageBits.arrow(this, false);
        prev.setOnClickListener(v -> pager.goTo(pager.current() - 1));
        nextArrow.setOnClickListener(v -> pager.goTo(pager.current() + 1));
        int aw = PageBits.dp(this, wide ? 40 : 36), ah = PageBits.dp(this, wide ? 64 : 56);
        root.addView(prev, new FrameLayout.LayoutParams(aw, ah, Gravity.START | Gravity.TOP));
        root.addView(nextArrow, new FrameLayout.LayoutParams(aw, ah, Gravity.START | Gravity.TOP));
        prev.setVisibility(View.GONE);
        nextArrow.setVisibility(View.GONE);
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
        pager.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> v.post(this::placeArrows));
    }

    /** A team's deep ground (its colour a shade under the card's), made once for each colour. */
    private PageArt.Ground ground(int colour) {
        PageArt.Ground g = grounds.get(colour);
        if (g == null) {
            g = new PageArt.Ground(ClubLook.lighter(ClubLook.ground(colour), -0.085), getResources().getDisplayMetrics().density, 0.3f, 0.35f);
            grounds.put(colour, g);
        }
        return g;
    }

    private void pad() {
        int side = PageBits.dp(this, 12);
        top.row.setPadding(barLeft + PageBits.dp(this, 4), barTop + PageBits.dp(this, 4), barRight + side, PageBits.dp(this, 4));
        top.row.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, root.getWidth()), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int head = top.row.getMeasuredHeight();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pager.getLayoutParams();
        if (lp.topMargin != head || lp.leftMargin != barLeft || lp.rightMargin != barRight || lp.bottomMargin != barBottom) {
            lp.topMargin = head;
            lp.leftMargin = barLeft;
            lp.rightMargin = barRight;
            lp.bottomMargin = barBottom;
            pager.setLayoutParams(lp);
        }
        placeArrows();
    }

    /** The arrows where the page puts them (inside the card's edges; beside the driver upright). */
    private void placeArrows() {
        DriverPage p = pages.isEmpty() ? null : pageAt(pager.current());
        if (p == null || p.getWidth() == 0) return;
        View holder = (View) pager.viewFor(pager.current());
        int scrolled = holder instanceof ScrollView ? holder.getScrollY() : 0;
        int y = pager.getTop() + Math.round(p.arrowY()) - scrolled;
        place(prev, pager.getLeft() + Math.round(p.arrowLeftX()), y);
        place(nextArrow, pager.getLeft() + Math.round(p.arrowRightX()), y);
    }

    private static void place(View a, int cx, int cy) {
        FrameLayout.LayoutParams ap = (FrameLayout.LayoutParams) a.getLayoutParams();
        int l = cx - ap.width / 2, t = cy - ap.height / 2;
        if (ap.leftMargin != l || ap.topMargin != t) {
            ap.leftMargin = l;
            ap.topMargin = t;
            a.setLayoutParams(ap);
        }
    }

    private DriverPage pageAt(int index) {
        View v = pager.viewFor(index);
        if (v instanceof DriverPage) return (DriverPage) v;
        if (v instanceof ScrollView && ((ScrollView) v).getChildCount() > 0) return (DriverPage) ((ScrollView) v).getChildAt(0);
        return null;
    }

    private void show(List<F1.Standing> list) {
        drivers = list;
        note.setVisibility(View.GONE);
        int at = DriverFacts.indexOf(drivers, driverId, name);
        pager.setPages(new PagePager.Pages() {
            @Override public int count() {
                return drivers.size();
            }

            @Override public View make() {
                DriverPage page = new DriverPage(DriverActivity.this, wide);
                pages.add(page);
                if (wide) return page;
                ScrollView sv = new ScrollView(DriverActivity.this);
                sv.setFillViewport(true);
                sv.setVerticalScrollBarEnabled(false);
                sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
                sv.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                sv.setOnScrollChangeListener((v, x, y, ox, oy) -> placeArrows());
                return sv;
            }

            @Override public void bind(View view, int index) {
                F1.Standing d = drivers.get(index);
                DriverPage page = view instanceof ScrollView ? (DriverPage) ((ScrollView) view).getChildAt(0) : (DriverPage) view;
                page.bind(d, F1.keptSeason(d.driverId), next);
                page.setCountdown(counted, underWay);
                if (view instanceof ScrollView) view.scrollTo(0, 0);
            }

            @Override public void moved(float pos) {
                int n = drivers.size();
                if (n == 0) return;
                int a = Math.max(0, Math.min(n - 1, (int) Math.floor(pos))), b = Math.min(n - 1, a + 1);
                float t = b == a ? 0f : Math.max(0f, Math.min(1f, pos - a));
                fade.set(ground(F1.colour(drivers.get(a).teamId)), ground(F1.colour(drivers.get(b).teamId)), t);
            }

            @Override public void settled(int index) {
                arrive(index);
            }
        }, at);
        countdown();
    }

    private void arrive(int index) {
        if (index < 0 || index >= drivers.size()) return;
        F1.Standing d = drivers.get(index);
        driverId = d.driverId;
        String place = DriverFacts.place(d.position);
        StringBuilder line = new StringBuilder();
        if (!place.isEmpty()) line.append(place).append(" of ").append(drivers.size());
        if (!d.points.isEmpty()) line.append(line.length() > 0 ? "  ·  " : "").append(d.points).append(" points");
        top.line.setText(line);
        prev.setVisibility(index > 0 ? View.VISIBLE : View.GONE);
        nextArrow.setVisibility(index < drivers.size() - 1 ? View.VISIBLE : View.GONE);
        placeArrows();
        load(index);
    }

    /** His season (one read, unless it was read lately), then the drivers either side, read ahead. */
    private void load(int index) {
        F1.Standing d = drivers.get(index);
        if (F1.keptSeason(d.driverId) != null) {
            ahead(index);
            return;
        }
        read(d, () -> ahead(index));
    }

    private void ahead(int index) {
        for (int i = index - 1; i <= index + 1; i += 2) {
            if (i < 0 || i >= drivers.size()) continue;
            F1.Standing d = drivers.get(i);
            if (F1.keptSeason(d.driverId) == null) read(d, null);
        }
    }

    private void read(F1.Standing d, Runnable then) {
        String id = d.driverId;
        if (id == null || id.isEmpty() || reading.contains(id)) return;
        reading.add(id);
        App.io.execute(() -> {
            F1.Season s = F1.season(id);
            App.main.post(() -> {
                reading.remove(id);
                if (isDestroyed()) return;
                if (s != null) {
                    for (DriverPage p : pages) if (p.driver() == d) p.setSeason(s);
                }
                if (then != null) then.run();
            });
        });
    }

    private String[] counted = {"", "", ""};
    private boolean underWay;

    /** The countdown now, on every page, and again when its minutes next change. */
    private void countdown() {
        App.main.removeCallbacks(tick);
        if (next == null || next.race <= 0) return;
        long now = System.currentTimeMillis();
        counted = DriverFacts.countdown(next.race, now);
        underWay = now >= next.race;
        for (DriverPage p : pages) p.setCountdown(counted, underWay);
        if (!underWay) App.main.postDelayed(tick, DriverFacts.untilTick(next.race, now));
    }
}
