#!/usr/bin/env python3
"""Reject WWI showcase captures whose item is missing or framed away from center.

The four item-display views use the same gray-concrete backdrop. This check compares
an interior target region with a clean patch of that backdrop, so a valid PNG of an
empty wall cannot accidentally be published as proof of a rendered model.
"""
from __future__ import annotations

import argparse
from math import dist
from pathlib import Path
from statistics import median
import sys

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ITEMS = ("mills_bomb", "stielhandgranate", "wex", "wwi_fuel")
EXPECTED_SIZE = (1280, 800)
BACKGROUND = (1000, 100, 1150, 250)
TARGET = (420, 160, 860, 570)
MIN_VISIBLE_FRACTION = 0.003
MIN_COLOUR_DISTANCE = 28.0


def visible_fraction(path: Path) -> float:
    with Image.open(path) as source:
        image = source.convert("RGB")
    if image.size != EXPECTED_SIZE:
        raise ValueError(f"{path.name}: expected {EXPECTED_SIZE} screenshot, got {image.size}")

    background_pixels = list(image.crop(BACKGROUND).get_flattened_data())
    background = tuple(
        int(median(channel)) for channel in zip(*background_pixels)
    )
    target_pixels = image.crop(TARGET).get_flattened_data()
    threshold = MIN_COLOUR_DISTANCE
    different = sum(1 for pixel in target_pixels if dist(pixel, background) >= threshold)
    return different / ((TARGET[2] - TARGET[0]) * (TARGET[3] - TARGET[1]))


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
            fraction = visible_fraction(path)
        except Exception as exc:
            print(f"FAIL {path}: {exc}", file=sys.stderr)
            failed = True
            continue
        print(f"{path}: {fraction:.2%} distinct target pixels")
        if fraction < MIN_VISIBLE_FRACTION:
            print(
                f"FAIL {path.name}: no clearly visible, near-centered model "
                f"(need >= {MIN_VISIBLE_FRACTION:.2%})",
                file=sys.stderr,
            )
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
