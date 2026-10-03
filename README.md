# Sideplay

A free, open-source receiver for AirPlay on Android TV and Google TV. Mirror an iPhone, iPad or Mac screen, or send videos to your TV.

Sideplay is not affiliated with, endorsed by, or sponsored by Apple Inc. AirPlay is a trademark of Apple Inc.

## Status

Early development. Design: `docs/superpowers/specs/2026-10-03-airplay-tv-receiver-design.md`.

## Building

Requirements: macOS or Linux, Homebrew `openjdk@17`, the Android SDK, NDK 27.0.12077973, CMake 3.22.1.

    git clone --recurse-submodules <this repo>
    ./gw :app:assembleDebug

## Credits

Sideplay is a fork of [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) and builds on:

- [UxPlay](https://github.com/FDH2/UxPlay) (GPL-3.0): AirPlay protocol implementation
- playfair (GPL-3.0), bundled in UxPlay: FairPlay handshake
- [libplist](https://github.com/libimobiledevice/libplist) (LGPL-2.1)
- [OpenSSL](https://www.openssl.org/) via [openssl-cmake](https://github.com/viaduck/openssl-cmake) (Apache-2.0)
- [FFmpeg](https://ffmpeg.org/) (LGPL-2.1+)

## License

GPL-3.0. See `LICENSE`.
