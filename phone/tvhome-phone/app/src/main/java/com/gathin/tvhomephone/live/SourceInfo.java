package com.gathin.tvhomephone.live;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One source of a film or episode as the pickers show it: TorBox's (the family's own file there) or an IPTV playlist's
 * (and which playlist), and what its name says of its picture and sound, in MediaTags' words so that Marks draws them
 * with their logos: "4K", "1080p"; "Dolby Vision", "HDR10+", "HDR10", "HLG"; "Dolby Atmos", "Dolby Digital+ 5.1",
 * "DTS-HD 7.1", "AAC Stereo"; then its file's kind and size. Read from what the lookup kept beside the address (the
 * fifth part of a play, "about"), else from its label. Pure.
 */
public final class SourceInfo {
    /** TorBox's copy (else a playlist's). */
    public final boolean torbox;
    /** The playlist's name ("" for TorBox's, or when not known); the file's or the entry's own name ("" when not known). */
    public final String playlist, name;
    /** As MediaTags says them: the picture ("4K", "1080p", "720p", "SD", ""), its HDR, its sound. */
    public final String video, hdr, audio;
    /** HD said without its lines ("HD"). */
    public final boolean hd;
    /** The file's kind ("MKV", "MP4", or ""). */
    public final String container;
    /** Its size in bytes (0 when not known). */
    public final long size;

    private SourceInfo(boolean torbox, String playlist, String name, String video, boolean hd, String hdr, String audio,
                       String container, long size) {
        this.torbox = torbox;
        this.playlist = playlist;
        this.name = name;
        this.video = video;
        this.hd = hd;
        this.hdr = hdr;
        this.audio = audio;
        this.container = container;
        this.size = size;
    }

    /** What a play ({address, user agent, referrer, label, about}) says of its source. */
    public static SourceInfo of(String[] play) {
        if (play == null || play.length == 0) return of("", "", "");
        return of(play[0], play.length > 3 ? play[3] : "", play.length > 4 ? play[4] : "");
    }

    static SourceInfo of(String url, String label, String about) {
        JSONObject a = parse(about);
        String l = label == null ? "" : label;
        boolean torbox = a != null && a.has("k") ? "torbox".equals(a.optString("k")) : TorBox.ours(url) || l.startsWith(TorBox.NAME);
        String playlist = a == null ? "" : a.optString("pl", "");
        if (!torbox && playlist.isEmpty()) playlist = playlistOf(l);
        String name = a == null ? "" : a.optString("n", "");
        long size = a == null ? 0 : Math.max(0, a.optLong("sz", 0));
        String download = a == null ? "" : a.optString("dl", "");
        // The tags from the file's name and its download's (an obfuscated file's say nothing), else from the label.
        String words = name.isEmpty() && download.isEmpty() ? l : name + " " + download;
        String container = Vod.extension(name);
        if (container.isEmpty()) container = Vod.extension(url);
        if (container.isEmpty()) container = labelKind(l);
        return new SourceInfo(torbox, playlist, name, video(words), hd(words), hdr(words), audio(words), container, size);
    }

    /** TorBox's copy of a file, as of() reads it from what the lookup keeps, without the keeping. */
    static SourceInfo torbox(String name, long size, String download) {
        String n = name == null ? "" : name, d = download == null ? "" : download;
        String words = n + " " + d;
        return new SourceInfo(true, "", n, video(words), hd(words), hdr(words), audio(words), Vod.extension(n), Math.max(0, size));
    }

    // ---- the best first ---------------------------------------------------------------------------------------------

    /**
     * Pure: which of two copies is offered first, the best: one in a file the players take well (MKV, MP4, M4V, WEBM, MOV,
     * or not known) before AVI and the like; then the picture (4K, then 1080p, then 720p, then the rest); then HDR (Dolby
     * Vision or any other) before SDR; then Dolby Atmos or surround sound before stereo; then the bigger file. 0 for two
     * it can't tell apart (their order kept).
     */
    public static int compare(SourceInfo a, SourceInfo b) {
        int c = Boolean.compare(poor(a.container), poor(b.container));
        if (c == 0) c = Integer.compare(pictureRank(a.video), pictureRank(b.video));
        if (c == 0) c = Boolean.compare(a.hdr.isEmpty(), b.hdr.isEmpty());
        if (c == 0) c = Boolean.compare(!surround(a.audio), !surround(b.audio));
        if (c == 0) c = Long.compare(b.size, a.size);
        return c;
    }

    /** Pure: the plays ({address, user agent, referrer, label, about}) in the same order, best first; alike ones as they were. */
    public static int compare(String[] a, String[] b) {
        return compare(of(a), of(b));
    }

    /** Pure: 4K first, then 1080p, then 720p, then the rest (SD, or a picture not known). */
    static int pictureRank(String video) {
        switch (video == null ? "" : video) {
            case "4K": return 0;
            case "1080p": return 1;
            case "720p": return 2;
            default: return 3;
        }
    }

    /** Pure: a file the players may struggle with: AVI, TS and the like (a kind not known isn't one). */
    static boolean poor(String container) {
        String k = container == null ? "" : container;
        return !k.isEmpty() && !k.matches("MKV|MP4|M4V|WEBM|MOV");
    }

    /**
     * Pure: sound with more than two channels, as audio() says it: Dolby Atmos; 5.1 or 7.1; DTS:X, DTS-HD and Dolby
     * TrueHD; Dolby Digital, Dolby Digital+ and DTS unless they say stereo.
     */
    static boolean surround(String audio) {
        String a = audio == null ? "" : audio;
        if (a.isEmpty()) return false;
        if (a.startsWith("Dolby Atmos") || a.endsWith("5.1") || a.endsWith("7.1")) return true;
        if (a.startsWith("DTS:X") || a.startsWith("DTS-HD") || a.startsWith("Dolby TrueHD")) return true;
        return (a.startsWith("Dolby Digital") || a.startsWith("DTS")) && !a.endsWith("Stereo");
    }

    /** What the lookup keeps beside a playlist's copy: its playlist and its entry's name. */
    static String aboutPlaylist(String playlist, String name) {
        try {
            return new JSONObject().put("k", "iptv").put("pl", playlist == null ? "" : playlist).put("n", name == null ? "" : name).toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /** What the lookup keeps beside TorBox's copy: its file's name, its size and its download's name. */
    static String aboutTorBox(String name, long size, String download) {
        try {
            return new JSONObject().put("k", "torbox").put("n", name == null ? "" : name).put("sz", Math.max(0, size))
                    .put("dl", download == null ? "" : download).toString();
        } catch (JSONException e) {
            return "";
        }
    }

    private static JSONObject parse(String about) {
        if (about == null || about.isEmpty() || !about.startsWith("{")) return null;
        try {
            return new JSONObject(about);
        } catch (JSONException e) {
            return null;
        }
    }

    /** Where it comes from, for grouping and saying: "TorBox", or "IPTV · Main" ("IPTV" when the playlist isn't named). */
    public String from() {
        if (torbox) return TorBox.NAME;
        return playlist.isEmpty() ? "IPTV" : "IPTV · " + playlist;
    }

    /** Its picture's and sound's marks, with their logos (Marks), as its name says them. */
    List<Marks.Mark> marks() {
        return Marks.of(video, hd, hdr, audio, false);
    }

    /** Spoken: where it comes from, then its marks ("TorBox, 4K Ultra HD, Dolby Vision, Dolby Atmos, 14.2 GB"). */
    public String spoken() {
        StringBuilder b = new StringBuilder(from());
        String m = Marks.spoken(marks());
        if (!m.isEmpty()) b.append(", ").append(m);
        if (size > 0) b.append(", ").append(size(size));
        return b.toString();
    }

    /** Pure: a size as people say it ("14.2 GB", "850 MB"; "" for none). */
    static String size(long bytes) {
        if (bytes <= 0) return "";
        double gb = bytes / (1024.0 * 1024 * 1024);
        if (gb >= 1) return String.format(Locale.ROOT, gb >= 100 ? "%.0f GB" : "%.1f GB", gb);
        return Math.max(1, Math.round(bytes / (1024.0 * 1024))) + " MB";
    }

    /** The playlist named at a label's front ("Main · 4K · MKV" → "Main"), "" when it starts with its picture or kind. */
    static String playlistOf(String label) {
        if (label == null || label.isEmpty()) return "";
        String first = label.split(" · ", 2)[0].trim();
        if (first.isEmpty() || first.startsWith("Source ") || first.equals(TorBox.NAME)) return "";
        String q = first.toUpperCase(Locale.ROOT);
        if (q.equals("4K") || q.equals("1080P") || q.equals("HD") || q.equals("SD") || q.matches("MKV|MP4|AVI|TS|M4V|MOV|WEBM")) return "";
        return first;
    }

    private static String labelKind(String label) {
        String[] parts = label.split(" · ");
        String last = parts[parts.length - 1].trim();
        return last.matches("MKV|MP4|AVI|TS|M4V|MOV|WEBM") ? last : "";
    }

    // ---- what a name says --------------------------------------------------------------------------------------------

    /**
     * A name made searchable: lower case, brackets and underscores as spaces, dots as spaces but in a sound's channels
     * ("DDP5.1" keeps its "5.1"; "2024.2160p", "x265.10bit" and "EAC3.6CH" come apart).
     */
    static String norm(String name) {
        String n = (name == null ? "" : name).toLowerCase(Locale.ROOT);
        n = n.replaceAll("[_\\[\\](){}|,;]+", " ");
        n = n.replaceAll("(?<![0-9])([1-7])\\.([01])(?![0-9])", "$1\u0001$2").replace('.', ' ').replace('\u0001', '.');
        return " " + n.replaceAll("\\s+", " ").trim() + " ";
    }

    private static Pattern word(String alternatives) {
        return Pattern.compile("(?:^|[\\s\\-])(?:" + alternatives + ")(?=$|[\\s\\-])");
    }

    private static final Pattern UHD = word("2160p|4k|uhd|4kuhd"), FHD = word("1080p|1080i|fhd|fullhd"), HD720 = word("720p"),
            SDP = word("576p|480p|360p|sd|dvdrip|dvd"), HDW = word("hd");
    private static final Pattern DV = word("dolby vision|dv|dovi|dvhe|dvh1|dv hdr|dv hdr10"), HDR10P = word("hdr10\\+|hdr10plus|hdr10 plus|hdr\\+"),
            HDR10 = word("hdr10|hdr 10"), HLG = word("hlg"), HDR = word("hdr");
    private static final Pattern ATMOS = word("atmos|ddpa(?:[0-9]\\.[0-9])?|dolby atmos"), DTSX = word("dts-x|dts x|dtsx|dts:x"),
            DTSHD = word("dts-hd(?: ma)?|dts hd(?: ma)?|dtshd|dts-ma|dts ma|dts-hdma"), TRUEHD = word("truehd(?:[0-9]\\.[0-9])?|true hd"),
            DDP = word("ddp(?:[0-9]\\.[0-9])?|dd\\+(?:[0-9]\\.[0-9])?|eac3|e-ac-3|eac-3|e ac 3|dolby digital plus"),
            DD = word("dd(?:[0-9]\\.[0-9])?|ac3|ac-3|dolby digital"), DTS = word("dts(?:[0-9]\\.[0-9])?"),
            AAC = word("aac(?:[0-9]\\.[0-9])?|aac-lc|he-aac"), FLAC = word("flac(?:[0-9]\\.[0-9])?"), OPUS = word("opus(?:[0-9]\\.[0-9])?"),
            MP3 = word("mp3");
    private static final Pattern CHANNELS = Pattern.compile("(?<![0-9.])(7\\.1|5\\.1|2\\.0)(?![0-9])");
    private static final Pattern CH_COUNT = Pattern.compile("(?:^|[\\s\\-])([862])ch(?=$|[\\s\\-])");

    /** Pure: the picture a name says: "4K", "1080p", "720p", "SD", or "". */
    static String video(String name) {
        String n = norm(name);
        if (UHD.matcher(n).find()) return "4K";
        if (FHD.matcher(n).find()) return "1080p";
        if (HD720.matcher(n).find()) return "720p";
        if (SDP.matcher(n).find()) return "SD";
        return "";
    }

    /** Pure: HD said without its lines ("Film HD"). */
    static boolean hd(String name) {
        return video(name).isEmpty() && HDW.matcher(norm(name)).find();
    }

    /** Pure: the HDR a name says: "Dolby Vision", "HDR10+", "HDR10", "HLG", "HDR", or "". */
    static String hdr(String name) {
        String n = norm(name);
        if (DV.matcher(n).find()) return "Dolby Vision";
        if (HDR10P.matcher(n).find()) return "HDR10+";
        if (HDR10.matcher(n).find()) return "HDR10";
        if (HLG.matcher(n).find()) return "HLG";
        if (HDR.matcher(n).find()) return "HDR";
        return "";
    }

    /** Pure: the sound a name says, in MediaTags' words ("Dolby Atmos", "Dolby TrueHD 7.1", "DTS-HD 5.1", "5.1"), or "". */
    static String audio(String name) {
        String n = norm(name);
        String kind;
        if (ATMOS.matcher(n).find()) return "Dolby Atmos";
        else if (DTSX.matcher(n).find()) kind = "DTS:X";
        else if (DTSHD.matcher(n).find()) kind = "DTS-HD";
        else if (TRUEHD.matcher(n).find()) kind = "Dolby TrueHD";
        else if (DDP.matcher(n).find()) kind = "Dolby Digital+";
        else if (DD.matcher(n).find()) kind = "Dolby Digital";
        else if (DTS.matcher(n).find()) kind = "DTS";
        else if (AAC.matcher(n).find()) kind = "AAC";
        else if (FLAC.matcher(n).find()) kind = "FLAC";
        else if (OPUS.matcher(n).find()) kind = "Opus";
        else if (MP3.matcher(n).find()) kind = "MPEG";
        else kind = "";
        String ch = channels(n);
        if (kind.isEmpty()) return ch.equals("Stereo") ? "" : ch;
        return ch.isEmpty() ? kind : kind + " " + ch;
    }

    private static String channels(String n) {
        Matcher m = CHANNELS.matcher(n);
        if (m.find()) {
            switch (m.group(1)) {
                case "7.1": return "7.1";
                case "5.1": return "5.1";
                default: return "Stereo";
            }
        }
        Matcher c = CH_COUNT.matcher(n);
        if (c.find()) {
            String w = c.group(1);
            return w.equals("8") ? "7.1" : w.equals("6") ? "5.1" : "Stereo";
        }
        return "";
    }
}
