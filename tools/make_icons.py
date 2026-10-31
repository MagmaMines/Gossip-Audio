#!/usr/bin/env python3
"""GossipAudio icon pipeline.

Turns GossipAudio.png (the brand note glyph) into every launcher asset the
Android build needs:

  * legacy raster launcher icons (mipmap-*/ic_launcher.png)
  * adaptive-icon foreground layers (mipmap-*/ic_launcher_foreground.png)
  * round launcher icons (mipmap-*/ic_launcher_round.png)
  * a 1024px Play-Store icon (assets/store/playstore-icon-1024.png)
  * an in-app brand mark (drawable-nodpi/gossipaudio_logo.png)

Usage:  python3 tools/make_icons.py
"""
from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "GossipAudio.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")

YELLOW = (245, 197, 24, 255)      # brand yellow
DARK_YELLOW = (138, 106, 0, 255)  # outline shade
INK = (11, 11, 12, 255)           # system black
WHITE = (255, 255, 255, 255)

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}


def load_glyph() -> Image.Image:
    """Extract the yellow note glyph from the logo as an RGBA sprite."""
    im = Image.open(SRC).convert("RGBA")
    px = im.load()
    w, h = im.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    opx = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            # yellow-ish (incl. the darker outline of the original art)
            if r > 150 and g > 110 and b < 130 and (r - b) > 70 and a > 40:
                opx[x, y] = (r, g, b, 255)
    bbox = out.getbbox()
    if bbox:
        out = out.crop(bbox)
    return out


def fit(sprite: Image.Image, box: int) -> Image.Image:
    """Scale a sprite so its longest edge is `box`, keeping aspect ratio."""
    w, h = sprite.size
    scale = box / max(w, h)
    return sprite.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)


def centered(canvas: Image.Image, sprite: Image.Image) -> Image.Image:
    x = (canvas.width - sprite.width) // 2
    y = (canvas.height - sprite.height) // 2
    canvas.alpha_composite(sprite, (x, y))
    return canvas


def rounded_mask(size: int, radius: int) -> Image.Image:
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return m


def circle_mask(size: int) -> Image.Image:
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.ellipse((0, 0, size - 1, size - 1), fill=255)
    return m


def legacy_icon(size: int, round_icon: bool, glyph: Image.Image) -> Image.Image:
    """Black squircle + yellow glyph + dark-yellow hairline, like the brand UI."""
    ss = 4  # supersample for smooth edges
    s = size * ss
    base = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(base)
    inset = max(1, int(s * 0.02))
    if round_icon:
        d.ellipse((inset, inset, s - inset, s - inset), fill=INK)
        d.ellipse((inset, inset, s - inset, s - inset), outline=DARK_YELLOW, width=max(2, int(s * 0.03)))
    else:
        r = int(s * 0.26)
        d.rounded_rectangle((inset, inset, s - inset, s - inset), radius=r, fill=INK)
        d.rounded_rectangle((inset, inset, s - inset, s - inset), radius=r,
                            outline=DARK_YELLOW, width=max(2, int(s * 0.03)))
    g = fit(glyph, int(s * 0.62))
    glow = g.filter(ImageFilter.GaussianBlur(int(s * 0.035)))
    base.alpha_composite(glow, ((s - glow.width) // 2, (s - glow.height) // 2))
    base.alpha_composite(g, ((s - g.width) // 2, (s - g.height) // 2))
    return base.resize((size, size), Image.LANCZOS)


def adaptive_foreground(size: int, glyph: Image.Image) -> Image.Image:
    # Adaptive icons: 108dp canvas, safe zone is the middle 66dp (~61%).
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    g = fit(glyph, int(size * 0.56))
    return centered(canvas, g)


def main() -> None:
    glyph = load_glyph()
    for dens, scale in DENSITIES.items():
        out_dir = os.path.join(RES, f"mipmap-{dens}")
        os.makedirs(out_dir, exist_ok=True)

        legacy = int(48 * scale)
        legacy_icon(legacy, False, glyph).save(os.path.join(out_dir, "ic_launcher.png"))
        legacy_icon(legacy, True, glyph).save(os.path.join(out_dir, "ic_launcher_round.png"))

        fg = int(108 * scale)
        adaptive_foreground(fg, glyph).save(os.path.join(out_dir, "ic_launcher_foreground.png"))

    os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
    brand = Image.open(SRC).convert("RGBA").resize((512, 512), Image.LANCZOS)
    brand.save(os.path.join(RES, "drawable-nodpi", "gossipaudio_logo.png"))

    # A transparent 512px glyph, used by splash + hero animations.
    spr = fit(glyph, 480)
    spr_canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    spr_canvas.alpha_composite(spr, ((512 - spr.width) // 2, (512 - spr.height) // 2))
    spr_canvas.save(os.path.join(RES, "drawable-nodpi", "gossipaudio_glyph.png"))

    os.makedirs(os.path.join(ROOT, "assets", "store"), exist_ok=True)
    store = legacy_icon(1024, False, glyph)
    store.save(os.path.join(ROOT, "assets", "store", "playstore-icon-1024.png"))
    print("icons written")


if __name__ == "__main__":
    main()
