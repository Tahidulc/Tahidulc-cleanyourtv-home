package com.gathin.homefieldtv.engine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * What a provider's own page says of a copy's stream: Xtream's get_vod_info "info" (an episode's "info" in
 * get_series_info), which panels fill from ffprobe: "video" (codec_name, codec_tag_string, profile, width, height,
 * color_transfer, color_primaries, side_data_list with Dolby Vision's configuration record or HDR10+'s dynamic metadata),
 * "audio" (codec_name, profile, channels, channel_layout), "bitrate" (kb/s) and "duration_secs". Read into:
 *
 * <pre>
 *   picture   from the width and height: 4K (3200 wide or 1800 high), 1080p (1800 or 1000), 720p (1200 or 700), else SD
 *   hdr       Dolby Vision (its side data or a dvhe/dvh1 tag), HDR10+ (SMPTE 2094-40), HDR10 (PQ, smpte2084), HLG
 *             (arib-std-b67), else SDR
 *   sound     as Formats.audio words it: Dolby Atmos (TrueHD or E-AC-3 with Atmos or JOC), Dolby TrueHD 7.1, DTS-HD MA
 *             7.1, DTS-HD 5.1, DTS:X, DTS 5.1, Dolby Digital+ 5.1, Dolby Digital 5.1, and for the rest their channels
 *             alone (5.1, Stereo)
 *   size      the file's bytes where a panel gives them ("size", "file_size", "filesize"), else none
 *   estimate  bitrate × length, for a size worked out ("≈ 4.1 GB") when nothing better is known
 * </pre>
 *
 * Pure; the address is never part of it.
 */
public final class StreamInfo {
    public final int picture;
    public final String hdr, sound;
    /** Bytes as given (0: not given), bits a second, seconds. */
    public final long size, bitrate, seconds;

    StreamInfo(int picture, String hdr, String sound, long size, long bitrate, long seconds) {
        this.picture = picture;
        this.hdr = hdr == null ? "" : hdr;
        this.sound = sound == null ? "" : sound;
        this.size = Math.max(0, size);
        this.bitrate = Math.max(0, bitrate);
        this.seconds = Math.max(0, seconds);
    }

    /** Pure: what an info object says of its stream, or null when it says nothing of it (no video, audio or size). */
    public static StreamInfo of(JSONObject info) {
        if (info == null) return null;
        JSONObject v = info.optJSONObject("video"), a = info.optJSONObject("audio");
        int picture = v == null ? 0 : picture(v.optInt("width"), v.optInt("height"));
        String hdr = v == null ? "" : hdr(v);
        String sound = a == null ? "" : sound(a);
        long size = number(info, "size", "file_size", "filesize");
        long kbps = number(info, "bitrate");
        long secs = number(info, "duration_secs");
        if (picture == 0 && hdr.isEmpty() && sound.isEmpty() && size <= 0) return null;
        return new StreamInfo(picture, hdr, sound, size, kbps * 1000L, secs);
    }

    /** Pure: the bytes its bitrate and length make (0 without both). */
    public long estimate() {
        return bitrate > 0 && seconds > 0 ? bitrate / 8L * seconds : 0L;
    }

    /** Pure: the picture a width and height make (0 without either). */
    static int picture(int width, int height) {
        if (width <= 0 && height <= 0) return 0;
        if (width >= 3200 || height >= 1800) return Catalog.PICTURE_4K;
        if (width >= 1800 || height >= 1000) return Catalog.PICTURE_1080;
        if (width >= 1200 || height >= 700) return Catalog.PICTURE_720;
        return Catalog.PICTURE_SD;
    }

    /** Pure: the HDR a video stream says (SDR when it says a picture and none of these; "" when it says no picture). */
    static String hdr(JSONObject v) {
        String tag = v.optString("codec_tag_string").toLowerCase(Locale.ROOT), profile = v.optString("profile").toLowerCase(Locale.ROOT);
        boolean dv = tag.startsWith("dvh") || tag.startsWith("dva") || profile.contains("dolby vision"), plus = false;
        JSONArray side = v.optJSONArray("side_data_list");
        for (int i = 0; side != null && i < side.length(); i++) {
            JSONObject s = side.optJSONObject(i);
            String type = s == null ? "" : s.optString("side_data_type").toLowerCase(Locale.ROOT);
            if (type.contains("dovi") || type.contains("dolby vision")) dv = true;
            if (type.contains("2094-40") || type.contains("hdr10+")) plus = true;
        }
        String transfer = v.optString("color_transfer").toLowerCase(Locale.ROOT);
        if (dv) return "Dolby Vision";
        if (transfer.equals("smpte2084")) return plus ? "HDR10+" : "HDR10";
        if (transfer.equals("arib-std-b67")) return "HLG";
        return v.optInt("width") > 0 || v.optInt("height") > 0 || !v.optString("codec_name").isEmpty() ? "SDR" : "";
    }

    /** Pure: the sound an audio stream says, as Formats.audio words it ("" when it says neither its codec nor channels). */
    static String sound(JSONObject a) {
        String codec = a.optString("codec_name").toLowerCase(Locale.ROOT);
        String profile = (a.optString("profile") + " " + a.optString("codec_long_name")).toLowerCase(Locale.ROOT);
        String ch = channels(a.optInt("channels"), a.optString("channel_layout"));
        boolean atmos = profile.contains("atmos") || profile.contains("joc");
        String kind;
        switch (codec) {
            case "truehd":
                if (atmos) return "Dolby Atmos";
                kind = "Dolby TrueHD";
                break;
            case "eac3":
                if (atmos) return "Dolby Atmos";
                kind = "Dolby Digital+";
                break;
            case "ac3":
                kind = "Dolby Digital";
                break;
            case "dts":
            case "dca":
                if (profile.contains("dts:x") || profile.contains("dts-x")) return "DTS:X";
                kind = profile.contains("ma") ? "DTS-HD MA" : profile.contains("dts-hd") || profile.contains("hra") ? "DTS-HD" : "DTS";
                break;
            default:
                kind = "";
                break;
        }
        if (kind.isEmpty()) return ch;
        return ch.isEmpty() || ch.equals("Stereo") && !kind.startsWith("Dolby Digital") ? kind : kind + " " + ch;
    }

    /** Pure: a stream's channels as words: 7.1, 5.1, Stereo, Mono ("" when not said). */
    static String channels(int n, String layout) {
        if (n >= 8) return "7.1";
        if (n >= 6) return "5.1";
        if (n == 2) return "Stereo";
        if (n == 1) return "Mono";
        String l = layout == null ? "" : layout.toLowerCase(Locale.ROOT);
        if (l.startsWith("7.1")) return "7.1";
        if (l.startsWith("5.1")) return "5.1";
        if (l.startsWith("stereo")) return "Stereo";
        if (l.startsWith("mono")) return "Mono";
        return "";
    }

    private static long number(JSONObject o, String... keys) {
        for (String k : keys) {
            Object v = o.opt(k);
            if (v instanceof Number) return ((Number) v).longValue();
            if (v instanceof String) {
                try {
                    return Long.parseLong(((String) v).trim());
                } catch (NumberFormatException ignored) {
                    // not a number: the next
                }
            }
        }
        return 0L;
    }

    /** What it says put on a copy: its picture, HDR, sound and size where it says them (the name's kept for the rest). */
    void apply(Catalog.Play p) {
        if (picture > 0) p.picture = picture;
        if (!hdr.isEmpty()) p.hdr = hdr;
        if (!sound.isEmpty()) p.sound = sound;
        if (size > 0 && (p.size <= 0 || p.sizeGuessed)) {
            p.size = size;
            p.sizeGuessed = false;
        }
        p.info = true;
        p.stream = this;
    }

    /** Previews and tests: what a provider's page says, put on a copy as the cover's look for it would. */
    public void applyTo(Catalog.Play p) {
        apply(p);
    }
}
