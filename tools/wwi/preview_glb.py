#!/usr/bin/env python3
"""Offline preview of a generated GLB (developer QA only, not shipped in the mod).

The CI captures the real in-game screenshots with a Minecraft client; this script is a fast local loop that
renders the same geometry with a tiny software rasteriser so a model can be checked before it is pushed.

Usage:  python tools/wwi/preview_glb.py <model.glb> <out.png> [--yaw 35] [--pitch 18] [--dist 12]
                                 [--width 900] [--height 600] [--ortho]
"""
from __future__ import annotations

import json
import struct
import sys
import zlib
from pathlib import Path

import numpy as np


def load_glb(path: Path):
    data = path.read_bytes()
    magic, version, total = struct.unpack_from("<III", data, 0)
    if magic != 0x46546C67:
        raise ValueError("not a GLB")
    pos = 12
    doc = None
    binary = b""
    while pos + 8 <= total:
        length, kind = struct.unpack_from("<II", data, pos)
        pos += 8
        chunk = data[pos:pos + length]
        if kind == 0x4E4F534A:
            doc = json.loads(chunk.decode("utf-8"))
        elif kind == 0x004E4942:
            binary = chunk
        pos += length + 3 & ~3
    return doc, binary


def accessor(doc, binary, index):
    a = doc["accessors"][index]
    bv = doc["bufferViews"][a["bufferView"]]
    base = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
    comps = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[a["type"]]
    ct = a["componentType"]
    if ct == 5126:
        dtype = np.float32
    elif ct == 5123:
        dtype = np.uint16
    elif ct == 5125:
        dtype = np.uint32
    else:
        dtype = np.uint8
    count = a["count"] * comps
    arr = np.frombuffer(binary, dtype=dtype, count=count, offset=base)
    return arr.reshape(-1, comps) if comps > 1 else arr


def png_bytes(width, height, rgb):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        raw.extend(rgb[y * width * 3:(y + 1) * width * 3].tobytes())
    def chunk(kind, payload):
        return (struct.pack(">I", len(payload)) + kind + payload
                + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF))
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 6))
            + chunk(b"IEND", b""))


def decode_png(data):
    pos = 8
    width = height = 0
    idat = b""
    while pos < len(data):
        length, kind_int = struct.unpack_from(">II", data, pos)
        kind = struct.pack(">I", kind_int)
        pos += 8
        payload = data[pos:pos + length]
        pos += length + 4
        if kind == b"IHDR":
            width, height, depth, color = struct.unpack_from(">IIBB", payload, 0)
            if depth != 8:
                raise ValueError("only 8-bit PNG")
            channels = {0: 1, 2: 3, 4: 2, 6: 4}[color]
        elif kind == b"IDAT":
            idat += payload
        elif kind == b"IEND":
            break
    raw = zlib.decompress(idat)
    stride = width * channels
    out = np.zeros((height, stride), dtype=np.uint8)
    prev = np.zeros(stride, dtype=np.uint8)
    p = 0
    for y in range(height):
        f = raw[p]
        p += 1
        line = np.frombuffer(raw[p:p + stride], dtype=np.uint8).copy()
        p += stride
        if f == 1:
            for i in range(channels, stride):
                line[i] = (int(line[i]) + int(line[i - channels])) & 0xFF
        elif f == 2:
            line = (line.astype(np.int32) + prev.astype(np.int32)).astype(np.uint8)
        elif f == 3:
            for i in range(stride):
                left = int(line[i - channels]) if i >= channels else 0
                line[i] = (int(line[i]) + ((left + int(prev[i])) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = int(line[i - channels]) if i >= channels else 0
                b = int(prev[i])
                c = int(prev[i - channels]) if i >= channels else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (int(line[i]) + pr) & 0xFF
        out[y] = line
        prev = line
    img = out.reshape(height, width, channels)
    if channels == 1:
        img = np.repeat(img, 3, axis=2)
    return img[:, :, :3]


def quat_to_mat(q):
    x, y, z, w = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])


def node_matrix(node, parent):
    t = np.array(node.get("translation", [0, 0, 0]), dtype=float)
    r = np.array(node.get("rotation", [0, 0, 0, 1]), dtype=float)
    s = np.array(node.get("scale", [1, 1, 1]), dtype=float)
    m = np.eye(4)
    m[:3, :3] = quat_to_mat(r) @ np.diag(s)
    m[:3, 3] = t
    return parent @ m


def render(path: Path, out: Path, yaw=35.0, pitch=18.0, dist=12.0, width=900, height=600, ortho=False):
    doc, binary = load_glb(path)
    textures = []
    for image in doc.get("images", []):
        view = doc["bufferViews"][image["bufferView"]]
        data = binary[view.get("byteOffset", 0):view.get("byteOffset", 0) + view["byteLength"]]
        textures.append(decode_png(data).astype(np.float32) / 255.0)

    mats = doc.get("materials", [])
    tex_index = []
    for m in mats:
        pbr = m.get("pbrMetallicRoughness", {})
        tex = pbr.get("baseColorTexture")
        if tex is None:
            tex_index.append(-1)
        else:
            t = doc["textures"][tex["index"]]
            tex_index.append(t.get("source", 0))

    scene = doc["scenes"][doc.get("scene", 0)]
    world = {}

    def walk(ni, parent):
        node = doc["nodes"][ni]
        m = node_matrix(node, parent)
        world[ni] = m
        for c in node.get("children", []):
            walk(c, m)

    for root in scene["nodes"]:
        walk(root, np.eye(4))

    # collect triangles
    tris = []
    for ni, node in enumerate(doc["nodes"]):
        if "mesh" not in node:
            continue
        mesh = doc["meshes"][node["mesh"]]
        M = world[ni]
        for prim in mesh["primitives"]:
            pos = accessor(doc, binary, prim["attributes"]["POSITION"]).astype(float)
            uv = accessor(doc, binary, prim["attributes"]["TEXCOORD_0"]).astype(float)
            idx = accessor(doc, binary, prim["indices"]).astype(int).ravel()
            base = prim.get("material", 0)
            color = np.array(mats[base].get("pbrMetallicRoughness", {}).get("baseColorFactor", [1, 1, 1, 1])[:3], dtype=float)
            v = (np.c_[pos, np.ones(len(pos))] @ M.T)[:, :3]
            for i in range(0, len(idx), 3):
                a, b, c = v[idx[i]], v[idx[i + 1]], v[idx[i + 2]]
                ua, ub, uc = uv[idx[i]], uv[idx[i + 1]], uv[idx[i + 2]]
                tris.append((a, b, c, ua, ub, uc, color, tex_index[base] if base < len(tex_index) else -1))
    if not tris:
        raise ValueError("no triangles")

    allv = np.array([t[0] for t in tris] + [t[1] for t in tris] + [t[2] for t in tris])
    center = (allv.min(axis=0) + allv.max(axis=0)) * 0.5
    radius = float(np.linalg.norm(allv.max(axis=0) - allv.min(axis=0))) * 0.5

    ry = np.radians(yaw)
    rp = np.radians(pitch)
    fwd = np.array([np.sin(ry) * np.cos(rp), np.sin(rp), np.cos(ry) * np.cos(rp)])
    fwd /= np.linalg.norm(fwd)
    up0 = np.array([0.0, 1.0, 0.0])
    right = np.cross(fwd, up0)
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    eye = center - fwd * (dist if not ortho else 0.0)
    scale = (width * 0.45) / max(radius, 0.001)
    if not ortho:
        scale *= max(dist, 0.001)

    def to_view(p):
        d = p - eye
        return np.array([np.dot(d, right), np.dot(d, up), np.dot(d, fwd)])

    depth = np.full((height, width), np.inf, dtype=np.float32)
    color = np.zeros((height, width, 3), dtype=np.float32)
    color[:, :] = np.array([0.55, 0.72, 0.92])
    light_dir = np.array([0.4, 0.8, 0.45])
    light_dir /= np.linalg.norm(light_dir)

    for (a, b, c, ua, ub, uc, base_col, ti) in tris:
        va, vb, vc = to_view(a), to_view(b), to_view(c)
        if ortho:
            sx = va[0] * scale + width * 0.5
            sy = height * 0.5 - va[1] * scale
            tx = np.array([sx, sy])
            sx2 = vb[0] * scale + width * 0.5
            sy2 = height * 0.5 - vb[1] * scale
            tx2 = np.array([sx2, sy2])
            sx3 = vc[0] * scale + width * 0.5
            sy3 = height * 0.5 - vc[1] * scale
            tx3 = np.array([sx3, sy3])
        else:
            if min(va[2], vb[2], vc[2]) <= 0.02:
                continue
            f = 1.0 / va[2]
            sx = va[0] * scale * f + width * 0.5
            sy = height * 0.5 - va[1] * scale * f
            tx = np.array([sx, sy])
            f = 1.0 / vb[2]
            sx2 = vb[0] * scale * f + width * 0.5
            sy2 = height * 0.5 - vb[1] * scale * f
            tx2 = np.array([sx2, sy2])
            f = 1.0 / vc[2]
            sx3 = vc[0] * scale * f + width * 0.5
            sy3 = height * 0.5 - vc[1] * scale * f
            tx3 = np.array([sx3, sy3])
        minx = max(int(min(sx, sx2, sx3)), 0)
        maxx = min(int(max(sx, sx2, sx3)) + 1, width)
        miny = max(int(min(sy, sy2, sy3)), 0)
        maxy = min(int(max(sy, sy2, sy3)) + 1, height)
        if minx >= maxx or miny >= maxy:
            continue
        area = (sx2 - sx) * (sy3 - sy) - (sx3 - sx) * (sy2 - sy)
        if abs(area) < 1e-9:
            continue
        n = np.cross(b - a, c - a)
        ln = np.linalg.norm(n)
        if ln < 1e-12:
            continue
        n = n / ln
        shade = 0.32 + 0.68 * max(0.0, float(np.dot(n, light_dir)))
        base = base_col
        img = textures[ti] if 0 <= ti < len(textures) else None
        for py in range(miny, maxy):
            for px in range(minx, maxx):
                w0 = ((sx2 - px) * (sy3 - py) - (sx3 - px) * (sy2 - py)) / area
                w1 = ((sx3 - px) * (sy - py) - (sx - px) * (sy3 - py)) / area
                w2 = 1.0 - w0 - w1
                if w0 < 0 or w1 < 0 or w2 < 0:
                    continue
                if ortho:
                    z = a[2] * w0 + b[2] * w1 + c[2] * w2
                else:
                    z = 1.0 / (w0 / va[2] + w1 / vb[2] + w2 / vc[2])
                if z >= depth[py, px]:
                    continue
                depth[py, px] = z
                u = ua[0] * w0 + ub[0] * w1 + uc[0] * w2
                v = ua[1] * w0 + ub[1] * w1 + uc[1] * w2
                col = base
                if img is not None:
                    ih, iw = img.shape[:2]
                    tx = int((u % 1.0) * iw) % iw
                    ty = int((v % 1.0) * ih) % ih
                    col = img[ty, tx] * base
                color[py, px] = np.clip(col * shade, 0, 1)

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(png_bytes(width, height, (color * 255).astype(np.uint8).ravel()))
    print(f"{out}: {width}x{height}, {len(tris)} triangles, centre={np.round(center, 2).tolist()}")


def main():
    args = sys.argv[1:]
    if len(args) < 2:
        print(__doc__)
        return
    model = Path(args[0])
    out = Path(args[1])
    opts = {}
    i = 2
    while i < len(args):
        key = args[i].lstrip("-")
        opts[key] = float(args[i + 1]) if i + 1 < len(args) else 1.0
        i += 2
    render(model, out,
           yaw=opts.get("yaw", 35.0), pitch=opts.get("pitch", 18.0), dist=opts.get("dist", 12.0),
           width=int(opts.get("width", 900)), height=int(opts.get("height", 600)),
           ortho=bool(opts.get("ortho", 0.0)))


if __name__ == "__main__":
    main()
