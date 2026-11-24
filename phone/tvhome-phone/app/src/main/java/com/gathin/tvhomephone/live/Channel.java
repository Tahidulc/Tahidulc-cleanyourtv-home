package com.gathin.tvhomephone.live;

/**
 * One live channel from the family's own playlist: its name, group and logo as the playlist gives them, the stream to
 * play, the guide's id for it, and the few per-channel request options an M3U can carry (#EXTVLCOPT). The key stays the
 * same across refreshes (favourites and the last watched use it).
 *
 * <p>The name and logo are the ones shown: the family's own where they gave it one (Rename channel, Change logo:
 * ChannelEdits), or the name without its quality tags for a channel shown as one row in several qualities (Versions);
 * playlistName and playlistLogo stay the playlist's, for what goes by them (Hidden's lasting key, the name matching).
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
    /** The name and logo the playlist gives it (name and logo are as shown). */
    public final String playlistName, playlistLogo;
    /**
     * The same channel in the family's other playlists (Sources), at the same picture: played when this one's stream
     * drops (Playback). Empty with one playlist.
     */
    public final java.util.List<Channel> alternates;

    public Channel(String key, String name, String group, String logo, String url, String tvgId, String userAgent,
                   String referrer, int number) {
        this.key = key;
        this.name = name == null ? "" : name;
        this.group = group == null ? "" : group;
        this.logo = logo == null ? "" : logo;
        this.url = url == null ? "" : url;
        this.tvgId = tvgId == null ? "" : tvgId;
        this.userAgent = userAgent == null ? "" : userAgent;
        this.referrer = referrer == null ? "" : referrer;
        this.number = number;
        this.playlistName = this.name;
        this.playlistLogo = this.logo;
        this.alternates = new java.util.ArrayList<>(0);
    }

    private Channel(Channel of, String name, String logo) {
        key = of.key;
        this.name = name == null ? of.playlistName : name;
        group = of.group;
        this.logo = logo == null ? of.playlistLogo : logo;
        url = of.url;
        tvgId = of.tvgId;
        userAgent = of.userAgent;
        referrer = of.referrer;
        number = of.number;
        playlistName = of.playlistName;
        playlistLogo = of.playlistLogo;
        alternates = of.alternates;
    }

    /** The same channel shown under another name or logo (its stream, key and backups the same). */
    Channel dressed(String name, String logo) {
        return new Channel(this, name, logo);
    }
}
