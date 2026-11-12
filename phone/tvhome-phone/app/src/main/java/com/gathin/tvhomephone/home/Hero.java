package com.gathin.tvhomephone.home;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.gathin.tvhomephone.App;

import java.util.ArrayList;
import java.util.List;

/**
 * Home's hero, as the TV launcher's: a few titles to swipe between, each over its backdrop with its logo (or its name
 * in Outfit), a line of facts, a kicker saying why it's there, and Play (or Resume) and More. It moves on by itself
 * every AUTO_MS while Home shows and nobody holds it, round and round, and tells Home which page is up so the blurred
 * backdrop behind the page follows it.
 */
final class Hero {
    static final long AUTO_MS = 9_000L;
    private static final int LOOPS = 1000;

    /** One page: what it shows and what its buttons do. */
    static final class Page {
        String kicker = "", title = "", facts = "", overview = "", cert = "", image, logo, play = "Play", more = "More info";
        int accent = Art.FILM;
        /** How far in (0 to 1) for Resume; negative for none. */
        float progress = -1;
        String left = "";
        Runnable onPlay, onMore;
        /** A loading page: a shimmer and nothing else. */
        boolean loading;
    }

    interface OnPage { void on(Page p); }

    final FrameLayout box;
    final RecyclerView pager;
    final LinearLayout dots;
    private final Context c;
    private final List<Page> pages = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private final Snap snap = new Snap();
    private final int height;
    /** Sideways or on a wide screen; compact: too short a hero for the overview (a phone sideways). */
    private final boolean wide, compact;
    private OnPage onPage;
    private boolean visible, touching;
    private int shownAt = -1;
    private final Runnable auto = this::advance;

    Hero(Context c, int height) {
        this.c = c;
        this.height = height;
        this.wide = c.getResources().getConfiguration().screenWidthDp >= 600;
        this.compact = height < Art.dp(c, 340);
        box = new FrameLayout(c);
        pager = new RecyclerView(c);
        pager.setLayoutManager(new LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false));
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pager.setItemAnimator(null);
        pager.setHasFixedSize(true);
        snap.attachToRecyclerView(pager);
        pager.setAdapter(adapter);
        pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView r, int s) {
                if (s == RecyclerView.SCROLL_STATE_IDLE) {
                    settled();
                    later();
                } else if (s == RecyclerView.SCROLL_STATE_DRAGGING) {
                    App.main.removeCallbacks(auto);
                }
            }
        });
        // A finger on the hero holds it; it moves on again AUTO_MS after it is let go.
        pager.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override public boolean onInterceptTouchEvent(@NonNull RecyclerView r, @NonNull MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    touching = true;
                    App.main.removeCallbacks(auto);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    touching = false;
                    later();
                }
                return false;
            }
        });
        box.addView(pager, new FrameLayout.LayoutParams(-1, height));
        dots = new LinearLayout(c);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-2, Art.dp(c, 8), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dl.bottomMargin = Art.dp(c, 12);
        box.addView(dots, dl);
    }

    void onPage(OnPage l) {
        onPage = l;
    }

    /** The pages (none: a loading page). Keeps the page it was on when the same number come again. */
    void set(List<Page> list) {
        int was = pages.size();
        int on = was == 0 ? 0 : current() % was;
        pages.clear();
        if (list == null || list.isEmpty()) {
            Page p = new Page();
            p.loading = true;
            pages.add(p);
        } else {
            pages.addAll(list);
        }
        adapter.notifyDataSetChanged();
        int start = pages.size() < 2 ? 0 : (LOOPS / 2) * pages.size() + (pages.size() == was ? on : 0);
        pager.scrollToPosition(start);
        shownAt = -1;
        dots();
        App.main.post(this::settled);
        later();
    }

    void visible(boolean on) {
        visible = on;
        touching = false;
        if (on) later();
        else App.main.removeCallbacks(auto);
    }

    /** The pictures stay a little behind as the page scrolls up (a gentle parallax). */
    void parallax(int scrollY) {
        float y = Math.max(0, scrollY) * 0.35f;
        for (int i = 0; i < pager.getChildCount(); i++) {
            View page = pager.getChildAt(i);
            View img = page instanceof ViewGroup ? ((ViewGroup) page).getChildAt(0) : null;
            if (img != null) img.setTranslationY(y);
        }
    }

    private int current() {
        LinearLayoutManager lm = (LinearLayoutManager) pager.getLayoutManager();
        if (lm == null) return 0;
        int at = lm.findFirstCompletelyVisibleItemPosition();
        if (at < 0) at = lm.findFirstVisibleItemPosition();
        return Math.max(0, at);
    }

    private void settled() {
        if (pages.isEmpty()) return;
        int at = current() % pages.size();
        if (at == shownAt) return;
        shownAt = at;
        dots();
        if (onPage != null) onPage.on(pages.get(at));
    }

    private void later() {
        App.main.removeCallbacks(auto);
        if (visible && !touching && pages.size() > 1) App.main.postDelayed(auto, AUTO_MS);
    }

    private void advance() {
        if (!visible || touching || pages.size() < 2 || pager.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            later();
            return;
        }
        LinearLayoutManager lm = (LinearLayoutManager) pager.getLayoutManager();
        if (lm == null) return;
        int at = current();
        if (at + 1 >= adapter.getItemCount()) {
            pager.scrollToPosition((LOOPS / 2) * pages.size() + at % pages.size());
            later();
            return;
        }
        RecyclerView.SmoothScroller s = snap.slide(lm);
        if (s == null) pager.smoothScrollToPosition(at + 1);
        else {
            s.setTargetPosition(at + 1);
            lm.startSmoothScroll(s);
        }
        later();
    }

    /** One small dot a page, the one showing long and white. */
    private void dots() {
        dots.removeAllViews();
        if (pages.size() < 2) return;
        int on = Math.max(0, shownAt);
        for (int i = 0; i < pages.size(); i++) {
            View d = new View(c);
            boolean here = i == on;
            d.setBackground(Art.round(here ? 0xFFFFFFFF : 0x55FFFFFF, Art.dp(c, 3)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Art.dp(c, here ? 18 : 6), Art.dp(c, 6));
            lp.leftMargin = lp.rightMargin = Art.dp(c, 3);
            dots.addView(d, lp);
        }
    }

    private static final class Snap extends PagerSnapHelper {
        RecyclerView.SmoothScroller slide(RecyclerView.LayoutManager lm) {
            return createScroller(lm);
        }
    }

    // ---- a page ---------------------------------------------------------------------------------------------------

    private final class Holder extends RecyclerView.ViewHolder {
        final ImageView image, logo;
        final TextView kicker, title, facts, cert, overview, play, more, left;
        final LinearLayout words, progressRow;
        final View fill;
        String logoFor;

        Holder(FrameLayout page) {
            super(page);
            page.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
            page.setClipChildren(true);
            image = new ImageView(c);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            page.addView(image, new FrameLayout.LayoutParams(-1, -1));
            View top = new View(c);
            top.setBackground(Art.vertical(0xCC0B0C21, 0x000B0C21));
            page.addView(top, new FrameLayout.LayoutParams(-1, Art.dp(c, 150), Gravity.TOP));
            if (wide) {
                View side = new View(c);
                side.setBackground(Art.horizontal(0xE60D0F2C, 0x000D0F2C));
                page.addView(side, new FrameLayout.LayoutParams(Math.round(c.getResources().getDisplayMetrics().widthPixels * 0.7f), -1, Gravity.START));
            }
            View bottom = new View(c);
            bottom.setBackground(Art.vertical(0x000D0F2C, 0xB30D0F2C, 0xFF0D0F2C));
            page.addView(bottom, new FrameLayout.LayoutParams(-1, Math.round(height * (wide ? 0.55f : 0.62f)), Gravity.BOTTOM));

            words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setGravity(Gravity.BOTTOM | Gravity.START);
            int side = Art.dp(c, wide ? 28 : 20);
            words.setPadding(side, 0, side, Art.dp(c, compact ? 28 : 34));
            kicker = Rows.text(c, 11, Art.FILM, Type.interSemi(c), 1);
            kicker.setLetterSpacing(0.12f);
            words.addView(kicker);
            FrameLayout titleBox = new FrameLayout(c);
            title = Rows.text(c, compact ? 28 : 30, Art.TEXT, Type.outfit(c, 800), 2);
            title.setGravity(Gravity.BOTTOM | Gravity.START);
            title.setLineSpacing(0, 0.92f);
            title.setShadowLayer(Art.dp(c, 8), 0, Art.dp(c, 1), 0x66000000);
            titleBox.addView(title, new FrameLayout.LayoutParams(-1, -1, Gravity.BOTTOM));
            logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_START);
            logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-1, -1, Gravity.BOTTOM | Gravity.START);
            ll.rightMargin = Art.dp(c, wide ? 40 : 60);
            titleBox.addView(logo, ll);
            // The logo's box (or the name's): the same height whichever shows, so nothing moves when a logo arrives.
            LinearLayout.LayoutParams tb = new LinearLayout.LayoutParams(-1, Art.dp(c, compact ? 58 : 68));
            tb.topMargin = Art.dp(c, 6);
            words.addView(titleBox, tb);

            LinearLayout factsRow = new LinearLayout(c);
            factsRow.setOrientation(LinearLayout.HORIZONTAL);
            factsRow.setGravity(Gravity.CENTER_VERTICAL);
            cert = Rows.text(c, 11, 0xFF1A1D24, Type.interSemi(c), 1);
            cert.setPadding(Art.dp(c, 6), Art.dp(c, 2), Art.dp(c, 6), Art.dp(c, 2));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.rightMargin = Art.dp(c, 8);
            factsRow.addView(cert, cl);
            facts = Rows.text(c, 13, Art.SOFT, Type.inter(c), 1);
            factsRow.addView(facts, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams fr = new LinearLayout.LayoutParams(-1, Art.dp(c, 20));
            fr.topMargin = Art.dp(c, 10);
            words.addView(factsRow, fr);

            progressRow = new LinearLayout(c);
            progressRow.setOrientation(LinearLayout.HORIZONTAL);
            progressRow.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout track = new FrameLayout(c);
            track.setBackground(Art.round(0x40FFFFFF, Art.dp(c, 2)));
            fill = new View(c);
            fill.setBackground(Art.round(Art.FILM, Art.dp(c, 2)));
            track.addView(fill, new FrameLayout.LayoutParams(0, -1));
            progressRow.addView(track, new LinearLayout.LayoutParams(Art.dp(c, 120), Art.dp(c, 4)));
            left = Rows.text(c, 12, Art.SOFT, Type.inter(c), 1);
            left.setPadding(Art.dp(c, 10), 0, 0, 0);
            progressRow.addView(left);
            LinearLayout.LayoutParams pr = new LinearLayout.LayoutParams(-1, Art.dp(c, 16));
            pr.topMargin = Art.dp(c, 8);
            words.addView(progressRow, pr);

            overview = Rows.text(c, 13, Art.DIM, Type.inter(c), 2);
            overview.setLineSpacing(Art.dp(c, 2), 1f);
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1, -2);
            ol.topMargin = Art.dp(c, 8);
            words.addView(overview, ol);

            LinearLayout buttons = new LinearLayout(c);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            play = button(true);
            more = button(false);
            buttons.addView(play, new LinearLayout.LayoutParams(-2, Art.dp(c, 44)));
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, Art.dp(c, 44));
            ml.leftMargin = Art.dp(c, 10);
            buttons.addView(more, ml);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = Art.dp(c, 14);
            words.addView(buttons, bl);

            int width = wide ? Math.round(c.getResources().getDisplayMetrics().widthPixels * 0.55f) : -1;
            page.addView(words, new FrameLayout.LayoutParams(width, -1, Gravity.BOTTOM | Gravity.START));
        }

        private TextView button(boolean main) {
            TextView b = Rows.text(c, 15, main ? 0xFF10121F : Art.TEXT, Type.outfit(c, 600), 1);
            b.setGravity(Gravity.CENTER);
            b.setPadding(Art.dp(c, main ? 18 : 16), 0, Art.dp(c, 20), 0);
            b.setBackground(main ? Art.round(0xFFFFFFFF, Art.dp(c, 22)) : Art.glass(c, 22));
            Drawable g = new Art.Glyph(main ? Art.Glyph.PLAY : Art.Glyph.INFO, main ? 0xFF10121F : Art.TEXT, Art.dp(c, main ? 14 : 16));
            g.setBounds(0, 0, g.getIntrinsicWidth(), g.getIntrinsicHeight());
            b.setCompoundDrawables(g, null, null, null);
            b.setCompoundDrawablePadding(Art.dp(c, 8));
            return b;
        }

        void bind(Page p) {
            FrameLayout page = (FrameLayout) itemView;
            if (p.loading) {
                page.setBackground(new Art.Shimmer(0));
                Rows.clear(image);
                Rows.clear(logo);
                words.setVisibility(View.INVISIBLE);
                return;
            }
            words.setVisibility(View.VISIBLE);
            page.setBackground(Art.placeholder(p.title, 0));
            int w = c.getResources().getDisplayMetrics().widthPixels;
            if (p.image != null) {
                try {
                    Glide.with(image).load(p.image).override(w, height).centerCrop()
                            .transition(DrawableTransitionOptions.withCrossFade(240)).into(image);
                } catch (RuntimeException ignored) {
                    // the screen went away
                }
            } else {
                Rows.clear(image);
            }
            kicker.setText(p.kicker.toUpperCase(java.util.Locale.UK));
            kicker.setTextColor(p.accent);
            kicker.setVisibility(p.kicker.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            title.setText(p.title);
            title.setVisibility(View.VISIBLE);
            logoFor = p.logo;
            if (p.logo != null) {
                String want = p.logo;
                try {
                    Glide.with(logo).load(want).override(Art.dp(c, 300), Art.dp(c, 84)).fitCenter()
                            .listener(new RequestListener<Drawable>() {
                                @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> t, boolean first) {
                                    return false;
                                }

                                @Override public boolean onResourceReady(@NonNull Drawable d, @NonNull Object model, Target<Drawable> t,
                                                                         @NonNull DataSource s, boolean first) {
                                    // The logo in, the name out (only if this page still wants that logo).
                                    if (want.equals(logoFor)) title.setVisibility(View.INVISIBLE);
                                    return false;
                                }
                            })
                            .transition(DrawableTransitionOptions.withCrossFade(200)).into(logo);
                } catch (RuntimeException ignored) {
                    // the screen went away
                }
            } else {
                Rows.clear(logo);
            }
            cert.setVisibility(p.cert.isEmpty() ? View.GONE : View.VISIBLE);
            if (!p.cert.isEmpty()) {
                cert.setText(p.cert);
                cert.setBackground(Art.round(certColour(p.cert), Art.dp(c, 4)));
            }
            facts.setText(p.facts);
            boolean resume = p.progress >= 0;
            progressRow.setVisibility(resume ? View.VISIBLE : View.GONE);
            if (resume) {
                ViewGroup.LayoutParams lp = fill.getLayoutParams();
                lp.width = Math.max(Art.dp(c, 4), Math.round(Art.dp(c, 120) * Math.min(1, p.progress)));
                fill.setLayoutParams(lp);
                fill.setBackground(Art.round(p.accent, Art.dp(c, 2)));
                left.setText(p.left);
            }
            overview.setText(p.overview);
            overview.setVisibility(p.overview.isEmpty() || resume || compact ? View.GONE : View.VISIBLE);
            play.setText(p.play);
            play.setVisibility(p.onPlay == null ? View.GONE : View.VISIBLE);
            play.setOnClickListener(p.onPlay == null ? null : v -> p.onPlay.run());
            more.setText(p.more);
            more.setVisibility(p.onMore == null ? View.GONE : View.VISIBLE);
            more.setOnClickListener(p.onMore == null ? null : v -> p.onMore.run());
            itemView.setOnClickListener(p.onMore != null ? v -> p.onMore.run() : p.onPlay != null ? v -> p.onPlay.run() : null);
            itemView.setContentDescription(p.title + (p.facts.isEmpty() ? "" : ", " + p.facts));
        }
    }

    /** The UK certificate's own colour, as the launcher draws it. Pure. */
    static int certColour(String cert) {
        String u = cert == null ? "" : cert.toUpperCase(java.util.Locale.UK);
        if (u.equals("U")) return 0xFF2BA84A;
        if (u.equals("PG")) return 0xFFF2B705;
        if (u.startsWith("12")) return 0xFFF07F1A;
        if (u.equals("15")) return 0xFFE6007E;
        if (u.equals("18") || u.equals("R18")) return 0xFFDA291C;
        return 0xFFD1D5DB;
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Holder(new FrameLayout(parent.getContext()));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int pos) {
            h.bind(pages.get(pos % pages.size()));
        }

        @Override public int getItemCount() {
            return pages.size() < 2 ? pages.size() : pages.size() * LOOPS;
        }
    }
}
