package com.gathin.tvhomephone.live;

/**
 * One live channel from the family's own playlist: its name, group and logo as the playlist gives them, the stream to
 * play, the guide's id for it, and the few per-channel request options an M3U can carry (#EXTVLCOPT). The key stays the
 * same across refreshes (favourites and the last watched use it).
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
    }
}
