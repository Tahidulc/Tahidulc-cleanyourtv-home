package com.gathin.tvhomephone.live;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A film's or episode's subtitles in its player: the ones the playlist's stream carries (its own tracks, in the family's
 * languages when subtitles are on), or a file found on OpenSubtitles when it has none of its own, drawn here over the
 * picture at the moment the film is at, moved earlier or later as the timing is nudged (no reload, so the playlist's
 * one connection is left alone). On or off is kept for every film; the file found, and its timing, for that film.
 */
@OptIn(markerClass = UnstableApi.class)
final class VodSubtitles {
    /** Off (only a film's forced lines, its foreign parts, show), the film's own, or the file from OpenSubtitles. */
    private static final int OFF = 0, OWN = 1, FOUND = 2;

    private final AppCompatActivity a;
    private final ExoPlayer player;
    private final PlayerView view;
    /** The film's key (its player's), and what is known of it: name, TMDB id, series, season, episode. */
    private final String film, name;
    private final int tmdb, season, episode;
    private final boolean tv;
    private final Runnable changed;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();

    /** The hint that a key would find subtitles, once while the app is open. */
    private static boolean hinted;
    private int mode = OFF;
    /** Whether the film's own tracks have been looked at once it started (to find one when it has none). */
    private boolean decided;
    /** The file from OpenSubtitles showing: what it is, its lines' starts and ends (µs) and the lines themselves. */
    private Subtitles.Kept found;
    private long[] starts, ends;
    private List<List<Cue>> lines;
    private int[] shown;
    private boolean released;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (released || mode != FOUND || starts == null) return;
            long at = (player.getCurrentPosition() - (found == null ? 0 : found.offsetMs)) * 1000L;
            int[] on = Subtitles.active(starts, ends, at);
            if (!Arrays.equals(on, shown)) {
                shown = on;
                List<Cue> cues = new ArrayList<>();
                for (int i : on) cues.addAll(lines.get(i));
                SubtitleView sv = view.getSubtitleView();
                if (sv != null) sv.setCues(together(cues));
            }
            h.postDelayed(this, player.isPlaying() ? 100 : 250);
        }
    };

    /**
     * Lines on at once with no place of their own (most SubRip files) put one above the other in one block: drawn
     * separately they would each go to the foot of the picture, over one another. Lines placed by the file stay put.
     */
    static List<Cue> together(List<Cue> cues) {
        if (cues.size() < 2) return cues;
        for (Cue q : cues) if (q.text == null || q.line != Cue.DIMEN_UNSET || q.position != Cue.DIMEN_UNSET) return cues;
        SpannableStringBuilder s = new SpannableStringBuilder();
        for (Cue q : cues) {
            if (s.length() > 0) s.append('\n');
            s.append(q.text);
        }
        List<Cue> one = new ArrayList<>();
        one.add(cues.get(0).buildUpon().setText(s).build());
        return one;
    }

    VodSubtitles(AppCompatActivity a, ExoPlayer player, PlayerView view, String film, JSONObject meta, String title, Runnable changed) {
        this.a = a;
        this.player = player;
        this.view = view;
        this.film = film;
        String n = meta.optString("name");
        this.name = n.isEmpty() ? (title == null ? "" : title) : n;
        this.tmdb = meta.optInt("tmdb");
        this.tv = meta.optBoolean("tv") || meta.optInt("e") > 0;
        this.season = meta.optInt("s");
        this.episode = meta.optInt("e");
        this.changed = changed;
        player.addListener(new Player.Listener() {
            @Override public void onTracksChanged(@NonNull Tracks tracks) {
                decide(tracks);
            }

            @Override public void onCues(@NonNull CueGroup cues) {
                // The player view cleared them (its own text off): drawn again at the next tick.
                if (mode == FOUND) shown = null;
            }
        });
    }

    /** As the film starts: the look, and on or off as last time (the file found for it straight away). */
    void start() {
        applyLook();
        if (!Subtitles.on(a)) {
            own(false);
            mode = OFF;
            return;
        }
        List<Subtitles.Kept> kept = Subtitles.kept(a, film);
        if (!kept.isEmpty() && "os".equals(Subtitles.last(a, film))) {
            show(kept.get(0), false);
            decided = true;
        } else {
            own(true);
            mode = OWN;
        }
    }

    void release() {
        released = true;
        h.removeCallbacksAndMessages(null);
        work.shutdownNow();
    }

    /** Whether the stream carries subtitles of its own (the information bar's CC). */
    boolean hasOwn() { return !ownTracks().isEmpty(); }

    boolean isOn() { return mode != OFF; }

    // ---- the film's own -------------------------------------------------------------------------------------------

    /** The film's own lines in the family's languages (forced ones only when off), the file found put away. */
    private void own(boolean wanted) {
        hideFound();
        TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                // A track a film's file marks as its default isn't put on by that alone: only forced ones show when off.
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setSelectUndeterminedTextLanguage(false);
        List<String> langs = Subtitles.languages(a);
        b.setPreferredTextLanguages(wanted ? langs.toArray(new String[0]) : new String[0]);
        player.setTrackSelectionParameters(b.build());
    }

    private static final class Own {
        final Tracks.Group group;
        final int index;
        final Format format;

        Own(Tracks.Group group, int index) {
            this.group = group;
            this.index = index;
            this.format = group.getTrackFormat(index);
        }

        boolean selected() { return group.isTrackSelected(index); }

        boolean forced() { return (format.selectionFlags & C.SELECTION_FLAG_FORCED) != 0; }
    }

    private List<Own> ownTracks() {
        List<Own> out = new ArrayList<>();
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) if (g.isTrackSupported(i)) out.add(new Own(g, i));
        }
        return out;
    }

    /** A track's name: its language (or its own label), with SDH and Forced when it says so. */
    private static String ownName(Own t, int n) {
        Format f = t.format;
        String l = Subtitles.languageName(f.language);
        String label = f.label == null ? "" : f.label.trim();
        if (l.isEmpty()) l = label.isEmpty() ? "Subtitles " + n : label;
        else if (!label.isEmpty() && !label.equalsIgnoreCase(l)) l += " (" + label + ")";
        if ((f.roleFlags & (C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND | C.ROLE_FLAG_CAPTION)) != 0) l += " · SDH";
        if (t.forced()) l += " · Forced";
        return l;
    }

    /**
     * Once the film's tracks are known, with subtitles on: when none of its own came on in the family's languages, the
     * file kept for it, or (when that's allowed) the best one OpenSubtitles has.
     */
    private void decide(Tracks tracks) {
        if (decided || released || mode != OWN || tracks.getGroups().isEmpty()) return;
        boolean any = false;
        for (Tracks.Group g : tracks.getGroups()) any |= g.getType() == C.TRACK_TYPE_VIDEO || g.getType() == C.TRACK_TYPE_AUDIO;
        if (!any) return;
        decided = true;
        changed.run();
        for (Own t : ownTracks()) if (t.selected() && !t.forced()) return;
        List<Subtitles.Kept> kept = Subtitles.kept(a, film);
        if (!kept.isEmpty()) {
            show(kept.get(0), false);
            return;
        }
        if (!Subtitles.autoFind(a)) return;
        if (!OpenSubtitles.hasKey(a)) {
            if (!hinted) toast("No " + languageWords() + " subtitles in this one: add an OpenSubtitles key in Settings to find some");
            hinted = true;
            return;
        }
        find(true);
    }

    private String languageWords() {
        List<String> l = Subtitles.languages(a);
        return Subtitles.languageName(l.get(0));
    }

    // ---- the file found -------------------------------------------------------------------------------------------

    /** That file's lines over the picture (read on the side), the film's own put off. */
    private void show(Subtitles.Kept k, boolean say) {
        work.execute(() -> {
            try {
                byte[] data = Files.readAllBytes(new File(k.path).toPath());
                Format f = new Format.Builder().setSampleMimeType(Subtitles.mime(data)).build();
                DefaultSubtitleParserFactory pf = new DefaultSubtitleParserFactory();
                if (!pf.supportsFormat(f)) throw new IOException("kind");
                SubtitleParser p = pf.create(f);
                List<CuesWithTiming> all = new ArrayList<>();
                p.parse(data, SubtitleParser.OutputOptions.allCues(), all::add);
                all.sort((x, y) -> Long.compare(x.startTimeUs, y.startTimeUs));
                long[] s = new long[all.size()], e = new long[all.size()];
                List<List<Cue>> l = new ArrayList<>();
                for (int i = 0; i < all.size(); i++) {
                    CuesWithTiming c = all.get(i);
                    s[i] = c.startTimeUs;
                    e[i] = c.durationUs == C.TIME_UNSET ? -1 : c.endTimeUs;
                    l.add(c.cues);
                }
                if (s.length == 0) throw new IOException("empty");
                h.post(() -> {
                    if (released) return;
                    TrackSelectionParameters ps = player.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build();
                    player.setTrackSelectionParameters(ps);
                    found = k;
                    starts = s;
                    ends = e;
                    lines = l;
                    shown = null;
                    mode = FOUND;
                    Subtitles.setOn(a, true);
                    Subtitles.keep(a, film, k);
                    h.removeCallbacks(tick);
                    tick.run();
                    changed.run();
                    if (say) toast(Subtitles.languageName(k.lang) + " subtitles from OpenSubtitles");
                });
            } catch (IOException | RuntimeException ex) {
                h.post(() -> {
                    if (!released) toast("Those subtitles wouldn't open");
                });
            }
        });
    }

    private void hideFound() {
        h.removeCallbacks(tick);
        starts = ends = null;
        lines = null;
        found = null;
        shown = null;
        SubtitleView sv = view.getSubtitleView();
        if (sv != null) sv.setCues(new ArrayList<>());
    }

    /**
     * Looks on OpenSubtitles in the family's languages (every language when all): by itself, the best one put on; asked
     * for, the list to choose from.
     */
    private void find(boolean byItself) {
        find(byItself, false);
    }

    private void find(boolean byItself, boolean all) {
        AlertDialog wait = byItself ? null : new AlertDialog.Builder(a).setMessage("Looking on OpenSubtitles…")
                .setNegativeButton("Cancel", null).show();
        List<String> langs = Subtitles.languages(a);
        float fps = player.getVideoFormat() == null ? 0 : player.getVideoFormat().frameRate;
        String quality = MediaTags.video(player.getVideoFormat());
        boolean hi = Subtitles.hearingImpaired(a);
        work.execute(() -> {
            try {
                List<Subtitles.Result> got = Subtitles.rank(OpenSubtitles.search(a, tmdb, tv, season, episode, name,
                        all ? null : langs), langs, fps, quality, hi);
                h.post(() -> {
                    if (released || (wait != null && !wait.isShowing())) return;
                    if (wait != null) wait.dismiss();
                    if (byItself) {
                        for (Subtitles.Result r : got) {
                            if (Subtitles.langRank(r.lang, langs) < langs.size() && !r.foreignOnly) {
                                fetch(r, true);
                                return;
                            }
                        }
                        return;
                    }
                    choose(got, fps, quality, all);
                });
            } catch (IOException ex) {
                h.post(() -> {
                    if (released) return;
                    if (wait != null) {
                        if (!wait.isShowing()) return;
                        wait.dismiss();
                    }
                    if (!byItself) toast(ex.getMessage());
                });
            }
        });
    }

    /** The list found, best first, to choose one from (or to look in every language). */
    private void choose(List<Subtitles.Result> got, float fps, String quality, boolean all) {
        if (a.isFinishing()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(got.isEmpty() ? "None found" : "OpenSubtitles");
        if (got.isEmpty()) {
            b.setMessage(all ? "OpenSubtitles has no subtitles for this one." : "None in " + String.join(", ", names(Subtitles.languages(a))) + ".");
        } else {
            int n = Math.min(got.size(), 40);
            CharSequence[] items = new CharSequence[n];
            for (int i = 0; i < n; i++) items[i] = line(got.get(i), fps, quality);
            b.setItems(items, (d, w) -> fetch(got.get(w), true));
        }
        if (!all) b.setNeutralButton("Every language", (d, w) -> find(false, true));
        b.setNegativeButton("Close", null).show();
    }

    private static List<String> names(List<String> codes) {
        List<String> out = new ArrayList<>();
        for (String c : codes) out.add(Subtitles.languageName(c));
        return out;
    }

    /** "English · 12,300 downloads · SDH · Fits this copy", the release's name under it. */
    private CharSequence line(Subtitles.Result r, float fps, String quality) {
        SpannableStringBuilder s = new SpannableStringBuilder();
        String lang = Subtitles.languageName(r.lang);
        s.append(lang.isEmpty() ? r.lang : lang);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        StringBuilder more = new StringBuilder();
        if (r.downloads > 0) more.append(" · ").append(String.format(Locale.UK, "%,d", r.downloads)).append(r.downloads == 1 ? " download" : " downloads");
        if (r.hearingImpaired) more.append(" · SDH");
        if (r.machine) more.append(" · Machine translated");
        if (r.foreignOnly) more.append(" · Foreign parts only");
        s.append(more);
        if (Subtitles.fpsFits(r.fps, fps) || Subtitles.qualityFits(r.release, quality)) {
            int from = s.length();
            s.append(" · Fits this copy");
            s.setSpan(new ForegroundColorSpan(0xFF2FBF71), from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (!r.release.isEmpty()) {
            s.append('\n');
            int from = s.length();
            s.append(r.release.length() > 90 ? r.release.substring(0, 88) + "…" : r.release);
            s.setSpan(new RelativeSizeSpan(0.82f), from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(0xFF9AA0B4), from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    /** That one downloaded (one of the day's downloads), kept for the film and shown. */
    private void fetch(Subtitles.Result r, boolean say) {
        AlertDialog wait = new AlertDialog.Builder(a).setMessage("Downloading the subtitles…").setCancelable(false).show();
        work.execute(() -> {
            try {
                OpenSubtitles.Got g = OpenSubtitles.download(a, r);
                Subtitles.Kept k = new Subtitles.Kept();
                k.path = g.file.getAbsolutePath();
                k.fileId = r.fileId;
                k.lang = r.lang;
                k.release = r.release;
                h.post(() -> {
                    if (released) return;
                    wait.dismiss();
                    show(k, false);
                    if (say) {
                        String left = g.remaining >= 0 ? " · " + g.remaining + (g.remaining == 1 ? " download" : " downloads") + " left today" : "";
                        toast(Subtitles.languageName(k.lang) + " subtitles on" + left);
                    }
                });
            } catch (IOException ex) {
                h.post(() -> {
                    if (released) return;
                    wait.dismiss();
                    toast(ex.getMessage());
                });
            }
        });
    }

    // ---- the menu -------------------------------------------------------------------------------------------------

    /** The CC button's menu: off, the film's own, those found for it, look for more, the timing, size and look. */
    void menu() {
        if (a.isFinishing()) return;
        List<Own> own = ownTracks();
        List<Subtitles.Kept> kept = Subtitles.kept(a, film);
        List<CharSequence> items = new ArrayList<>();
        List<Runnable> does = new ArrayList<>();
        items.add(tick(mode == OFF, "Off"));
        does.add(() -> {
            own(false);
            mode = OFF;
            Subtitles.setOn(a, false);
            Subtitles.setLast(a, film, "off");
            changed.run();
        });
        int n = 0;
        for (Own t : own) {
            boolean on = mode == OWN && t.selected();
            items.add(tick(on, ownName(t, ++n), "From the film"));
            does.add(() -> {
                hideFound();
                mode = OWN;
                decided = true;
                player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(new TrackSelectionOverride(t.group.getMediaTrackGroup(), t.index)).build());
                Subtitles.setOn(a, true);
                Subtitles.setLast(a, film, "own");
                changed.run();
            });
        }
        for (Subtitles.Kept k : kept) {
            boolean on = mode == FOUND && found != null && found.fileId == k.fileId;
            String lang = Subtitles.languageName(k.lang);
            items.add(tick(on, (lang.isEmpty() ? "Subtitles" : lang) + " · OpenSubtitles", k.release));
            does.add(() -> show(k, true));
        }
        items.add(tick(false, "Find on OpenSubtitles…", !OpenSubtitles.hasKey(a) ? "Needs your free API key (Settings, Subtitles)"
                : own.isEmpty() && kept.isEmpty() ? "None in the film itself" : ""));
        does.add(() -> {
            if (OpenSubtitles.hasKey(a)) find(false);
            else new AlertDialog.Builder(a).setTitle("OpenSubtitles").setMessage(OpenSubtitles.NEEDS_KEY
                    + ".\n\nIt's free: sign up at opensubtitles.com, then Profile, API consumers, New consumer, and paste the key in.")
                    .setPositiveButton("OK", null).show();
        });
        if (mode == FOUND && found != null) {
            items.add(tick(false, "Timing · " + Subtitles.offsetText(found.offsetMs), "Earlier or later than the picture"));
            does.add(this::timing);
        }
        items.add(tick(false, "Size and look · " + Subtitles.SIZE_NAMES[Subtitles.size(a)]));
        does.add(this::lookMenu);
        new AlertDialog.Builder(a).setTitle("Subtitles")
                .setItems(items.toArray(new CharSequence[0]), (d, w) -> does.get(w).run())
                .show();
    }

    private static CharSequence tick(boolean on, String words) { return tick(on, words, ""); }

    /** A menu line: ticked when it's the one on, with a smaller line under it. */
    private static CharSequence tick(boolean on, String words, String under) {
        SpannableStringBuilder s = new SpannableStringBuilder(on ? "✓  " : "     ");
        int from = s.length();
        s.append(words);
        if (on) s.setSpan(new StyleSpan(Typeface.BOLD), from, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (under != null && !under.isEmpty()) {
            s.append("\n     ");
            int u = s.length();
            s.append(under.length() > 70 ? under.substring(0, 68) + "…" : under);
            s.setSpan(new RelativeSizeSpan(0.8f), u, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(0xFF9AA0B4), u, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    /** The file found moved earlier or later, as it plays (kept for the film when done). */
    private void timing() {
        if (found == null) return;
        Subtitles.Kept k = found;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 20);
        box.setPadding(pad, Ui.dp(a, 8), pad, 0);
        TextView value = new TextView(a);
        value.setTextSize(28);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setGravity(Gravity.CENTER);
        value.setText(Subtitles.offsetText(k.offsetMs));
        box.addView(value);
        TextView hint = new TextView(a);
        hint.setText("Words before the picture? Later. After it? Earlier.");
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 12));
        box.addView(hint);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER);
        String[] words = {"−1 s", "−0.5 s", "+0.5 s", "+1 s"};
        int[] by = {-1000, -Subtitles.STEP_MS, Subtitles.STEP_MS, 1000};
        for (int i = 0; i < words.length; i++) {
            com.google.android.material.button.MaterialButton bt = new com.google.android.material.button.MaterialButton(a,
                    null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            bt.setText(words[i]);
            bt.setAllCaps(false);
            int step = by[i];
            bt.setOnClickListener(v -> {
                k.offsetMs = Subtitles.clampOffset(k.offsetMs + step);
                value.setText(Subtitles.offsetText(k.offsetMs));
                shown = null;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.leftMargin = lp.rightMargin = Ui.dp(a, 3);
            row.addView(bt, lp);
        }
        box.addView(row);
        new AlertDialog.Builder(a).setTitle("Subtitle timing").setView(box)
                .setNeutralButton("Reset", (d, w) -> {
                    k.offsetMs = 0;
                    shown = null;
                    Subtitles.keep(a, film, k);
                })
                .setPositiveButton("Done", null)
                .setOnDismissListener(d -> Subtitles.keep(a, film, k))
                .show();
    }

    /** The words' size and look, for every film. */
    private void lookMenu() {
        int size = Subtitles.size(a), look = Subtitles.look(a);
        String[] items = new String[Subtitles.SIZE_NAMES.length + Subtitles.LOOK_NAMES.length];
        for (int i = 0; i < Subtitles.SIZE_NAMES.length; i++) items[i] = (i == size ? "✓  " : "     ") + "Size: " + Subtitles.SIZE_NAMES[i];
        for (int i = 0; i < Subtitles.LOOK_NAMES.length; i++) {
            items[Subtitles.SIZE_NAMES.length + i] = (i == look ? "✓  " : "     ") + Subtitles.LOOK_NAMES[i];
        }
        new AlertDialog.Builder(a).setTitle("Size and look").setItems(items, (d, w) -> {
            if (w < Subtitles.SIZE_NAMES.length) Subtitles.setSize(a, w);
            else Subtitles.setLook(a, w - Subtitles.SIZE_NAMES.length);
            applyLook();
            shown = null;
        }).show();
    }

    /** The size and look chosen, on the player's subtitles (the film's own and the file found alike). */
    void applyLook() {
        SubtitleView sv = view.getSubtitleView();
        if (sv == null) return;
        sv.setApplyEmbeddedFontSizes(false);
        sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * Subtitles.SIZES[Subtitles.size(a)]);
        int look = Subtitles.look(a);
        int fg = look == 2 ? 0xFFFFE14D : Color.WHITE;
        int bg = look == 1 ? 0xCC000000 : Color.TRANSPARENT;
        int edge = look == 1 ? CaptionStyleCompat.EDGE_TYPE_NONE : CaptionStyleCompat.EDGE_TYPE_OUTLINE;
        sv.setStyle(new CaptionStyleCompat(fg, bg, Color.TRANSPARENT, edge, Color.BLACK, null));
    }

    private void toast(String words) {
        if (words == null || words.isEmpty() || released) return;
        Toast.makeText(a, words, Toast.LENGTH_LONG).show();
    }
}
