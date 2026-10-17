#!/usr/bin/env python3
"""Walkable deck shapes from the vehicle GLBs (reply 7).

Vanilla hitboxes are single axis-aligned boxes, so long/rotating vehicles get a set of square "deck cells"
(PartEntities) that follow the hull. This script reads each model at rest pose (node TRS + KHR_mesh_quantization),
rasterises the up-facing surfaces (top) and down-facing surfaces (bottom) on a 0.2 m grid, then groups them into
square cells of size c: a cell is kept when >= 45 % of it is covered (thin barrels/antennas drop out), its top is
the 75th percentile of the surface heights and its bottom the 25th percentile of the underside.

Output: mods/aiarsenal/src/main/java/ru/aiforge/realwar/deck/DeckShapes.java (generated, do not edit).
Model space = glTF: +X left, +Y up, +Z forward, metres.
"""
import json, struct, sys, os, math
from pathlib import Path
import numpy as np

REPO_ROOT = Path(__file__).resolve().parents[2]
ASSET_ROOT = REPO_ROOT / 'mods/aiarsenal/src/main/resources/assets/aiarsenal'
JAVA_ROOT = REPO_ROOT / 'mods/aiarsenal/src/main/java/ru/aiforge/realwar/deck'
ROOT = str(ASSET_ROOT) + os.sep
OUT = str(JAVA_ROOT / 'DeckShapes.java')
FINE = 0.2
MAXCELLS = 44

MODELS = [
    # realwar ground vehicles
    'models/glb/t90m.glb', 'models/glb/abrams.glb', 'models/glb/bmp3.glb', 'models/glb/msta.glb',
    'models/glb/d30.glb', 'models/glb/m777.glb', 'models/glb/grad.glb', 'models/glb/smerch.glb',
    'models/glb/s400.glb', 'models/glb/zu23.glb', 'models/glb/radar_mobile.glb', 'glb/yarstel.glb',
    # WWI vertical prototype
    'models/glb/mark_i.glb',
    # WW2 (reply 8)
    'models/glb/pz2.glb', 'models/glb/pz4.glb', 'models/glb/tiger.glb', 'models/glb/sturmtiger.glb', 'models/glb/maus.glb', 'models/glb/ratte.glb',
    'models/glb/t34.glb', 'models/glb/kv2.glb', 'models/glb/is2.glb', 'models/glb/kv5.glb', 'models/glb/bm13.glb',
    # boats / sub
    'models/glb/raptor.glb', 'models/glb/cb90.glb', 'models/glb/sub636.glb',
    # aircraft (realwar) + BM-21 (bm21grad stack)
    'models/glb/tu95.glb', 'glb/bm21.glb',
]

CT = {5126: ('f', 4), 5120: ('b', 1), 5121: ('B', 1), 5122: ('h', 2), 5123: ('H', 2), 5125: ('I', 4)}
NORM = {5120: 127.0, 5121: 255.0, 5122: 32767.0, 5123: 65535.0}


def load(path):
    f = open(path, 'rb').read()
    n = struct.unpack('<I', f[12:16])[0]
    j = json.loads(f[20:20 + n])
    off = 20 + n
    blen = struct.unpack('<I', f[off:off + 4])[0]
    binb = f[off + 8:off + 8 + blen]
    return j, binb


def accessor(j, binb, ai):
    a = j['accessors'][ai]
    bv = j['bufferViews'][a['bufferView']]
    fmt, sz = CT[a['componentType']]
    nc = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}[a['type']]
    stride = bv.get('byteStride', sz * nc)
    start = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    dt = np.dtype('<' + fmt)
    cnt = a['count']
    if stride == sz * nc:
        arr = np.frombuffer(binb, dtype=dt, count=cnt * nc, offset=start).reshape(cnt, nc).astype(np.float64)
    else:
        raw = np.frombuffer(binb, dtype=np.uint8, count=stride * (cnt - 1) + sz * nc, offset=start)
        arr = np.array([np.frombuffer(raw[i * stride:i * stride + sz * nc].tobytes(), dtype=dt) for i in range(cnt)], dtype=np.float64)
    if a.get('normalized') and a['componentType'] in NORM:
        arr = np.maximum(arr / NORM[a['componentType']], -1.0)
    return arr


def trs(node):
    if 'matrix' in node:
        return np.array(node['matrix'], dtype=np.float64).reshape(4, 4).T
    t = node.get('translation', [0, 0, 0])
    r = node.get('rotation', [0, 0, 0, 1])
    s = node.get('scale', [1, 1, 1])
    x, y, z, w = r
    R = np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                  [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                  [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
    M = np.eye(4)
    M[:3, :3] = R * np.array(s)[None, :]
    M[:3, 3] = t
    return M


def triangles(path):
    j, binb = load(path)
    out = []
    scene = j['scenes'][j.get('scene', 0)]

    def walk(ni, parent):
        node = j['nodes'][ni]
        M = parent @ trs(node)
        if 'mesh' in node:
            for p in j['meshes'][node['mesh']]['primitives']:
                if p.get('mode', 4) != 4:
                    continue
                pos = accessor(j, binb, p['attributes']['POSITION'])
                idx = accessor(j, binb, p['indices']).astype(np.int64).ravel() if 'indices' in p else np.arange(len(pos))
                pw = (M[:3, :3] @ pos.T).T + M[:3, 3]
                out.append(pw[idx].reshape(-1, 3, 3))
        for c in node.get('children', []):
            walk(c, M)

    for ni in scene['nodes']:
        walk(ni, np.eye(4))
    return np.concatenate(out) if out else np.zeros((0, 3, 3))


def raster(tris, x0, z0, nx, nz):
    top = np.full((nx, nz), -1e9)
    bot = np.full((nx, nz), 1e9)
    a, b, c = tris[:, 0], tris[:, 1], tris[:, 2]
    nrm = np.cross(b - a, c - a)
    ln = np.linalg.norm(nrm, axis=1) + 1e-12
    ny = nrm[:, 1] / ln
    for t, k in zip(tris, ny):
        if abs(k) < 0.2:
            continue
        xs, zs = t[:, 0], t[:, 2]
        i0 = max(0, int((xs.min() - x0) / FINE)); i1 = min(nx - 1, int((xs.max() - x0) / FINE))
        j0 = max(0, int((zs.min() - z0) / FINE)); j1 = min(nz - 1, int((zs.max() - z0) / FINE))
        if i1 < i0 or j1 < j0:
            continue
        gi, gj = np.meshgrid(np.arange(i0, i1 + 1), np.arange(j0, j1 + 1), indexing='ij')
        px = x0 + (gi + 0.5) * FINE
        pz = z0 + (gj + 0.5) * FINE
        (ax, ay, az), (bx, by, bz), (cx, cy, cz) = t
        d = (bz - cz) * (ax - cx) + (cx - bx) * (az - cz)
        if abs(d) < 1e-12:
            continue
        l1 = ((bz - cz) * (px - cx) + (cx - bx) * (pz - cz)) / d
        l2 = ((cz - az) * (px - cx) + (ax - cx) * (pz - cz)) / d
        l3 = 1 - l1 - l2
        m = (l1 >= -1e-3) & (l2 >= -1e-3) & (l3 >= -1e-3)
        if not m.any():
            continue
        y = l1 * ay + l2 * by + l3 * cy
        if k > 0:
            sub = top[i0:i1 + 1, j0:j1 + 1]
            np.maximum(sub, np.where(m, y, -1e9), out=sub)
        else:
            sub = bot[i0:i1 + 1, j0:j1 + 1]
            np.minimum(sub, np.where(m, y, 1e9), out=sub)
    return top, bot


def cells(path):
    tris = triangles(ROOT + path)
    mn = tris.reshape(-1, 3).min(0); mx = tris.reshape(-1, 3).max(0)
    x0, z0 = mn[0] - FINE, mn[2] - FINE
    nx = int((mx[0] - x0) / FINE) + 2; nz = int((mx[2] - z0) / FINE) + 2
    top, bot = raster(tris, x0, z0, nx, nz)
    cov = top > -1e8
    area = cov.sum() * FINE * FINE
    c = max(0.9, min(4.6, math.sqrt(area / 34.0)))
    for _ in range(12):
        res = group(top, bot, cov, x0, z0, nx, nz, c)
        if len(res) <= MAXCELLS:
            break
        c *= 1.1
    return round(c, 3), res, (mn, mx)


def group(top, bot, cov, x0, z0, nx, nz, c):
    k = max(1, int(round(c / FINE)))
    out = []
    # grid anchored on the model centre line (x = 0) so the cells are symmetric
    gx0 = -math.ceil((0 - x0) / c) * c
    gz0 = -math.ceil((0 - z0) / c) * c
    xi = 0
    while gx0 + xi * c < x0 + nx * FINE:
        zi = 0
        while gz0 + zi * c < z0 + nz * FINE:
            cx0, cz0 = gx0 + xi * c, gz0 + zi * c
            i0 = int((cx0 - x0) / FINE); j0 = int((cz0 - z0) / FINE)
            ia, ib = max(0, i0), min(nx, i0 + k); ja, jb = max(0, j0), min(nz, j0 + k)
            if ib > ia and jb > ja:
                cv = cov[ia:ib, ja:jb]
                if cv.sum() >= 0.45 * k * k:
                    t = np.percentile(top[ia:ib, ja:jb][cv], 75)
                    bb = bot[ia:ib, ja:jb]
                    bv = bb[bb < 1e8]
                    b = np.percentile(bv, 25) if bv.size else t - 0.4
                    b = min(b, t - 0.3)
                    out.append((cx0 + c / 2, cz0 + c / 2, b, t))
            zi += 1
        xi += 1
    return out


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    lines = []
    for m in MODELS:
        if not os.path.exists(ROOT + m):
            print('missing', m); continue
        c, res, (mn, mx) = cells(m)
        flat = [c] + [v for r in res for v in r]
        print(f'{m:40s} c={c:.2f} cells={len(res):3d} L={mx[2]-mn[2]:.1f} W={mx[0]-mn[0]:.1f} H={mx[1]:.1f}')
        body = ', '.join(f'{v:.2f}f' for v in flat)
        lines.append(f'        M.put("{m}", new float[]{{{body}}});')
    java = '''package ru.aiforge.realwar.deck;

import java.util.HashMap;
import java.util.Map;

/**
 * GENERATED by tools/deck/gen_decks.py from the vehicle GLBs - do not edit by hand.
 * Per model: {cellSize, then per cell: localX, localZ, bottomY, topY} in model space (glTF: +X left, +Y up, +Z fwd).
 */
public final class DeckShapes {
    private static final Map<String, float[]> M = new HashMap<>();

    static {
%s
    }

    private DeckShapes() {
    }

    /** @param model resource path inside assets/aiarsenal, e.g. "models/glb/t90m.glb"; null if no deck */
    public static float[] get(String model) {
        return M.get(model);
    }
}
''' % '\n'.join(lines)
    open(OUT, 'w').write(java)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
