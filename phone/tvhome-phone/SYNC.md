# TV Home sync: the phone, the tablet and the TVs

The phone app and every TV keep the same **My list**, **ratings** (How was it?), **Film night** plan, **Coming up
pop-up mutes**, **reminders** and **starred clubs**. The phones, the tablets and Homefield TV on each TV also keep the
same **IPTV playlists** (logins included), **favourite and recent channels**, **hidden and favourite categories and
hidden channels**, **Continue watching** and a set of **settings** (see [Live TV, Continue watching and
settings](#live-tv-continue-watching-and-settings)). There is no cloud: each device keeps its own copy and they swap
changes over the link every launcher already runs (DeliveryLink, port 47820, answered only on the home network and the
tailnet). Away from home the phone works from its own copy and catches up when it can reach a TV again (directly at
home, or over Tailscale).

## Items

Every synced thing is one item:

```json
{"type": "mylist", "key": "tv:1399", "data": {...}, "updated": 1762250000000, "deleted": false, "from": "Pixel 8"}
```

- `type` and `key` name it; `data` is the content (an empty object for a tombstone).
- `updated` is the device clock in ms when it last changed; `deleted` true is a tombstone (kept 60 days).
- Merge is last writer wins per `type`+`key`: the higher `updated` wins; on a tie the greater `from` string wins.
- `from` is the device's name (a TV's name as `/hello` gives it, or the phone's model).

| type | key | data |
|---|---|---|
| `mylist` | `mv:<tmdb id>` or `tv:<tmdb id>` | `{id, tv, title, poster, backdrop, added}` (TMDB image URLs) |
| `rating` | `mv:<id>` / `tv:<id>` or `tv:<id>:s<season>` | `{value: -1 not for me, 1 liked, 2 loved, title, at}` |
| `filmnight` | `plan` | `{id, title, start (ms), runtime (min), lights, remind, quiet}`; a tombstone when cancelled |
| `mute` | `mv:<id>` / `tv:<id>` | `{title}`: that title's release pop-up is off (Coming up's hold OK) |
| `reminder` | free (`game:<espn id>`, `show:<channel>:<start>`) | `{title, at (ms), kind, ...}` |
| `clubs` | `starred` | `{teams: [espn team ids]}` |
| `playlist` | `pl:` + 24 hex digits (below) | `{name, kind: "m3u" or "xtream", m3u, guide, server, user, pass}`: **holds the login** |
| `livefav` | `list` | `{channels: [{pl: playlist key, ch: the channel's key in it}]}`: the favourite channels in order |
| `liverecent` | `list` | the same, the channels last watched, newest first (12 at most) |
| `livecat` | `groups` / `channels` | `{hidden: [category], favs: [category]}` / `{hidden: {channel key: name}}` |
| `resume` | `mv:` or `tv:` + the title's name made plain | `{tv, name, year, alt, tmdb, s, e, pos, dur, at, poster, title}` |
| `setting` | the setting's name (`posters.landscape`) | `{v: value, t: "b" boolean, "i" int, "l" long or "s" text}` |
| `myboard` | `b` and 12 hex digits (made where it was first saved) | `{name, spec: the board's design (BoardSpec), at}`: one of My scoreboards; the TV launcher keeps them as its own (MyBoardStore), Homefield TV in its sync store as they are (its MyBoardStore), synced with the launcher on its TV |

## Endpoints on each TV (all POST, JSON, `X-Token` from pairing)

Pairing is the one Delivery to TV already uses: `POST /pair {"phone": name}` makes the TV show a four-digit code,
`POST /pair/confirm {"code"}` returns `{"token", "tv"}`. Every call below carries `X-Token: <token>`; without a known
token it is 401 (the phone then offers to pair again). TVs find each other as TvLink does (`/hello`), and a TV may
call `/sync` on another TV without a token only from an address it knows as one of the house's TVs.

- `POST /sync` `{"since": ms, "device": name, "items": [...]}` → `{"now": ms, "items": [...]}`: the TV merges the
  items it is given, then answers with every item that changed on it after `since` (its clock). The caller keeps
  `now` as its next `since` for that TV. A full sync is `since: 0`.
- `POST /sync/watching` `{}` → `{"shows": [...], "resume": [...]}`: what is being watched on that TV, read only (Your
  shows' series with where they are up to and what's next; Continue watching's titles with how far through).
- `POST /play` `{"id", "tv", "title"}` → opens the title on that TV in the licensed streaming app that carries it in
  the UK (else its store, else a note on screen); 409 when the TV is off.
- `POST /tmdb` `{}` → `{"key"}`: the TV's TMDB key, so the phone can read TMDB without its own (kept on the phone).
- `POST /ai/key` `{"key", "model"}` → `{"ok"}`: the family's Anthropic API key for Ask AI and the model chosen, sent by
  Settings, Ask AI, Send key to the TVs; kept in the launcher's own private storage, never synced on, logged or shown
  ("" takes it away). Like the logins, it crosses the home network over this link. Homefield TV doesn't get it this way
  (the launcher's provider hands it only the sync token and TMDB's key): on Homefield TV it is typed in Settings › Other ›
  Ask AI.

The phone syncs with every paired TV when it opens, a few seconds after each change it makes, and every 15 minutes
(WorkManager). A TV syncs with the other TVs ten seconds after a local change and every ten minutes. Homefield TV syncs
with the launcher on its own TV when it opens, ten seconds after a change made in it, and every ten minutes while it
runs.

## Live TV, Continue watching and settings

The phone and tablet app (sync/LiveSync) and Homefield TV (sync/TvLiveSync) put what changed in their own stores into
their copy of the items just before each sync, and write the items back into their stores just after it. The launcher
does not use these kinds itself: SyncHub keeps them as they come (its private `sync_relay` preferences, never on
/sdcard), at most 64 KB of data an item and at most 30 playlists, 50 `livecat`, 400 `resume` and 300 `setting` items,
and passes them on like the rest.

- **playlist**: an M3U address (and its guide) or an Xtream Codes server, user name and password. The key is `pl:` and
  the first 12 bytes of SHA-256 of the kind and where it is (`xtream`, the server without its scheme or end slash in
  lower case, the user name; or `m3u` and the address), never the password: the same playlist has the same key on
  every device, whatever it is called there, and keeps it when its password changes. A playlist added on one device
  appears on the others with its login, so it is never typed in again; one removed on its own is a tombstone and goes
  everywhere. Remove everything is different: it resets one device only (below).
- **livefav** / **liverecent**: one `list` item each, the channels as `{pl, ch}`: the playlist's key and the channel's
  own key there (`x:<stream id>` for Xtream, `<tvg-id>|<name>` for M3U), so a channel is found whatever place its
  playlist has on a device (Live TV keys a later playlist's channels `p1:…`, `p2:…`). A device puts its own channels into
  the places its playlists had in the family's list and leaves the others' channels where they are. At most 500
  favourites and 12 recents.
- **livecat** (phones and tablets): `groups` holds the hidden categories and the favourite ones in order; `channels`
  the hidden channels (Live TV's lasting key, `id:<tvg-id>|<name>` or `nm:<name>|<hash>`, and the name), as many as fit
  in 60 KB, the rest kept on that device only.
- **resume**: where a film or an episode is up to, by its name made plain (`EN - Dune: Part Two (2024) 4K` and `Dune
  Part Two` are both `dune part two`), with the season, episode, position, length and when it was watched; never an
  address, as each device finds its own copy. A device moves its own point on when the family's is later, and looks up
  a title it hasn't started (by name, among its own films and series, at most four a sync and once each). Homefield TV
  moves to another episode, or takes a new series, only when it already has that series' episodes. A removal elsewhere
  goes everywhere, unless the title was watched again since.
- **setting**: the settings every device has, by one name:

| name | phone and tablet (preferences: key) | Homefield TV |
|---|---|---|
| `posters.landscape` | `posters`: `landscape` | `look`: `posters_wide` (shows the next time it opens) |
| `board.on`, `board.lineups` | `score_overlay`: `on`, `lineups` | `scoreboard`: `on`, `lineups` |
| `board.cover`, `board.style`, `board.custom`, `board.logo`, `board.subs`, `board.fotmob`, `board.seethrough` | `score_overlay`: the same names (`see_through`; `custom`: the id of the board of My scoreboards in use) | |
| `subs.on`, `subs.langs`, `subs.size`, `subs.look`, `subs.hi`, `subs.auto`, `captions.live` | `subtitles`: `on`, `langs`, `size`, `look`, `hi`, `auto`, `auto_live` | |
| `sports.kind.<kind>` | `sports_kinds`: each alert kind's switch | |
| `times.24h`, `look.progress` | `look`: `times24`, `progress` (Programme times, Progress bars) | the same file and keys; not passed on yet |
| `channels.merge`, `channels.sort` | `live_edits`: `merge`, `sort` (One row per channel, Channels sorting) | |

What belongs to one device stays on it: its theme, picture quality (Adaptive or fixed), buffer, decoder, screen and
where the scoreboard and logo sit, the bedtime, and **TorBox's key, which is never synced**: TorBox is added on each
device that uses it, and its key goes nowhere but TorBox. The TMDB key comes from a TV's `/tmdb` as before. **Ask AI's
Anthropic key is never synced either**: typed on each device (or sent to the paired TVs from the phone by hand, `/ai/key`),
kept in its own private preferences (`ai_key`), and it goes nowhere but Anthropic's API. The channel logos picked on a
phone stay on that phone.

**Who gets the logins.** A `playlist` item is the only kind that carries a secret. The launcher takes and gives it
only to a caller with a token: a paired phone or tablet (its pairing token) or Homefield TV on the same TV (below).
TVs never pass playlists to each other on their own, so a playlist reaches a TV's Homefield TV through a phone or
tablet paired with that TV, or from Homefield TV on that TV: **pair each phone and tablet with each TV**. No app
logs, prints or shows a login, a token or an item's data, and each keeps its copy in its own private storage.

**A device's first sync.** A device that has never synced a kind (a new phone, Homefield TV after installing, or a
kind just switched on again) sends none of it until a TV has answered: it takes the family's in first, keeps its own
as well (its favourites after the family's, its playlists beside theirs, nothing removed), and only then sends what
the family hasn't got. So a new device never overwrites the others with what it had before.

**Remove everything resets one device.** Live TV's Remove everything on a phone or tablet, and Settings, Remove
everything on Homefield TV, clear that device's playlists and live data (channels, guide, favourites, categories; on
Homefield TV also where films were left) and nothing else anywhere: no removal is sent, so the family's other devices
keep theirs. While it clears, the sync is held still, and this device's record of what it sent is forgotten, so nothing
it had can later go out as a removal. Playlists sync is switched off on that device, so the family's playlists don't
flow straight back; its confirmation says so, and Settings, Sync says it was switched off by Remove everything. Switched
on again, the family's playlists are taken in as on a new device (the first-sync rule above), with their favourites. The
other kinds stay on and start afresh the same way. Removing one playlist on purpose still goes everywhere.

**Switches.** Settings, Sync on the phone and the tablet, and Settings, Sync on Homefield TV: Playlists, Favourites &
recents (with the categories on the phones), Continue watching and Settings, all on to begin with. A kind switched off
is neither sent nor taken in on that device. Homefield TV's Sync line also says when it last synced (or why it
couldn't) and has Sync now; the phone shows each TV's last sync and Sync now.

## Homefield TV and the launcher on the same TV

Homefield TV (com.gathin.homefieldtv) has no pairing code: it gets a token from the launcher on its own TV.

1. The launcher keeps a random token for it (SyncHub.appToken: 16 bytes from SecureRandom as 32 hex digits, in its
   private preferences, compared in constant time).
2. Homefield TV calls `ContentResolver.call(content://com.gathin.tvhome.sync, "token")`. The launcher's
   SyncTokenProvider answers `{token, port: 47820}` only when the caller's signing certificate is the launcher's own
   (`PackageManager.checkSignatures(launcher uid, calling uid) == SIGNATURE_MATCH`); any other app gets a
   SecurityException. This is what a signature permission checks, without its trap (a permission an app asks for
   before the app that defines it is installed is never granted). Both apps are signed with the same key.
3. Homefield TV calls `POST http://127.0.0.1:47820/sync` with `X-Token: <that token>`. DeliveryLink accepts the app
   token only on a connection from the TV itself (loopback); from anywhere else it is just an unknown token (401).
   With it, Homefield TV is a trusted caller like a paired phone: it gets and gives the playlists too.
4. Homefield TV declares `<queries><package android:name="com.gathin.tvhome"/></queries>` to see the provider. Without
   the launcher, or with an older one, its Sync line says so and nothing else changes.

## Homefield's own updates (GET, from launcher 5.16)

Each TV keeps the newest Homefield for the phones and tablets (PhoneShelf), in the launcher's own folder
`/sdcard/Android/data/com.gathin.tvhome/files/phone/`, put there over ADB. Both calls carry `X-Token` (or come from the
TV itself, over ADB's port forward):

- `GET /phone/update.json` → `{"code", "name", "notes", "file", "size", "sha256", "patches": [{"from", "file",
  "bytes"}]}`: the version kept (its version code and name, a line on what is new), the whole APK (its file name,
  length and SHA-256) and what changed against recent versions (launcher/update/mkpatch.py, one per version code
  `from`). 404 when nothing is kept.
- `GET /phone/<file>` → the file, `Range: bytes=N-` answered with 206 so a broken download picks up where it stopped;
  503 while two phones are already fetching.

The app (update/AppUpdate) asks its paired TVs when it opens (at most every half hour) and from the 15-minute sync
(every three hours), fetches the patch for its own version when there is one (else the whole APK), puts the new APK
together against the one installed, checks its length and SHA-256 and installs it through Android's PackageInstaller
(on Android 12 and later, once Homefield may install apps, without a question).

To put a new version on a TV: make the patches with `mkpatch.py OLD.apk NEW.apk Homefield-<old code>-<new code>.patch`
(it prints the new APK's SHA-256), write update.json, then `adb push` the APK and patches into that folder and
update.json last. The launcher makes the folder when it starts, so it can read what ADB puts there.
