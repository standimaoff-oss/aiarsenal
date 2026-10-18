#!/usr/bin/env python3
"""Flat-shaded orthographic preview of a GLB, so models can be inspected without Minecraft.

Renders a z-buffered image with back-face culling and embedded PNG texture tinting, which makes
inverted winding and wrong proportions obvious before anything is shipped.

    python tools/wwi/render_preview.py mark_i.glb [out.png] [--azim -28] [--elev 14] [--width 900]
"""
from __future__ import annotations

import argparse
import math
import struct
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from glb_builder import png_rgba  # noqa: E402
from verify_assets import MODELS, parse_glb  # noqa: E402

COMPONENT = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2), 5123: ("H", 2),
             5125: ("I", 4), 5126: ("f", 4)}
DIMENSION = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def read_accessor(js: dict, binary: bytes, index: int) -> list[tuple]:
    acc = js["accessors"][index]
    view = js["bufferViews"][acc["bufferView"]]
    fmt, size = COMPONENT[acc["componentType"]]
    n = DIMENSION[acc["type"]]
    stride = view.get("byteStride") or size * n
    start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    return [struct.unpack_from("<" + fmt * n, binary, start + i * stride) for i in range(acc["count"])]


def _png_average_rgb(png_bytes: bytes) -> tuple[float, float, float]:
    """Decode an embedded RGBA8 PNG (filter 0) and return its mean RGB in 0..1."""
    if not png_bytes.startswith(b"\x89PNG\r\n\x1a\n"):
        return (1.0, 1.0, 1.0)
    pos = 8
    w = h = 0
    idat = bytearray()
    while pos + 8 <= len(png_bytes):
        length = struct.unpack_from(">I", png_bytes, pos)[0]
        kind = png_bytes[pos + 4:pos + 8]
        data = png_bytes[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            w, h, bit_depth, color_type = struct.unpack_from(">IIBB", data, 0)
            if bit_depth != 8 or color_type != 6:
                return (1.0, 1.0, 1.0)
        elif kind == b"IDAT":
            idat.extend(data)
        elif kind == b"IEND":
            break
    if w <= 0 or h <= 0 or not idat:
        return (1.0, 1.0, 1.0)
    raw = zlib.decompress(bytes(idat))
    stride = 1 + w * 4
    r_sum = g_sum = b_sum = count = 0
    for y in range(0, h, max(1, h // 32)):
        row = y * stride + 1
        if row + w * 4 > len(raw):
            break
        for x in range(0, w, max(1, w // 32)):
            o = row + x * 4
            r_sum += raw[o]
            g_sum += raw[o + 1]
            b_sum += raw[o + 2]
            count += 1
    if count == 0:
        return (1.0, 1.0, 1.0)
    return (r_sum / (255.0 * count), g_sum / (255.0 * count), b_sum / (255.0 * count))


def material_colours(js: dict, binary: bytes) -> list[tuple[float, float, float]]:
    views = js.get("bufferViews", [])
    images = js.get("images", [])
    textures = js.get("textures", [])
    img_rgb: list[tuple[float, float, float]] = []
    for img in images:
        vi = img.get("bufferView")
        if vi is not None and 0 <= vi < len(views):
            v = views[vi]
            off = v.get("byteOffset", 0)
            ln = v.get("byteLength", 0)
            img_rgb.append(_png_average_rgb(binary[off:off + ln]))
        else:
            img_rgb.append((1.0, 1.0, 1.0))
    out: list[tuple[float, float, float]] = []
    for mat in js.get("materials", []):
        pbr = mat.get("pbrMetallicRoughness", {})
        base = pbr.get("baseColorFactor", (1.0, 1.0, 1.0, 1.0))[:3]
        tex_info = pbr.get("baseColorTexture")
        if tex_info is not None:
            ti = tex_info.get("index", 0)
            if 0 <= ti < len(textures):
                si = textures[ti].get("source", 0)
                if 0 <= si < len(img_rgb):
                    tr, tg, tb = img_rgb[si]
                    base = (base[0] * tr, base[1] * tg, base[2] * tb)
        out.append(base)
    return out or [(0.8, 0.8, 0.8)]


def trs_matrix(node: dict) -> list[float]:
    """Row-major 4x4 for a node's translation/rotation/scale."""
    if "matrix" in node:
        m = list(node["matrix"])
        return [m[r + 4 * c] for r in range(4) for c in range(4)]
    tx, ty, tz = node.get("translation", (0.0, 0.0, 0.0))
    x, y, z, w = node.get("rotation", (0.0, 0.0, 0.0, 1.0))
    sx, sy, sz = node.get("scale", (1.0, 0.0, 1.0)) if "scale" not in node else node.get("scale", (1.0, 1.0, 1.0))
    r = [
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ]
    out = [[0.0] * 4 for _ in range(4)]
    for i in range(3):
        for j in range(3):
            out[i][j] = r[i][j] * (sx, sy, sz)[j]
        out[i][3] = (tx, ty, tz)[i]
    out[3][3] = 1.0
    return [out[i][j] for i in range(4) for j in range(4)]


def multiply(a: list[float], b: list[float]) -> list[float]:
    out = [0.0] * 16
    for i in range(4):
        for j in range(4):
            out[i * 4 + j] = sum(a[i * 4 + k] * b[k * 4 + j] for k in range(4))
    return out


def apply(m: list[float], p) -> tuple[float, float, float]:
    return (m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3],
            m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7],
            m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11])


def triangles(js: dict, binary: bytes):
    """(world-space triangle, base colour) for every primitive of the default scene."""
    nodes = js.get("nodes", [])
    meshes = js.get("meshes", [])
    mat_cols = material_colours(js, binary)
    stack = [(i, [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1])
             for i in js["scenes"][js.get("scene", 0)]["nodes"]]
    while stack:
        index, parent = stack.pop()
        node = nodes[index]
        world = multiply(parent, trs_matrix(node))
        stack.extend((child, world) for child in node.get("children", ()))
        if "mesh" not in node:
            continue
        for primitive in meshes[node["mesh"]].get("primitives", []):
            mi = primitive.get("material", 0)
            colour = mat_cols[mi] if 0 <= mi < len(mat_cols) else (0.8, 0.8, 0.8)
            points = read_accessor(js, binary, primitive["attributes"]["POSITION"])
            if "indices" in primitive:
                flat = [i[0] for i in read_accessor(js, binary, primitive["indices"])]
            else:
                flat = list(range(len(points)))
            for k in range(0, len(flat) - 2, 3):
                yield [apply(world, points[j]) for j in flat[k:k + 3]], colour


def render(path: Path, out: Path, azim: float, elev: float, width: int) -> None:
    js, binary = parse_glb(path)
    tris = list(triangles(js, binary))
    if not tris:
        raise SystemExit(f"{path}: no triangles")

    # View basis: yaw about Y, then pitch down from above by +elev, looking towards -Z (camera at +Z).
    ca, sa = math.cos(math.radians(azim)), math.sin(math.radians(azim))
    ce, se = math.cos(math.radians(-elev)), math.sin(math.radians(-elev))

    def project(p):
        x = p[0] * ca - p[2] * sa
        z = p[0] * sa + p[2] * ca
        return (x, p[1] * ce + z * se, z * ce - p[1] * se)

    projected = [([project(p) for p in tri], colour) for tri, colour in tris]
    xs = [q[0] for tri, _ in projected for q in tri]
    ys = [q[1] for tri, _ in projected for q in tri]
    zs = [q[2] for tri, _ in projected for q in tri]
    span = max(max(xs) - min(xs), 1e-6)
    scale = (width - 24) / span
    height = int(math.ceil((max(ys) - min(ys)) * scale)) + 24
    ox = (width - span * scale) * 0.5 - min(xs) * scale
    oy = height - 12 + min(ys) * scale

    zmin, zmax = min(zs), max(zs)
    zrange = max(zmax - zmin, 1e-6)
    light = [0.42, 0.72, 0.55]
    norm = math.sqrt(sum(v * v for v in light))
    light = [v / norm for v in light]

    buf = [(24, 26, 30, 255)] * (width * height)
    depth = [-math.inf] * (width * height)
    drawn = culled = 0
    for tri, colour in projected:
        (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = tri
        ux, uy, uz = x1 - x0, y1 - y0, z1 - z0
        vx, vy, vz = x2 - x0, y2 - y0, z2 - z0
        nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
        length = math.sqrt(nx * nx + ny * ny + nz * nz)
        if length < 1e-12:
            continue
        nx, ny, nz = nx / length, ny / length, nz / length
        if nz < 0.0:  # faces away from the camera (+Z)
            culled += 1
            continue
        shade = 0.42 + 0.68 * max(0.0, nx * light[0] + ny * light[1] + nz * light[2])
        mid = (z0 + z1 + z2) / 3.0
        shade *= 1.0 - 0.18 * max(0.0, min(1.0, (zmax - mid) / zrange))
        rgb = tuple(max(0, min(255, int(c * 255 * shade))) for c in colour) + (255,)
        px = [x0 * scale + ox, x1 * scale + ox, x2 * scale + ox]
        py = [oy - y0 * scale, oy - y1 * scale, oy - y2 * scale]
        pz = [z0, z1, z2]
        minx, maxx = max(0, int(min(px))), min(width - 1, int(max(px)) + 1)
        miny, maxy = max(0, int(min(py))), min(height - 1, int(max(py)) + 1)
        det = (py[1] - py[2]) * (px[0] - px[2]) + (px[2] - px[1]) * (py[0] - py[2])
        if abs(det) < 1e-9:
            continue
        for y in range(miny, maxy + 1):
            for x in range(minx, maxx + 1):
                w0 = ((py[1] - py[2]) * (x - px[2]) + (px[2] - px[1]) * (y - py[2])) / det
                w1 = ((py[2] - py[0]) * (x - px[2]) + (px[0] - px[2]) * (y - py[2])) / det
                w2 = 1.0 - w0 - w1
                if w0 < 0.0 or w1 < 0.0 or w2 < 0.0:
                    continue
                z = w0 * pz[0] + w1 * pz[1] + w2 * pz[2]
                i = y * width + x
                if z > depth[i]:
                    depth[i] = z
                    buf[i] = rgb
                    drawn += 1
    out.write_bytes(png_rgba(width, height, buf))
    print(f"{out}: {width}x{height}, {len(tris):,} triangles, {drawn:,} pixels, {culled:,} back faces culled")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", help="GLB name under models/glb, or a path")
    parser.add_argument("out", nargs="?", default=None, help="output PNG (default: previews/<model>.png)")
    parser.add_argument("--azim", type=float, default=-28.0)
    parser.add_argument("--elev", type=float, default=14.0)
    parser.add_argument("--width", type=int, default=900)
    args = parser.parse_args()
    path = Path(args.model)
    if not path.exists():
        path = MODELS / args.model
    if not path.exists():
        raise SystemExit(f"no such model: {args.model}")
    out = Path(args.out) if args.out else (Path(__file__).resolve().parents[2] / "previews" /
                                       (path.stem + "-preview.png"))
    out.parent.mkdir(parents=True, exist_ok=True)
    render(path, out, args.azim, args.elev, args.width)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
