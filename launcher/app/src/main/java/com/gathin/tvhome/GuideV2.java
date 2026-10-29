package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Launcher v2's TV guide: the TiviMate playlist's channels (the theme's "guide_channels", in the playlist's order and
 * groups) with what is on them from the public schedule behind Sky's online guide. Two hours across, seven channels
 * down; the selected programme's picture and details fill the top. OK puts TiviMate on the channel; Back closes.
 */
final class GuideV2 {
    private static final int ROWS = 7;
    private static final long WINDOW_MS = 2 * 60 * 60_000L, HALF_HOUR = 30 * 60_000L;
    private static final int GRID_X = 96, GRID_W = 1728, CH_W = 360, LINE_X = GRID_X + CH_W + 16;
    private static final int LINE_W = GRID_X + GRID_W - LINE_X;
    private static final String[] GROUPS = {"All channels", "Entertainment", "Sport", "News"};

    static final class Show {
        long start, end;
        String title, text, picture;
    }

    private final Context c;
    private final FrameLayout parent;
    private final float s;
    private final Typeface regular, semi;
    private final List<String[]> all;
    private final Runnable closed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService pics = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private final Map<String, List<Show>> shows = new HashMap<>();
    private long readAt;

    private FrameLayout root;
    private ImageView picA, picB;
    private boolean picFrontA = true;
    private String picShown;
    private ImageView infoLogo;
    private TextView infoChannel, infoTitle, infoTimes, infoLeft, infoText;
    private View infoBar, infoDone;
    private LinearLayout chips;
    private FrameLayout grid;
    private final List<TextView> hours = new ArrayList<>();
    private TextView nowPill;

    private List<String[]> list = new ArrayList<>();
    private int group, chan, top;
    private boolean onChips;
    /** The moment the selection sits on: Up and Down keep to it, Left and Right move it a programme at a time. */
    private long at, windowStart;

    GuideV2(Context c, FrameLayout parent, float s, Typeface regular, Typeface semi, List<String[]> channels, Runnable closed) {
        this.c = c;
        this.parent = parent;
        this.s = s;
        this.regular = regular;
        this.semi = semi;
        this.all = channels;
        this.closed = closed;
    }

    boolean isOpen() {
        return root != null && root.getVisibility() == View.VISIBLE;
    }

    void show() {
        if (root == null) build();
        long now = System.currentTimeMillis();
        windowStart = now - now % HALF_HOUR;
        at = now;
        group = 0;
        onChips = false;
        filter();
        chan = 0;
        top = 0;
        root.setAlpha(0f);
        root.setVisibility(View.VISIBLE);
        root.animate().alpha(1f).setDuration(250).setInterpolator(HomeV2.SKY).start();
        root.bringToFront();
        draw();
        if (System.currentTimeMillis() - readAt > 15 * 60_000L) read();
    }

    void hide() {
        stopPreview();
        if (!isOpen()) return;
        root.animate().alpha(0f).setDuration(200).withEndAction(() -> root.setVisibility(View.GONE)).start();
    }

    void close() {
        stopPreview();
        io.shutdownNow();
        pics.shutdownNow();
        cache.evictAll();
    }

    // ---- building --------------------------------------------------------------------------------------------------

    private void build() {
        root = new FrameLayout(c);
        root.setBackgroundColor(0xFF0F1240);
        root.setClickable(true);
        parent.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        picA = pic();
        picB = pic();
        picB.setAlpha(0f);
        add(fade(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF0F1240, 0x000F1240}), 820, 0, 560, 600);
        add(fade(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x000F1240, 0xFF0F1240}), 820, 380, 1100, 222);
        TextView title = text(38, semi, Color.WHITE);
        title.setText("TV Guide");
        add(title, 96, 44, 300, 50);
        chips = new LinearLayout(c);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (String g : GROUPS) {
            TextView chip = text(21, regular, Color.WHITE);
            chip.setText(g);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(px(22), 0, px(22), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(46));
            lp.rightMargin = px(10);
            chips.addView(chip, lp);
        }
        add(chips, 330, 42, 1100, 50);
        TextClock clock = new TextClock(c);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(40));
        clock.setTypeface(regular);
        clock.setTextColor(Color.WHITE);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        clp.topMargin = px(40);
        clp.rightMargin = px(96);
        root.addView(clock, clp);

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout ch = new LinearLayout(c);
        ch.setOrientation(LinearLayout.HORIZONTAL);
        ch.setGravity(Gravity.CENTER_VERTICAL);
        infoLogo = new ImageView(c);
        infoLogo.setAdjustViewBounds(true);
        infoLogo.setMaxHeight(px(34));
        infoLogo.setMaxWidth(px(220));
        ch.addView(infoLogo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(34)));
        infoChannel = text(21, regular, 0xD9FFFFFF);
        LinearLayout.LayoutParams icl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        icl.leftMargin = px(16);
        ch.addView(infoChannel, icl);
        info.addView(ch);
        infoTitle = text(54, semi, Color.WHITE);
        infoTitle.setMaxLines(1);
        infoTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams itl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itl.topMargin = px(22);
        info.addView(infoTitle, itl);
        LinearLayout times = new LinearLayout(c);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);
        infoTimes = text(21, regular, 0xD9FFFFFF);
        times.addView(infoTimes);
        FrameLayout bar = new FrameLayout(c);
        bar.setBackground(fill(3, 0x33FFFFFF));
        infoDone = new View(c);
        infoDone.setBackground(fill(3, Color.WHITE));
        bar.addView(infoDone, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        infoBar = bar;
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(220), px(6));
        bl.leftMargin = px(18);
        bl.rightMargin = px(18);
        times.addView(bar, bl);
        infoLeft = text(21, regular, 0xD9FFFFFF);
        times.addView(infoLeft);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = px(18);
        info.addView(times, tl);
        infoText = text(22, regular, 0xEBFFFFFF);
        infoText.setMaxLines(2);
        infoText.setEllipsize(TextUtils.TruncateAt.END);
        infoText.setLineSpacing(px(4), 1f);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        xl.topMargin = px(18);
        info.addView(infoText, xl);
        add(info, 96, 132, 960, 340);

        FrameLayout panel = new FrameLayout(c);
        GradientDrawable pb = fill(22, 0x2E0A0C32);
        pb.setStroke(px(1.5f), 0x24FFFFFF);
        panel.setBackground(pb);
        add(panel, 80, 486, 1760, 548);
        TextView today = text(20, semi, Color.WHITE);
        today.setText("Today");
        add(today, GRID_X + 24, 504, 200, 30);
        for (int i = 0; i < 4; i++) {
            TextView hh = text(20, regular, 0xD9FFFFFF);
            add(hh, LINE_X + i * LINE_W / 4 + 8, 504, 160, 30);
            View tick = new View(c);
            tick.setBackgroundColor(0x33FFFFFF);
            add(tick, LINE_X + i * LINE_W / 4, 500, 1, 32);
            hours.add(hh);
        }
        nowPill = text(17, semi, 0xFF1A1D24);
        nowPill.setGravity(Gravity.CENTER);
        nowPill.setBackground(fill(16, Color.WHITE));
        add(nowPill, LINE_X, 500, 64, 32);
        grid = new FrameLayout(c);
        grid.setClipChildren(true);
        add(grid, GRID_X, 546, GRID_W, ROWS * 60);
        TextView hint = text(18, regular, 0xB3FFFFFF);
        hint.setText("OK  Watch   ·   ◀ ▶  Earlier / later   ·   ▲ ▼  Channels   ·   BACK  Home");
        hint.setGravity(Gravity.CENTER);
        add(hint, 0, 1040, 1920, 30);
        root.setVisibility(View.GONE);
    }

    private ImageView pic() {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        add(v, 820, 0, 1100, 600);
        return v;
    }

    // ---- data ------------------------------------------------------------------------------------------------------

    private void filter() {
        list = new ArrayList<>();
        for (String[] ch : all) {
            String g = ch[2];
            boolean in = group == 0 || group == 1 && g.equals("Entertainment") || group == 2 && g.contains("Sport") || group == 3 && g.equals("News");
            if (in) list.add(ch);
        }
    }

    private void read() {
        readAt = System.currentTimeMillis();
        List<String[]> want = new ArrayList<>(all);
        io.execute(() -> {
            long now = System.currentTimeMillis();
            for (int from = 0; from < want.size(); from += 20) {
                StringBuilder ids = new StringBuilder();
                for (int i = from; i < Math.min(want.size(), from + 20); i++) {
                    if (ids.length() > 0) ids.append(',');
                    ids.append(want.get(i)[0]);
                }
                Map<String, List<Show>> got = new HashMap<>();
                for (int day = 0; day < 2; day++) {
                    String date = Instant.ofEpochMilli(now).plusSeconds(day * 86_400L).atZone(ZoneOffset.UTC).toLocalDate()
                            .format(DateTimeFormatter.BASIC_ISO_DATE);
                    try {
                        JSONArray sch = new JSONObject(HomeV2.get("https://awk.epgsky.com/hawk/linear/schedule/" + date + "/" + ids)).optJSONArray("schedule");
                        for (int i = 0; sch != null && i < sch.length(); i++) {
                            JSONObject one = sch.getJSONObject(i);
                            List<Show> l = got.computeIfAbsent(one.optString("sid"), k -> new ArrayList<>());
                            JSONArray ev = one.optJSONArray("events");
                            for (int k = 0; ev != null && k < ev.length(); k++) {
                                JSONObject e = ev.getJSONObject(k);
                                Show sh = new Show();
                                sh.start = e.optLong("st") * 1000L;
                                sh.end = sh.start + e.optLong("d") * 1000L;
                                if (sh.end < now - HALF_HOUR) continue;
                                sh.title = e.optString("t");
                                sh.text = e.optString("sy", "");
                                String uuid = e.optString("programmeuuid", "");
                                sh.picture = uuid.isEmpty() ? null : "https://images.metadata.sky.com/pd-image/" + uuid + "/16-9/1280";
                                l.add(sh);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                for (List<Show> l : got.values()) l.sort((x, y) -> Long.compare(x.start, y.start));
                main.post(() -> {
                    shows.putAll(got);
                    if (isOpen()) draw();
                });
            }
        });
    }

    private Show current() {
        if (list.isEmpty()) return null;
        List<Show> l = shows.get(list.get(chan)[0]);
        if (l == null) return null;
        for (Show sh : l) if (sh.start <= at && sh.end > at) return sh;
        return null;
    }

    // ---- the corner preview ----------------------------------------------------------------------------------------

    private FrameLayout pip;
    private android.view.TextureView video;
    private android.media.MediaPlayer player;
    private String[] previewing;
    private TextView pipTitle;
    private ImageView pipLogo;
    private View pipWait;

    /** A channel plays in the corner when the playlist's login is in the theme and the channel has its stream id. */
    private boolean canPreview(String[] ch) {
        return ch.length > 4 && !ch[4].isEmpty() && !Config.themeString(c, "iptv_server", "").isEmpty();
    }

    private void buildPip() {
        pip = new FrameLayout(c);
        pip.setClipToOutline(true);
        pip.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(22)); }
        });
        pip.setBackgroundColor(Color.BLACK);
        pip.setElevation(px(20));
        video = new android.view.TextureView(c);
        pip.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pipWait = new View(c);
        pipWait.setBackgroundColor(0xFF000000);
        pip.addView(pipWait, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xD9000000}));
        pip.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView live = text(15, semi, Color.WHITE);
        live.setText("LIVE");
        live.setLetterSpacing(0.07f);
        live.setPadding(px(9), px(3), px(9), px(2));
        live.setBackground(fill(6, 0xFFFF3B4A));
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.leftMargin = px(18);
        ll.topMargin = px(16);
        pip.addView(live, ll);
        TextView hint = text(17, regular, Color.WHITE);
        hint.setText("OK  Full screen");
        hint.setPadding(px(14), px(5), px(14), px(4));
        hint.setBackground(fill(18, 0x8C000000));
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        hl.rightMargin = px(16);
        hl.topMargin = px(14);
        pip.addView(hint, hl);
        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        pipLogo = new ImageView(c);
        pipLogo.setAdjustViewBounds(true);
        pipLogo.setMaxHeight(px(28));
        pipLogo.setMaxWidth(px(160));
        meta.addView(pipLogo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(28)));
        pipTitle = text(22, semi, Color.WHITE);
        pipTitle.setSingleLine(true);
        pipTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = px(14);
        meta.addView(pipTitle, tl);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        ml.leftMargin = px(20);
        ml.rightMargin = px(20);
        ml.bottomMargin = px(16);
        pip.addView(meta, ml);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(px(22));
        ring.setStroke(px(2), 0xD9FFFFFF);
        pip.setForeground(ring);
        add(pip, 1192, 126, 632, 356);
        pip.setVisibility(View.GONE);
    }

    private void preview(String[] ch) {
        if (pip == null) buildPip();
        stopPlayer();
        previewing = ch;
        Show cur = current();
        pipTitle.setText(cur != null ? cur.title.replaceFirst("^(?i)live:? ", "") : ch[1]);
        image(pipLogo, logoUrl(ch[0]), px(160));
        pipWait.setAlpha(1f);
        if (pip.getVisibility() != View.VISIBLE) {
            pip.setVisibility(View.VISIBLE);
            pip.setAlpha(0f);
            pip.setScaleX(0.6f);
            pip.setScaleY(0.6f);
            pip.setPivotX(px(632));
            pip.setPivotY(px(356));
            pip.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(350).setInterpolator(HomeV2.SKY).start();
        }
        String base = Config.themeString(c, "iptv_server", "") + "/live/" + Config.themeString(c, "iptv_user", "") + "/"
                + Config.themeString(c, "iptv_pass", "") + "/" + ch[4];
        play(base, false);
    }

    /** Plays the stream into the corner: the plain transport stream (quickest to start), HLS if this TV's player turns that down. */
    private void play(String base, boolean hls) {
        android.graphics.SurfaceTexture st = video.getSurfaceTexture();
        if (st == null) {
            video.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture s, int w, int h) { if (previewing != null) play(base, hls); }
                @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture s, int w, int h) { }
                @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture s) { stopPlayer(); return true; }
                @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture s) { }
            });
            return;
        }
        android.media.MediaPlayer mp = new android.media.MediaPlayer();
        player = mp;
        try {
            mp.setSurface(new android.view.Surface(st));
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("User-Agent", "TiviMate/5.1.6 (Android 14)");
            mp.setDataSource(c, android.net.Uri.parse(base + (hls ? ".m3u8" : ".ts")), headers);
            // With sound, as the channel will be in TiviMate.
            mp.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build());
            mp.setOnPreparedListener(m -> {
                if (player != m) return;
                m.start();
                float vw = m.getVideoWidth(), vh = m.getVideoHeight();
                if (vw > 0 && vh > 0) {
                    android.graphics.Matrix mx = new android.graphics.Matrix();
                    float scale = Math.max(video.getWidth() / vw, video.getHeight() / vh);
                    mx.setScale(vw * scale / video.getWidth(), vh * scale / video.getHeight(), video.getWidth() / 2f, video.getHeight() / 2f);
                    video.setTransform(mx);
                }
            });
            mp.setOnInfoListener((m, what, extra) -> {
                if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && player == m) pipWait.animate().alpha(0f).setDuration(250).start();
                return false;
            });
            mp.setOnErrorListener((m, what, extra) -> {
                if (player != m) return true;
                stopPlayer();
                if (!hls && previewing != null) main.post(() -> play(base, true));
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            stopPlayer();
            if (!hls) play(base, true);
        }
    }

    private void stopPlayer() {
        android.media.MediaPlayer mp = player;
        player = null;
        if (mp == null) return;
        try { mp.stop(); } catch (Exception ignored) { }
        mp.release();
    }

    private void stopPreview() {
        previewing = null;
        stopPlayer();
        if (pip != null && pip.getVisibility() == View.VISIBLE) {
            pip.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(200).withEndAction(() -> pip.setVisibility(View.GONE)).start();
        }
    }

    // ---- keys ------------------------------------------------------------------------------------------------------

    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (previewing != null) { stopPreview(); return true; }
                hide();
                closed.run();
            }
            return true;
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                if (onChips) {
                    onChips = false;
                } else if (!list.isEmpty()) {
                    String[] ch = list.get(chan);
                    // OK once: the channel live in the corner. OK again on the same channel: full screen in TiviMate.
                    if (previewing != ch && canPreview(ch)) {
                        preview(ch);
                        return true;
                    }
                    hide();
                    closed.run();
                    GoalWatchService.onAppOpened(c, "ar.tvplayer.tv");
                    Bitmap logo = cache.get(logoUrl(ch[0]));
                    if (logo != null) AppSearchService.tune(c, "ar.tvplayer.tv", java.util.Collections.singletonList(ch[1]), logo);
                    else io.execute(() -> {
                        // The logo first, so the cover over TiviMate always shows it.
                        Bitmap got = ScoresTicker.cachedLogo(c, logoUrl(ch[0]), 128);
                        main.post(() -> AppSearchService.tune(c, "ar.tvplayer.tv", java.util.Collections.singletonList(ch[1]), got));
                    });
                    return true;
                }
                draw();
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (onChips) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && group > 0) group--;
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && group < GROUPS.length - 1) group++;
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) onChips = false;
            else { draw(); return true; }
            if (code != KeyEvent.KEYCODE_DPAD_DOWN) {
                filter();
                chan = 0;
                top = 0;
            }
            draw();
            return true;
        }
        Show cur = current();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (chan > 0) chan--; else onChips = true;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (chan < list.size() - 1) chan++;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                at = cur != null ? cur.end + 1 : at + HALF_HOUR;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT: {
                long now = System.currentTimeMillis();
                long back = cur != null ? cur.start - 1 : at - HALF_HOUR;
                at = Math.max(now, back);
                break;
            }
            default:
                return false;
        }
        if (chan < top) top = chan;
        if (chan >= top + ROWS) top = chan - ROWS + 1;
        // The window follows the selection along the day, half an hour at a time.
        while (at >= windowStart + WINDOW_MS - HALF_HOUR / 2) windowStart += HALF_HOUR;
        long now = System.currentTimeMillis();
        while (at < windowStart && windowStart > now - now % HALF_HOUR) windowStart -= HALF_HOUR;
        draw();
        return true;
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    private void draw() {
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
        for (int i = 0; i < chips.getChildCount(); i++) {
            TextView chip = (TextView) chips.getChildAt(i);
            boolean sel = i == group, focus = onChips && sel;
            GradientDrawable b = fill(23, focus ? Color.WHITE : sel ? 0x40FFFFFF : 0x14FFFFFF);
            b.setStroke(px(1.5f), focus ? Color.WHITE : 0x40FFFFFF);
            chip.setBackground(b);
            chip.setTextColor(focus ? 0xFF1A1D24 : Color.WHITE);
            chip.setTypeface(sel ? semi : regular);
        }
        for (int i = 0; i < hours.size(); i++) hours.get(i).setText(hm.format(Instant.ofEpochMilli(windowStart + i * HALF_HOUR)));
        long now = System.currentTimeMillis();
        boolean nowIn = now >= windowStart && now < windowStart + WINDOW_MS;
        nowPill.setVisibility(nowIn ? View.VISIBLE : View.GONE);
        nowPill.setText(hm.format(Instant.ofEpochMilli(now)));
        FrameLayout.LayoutParams np = (FrameLayout.LayoutParams) nowPill.getLayoutParams();
        np.leftMargin = px(LINE_X + LINE_W * (now - windowStart) / (float) WINDOW_MS - 32);
        nowPill.setLayoutParams(np);

        grid.removeAllViews();
        float perMs = px(LINE_W) / (float) WINDOW_MS;
        for (int r = 0; r < ROWS && top + r < list.size(); r++) {
            int i = top + r;
            String[] ch = list.get(i);
            boolean selRow = i == chan && !onChips;
            int y = px(r * 60);
            FrameLayout name = new FrameLayout(c);
            name.setBackground(fill(12, selRow ? 0x33FFFFFF : 0x14FFFFFF));
            LinearLayout nl = new LinearLayout(c);
            nl.setOrientation(LinearLayout.HORIZONTAL);
            nl.setGravity(Gravity.CENTER_VERTICAL);
            nl.setPadding(px(14), 0, px(12), 0);
            TextView num = text(17, regular, 0xB3FFFFFF);
            num.setText(ch[3].isEmpty() ? String.valueOf(i + 1) : ch[3]);
            nl.addView(num, new LinearLayout.LayoutParams(px(46), ViewGroup.LayoutParams.WRAP_CONTENT));
            ImageView logo = new ImageView(c);
            logo.setAdjustViewBounds(true);
            logo.setMaxHeight(px(26));
            logo.setMaxWidth(px(92));
            nl.addView(logo, new LinearLayout.LayoutParams(px(96), px(26)));
            image(logo, logoUrl(ch[0]), px(120));
            TextView n = text(19, selRow ? semi : regular, Color.WHITE);
            n.setText(ch[1]);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nlp.leftMargin = px(10);
            nl.addView(n, nlp);
            name.addView(nl, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(CH_W), px(52));
            lp.topMargin = y;
            grid.addView(name, lp);
            List<Show> l = shows.get(ch[0]);
            if (l == null || l.isEmpty()) {
                TextView none = cell("Loading…", false, false);
                FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px(LINE_W), px(52));
                cl.leftMargin = px(CH_W + 16);
                cl.topMargin = y;
                grid.addView(none, cl);
                continue;
            }
            for (Show sh : l) {
                long a = Math.max(sh.start, windowStart), b = Math.min(sh.end, windowStart + WINDOW_MS);
                if (b <= a) continue;
                boolean sel = selRow && sh.start <= at && sh.end > at;
                boolean live = sh.title.toLowerCase(Locale.ROOT).startsWith("live");
                TextView cell = cell(sh.title.replaceFirst("^(?i)live:? ", ""), sel, live);
                FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Math.max(px(6), Math.round((b - a) * perMs) - px(6)), px(52));
                cl.leftMargin = px(CH_W + 16) + Math.round((a - windowStart) * perMs);
                cl.topMargin = y;
                grid.addView(cell, cl);
            }
        }
        Show cur = current();
        if (!list.isEmpty()) {
            String[] ch = list.get(chan);
            image(infoLogo, logoUrl(ch[0]), px(220));
            infoChannel.setText((ch[3].isEmpty() ? "" : ch[3] + " · ") + ch[1]);
        }
        if (cur != null) {
            infoTitle.setText(cur.title.replaceFirst("^(?i)live:? ", ""));
            infoTimes.setText(hm.format(Instant.ofEpochMilli(cur.start)) + " – " + hm.format(Instant.ofEpochMilli(cur.end)));
            boolean on = cur.start <= now;
            infoBar.setVisibility(on ? View.VISIBLE : View.GONE);
            if (on) {
                FrameLayout.LayoutParams dl = (FrameLayout.LayoutParams) infoDone.getLayoutParams();
                dl.width = Math.round(px(220) * Math.min(1f, (now - cur.start) / (float) Math.max(1, cur.end - cur.start)));
                infoDone.setLayoutParams(dl);
            }
            long mins = Math.max(1, ((on ? cur.end : cur.start) - now) / 60_000L);
            infoLeft.setText(on ? mins + " min left" : "Starts in " + (mins >= 60 ? mins / 60 + " hr " + mins % 60 + " min" : mins + " min"));
            infoText.setText(cur.text);
            picture(cur.picture);
        } else {
            infoTitle.setText(list.isEmpty() ? "No channels" : "Loading the guide…");
            infoTimes.setText("");
            infoLeft.setText("");
            infoBar.setVisibility(View.GONE);
            infoText.setText("");
        }
    }

    private TextView cell(String title, boolean sel, boolean live) {
        TextView t = text(20, sel ? semi : regular, sel ? 0xFF1A1D24 : Color.WHITE);
        t.setText(live ? "● " + title : title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(px(14), 0, px(10), 0);
        GradientDrawable b = fill(10, sel ? Color.WHITE : 0x1AFFFFFF);
        if (sel) b.setStroke(px(2), Color.WHITE);
        t.setBackground(b);
        if (live && !sel) t.setTextColor(0xFFFFFFFF);
        return t;
    }

    private void picture(String url) {
        if (url == null || url.equals(picShown)) return;
        picShown = url;
        ImageView in = picFrontA ? picB : picA, out = picFrontA ? picA : picB;
        picFrontA = !picFrontA;
        image(in, url, px(1100), () -> {
            in.animate().alpha(1f).setDuration(400).setInterpolator(HomeV2.SKY).start();
            out.animate().alpha(0f).setDuration(400).setInterpolator(HomeV2.SKY).start();
        });
    }

    private static String logoUrl(String sid) {
        return "https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/600/600/skychb" + sid + ".png";
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private void image(ImageView v, String url, int width) {
        image(v, url, width, null);
    }

    private void image(ImageView v, String url, int width, Runnable then) {
        v.setTag(R.id.v2_url, url);
        Bitmap hit = cache.get(url);
        if (hit != null) {
            v.setImageBitmap(hit);
            if (then != null) then.run();
            return;
        }
        v.setImageDrawable(null);
        pics.execute(() -> {
            Bitmap b = null;
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(6000);
                con.setReadTimeout(10000);
                try (InputStream in = con.getInputStream()) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    Bitmap full = BitmapFactory.decodeStream(in, null, o);
                    if (url.contains("newchanlogos")) full = HomeV2.trim(full);
                    if (full != null && full.getWidth() > width * 2) {
                        b = Bitmap.createScaledBitmap(full, width, Math.round(full.getHeight() * width / (float) full.getWidth()), true);
                        full.recycle();
                    } else {
                        b = full;
                    }
                } finally {
                    con.disconnect();
                }
            } catch (Exception | OutOfMemoryError ignored) {
            }
            if (b == null) return;
            Bitmap done = b;
            cache.put(url, done);
            main.post(() -> {
                if (!url.equals(v.getTag(R.id.v2_url))) return;
                v.setImageBitmap(done);
                if (then != null) then.run();
            });
        });
    }

    private int px(float design) {
        return Math.round(design * s);
    }

    private void add(View v, int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(w), px(h));
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        root.addView(v, lp);
    }

    private View fade(GradientDrawable.Orientation o, int[] colours) {
        View v = new View(c);
        v.setBackground(new GradientDrawable(o, colours));
        return v;
    }

    private TextView text(float size, Typeface face, int colour) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTypeface(face);
        t.getPaint().setSubpixelText(true);
        t.setTextColor(colour);
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable fill(float radius, int colour) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(radius));
        g.setColor(colour);
        return g;
    }
}
