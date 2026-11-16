package com.gathin.homefieldtv.engine;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catch-up: a past programme, or the one on now from its start, played from the provider's own archive, where a
 * channel has one: an Xtream Codes stream marked tv_archive (for tv_archive_duration days), or an M3U entry with
 * catchup, catchup-days and catchup-source (or the older timeshift and tvg-rec days). The archive's address is made here
 * from the channel's own live address, which holds the login: never shown, never logged.
 *
 * <p>The M3U kinds: default (catchup-source is the whole address), append (added to the live address), shift (utc and
 * lutc added to it), flussonic (the live address's name given the start and length), and xc (an Xtream Codes archive
 * from the live address). The template's fields, as other players fill them: {utc} ${start} {start} the start, {utcend}
 * ${end} {end} the end, {lutc} ${now} {now} ${timestamp} now (Unix seconds); {duration} ${duration} the length and
 * {offset} ${offset} how long ago it started (seconds, {duration:60} and {offset:60} divided); {Y} {m} {d} {H} {M} {S}
 * the start's date and time, and {start:Y-m-d:H-M} (or end) in a format of those letters. An Xtream server reads its
 * archive's start in its own time zone (server_info.timezone), to the minute; the M3U fields are in UTC.
 */
public final class Catchup {
    private Catchup() {}

    public static final String XC = "xc", DEFAULT = "default", APPEND = "append", SHIFT = "shift", FLUSSONIC = "flussonic";
    /** Days kept when a playlist says it has catch-up but not for how long. */
    public static final int DEFAULT_DAYS = 1;
    /** The most days of an archive believed (a playlist saying 365 means "long"). */
    static final int MOST_DAYS = 31;

    private static final Pattern XTREAM = Pattern.compile("^(https?://[^/?#]+)/(?:live/)?([^/?#]+)/([^/?#]+)/(\\d+)(?:\\.([A-Za-z0-9]+))?$");
    private static final Pattern FIELD = Pattern.compile("\\$?\\{([A-Za-z-]+)(?::([^}]*))?\\}");
    private static final Pattern HLS_NAME = Pattern.compile("^(.*/)([^/?#]+)\\.m3u8(\\?.*)?$");
    private static final Pattern MPEGTS = Pattern.compile("^(.*/)mpegts(\\?.*)?$");

    // ---- what a playlist says --------------------------------------------------------------------------------------

    /** Pure: days of archive from Xtream's tv_archive and tv_archive_duration (0: none). */
    public static int xtreamDays(String tvArchive, String duration) {
        if (number(tvArchive) != 1 && !"true".equalsIgnoreCase(tvArchive == null ? "" : tvArchive.trim())) return 0;
        int d = number(duration);
        return d > 0 ? Math.min(d, MOST_DAYS) : DEFAULT_DAYS;
    }

    /** Pure: an M3U catchup attribute tidied: one of the kinds above, "" for none (vod, none, disabled, unknown). */
    public static String kind(String attr) {
        String a = attr == null ? "" : attr.trim().toLowerCase(Locale.ROOT);
        switch (a) {
            case "default": return DEFAULT;
            case "append": return APPEND;
            case "shift": case "timeshift": return SHIFT;
            case "flussonic": case "flussonic-hls": case "flussonic-ts": case "fs": return FLUSSONIC;
            case "xc": return XC;
            default: return "";
        }
    }

    /**
     * Pure: an M3U entry's catch-up {kind, days, source} from its attributes (or the playlist header's), null for none.
     * A source without a kind is the default kind; days without a kind are an Xtream archive when the live address is
     * an Xtream one, else shift.
     */
    public static String[] m3u(String catchup, String days, String source, String timeshift, String tvgRec, String liveUrl) {
        String k = kind(catchup), src = source == null ? "" : source.trim();
        int d = number(days);
        if (d <= 0) d = number(timeshift);
        if (d <= 0) d = number(tvgRec);
        if (catchup != null && !catchup.trim().isEmpty() && k.isEmpty()) return null;
        if (k.isEmpty()) {
            if (!src.isEmpty()) k = DEFAULT;
            else if (d > 0) k = xtreamParts(liveUrl) != null ? XC : SHIFT;
            else return null;
        }
        if (k.equals(DEFAULT) && src.isEmpty()) return null;
        if (k.equals(XC) && xtreamParts(liveUrl) == null) return null;
        if (d <= 0) d = DEFAULT_DAYS;
        return new String[]{k, String.valueOf(Math.min(d, MOST_DAYS)), src};
    }

    /**
     * Pure: an Xtream server's M3U playlist given its archive from the player API: each channel whose address is the
     * server's own and whose stream id has days gets them (xc), and the server's time zone goes on its xc archives.
     */
    public static List<Channel> withXtream(List<Channel> list, Map<String, Integer> days, String serverZone) {
        List<Channel> out = new ArrayList<>(list.size());
        String z = serverZone == null ? "" : serverZone;
        for (Channel c : list) {
            if (c.hasArchive()) {
                out.add(XC.equals(c.catchup) && c.zone.isEmpty() && !z.isEmpty() ? c.archived(XC, c.catchupSource, c.archiveDays, z) : c);
                continue;
            }
            String[] p = xtreamParts(c.url);
            Integer d = p == null || days == null ? null : days.get(p[3]);
            out.add(d != null && d > 0 ? c.archived(XC, "", Math.min(d, MOST_DAYS), z) : c);
        }
        return out;
    }

    /** Pure: whether a programme starting at startMs is in an archive of this many days now (started, not too old). */
    public static boolean inArchive(int days, long startMs, long nowMs) {
        return days > 0 && startMs < nowMs && startMs >= nowMs - days * 86_400_000L;
    }

    /** Pure: a time zone by name (an Xtream server's), UTC when it has none or none Java knows. */
    public static ZoneId zone(String name) {
        if (name == null || name.trim().isEmpty()) return ZoneOffset.UTC;
        try {
            return ZoneId.of(name.trim());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    // ---- a channel's archive -----------------------------------------------------------------------------------------

    /** The copy of the channel whose provider keeps an archive: its own, else one in another playlist; null for none. */
    public static Channel source(Channel c) {
        if (c == null) return null;
        if (c.hasArchive()) return c;
        for (Channel a : c.alternates) if (a.hasArchive()) return a;
        return null;
    }

    /** Whether the channel's archive has a programme from startMs now. */
    public static boolean has(Channel c, long startMs, long nowMs) {
        Channel s = source(c);
        return s != null && inArchive(s.archiveDays, startMs, nowMs);
    }

    /** The earliest time the channel's archive goes back to now (Long.MAX_VALUE: it keeps none). */
    public static long earliest(Channel c, long nowMs) {
        Channel s = source(c);
        return s == null ? Long.MAX_VALUE : nowMs - s.archiveDays * 86_400_000L;
    }

    /** Pure: {how many of the channels keep an archive, the most days any keeps}. */
    public static int[] count(List<Channel> channels) {
        int n = 0, most = 0;
        for (Channel c : channels) {
            Channel s = source(c);
            if (s == null) continue;
            n++;
            most = Math.max(most, s.archiveDays);
        }
        return new int[]{n, most};
    }

    /** Pure: Settings' line about the archive: how many channels keep programmes and for how long. */
    public static String summary(int channels, int days) {
        if (channels <= 0) return "Your provider keeps no catch-up for these channels";
        return String.format(Locale.UK, "Catch-up: %,d %s programmes for up to %d %s", channels,
                channels == 1 ? "channel keeps" : "channels keep", days, days == 1 ? "day" : "days");
    }

    /** Pure: how finely an archive can be started: an Xtream server's to the minute, the others to the second. */
    public static long step(String kind) {
        return XC.equals(kind) ? 60_000L : 1000L;
    }

    /**
     * Pure: the start to ask the archive for, for target (where the remote moved to) from current (where it plays): the
     * start of target's minute on an Xtream server (or its second), never behind current when moving on (dir > 0).
     */
    public static long requestAt(String kind, long target, long current, int dir) {
        long g = step(kind);
        long a = target - Math.floorMod(target, g);
        if (dir > 0 && a <= current) a = current - Math.floorMod(current, g) + g;
        return a;
    }

    /** Pure: how far to ask the archive for from start: past the programme's end (or half an hour), at most four hours. */
    public static long requestEnd(long startMs, long programmeEnd) {
        long end = Math.max(programmeEnd, startMs + 30 * 60_000L) + 5 * 60_000L;
        return Math.min(end, startMs + 4 * 3600_000L);
    }

    // ---- Xtream Codes ----------------------------------------------------------------------------------------------

    /** Pure: an Xtream live address's parts {base, user, pass, stream id, extension ("" for none)}, or null. */
    public static String[] xtreamParts(String liveUrl) {
        if (liveUrl == null) return null;
        Matcher m = XTREAM.matcher(liveUrl.trim());
        if (!m.matches()) return null;
        return new String[]{m.group(1), m.group(2), m.group(3), m.group(4), m.group(5) == null ? "" : m.group(5)};
    }

    /** Pure: an archive's start as Xtream servers read it: yyyy-MM-dd:HH-mm in the server's time zone. */
    public static String xtreamStart(long startMs, ZoneId zone) {
        ZonedDateTime t = Instant.ofEpochMilli(startMs).atZone(zone);
        return String.format(Locale.ROOT, "%04d-%02d-%02d:%02d-%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(),
                t.getMinute());
    }

    /** Pure: minutes to ask an archive for, from start to end (at least one). */
    public static int minutes(long startMs, long endMs) {
        return (int) Math.max(1, (endMs - startMs + 59_999L) / 60_000L);
    }

    /** Pure: an Xtream archive's address (/timeshift/), HLS when the live one is; null when the live one isn't Xtream's. */
    public static String xtream(String liveUrl, long startMs, int minutes, ZoneId zone) {
        String[] p = xtreamParts(liveUrl);
        if (p == null) return null;
        String ext = p[4].equalsIgnoreCase("m3u8") ? "m3u8" : "ts";
        return p[0] + "/timeshift/" + p[1] + "/" + p[2] + "/" + minutes + "/" + xtreamStart(startMs, zone) + "/" + p[3] + "." + ext;
    }

    /** Pure: the older form of an Xtream archive's address (/streaming/timeshift.php), for servers without /timeshift/. */
    public static String xtreamPhp(String liveUrl, long startMs, int minutes, ZoneId zone) {
        String[] p = xtreamParts(liveUrl);
        if (p == null) return null;
        return p[0] + "/streaming/timeshift.php?username=" + p[1] + "&password=" + p[2] + "&stream=" + p[3] + "&start="
                + xtreamStart(startMs, zone) + "&duration=" + minutes;
    }

    // ---- M3U templates ---------------------------------------------------------------------------------------------

    /** Pure: a catch-up template's fields filled in (UTC), as above; fields it doesn't know left as they are. */
    public static String fill(String template, long startMs, long endMs, long nowMs) {
        if (template == null) return "";
        long start = startMs / 1000, end = endMs / 1000, now = nowMs / 1000;
        Matcher m = FIELD.matcher(template);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String name = m.group(1).toLowerCase(Locale.ROOT), arg = m.group(2), v;
            boolean formatted = arg != null && !arg.isEmpty() && !digits(arg);
            switch (name) {
                case "utc": case "start":
                    v = formatted ? format(startMs, arg) : String.valueOf(start);
                    break;
                case "utcend": case "end":
                    v = formatted ? format(endMs, arg) : String.valueOf(end);
                    break;
                case "lutc": case "now": case "timestamp":
                    v = formatted ? format(nowMs, arg) : String.valueOf(now);
                    break;
                case "duration":
                    v = String.valueOf(divided(end - start, arg));
                    break;
                case "offset":
                    v = String.valueOf(divided(now - start, arg));
                    break;
                case "y": case "m": case "d": case "h": case "s":
                    // {Y} {m} {d} {H} {M} {S}: case matters ({m} the month, {M} the minute).
                    v = format(startMs, m.group(1));
                    break;
                case "catchup-id":
                    v = "";
                    break;
                default:
                    v = m.group(0);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(v));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Pure: a time in UTC in a format of Y m d H M S (four-figure year, the rest two figures); other characters as they are. */
    static String format(long ms, String fmt) {
        ZonedDateTime t = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC);
        StringBuilder b = new StringBuilder();
        for (char ch : fmt.toCharArray()) {
            switch (ch) {
                case 'Y': b.append(String.format(Locale.ROOT, "%04d", t.getYear())); break;
                case 'm': b.append(String.format(Locale.ROOT, "%02d", t.getMonthValue())); break;
                case 'd': b.append(String.format(Locale.ROOT, "%02d", t.getDayOfMonth())); break;
                case 'H': b.append(String.format(Locale.ROOT, "%02d", t.getHour())); break;
                case 'M': b.append(String.format(Locale.ROOT, "%02d", t.getMinute())); break;
                case 'S': b.append(String.format(Locale.ROOT, "%02d", t.getSecond())); break;
                default: b.append(ch);
            }
        }
        return b.toString();
    }

    private static long divided(long seconds, String by) {
        int n = number(by);
        return n > 0 ? seconds / n : seconds;
    }

    /** Pure: a Flussonic live address made into its archive's: NAME.m3u8 to NAME-start-length.m3u8, mpegts to timeshift_abs-start.ts. */
    static String flussonic(String live, long startMs, long endMs) {
        long start = startMs / 1000, length = Math.max(1, (endMs - startMs) / 1000);
        Matcher h = HLS_NAME.matcher(live);
        if (h.matches()) return h.group(1) + h.group(2) + "-" + start + "-" + length + ".m3u8" + (h.group(3) == null ? "" : h.group(3));
        Matcher t = MPEGTS.matcher(live);
        if (t.matches()) return t.group(1) + "timeshift_abs-" + start + ".ts" + (t.group(2) == null ? "" : t.group(2));
        int q = live.indexOf('?');
        String path = q < 0 ? live : live.substring(0, q), query = q < 0 ? "" : live.substring(q);
        return (path.endsWith("/") ? path : path + "/") + "timeshift_abs-" + start + ".ts" + query;
    }

    // ---- an archive's address ----------------------------------------------------------------------------------------

    /**
     * Pure: the archive address of a channel's programme from startMs to endMs, by its kind (null: none): kind, its
     * catchup-source, its live address, its Xtream server's time zone.
     */
    public static String url(String kind, String source, String liveUrl, String zone, long startMs, long endMs, long nowMs) {
        if (kind == null || liveUrl == null || liveUrl.isEmpty()) return null;
        switch (kind) {
            case XC:
                return xtream(liveUrl, startMs, minutes(startMs, endMs), zone(zone));
            case DEFAULT:
                return source == null || source.isEmpty() ? null : fill(source, startMs, endMs, nowMs);
            case APPEND: {
                String s = fill(source, startMs, endMs, nowMs);
                if (s.isEmpty()) return null;
                if (s.startsWith("?") && liveUrl.contains("?")) s = "&" + s.substring(1);
                return liveUrl + s;
            }
            case SHIFT:
                return liveUrl + (liveUrl.contains("?") ? "&" : "?") + "utc=" + startMs / 1000 + "&lutc=" + nowMs / 1000;
            case FLUSSONIC:
                return flussonic(liveUrl, startMs, endMs);
            default:
                return null;
        }
    }

    /** The channel's archive address from startMs to endMs (null: it keeps none). */
    public static String url(Channel c, long startMs, long endMs, long nowMs) {
        Channel s = source(c);
        return s == null ? null : url(s.catchup, s.catchupSource, s.url, s.zone, startMs, endMs, nowMs);
    }

    /** The older Xtream form of the channel's archive address (null: not an Xtream archive). */
    public static String olderUrl(Channel c, long startMs, long endMs) {
        Channel s = source(c);
        return s == null || !XC.equals(s.catchup) ? null : xtreamPhp(s.url, startMs, minutes(startMs, endMs), zone(s.zone));
    }

    private static boolean digits(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        return !s.isEmpty();
    }

    private static int number(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
