# =====================================================================
# iskolarPath app icon generator (Pillow).
#
#   py scripts/make-icon.py
#
# Writes packaging/iskolarPath.ico (multi-size) and a 512px PNG master.
# Brand colours match src/main/resources/css/app.css:
#   dark surface #14110C, orange accents #F6A85A / #E8842C / #C2480B.
# =====================================================================

from PIL import Image, ImageDraw

S = 1024  # supersampled master; downscaled at the end


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


TOP = (34, 27, 20)      # #221B14
BOTTOM = (14, 12, 9)    # #0E0C09
BORDER = (58, 46, 30)   # #3A2E1E
ORANGE = (246, 168, 90)  # #F6A85A
ORANGE_DK = (232, 132, 44)  # #E8842C
ORANGE_DEEP = (194, 72, 11)  # #C2480B


def rounded_gradient(size, radius):
    """Vertical gradient inside a rounded-rect alpha mask."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(size):
        colour = lerp(TOP, BOTTOM, y / (size - 1))
        for x in range(size):
            # rounded-corner test
            dx = min(x, size - 1 - x)
            dy = min(y, size - 1 - y)
            inside = True
            if dx < radius and dy < radius:
                inside = (radius - dx) ** 2 + (radius - dy) ** 2 <= radius ** 2
            if inside:
                px[x, y] = (*colour, 255)
    return img


def draw(scale):
    img = rounded_gradient(S, int(0.20 * S))
    d = ImageDraw.Draw(img)

    def p(pt):
        return (pt[0] * scale, pt[1] * scale)

    # mortarboard diamond
    diamond = [p((256, 92)), p((372, 150)), p((256, 208)), p((140, 150))]
    d.polygon(diamond, fill=ORANGE)
    # shaded right half
    shade = [p((256, 92)), p((372, 150)), p((256, 208))]
    d.polygon(shade, fill=ORANGE_DK)
    # head band
    d.rounded_rectangle([p((196, 158)), p((316, 192))], radius=p((12, 12))[0], fill=ORANGE_DEEP)
    # button
    d.ellipse([p((240, 96)), p((272, 128))], fill=ORANGE_DEEP)
    # tassel
    d.line([p((256, 112)), p((340, 240))], fill=ORANGE, width=int(14 * scale))
    d.ellipse([p((316, 216)), p((364, 264))], fill=ORANGE)

    # border ring
    d.rounded_rectangle([p((4, 4)), p((S - 4, S - 4))], radius=int(0.20 * S),
                        outline=BORDER, width=int(6 * scale))
    return img


img = draw(1).resize((512, 512), Image.LANCZOS)

import os
os.makedirs("packaging", exist_ok=True)
img.save("packaging/iskolarPath-icon-512.png")
img.save(
    "packaging/iskolarPath.ico",
    format="ICO",
    sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
)
print("wrote packaging/iskolarPath.ico and packaging/iskolarPath-icon-512.png")
