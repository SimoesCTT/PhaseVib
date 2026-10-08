# PhaseVib

A phase-domain co-processor for the phone's motion sensors.

Reads the gyroscope and accelerometer at maximum rate, runs a phase-domain
analysis pipeline in parallel with the sensor stream, and displays live
Kuramoto order parameter and coherence metrics.

## Features

- Reads gyroscope and accelerometer at 200 Hz
- Rolling 512-sample buffer, phase pipeline every 128 samples
- Live Kuramoto order parameter r
- Live mean pairwise phase coherence
- CPU time comparison: FFT pipeline vs phase pipeline
- Raw data export as CSV
- No INTERNET, no permissions

## Status

Research and educational tool. Sensors only.

## Building

Requires JDK 17+, Android SDK 34.

    ./gradlew assembleDebug

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
