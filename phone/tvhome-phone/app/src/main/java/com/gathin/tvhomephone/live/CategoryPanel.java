package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhomephone.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Live TV's categories down the side, as the family asked: the button at the guide's top left slides this panel in from
 * the left over a scrim, listing the categories in the app's own order (Favourites, All, Recent and Sport, then the
 * playlist's own: favourite ones first with a star, hidden ones left out, the same set the guide had as chips). A tap
 * shows that one in the guide and slides the panel away; the scrim, Back or a swipe to the left put it away too. Hold
 * one of the playlist's own for Favourite, Hide or all the categories. On a tablet on its side it can stay docked open
 * beside the guide (docked: no scrim, the button tucks it away). One RecyclerView of rows, so a playlist with hundreds of
 * categories slides as smoothly as one with ten.
 */
@SuppressLint("ViewConstructor")
final class CategoryPanel extends FrameLayout {
    /** What the panel asks of the guide. */
    interface Listener {
        /** A category tapped: show it in the guide. */
        void onPick(String key);

        /** A playlist's own category held: its menu (Favourite, Hide, all the categories). */
        void onHold(View row, String key);

        /** "All categories…": the sheet to hide, show and make favourites. */
        void onManage();

        /** The panel has gone (or the docked one tucked away), however it was put away. */
        void onShut();
    }

    /** How long the slide takes. */
    static final long SLIDE_MS = 250;
    /** The panel's widest, and the share of a narrow screen it takes at most. */
    static final int WIDTH_DP = 320, DOCKED_DP = 264;
    static final float MOST_OF = 0.84f;

    private final boolean docked;
    private final View scrim;
    private final LinearLayout sheet;
    private final RecyclerView list;
    private final Rows rows = new Rows();
    private final ImageButton close;
    private final int slop, minFling;
    private final Interpolator in = new DecelerateInterpolator(1.8f), out = new DecelerateInterpolator(1.2f);
    private Listener listener;
    private boolean open;
    private VelocityTracker tracker;
    private float downX, downY;
    private boolean dragging;

    /** docked: beside the guide on a tablet on its side (no scrim); otherwise over the page, with its scrim. */
    CategoryPanel(Context c, boolean docked) {
        super(c);
        this.docked = docked;
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();

        scrim = new View(c);
        scrim.setBackgroundColor(0x99000000);
        scrim.setAlpha(0f);
        scrim.setContentDescription(c.getString(R.string.cats_close));
        scrim.setOnClickListener(v -> close(true));
        scrim.setVisibility(docked ? GONE : VISIBLE);
        addView(scrim, new LayoutParams(-1, -1));

        sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        // 07's frosted panel: tinted glass with its bright hairline, deep enough to read over the guide (no live blur).
        // Over the page: rounded where it meets the scrim. Docked: a rounded card standing off the guide's edge.
        TvLook.Pane ground = docked ? TvLook.mat(c, 24) : TvLook.deep(c, 24);
        float r = Ui.dp(c, 24);
        if (!docked) ground.corners(0, r, r, 0);
        sheet.setBackground(ground);
        sheet.setElevation(Ui.dp(c, docked ? 4 : 16));

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(c, 20), Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 6));
        TextView title = TvLook.text(c, c.getString(R.string.groups_title), 22, Ui.TEXT, true);
        title.setLetterSpacing(-0.02f);
        title.setSingleLine(true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        close = Ui.icon(c, R.drawable.live_ic_close, R.string.cats_close);
        close.setOnClickListener(v -> close(true));
        close.setVisibility(docked ? GONE : VISIBLE);
        head.addView(close, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        sheet.addView(head, new LinearLayout.LayoutParams(-1, -2));

        list = new RecyclerView(c);
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setItemAnimator(null);
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 16));
        list.setAdapter(rows);
        sheet.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        LayoutParams sp = new LayoutParams(docked ? -1 : Ui.dp(c, WIDTH_DP), -1, Gravity.START);
        if (docked) sp.setMargins(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 8));
        addView(sheet, sp);
        if (!docked) setVisibility(GONE);
    }

    void setListener(Listener l) { listener = l; }

    boolean isOpen() { return open; }

    boolean docked() { return docked; }

    /** The categories, as the guide has them ({words, key, "own"} for a playlist's own), and the one showing. */
    @SuppressLint("NotifyDataSetChanged")
    void set(List<String[]> items, String chosen) {
        rows.items = new ArrayList<>(items);
        rows.chosen = chosen;
        rows.notifyDataSetChanged();
    }

    /** Another category showing (its row lit), the list where it was. */
    void choose(String key) {
        rows.chosen = key;
        rows.notifyItemRangeChanged(0, rows.getItemCount());
    }

    /**
     * Room at the panel's side and top (a navigation bar at the left, the status bar) and at its list's foot (the tab
     * bar, the navigation bar). Docked, the guide's frame has the side and the top already.
     */
    void fit(int left, int top, int bottom) {
        sheet.setPadding(docked ? 0 : left, docked ? 0 : top, 0, 0);
        list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), bottom + Ui.dp(getContext(), 16));
    }

    /** Pure: the sheet's width over a page this wide: WIDTH_DP at most, and no more than MOST_OF of it. */
    static int sheetWidth(int page, int widest) {
        return Math.max(0, Math.min(widest, Math.round(page * MOST_OF)));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (!docked) {
            LayoutParams lp = (LayoutParams) sheet.getLayoutParams();
            int w = sheetWidth(MeasureSpec.getSize(widthSpec), Ui.dp(getContext(), WIDTH_DP));
            if (lp.width != w) lp.width = w;
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    // ---- sliding in and out ---------------------------------------------------------------------------------------

    /** Slides in from the left over its scrim (at once without animate); the chosen category in sight. */
    void open(boolean animate) {
        if (docked) {
            open = true;
            return;
        }
        boolean was = open;
        open = true;
        setVisibility(VISIBLE);
        scrollToChosen();
        float from = was ? sheet.getTranslationX() : -width();
        sheet.animate().cancel();
        scrim.animate().cancel();
        if (!animate) {
            sheet.setTranslationX(0);
            scrim.setAlpha(1f);
            return;
        }
        sheet.setTranslationX(from);
        sheet.animate().translationX(0).setDuration(SLIDE_MS).setInterpolator(in).withLayer().start();
        scrim.animate().alpha(1f).setDuration(SLIDE_MS).start();
    }

    /** Slides away to the left, the scrim fading (at once without animate). */
    void close(boolean animate) {
        if (docked) {
            if (open) {
                open = false;
                if (listener != null) listener.onShut();
            }
            return;
        }
        if (!open) return;
        open = false;
        sheet.animate().cancel();
        scrim.animate().cancel();
        Runnable gone = () -> {
            if (open) return;
            setVisibility(GONE);
            sheet.setTranslationX(0);
        };
        if (!animate) {
            scrim.setAlpha(0f);
            gone.run();
        } else {
            sheet.animate().translationX(-width()).setDuration(SLIDE_MS).setInterpolator(out).withLayer().withEndAction(gone).start();
            scrim.animate().alpha(0f).setDuration(SLIDE_MS).start();
        }
        if (listener != null) listener.onShut();
    }

    private float width() {
        int w = sheet.getWidth();
        return w > 0 ? w : sheetWidth(getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels,
                Ui.dp(getContext(), WIDTH_DP));
    }

    /** The sheet itself (the guide slides beside it when it is docked). */
    View sheet() { return sheet; }

    /** The list from its top when the one showing is in sight there; further down, that one a little under the top. */
    private void scrollToChosen() {
        LinearLayoutManager lm = (LinearLayoutManager) list.getLayoutManager();
        if (lm == null) return;
        Context c = getContext();
        int at = rows.indexOf(rows.chosen);
        int room = list.getHeight() > 0 ? list.getHeight()
                : (getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels) - Ui.dp(c, 140);
        lm.scrollToPositionWithOffset(firstShown(at, Ui.dp(c, 54), room), at > 0 && !inSight(at, Ui.dp(c, 54), room) ? Ui.dp(c, 96) : 0);
    }

    /** Pure: whether the row at (rows of height row) shows whole in room from the list's top, with some to spare. */
    static boolean inSight(int at, int row, int room) {
        return at < 0 || (at + 1) * row + row <= room;
    }

    /** Pure: the row the list opens on: its first while the one showing is in sight from there, else that one. */
    static int firstShown(int at, int row, int room) {
        return inSight(at, row, room) ? 0 : at;
    }

    // ---- a swipe to the left puts it away ---------------------------------------------------------------------------

    /** Pure: whether a drag of dx (to the left: below 0) let go at velocity vx closes a panel this wide. */
    static boolean shuts(float dx, float vx, int width, float minFling) {
        if (vx <= -minFling) return true;
        if (vx >= minFling) return false;
        return -dx > width / 3f;
    }

    /** Pure: whether a move from the press is the panel's own swipe away (sideways, to the left, past the slop). */
    static boolean swipes(float dx, float dy, int slop) {
        return dx < -slop && Math.abs(dx) > Math.abs(dy) * 1.2f;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (docked || !open) return super.onInterceptTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                if (tracker == null) tracker = VelocityTracker.obtain();
                else tracker.clear();
                tracker.addMovement(e);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (tracker != null) tracker.addMovement(e);
                if (!dragging && downX < sheet.getRight() + slop && swipes(e.getX() - downX, e.getY() - downY, slop)) {
                    dragging = true;
                    sheet.animate().cancel();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                return dragging;
            default:
                return false;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!dragging) return super.onTouchEvent(e);
        if (tracker != null) tracker.addMovement(e);
        float dx = Math.min(0, e.getX() - downX);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                sheet.setTranslationX(dx);
                scrim.setAlpha(Math.max(0f, 1f + dx / Math.max(1f, width())));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                float vx = 0;
                if (tracker != null) {
                    tracker.computeCurrentVelocity(1000);
                    vx = tracker.getXVelocity();
                }
                if (e.getActionMasked() == MotionEvent.ACTION_UP && shuts(dx, vx, Math.round(width()), minFling)) close(true);
                else open(true);
                return true;
            default:
                return true;
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (tracker != null) {
            tracker.recycle();
            tracker = null;
        }
        super.onDetachedFromWindow();
    }

    // ---- the rows -----------------------------------------------------------------------------------------------------

    /** The list's kinds of row: a category, the heading over the playlist's own, and "All categories…". */
    private static final int ROW = 0, HEAD = 1, MANAGE = 2;

    /** The guide's own choices (no flag, an icon of their own) and the playlist's categories after them. */
    private final class Rows extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<String[]> items = new ArrayList<>();
        String chosen;

        int indexOf(String key) {
            for (int i = 0; i < items.size(); i++) if (items.get(i)[1].equals(key)) return position(i);
            return -1;
        }

        /** The heading over the playlist's own sits before its first; "All categories…" after the last. */
        private int firstOwn() {
            for (int i = 0; i < items.size(); i++) if (items.get(i).length > 2) return i;
            return -1;
        }

        private int position(int item) {
            int f = firstOwn();
            return f >= 0 && item >= f ? item + 1 : item;
        }

        private int item(int position) {
            int f = firstOwn();
            return f >= 0 && position > f ? position - 1 : position;
        }

        @Override public int getItemCount() {
            return items.size() + (firstOwn() >= 0 ? 2 : 1);
        }

        @Override public int getItemViewType(int position) {
            if (position == getItemCount() - 1) return MANAGE;
            int f = firstOwn();
            return f >= 0 && position == f ? HEAD : ROW;
        }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            if (type == HEAD) {
                TextView t = TvLook.text(c, c.getString(R.string.cats_playlist), 11, TvLook.L3, true);
                t.setLetterSpacing(0.12f);
                t.setPadding(Ui.dp(c, 12), Ui.dp(c, 14), 0, Ui.dp(c, 6));
                t.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerView.ViewHolder(t) {};
            }
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
            row.setMinimumHeight(Ui.dp(c, 52));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.dp(c, 2);
            row.setLayoutParams(lp);
            ImageView icon = new ImageView(c);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20));
            ip.rightMargin = Ui.dp(c, 14);
            row.addView(icon, ip);
            TextView words = TvLook.text(c, "", 15.5f, Ui.TEXT, false);
            words.setMaxLines(2);
            words.setEllipsize(TextUtils.TruncateAt.END);
            words.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
            row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
            row.setTag(new View[]{icon, words});
            return new RecyclerView.ViewHolder(row) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            int type = getItemViewType(position);
            if (type == HEAD) return;
            Context c = h.itemView.getContext();
            View[] v = (View[]) h.itemView.getTag();
            ImageView icon = (ImageView) v[0];
            TextView words = (TextView) v[1];
            if (type == MANAGE) {
                words.setText(R.string.groups_manage);
                words.setTextColor(Ui.SOFT);
                words.setTypeface(android.graphics.Typeface.DEFAULT);
                icon.setVisibility(VISIBLE);
                icon.setImageResource(R.drawable.live_ic_edit);
                icon.setImageTintList(ColorStateList.valueOf(Ui.SOFT));
                h.itemView.setBackground(look(c, false));
                h.itemView.setSelected(false);
                h.itemView.setContentDescription(c.getString(R.string.groups_manage));
                h.itemView.setOnClickListener(x -> {
                    if (listener != null) listener.onManage();
                });
                h.itemView.setOnLongClickListener(null);
                return;
            }
            String[] it = items.get(item(position));
            boolean own = it.length > 2, on = it[1].equals(chosen);
            // A playlist's own category with its country's flag where it names one; the guide's own with an icon.
            words.setText(own ? GuideLook.chip(it[1], it[0]) : it[0]);
            // 07's list: the chosen one white with dark writing, the rest in a soft white.
            words.setTextColor(on ? TvLook.INK : 0xE6FFFFFF);
            words.setTypeface(on ? TvLook.semi(c) : TvLook.inter(c));
            int glyph = glyph(it[1]);
            icon.setVisibility(own ? GONE : VISIBLE);
            if (!own) {
                icon.setImageResource(glyph);
                icon.setImageTintList(ColorStateList.valueOf(Library.FAVOURITES.equals(it[1]) ? (on ? 0xFFB07A00 : Ui.STAR) : on ? TvLook.INK : Ui.SOFT));
            }
            h.itemView.setBackground(look(c, on));
            h.itemView.setSelected(on);
            h.itemView.setContentDescription(on ? c.getString(R.string.cats_chosen, it[0]) : it[0]);
            h.itemView.setOnClickListener(x -> {
                if (listener != null) listener.onPick(it[1]);
            });
            h.itemView.setOnLongClickListener(own ? x -> {
                if (listener != null) listener.onHold(x, it[1]);
                return true;
            } : null);
        }
    }

    /** The guide's own choices' icons: the star, the list, the clock going back, the ball. */
    static int glyph(String key) {
        if (Library.FAVOURITES.equals(key)) return R.drawable.live_ic_star;
        if (Library.RECENT.equals(key)) return R.drawable.live_ic_replay;
        if (SportGuide.GROUP.equals(key)) return R.drawable.sports_ic_ball;
        return R.drawable.live_ic_list;
    }

    /** A row's ground: the chosen one white (07's chosen row), the rest clear, each with a ripple. */
    private static RippleDrawable look(Context c, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.dp(c, 14));
        g.setColor(on ? 0xF0FFFFFF : 0x00000000);
        return new RippleDrawable(ColorStateList.valueOf(on ? 0x22000000 : 0x33FFFFFF), g, Ui.round(c, Ui.TEXT, 14));
    }

    // ---- what goes in it ------------------------------------------------------------------------------------------------

    /**
     * Pure: the categories in the app's own order, as the chips had them: Favourites, All, Recent and Sport ({words,
     * key}), then the playlist's own ({words, key, "own"}), favourite ones first with a star, the hidden ones left out.
     */
    static List<String[]> items(String favourites, String all, String recent, String sport, List<String> playlist,
                                Set<String> hidden, List<String> favs) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{favourites, Library.FAVOURITES});
        out.add(new String[]{all, Library.ALL});
        out.add(new String[]{recent, Library.RECENT});
        out.add(new String[]{sport, SportGuide.GROUP});
        for (String g : Groups.order(playlist, hidden, favs)) out.add(new String[]{favs.contains(g) ? "★ " + g : g, g, "own"});
        return out;
    }

    /** Pure: the words for the category showing (the button's), from the list; the key itself when not in it. */
    static String words(List<String[]> items, String key) {
        for (String[] it : items) if (it[1].equals(key)) return it.length > 2 ? GuideLook.chip(it[1], it[0]) : it[0];
        return key == null ? "" : key;
    }

    /**
     * Pure: whether the panel docks beside the guide: a tablet on its side with room for it, the guide and the
     * picture's pane (sideDp) beside one another, the guide keeping at least guideDp.
     */
    static boolean docks(boolean tablet, boolean sideways, int screenWidthDp, int sideDp, int guideDp) {
        return tablet && sideways && screenWidthDp - DOCKED_DP - sideDp >= guideDp;
    }
}
