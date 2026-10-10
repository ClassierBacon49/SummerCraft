"""Synthesises the mod's sounds as OGG files.

Run with the tools venv:  tools/.venv/Scripts/python tools/gen_sounds.py
Loops are an exact number of cycles long so they repeat without a click.
"""
import json
import os

import numpy as np
import soundfile as sf

SR = 22050
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "summercraft")
rng = np.random.default_rng(3)


def lowpass(x, cutoff):
    """One-pole low-pass, run twice for a softer slope."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        for i, v in enumerate(x):
            acc = (1 - a) * v + a * acc
            y[i] = acc
        x = y
    return x


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def normalise(x, peak=0.9):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


# Firing pattern over one 720-degree engine cycle: (position in the cycle, loudness) per cylinder.
# Uneven loudness is what gives a boxer or a cross-plane V8 its burble.
PATTERNS = {
    "inline_4": [(i / 4, 1.0) for i in range(4)],
    "boxer_4": [(0.0, 1.0), (0.27, 0.7), (0.5, 0.95), (0.77, 0.65)],
    "v6": [(i / 6, a) for i, a in enumerate([1.0, 0.85, 0.95, 0.8, 1.0, 0.88])],
    "v8": [(i / 8, a) for i, a in enumerate([1.0, 0.7, 1.0, 1.0, 0.7, 1.0, 0.7, 0.7])],
}
CHARACTER = {"inline_4": (0.0, 1.0), "boxer_4": (-0.1, 0.8), "v6": (0.15, 1.0), "v8": (-0.15, 0.7)}


def engine(kind, rpm, seconds, brightness):
    """One firing pulse per cylinder per two crank turns: decaying resonant thumps placed by the firing pattern."""
    pattern = PATTERNS[kind]
    bright_shift, tone = CHARACTER[kind]
    brightness = max(0.05, brightness + bright_shift)
    cycle_hz = rpm / 60 / 2
    cycles = max(1, int(round(cycle_hz * seconds)))
    cycle_len = int(round(SR / cycle_hz))
    fire_hz = cycle_hz * len(pattern)
    pulse_len = int(SR / fire_hz * 1.6)
    t = np.arange(pulse_len) / SR
    out = np.zeros(cycles * cycle_len + pulse_len)
    for c in range(cycles):
        for pos, amp in pattern:
            start = c * cycle_len + int(pos * cycle_len)
            a = amp * (1 + rng.normal(0, 0.05))
            decay = np.exp(-t * fire_hz * 3.0)
            body = np.sin(2 * np.pi * fire_hz * tone * t) + 0.6 * np.sin(2 * np.pi * fire_hz * 2.0 * tone * t + 0.4)
            ring = brightness * np.sin(2 * np.pi * fire_hz * 5.3 * t) * np.exp(-t * fire_hz * 8)
            noise = rng.normal(0, 0.35 * brightness, pulse_len) * np.exp(-t * fire_hz * 6)
            out[start:start + pulse_len] += a * decay * (body + ring + noise)
    # Wrap the tail of the last pulses round to the start so the loop is seamless.
    tail = out[cycles * cycle_len:]
    out = out[:cycles * cycle_len]
    out[:len(tail)] += tail
    rumble = lowpass(rng.normal(0, 0.15, len(out)), 120)
    out = lowpass(out + rumble, 1800 + 2500 * brightness)
    return normalise(crossfade_loop(highpass(out, 25), samples=cycle_len * 2))


def _resonant_noise(seconds, centre, q, wobble, rng_):
    """Noise forced through a narrow, slowly wandering band: air ringing in a housing rather than a pure tone."""
    from scipy import signal as sig
    sr = 44100
    n = int(seconds * sr)
    noise = rng_.normal(0, 1, n)
    out = np.zeros(n)
    block = 2048
    drift = np.cumsum(rng_.normal(0, 1, n // block + 2))
    drift = drift / (np.max(np.abs(drift)) + 1e-9)
    zi = None
    for b in range(0, n, block):
        f = centre * (1 + wobble * drift[b // block])
        bw = f / q
        sos = sig.butter(2, [f - bw / 2, f + bw / 2], "bandpass", fs=sr, output="sos")
        if zi is None:
            zi = sig.sosfilt_zi(sos) * 0
        seg, zi = sig.sosfilt(sos, noise[b:b + block], zi=zi)
        out[b:b + block] = seg
    return out


def whistle(seconds=2.0):
    """Turbo spool: an airy whoosh with a ringing whistle on top, both made of noise."""
    from scipy import signal as sig
    sr = 44100
    rng_ = np.random.default_rng(11)
    ring = _resonant_noise(seconds + 0.3, 3100, 18, 0.03, rng_)
    ring2 = _resonant_noise(seconds + 0.3, 6200, 22, 0.03, rng_) * 0.3
    whoosh = sig.sosfilt(sig.butter(2, [700, 5000], "bandpass", fs=sr, output="sos"), rng_.normal(0, 1, len(ring)))
    x = ring / np.std(ring) * 0.6 + ring2 / np.std(ring2) * 0.15 + whoosh / np.std(whoosh) * 0.5
    n, fade = int(seconds * sr), int(0.3 * sr)
    body, tail = x[:n].copy(), x[n:n + fade]
    ramp = np.linspace(0, 1, len(tail))
    body[:len(tail)] = body[:len(tail)] * ramp + tail * (1 - ramp)
    return normalise(body, 0.7)


def whine(seconds=2.0):
    """Supercharger: gear-driven rotors give a rough, slightly unsteady whine over a rush of air."""
    from scipy import signal as sig
    sr = 44100
    rng_ = np.random.default_rng(12)
    a = _resonant_noise(seconds + 0.3, 1250, 40, 0.01, rng_)
    b = _resonant_noise(seconds + 0.3, 2500, 45, 0.01, rng_) * 0.5
    air = sig.sosfilt(sig.butter(2, [400, 3000], "bandpass", fs=sr, output="sos"), rng_.normal(0, 1, len(a)))
    x = a / np.std(a) * 0.55 + b / np.std(b) * 0.25 + air / np.std(air) * 0.35
    n, fade = int(seconds * sr), int(0.3 * sr)
    body, tail = x[:n].copy(), x[n:n + fade]
    ramp = np.linspace(0, 1, len(tail))
    body[:len(tail)] = body[:len(tail)] * ramp + tail * (1 - ramp)
    return normalise(body, 0.7)


def blow_off():
    t = np.arange(int(0.7 * SR)) / SR
    hiss = highpass(rng.normal(0, 1, len(t)), 1500)
    env = np.minimum(1, t * 80) * np.exp(-t * 5)
    flutter = 0.7 + 0.3 * np.sign(np.sin(2 * np.pi * 28 * t))
    return normalise(hiss * env * flutter)


def crossfade_loop(x, fade_seconds=0.15, samples=None):
    n = samples or int(fade_seconds * SR)
    head, tail = x[:n], x[-n:]
    ramp = np.linspace(0, 1, n)
    x = x[:-n].copy()
    x[:n] = head * ramp + tail * (1 - ramp)
    return x


def starter(seconds=2.0):
    t = np.arange(int(seconds * SR)) / SR
    whine = 0.35 * np.sin(2 * np.pi * 320 * t) + 0.2 * np.sin(2 * np.pi * 640 * t + 1)
    chug_hz = 9.0
    chug = 0.5 + 0.5 * np.sin(2 * np.pi * chug_hz * t) ** 8
    thump = np.sin(2 * np.pi * 60 * t) * chug
    grind = lowpass(rng.normal(0, 0.4, len(t)), 2000) * chug
    return normalise((whine * (0.6 + 0.4 * chug) + thump + grind) * 0.8)


def squeal(seconds=2.0):
    t = np.arange(int(seconds * SR)) / SR
    wobble = 60 * np.sin(2 * np.pi * 3.1 * t) + 30 * np.sin(2 * np.pi * 7.3 * t)
    phase = 2 * np.pi * np.cumsum(1050 + wobble) / SR
    tone = np.sin(phase) + 0.4 * np.sin(2 * phase) + 0.15 * np.sin(3 * phase)
    hiss = highpass(rng.normal(0, 0.5, len(t)), 1500)
    return normalise(crossfade_loop(tone * (0.8 + 0.2 * np.sin(2 * np.pi * 11 * t)) + hiss))


def horn(seconds=1.0):
    t = np.arange(int(seconds * SR)) / SR
    def buzz(f):
        return np.tanh(3 * np.sin(2 * np.pi * f * t))
    return normalise(lowpass(buzz(400) + buzz(500), 3500) * 0.8)


def burst(seconds, decay, cutoff, tone=None):
    t = np.arange(int(seconds * SR)) / SR
    env = np.exp(-t * decay)
    x = lowpass(rng.normal(0, 1, len(t)), cutoff) * env
    if tone:
        for f, d in tone:
            x += 0.6 * np.sin(2 * np.pi * f * t) * np.exp(-t * d)
    return normalise(x)


def ratchet():
    x = np.zeros(int(0.12 * SR))
    for start in (0.0, 0.05):
        click = burst(0.03, 250, 6000, [(2800, 300)])
        i = int(start * SR)
        x[i:i + len(click)] += click
    return normalise(x)


def gear_grind():
    t = np.arange(int(0.6 * SR)) / SR
    teeth = np.sign(np.sin(2 * np.pi * 180 * t)) * (0.5 + 0.5 * np.sin(2 * np.pi * 23 * t))
    grit = highpass(rng.normal(0, 0.8, len(t)), 800)
    env = np.minimum(1, t * 30) * np.exp(-t * 3)
    return normalise((teeth + grit) * env)


SOUNDS = {
    "turbo_whistle": whistle,
    "supercharger_whine": whine,
    "blow_off": blow_off,
    "starter": starter,
    "tyre_squeal": squeal,
    "horn": horn,
    "ratchet": ratchet,
    "gear_shift": lambda: burst(0.25, 25, 900, [(140, 30), (1900, 60)]),
    "gear_grind": gear_grind,
    "backfire": lambda: burst(0.45, 14, 1200, [(70, 12)]),
    "crash": lambda: burst(0.9, 6, 3000, [(310, 5), (733, 7), (1180, 9), (2050, 12)]),
}

SUBTITLES = {
    "turbo_whistle": "turbo", "supercharger_whine": "supercharger", "blow_off": "blow_off", "starter": "starter", "tyre_squeal": "tyre_squeal",
    "horn": "horn", "ratchet": "ratchet", "gear_shift": "gear_shift", "gear_grind": "gear_grind",
    "backfire": "backfire", "crash": "crash",
}

if __name__ == "__main__":
    os.makedirs(os.path.join(ROOT, "sounds"), exist_ok=True)
    manifest = {}
    for name, make in SOUNDS.items():
        data = make().astype(np.float32)
        rate = 44100 if name in ("turbo_whistle", "supercharger_whine") else SR
        sf.write(os.path.join(ROOT, "sounds", name + ".ogg"), data, rate, format="OGG", subtype="VORBIS")
        manifest[name] = {"sounds": [{"name": f"summercraft:{name}"}], "subtitle": f"subtitles.summercraft.{SUBTITLES[name]}"}
        print(f"{name}: {len(data) / SR:.2f}s")
    # Engines: four recordings per engine at different revs, made by engine_synth at full sample rate.
    import engine_synth
    for kind in engine_synth.PATTERNS:
        for layer, rpm in enumerate(engine_synth.LAYER_RPMS):
            name = f"engine_{kind}_{layer}"
            data = engine_synth.engine_loop(kind, rpm, 3.0, np.random.default_rng(layer * 17 + len(kind))).astype(np.float32)
            sf.write(os.path.join(ROOT, "sounds", name + ".ogg"), data, engine_synth.SR, format="OGG", subtype="VORBIS")
            manifest[name] = {"sounds": [{"name": f"summercraft:{name}"}], "subtitle": "subtitles.summercraft.engine"}
            print(f"{name}: {len(data) / engine_synth.SR:.2f}s @ {rpm} rpm")
    with open(os.path.join(ROOT, "sounds.json"), "w") as f:
        json.dump(manifest, f, indent=2)
