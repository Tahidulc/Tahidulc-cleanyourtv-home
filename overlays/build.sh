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
npx --yes esbuild@0.24.0 src/kickoff/trophy3d.js --bundle --format=iife --minify --target=es2019 \
  --alias:three="$(pwd)/src/vendor/three.module.js" --outfile="$OUT/trophy3d.js" --log-level=warning
echo "built $OUT: $(du -sh "$OUT" | cut -f1)"
