# TV Home sync: the phone and the TVs

The phone app and every TV keep the same **My list**, **ratings** (How was it?), **Film night** plan, **Coming up
pop-up mutes**, **reminders** and **starred clubs**. There is no cloud: each device keeps its own copy and they swap
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

The phone syncs with every paired TV when it opens, after each change it makes, and every 15 minutes (WorkManager).
A TV syncs with the other TVs ten seconds after a local change and every ten minutes.

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
