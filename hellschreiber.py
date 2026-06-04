#!/usr/bin/env python3
"""Tiny dependency-free Feld-Hell / Hellschreiber text-to-WAV toy.

This is a deliberately small retro dot-matrix audio experiment, not a robust
radio modem.  It can synthesize text as keyed audio tones and render such audio
back into a grayscale Hellschreiber-style strip for human reading.
"""

import argparse
import math
import shutil
import struct
import subprocess
import sys
import wave


DOT_RATE = 122.5
TONE_HZ = 1000
SAMPLE_RATE = 8000
ROWS = 7
AMPLITUDE = 0.65
CHAR_SPACING = 1
WORD_SPACING = 3

# 5x7 font.  Use # for ink and spaces for background so glyphs stay readable.
FONT = {
    " ": [
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
    ],
    "!": [
        "  #  ",
        "  #  ",
        "  #  ",
        "  #  ",
        "  #  ",
        "     ",
        "  #  ",
    ],
    "?": [
        " ### ",
        "#   #",
        "    #",
        "   # ",
        "  #  ",
        "     ",
        "  #  ",
    ],
    ".": [
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "  #  ",
    ],
    ",": [
        "     ",
        "     ",
        "     ",
        "     ",
        "     ",
        "  #  ",
        " #   ",
    ],
    "-": [
        "     ",
        "     ",
        "     ",
        " ### ",
        "     ",
        "     ",
        "     ",
    ],
    "/": [
        "    #",
        "    #",
        "   # ",
        "  #  ",
        " #   ",
        "#    ",
        "#    ",
    ],
    ":": [
        "     ",
        "  #  ",
        "     ",
        "     ",
        "     ",
        "  #  ",
        "     ",
    ],
    "0": [
        " ### ",
        "#   #",
        "#  ##",
        "# # #",
        "##  #",
        "#   #",
        " ### ",
    ],
    "1": [
        "  #  ",
        " ##  ",
        "# #  ",
        "  #  ",
        "  #  ",
        "  #  ",
        "#####",
    ],
    "2": [
        " ### ",
        "#   #",
        "    #",
        "   # ",
        "  #  ",
        " #   ",
        "#####",
    ],
    "3": [
        " ### ",
        "#   #",
        "    #",
        "  ## ",
        "    #",
        "#   #",
        " ### ",
    ],
    "4": [
        "   # ",
        "  ## ",
        " # # ",
        "#  # ",
        "#####",
        "   # ",
        "   # ",
    ],
    "5": [
        "#####",
        "#    ",
        "#    ",
        "#### ",
        "    #",
        "#   #",
        " ### ",
    ],
    "6": [
        " ### ",
        "#   #",
        "#    ",
        "#### ",
        "#   #",
        "#   #",
        " ### ",
    ],
    "7": [
        "#####",
        "    #",
        "   # ",
        "  #  ",
        " #   ",
        " #   ",
        " #   ",
    ],
    "8": [
        " ### ",
        "#   #",
        "#   #",
        " ### ",
        "#   #",
        "#   #",
        " ### ",
    ],
    "9": [
        " ### ",
        "#   #",
        "#   #",
        " ####",
        "    #",
        "#   #",
        " ### ",
    ],
    "A": [
        " ### ",
        "#   #",
        "#   #",
        "#####",
        "#   #",
        "#   #",
        "#   #",
    ],
    "B": [
        "#### ",
        "#   #",
        "#   #",
        "#### ",
        "#   #",
        "#   #",
        "#### ",
    ],
    "C": [
        " ### ",
        "#   #",
        "#    ",
        "#    ",
        "#    ",
        "#   #",
        " ### ",
    ],
    "D": [
        "#### ",
        "#   #",
        "#   #",
        "#   #",
        "#   #",
        "#   #",
        "#### ",
    ],
    "E": [
        "#####",
        "#    ",
        "#    ",
        "#### ",
        "#    ",
        "#    ",
        "#####",
    ],
    "F": [
        "#####",
        "#    ",
        "#    ",
        "#### ",
        "#    ",
        "#    ",
        "#    ",
    ],
    "G": [
        " ### ",
        "#   #",
        "#    ",
        "#  ##",
        "#   #",
        "#   #",
        " ### ",
    ],
    "H": [
        "#   #",
        "#   #",
        "#   #",
        "#####",
        "#   #",
        "#   #",
        "#   #",
    ],
    "I": [
        "#####",
        "  #  ",
        "  #  ",
        "  #  ",
        "  #  ",
        "  #  ",
        "#####",
    ],
    "J": [
        "#####",
        "    #",
        "    #",
        "    #",
        "    #",
        "#   #",
        " ### ",
    ],
    "K": [
        "#   #",
        "#  # ",
        "# #  ",
        "##   ",
        "# #  ",
        "#  # ",
        "#   #",
    ],
    "L": [
        "#    ",
        "#    ",
        "#    ",
        "#    ",
        "#    ",
        "#    ",
        "#####",
    ],
    "M": [
        "#   #",
        "## ##",
        "# # #",
        "# # #",
        "#   #",
        "#   #",
        "#   #",
    ],
    "N": [
        "#   #",
        "##  #",
        "##  #",
        "# # #",
        "#  ##",
        "#  ##",
        "#   #",
    ],
    "O": [
        " ### ",
        "#   #",
        "#   #",
        "#   #",
        "#   #",
        "#   #",
        " ### ",
    ],
    "P": [
        "#### ",
        "#   #",
        "#   #",
        "#### ",
        "#    ",
        "#    ",
        "#    ",
    ],
    "Q": [
        " ### ",
        "#   #",
        "#   #",
        "#   #",
        "# # #",
        "#  # ",
        " ## #",
    ],
    "R": [
        "#### ",
        "#   #",
        "#   #",
        "#### ",
        "# #  ",
        "#  # ",
        "#   #",
    ],
    "S": [
        " ####",
        "#    ",
        "#    ",
        " ### ",
        "    #",
        "    #",
        "#### ",
    ],
    "T": [
        "#####",
        "  #  ",
        "  #  ",
        "  #  ",
        "  #  ",
        "  #  ",
        "  #  ",
    ],
    "U": [
        "#   #",
        "#   #",
        "#   #",
        "#   #",
        "#   #",
        "#   #",
        " ### ",
    ],
    "V": [
        "#   #",
        "#   #",
        "#   #",
        "#   #",
        "#   #",
        " # # ",
        "  #  ",
    ],
    "W": [
        "#   #",
        "#   #",
        "#   #",
        "# # #",
        "# # #",
        "## ##",
        "#   #",
    ],
    "X": [
        "#   #",
        "#   #",
        " # # ",
        "  #  ",
        " # # ",
        "#   #",
        "#   #",
    ],
    "Y": [
        "#   #",
        "#   #",
        " # # ",
        "  #  ",
        "  #  ",
        "  #  ",
        "  #  ",
    ],
    "Z": [
        "#####",
        "    #",
        "   # ",
        "  #  ",
        " #   ",
        "#    ",
        "#####",
    ],
}


def text_to_dots(text):
    """Return Hell-scanned dots for text: columns left-to-right, rows top-down."""
    dots = []
    for char in text.upper():
        glyph = FONT.get(char, FONT[" "])
        width = len(glyph[0])
        for col in range(width):
            for row in range(ROWS):
                dots.append(1 if glyph[row][col] != " " else 0)

        spacing = WORD_SPACING if char == " " else CHAR_SPACING
        dots.extend([0] * ROWS * spacing)
    return dots


def samples_per_dot(sample_rate, dot_rate):
    return max(1, int(round(sample_rate / dot_rate)))


def tone_window(sample_rate, tone_hz, count, phase):
    samples = []
    step = 2.0 * math.pi * tone_hz / sample_rate
    fade = min(max(1, count // 6), 12)
    for index in range(count):
        envelope = 1.0
        if index < fade:
            envelope = index / fade
        elif index >= count - fade:
            envelope = (count - index - 1) / fade
        value = math.sin(phase) * AMPLITUDE * envelope
        samples.append(int(max(-1.0, min(1.0, value)) * 32767))
        phase += step
        if phase > 2.0 * math.pi:
            phase -= 2.0 * math.pi
    return samples, phase


def write_wav(path, dots, sample_rate=SAMPLE_RATE, dot_rate=DOT_RATE, tone_hz=TONE_HZ):
    per_dot = samples_per_dot(sample_rate, dot_rate)
    phase = 0.0
    with wave.open(path, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        for dot in dots:
            if dot:
                samples, phase = tone_window(sample_rate, tone_hz, per_dot, phase)
            else:
                samples = [0] * per_dot
            wav.writeframes(struct.pack("<" + "h" * len(samples), *samples))


def read_wav_mono(path):
    with wave.open(path, "rb") as wav:
        channels = wav.getnchannels()
        sample_width = wav.getsampwidth()
        sample_rate = wav.getframerate()
        frames = wav.getnframes()
        if channels not in (1, 2):
            raise SystemExit("Only mono or stereo WAV files are supported.")
        if sample_width not in (1, 2):
            raise SystemExit("Only 8-bit unsigned or 16-bit signed PCM WAV files are supported.")
        raw = wav.readframes(frames)

    samples = []
    if sample_width == 1:
        values = [byte - 128 for byte in raw]
    else:
        count = len(raw) // 2
        values = struct.unpack("<" + "h" * count, raw)

    for index in range(0, len(values), channels):
        if channels == 1:
            samples.append(values[index])
        else:
            samples.append((values[index] + values[index + 1]) / 2.0)
    return sample_rate, samples


def goertzel_energy(samples, sample_rate, tone_hz):
    if not samples:
        return 0.0
    omega = 2.0 * math.pi * tone_hz / sample_rate
    coeff = 2.0 * math.cos(omega)
    prev = 0.0
    prev2 = 0.0
    for sample in samples:
        current = sample + coeff * prev - prev2
        prev2 = prev
        prev = current
    power = prev2 * prev2 + prev * prev - coeff * prev * prev2
    return power / (len(samples) * len(samples))


def wav_to_brightness(path, dot_rate=DOT_RATE, tone_hz=TONE_HZ):
    sample_rate, samples = read_wav_mono(path)
    per_dot = samples_per_dot(sample_rate, dot_rate)
    energies = []
    for start in range(0, len(samples) - per_dot + 1, per_dot):
        window = samples[start : start + per_dot]
        energies.append(goertzel_energy(window, sample_rate, tone_hz))

    if not energies:
        return []

    floor = sorted(energies)[len(energies) // 10]
    peak = max(energies)
    scale = peak - floor
    if scale <= 0:
        return [0 for _ in energies]

    brightness = []
    for energy in energies:
        normalized = (energy - floor) / scale
        normalized = max(0.0, min(1.0, normalized))
        # Light compression keeps weak dots visible while preserving grayscale.
        brightness.append(int(round(math.sqrt(normalized) * 255)))
    return brightness


def dots_to_image(brightness):
    width = len(brightness) // ROWS
    image = [[0 for _ in range(width)] for _ in range(ROWS)]
    for index in range(width * ROWS):
        col = index // ROWS
        row = index % ROWS
        image[row][col] = brightness[index]
    return image


def write_pgm(path, image):
    height = len(image)
    width = len(image[0]) if height else 0
    with open(path, "wb") as handle:
        handle.write(f"P5\n{width} {height}\n255\n".encode("ascii"))
        for row in image:
            handle.write(bytes(row))


def print_ascii(image, ascii_width=None):
    if not image or not image[0]:
        print("(no dots detected)")
        return

    width = len(image[0])
    if ascii_width is None or ascii_width <= 0 or ascii_width >= width:
        step = 1
        out_width = width
    else:
        step = width / ascii_width
        out_width = ascii_width

    shades = " .:-=+*#%@"
    for row in image:
        chars = []
        for out_col in range(out_width):
            start = int(out_col * step)
            end = max(start + 1, int((out_col + 1) * step))
            value = sum(row[start:min(end, width)]) / (min(end, width) - start)
            chars.append(shades[int(value * (len(shades) - 1) / 255)])
        print("".join(chars).rstrip())


def command_tx(args):
    dots = text_to_dots(args.text)
    write_wav(args.output, dots, args.sample_rate, args.rate, args.tone)
    print(f"Wrote {args.output}")


def command_rx(args):
    brightness = wav_to_brightness(args.input, args.rate, args.tone)
    image = dots_to_image(brightness)
    if args.output:
        write_pgm(args.output, image)
        print(f"Wrote {args.output}")
    print_ascii(image, args.ascii_width)


def command_play(args):
    if not shutil.which("aplay"):
        print("aplay was not found; install ALSA utilities or play the WAV another way.", file=sys.stderr)
        return 1
    return subprocess.run(["aplay", args.input]).returncode


def command_fontdemo(args):
    text = args.text.upper()
    glyphs = [FONT.get(char, FONT[" "]) for char in text]
    for row in range(ROWS):
        print(" ".join(glyph[row] for glyph in glyphs).rstrip())


def build_parser():
    parser = argparse.ArgumentParser(description="Tiny Feld-Hell / Hellschreiber WAV toy")
    subparsers = parser.add_subparsers(dest="command", required=True)

    tx = subparsers.add_parser("tx", help="turn text into a mono 16-bit PCM WAV")
    tx.add_argument("text", help="text to transmit; converted to uppercase")
    tx.add_argument("-o", "--output", default="hell.wav", help="output WAV path")
    tx.add_argument("--tone", type=float, default=TONE_HZ, help="tone frequency in Hz")
    tx.add_argument("--rate", type=float, default=DOT_RATE, help="dot rate in dots/sec")
    tx.add_argument("--sample-rate", type=int, default=SAMPLE_RATE, help="WAV sample rate in Hz")
    tx.set_defaults(func=command_tx)

    rx = subparsers.add_parser("rx", help="render a Hellschreiber WAV as a visual strip")
    rx.add_argument("input", help="input WAV path")
    rx.add_argument("-o", "--output", help="output PGM path")
    rx.add_argument("--tone", type=float, default=TONE_HZ, help="tone frequency in Hz")
    rx.add_argument("--rate", type=float, default=DOT_RATE, help="dot rate in dots/sec")
    rx.add_argument("--ascii-width", type=int, help="scale terminal preview to this width")
    rx.set_defaults(func=command_rx)

    play = subparsers.add_parser("play", help="play a WAV with aplay if available")
    play.add_argument("input", help="input WAV path")
    play.set_defaults(func=command_play)

    fontdemo = subparsers.add_parser("fontdemo", help="print the built-in bitmap font")
    fontdemo.add_argument("text", nargs="?", default="NIMITZ", help="text to display")
    fontdemo.set_defaults(func=command_fontdemo)
    return parser


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args) or 0


if __name__ == "__main__":
    raise SystemExit(main())
