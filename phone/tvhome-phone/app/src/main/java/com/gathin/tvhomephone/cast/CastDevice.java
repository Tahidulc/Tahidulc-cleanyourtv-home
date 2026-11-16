package com.gathin.tvhomephone.cast;

/**
 * Somewhere on the home Wi-Fi a channel can go to: Homefield TV on one of the family's TVs, a paired TV's own player
 * (TV Home, TiviMate), a Chromecast or Google TV, or a DLNA set. Its name to show, and where to reach it.
 */
public final class CastDevice {
    public static final int HOMEFIELD = 0, TV_HOME = 1, CHROMECAST = 2, DLNA = 3;

    public final int kind;
    /** One per device: kind and where it is. */
    public final String id;
    public final String name;
    /** Homefield TV: its address and port. DLNA: its AVTransport control address. Chromecast: the route's id. */
    final String host, control, route;
    final int port;

    private CastDevice(int kind, String id, String name, String host, int port, String control, String route) {
        this.kind = kind;
        this.id = id;
        this.name = name == null || name.trim().isEmpty() ? "TV" : name.trim();
        this.host = host;
        this.port = port;
        this.control = control;
        this.route = route;
    }

    public static CastDevice homefield(String name, String host, int port) {
        return new CastDevice(HOMEFIELD, "hf:" + host + ":" + port, name, host, port, null, null);
    }

    public static CastDevice tvHome(String name) {
        return new CastDevice(TV_HOME, "tvh:" + name, name, null, 0, null, null);
    }

    public static CastDevice chromecast(String name, String routeId) {
        return new CastDevice(CHROMECAST, "cc:" + routeId, name, null, 0, null, routeId);
    }

    public static CastDevice dlna(String name, String control) {
        return new CastDevice(DLNA, "dlna:" + control, name, null, 0, control, null);
    }
}
