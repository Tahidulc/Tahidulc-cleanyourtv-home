#!/bin/sh
# Builds assets/broadcast/ (packaged into the TV launcher and the phone app) from src/. Needs Node (npx) for esbuild.
set -e
cd "$(dirname "$0")"
OUT=assets/broadcast
rm -rf "$OUT"
mkdir -p "$OUT/fonts" "$OUT/sfx"
cp src/fonts/*.ttf "$OUT/fonts/"
cp src/sfx/*.ogg "$OUT/sfx/"
cp src/host/*.html src/host/*.js "$OUT/"
cat src/kickoff/scene-part.js src/kickoff/popup-part.js > "$OUT/kickoff.js"
cat src/lineups/head.js src/lineups/body.js > "$OUT/lineups.js"
# the first designs: the kick-off strap in the board's look (page elements), and the line-ups over the ground (the same
# head as the classic line-ups', with their own sequence)
cp src/strap/strap.js "$OUT/strap.js"
cat src/lineups/head.js src/stadium/body.js > "$OUT/stadium.js"
npx --yes esbuild@0.24.0 src/kickoff/trophy3d.js --bundle --format=iife --minify --target=es2019 \
  --alias:three="$(pwd)/src/vendor/three.module.js" --outfile="$OUT/trophy3d.js" --log-level=warning
# the matchday intro: its scenes, the trophies (src/kickoff/trophy-models.js) and three.js in one script
npx --yes esbuild@0.24.0 src/intro/intro.js --bundle --format=iife --minify --target=es2019 \
  --alias:three="$(pwd)/src/vendor/three.module.js" --outfile="$OUT/intro.js" --log-level=warning
echo "built $OUT: $(du -sh "$OUT" | cut -f1)"
