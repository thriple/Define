"""Robust cleanup of per-stage landmarks: Theil-Sen line across stages, outliers (> tol px) replaced by the
fit. Neck is fitted on stages 1-5 only (bigger stages' traps hide it) and extrapolated."""
import json, numpy as np, itertools
lm = json.load(open('rig.json'))
st = np.arange(1, 9)
def theil_sen(x, y):
    slopes = [(y[j]-y[i])/(x[j]-x[i]) for i, j in itertools.combinations(range(len(x)), 2)]
    m = np.median(slopes); b = np.median(y - m*x); return m, b
out = {str(s): {} for s in st}
for key, tol, use in [('neck', 8, range(0,5)), ('shoulder', 12, range(8)), ('armpit', 18, range(8)), ('hand', 15, range(8)), ('crotch', 15, range(8))]:
    y = np.array([lm[str(s)][key] for s in st], float)
    idx = list(use)
    m, b = theil_sen(st[idx].astype(float), y[idx])
    fit = m*st + b
    clean = np.where((np.abs(y-fit) > tol) | ~np.isin(np.arange(8), idx), fit, y)
    for s, v, raw in zip(st, clean, y): out[str(s)][key] = round(float(v), 1)
    print(f'{key:9s} raw', y.astype(int).tolist(), '-> clean', [int(round(v)) for v in clean])
for s in st:
    L = lm[str(s)]
    o = out[str(s)]
    o.update(top=L['top'], feet=L['feet'], cx=L['cx'], W=L['W'], H=L['H'])
    # torso half-width at the chest: robust from the armpit-row torso edges, clamped to a sane range
    o['torsoHalf'] = round(float(np.clip((L['torsoR']-L['torsoL'])/2, 55, 80)), 1)
json.dump(out, open('rig_clean.json', 'w'), indent=1)
print(json.dumps(out['1']), json.dumps(out['8']))
