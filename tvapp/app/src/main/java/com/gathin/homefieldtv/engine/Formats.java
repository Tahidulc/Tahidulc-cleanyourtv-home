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
 * order, the best first, the same as the phone's: with no limit on size. And, for the cover a film opens on, a copy's
 * labels as a release names them (labels): its HDR (or SDR), its sound in short ("TrueHD Atmos", "DTS-HD MA 7.1", "DD+
 * 5.1", "AAC Stereo"), its codec (HEVC, AV1, H.264) and its release (REMUX, BluRay, WEB-DL, WEBRip, HDTV). Pure.
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
        if (c == 0) c = Boolean.compare(!isHdr(hdrA), !isHdr(hdrB));
        if (c == 0) c = Boolean.compare(!surround(soundA), !surround(soundB));
        if (c == 0) c = Long.compare(sizeB, sizeA);
        return c;
    }

    /** Copies put in that order (TimSort: those alike keep theirs). */
    public static void sort(List<Catalog.Play> plays) {
        plays.sort(Formats::compare);
    }

    /** Pure: an HDR picture as hdr() (or a provider's page) says it: anything but none and SDR. */
    static boolean isHdr(String s) {
        return s != null && !s.isEmpty() && !s.equals("SDR");
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

    // ---- a copy's labels ------------------------------------------------------------------------------------------

    private static final Pattern SDR = word("sdr"), HEVC = word("hevc|x265|h265|h 265|h-265"), AV1 = word("av1"),
            AVC = word("x264|h264|h 264|h-264|avc"), VP9 = word("vp9"), REMUX = word("remux|bdremux|bd-remux"),
            BLURAY = word("bluray|blu-ray|bdrip|brrip|bd-rip"), WEBDL = word("web-dl|webdl|web dl"), WEBRIP = word("webrip|web-rip|web rip"),
            WEB = word("web"), HDTV = word("hdtv|pdtv"), DVD = word("dvdrip|dvd-rip|dvd"), DTSMA = word("dts-hd ma|dts hd ma|dts-hdma|dts-ma|dts ma|dtshd-ma");

    /**
     * Pure: what a copy is, as its row on the cover shows it: its HDR (hdrLabel: Dolby Vision, HDR10+, HDR10, HLG, SDR),
     * its sound (soundLabel: Dolby Atmos, TrueHD 7.1, DTS-HD MA 7.1, Dolby Digital+ 5.1, 5.1, Stereo), its codec and its
     * release; what is known (a provider's page, read into hdrKnown and soundKnown) before what the name says, and those
     * not known left out.
     */
    public static java.util.List<String> labels(String name, String hdrKnown, String soundKnown) {
        java.util.List<String> out = new java.util.ArrayList<>(4);
        String h = hdrLabel(name, hdrKnown);
        if (!h.isEmpty()) out.add(h);
        String s = soundLabel(name, soundKnown);
        if (!s.isEmpty()) out.add(s);
        String c = codec(name);
        if (!c.isEmpty()) out.add(c);
        String r = release(name);
        if (!r.isEmpty()) out.add(r);
        return out;
    }

    /**
     * Pure: the sound a name says, in short as releases write it: "TrueHD Atmos", "DD+ Atmos", "Atmos", "DTS:X",
     * "DTS-HD MA 7.1", "DTS-HD 5.1", "TrueHD 7.1", "DD+ 5.1", "DD 5.1", "DTS 5.1", "AAC Stereo", "FLAC", "5.1"; or "".
     */
    public static String sound(String name) {
        String n = norm(name);
        String kind;
        if (DTSX.matcher(n).find()) kind = "DTS:X";
        else if (DTSMA.matcher(n).find()) kind = "DTS-HD MA";
        else if (DTSHD.matcher(n).find()) kind = "DTS-HD";
        else if (TRUEHD.matcher(n).find()) kind = "TrueHD";
        else if (DDP.matcher(n).find()) kind = "DD+";
        else if (DD.matcher(n).find()) kind = "DD";
        else if (DTS.matcher(n).find()) kind = "DTS";
        else if (AAC.matcher(n).find()) kind = "AAC";
        else if (FLAC.matcher(n).find()) kind = "FLAC";
        else if (OPUS.matcher(n).find()) kind = "Opus";
        else if (MP3.matcher(n).find()) kind = "MP3";
        else kind = "";
        if (ATMOS.matcher(n).find()) return kind.isEmpty() ? "Atmos" : kind + " Atmos";
        String ch = channels(n);
        if (kind.isEmpty()) return ch.equals("Stereo") ? "" : ch;
        return ch.isEmpty() ? kind : kind + " " + ch;
    }

    /**
     * Pure: a copy's HDR: as known (a provider's page), else as its name says it (hdr()), else SDR where the name says
     * so or is a release's (its codec or release named: releases name their HDR); "" when nothing says.
     */
    public static String hdrLabel(String name, String known) {
        if (known != null && !known.isEmpty()) return known;
        String h = hdr(name);
        if (!h.isEmpty()) return h;
        if (SDR.matcher(norm(name)).find() || !codec(name).isEmpty() || !release(name).isEmpty()) return "SDR";
        return "";
    }

    /**
     * Pure: a copy's sound as its row says it: "Dolby Atmos", "TrueHD 7.1", "DTS-HD MA 7.1", "DTS-HD 5.1", "DTS:X", "DTS
     * 5.1", "Dolby Digital+ 5.1", "Dolby Digital 5.1", and for AAC and the like their channels ("5.1", "Stereo"); from
     * what is known (audio()'s words: a provider's page, or a TorBox file's name read already), else the name; "" for
     * none.
     */
    public static String soundLabel(String name, String known) {
        String a = known != null && !known.isEmpty() ? known : audio(name);
        if (a.isEmpty()) return "";
        if (a.startsWith("Dolby Atmos")) return "Dolby Atmos";
        if (a.startsWith("DTS-HD") && !a.startsWith("DTS-HD MA") && DTSMA.matcher(norm(name)).find()) a = "DTS-HD MA" + a.substring(6);
        if (a.startsWith("Dolby TrueHD")) return "TrueHD" + a.substring("Dolby TrueHD".length());
        for (String k : new String[]{"AAC", "FLAC", "Opus", "MPEG"}) {
            if (a.equals(k) || a.startsWith(k + " ")) {
                String ch = a.substring(k.length()).trim();
                return ch.isEmpty() ? a : ch;
            }
        }
        return a;
    }

    /** Pure: audio()'s words in short ("Dolby Atmos" → "Atmos", "Dolby Digital+ 5.1" → "DD+ 5.1"), "" for none. */
    public static String shortSound(String audio) {
        String a = audio == null ? "" : audio.trim();
        if (a.isEmpty()) return "";
        if (a.startsWith("Dolby Atmos")) return "Atmos";
        return a.replace("Dolby Digital+", "DD+").replace("Dolby Digital", "DD").replace("Dolby TrueHD", "TrueHD");
    }

    /** Pure: the picture's codec a name says: "HEVC", "AV1", "H.264", "VP9", or "". */
    public static String codec(String name) {
        String n = norm(name);
        if (HEVC.matcher(n).find()) return "HEVC";
        if (AV1.matcher(n).find()) return "AV1";
        if (AVC.matcher(n).find()) return "H.264";
        if (VP9.matcher(n).find()) return "VP9";
        return "";
    }

    /** Pure: the release a name says: "REMUX", "BluRay", "WEB-DL", "WEBRip", "WEB", "HDTV", "DVD", or "". */
    public static String release(String name) {
        String n = norm(name);
        if (REMUX.matcher(n).find()) return "REMUX";
        if (WEBDL.matcher(n).find()) return "WEB-DL";
        if (WEBRIP.matcher(n).find()) return "WEBRip";
        if (BLURAY.matcher(n).find()) return "BluRay";
        if (WEB.matcher(n).find()) return "WEB";
        if (HDTV.matcher(n).find()) return "HDTV";
        if (DVD.matcher(n).find()) return "DVD";
        return "";
    }

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
