#!/usr/bin/env python3
"""Prépare la base de logos pour l'APK : brands/logos/ → app/src/main/assets/brands/.

- SVG minifiés par svgo (multipass, via npx) ;
- SVG qui ne font qu'emballer un raster (data:image) : le raster est extrait tel
  quel — un « SVG » de 270 Ko cachant un PNG n'a rien de vectoriel ;
- rasters ramenés à MAX_PX de côté au plus (jamais agrandis), puis convertis en
  WebP q85 (cwebp) quand la conversion fait gagner de la place.

L'app résout les logos par nom de base : l'extension peut changer sans toucher
brands.json. Rejouable après tout ajout ou remplacement de logo.
Dépendances : python3, node (npx svgo), cwebp. Usage : tools/optimize-logos.py
"""

import base64
import re
import shutil
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "brands" / "logos"
DST = ROOT / "app" / "src" / "main" / "assets" / "brands"
# Logo de l'écran carte : 220 dp de large, soit ~770 px en xxhdpi.
MAX_PX = 768
RASTERS = {".png", ".jpg", ".jpeg", ".webp"}


def raster_size(data: bytes) -> tuple[int, int]:
    """Largeur et hauteur d'un PNG, JPEG ou WebP, lues dans l'en-tête."""
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return struct.unpack(">II", data[16:24])
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        chunk = data[12:16]
        if chunk == b"VP8X":
            w = int.from_bytes(data[24:27], "little") + 1
            h = int.from_bytes(data[27:30], "little") + 1
            return w, h
        if chunk == b"VP8L":
            bits = int.from_bytes(data[21:25], "little")
            return (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1
        return struct.unpack("<HH", data[26:30])[0] & 0x3FFF, struct.unpack("<HH", data[28:30])[0] & 0x3FFF
    if data[:2] == b"\xff\xd8":
        i = 2
        while i < len(data):
            marker, length = data[i + 1], struct.unpack(">H", data[i + 2:i + 4])[0]
            if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
                h, w = struct.unpack(">HH", data[i + 5:i + 9])
                return w, h
            i += 2 + length
    raise ValueError("format raster non reconnu")


def embedded_raster(svg: str) -> tuple[str, bytes] | None:
    """Le raster d'un SVG qui n'emballe qu'une image, sinon None."""
    if re.search(r"<(path|rect|circle|ellipse|polygon|polyline|line|text)\b", svg):
        return None
    m = re.search(r"data:image/(png|jpe?g|webp);base64,([A-Za-z0-9+/=\s]+)", svg)
    if not m:
        return None
    ext = ".jpg" if m.group(1).startswith("jp") else "." + m.group(1)
    return ext, base64.b64decode(re.sub(r"\s", "", m.group(2)))


def optimize_raster(path: Path, data: bytes, work: Path) -> None:
    w, h = raster_size(data)
    src = work / path.name
    src.write_bytes(data)
    out = work / (path.stem + ".webp")
    resize = ["-resize", str(MAX_PX), "0"] if w >= h else ["-resize", "0", str(MAX_PX)]
    args = ["cwebp", "-quiet", "-q", "85", "-alpha_q", "100"]
    if max(w, h) > MAX_PX:
        args += resize
    subprocess.run(args + [str(src), "-o", str(out)], check=True)
    if path.suffix == ".webp" or out.stat().st_size < len(data) or max(w, h) > MAX_PX:
        shutil.copy(out, DST / out.name)
    else:
        (DST / path.name).write_bytes(data)


def main() -> int:
    if DST.exists():
        shutil.rmtree(DST)
    DST.mkdir(parents=True)
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        svgs = work / "svg"
        svgs.mkdir()
        for f in sorted(SRC.iterdir()):
            if f.suffix == ".svg":
                text = f.read_text(encoding="utf-8")
                raster = embedded_raster(text)
                if raster:
                    ext, data = raster
                    optimize_raster(Path(f.stem + ext), data, work)
                    print(f"   {f.name} : raster embarqué extrait")
                else:
                    shutil.copy(f, svgs / f.name)
            elif f.suffix.lower() in RASTERS:
                optimize_raster(f, f.read_bytes(), work)
        subprocess.run(
            ["npx", "--yes", "svgo", "--multipass", "--quiet", "-f", str(svgs), "-o", str(DST)],
            check=True,
        )
    files = sorted(DST.iterdir())
    total = sum(f.stat().st_size for f in files)
    print(f"{len(files)} fichiers, {total / 1e6:.1f} Mo dans {DST.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
