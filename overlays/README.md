# Live overlays: the kick-off pop-up and line-ups, two styles each

The family's own broadcast-style graphics, shown over the channel by the TV launcher (`launcher/`, ClassicIntro) and
the phone app (`phone/tvhome-phone/`, PlayerIntro). Both apps take this folder in through their `build.gradle`
(`sourceSets`): the pages and their files (`assets/broadcast/`), the Java that plays them (`java/com/gathin/broadcast/`)
and its tests (`javatest/`).

| Part | What it is |
|---|---|
| `assets/broadcast/kickoff.html` | The kick-off pop-up: both crests fly in, the competition's 3D trophy rises behind, the bar opens with the teams, the tab changes (competition, Whitebrook Road Sports, the ground), then it all closes. Kinds: `kickoff`, `restart` (the score, scorer and minute after a goal), `second` (the second half). |
| `assets/broadcast/lineups.html` | The classic line-ups: each team by position, the players beaming in with their number and name, the subs, the formation on a tilted pitch; both teams, then a wipe back to the channel. Back fades it away. |
| `assets/broadcast/strap.html` | The first design's kick-off pop-up: a strap in the scoreboard's own look (all nine), the teams, the ground and its weather, that folds into the board where it sits (`board` {x, y, w, h} on 1920 x 1080). Same kinds as the pop-up. |
| `assets/broadcast/stadium.html` | The first design's line-ups: a stadium drawn for the day and the weather, the Whitebrook Road Sports ident, a title card, each eleven on a formation board with their pictures, the manager and subs, the referee and a countdown, then a wipe back. Back fades it away. |
| `assets/broadcast/reveal.html` | The competition intros (`assets-tv/broadcast/intros/`, 20 s films made ahead from `src/reveal/reveal.js` by `tools/reveal_render.js`, their sound by `tools/reveal_synth.py`): the Premier League's is the family's own clip then the Whitebrook Road Sports ending; the others, F1, UFC, boxing and plain football (a Champions League ball) in the same style with their own logos. The app plays the sound itself and silences the channel meanwhile; the page writes the fixture under the wordmark; Back fades it away. |
| `java/.../Sfx.java` | The scoreboard's own sounds, played by the app: the line-ups' tab swish as anything slides out beside the board, the goal sound chosen over the board's goal animation, and the family's swish as its new score pops up. |
| `java/.../BroadcastView.java` | A see-through view playing one of them over the picture: the page in a WebView, its sounds (`sfx/`) in a SoundPool. |
| `java/.../BroadcastData.java` | What a page is given: clubs, crests, colours, competition, ground; for the line-ups FotMob's two teams with each player's picture, all inline. |
| `java/.../PlPhotos.java` | The Premier League's own player pictures, matched to FotMob's line-ups by club and name through its fantasy game's player list. |
| `java/.../LaLigaPhotos.java` | La Liga's own player pictures for a La Liga game, from each player's public page on laliga.com (checked for the same name and club), cut square to the Premier League's framing. |
| `java/.../Moments.java` | When each comes: the line-ups from seven minutes before kick-off on the picture, the pop-up at each kick-off and 40 seconds after a goal reaches the picture. |

The pages are built from `src/` with `./build.sh` (Node's `npx` fetches esbuild to put the 3D trophies and three.js
into one script):

- `src/kickoff/`, `src/lineups/`: the drawing code, from the mock-ups, set to draw over a see-through page (`POP.background = 'none'`, `window.OVERLAY`).
- `src/host/`: the pages and the small scripts that take the app's data (`BROADCAST.start(json)`) and drive them in real time, calling the app back (`AndroidBroadcast.ready/started/done/sound/log`).
- `src/vendor/`: three.js r170 (MIT) and its RoomEnvironment.
- `src/fonts/`: Khand 600 and 700 (the lettering matched to the user's clips), Barlow Condensed 700 (its `&`, and the Premier League's ribbons), Outfit (the Whitebrook Road Sports wordmark); and for the competitions' scoreboards (CompBug), each cut down to Latin, Montserrat 500 to 800, Inter 500 and 600, Rubik 600, Barlow Condensed 800 and Barlow Semi Condensed 700. All SIL Open Font Licence (`OFL.txt`, copied with them).
- `src/strap/`, `src/stadium/`: the first designs (the stadium continues `src/lineups/head.js`, the same ground).
- `src/reveal/reveal.js`: the intros' drawing (deterministic, `draw(t)`); `tools/reveal_comps.json` names each one's logo, colours and words; `tools/reveal_render.js` renders frames, `tools/reveal_synth.py` the sound (no recordings).
- `src/sfx/`: the sounds, synthesised for the mock-ups and cut to each moment (Ogg Vorbis): `popup-*`, `swish-*`, `strap-<look>` (no whistle, no crowd), `stadium-*`, and the nine goal sounds `goal-*` (made by `tools/goal_synth.py`, timed to the board's goal animation; three of them in the style measured from the family's kick-off clip, a bright swipe of air into a deep boom, with nothing of its sound used). `goal-swoosh` and `score-swish` are the family's own picks, stock sound effects they supplied: the swoosh over the GOAL band (cut to start 0.8 s in, its loudest moment 0.65 s in, as the band lands; 3 dB down) and the swish as the new score pops (trimmed to its whoosh, its loudest moment 0.5 s in).

Nothing here is the broadcaster's: the drawing, the trophies (our own 3D models), the lettering (a free font close to
theirs) and the sounds are all made for this project; the competitions' and clubs' logos come from the scores' feeds
at run time, as elsewhere in the apps.

Without WebGL the pop-up draws flat trophies instead. If FotMob has no line-ups yet, the apps ask again a minute later.
