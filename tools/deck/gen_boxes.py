#!/usr/bin/env python3
"""Walkable collision boxes for every vehicle GLB (reply 9) - replaces the single-level deck cells of reply 7.

Pipeline per model (rest pose, glTF model space: +X left, +Y up, +Z forward, metres):
  1. triangles of every mesh node, tagged with the rotating group they belong to
     (0 = hull, 1 = subtree of `launcher_yaw` (turret, rotates by az), 2 = `sec_yaw` (Ratte rear turret));
     antennas / plumes / hidden reload props are skipped;
  2. surface voxelisation on a grid of `res` metres (0.2 m tanks ... 0.5 m ships/aircraft);
  3. closed cavities are filled (scipy binary_fill_holes) - a tank becomes solid, while rooms that are reachable
     through an opening (Ratte interior: rear door) stay hollow and walkable;
  4. specks (< 3 voxels) are dropped, then the solid voxels are merged greedily into axis-aligned boxes
     (x run -> z rows -> y slabs). A tank ends up with a few hundred boxes instead of tens of thousands of triangles.
Output: src/main/resources/assets/aiarsenal/deck/<glb name>.deck (big-endian, read by realwar.deck.DeckShapes):
  int version=2, float res, int nGroups, per group {int id, float pivotX, float pivotZ},
  int nBoxes, per box {byte grp, float x0, y0, z0, x1, y1, z1}.
"""
import os, sys, struct, re
import numpy as np
from scipy import ndimage
sys.path.insert(0, os.path.dirname(__file__))
from gen_decks import load, accessor, trs, MODELS as M0, ROOT
MODELS = M0 + ['models/glb/ah64.glb', 'models/glb/ka52.glb', 'glb/tu95ms.glb', 'glb/tu160.glb', 'models/glb/bismarck.glb',
                 # reply 11: WW2 aeroplanes (moving surfaces / gear / bombs / turrets are not walkable)
                 'models/glb/bf109.glb', 'models/glb/yak3.glb', 'models/glb/he111.glb', 'models/glb/pe8.glb',
                 # reply 12: Su-57
                 'models/glb/su57.glb', 'models/glb/bradley.glb', 'models/glb/tos1a.glb', 'models/glb/ural.glb',
                 'models/glb/wirbelwind.glb', 'models/glb/zsu37.glb', 'models/glb/gepard.glb', 'models/glb/shilka.glb']

OUTDIR = ROOT + 'deck/'
SKIP = re.compile(r'rotor|blade|prop_|antenna|_ant\b|_ant_|whip|rocket_load|plume|flame|disc$|chute|_lens|decal|^(h|f|l|q)\d+_|^rf_|arado|rudder|^gear_[LRT]$|^bomb[xkf]?_\d|^tur_\d|^bay_[LR]$|^ail_|^flap_|^elev$|^rud$|^elev_[LR]$|^rud_[LR]$|^levcon_|^noz|^ab|^glow_|^pod_\d|^kh38_\d|^fab_\d|^gear_N$|^gear_door', re.I)
GROUPS = {'launcher_yaw': 1, 'sec_yaw': 2,
          # battleship: 4 main turrets + 6 secondary turrets (walkable roofs follow the turret yaw)
          'tA_yaw': 1, 'tB_yaw': 2, 'tC_yaw': 3, 'tD_yaw': 4,
          's0_yaw': 5, 's1_yaw': 6, 's2_yaw': 7, 's3_yaw': 8, 's4_yaw': 9, 's5_yaw': 10}


def triangles(path):
    j, binb = load(path)
    out, pivots = [], {}
    scene = j['scenes'][j.get('scene', 0)]

    def walk(ni, parent, grp, skip):
        node = j['nodes'][ni]
        name = node.get('name', '')
        M = parent @ trs(node)
        if name in GROUPS:
            grp = GROUPS[name]
            pivots[grp] = (M[0, 3], M[2, 3])
        skip = skip or bool(SKIP.search(name)) or (os.path.basename(path) == 'mark_i.glb' and name in {'launcher_yaw', 'launcher_yaw_left'})
        if 'mesh' in node and not skip:
            for p in j['meshes'][node['mesh']]['primitives']:
                if p.get('mode', 4) != 4:
                    continue
                pos = accessor(j, binb, p['attributes']['POSITION'])
                idx = accessor(j, binb, p['indices']).astype(np.int64).ravel() if 'indices' in p else np.arange(len(pos))
                pw = (M[:3, :3] @ pos.T).T + M[:3, 3]
                out.append((grp, pw[idx].reshape(-1, 3, 3)))
        for c in node.get('children', []):
            walk(c, M, grp, skip)

    for ni in scene['nodes']:
        walk(ni, np.eye(4), 0, False)
    return out, pivots


def sample(tris, res):
    """Points on the triangles spaced <= res/2 (barycentric grid per triangle, vectorised by subdivision level)."""
    pts = [tris.reshape(-1, 3)]
    e = np.maximum(np.maximum(np.linalg.norm(tris[:, 1] - tris[:, 0], axis=1), np.linalg.norm(tris[:, 2] - tris[:, 1], axis=1)),
                   np.linalg.norm(tris[:, 0] - tris[:, 2], axis=1))
    n = np.clip(np.ceil(e / (res * 0.45)).astype(int), 1, 400)
    for k in np.unique(n):
        t = tris[n == k]
        if k <= 1:
            continue
        a, b = np.meshgrid(np.arange(k + 1), np.arange(k + 1))
        m = (a + b) <= k
        u, v = (a[m] / k)[None, :, None], (b[m] / k)[None, :, None]
        p = t[:, 0][:, None, :] + u * (t[:, 1] - t[:, 0])[:, None, :] + v * (t[:, 2] - t[:, 0])[:, None, :]
        pts.append(p.reshape(-1, 3))
    return np.concatenate(pts)


def greedy(vox):
    """Axis-aligned box cover of a boolean grid [x, y, z] -> list of (x0, y0, z0, x1, y1, z1) in voxel units."""
    v = vox.copy()
    nx, ny, nz = v.shape
    boxes = []
    for y in range(ny):
        for z in range(nz):
            x = 0
            while x < nx:
                if not v[x, y, z]:
                    x += 1
                    continue
                x1 = x
                while x1 + 1 < nx and v[x1 + 1, y, z]:
                    x1 += 1
                z1 = z
                while z1 + 1 < nz and v[x:x1 + 1, y, z1 + 1].all():
                    z1 += 1
                y1 = y
                while y1 + 1 < ny and v[x:x1 + 1, y1 + 1, z:z1 + 1].all():
                    y1 += 1
                v[x:x1 + 1, y:y1 + 1, z:z1 + 1] = False
                boxes.append((x, y, z, x1 + 1, y1 + 1, z1 + 1))
                x = x1 + 1
    return boxes


def build(m):
    parts, pivots = triangles(ROOT + m)
    allp = np.concatenate([t.reshape(-1, 3) for _, t in parts])
    mn, mx = allp.min(0), allp.max(0)
    L = max(mx - mn)
    res = 0.2 if L < 12 else (0.3 if L < 45 else 0.5)      # Ratte 0.3: 1.5 m doors stay 4+ voxels wide
    o = mn - res
    shape = np.ceil((mx - mn) / res).astype(int) + 3
    out = []
    for g in sorted({g for g, _ in parts}):
        tris = np.concatenate([t for gg, t in parts if gg == g])
        p = sample(tris, res)
        idx = np.floor((p - o) / res).astype(int)
        idx = np.clip(idx, 0, shape - 1)
        vox = np.zeros(shape, bool)
        vox[idx[:, 0], idx[:, 1], idx[:, 2]] = True
        # true surface heights per voxel: box tops / bottoms snap to the geometry instead of the voxel grid
        flat = np.ravel_multi_index((idx[:, 0], idx[:, 1], idx[:, 2]), shape)
        hmax = np.full(vox.size, -np.inf)
        hmin = np.full(vox.size, np.inf)
        np.maximum.at(hmax, flat, p[:, 1])
        np.minimum.at(hmin, flat, p[:, 1])
        hmax = hmax.reshape(shape)
        hmin = hmin.reshape(shape)
        vox = ndimage.binary_fill_holes(vox)
        lab, nl = ndimage.label(vox)
        if nl:
            sizes = ndimage.sum(vox, lab, range(1, nl + 1))
            for i, s in enumerate(sizes):
                if s < 3:
                    vox[lab == i + 1] = False
        for b in greedy(vox):
            lo = o + np.array(b[:3]) * res
            hi = o + np.array(b[3:]) * res
            top = hmax[b[0]:b[3], b[4] - 1, b[2]:b[5]]
            bot = hmin[b[0]:b[3], b[1], b[2]:b[5]]
            if np.isfinite(top).any():
                hi[1] = min(hi[1], max(lo[1] + 0.02, float(top[np.isfinite(top)].max())))
            if np.isfinite(bot).any():
                lo[1] = max(lo[1], min(hi[1] - 0.02, float(bot[np.isfinite(bot)].min())))
            out.append((g, *lo, *hi))
    return res, pivots, out, (mn, mx)


def main():
    os.makedirs(OUTDIR, exist_ok=True)
    only = set(sys.argv[1:])
    for m in MODELS:
        name = os.path.basename(m)[:-4]
        if only and name not in only:
            continue
        if not os.path.exists(ROOT + m):
            print('missing', m)
            continue
        res, piv, boxes, (mn, mx) = build(m)
        with open(OUTDIR + name + '.deck', 'wb') as f:
            f.write(struct.pack('>ifi', 2, res, len(piv)))
            for g, (px, pz) in sorted(piv.items()):
                f.write(struct.pack('>iff', g, px, pz))
            f.write(struct.pack('>i', len(boxes)))
            for b in boxes:
                f.write(struct.pack('>bffffff', *b))
        ng = {g: sum(1 for b in boxes if b[0] == g) for g in sorted({b[0] for b in boxes})}
        print(f'{m:34s} res={res:.1f} boxes={len(boxes):4d} {ng} L={mx[2]-mn[2]:.1f} W={mx[0]-mn[0]:.1f} H={mx[1]-mn[1]:.1f}'
              f' {os.path.getsize(OUTDIR + name + ".deck")//1024} KB')


if __name__ == '__main__':
    main()
