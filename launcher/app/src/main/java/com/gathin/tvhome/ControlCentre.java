package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Control Centre (launcher v2): the one button at the top right of Home opens it, a panel just under it in the
 * launcher's navy glass (after Apple TV's): the cameras (the latest alarm's picture, OK opening the smart home panel's
 * live view), then the controls two across, each an icon, a name and what it is now (the Wi-Fi network, the VPN on or
 * off). OK on one does what its own button did and closes the panel; Back closes it.
 */
final class ControlCentre {
    /** One control: its name, icon, what it is now (may be null), what OK does, and whether it is lit (VPN on). */
    static final class Item {
        final String name;
        final int icon;
        final Supplier<String> state;
        final Supplier<Boolean> lit;
        final Runnable ok;
        /** The section it is in, under its own heading after the controls ("THE OTHER TV"); null: the controls. */
        String section;

        Item(String name, int icon, Supplier<String> state, Supplier<Boolean> lit, Runnable ok) {
            this.name = name;
            this.icon = icon;
            this.state = state;
            this.lit = lit;
            this.ok = ok;
        }

        Item in(String heading) {
            section = heading;
            return this;
        }
    }

    private static final int NAVY = 0xFF10157E;
    private final HomeV2 h;
    private final Context c;
    private final List<Item> items;
    /** The camera tile: {name, open}; null without cameras. */
    private final String camera;
    private final Runnable openCamera;
    /** The doorbell's app opened; its tile shows beside the camera's while the app (CloudEdge) is on the TV. */
    private final Runnable openDoorbell;
    private final Typeface heavy, semi, regular;
    private FrameLayout overlay;
    private boolean open;
    private final List<View> tiles = new ArrayList<>();
    private final List<Runnable> acts = new ArrayList<>();
    /** Each tile's place: {row, column, columns it spans}. */
    private final List<int[]> at = new ArrayList<>();
    private int focus;

    ControlCentre(HomeV2 home, List<Item> items, String camera, Runnable openCamera, Runnable openDoorbell) {
        h = home;
        c = home.a;
        this.items = items;
        this.camera = camera;
        this.openCamera = openCamera;
        this.openDoorbell = openDoorbell;
        heavy = c.getResources().getFont(R.font.outfit_extrabold);
        semi = c.getResources().getFont(R.font.inter_semibold);
        regular = c.getResources().getFont(R.font.inter_regular);
    }

    boolean isOpen() {
        return open;
    }

    /** The card: as big and as placed as the weather card (WeatherPanel), so everything is in sight at once. */
    private static final int CARD_X = 200, CARD_Y = 110, CARD_W = 1520, CARD_H = 860;

    /**
     * The Control Centre as a card in the middle of the screen, the size of the weather card: on the left the cameras,
     * what is live and today's prayer times; on the right the controls three across and the other TV.
     */
    void show() {
        if (open) return;
        open = true;
        tiles.clear();
        acts.clear();
        at.clear();
        overlay = new FrameLayout(c);
        overlay.setClickable(true);
        overlay.setBackgroundColor(0xB3050816);
        FrameLayout card = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1A2296, 0xFF121980, 0xFF0B0F5A});
        g.setCornerRadius(h.px(44));
        g.setStroke(Math.max(1, h.px(1.5f)), 0x40FFFFFF);
        card.setBackground(g);
        card.setClipToOutline(true);
        card.setOutlineProvider(h.round(44));
        card.setClipChildren(false);

        TextView title = text(40, heavy, Color.WHITE);
        title.setText("Control Centre");
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2);
        tl.leftMargin = px(56);
        tl.topMargin = px(36);
        card.addView(title, tl);
        TextView hint = text(17, regular, 0x99FFFFFF);
        Keys.set(hint, "OK to open  ·  Back to close");
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        hl.rightMargin = px(56);
        hl.topMargin = px(50);
        card.addView(hint, hl);

        LinearLayout cols = new LinearLayout(c);
        cols.setClipChildren(false);
        LinearLayout left = new LinearLayout(c), right = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        right.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(false);
        right.setClipChildren(false);
        cols.addView(left, new LinearLayout.LayoutParams(px(560), -2));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f);
        rp.leftMargin = px(48);
        cols.addView(right, rp);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, -2);
        cl.leftMargin = px(56);
        cl.rightMargin = px(56);
        cl.topMargin = px(96);
        card.addView(cols, cl);

        // Left: the cameras (the doorbell beside the camera), what is live, today's prayer times.
        boolean cam = camera != null && openCamera != null;
        boolean bell = openDoorbell != null && DoorbellAlert.installed(c);
        int camTiles = 0;
        if (cam || bell) {
            left.addView(heading("CAMERAS"), headParams());
            LinearLayout camRow = new LinearLayout(c);
            camRow.setClipChildren(false);
            if (cam) {
                camRow.addView(cameraTile(), new LinearLayout.LayoutParams(0, -1, 1f));
                acts.add(() -> { hide(); openCamera.run(); });
                camTiles++;
            }
            if (bell) {
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -1, 1f);
                if (cam) bp.leftMargin = px(14);
                camRow.addView(doorbellTile(), bp);
                acts.add(() -> { hide(); openDoorbell.run(); });
                camTiles++;
            }
            // Smaller when much is live, so the prayer times still fit under it.
            int live = liveLines().size();
            boolean prayer = Prayer.heading(c) != null;
            int camH = live >= 2 ? (prayer ? 150 : 180) : prayer ? 190 : cam && bell ? 200 : 290;
            left.addView(camRow, new LinearLayout.LayoutParams(-1, px(camH)));
        }
        liveSection(left);
        prayerSection(left);

        // Right: the controls, then each section of its own under its heading (the other TV), three across.
        List<String> sections = new ArrayList<>();
        sections.add(null);
        for (Item it : items) if (it.section != null && !sections.contains(it.section)) sections.add(it.section);
        for (String sec : sections) {
            List<Item> group = new ArrayList<>();
            for (Item it : items) if (java.util.Objects.equals(it.section, sec)) group.add(it);
            if (group.isEmpty()) continue;
            right.addView(heading(sec == null ? "CONTROLS" : sec), headParams());
            LinearLayout line = null;
            for (int i = 0; i < group.size(); i++) {
                Item it = group.get(i);
                if (i % ACROSS == 0) {
                    line = new LinearLayout(c);
                    line.setClipChildren(false);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(96));
                    if (i > 0) lp.topMargin = px(16);
                    right.addView(line, lp);
                }
                View t = tile(it);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -1, 1f);
                if (i % ACROSS > 0) tp.leftMargin = px(16);
                line.addView(t, tp);
                tiles.add(t);
                acts.add(() -> { hide(); it.ok.run(); });
            }
            for (int k = group.size() % ACROSS; k > 0 && k < ACROSS && line != null; k++) {
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -1, 1f);
                sp.leftMargin = px(16);
                line.addView(new View(c), sp);
            }
        }

        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(h.px(CARD_W), h.px(CARD_H));
        bl.leftMargin = h.px(CARD_X);
        bl.topMargin = h.px(CARD_Y);
        overlay.addView(card, bl);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).setInterpolator(Fluid.EASE).start();
        // Out of the top bar's button, as an iPhone's Control Centre: the card grows from it on a spring, its two halves
        // arriving a moment after one another.
        Fluid.in(card, h.controlButton());
        if (card instanceof ViewGroup) Fluid.cascade((ViewGroup) card, 90, 45, h.px(18));
        focus = Math.min(camTiles, Math.max(0, tiles.size() - 1));
        paint();
    }

    /** The controls three across. */
    private static final int ACROSS = 3;

    void hide() {
        if (!open) return;
        open = false;
        View o = overlay;
        overlay = null;
        // Back into the button it came out of.
        if (o instanceof ViewGroup && ((ViewGroup) o).getChildCount() > 0) Fluid.out(((ViewGroup) o).getChildAt(((ViewGroup) o).getChildCount() - 1), h.controlButton(), null);
        o.animate().alpha(0f).setDuration(220).setInterpolator(Fluid.EASE_IN).withEndAction(() -> h.root.removeView(o)).start();
        h.paint();
    }

    /**
     * Live now: an order on its way, the drive home and the car, as the phone passed them on lately (SmartPanel keeps
     * them; DeliveryLink brings them). Only when there is something; words only.
     */
    private void liveSection(LinearLayout panel) {
        List<String[]> lines = liveLines();
        if (lines.isEmpty()) return;
        liveRows(panel, lines);
    }

    /** What is live now: {icon, first line, second line} for the order, the drive home and the car. */
    private List<String[]> liveLines() {
        List<String[]> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        org.json.JSONObject d = SmartPanel.delivery;
        if (d != null && now - SmartPanel.deliveryAt < 3 * 3_600_000L && d.optInt("stage", 0) < 4) {
            String from = d.optString("from", "").trim(), status = d.optString("status", "").trim(), eta = d.optString("eta", "").trim();
            String app = d.optString("app", "Delivery").trim();
            lines.add(new String[]{"🛵", app + (from.isEmpty() ? "" : " · " + from),
                    status + (eta.isEmpty() ? "" : (status.isEmpty() ? "" : " · ") + "arriving " + eta)});
        }
        org.json.JSONObject t = SmartPanel.trip;
        if (t != null && now - SmartPanel.tripAt < 90 * 60_000L && t.optInt("minutes", -1) >= 0) {
            int m = t.optInt("minutes");
            String home = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(new java.util.Date(SmartPanel.tripAt + m * 60_000L));
            lines.add(new String[]{"🚗", "On the way home", m + " min · home about " + home});
        }
        org.json.JSONObject car = SmartPanel.car;
        if (car != null && now - SmartPanel.carAt < 12 * 3_600_000L) {
            int battery = car.optInt("battery", -1);
            String title = car.optString("title", "Tesla").trim(), status = car.optString("status", "").trim();
            lines.add(new String[]{"🔋", (title.isEmpty() ? "Tesla" : title) + (battery >= 0 ? " · " + battery + "%" : ""), status});
        }
        return lines;
    }

    private void liveRows(LinearLayout panel, List<String[]> lines) {
        panel.addView(heading("LIVE NOW"), headParams());
        for (int i = 0; i < lines.size(); i++) {
            String[] l = lines.get(i);
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(18), px(10), px(18), px(10));
            row.setBackground(fill(18, 0x1FFFFFFF));
            TextView icon = text(26, regular, Color.WHITE);
            icon.setText(l[0]);
            row.addView(icon);
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView a = text(19, semi, Color.WHITE);
            a.setText(l[1]);
            a.setSingleLine(true);
            a.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(a);
            if (!l[2].isEmpty()) {
                TextView b = text(16, regular, 0xFFC9CEF2);
                b.setText(l[2]);
                b.setSingleLine(true);
                b.setEllipsize(android.text.TextUtils.TruncateAt.END);
                words.addView(b);
            }
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
            wl.leftMargin = px(14);
            row.addView(words, wl);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) rl.topMargin = px(10);
            panel.addView(row, rl);
        }
    }

    /**
     * Prayer times (Prayer, when the theme asks): the mosque and the Islamic date as the heading, then the day on the
     * sun's path (PrayerArc) with the next prayer over it and the five jamā'ah times under it. Words only: the remote
     * passes over it.
     */
    private void prayerSection(LinearLayout panel) {
        String head = Prayer.heading(c);
        if (head == null) return;
        PrayerArc arc = new PrayerArc(c, px(100) / 100f, false, heavy, semi, regular);
        if (arc.empty()) return;
        panel.addView(heading(head), headParams());
        panel.addView(arc, new LinearLayout.LayoutParams(-1, px(258)));
    }

    private TextView heading(String s) {
        TextView t = text(15, semi, 0xB3FFFFFF);
        t.setText(s);
        t.setLetterSpacing(0.16f);
        return t;
    }

    private LinearLayout.LayoutParams headParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = px(22);
        lp.bottomMargin = px(12);
        return lp;
    }

    /** The cameras: the latest alarm's picture when there has been one this session, the camera's name over it. */
    private View cameraTile() {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(fill(24, 0xFF0A0E46));
        f.setClipToOutline(true);
        f.setOutlineProvider(round(24));
        Object[] shot = CameraAlert.lastShot(camera);
        Bitmap pic = shot != null && shot[0] instanceof Bitmap ? (Bitmap) shot[0] : null;
        if (pic != null) {
            ImageView i = new ImageView(c);
            i.setImageBitmap(pic);
            i.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(i, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ImageView i = new ImageView(c);
            i.setImageResource(R.drawable.ic_camera);
            i.setColorFilter(0x66FFFFFF);
            f.addView(i, new FrameLayout.LayoutParams(px(96), px(96), Gravity.CENTER));
        }
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xB3000000}));
        f.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout name = new LinearLayout(c);
        name.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(c);
        icon.setImageResource(R.drawable.ic_camera);
        icon.setColorFilter(Color.WHITE);
        name.addView(icon, new LinearLayout.LayoutParams(px(28), px(28)));
        TextView t = text(21, semi, Color.WHITE);
        t.setText(camera);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = px(10);
        name.addView(t, tl);
        if (shot == null) {
            TextView s = text(16, regular, 0xBFFFFFFF);
            s.setText("  ·  live view");
            name.addView(s);
        }
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        nl.leftMargin = px(18);
        nl.bottomMargin = px(14);
        f.addView(name, nl);
        tiles.add(f);
        return f;
    }

    /** The doorbell: the last ring's picture this session (else a bell), "Doorbell" and when it last rang over it. */
    private View doorbellTile() {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(fill(24, 0xFF0A0E46));
        f.setClipToOutline(true);
        f.setOutlineProvider(round(24));
        Object[] ring = DoorbellAlert.lastRing();
        Bitmap pic = ring != null && ring[0] instanceof Bitmap ? (Bitmap) ring[0] : null;
        if (pic != null) {
            ImageView i = new ImageView(c);
            i.setImageBitmap(pic);
            i.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(i, new FrameLayout.LayoutParams(-1, -1));
        } else {
            f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF3A2A0E, 0xFF14102A, 0xFF0A0E46}));
            TextView b = text(54, regular, Color.WHITE);
            b.setText("🔔");
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
            bl.bottomMargin = px(20);
            f.addView(b, bl);
        }
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xB3000000}));
        f.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout name = new LinearLayout(c);
        name.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(21, semi, Color.WHITE);
        t.setText("Doorbell");
        name.addView(t);
        TextView s = text(15, regular, 0xBFFFFFFF);
        s.setText(ring == null ? "CloudEdge" : "Rang at " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK)
                .format(new java.util.Date((Long) ring[1])));
        name.addView(s);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        nl.leftMargin = px(18);
        nl.bottomMargin = px(14);
        f.addView(name, nl);
        tiles.add(f);
        return f;
    }

    private View tile(Item it) {
        LinearLayout t = new LinearLayout(c);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(px(18), 0, px(14), 0);
        ImageView i = new ImageView(c);
        i.setImageResource(it.icon);
        i.setTag("icon");
        t.addView(i, new LinearLayout.LayoutParams(px(32), px(32)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView n = text(20, semi, Color.WHITE);
        n.setText(it.name);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        n.setTag("name");
        words.addView(n);
        String st = it.state == null ? null : it.state.get();
        if (st != null && !st.isEmpty()) {
            TextView s = text(15, regular, 0xB3FFFFFF);
            s.setText(st);
            s.setSingleLine(true);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            s.setTag("state");
            words.addView(s);
        }
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.leftMargin = px(14);
        t.addView(words, wl);
        t.setTag(it);
        return t;
    }

    /** The panel a size down from Home's own measures, so it ends well clear of the foot of the screen. */
    private static final float K = 1f;

    private int px(float v) {
        return h.px(v * K);
    }

    private TextView text(float size, android.graphics.Typeface face, int colour) {
        return h.text(size * K, face, colour);
    }

    private GradientDrawable fill(float radius, int colour) {
        return h.fill(radius * K, colour);
    }

    private android.view.ViewOutlineProvider round(float radius) {
        return h.round(radius * K);
    }

    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled() && focus < acts.size()) acts.get(focus).run();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int dx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        int dy = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        if (dx == 0 && dy == 0) return true;
        int to = nearest(dx, dy);
        if (to >= 0) { focus = to; paint(); }
        return true;
    }

    /**
     * The tile the arrow points to: of those whose middle lies that way, the nearest, a sideways offset counting
     * double (so Down goes to the tile under this one, and Right from the cameras to the controls beside them).
     */
    private int nearest(int dx, int dy) {
        if (focus >= tiles.size()) return -1;
        float[] here = middle(tiles.get(focus));
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < tiles.size(); i++) {
            if (i == focus) continue;
            float[] m = middle(tiles.get(i));
            float along = dx != 0 ? (m[0] - here[0]) * dx : (m[1] - here[1]) * dy;
            float across = dx != 0 ? Math.abs(m[1] - here[1]) : Math.abs(m[0] - here[0]);
            if (along <= px(8)) continue;
            float score = along + 2f * across;
            if (score < bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    private static float[] middle(View v) {
        int[] xy = new int[2];
        v.getLocationOnScreen(xy);
        return new float[]{xy[0] + v.getWidth() / 2f, xy[1] + v.getHeight() / 2f};
    }

    private void paint() {
        for (int i = 0; i < tiles.size(); i++) {
            View t = tiles.get(i);
            boolean on = i == focus;
            Item it = t.getTag() instanceof Item ? (Item) t.getTag() : null;
            boolean lit = it != null && it.lit != null && Boolean.TRUE.equals(it.lit.get());
            if (it == null) {
                // The camera: a white ring when focused, along the tile's own rounded edge (the stroke sits half its
                // width inside the edge, so its corners are rounded by that much less to follow it).
                int w = px(4);
                GradientDrawable ring = new GradientDrawable();
                ring.setCornerRadius(Math.max(0, px(24) - w / 2f));
                if (on) ring.setStroke(w, Color.WHITE);
                ((FrameLayout) t).setForeground(ring);
                t.animate().scaleX(on ? 1.02f : 1f).scaleY(on ? 1.02f : 1f).setDuration(140).start();
                continue;
            }
            GradientDrawable bg = fill(22, on ? Color.WHITE : lit ? 0x669B5CFF : 0x33050838);
            if (!on) bg.setStroke(Math.max(1, px(1)), lit ? 0xB3C4A8FF : 0x26FFFFFF);
            t.setBackground(bg);
            int ink = on ? NAVY : Color.WHITE;
            ((ImageView) t.findViewWithTag("icon")).setColorFilter(on ? NAVY : lit ? 0xFFD9C4FF : Color.WHITE);
            ((TextView) t.findViewWithTag("name")).setTextColor(ink);
            View s = t.findViewWithTag("state");
            if (s != null) ((TextView) s).setTextColor(on ? 0xB310157E : lit ? 0xFFD9C4FF : 0xB3FFFFFF);
            Fluid.spring(t, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, on ? 1.05f : 1f, Fluid.STIFF_SMALL, 0.8f);
            Fluid.spring(t, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, on ? 1.05f : 1f, Fluid.STIFF_SMALL, 0.8f);
        }
    }
}
