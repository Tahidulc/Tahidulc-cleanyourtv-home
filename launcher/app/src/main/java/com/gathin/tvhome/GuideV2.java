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
 * In Sky's own guide's look: its blue with soft circles, flat rows and programmes split by thin lines, the selected
 * programme lighter, each channel's name with its number at the right, and times as Sky writes them ("7.00pm").
 */
final class GuideV2 {
    private static final int ROWS = 7;
    private static final long WINDOW_MS = 2 * 60 * 60_000L, HALF_HOUR = 30 * 60_000L;
    private static final int GRID_X = 96, GRID_W = 1728, CH_W = 360, LINE_X = GRID_X + CH_W + 16;
    private static final int LINE_W = GRID_X + GRID_W - LINE_X;
    private static final String[] GROUPS = {"All channels", "Entertainment", "Sport", "News"};
    /** Sky's guide: its blue (lighter top left, deeper bottom right), and that blue where the picture fades into it. */
    private static final int SKY_TOP = 0xFF2A86E0, SKY_MID = 0xFF1564C6, SKY_DEEP = 0xFF0B47A3, SKY_PIC = 0xFF1A6FD2;
    /** The lines between rows and programmes, a row's band, the selected programme, the menu's chosen item. */
    private static final int LINE = 0x40FFFFFF, BAND = 0x1FFFFFFF, CHOSEN = 0x47FFFFFF, MENU = 0xFF0C3483;

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
        for (String[] ch : channels) if (ch[1].matches("(?i).*\\b(uhd|4k)\\b.*")) UHD.add(ch[0]);
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
        root.setBackground(new SkyGround());
        root.setClickable(true);
        parent.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        picA = pic();
        picB = pic();
        picB.setAlpha(0f);
        add(fade(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{SKY_PIC, 0x001A6FD2}), 820, 0, 560, 600);
        add(fade(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x001A6FD2, SKY_MID}), 820, 380, 1100, 222);
        TextView title = text(38, regular, Color.WHITE);
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
        // As Sky's guide writes the time: 7.11pm.
        clock.setFormat24Hour("h.mma");
        clock.setFormat12Hour("h.mma");
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
        infoTitle = text(50, regular, Color.WHITE);
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

        // Sky's grid: a header band ("Today" and the half hours) over flat rows, everything split by thin lines.
        View band = new View(c);
        band.setBackgroundColor(BAND);
        add(band, GRID_X, 490, GRID_W, 50);
        View under = new View(c);
        under.setBackgroundColor(LINE);
        add(under, GRID_X, 540, GRID_W, 1);
        View side = new View(c);
        side.setBackgroundColor(LINE);
        add(side, GRID_X + CH_W, 490, 1, 50 + ROWS * 60);
        TextView today = text(22, semi, Color.WHITE);
        today.setText("Today");
        add(today, GRID_X + 20, 503, 200, 30);
        for (int i = 0; i < 4; i++) {
            TextView hh = text(22, semi, Color.WHITE);
            add(hh, LINE_X + i * LINE_W / 4 + 8, 503, 170, 30);
            View tick = new View(c);
            tick.setBackgroundColor(LINE);
            add(tick, LINE_X + i * LINE_W / 4, 490, 1, 50);
            hours.add(hh);
        }
        nowPill = text(16, semi, MENU);
        nowPill.setGravity(Gravity.CENTER);
        nowPill.setBackground(fill(4, Color.WHITE));
        add(nowPill, LINE_X, 494, 84, 26);
        grid = new FrameLayout(c);
        grid.setClipChildren(true);
        add(grid, GRID_X, 541, GRID_W, ROWS * 60);
        TextView hint = text(18, regular, 0xB3FFFFFF);
        Keys.set(hint, "OK  Watch   ·   ◀ ▶  Earlier / later   ·   ▲ ▼  Channels   ·   BACK  Home");
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
        Keys.set(hint, "OK  Full screen");
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
                + Config.themeString(c, "iptv_pass", "") + "/" + HomeV2.previewStream(c, ch[1], ch[4]);
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
                    // The cover over TiviMate shows the channel's logo large: Sky's full-size picture, trimmed (the
                    // guide's own copy is list-sized), fetched first so it is always there.
                    new Thread(() -> {
                        Bitmap got = HomeV2.trim(ScoresTicker.cachedLogo(c, logoUrl(ch[0]), 512));
                        main.post(() -> AppSearchService.tune(c, "ar.tvplayer.tv", java.util.Collections.singletonList(ch[1]), got));
                    }).start();
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

    /** Times as Sky's guide writes them: 7.00pm, 12.30am. */
    private static final DateTimeFormatter SKY_TIME = DateTimeFormatter.ofPattern("h.mma", Locale.UK)
            .withZone(ZoneId.systemDefault());

    private static String sky(long ms) {
        return SKY_TIME.format(Instant.ofEpochMilli(ms)).toLowerCase(Locale.UK);
    }

    /** "30m", "1h 05m", as Sky gives a programme's length. */
    private static String length(long ms) {
        long m = Math.max(1, Math.round(ms / 60_000.0));
        return m >= 60 ? m / 60 + "h " + (m % 60 < 10 ? "0" : "") + m % 60 + "m" : m + "m";
    }

    private void draw() {
        DateTimeFormatter hm = SKY_TIME;
        for (int i = 0; i < chips.getChildCount(); i++) {
            TextView chip = (TextView) chips.getChildAt(i);
            boolean sel = i == group, focus = onChips && sel;
            // As Sky's menu: plain words, the chosen one in a deep blue box lit along its top and bottom edges.
            chip.setBackground(sel ? new MenuBox(focus) : null);
            chip.setTextColor(sel || focus ? Color.WHITE : 0xE6FFFFFF);
            chip.setTypeface(sel ? semi : regular);
        }
        for (int i = 0; i < hours.size(); i++) hours.get(i).setText(sky(windowStart + i * HALF_HOUR));
        long now = System.currentTimeMillis();
        boolean nowIn = now >= windowStart && now < windowStart + WINDOW_MS;
        nowPill.setVisibility(nowIn ? View.VISIBLE : View.GONE);
        nowPill.setText(sky(now));
        FrameLayout.LayoutParams np = (FrameLayout.LayoutParams) nowPill.getLayoutParams();
        np.leftMargin = px(LINE_X + LINE_W * (now - windowStart) / (float) WINDOW_MS - 42);
        nowPill.setLayoutParams(np);

        grid.removeAllViews();
        float perMs = px(LINE_W) / (float) WINDOW_MS;
        for (int r = 0; r < ROWS && top + r < list.size(); r++) {
            int i = top + r;
            String[] ch = list.get(i);
            boolean selRow = i == chan && !onChips;
            int y = px(r * 60);
            // The row: lighter across while it is the one chosen, a thin line under it.
            if (selRow) {
                View rowBand = new View(c);
                rowBand.setBackgroundColor(0x14FFFFFF);
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(px(GRID_W), px(60));
                bl.topMargin = y;
                grid.addView(rowBand, bl);
            }
            View rule = new View(c);
            rule.setBackgroundColor(LINE);
            FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(px(GRID_W), Math.max(1, px(1)));
            rl.topMargin = y + px(59);
            grid.addView(rule, rl);
            // The channel: its logo and name, its number at the right (Sky's "ITV+1   203").
            FrameLayout name = new FrameLayout(c);
            if (selRow) name.setBackgroundColor(0x1FFFFFFF);
            LinearLayout nl = new LinearLayout(c);
            nl.setOrientation(LinearLayout.HORIZONTAL);
            nl.setGravity(Gravity.CENTER_VERTICAL);
            nl.setPadding(px(16), 0, px(18), 0);
            ImageView logo = new ImageView(c);
            logo.setAdjustViewBounds(true);
            logo.setMaxHeight(px(26));
            logo.setMaxWidth(px(80));
            nl.addView(logo, new LinearLayout.LayoutParams(px(84), px(26)));
            image(logo, logoUrl(ch[0]), px(120));
            TextView n = text(22, selRow ? semi : regular, Color.WHITE);
            n.setText(ch[1]);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nlp.leftMargin = px(10);
            nl.addView(n, nlp);
            TextView num = text(22, selRow ? semi : regular, Color.WHITE);
            num.setText(ch[3].isEmpty() ? String.valueOf(i + 1) : ch[3]);
            num.setGravity(Gravity.END);
            LinearLayout.LayoutParams numl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            numl.leftMargin = px(10);
            nl.addView(num, numl);
            name.addView(nl, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(CH_W), px(59));
            lp.topMargin = y;
            grid.addView(name, lp);
            List<Show> l = shows.get(ch[0]);
            if (l == null || l.isEmpty()) {
                TextView none = cell("Loading…", false, false);
                FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px(LINE_W + 16), px(59));
                cl.leftMargin = px(CH_W + 1);
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
                // Programmes side by side, a thin line between them (the first one from the channel's line).
                int left = px(CH_W + 16) + Math.round((a - windowStart) * perMs);
                if (a == windowStart) left = px(CH_W + 1);
                int right = px(CH_W + 16) + Math.round((b - windowStart) * perMs);
                FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Math.max(px(6), right - left - Math.max(1, px(1))), px(59));
                cl.leftMargin = left;
                cl.topMargin = y;
                grid.addView(cell, cl);
                View split = new View(c);
                split.setBackgroundColor(LINE);
                FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(Math.max(1, px(1)), px(59));
                sl.leftMargin = right - Math.max(1, px(1));
                sl.topMargin = y;
                if (b < windowStart + WINDOW_MS) grid.addView(split, sl);
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
            infoTimes.setText(length(cur.end - cur.start) + "   ·   " + (cur.start <= now ? "Started at " : "Starts at ") + sky(cur.start));
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
        TextView t = text(22, sel ? semi : regular, Color.WHITE);
        t.setText(live ? "● " + title : title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(px(14), 0, px(10), 0);
        // Flat as Sky's: the chosen programme lighter, with a white line along its foot.
        if (sel) t.setBackground(new Chosen());
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

    /** The guide's channels that are UHD in the playlist: their logos say UHD. */
    private static final java.util.Set<String> UHD = new java.util.HashSet<>();

    private static String logoUrl(String sid) {
        return ScoresTicker.skyLogo(sid, UHD.contains(sid) ? "UHD" : null);
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
            if (url.contains("newchanlogos")) HomeV2.fitLogo(v, hit);
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
                    if (full != null && url.endsWith("#uhd")) full = ScoresTicker.uhdLogo(c, full);
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
                if (url.contains("newchanlogos")) HomeV2.fitLogo(v, done);
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

    // ---- Sky's look ------------------------------------------------------------------------------------------------

    /** The guide's ground: Sky's blue, lighter top left, with large soft circles drifting low and to the right. */
    private final class SkyGround extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private android.graphics.Shader shader;
        private android.graphics.Rect laid;

        @Override public void draw(@androidx.annotation.NonNull android.graphics.Canvas cv) {
            android.graphics.Rect r = getBounds();
            if (shader == null || !r.equals(laid)) {
                laid = new android.graphics.Rect(r);
                shader = new android.graphics.LinearGradient(r.left, r.top, r.right, r.bottom,
                        new int[]{SKY_TOP, SKY_MID, SKY_DEEP}, new float[]{0f, 0.45f, 1f}, android.graphics.Shader.TileMode.CLAMP);
            }
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setShader(shader);
            cv.drawRect(r, paint);
            paint.setShader(null);
            float w = r.width(), h = r.height();
            // Soft discs and rings, as behind Sky's guide.
            paint.setColor(0x0FFFFFFF);
            cv.drawCircle(w * 0.80f, h * 0.88f, h * 0.30f, paint);
            paint.setColor(0x0AFFFFFF);
            cv.drawCircle(w * 0.62f, h * 0.30f, h * 0.17f, paint);
            cv.drawCircle(w * 0.08f, h * 0.95f, h * 0.20f, paint);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(px(2));
            paint.setColor(0x14FFFFFF);
            cv.drawCircle(w * 0.93f, h * 0.62f, h * 0.22f, paint);
            cv.drawCircle(w * 0.47f, h * 0.12f, h * 0.09f, paint);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
    }

    /** The menu's chosen item: a deep blue box with a light line glowing along its top and bottom edges. */
    private final class MenuBox extends android.graphics.drawable.Drawable {
        private final boolean focus;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF box = new android.graphics.RectF();

        MenuBox(boolean focus) {
            this.focus = focus;
        }

        @Override public void draw(@androidx.annotation.NonNull android.graphics.Canvas cv) {
            android.graphics.Rect r = getBounds();
            float rad = px(6);
            box.set(r);
            paint.setShader(null);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(focus ? 0xFF103E9A : MENU);
            cv.drawRoundRect(box, rad, rad, paint);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(px(1.5f));
            paint.setColor(focus ? 0xCCFFFFFF : 0x66FFFFFF);
            cv.drawRoundRect(box, rad, rad, paint);
            // The glow: a short bright streak in the middle of the top and bottom edges, fading out to either side.
            paint.setStyle(android.graphics.Paint.Style.FILL);
            float cx = r.exactCenterX(), half = r.width() * 0.32f;
            paint.setShader(new android.graphics.LinearGradient(cx - half, 0, cx + half, 0,
                    new int[]{0x00FFFFFF, focus ? 0xFFFFFFFF : 0xB3FFFFFF, 0x00FFFFFF}, null, android.graphics.Shader.TileMode.CLAMP));
            float t = Math.max(1, px(2));
            cv.drawRect(cx - half, r.top, cx + half, r.top + t, paint);
            cv.drawRect(cx - half, r.bottom - t, cx + half, r.bottom, paint);
            paint.setShader(null);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** The chosen programme: lighter blue over the row, a white line along its foot. */
    private final class Chosen extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint();

        @Override public void draw(@androidx.annotation.NonNull android.graphics.Canvas cv) {
            android.graphics.Rect r = getBounds();
            paint.setColor(CHOSEN);
            cv.drawRect(r, paint);
            paint.setColor(Color.WHITE);
            cv.drawRect(r.left, r.bottom - Math.max(2, px(3)), r.right, r.bottom, paint);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
