#!/bin/sh
# Builds assets/broadcast/ (packaged into the TV launcher and the phone app) from src/. Needs Node (npx) for esbuild.
set -e
cd "$(dirname "$0")"
OUT=assets/broadcast
rm -rf "$OUT"
mkdir -p "$OUT/fonts" "$OUT/sfx" "$OUT/flags"
cp src/fonts/*.ttf src/fonts/OFL.txt "$OUT/fonts/"
cp src/sfx/*.ogg "$OUT/sfx/"
# the players' flags for the studio line-ups (Twemoji, CC-BY 4.0: tools/flags.py fetched them)
cp src/flags/*.svg "$OUT/flags/"
cp src/host/*.html src/host/*.js "$OUT/"
cat src/kickoff/scene-part.js src/kickoff/popup-part.js > "$OUT/kickoff.js"
cat src/lineups/head.js src/lineups/body.js > "$OUT/lineups.js"
# the first designs: the kick-off strap in the board's look (page elements), and the line-ups over the ground (the same
# head as the classic line-ups', with their own sequence)
cp src/strap/strap.js "$OUT/strap.js"
cat src/lineups/head.js src/stadium/body.js > "$OUT/stadium.js"
npx --yes esbuild@0.24.0 src/kickoff/trophy3d.js --bundle --format=iife --minify --target=es2019 \
  --alias:three="$(pwd)/src/vendor/three.module.js" --outfile="$OUT/trophy3d.js" --log-level=warning
# the match intro (src/match): three.js and its scenes, one file
npx --yes esbuild@0.24.0 src/match/match.js --bundle --format=iife --minify --target=es2019 \
  --alias:three="$(pwd)/src/vendor/three.module.js" --outfile="$OUT/match3d.js" --log-level=warning
# the studio line-ups (src/studio) and the kit pictures the scoreboards draw (src/kits): three.js and the 3D shirt each
npx --yes esbuild@0.24.0 src/studio/studio.js --bundle --format=iife --minify --target=es2019 \
  --alias:three="$(pwd)/src/vendor/three.module.js" --outfile="$OUT/studio3d.js" --log-level=warning
npx --yes esbuild@0.24.0 src/kits/sheet.js --bundle --format=iife --minify --target=es2019 \
  --alias:three="$(pwd)/src/vendor/three.module.js" --outfile="$OUT/kits3d.js" --log-level=warning
echo "built $OUT: $(du -sh "$OUT" | cut -f1)"
