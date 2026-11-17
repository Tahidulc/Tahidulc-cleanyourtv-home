package com.gathin.homefieldtv.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a film's or episode's name says of its picture's HDR and its sound, read as the phone reads them (its
 * SourceInfo), for the order the copies are offered in and the logos beside each: "Dolby Vision", "HDR10+", "HDR10",
 * "HLG", "HDR"; "Dolby Atmos", "Dolby TrueHD 7.1", "DTS-HD 5.1", "Dolby Digital+ 5.1", "AAC Stereo", "5.1". And that
 * order, the best first, the same as the phone's: with no limit on size. Pure.
 */
public final class Formats {
    private Formats() {}

    // ---- the best first ---------------------------------------------------------------------------------------------

    /**
     * Pure: which of two copies is offered first, the best: one in a file the players take well (MKV, MP4, M4V, WEBM,
     * MOV, or not known) before AVI and the like; then the picture (4K, then 1080p, then 720p, then the rest); then HDR
     * (Dolby Vision or any other) before SDR; then Dolby Atmos or surround sound before stereo; then the bigger file. 0
     * for two it can't tell apart (a sort keeps their order).
     */
    public static int compare(Catalog.Play a, Catalog.Play b) {
        return compare(a.kind, a.picture, a.hdr, a.sound, a.size, b.kind, b.picture, b.hdr, b.sound, b.size);
    }

    static int compare(String kindA, int pictureA, String hdrA, String soundA, long sizeA,
                       String kindB, int pictureB, String hdrB, String soundB, long sizeB) {
        int c = Boolean.compare(poor(kindA), poor(kindB));
        if (c == 0) c = Integer.compare(pictureRank(pictureA), pictureRank(pictureB));
        if (c == 0) c = Boolean.compare(empty(hdrA), empty(hdrB));
        if (c == 0) c = Boolean.compare(!surround(soundA), !surround(soundB));
        if (c == 0) c = Long.compare(sizeB, sizeA);
        return c;
    }

    /** Copies put in that order (TimSort: those alike keep theirs). */
    public static void sort(List<Catalog.Play> plays) {
        plays.sort(Formats::compare);
    }

    private static boolean empty(String s) {
        return s == null || s.isEmpty();
    }

    /** Pure: 4K first, then 1080p, then 720p, then the rest (SD, or a picture not known). */
    static int pictureRank(int picture) {
        switch (picture) {
            case Catalog.PICTURE_4K: return 0;
            case Catalog.PICTURE_1080: return 1;
            case Catalog.PICTURE_720: return 2;
            default: return 3;
        }
    }

    /** Pure: a file the players may struggle with: AVI, TS and the like (a kind not known isn't one). */
    static boolean poor(String kind) {
        String k = kind == null ? "" : kind;
        return !k.isEmpty() && !k.equals("MKV") && !k.equals("MP4") && !k.equals("M4V") && !k.equals("WEBM") && !k.equals("MOV");
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

    /** Pure: the HDR a name says: "Dolby Vision", "HDR10+", "HDR10", "HLG", "HDR", or "". */
    public static String hdr(String name) {
        String n = norm(name);
        if (DV.matcher(n).find()) return "Dolby Vision";
        if (HDR10P.matcher(n).find()) return "HDR10+";
        if (HDR10.matcher(n).find()) return "HDR10";
        if (HLG.matcher(n).find()) return "HLG";
        if (HDR.matcher(n).find()) return "HDR";
        return "";
    }

    /** Pure: the sound a name says ("Dolby Atmos", "Dolby TrueHD 7.1", "DTS-HD 5.1", "5.1"), or "". */
    public static String audio(String name) {
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
        return once(ch.isEmpty() ? kind : kind + " " + ch);
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

    /** The channels at the end of audio()'s words ("Dolby Digital+ 5.1" → "5.1"), or "". Pure. */
    public static String channelsOf(String audio) {
        String a = audio == null ? "" : audio.trim();
        for (String ch : new String[]{"7.1", "5.1", "Stereo"}) {
            if (a.endsWith(" " + ch) || a.equals(ch)) return ch;
        }
        return "";
    }

    /** One copy of each sound's words, however many titles say it (they're kept with each title). */
    private static final Map<String, String> ONCE = new HashMap<>();

    private static synchronized String once(String s) {
        String had = ONCE.get(s);
        if (had != null) return had;
        ONCE.put(s, s);
        return s;
    }

    /**
     * Pure: whether a name may say HDR ("HDR", "HDR10", "HLG"), looked at quickly: most playlists' names say none, and
     * a playlist loads many thousands, so only these are read with every pattern.
     */
    static boolean mayHdr(String name) {
        return has(name, "hdr") || has(name, "hlg");
    }

    /** Pure: whether a name may say its sound ("Atmos", "DTS", "DDP5.1", "6CH"), looked at quickly, as mayHdr. */
    static boolean maySound(String name) {
        for (String hint : SOUND) if (has(name, hint)) return true;
        return false;
    }

    private static final String[] SOUND = {"atmos", "dts", "truehd", "true hd", "ddp", "dd+", "dd5", "dd2", "dd7", "ac3", "ac-3",
            "eac", "aac", "flac", "opus", "mp3", "5.1", "7.1", "2.0", "6ch", "8ch", "2ch", "dolby"};

    /** Pure: a name saying these letters (a hint in small letters), in any case, without making a copy of it. */
    private static boolean has(String s, String hint) {
        if (s == null) return false;
        int n = s.length(), m = hint.length();
        char first = hint.charAt(0);
        for (int i = 0; i + m <= n; i++) {
            if (Character.toLowerCase(s.charAt(i)) == first && s.regionMatches(true, i, hint, 0, m)) return true;
        }
        return false;
    }
}
