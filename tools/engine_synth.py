"""Engine sound synthesis modelled on how an engine actually makes noise.

Each cylinder firing dumps a sharp pressure pulse into the exhaust. The pipe echoes it (a feedback comb filter,
which gives the throaty, hollow character), the muffler takes the harsh top off, and the body adds low rumble.
Firing is never perfectly even: every cylinder is slightly different and every bang varies a little. On top sits
some valvetrain clatter that pulses with the firing. Odd firing patterns (boxer, cross-plane V8) give the burble.
"""
import numpy as np
from scipy import signal

SR = 44100

# Position in the 720-degree cycle and loudness of each cylinder's exhaust pulse.
PATTERNS = {
    "inline_4": [(i / 4, 1.0) for i in range(4)],
    "boxer_4": [(0.0, 1.0), (0.24, 0.72), (0.5, 0.95), (0.76, 0.68)],
    "v6": [(i / 6, a) for i, a in enumerate([1.0, 0.86, 0.95, 0.82, 1.0, 0.9])],
    "v8": [(i / 8, a) for i, a in enumerate([1.0, 0.72, 1.0, 1.0, 0.72, 1.0, 0.72, 0.72])],
}

# pipe: echo delay (s) and feedback; muffler: low-pass cutoff (Hz); bass: low-end boost; mech: clatter; drive: grit
PARAMS = {
    "inline_4": dict(pipe=0.0085, pipe_g=0.45, muffler=1300, bass=0.6, mech=0.10, drive=1.3, pulse=0.0022),
    "boxer_4": dict(pipe=0.011, pipe_g=0.5, muffler=1000, bass=1.0, mech=0.08, drive=1.5, pulse=0.0028),
    "v6": dict(pipe=0.0075, pipe_g=0.4, muffler=1500, bass=0.7, mech=0.10, drive=1.4, pulse=0.0020),
    "v8": dict(pipe=0.012, pipe_g=0.55, muffler=850, bass=1.4, mech=0.07, drive=1.7, pulse=0.0032),
}

LAYER_RPMS = [1000, 2200, 3800, 5800]


def _band(lo, hi, order=2):
    return signal.butter(order, [lo, hi], "bandpass", fs=SR, output="sos")


def _low(f, order=4):
    return signal.butter(order, f, "lowpass", fs=SR, output="sos")


def _high(f, order=2):
    return signal.butter(order, f, "highpass", fs=SR, output="sos")


def engine_loop(kind, rpm, seconds, rng):
    pattern, p = PATTERNS[kind], PARAMS[kind]
    cycle = 2 * 60 / rpm
    cycles = max(2, round(seconds / cycle))
    length = int(round(cycles * cycle * SR))
    warm, fade = int(0.3 * SR), int(0.25 * SR)
    total = warm + length + fade

    # Excitation: one pressure pulse plus a burst of turbulence per firing.
    pulse_t = np.arange(int(0.03 * SR)) / SR
    pulse = np.exp(-pulse_t / p["pulse"]) - np.exp(-pulse_t / 0.0004)
    burst_env = np.exp(-pulse_t / 0.004)
    imbalance = 1 + rng.normal(0, 0.08, len(pattern))
    excitation = np.zeros(total + len(pulse_t))
    # The crank never turns at a perfectly steady speed: let the cycle length wander a little.
    time = 0.0
    while time * SR < total:
        this_cycle = cycle * (1 + rng.normal(0, 0.012))
        for i, (pos, amp) in enumerate(pattern):
            start = int((time + pos * this_cycle + rng.normal(0, 0.008 * cycle)) * SR)
            if start < 0 or start >= total:
                continue
            a = amp * imbalance[i] * (1 + rng.normal(0, 0.15))
            width = p["pulse"] * (1 + rng.normal(0, 0.15))
            shaped = np.exp(-pulse_t / max(width, 0.0008)) - np.exp(-pulse_t / 0.0004)
            turbulence = signal.sosfilt(_band(150, 4000), rng.normal(0, 1, len(pulse_t))) * burst_env
            excitation[start:start + len(pulse_t)] += a * (shaped + 0.9 * turbulence)
        time += this_cycle
    excitation = excitation[:total]

    # Exhaust pipe echo, then muffler (a little brighter at high revs), then body rumble.
    delay = int(p["pipe"] * SR)
    y = signal.lfilter([1.0], np.r_[1.0, np.zeros(delay - 1), -p["pipe_g"]], excitation)
    y = signal.sosfilt(_low(p["muffler"] * (0.75 + rpm / 6000 * 0.6)), y)
    y = y + 0.5 * p["bass"] * signal.sosfilt(_band(35, 160), y)

    # Valvetrain clatter and intake roar, pulsing with the firing.
    firing_env = signal.sosfilt(_low(80, 2), np.abs(excitation))
    firing_env /= firing_env.max() + 1e-9
    rms = np.sqrt(np.mean(y ** 2)) + 1e-9
    clatter = signal.sosfilt(_band(1800, 6000), rng.normal(0, 1, total)) * firing_env
    intake = signal.sosfilt(_band(200, 900), rng.normal(0, 1, total)) * (0.5 + 0.5 * firing_env)
    y = y + rms * (p["mech"] * 2 * clatter / (np.std(clatter) + 1e-9) + 0.3 * intake / (np.std(intake) + 1e-9))

    # A touch of saturation for grit, and take out DC and subsonic thump.
    y = np.tanh(y / (np.percentile(np.abs(y), 99.5) + 1e-9) * p["drive"])
    y = signal.sosfilt(_high(30), y)

    # Seamless loop: drop the warm-up, then crossfade the overrun into the start.
    y = y[warm:]
    body, tail = y[:length].copy(), y[length:length + fade]
    ramp = np.linspace(0, 1, fade)
    body[:fade] = body[:fade] * ramp + tail * (1 - ramp)
    return body / (np.max(np.abs(body)) + 1e-9) * 0.85
