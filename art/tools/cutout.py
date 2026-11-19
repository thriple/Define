"""Cut the hero out of the flat grey background: low-saturation light pixels connected to the border are
background; near the floor, bluish-grey shadow pixels (lighter than the navy boots) are too. All stages share
one crop so they line up."""
import numpy as np, glob, os, sys
from PIL import Image, ImageFilter
from scipy import ndimage
fs = sorted(glob.glob(sys.argv[1], recursive=True))
out = sys.argv[2]; os.makedirs(out, exist_ok=True)
masks, rgbs = [], []
for f in fs:
    im = np.asarray(Image.open(f).convert('RGB')).astype(np.float32) / 255
    mx = im.max(2); mn = im.min(2); sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    cand = (sat < 0.13) & (mx > 0.45)
    floor = np.zeros_like(cand); floor[int(im.shape[0] * 0.80):] = True
    cand |= floor & (sat < 0.35) & (mx > 0.42)
    lab, _ = ndimage.label(cand)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    fg = ~np.isin(lab, list(border))
    lab2, n2 = ndimage.label(fg)
    sizes = ndimage.sum(fg, lab2, range(1, n2 + 1))
    keep = ndimage.binary_fill_holes(lab2 == (np.argmax(sizes) + 1))
    keep = ndimage.binary_opening(keep, iterations=2)
    masks.append(keep); rgbs.append(im)
union = np.any(masks, axis=0); ys, xs = np.where(union)
y0, y1 = max(ys.min() - 12, 0), min(ys.max() + 12, union.shape[0])
x0, x1 = max(xs.min() - 12, 0), min(xs.max() + 12, union.shape[1])
print('common crop', x0, y0, x1, y1)
H = 900
for i, (m, im) in enumerate(zip(masks, rgbs)):
    a = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
    rgba = Image.fromarray((im * 255).astype(np.uint8)).convert('RGBA'); rgba.putalpha(a)
    rgba = rgba.crop((x0, y0, x1, y1))
    rgba = rgba.resize((round(rgba.width * H / rgba.height), H), Image.LANCZOS)
    rgba.save(f'{out}/hero_stage{i + 1}.webp', 'WEBP', quality=88, method=6)
prev = Image.new('RGB', (rgba.width * len(fs), H), (11, 10, 42))
for i in range(len(fs)):
    t = Image.open(f'{out}/hero_stage{i + 1}.webp'); prev.paste(t, (i * t.width, 0), t)
prev.resize((prev.width // 3, prev.height // 3)).save(f'{out}_preview.png')
