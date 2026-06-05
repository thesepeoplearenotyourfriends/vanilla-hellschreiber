<img width="546" height="258" alt="image" src="https://github.com/user-attachments/assets/576247da-b0f9-4663-ac2e-d4922bf75ed7" />


# Vanilla Python, JavaScript and Android Hellschreiber Toys

This repository contains three dependency-free Feld-Hell / Hellschreiber experiments:

- `hellschreiber.py` is a command-line Python toy that can transmit text to a WAV file and receive a WAV file into a visual strip.
- `index.html` is a browser-only single-page playground with its CSS and JavaScript embedded in the same file. Try it at https://thesepeoplearenotyourfriends.github.io/vanilla-hellschreiber/
- `hell-android-apk/` is a vanilla Android app project named HellScribe for touch-first speaker transmit and microphone receive.

Both versions are intentionally small, readable, and educational. They are not intended to be robust radio modems, automatic OCR tools, or production DSP implementations.

## What is implemented

The toys use a simple 5x7 bitmap font and the classic Hellschreiber idea of scanning each glyph column-by-column, top-to-bottom. A black dot becomes a short sine tone burst, and a white dot becomes silence. The receive side measures energy in one dot-sized audio window at a time and reshapes the result back into a seven-row visual strip for human reading.

Default settings are shared between the two versions where practical:

- Tone: `1000 Hz`
- Dot rate: `122.5 dots/second`
- Sample rate: `8000 Hz`
- Rows: `7`

## Python CLI version

The Python program has no third-party package dependencies. It uses only the Python standard library.

### Create a WAV from text

```sh
python3 hellschreiber.py tx "CQ CQ HELLSCHREIBER 123" -o hell.wav
```

Optional transmit settings:

```sh
python3 hellschreiber.py tx "NIMITZ" --tone 1000 --rate 122.5 --sample-rate 8000 -o nimitz.wav
```

### Render a WAV visually

```sh
python3 hellschreiber.py rx hell.wav
```

You can also write a grayscale PGM image:

```sh
python3 hellschreiber.py rx hell.wav -o hell.pgm
```

### Play a WAV on Linux with ALSA

```sh
python3 hellschreiber.py play hell.wav
```

The play command calls `aplay` if it is installed. You can also open the generated WAV in any normal audio player.

### Preview the font

```sh
python3 hellschreiber.py fontdemo "HELLO 123"
```

## Android APK version
<img width="288" height="649" alt="image" src="https://github.com/user-attachments/assets/20724d74-265d-49e7-a137-6350f3809c45" />

The `hell-android-apk/` directory contains HellScribe, a small Android app project using only the Android framework. It keeps the same 5x7 Hellschreiber rendering idea and default tone settings, but favors phone workflows over files:

- Large touch-friendly Render, Play speaker, Listen mic, and Stop controls.
- Speaker transmit using Android `AudioTrack`.
  - Playback debug progress: the Android transmitter now builds one PCM buffer for the entire rendered message, streams it through a single `MODE_STREAM` `AudioTrack`, and waits for the playback head to drain the queued samples before stopping/releasing the track.
- Microphone visual receive using Android `AudioRecord` and a Goertzel tone detector.
- System-aware light/dark colors so the UI is not forced bright white on dark-mode devices.
- Android SDK 34 target settings, a no-Gradle project layout, and no third-party runtime dependencies.

Build it with the repository maintainer's no-Gradle shell script by passing the Android project directory:

```sh
/path/to/your/build-apk.sh hell-android-apk
```

## Browser JavaScript version

Open `index.html` directly in a modern browser. There is no npm install, no build step, no WebAssembly, no CDN, and no framework.

The page provides:

- A message textarea.
- Buttons for `Render`, `Play`, `Download WAV`, `Load WAV`, and `Decode/Render Loaded WAV`.
- Controls for tone frequency, dot rate, sample rate, canvas pixel scale, receive threshold, and receive contrast.
- A blocky canvas strip with a horizontally scrollable container for long messages.
- A small ASCII preview.

### Transmit workflow

1. Type a message.
2. Click `Render` to draw the Hellschreiber strip.
3. Click `Play` to synthesize and play browser audio with `AudioBufferSourceNode`.
4. Click `Download WAV` to save a mono 16-bit PCM WAV written manually in JavaScript with `DataView`.

The browser transmitter converts text to uppercase. Characters that are not in the built-in font become spaces.

### Receive workflow

1. Click `Load WAV` and choose a WAV file.
2. Set the same tone and dot rate used by the transmitter.
3. Click `Decode/Render Loaded WAV`.
4. Adjust threshold and contrast until the seven-row strip is readable.

The browser receiver uses `AudioContext.decodeAudioData` for the first-pass WAV loader, downmixes decoded audio to mono, runs a pure-JavaScript Goertzel detector at the selected tone frequency, and renders energy as grayscale/thresholded Hell columns. It does not perform OCR.

## Notes and limitations

- These toys assume the transmit and receive dot rates match.
- The receiver is visual only; you read the rendered strip yourself.
- The simple tone detector is intentionally boring and understandable rather than highly optimized.
- Browser audio playback may require a user gesture, which the `Play` button provides.
