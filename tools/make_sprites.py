"""Generates piece sprite strips /pN.png (12 frames: wP wN wB wR wQ wK bP ... bK) and the app icon."""
import sys, os
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
OUTLINE = "♙♘♗♖♕♔"   # white (outline) glyphs P N B R Q K
FILLED = "♟♞♝♜♛♚"    # black (filled) glyphs

def mask(ch, size, scale):
    big = size * scale
    f = ImageFont.truetype(FONT, int(big * 0.92))
    im = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(im)
    bb = d.textbbox((0, 0), ch, font=f)
    w, h = bb[2] - bb[0], bb[3] - bb[1]
    d.text(((big - w) / 2 - bb[0], (big - h) / 2 - bb[1] + big * 0.01), ch, font=f, fill=255)
    return im

def fill_holes(m):
    # flood fill background from the border; everything not reached is inside the piece
    w, h = m.size
    px = m.load()
    seen = bytearray(w * h)
    stack = [(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)]
    while stack:
        x, y = stack.pop()
        if x < 0 or y < 0 or x >= w or y >= h: continue
        i = y * w + x
        if seen[i] or px[x, y] > 100: continue
        seen[i] = 1
        stack.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    out = Image.new("L", (w, h), 0)
    op = out.load()
    for y in range(h):
        for x in range(w):
            if not seen[y * w + x]: op[x, y] = 255
    return out

def piece(i, white, size):
    scale = 4
    mo = mask(OUTLINE[i], size, scale)
    mf = mask(FILLED[i], size, scale)
    from PIL import ImageChops
    sil = fill_holes(ImageChops.lighter(mo, mf))
    big = size * scale
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    if white:
        img.paste((250, 250, 245, 255), (0, 0), sil)
        img.paste((20, 20, 20, 255), (0, 0), mo)
    else:
        img.paste((35, 35, 35, 255), (0, 0), sil)
        detail = ImageChops.subtract(sil, mf)
        img.paste((215, 215, 215, 255), (0, 0), detail)
    return img.resize((size, size), Image.LANCZOS)

for size in [12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 36, 40, 44, 48, 56, 64]:
    strip = Image.new("RGBA", (size * 12, size), (0, 0, 0, 0))
    for c in range(2):
        for i in range(6):
            strip.paste(piece(i, c == 0, size), ((c * 6 + i) * size, 0))
    strip.save(os.path.join(OUT, "p%d.png" % size), optimize=True)

# icon: white knight on a green rounded square
for s, name in [(48, "icon.png")]:
    big = s * 4
    ic = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(ic)
    d.rounded_rectangle((0, 0, big - 1, big - 1), radius=big // 6, fill=(46, 125, 50, 255))
    d.rectangle((big // 2, big // 2, big - big // 10, big - big // 10), fill=(56, 142, 60, 255))
    kn = piece(1, True, big * 7 // 8 // 4 * 4 * 1)
    kn = kn.resize((big * 7 // 8, big * 7 // 8), Image.LANCZOS)
    ic.alpha_composite(kn, (big // 16, big // 16))
    ic.resize((s, s), Image.LANCZOS).save(os.path.join(OUT, name), optimize=True)
print("done")
