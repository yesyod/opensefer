"""
Builds every app-icon asset (iOS, Android adaptive + legacy, Play Store) from one piece of artwork:
a rounded navy tile with an open white book and a gold bookmark cord.

The artwork's tile has baked-in rounded corners and a highlight rim, but both stores want a plain
full-bleed square (the OS applies its own mask). So the script
  1. rebuilds the navy background as a smooth, full-bleed field (push-pull inpainting of the tile's
     own background pixels, extended past the edges and under the book), and
  2. lifts the book + cord (with their soft shadow) off it as an RGBA layer,
then renders each target from those two layers. Pillow + numpy only.

    python3 design/app-icon/make_icons.py design/app-icon/source.png .   (from the repo root)
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SRC, ROOT = Path(sys.argv[1]), Path(sys.argv[2])

# ---------------------------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------------------------

def luma(a):
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]


def shift_max(mask, r):
    """Binary dilation by a (2r+1)² square (separable: rows, then columns)."""
    rows = mask.copy()
    for dx in range(1, r + 1):
        rows[:, dx:] |= mask[:, :-dx]
        rows[:, :-dx] |= mask[:, dx:]
    out = rows.copy()
    for dy in range(1, r + 1):
        out[dy:] |= rows[:-dy]
        out[:-dy] |= rows[dy:]
    return out


def dilate_big(mask, r):
    """Approximate dilation by a large radius (done at quarter scale)."""
    h, w = mask.shape
    small = Image.fromarray((mask * 255).astype(np.uint8)).resize((w // 4, h // 4), Image.BOX)
    m = np.asarray(small) > 0
    m = shift_max(m, max(1, r // 4))
    return np.asarray(Image.fromarray((m * 255).astype(np.uint8)).resize((w, h), Image.NEAREST)) > 0


def resize_float(a, size):
    """Bilinear resize of an HxW(xC) float array to size=(w, h)."""
    if a.ndim == 2:
        return np.asarray(Image.fromarray(a.astype(np.float32), 'F').resize(size, Image.BILINEAR))
    return np.stack([resize_float(a[..., c], size) for c in range(a.shape[2])], -1)


def push_pull(img, known):
    """Fills the unknown pixels of img smoothly from the known ones (exact where known)."""
    levels = []
    I = img * known[..., None]
    M = known.astype(np.float32)
    while min(M.shape) > 1:
        levels.append((I, M))
        h, w = M.shape
        I = np.pad(I, ((0, h % 2), (0, w % 2), (0, 0)), mode='edge')
        M = np.pad(M, ((0, h % 2), (0, w % 2)), mode='edge')
        I2 = I[0::2, 0::2] + I[1::2, 0::2] + I[0::2, 1::2] + I[1::2, 1::2]
        M2 = M[0::2, 0::2] + M[1::2, 0::2] + M[0::2, 1::2] + M[1::2, 1::2]
        val = I2 / np.maximum(M2, 1e-8)[..., None]
        M = np.minimum(M2, 1.0)
        I = val * M[..., None]
    filled = I / np.maximum(M, 1e-8)[..., None]
    for I, M in reversed(levels):
        up = resize_float(filled, (M.shape[1], M.shape[0]))
        val = I / np.maximum(M, 1e-8)[..., None]
        filled = M[..., None] * val + (1 - M[..., None]) * up
    return filled


def to_image(a, mode='RGB'):
    return Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8), mode)


def rounded_mask(size, inset, radius_frac, supersample=4):
    """An anti‑aliased rounded‑square mask (the legacy launcher shape)."""
    s = size * supersample
    m = Image.new('L', (s, s), 0)
    d = ImageDraw.Draw(m)
    i = inset * supersample
    d.rounded_rectangle([i, i, s - 1 - i, s - 1 - i], radius=int((s - 2 * i) * radius_frac), fill=255)
    return m.resize((size, size), Image.LANCZOS)


def circle_mask(size, inset, supersample=4):
    s = size * supersample
    m = Image.new('L', (s, s), 0)
    i = inset * supersample
    ImageDraw.Draw(m).ellipse([i, i, s - 1 - i, s - 1 - i], fill=255)
    return m.resize((size, size), Image.LANCZOS)


# ---------------------------------------------------------------------------------------------
# 1. Measure the tile and separate background from book
# ---------------------------------------------------------------------------------------------

art = np.asarray(Image.open(SRC).convert('RGB')).astype(np.float32) / 255
H, W = art.shape[:2]
# The tile is everything but the white surround — found by flooding the white in from the corners,
# so the (equally white) pages, enclosed by navy, stay part of the tile.
pale = Image.fromarray(((art.sum(-1) >= 2.7) * 255).astype(np.uint8)).copy()  # a writable image
for corner in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)):
    if pale.getpixel(corner) == 255:
        ImageDraw.floodfill(pale, corner, 128)
tile = np.asarray(pale) != 128
ys, xs = np.nonzero(tile)
x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
side = max(x1 - x0, y1 - y0) + 1  # the tile's size: what a store icon's full square shows
cx, cy = (x0 + x1) / 2, (y0 + y1) / 2

# Work on a canvas 1.5× the tile (an Android adaptive layer is 108dp around a 72dp visible tile).
CANVAS = int(round(side * 1.5))
ox, oy = int(round(cx - CANVAS / 2)), int(round(cy - CANVAS / 2))
canvas = np.zeros((CANVAS, CANVAS, 3), np.float32)
inside_canvas = np.zeros((CANVAS, CANVAS), bool)
sx0, sy0 = max(0, ox), max(0, oy)
sx1, sy1 = min(W, ox + CANVAS), min(H, oy + CANVAS)
canvas[sy0 - oy:sy1 - oy, sx0 - ox:sx1 - ox] = art[sy0:sy1, sx0:sx1]
inside_canvas[sy0 - oy:sy1 - oy, sx0 - ox:sx1 - ox] = tile[sy0:sy1, sx0:sx1]

L = luma(canvas)
warmth = canvas[..., 0] - canvas[..., 2]
# Known background: inside the tile, clear of its rim and of the book with its shadow.
rim_free = ~shift_max(~inside_canvas, 14)
# The book: bright pages, plus the warm gold cord (navy is cold, white neutral) — away from the
# tile's anti‑aliased outline, which is pale too.
core = rim_free & ((L > 0.5) | (warmth > 0.08))
book_zone = dilate_big(core, 96)
known = rim_free & ~book_zone
background = push_pull(canvas, known)  # full‑bleed, smooth, matches the tile's own lighting

# Alpha of the book: solid in its core; along its outline, the pixel's position between the
# local (shadowed) background and the page colour.
near = shift_max(core, 3)
local_bg = push_pull(canvas, inside_canvas & ~near)  # background as seen right next to the book
page_col = push_pull(canvas, core)  # the book's colour carried just past its outline
alpha = np.zeros((CANVAS, CANVAS), np.float32)
alpha[core] = 1.0
band = near & ~core
d = page_col - local_bg
t = ((canvas - local_bg) * d).sum(-1) / np.maximum((d * d).sum(-1), 1e-6)
alpha[band] = np.clip(t[band], 0, 1)

# Shadow: how much darker than the clean background the artwork is, as a black overlay.
shade_src = np.where((alpha[..., None] > 0), local_bg, canvas)
shadow = np.clip(1 - luma(shade_src) / np.maximum(luma(background), 1e-6), 0, 1)
shadow = np.where(shadow < 0.04, shadow * shadow / 0.04, shadow)  # ease the noise floor to zero
shadow[~book_zone] = 0
shadow *= inside_canvas

fg_alpha = alpha + shadow * (1 - alpha)
fg_rgb = np.where(fg_alpha[..., None] > 0, (alpha[..., None] * page_col) / np.maximum(fg_alpha, 1e-6)[..., None], 0)
fg_rgb = np.where(core[..., None], canvas, fg_rgb)  # the book's own pixels, untouched
foreground = np.dstack([fg_rgb, fg_alpha])

# Check: foreground over background must reproduce the artwork inside the tile.
recomposed = fg_rgb * fg_alpha[..., None] + background * (1 - fg_alpha[..., None])
err = np.abs(recomposed - canvas)[rim_free]
print('recomposition error: mean %.4f, p99 %.4f, max %.4f' % (err.mean(), np.percentile(err, 99), err.max()))

# ---------------------------------------------------------------------------------------------
# 2. Render the targets
# ---------------------------------------------------------------------------------------------

margin = (CANVAS - side) // 2
tile_box = (margin, margin, margin + side, margin + side)


def full_bleed(size):
    """The whole icon as a plain square (stores and iOS add their own mask). The book's pixels are
    the artwork's own; only the rim and the corners come from the rebuilt background."""
    keep = Image.fromarray((rim_free * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6))
    keep = np.asarray(keep).astype(np.float32) / 255
    rgb = canvas * keep[..., None] + background * (1 - keep[..., None])
    return to_image(rgb).crop(tile_box).resize((size, size), Image.LANCZOS)


def layer(rgba, size):
    return Image.fromarray((np.clip(rgba, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA').resize((size, size), Image.LANCZOS)


out = ROOT
# iOS: one 1024 opaque square (the App Store refuses transparency); Xcode derives every other size.
ios_dir = out / 'iosApp/iosApp/Assets.xcassets/AppIcon.appiconset'
ios_dir.mkdir(parents=True, exist_ok=True)
full_bleed(1024).convert('RGB').save(ios_dir / 'AppIcon-1024.png', optimize=True)
(ios_dir / 'Contents.json').write_text(json.dumps({
    'images': [{'filename': 'AppIcon-1024.png', 'idiom': 'universal', 'platform': 'ios', 'size': '1024x1024'}],
    'info': {'author': 'xcode', 'version': 1},
}, indent=2) + '\n')
(out / 'iosApp/iosApp/Assets.xcassets/Contents.json').write_text(json.dumps(
    {'info': {'author': 'xcode', 'version': 1}}, indent=2) + '\n')

# Android: adaptive layers (108dp) + legacy square/round icons (48dp) per density.
densities = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
mono = np.zeros((CANVAS, CANVAS, 4), np.float32)
mono[..., :3] = 1
# Themed icon: the book's silhouette, with the cord set off from the page by a thin gap.
cord = core & (warmth > 0.08)
gap = shift_max(cord, 7) & ~cord
mono[..., 3] = np.clip(alpha * ~gap, 0, 1)

for name, k in densities.items():
    d = out / f'composeApp/src/androidMain/res/mipmap-{name}'
    d.mkdir(parents=True, exist_ok=True)
    s108, s48 = int(round(108 * k)), int(round(48 * k))
    layer(foreground, s108).save(d / 'ic_launcher_foreground.png', optimize=True)
    to_image(background).resize((s108, s108), Image.LANCZOS).save(d / 'ic_launcher_background.png', optimize=True)
    layer(mono, s108).save(d / 'ic_launcher_monochrome.png', optimize=True)
    # Legacy (Android 7): the tile, 44dp of the 48dp grid, as a rounded square and as a circle.
    pad = round(2 * k)
    tile_img = full_bleed(s48 - 2 * pad)
    for fname, mask in (('ic_launcher.png', rounded_mask(s48, pad, 0.20)),
                        ('ic_launcher_round.png', circle_mask(s48, pad))):
        icon = Image.new('RGBA', (s48, s48), (0, 0, 0, 0))
        icon.paste(tile_img, (pad, pad))
        icon.putalpha(mask)
        icon.save(d / fname, optimize=True)

# The Play Store listing icon (32‑bit PNG, full square — Play rounds it).
design = out / 'design/app-icon'
design.mkdir(parents=True, exist_ok=True)
full_bleed(512).convert('RGBA').save(design / 'play-store-512.png', optimize=True)

# Safe‑zone check: the book must sit inside the 66dp circle of the 108dp adaptive layer.
yy, xx = np.nonzero(alpha > 0.05)
r = np.sqrt((xx - CANVAS / 2) ** 2 + (yy - CANVAS / 2) ** 2).max()
print('book reaches %.1fdp from the centre (safe zone: 33dp)' % (r / CANVAS * 108))
