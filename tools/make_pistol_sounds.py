#!/usr/bin/env python3
"""Original procedural game SFX, NOT field recordings or claims of acoustic authenticity.
C96: slightly longer/lower report + short bolt return. TT: shorter brighter report + slide return.
Deterministic source; numpy==2.2.6, soundfile==0.13.1. Also leaves headroom in the legacy PPSh sample; no changes to its timing or pitch.
"""
from pathlib import Path
import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]
SR = 44100


def shot(seed, duration, low, decay, action_at):
    rng = np.random.default_rng(seed)
    t = np.arange(round(duration * SR)) / SR
    noise = rng.standard_normal(len(t))
    frequencies = np.fft.rfftfreq(len(t), 1 / SR)
    spectrum = np.fft.rfft(noise)
    body = np.fft.irfft(spectrum / (1 + (frequencies / low)**4), n=len(t))
    body /= np.max(np.abs(body))
    crack = np.diff(noise, prepend=noise[0])
    crack /= np.max(np.abs(crack))
    envelope = (1 - np.exp(-t / .00035)) * np.exp(-t / decay)
    x = body * envelope + crack * (1 - np.exp(-t / .00012)) * np.exp(-t / .008) * .65
    x += .10 * noise * (1 - np.exp(-t / .012)) * np.exp(-t / .065)
    for at, hz, amp in ((.025, 1900, .10), (action_at, 2700, .08)):
        a = np.maximum(0, t - at)
        x += (t >= at) * np.sin(2 * np.pi * hz * a) * np.exp(-a / .006) * amp
    x *= np.minimum(1, (duration - t) / .02)
    return x / np.max(np.abs(x)) * .76


def main():
    folder = ROOT / 'mods/aiarsenal/src/main/resources/assets/aiarsenal/sounds'
    legacy = folder / 'smg_ppsh.ogg'
    data, rate = sf.read(legacy)
    peak = np.max(np.abs(data))
    if peak > .22:  # idempotent: do not repeatedly transcode an already safe sample
        sf.write(legacy, data * (.18 / peak), rate, format='OGG', subtype='VORBIS')
    for name, args in [('pistol_c96', (96, .48, 750, .033, .105)),
                       ('pistol_tt33', (33, .40, 1050, .026, .085))]:
        path = folder / (name + '.ogg')
        sf.write(path, shot(*args), SR, format='OGG', subtype='VORBIS')
        print(path)


if __name__ == '__main__':
    main()
