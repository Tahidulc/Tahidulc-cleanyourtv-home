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
