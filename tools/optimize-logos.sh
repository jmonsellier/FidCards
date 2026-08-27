#!/bin/bash
# Prépare la base de logos pour l'APK (docs/PROJET.md §6.3.2, « reste à faire ») :
# - SVG minifiés par svgo (multipass) ;
# - SVG contenant un raster embarqué (data:image) : rasterisés via qlmanage,
#   un « SVG » de 270 Ko cachant un PNG n'a rien de vectoriel ;
# - rasters redimensionnés à 512 px max (sips), puis convertis en WebP q82
#   (cwebp) quand la conversion fait gagner de la place.
# L'app résout les logos par nom de base, l'extension peut donc changer sans
# toucher brands.json. Rejouable après ajout d'une enseigne.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/brands/logos"
DST="$ROOT/app/src/main/assets/brands"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

rm -rf "$DST"
mkdir -p "$DST"

echo "— SVG (svgo, multipass)…"
npx --yes svgo --multipass --quiet -f "$SRC" -o "$DST" >/dev/null

echo "— SVG à raster embarqué → rasterisation…"
for f in "$DST"/*.svg; do
  [ -e "$f" ] || continue
  if grep -q "data:image" "$f"; then
    name="$(basename "$f" .svg)"
    qlmanage -t -s 1024 -o "$TMP" "$f" >/dev/null 2>&1
    if [ -e "$TMP/$name.svg.png" ]; then
      mv "$TMP/$name.svg.png" "$TMP/$name.png"
      sips -Z 512 "$TMP/$name.png" >/dev/null
      cp "$TMP/$name.png" "$DST/$name.png"
      rm "$f"
      echo "   $name.svg → $name.png"
    fi
  fi
done

echo "— Rasters (512 px max, WebP si plus léger)…"
for f in "$SRC"/*.png "$SRC"/*.jpg "$SRC"/*.webp "$DST"/*.png; do
  [ -e "$f" ] || continue
  name="$(basename "$f")"
  stem="${name%.*}"
  ext="${name##*.}"
  work="$TMP/$name"
  cp "$f" "$work"
  w=$(sips -g pixelWidth "$work" | awk '/pixelWidth/ {print $2}')
  h=$(sips -g pixelHeight "$work" | awk '/pixelHeight/ {print $2}')
  if [ "${w:-0}" -gt 512 ] || [ "${h:-0}" -gt 512 ]; then
    sips -Z 512 "$work" >/dev/null
  fi
  if [ "$ext" != "webp" ] && cwebp -quiet -q 82 "$work" -o "$TMP/$stem.webp" 2>/dev/null; then
    if [ "$(stat -f%z "$TMP/$stem.webp")" -lt "$(stat -f%z "$work")" ]; then
      rm -f "$DST/$name"
      cp "$TMP/$stem.webp" "$DST/$stem.webp"
      continue
    fi
  fi
  cp "$work" "$DST/$name"
done

echo "— Résultat :"
du -sh "$SRC" "$DST"
echo "$(ls "$DST" | wc -l) fichiers"
