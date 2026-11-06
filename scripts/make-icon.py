"""Generate the iskolarPath app icon from the Lucide 'GraduationCap' vector.

The source vector is packaging/graduation-cap.svg (Lucide icon set, ISC license,
free for commercial use). We recolor its stroke to the app accent and compose it
on the app's dark rounded tile, then emit a 512px preview PNG and a multi-size .ico.

Requires: pillow, svglib, reportlab  (already in the venv; pip install if missing)
Outputs:  packaging/iskolarPath-icon-512.png  +  packaging/iskolarPath.ico
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps
from svglib.svglib import svg2rlg
from reportlab.graphics import renderPM

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "packaging"
SVG_SRC = OUT_DIR / "graduation-cap.svg"

S = 512
ICON_FRAC = 0.64          # icon occupies 64% of the tile
RENDER = 1024             # supersampled vector render size

BG      = (21, 17, 13)    # #15110D app dark tile
BG_EDGE = (32, 26, 19)    # #201A13 subtle rim
ACCENT  = "#F6A85A"       # app accent orange

# --- 1. recolor + render the Lucide vector at high resolution ---
svg_text = SVG_SRC.read_text(encoding="utf-8").replace('stroke="currentColor"', f'stroke="{ACCENT}"')
tmp_svg = OUT_DIR / "_icon_tmp.svg"
tmp_svg.write_text(svg_text, encoding="utf-8")

drawing = svg2rlg(str(tmp_svg))
scale = RENDER / drawing.width
drawing.width, drawing.height = RENDER, RENDER
drawing.scale(scale, scale)

raw_png = OUT_DIR / "_icon_raw.png"
renderPM.drawToFile(drawing, str(raw_png), fmt="PNG", bg=0xFFFFFF)

# --- 2. white background -> transparency (icon is a single saturated color) ---
from PIL import ImageChops
vec = Image.open(raw_png).convert("RGB")
r, g, b = vec.split()
# alpha = max(r,g,b): white bg (255,255,255) -> 0, orange stroke -> ~full
alpha = ImageChops.lighter(ImageChops.lighter(r, g), b)
icon = Image.merge("RGBA", (*vec.split(), alpha))

# --- 3. compose on the rounded dark tile ---
tile = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(tile)
radius = int(S * 0.225)
d.rounded_rectangle([0, 0, S - 1, S - 1], radius=radius, fill=BG)
rim = int(S * 0.014)
d.rounded_rectangle([rim, rim, S - 1 - rim, S - 1 - rim], radius=radius - rim,
                    outline=BG_EDGE, width=rim * 2)

icon_s = int(S * ICON_FRAC)
icon = icon.resize((icon_s, icon_s), Image.LANCZOS)
off = (S - icon_s) // 2
tile.alpha_composite(icon, (off, off))

tile.save(OUT_DIR / "iskolarPath-icon-512.png")
ico_sizes = [256, 128, 64, 48, 32, 24, 16]
tile.save(OUT_DIR / "iskolarPath.ico", sizes=[(s, s) for s in ico_sizes])

tmp_svg.unlink(missing_ok=True)
raw_png.unlink(missing_ok=True)
print("wrote", OUT_DIR / "iskolarPath-icon-512.png")
print("wrote", OUT_DIR / "iskolarPath.ico")
