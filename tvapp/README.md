# Homefield TV

An IPTV app for Android TV only. From the first open it has the colour theme's ground and panels (Indigo • Blue as it
comes), the TV's own Roboto for the writing, the accent where the remote is and for what plays, and the menu down the
left that opens into a drawer; Settings and the side menus open in a panel down the right over the screen. It plays the family's own playlists (an M3U address or an Xtream Codes login): their live
channels and guide, and their own films and TV shows, browsed by the playlist's categories. It ships with no
playlists or sources of its own, and films and series come only from the playlists added to it. The app's name and
wordmark are Homefield; it carries no Sky logo or name.

## Screens

- **The menu:** the rail down the left of every screen (Homefield's mark, then Search, Home, the TV guide, Movies, TV
  shows, Sports, Recordings and My list, with Settings at the foot; the screen's own section in white). When the remote
  goes on to it, it opens into the drawer with each section's name, the remote's item lit in the accent and the screen's
  own in a quieter grey; Up and Down along it, OK goes there, Right or Back closes it.
- **Home:** one large card at a time with rounded corners, the artwork of what the remote is on filling it: its title
  big and bold over the picture (a channel's logo at the top right), a line of facts (season or running time, year,
  genre, age as "15+", five stars from its rating) and, along the card's foot, a strip of small pictures: a series'
  episodes (every season's in one row, each later season's first marked "Season 2", the one to carry on with first in
  view), a channel's next programmes (on now with how far in, the later ones with their times), or films like the one
  on the card. The page around the card takes the artwork's colour. Above it, Homefield's mark (OK opens the menu)
  and the sections as words: On now (or Your channels), Continue watching, Your TorBox, New films, New TV shows, Sport
  on now and Favourites; Left and Right go along a section's cards, the next picture cross-fading into the card as its
  name slides in from that side and the page's colour fades to its own; Up goes to the sections, Down into the
  strip, where the tile the remote is on grows with a white edge and shows its name. OK on a channel watches it, on a
  film or show opens its page, on Continue watching carries on, on an episode plays it and on a programme later sets a
  reminder; OK held on a channel makes it a favourite and CH+ and CH- change channel. Back goes from the strip to the
  card, then to the sections, then opens the menu, and Back from the menu goes back to the TV. Home doesn't play
  anything itself.

- **TV guide:** as the family's other app has it. At the top left the preview, 640 by 360, playing the channel last
  watched (or the programme from the archive Back came from); to its right, on a panel of its own, the programme the
  remote is on: its title, a star for a favourite channel, its times ("10:00 — 11:00") with a bar and the minutes left
  for one on now ("16 min"), the picture's frames a second for the channel playing ("50 FPS", or as last seen on a
  channel watched before), the playlist and the group at the right, and all it says about it; behind the words, across
  the panel's right-hand part, the programme's picture (Sky's public UK guide, else the channel's logo on a ground
  tinted by its own colour), faded into the panel from the left and the foot, cross-fading to the next programme's as
  the remote moves (Left, Right, Up, Down), the pictures either side read ahead. Under it the date and the clock in the light
  accent, the times every half hour and a dot at the time now; then a row a channel, 88 high: its number, its logo on its
  tile and its name, a ▶ on the channel in the preview and the archive's mark on one whose provider keeps an archive; its
  programmes in neutral cells, the part already shown a little lighter, the remote's row lighter still and its cell in
  the accent, their titles in two lines; a bell on a programme with a reminder. Up and Down move a row, the rows sliding
  so the remote's stays third from the top; Left and Right go along the programmes, the times paging so the remote's
  starts at the left (both glide on the display's own clock, and on smoothly while a key is held, never starting over);
  the channel keys page the rows, rewind and fast forward the times (back into an archive too).
  Left from the programme on now brings in the menu's rail and the groups beside it, pushing the guide right: the
  playlist's Favourites, All channels and Recently watched, its groups in the family's order and names (hidden and
  locked-away ones left out), then the guide's own categories; the guide shows a group as soon as the remote is on it.
  OK held on a group opens its menu, as TiviMate's: Rename group (Restore group name once renamed), Hide group, Move
  group (its row lifts, Up and Down carry it past the others, OK puts it down, Back leaves it), Sort channels (By order
  in playlist, By name, By number or Manual, the one in use ticked), then Show all groups, Hide all groups and Manage
  groups (Settings); on Favourites, All channels or Recently watched, those last three. Left again opens the drawer. OK
  on a programme on now plays its channel in the preview, and OK on the channel playing there goes full screen (Back
  from the player comes back to the guide); OK on a programme to come opens a small menu under it (Remind, Record on a
  channel with catch-up, Add to My list, Programme description); on one already shown that the archive keeps, OK plays it
  from its start. OK held (or MENU) opens the whole menu: Search (for the programme's title) and Settings, the
  programme's lines (Remind, Watch from start, Watch, Record, My list, its description), then under the channel's name
  TiviMate's Add to or Remove from Favourites, Block channel and Hide channel (out of every list at once: the guide, the
  channel lists, channel up and down, the numbers, search, Home's sections and the sports pages' Watch on), then Rename
  channel (with Settings, Appearance, TV guide, Channel names editor on, as it comes: the keyboard over the guide, the
  name shown everywhere and found by search with its old one too; Restore channel name), Change logo (the new logos found
  for it, the best first, each on its tile with its country and kind, and the playlist's own; the one chosen kept for
  that channel), Move channel (TiviMate's Manage positions: its row lifts in its group, or among the favourites, Up and
  Down move it, OK puts it down and the group keeps that order, Sort channels then Manual; Back leaves it) and Catch-up.
  Play/pause shows the description, Stop adds the channel to the favourites or takes it off, Back held goes back to the
  player and MENU held opens Search; the number keys go to a channel. Back comes back to the programmes on now, then
  opens the groups, then the drawer, then leaves the guide.
- **Channel logos:** the new logos (Settings, Appearance, Logos: New logos (Homefield), as it comes, or the playlist's
  own) are the tv-logo project's free logos for dark grounds (github.com/tv-logo/tv-logos, free for personal use: here
  for the family's own viewing only; no logo is in the app or this repository). The TV asks GitHub for the list of the
  project's logos' names once a week (the small list the app comes with until then: the United Kingdom's, Ireland's and
  the international ones), and fetches each logo it shows from GitHub with Homefield's plain User-Agent and nothing
  about the family, keeping it with its other pictures at the size it is drawn. A channel's name is matched to a logo's
  with its quality and country tags left out ("UK: BBC ONE FHD", "|UK| …", "[UK] …"), a list number before it too, One
  as 1, a "+1" kept apart, a few aliases (ITV as ITV1, More4, CBBC, Sky Main Event) and its numbers always counting;
  where the playlist's country says otherwise, another country's. A new logo shows only where the match is sure (else
  the playlist's); Change logo in a channel's menu offers every one found. On the guide's rows, the channel lists, the
  info card, Home's channel cards, search, History, My list and the sports pages' Watch on.
- **Catch-up's archive:** from a channel's Catch-up in the guide's menu: the channel along the top, its programmes down
  the left under each day's name, the remote's always at the same height with the list sliding under it (the one on now
  in the light accent, those the archive no longer keeps greyed), the days beside them (today's in the light accent), and
  the programme's picture (Sky's public UK guide, else the channel's logo) over a card with its day, times and what it is
  about. Right goes to the days and Left back; OK plays a programme already shown from the archive, watches the one on
  now, or opens the small menu for one to come; OK held opens the whole menu.
- **Live TV:** full screen, the picture alone. A channel changed to brings up the info card at the foot for the channel banner's
  seconds (Settings, Player; 6 as it comes), and INFO brings it up too: a card in the playback panel's colour with the
  channel's logo and name, the programme's title, the date and the clock, its times with a bar and the minutes left,
  the playlist and the group, chips for the picture and the sound as the player has them (FHD, 50 FPS, STEREO; the
  frames a second as the stream says them, else counted from the frames it shows over a few seconds and taken to the
  usual rate: 24, 25, 30, 50 or 60, left out until they are known, and kept for each channel), two lines about it and
  what's next, and behind its words, across its right-hand part, the programme's picture as the guide's panel has it,
  cross-fading as Up, Down, CH+, CH− and INFO bring up another channel's card. CH+ and CH− bring up the next or previous channel's card without leaving the channel
  (OK then changes to it); Up and Down change channel (Settings, Remote can make them bring up the live screen's rows of
  channels instead, as Homefield TV came); Right goes back to the channel watched last; the number keys go to a channel,
  the number in a box at the top right. OK brings up the overlay: the card at the top, a line across the foot for how
  far through the programme is, and a row of tiles under it (TV guide, History, then the channels last watched, each with
  what's on and how far through); Down from the tiles brings up the bar. OK held, or MENU, brings up the bar alone: round
  buttons along the foot, each with its label (Search, Channels list, History, Movies, TV shows, Recordings, My list,
  then Video tracks, Audio tracks, Audio offset, Closed captions, Display mode and Sleep timer labelled with what they are
  set to, then Add to favourites, Scoreboard over a match, Channel options, Settings and Exit). Each of the picture's and
  sound's buttons opens its list at the right: Video tracks is the Picture quality (Adaptive, 4K, 1080p, 720p, SD, Data
  saver); Audio tracks the stream's sound tracks, or Off; Audio offset the sound's delay for this channel, 50 ms a press
  either way, with Reset and Apply to all channels; Closed captions the subtitles (below); Display mode the picture's
  shape for this channel or all of them (Normal, Full, Zoom, Zoom - 2.35:1, Reduced, 4:3, 16:9, 2.35:1); Sleep timer
  stops the picture after 15 minutes to 4 hours; Channel options has Watch from the start and Lock this channel. Left
  brings up the channel list over the picture: the channels of the group playing down the left, the programmes of the
  one the remote is on beside them, and its programme in a card at the top right; Left again opens the groups (the
  playlist, Favourites, All channels, then the family's groups), and OK plays a channel with the list staying. Left
  held, or Channels list, opens it on its own ground with the picture playing on in a window at the top right. Up held
  brings up History, Right held the programme's description, Down held Search. Back goes to the TV guide with the
  channel in its picture; Back held stops the picture and goes Home. What is up goes after 10 seconds with no key. A
  stream that drops is tried on the same channel in another playlist, then again. Where the provider keeps the
  programme on now in its archive (or the stream itself reaches back to its start), Channel options has Watch from the
  start, as has the remote's rewind key.
- **History:** Up held while watching, or History on the overlay's tiles and the bar: over the dimmed picture, the
  programmes watched on this TV in the last seven days, day by day, each with its times, the time it was watched and the
  channel's logo and name (a programme counts once it has been watched for 10 seconds, and once however often). OK on
  one on now plays its channel; on one the archive keeps, plays it from its start; one that has ended with no archive
  to play it from is left out. Right goes to an entry's delete button, and the bin at the top right clears them all
  after asking. Kept on this TV only.
- **Subtitles:** over full-screen live TV and catch-up only (not the small pictures on Home and in the guide), for
  every channel: Off (as it starts), From the channel (the subtitles a channel sends: DVB subtitles where it puts them,
  its subtitle playlists, captions) or Automatic, made on the TV from what's said as it's said, a channel's own showing
  instead when it plainly sends some (a bare captions track every stream is given doesn't count). Chosen from the
  player's bar (Closed captions, the one chosen ticked, with what the channel sends) or Settings, Live TV, with their
  look (white with an outline, white on a black box, yellow) and size. The words keep about 40 letters to a line near the
  foot, move up above the info card and the overlay while they are up, and go while the channel lists and the live
  screen's rows are up. Automatic uses
  the TV's own on-device speech recognition where it has it (Android 13 and later); where it hasn't, it asks first
  ("a one-time download of about 40 MB, about 70 MB on the TV") and only then fetches Vosk's small English model into
  the app's own storage, its progress where the words go. The sound is heard on the TV, by a copy taken in the
  player's sound chain; nothing heard leaves the TV. A channel's own tracks stay off while nothing shows them.
- **Reminders:** in the guide, OK (or OK held) on a programme to come gives Remind (Cancel reminder once one is set), and
  a bell on its cell and "Reminder set" by its times. A minute before it starts, a card comes up at the top right over
  whatever is on the TV, other apps too: the channel's logo on its tile, "Starts in 1 min" (then "Starting now"), the
  title, the channel and its time, and what the keys do. It takes the remote after a second and a half; OK opens Homefield
  TV on the channel, Back puts it away, any other key lets it go, and it goes by itself after about 20 seconds. Over
  other apps where Android lets Homefield TV (Display over other apps, asked for once, when the first reminder is set,
  with a way to Android's page); otherwise a notification that asks to come up as the same card. Its alarm is exact
  where Android allows it and never wakes the TV: a TV that is off stays off, and a card is shown only up to a few
  minutes after the start. Reminders are kept on the TV and made again after a restart and an update.
- **Catch-up:** a programme from the provider's archive: an Xtream Codes login's streams marked as kept (for the days
  the server says, its times in the server's own time zone; the older timeshift.php address when the newer one
  doesn't answer), and an M3U playlist's catchup, catchup-days and catchup-source (default, append, shift, Flussonic
  and Xtream; {utc}, {start}, {end}, {duration}, {offset}, {lutc}, {Y}{m}{d}{H}{M}{S} and the like filled in; also the
  older timeshift and tvg-rec), or an Xtream server's M3U address given its archive by the player API. It starts with
  the info card at the foot, the archive's mark and the programme's whole length in it. OK brings up the overlay: the
  card at the top; across the foot a line for the programme, the part shown in the accent and what has been on so far
  in light grey; the time ("00:05 / 55:00") at the left; the buttons in the middle (to the programme's start or the one
  before, back, pause, on, the next programme or live); at the right, in a pill, from the start again and LIVE. Down
  brings up the row of tiles in the buttons' place, Up takes them away. Left and Right (and the rewind and fast forward
  keys) move it 10 seconds a press, more as they are held (30 seconds, a minute, three minutes), and the archive is asked
  for from there a moment after the remote rests; play/pause pauses. Up and Down change channel, live; CH+ and CH−
  bring up a channel's card; OK held or MENU the player's bar. Back closes the overlay, then goes to the TV guide. It
  goes on into the next programme when one ends, and is live again at the live edge. The archive's address is made on the TV from the
  channel's own, and like it is never shown or written out. Channels with no archive don't offer any of it.
- **Sports:** Sports is a page under the tab bar (its Sports tab), in 07's look over the blurred picture of what the
  remote is on: its title with its three sections as pills beside it (Up from the top of a list goes to them, Left and
  Right or OK pick one, Down comes back, Up again goes to the tab bar; Back at a section's root goes to the tab bar
  too). Today lists today's live and coming games (football, NFL, NBA, tennis, UFC, F1) under LIVE NOW, LATER and
  FINISHED with their counts, a row each washed at its ends in its two sides' colours (the away side's second colour
  when the two are too close), the competition in small capitals, both crests and names, the score in boxes of each
  side's colour (or the kick-off) and how far on; the remote's row white and lifted. Beside it the game the remote is
  on, big: its top split on the slant between the two sides' colours, the competition and LIVE, both crests, the score
  huge with the clock (a red card when one was shown), the scorers and red cards so far, the family's own channels that
  show it (English first, with the programme on), whether the hub is in step with the channel being watched and its
  delay, and what OK does. Tables has the leagues on glass down the left (Premier League, Champions League,
  Championship, Scottish Premiership, LaLiga, Serie A, Bundesliga, Ligue 1, Europa League, Conference League) and the
  one the remote is on as its table on glass beside them (each club's place with ESPN's zone mark, crest, name, played,
  won, drawn, lost, goal difference and points; Right goes into it); Formula 1 has the drivers in championship order
  (formula1.com's picture of each in a round frame, team and wins, points), the one the remote is on beside them on a
  card in his team's colour, standing, with his place, points, wins and podiums and the next Grand Prix. A game's page
  (a "‹ Sports · Today" line where Back goes) has the two sides on its header in their colours, the score and clock or
  "Full time" with the crowd, what's happened, the stats, both line-ups, momentum and Watch on buttons (English
  channels first), all held back to match the channel being watched.
  OK on a club opens the club's page: Right from a football game in Today goes on to its two clubs, Up from the top of a
  game's page to its two sides, and OK on a table's row; OK on a driver opens his page. Over a match in the player, the
  scoreboard: the game found from the channel and its guide, its clock set at kick-off with OK, alerts beside it, the
  kick-off and after-goal pop-ups and the line-ups, a cover for the channel's logo, its menu from the player's bar
  (Scoreboard), and a Scoreboard section in Settings. The scoreboard and the logo cover show only while the channel
  plays full screen with nothing over it: the info card, the OK overlay, the bar, the channel list, a number being typed
  or any screen or panel over the picture (the guide's preview, Home, Settings) takes them away at once, and
  they come back with plain playback; their pop-ups wait for it, or are let go when stale (a kick-off after two minutes,
  a goal's after ninety seconds), and after a longer time away the board catches up without popping up what it missed.
- **A club's page:** in 07, under the shell's "‹ back" line: the club's own colour behind (ESPN's, made deep enough for
  white writing; for a white or black club FotMob's, else ESPN's second colour), brightest a little above the middle
  and deepening to the edges, with a fine grain and a soft vignette; the crest and the Players and Management pills at
  the left, the lines' pills (All, Goalkeepers, Defenders, Midfielders, Forwards) at the right, white when on and glass
  in the club's colour when off; then the squad as a carousel of frosted-glass cards: the one the remote is on in the
  middle, larger and brighter, two smaller and fainter each side, each with his number in the club's coin at its top
  corner and his name at its foot; the middle one's line written down beside it and his surname huge under it, rising
  in as the remote moves. Every picture is a real one, the best there is, in the phone's order: his league's own cut-out
  (the Premier League's for a Premier League club, La Liga's for a La Liga club: head to waist in the club's kit, found
  by the club's and his names as the phone finds them), else ESPN's head and shoulders, both rising out of the card's top;
  else FotMob's face, as a portrait filling the card's top, cut to its shape; with no picture at all, the club's crest
  large and his initials. No shirt, head or figure is ever drawn in a picture's place. Left and Right glide the row a
  player on 07's ease (held, it glides on); Up goes to the pills, where Left and Right or OK pick one and Down comes
  back; OK on a card opens the player's page; Back closes. Management shows the manager and any staff FotMob lists, as
  cards in the same row. The squad is FotMob's (the club found once by its name in FotMob's search, its id then kept on
  this TV), with ESPN's roster read beside it for its players' head and shoulders, else ESPN's roster alone for the
  competition the club was met in (it names no manager: ESPN's list for a club is its managers of years gone by); the
  league's pictures are looked up as the cards come near.
- **A player's page:** in the same look, under a "‹ back" line with the club's name: his card of glass with his real
  picture rising out of it (or his face as a portrait in it, or the crest and his initials) and his number in the
  club's coin at its shoulder, his first name and surname huge, his position, his country with its flag and his age, a
  rule, what the figures are for, then this season's figures from FotMob for his main competition on glass tiles four
  across (appearances, goals, assists, clean sheets for a goalkeeper, yellow and red cards, minutes, rating; a
  manager's matches, won, drawn and lost at the club), each left out when the source has none. Round arrows at the sides
  when there is someone that way: Left and Right go to the previous and next of the squad as the club's page showed
  them (the same pills), sliding across; Back goes back, the club's row on him. His figures are one read of FotMob once
  the remote rests on him; the players either side are read after him.
- **An F1 driver's page:** in 07, under a "‹ Sports · Formula 1" line: the page dark with his team's colour glowing
  from its left, a big rounded card in that colour, his car number huge, italic and faded behind him, and the driver
  standing in his kit over the card's left half, rising above its top (formula1.com's picture, from the same family of
  addresses as the team logos; else ESPN's head and shoulders; with no picture of him at all, his team's logo large with
  his initials, never a drawn figure). At the right the next Grand Prix, its days, name and circuit with a countdown in
  days, hours and minutes on glass tiles, then his team's name very large with its logo on a glass tile, his number, his
  country's flag and his full name, his nationality and age (no source gives a place of birth), and his team's car from
  the side breaking out past the card's edge (formula1.com's, else a car drawn in the team's colour). Down the left his
  season so far under its year: Grands Prix and podiums (one read of his results this season, Jolpica's) and points
  (the championship's). Left and Right go to the driver before or after him in the championship, the page sliding across
  and its colour turning to the next team's.
- **Moving about:** on the dark screens the picture behind cross-fades as the remote moves to another title or
  channel, the words fade in and rise into place a moment after, pages cross-fade into each other, and the poster or
  tile the remote is on grows a little inside a soft white ring. Opacity, a small move and scale only, nothing
  blurred, and each picture read once at the size it is shown, so it stays light on a TV with 1 GB of memory.
- **Movies and TV shows:** the top bar (Home, TV shows, Movies, Recently added, Search and My list, with Homefield's
  mark in the middle and the clock at the right; Up from the rows goes to it), then the picture of the title the remote
  is on across the page in that picture's colour, under its TMDB logo (or its name in big lettering), its facts (rating,
  years, age, seasons or running time, 4K, Dolby Vision, Atmos, TorBox's cloud) and its story. Under it rows of
  artwork: Continue watching (wide pictures with the episode, the time left and a red bar), My list, Recently added,
  Your TorBox, then every category, each ending in "All 1,234" where it has more than the row shows, which opens all
  of them as a wall of posters with a sort (as in the playlist, by name, newest first or best rated). The poster the remote is on grows with a white edge;
  the rows glide up and along, and the picture and its words fade to the next as the remote moves.

- **Posters**, in Search and My list: the poster with
  rounded corners and a caption strip of the panel's colour under it with the title and year, cut short with "..", the
  rating in an accent chip at the poster's foot; "New" on the top left for a title the playlist added in the last
  fortnight, the best picture the family's playlists have of it on the top right (4K, 1080p or 720p in a white chip,
  with the Dolby Vision or Atmos mark where a name says so), TorBox's green cloud on one of the family's own TorBox
  titles (without a TMDB poster, a card in a colour of its own with its title), and how far through it is along the
  poster's foot. Where the remote is, the strip takes the focus colour and a thin light edge goes round the card.
  Settings, Appearance, Posters: Landscape makes every card 16:9, with the title's wide backdrop (TMDB's at w780, the
  poster where there is none) and the same strip under it.
- **A title:** its picture across the whole page, the page in its colour (a series' changing with the season), the
  title's TMDB logo (or its name in big lettering), a line of facts (★ rating, the years it ran, its age, its seasons
  or running time, the picture and sound marks, TorBox's cloud), then the red Play pill (Resume, or Resume S2 E4, and
  From the start where it was left part-way) and grey buttons: My List (a tick once on it), a film's trailer and, once
  something was started, taking it off Continue watching. A series then has its seasons as the phone has them: the
  season pills, the white one under the season the remote is in, over one row of every season's episodes, the trailer
  first; Left and Right go along the episodes and the pill follows into the next season, or along the pills and the
  row jumps to that season's first episode. The episode the remote is on grows and shows its name. Down from there the
  page rises: Overview and Genres side by side, the cast as round photos with the parts they play (OK searches for
  someone), and More like this, the poster the remote is on lifted. TMDB's logo, cast photos and seasons' posters come
  with the key the TV launcher hands over (kept in memory only), and are kept on this TV for a fortnight.

- **Choose a source:** Play on a film's page (and From the start, and an episode chosen) first lists every source it
  has, as the phone's Choose a source: the family's own TorBox files of it, then the playlists' copies, each source
  under its own heading, each copy with its mark (TorBox's green with a cloud, a playlist's blue with a screen and its
  name), the logos of its picture, HDR and sound as its name says them (4K Ultra HD, Dolby Vision, HDR10, Dolby Atmos,
  Dolby and its channels, DTS-HD 7.1…) and its file's kind, and its file's name and size under them. The best is first,
  marked Best match, with the remote on it: OK plays the one chosen, and the rest stay its backups in that order, for
  Change source while it plays; Back goes back to the page. With one source it plays straight away. Resume and Continue
  watching carry on straight away from the copy it was started on (the others after it), and the next episode starts
  straight away from its best copy, as on the phone.
- **Player:** the playback bar, in the dark look of catch-up's (a scrim from the foot, the amber knob with pause or
  play in it, the time watched under it, the next episode on a dark card at the end of one). OK pauses and plays, Left and Right go back and on (faster when held), Back keeps the
  place, and a series goes on to the next episode. It plays from every copy there is, the best first, tried in turn:
  the family's own TorBox files of the film or episode first (all of them found before anything plays, unless Settings
  put them last), then the playlists' copies. Over the title, where it is playing from: TorBox's green mark with a
  cloud, or the playlist's blue one with a screen and the playlist's name, and what the copy's name says ("1080p ·
  MKV"). Up lists every copy as Choose a source does, the one playing ticked; another one chosen carries on from the
  same moment.
- **TorBox:** films and episodes already in the family's own TorBox account (its finished torrents, usenet and web
  downloads) play as copies beside the playlists', matched by their names: the title, with its year no more than a year
  apart, and a series' season and episode (from the file's name, its folders or its download's name). Every version is
  offered, the best first, with no limit on size, the same on every TV and on the phone: 4K, then 1080p, then 720p,
  then the rest; within the same picture Dolby Vision or HDR before SDR, then Dolby Atmos or surround before stereo,
  then the bigger file; AVI and the like after all the others. The playlists' copies go the best first too, where their
  names (or categories) say; TorBox's stay before them unless Settings say last. Films and series in
  the account that no playlist has are listed as titles of their own under Your TorBox (a film by its name and year, a
  series by its name, with its seasons and episodes from the files), in Movies and TV shows, their grids and on Home; a
  series' episodes that only TorBox has join the playlist's. Their posters, stories, cast and episode names come from
  TMDB where it knows the title, with the key the TV launcher on this TV hands over (launcher 5.33 and later; kept in
  memory only, never written down, logged or shown, and sent only to TMDB); what TMDB said is kept on this TV for a
  fortnight, and without it the posters are drawn. They are read again as the app comes back after a quarter of an
  hour, and when TorBox is turned on, checked or forgotten. Nothing is searched for or added to the account. The API key
  stays on this TV, is never shown again once saved and never written to the log, and goes only to TorBox: in its
  requests, and in a file's address as it starts.
- **Search:** from the menu (or Down held while watching): the microphone at the top left (voice search, where the TV
  has it), the search field, and search's settings at the top right. Under them the searches made before as chips in
  two columns, with a bin to clear them; OK on one searches it again. The keyboard floats at the bottom right while
  typing, its strip suggesting names found, and what is typed is looked for as it is typed (once the typing rests for
  180 ms, off the main thread): Movies, Channels and TV shows, each a row with the playlists it was found in as chips
  (Your TorBox for the family's own titles), films and shows as posters, channels as tiles with their logo, name and
  what's on. The results are dimmed while the remote is on the keyboard or the field. Back closes the keyboard, the
  remote left on the field; Down goes on to the first row's playlists, then its results; OK on a channel watches it, on
  a title opens its page. Settings, Other, Search: Show search history and Show favorite channels first.
- **Recordings:** from the menu: the recordings by day, newest first, as History lists programmes (the programme, its
  day and times, Scheduled, Recording, how many days it is kept, or Gone, and the channel), the one the remote is on
  described in a card at the right, and "Recordings" at the top right with its settings' button. OK plays one that has
  begun; Right, then OK on the bin, deletes one. The family's provider allows one stream at a time and Homefield TV
  writes no video to the TV, so a recording is a programme kept from its channel's catch-up: chosen in the TV guide, it
  plays from the provider's archive, from a few minutes before it to a few minutes after (Settings, Other, Recording),
  for as long as the provider keeps it. A channel without catch-up can't be recorded, and the screen says so. Empty:
  the record mark, "No recordings" and how to make one.
- **My list:** from the menu: its four lists down a column (My TV programs, My reminders, My movies, My TV shows), the
  one the remote is on beside it at once: the programmes added to My list from the TV guide each time they are on now
  or to come, soonest first (OK on one on now watches it); the reminders set, soonest first (the bin cancels one); the
  films and series on My list as posters under their words. An empty list shows the bookmark and "No programs" (or
  reminders, movies, TV shows).
- **Settings:** a panel down the right over the screen it was opened from, that screen dimmed, with the sections
  General, Playlists, EPG, Appearance, Playback, Remote control, Parental controls, Other and About. OK opens one as a
  page that slides in from the right with its name in the panel's band; Back goes back a page, then closes the panel.
  Each row has its value under its name, a switch at the right where it is on or off, and section names are in the
  accent; a choice opens its list with the one in use ticked; a field is typed on a keyboard over a sheet risen from
  the foot, a password only ever as dots.
  General: starting when the TV starts; the screen it starts on (Home, Live TV with the last channel, the TV guide,
  Movies or TV shows); Back up data (settings, playlists, favourites, recent channels, reminders and My list saved to a
  file on the TV and in Downloads; never the TorBox key, and nothing sent over the network) and Restore data (this TV's
  backup or a file picked); Sync (Sync now and when it last synced, and a switch for each kind: playlists, favourites
  and recents, Continue watching, settings, passed on by the launcher on this TV to the family's phones, tablets and
  other TVs; TorBox's key stays on each device); and Remove everything (from this TV only).
  Playlists: each playlist with its expiry date, opening on its own page (its name, its address or server and login,
  the password as dots, its own user agent, using it alone or all of them together, removing it); TorBox (on or off, its
  copies first or last, the API key typed as dots, Check with TorBox: how many films and episodes it has ready, or
  TorBox's word why not, and Forget); adding an M3U playlist or an Xtream Codes login; Update all playlists and how
  often; and catch-up ("Catch-up: 120 channels keep programmes for up to 7 days", or "Your provider keeps no catch-up
  for these channels").
  EPG: its sources (the playlists' own guides, then more XMLTV guides that fill in what they leave out), a time shift
  for a guide whose times are out, how often it is read again and reading it as the app starts, Update EPG, Clear EPG,
  and the latest update ("EPG is successfully updated for 16436 channels on 17/11/2026 08:34").
  Appearance: TV guide (Channels sorting: By order in playlist as it comes, By name, By number or Manual, a group able to
  have its own; Channel names editor, on as it comes; channel numbers, the guide's rows, progress bars; Hidden channels,
  each one hidden with OK to show it again and Show all channels; Manage groups: Show all groups, Hide all groups and
  each group's page, shown or hidden (out of the guide, Live TV, channel up and down and Search, favourites staying),
  moved, renamed and its Sort channels); Player (the clock, 24-hour as it comes, 12-hour or off; how long the panels
  stay; live TV's channel tiles); Groups (how they are sorted); Logos (Logos priority: New logos (Homefield) as it comes,
  or Playlist's logos; Refresh logos, with how many logos the list has and from when; and the logos on black tiles,
  each trimmed and set in the middle with a dark one turned light, or as they come); Font size; Color theme: a primary
  colour (Indigo as it comes, Blue, Deep purple, Purple, Teal, Green,
  Red, Brown, Blue grey, Grey or Black) with an accent (Blue as it comes, Red, Pink, Purple, Indigo, Cyan, Teal, Green,
  Lime, Yellow, Amber, Orange or Deep orange), each a disc, the pair in use ticked and named ("Indigo • Blue"); OK keeps
  one and the screens start again in it, back on Settings. The themes from before are gone: a TV that had one comes up
  in Indigo • Blue. Then Posters (Portrait / Landscape) and Programme times (24-hour as it comes, or 12-hour).
  Playback: the buffer for live TV and for films, the decoders, matching the TV's frame rate and tunnelled playback;
  Picture: one Picture quality (Adaptive, which stays on the channel picked and uses its own copies as seamless quality
  levels, or a fixed 4K, 1080p, 720p, SD or Data saver) and the picture's shape for channels and for films (Normal,
  Full, Zoom, Zoom - 2.35:1, Reduced, 4:3, 16:9, 2.35:1; a channel can keep its own); Sound: passed through or decoded,
  two speakers, languages and every channel's sound delay; Subtitles on live TV (Off, From the channel or Automatic),
  their look and size, and where the TV needs it the English speech model (downloaded from there on OK, with how far
  it has come, or "On this TV"); Stream problems: reconnecting and the provider's adaptive stream. MENU (or OK held)
  while watching brings up the player's bar instead: its Display mode, Audio offset and Audio tracks change the shape,
  the delay and the track for the channel, and its Settings opens this panel.
  Remote control, Player: for live TV full screen, Up and Down, the channel keys' way round, OK and OK held, how long
  the number keys wait, the channel before, and the colour keys.
  Parental controls: a switch for a four-number PIN, asked before a locked category's or channel's programme plays
  (channels locked from Channel options on the player's bar), locked channels shown or hidden, and Settings locked too.
  Other: Search (Show search history, Show favorite channels first), Reminders (the ones set, soonest first, OK
  cancelling one, and whether their card may pop up over other apps), Recording (minutes before a programme starts and
  after it ends), VOD (Autoplay next episode) and Scoreboard.
  About: the version, and Updates: Check for updates (it also looks quietly as the app starts) and when it last
  looked; when a newer Homefield TV has been put on this TV (update.json and the APK in the app's own folder,
  files/update/), its name, what's new and Update now. The APK is checked first (its size, its SHA-256, that it is
  Homefield TV, newer, and signed with the same key), then handed to Android's installer, which asks before it updates;
  the first time, Android asks to let Homefield TV install apps. A dot on Settings shows one is waiting, and What's new
  shows once after an update, as a card over the screen.
- **From the phone:** while Homefield TV is open (or playing), the family's phone can send it the channel it is
  watching (its Live TV's cast button). The TV makes itself known on the home network (`_homefieldtv._tcp`, with its
  name), listens on port 47830 when it is free (the phone tries it when the announcement doesn't reach it) and answers
  only addresses on that network: the channel is played only when this TV's own playlists have it (by its stream id,
  else its guide id, else its tidied name), with "Playing … from …" for a moment, and the phone's Stop lets go of it
  (back to Home). The phone's stream address and login are never used.

## Getting a playlist on to a TV

On the TV: Settings, then Add an Xtream Codes login (or Add an M3U playlist), typed on the on-screen keyboard. A playlist
is kept only when it gives live channels.

From a computer with ADB connected to the TV (the setup entry point needs a permission only the ADB shell has):

```
adb shell content insert --uri content://com.gathin.homefieldtv.setup/xtream \
  --bind name:s:Home --bind server:s:http://host:port --bind user:s:NAME --bind pass:s:PASSWORD
adb shell content insert --uri content://com.gathin.homefieldtv.setup/m3u --bind name:s:Home --bind url:s:'http://…'
adb shell content query --uri content://com.gathin.homefieldtv.setup/playlists
adb shell content delete --uri content://com.gathin.homefieldtv.setup/playlists/p1
adb shell content insert --uri content://com.gathin.homefieldtv.setup/torbox --bind key:s:KEY
adb shell content delete --uri content://com.gathin.homefieldtv.setup/torbox
```

The login stays in the app's private storage on that TV. It is never logged, and the app shows the password only as dots.
TorBox's API key is kept the same way and checked with TorBox at once: the answer is how many films and episodes it has
ready, or TorBox's word why not.

## Building

JDK 17, Android SDK 35. In this folder:

```
./gradlew --no-daemon assembleRelease testReleaseUnitTest lintRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`, signed with the debug key for sideloading.

## Versions

| Version | What changed |
|---|---|
| 1.0 | The first version: Home, the TV guide, live TV with the channel banner, Movies and TV shows from the playlists, title pages with seasons and episodes, the player with the playback bar, Search and Settings. |
| 1.1 | Channel logos on black tiles, drawn as the TV launcher and the phone draw them: in the guide (the channel column and the large logo at the top), the channel banner's cards, Home's channel cards and its large picture, and Search. Each logo from the playlist is trimmed and set in the middle of a black tile; a white JPEG box round it is taken away and a mostly dark logo is turned light. Settings, Channel logos: on black tiles, or as the playlist gives them. |
| 1.2 | Films and TV shows in a new dark look: the rail; every category at a glance under a big picture of the title the remote is on; the side panel of lists (All categories, all of them, Continue watching, My list, the categories with their counts) and a grid for each; posters drawn as the phone's (a fade into the poster's own colour, New, 4K / 1080p / 720p and Dolby marks from the playlists' names, genre and rating, title and year); a title's page with the backdrop full screen, Play and round buttons, the story, facts and cast, a series' season pills over one row of every episode, the trailer, the cast and more like it. My list (the heart). Names lose the picture and sound tags after them. Outfit (SIL Open Font License 1.1) for the dark screens. |
| 1.3 | The TV guide and live TV in the dark look. The guide in the phone's guide's colours, with the categories (and their counts) down the side, the channel's band at the top over the programme's picture, and a strip under the channel's row with the programme's picture from Sky's public UK guide, its times, what it is and two lines about it. OK while watching brings up the banner with the picture's size and frame rate as the player has them and a row of the last ten channels watched on this TV; Up and Down bring up the live screen, the programme's picture behind and each category's channels as tiles with a picture of what's on, its title and a line about it. Pictures cross-fade, words fade and rise, pages cross-fade, and posters and tiles grow inside a ring as the remote moves. |
| 1.4 | Colour themes in Settings, Theme: Homefield (as it came), Midnight blue, Emerald, Crimson and Graphite, each with its own ground, panels, cards, accent, focus rings, guide colours and New badge, and the blue screens tinted to match; a picture of the app under the list changes as the remote moves through them, and the one chosen is kept. Settings, Posters: Portrait / Landscape: landscape cards are 16:9 with the title's backdrop, as the phone's. The family's phone can send the channel it is watching to the TV while Homefield TV is open: played only when the TV's own playlists have it, from the home network only. |
| 1.5 | Updates from inside the app: Settings, Updates shows the version on this TV, Check for updates and when it last looked. A newer version put on this TV (update.json and the APK in the app's own folder) is offered once its size, SHA-256, package, version and signing key check out: its name, what's new and Update now, which hands it to Android's installer (Android asks first; the first time it asks to let Homefield TV install apps). A dot on Settings while one waits; it looks quietly as the app starts; What's new once after an update. From the phone: its Stop lets go of the channel it sent, and the TV listens on port 47830 when it is free. |
| 1.6 | Catch-up: programmes from the provider's archive (Xtream Codes' tv_archive in the server's time zone, M3U catchup, catchup-days and catchup-source of every kind) and the programme on now from its start. The guide goes back into the archive's programmes with a small circle arrow on each (OK plays one; Back comes back to now); the channel banner has Watch from the start; a bar across the programme with the live edge, the time in a bubble as it moves, Left and Right 10 seconds a press and faster held, OK to pause, Up to go live, on into the next programme. OK held in the guide and on live TV brings up a small menu (watch, from the start, favourites). Settings says how many channels keep programmes and for how long. Sports hub and scoreboard in the player: Sports in the menu with today's games, a page for each (score, clock, events, live stats, line-ups) held back to the channel, and the scoreboard over a match with its alerts, pop-ups and line-ups, and a Scoreboard section in Settings. |
| 1.7 | Subtitles on live TV and catch-up, for every channel: Off (as it starts), From the channel (DVB subtitles where the channel puts them, subtitle playlists, captions) or Automatic, made on the TV from what's said as it's said, with a channel's own showing instead when it plainly sends some. In the channel's menu (OK held, Subtitles) and Settings, Live TV, with their look (white with an outline, on a black box, yellow) and size. The words sit near the foot, move up above the banner and the catch-up bar, and go under the live screen's rows. Automatic uses the TV's own speech recognition where it has it (Android 13 and later), else asks before a one-time download of Vosk's small English model (about 40 MB, about 70 MB on the TV; Apache 2.0, with JNA); nothing heard leaves the TV. |
| 1.8 | Reminders: in the guide, OK held on a programme to come, Remind me, and a bell on its block; a minute before it starts a card pops up over whatever is on the TV, other apps too (the channel's logo, the title, "Starts in 1 min"): OK opens the channel, Back puts it away, gone after about 20 seconds. Display over other apps asked for once, with a notification instead where it isn't allowed; never wakes a TV that is off; kept after a restart; listed in Settings, Reminders. Player settings: buffer size, Picture quality (Adaptive keeps you on your channel as the quality follows your connection), decoder, aspect and zoom, frame-rate matching, sound passthrough and delay; the channel banner's time; the player's menu on MENU. |
| 1.9 | TorBox in Movies and TV shows: films and episodes already in the family's own TorBox account play as copies beside the playlists', every version of them, all found before anything plays and tried first (or last, as Settings say); nothing is searched for or added. The player's bar shows where it plays from (TorBox's green mark with a cloud, a playlist's blue one with a screen) and Up lists every copy to change source while watching, from the same moment. Settings, TorBox: on or off, first or last, the API key (typed as dots, or sent from a computer over adb), Check and Forget. Settings: start screen, start with the TV, clock and text size, guide rows, what OK, the colour keys and the number keys do. Sync: your playlists, favourite channels, categories, recents, Continue watching and shared settings follow you between your phones, tablets and this TV (needs the TV launcher 5.32); Remove everything resets this TV only. A reminder due while the TV's screen is off now waits and shows once it is on, while its programme has only just begun. |
| 1.10 | Your TorBox: the films and series in the family's own TorBox that no playlist has, as titles of their own, in a Your TorBox row on Home and first on Movies and TV shows, in their grids, with TorBox's green cloud on each poster. Their posters, stories and episode names from TMDB where it knows them (the key from the TV launcher 5.33, kept in memory only), drawn posters without. A series' episodes that only TorBox has join its row, each with the cloud. On a TV that shows 4K, TorBox's copies go 4K under 20 GB first, then 1080p, then the rest. Settings: the guide's and playlists' updates, a guide time shift and more XMLTV guides; live categories hidden, moved and renamed; parental control with a PIN for locked categories, channels and Settings; backup and restore, on this TV or from a file, without the TorBox key. |
| 1.11 | Every source shown before playing, the best first: Play lists every TorBox file and playlist copy of a film or episode, each with its mark, the logos of its picture, HDR and sound, its file's name and size; the best has the remote and OK plays it, the rest staying as its backups for Change source. One order everywhere, with no limit on size: 4K, then 1080p, 720p and the rest; Dolby Vision or HDR before SDR; Dolby Atmos or surround before stereo; then the bigger file; AVI and the like last; TorBox's before the playlists', which go the best first too. Resume and Continue watching carry on from the copy it was started on; the next episode starts from its best copy, as on the phone. |
| 1.12 | The dark look everywhere, from the first open: Home, Search, Settings and the player's bar now look like Live TV, the guide, films and TV shows, and the app opens on its dark ground. Home has the rail (where the remote starts, as on the old menu), the big picture of what the remote is on, the mini TV at the top right on a dark card with the channel and what's on, and rows laid out as Films' are: On now and Sport on now as the live screen's tiles, Continue watching, Your TorBox, New films and New TV shows as posters. Search and Settings have the rail, Settings its sections on a side panel and the keyboard on a dark sheet; What's new, Updates and the PIN pad are dark too. The colour themes work on every screen, and a new icon and TV banner carry Homefield's amber mark. No more freezes with very big playlists: the channel lists and their counts, hidden and locked channels, Home's rows, the guide's categories, channel up and down, search, and films and TV shows with tens of thousands of titles are worked out off the main thread or kept ready, tried with 50,000 channels and 40,000 titles. With 1.11's every source shown before playing, the best first. |
| 1.13 | TiviMate's look and ways, on Homefield's own screens, in the Indigo • Blue theme the family's TiviMate uses (TiviMate's other primary and accent colours in Settings › Appearance). The icon rail that opens into the labelled menu; Settings as the panel from the right, in TiviMate's sections; Roboto; times written "10:00 — 11:00" and a 24-hour clock. Watching: the info card (INFO, and CH+/− showing the next channel without tuning), Up and Down changing channel, Right for the last channel, Left for the channel list over the picture with its groups, OK for the overlay with its seek line and tiles (TV guide, History, recent channels), the bar of round buttons with its pickers (picture, audio, audio offset per channel, captions, display mode with three new shapes, sleep timer, favourites, scoreboard, channel options), History, the description card and catch-up's player; Back to the guide. The guide: the preview top left with the programme's details, the dated time ruler with the now-dot, rows with number, logo tile and name, neutral blocks, the menu pushing it right, groups, the OK popup and the long-OK card (Remind, Watch from start, Record, My list, Favourites, Catch-up, Block channel), and the new catch-up archive by day. Search with the mic, history and results as you type; Movies and TV shows with the category column, poster rows and grid, title and series pages; My list; Recordings kept from catch-up (one stream per account, so nothing is recorded live). New settings that work: search history, favourite channels first, autoplay next episode, update or clear the guide now, groups sorting. |
| 1.13.1 | The screen stays on while a channel or a programme from the archive plays (or starts, or reconnects): the TV's screensaver no longer comes over what is being watched. Paused, stopped or failed, the TV may sleep as before. |
| 1.14 | Faster, smoother and lighter. Scrolling glides everywhere (the guide's rows and times, lists, groups, the menu, Search), holding a button keeps it moving smoothly, and the guide draws without making anything per frame. When a channel buffers, the app no longer redraws or calls the system on every stall, and a failing stream waits longer between tries. Memory: the player's buffer is capped (it could hold about 138 MB), pictures stay within a 40 MB cache, and memory is freed when the TV runs low. The guide's details and the player's channel card show the programme's picture behind them, changing as you move; FPS shows on the channel card, the OK overlay and the guide, measured from the picture when the channel doesn't say. |
| 1.15 | Movies, TV shows and a title's page in the TV show card look: the title's picture across the page in its own colour, its logo (or big lettering), the red Play pill, rows of artwork with the remote's lifted; a series keeps the phone's seasons (one row of every episode, the season pill following it, the page's colour changing with the season), then Overview and Genres, the cast's faces and More like this. Home is one large card at a time (channels, Continue watching, films and shows) with a strip of episodes, next programmes or like titles; the picture cross-fades as the remote moves and the page takes its colour. Hold OK in the guide for TiviMate's channel menu (Favourites, Block, Hide, Rename, Change logo, Move channel) and, on a group, Rename, Hide, Move, Sort channels and Manage groups; Settings › Appearance › TV guide has Hidden channels and Manage groups. New channel logos (the tv-logo project's, matched by name; Logos priority and Refresh in Settings › Appearance › Logos). The scoreboard and logo cover show only over a channel playing full screen, never over the guide, a menu or a panel. |
| 1.16 | Sports has Today, Tables and Formula 1: OK on a club opens its page in the club's colour with the squad as a carousel (Players or Management, the line filters, Left/Right gliding; the photo rising above the card, the number coin, the surname large), OK on a player opens his card with this season's figures, and an F1 driver's page comes in the team's colour with the giant number, the standing driver, the car and the next Grand Prix's countdown, Left/Right through the championship. The kick-off, restart and second-half pop-up now comes in the scoreboard's own look and folds onto it ("In my scoreboard's look", the default; Classic and Off kept). Nothing on the board from the live data comes before the picture shows it: the minutes added, cards, subs and VAR explained wait the channel's delay (three minutes with none set), and every look shows added time by the same rule. |
