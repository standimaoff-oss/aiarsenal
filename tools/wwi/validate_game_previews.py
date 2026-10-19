#!/usr/bin/env python3
"""Reject fresh WWI captures whose item is missing from its expected frame region.

Mills is captured in the player's hand; the other three portraits use centered
in-world item_display entities. Each mode compares its item region with a nearby
background patch so an empty but otherwise valid PNG cannot pass the release gate.
"""
from __future__ import annotations

import argparse
from math import dist
from pathlib import Path
from statistics import median
import sys

from PIL import Image, ImageStat

ROOT = Path(__file__).resolve().parents[2]
ITEMS = ("mills_bomb", "stielhandgranate", "wex", "wwi_fuel")
EXPECTED_SIZE = (1280, 800)

# First-person Mills capture: compare the grenade/body area with adjacent grass.
MILLS_BACKGROUND = (750, 450, 850, 700)
MILLS_TARGET = (900, 440, 1120, 700)
MIN_MILLS_CONTRAST = 20.0

# Three centered item_display captures on the gray-concrete wall.
SHOWCASE_BACKGROUND = (1000, 100, 1150, 250)
SHOWCASE_TARGET = (420, 160, 860, 570)
MIN_SHOWCASE_FRACTION = 0.003
MIN_COLOUR_DISTANCE = 28.0


def median_rgb(image: Image.Image, box: tuple[int, int, int, int]) -> tuple[int, int, int]:
    pixels = list(image.crop(box).get_flattened_data())
    return tuple(int(median(channel)) for channel in zip(*pixels))


def mean_rgb(image: Image.Image, box: tuple[int, int, int, int]) -> tuple[float, float, float]:
    return tuple(ImageStat.Stat(image.crop(box)).mean[:3])


def visible_score(path: Path, item: str) -> tuple[float, str]:
    with Image.open(path) as source:
        image = source.convert("RGB")
    if image.size != EXPECTED_SIZE:
        raise ValueError(f"{path.name}: expected {EXPECTED_SIZE} screenshot, got {image.size}")

    if item == "mills_bomb":
        background = mean_rgb(image, MILLS_BACKGROUND)
        target = mean_rgb(image, MILLS_TARGET)
        return dist(target, background), "mean RGB contrast"

    background = median_rgb(image, SHOWCASE_BACKGROUND)
    target_pixels = image.crop(SHOWCASE_TARGET).get_flattened_data()
    different = sum(
        1 for pixel in target_pixels if dist(pixel, background) >= MIN_COLOUR_DISTANCE
    )
    area = (SHOWCASE_TARGET[2] - SHOWCASE_TARGET[0]) * (SHOWCASE_TARGET[3] - SHOWCASE_TARGET[1])
    return different / area, "distinct target pixels"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "directory", nargs="?", type=Path, default=ROOT / "previews",
        help="directory containing the four staged PNGs (default: previews/)",
    )
    args = parser.parse_args()

    failed = False
    for item in ITEMS:
        path = args.directory / f"{item}.png"
        if not path.is_file():
            print(f"FAIL {path}: screenshot missing", file=sys.stderr)
            failed = True
            continue
        try:
            score, metric = visible_score(path, item)
        except Exception as exc:
            print(f"FAIL {path}: {exc}", file=sys.stderr)
            failed = True
            continue

        if item == "mills_bomb":
            print(f"{path}: {score:.1f} {metric}")
        else:
            print(f"{path}: {score:.2%} {metric}")
        minimum = MIN_MILLS_CONTRAST if item == "mills_bomb" else MIN_SHOWCASE_FRACTION
        if score < minimum:
            if item == "mills_bomb":
                reason = f"held grenade is not visible in its expected region (need contrast >= {minimum:g})"
            else:
                reason = f"no clearly visible, near-centered model (need >= {minimum:.2%})"
            print(f"FAIL {path.name}: {reason}", file=sys.stderr)
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
