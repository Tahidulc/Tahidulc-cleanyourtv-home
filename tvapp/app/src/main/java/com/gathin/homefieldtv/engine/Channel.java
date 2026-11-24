package com.gathin.homefieldtv.engine;

/**
 * One live channel from the family's own playlist: its name, group and logo as the playlist gives them, the stream to
 * play, the guide's id for it, and the few per-channel request options an M3U can carry (#EXTVLCOPT). The key stays the
 * same across refreshes (favourites and the last watched use it). Where the provider keeps an archive of it, how
 * (Catchup's kinds), for how many days, and the Xtream server's time zone.
 */
public final class Channel {
    public final String key;
    public final String name;
    public final String group;
    public final String logo;
    public final String url;
    public final String tvgId;
    public final String userAgent;
    public final String referrer;
    public final int number;
    /** Catch-up: the archive's kind (Catchup.XC and the M3U kinds), "" when the provider keeps none. */
    public final String catchup;
    /** An M3U entry's catchup-source (a template, or what is added to the live address), "" for none. */
    public final String catchupSource;
    /** How many days back the archive goes (0: none). */
    public final int archiveDays;
    /** The Xtream server's own time zone (its archive's times are in it), "" for UTC. */
    public final String zone;
    /**
     * The same channel in the family's other playlists (Sources), at the same picture: played when this one's stream
     * drops (Playback). Empty with one playlist.
     */
    public final java.util.List<Channel> alternates = new java.util.ArrayList<>(0);
    /**
     * The same channel in its other qualities in this playlist's category (FHD, HD, SD), with this one, best first, and
     * the channel's name without its quality tags (Versions): worked out once a load (Lists.Index); null for a channel
     * in one version. The lists show them as one only while the setting says so (Versions.of).
     */
    public volatile Versions versions;

    public Channel(String key, String name, String group, String logo, String url, String tvgId, String userAgent,
                   String referrer, int number) {
        this(key, name, group, logo, url, tvgId, userAgent, referrer, number, "", "", 0, "");
    }

    public Channel(String key, String name, String group, String logo, String url, String tvgId, String userAgent,
                   String referrer, int number, String catchup, String catchupSource, int archiveDays, String zone) {
        this.key = key;
        this.name = own(name);
        // What thousands of channels share (their group, the playlist's user agent, the archive's kind and time zone):
        // one copy each, however many channels say it; and one empty string for all that say nothing.
        this.group = shared(group);
        this.logo = own(logo);
        this.url = own(url);
        this.tvgId = own(tvgId);
        this.userAgent = shared(userAgent);
        this.referrer = shared(referrer);
        this.number = number;
        this.catchup = shared(catchup);
        this.catchupSource = shared(catchupSource);
        this.archiveDays = this.catchup.isEmpty() ? 0 : Math.max(0, archiveDays);
        this.zone = shared(zone);
    }

    /** A string of its own, or the one empty string. */
    static String own(String s) {
        return s == null || s.isEmpty() ? "" : s;
    }

    /** A string many channels say: the one copy of it (Java's own pool, which lets go of what nothing holds). */
    static String shared(String s) {
        return s == null || s.isEmpty() ? "" : s.intern();
    }

    /** The same channel under another key (a playlist's prefix before it). */
    public Channel keyed(String newKey) {
        return new Channel(newKey, name, group, logo, url, tvgId, userAgent, referrer, number, catchup, catchupSource, archiveDays, zone);
    }

    /** The same channel with an archive: its kind, catchup-source, days and server time zone. */
    public Channel archived(String kind, String source, int days, String serverZone) {
        return new Channel(key, name, group, logo, url, tvgId, userAgent, referrer, number, kind, source, days, serverZone);
    }

    /** Whether the provider keeps an archive of this copy of the channel. */
    public boolean hasArchive() {
        return archiveDays > 0;
    }
}
