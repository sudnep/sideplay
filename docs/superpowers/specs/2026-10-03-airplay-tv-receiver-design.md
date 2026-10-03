# AirPlay Receiver for Android TV — Design

Date: 2026-10-03
Status: Draft for review

## 1. Intent

**Outcome.** A free Android TV app on Google Play that lets iPhone/iPad/Mac users show content on their TV via AirPlay: screen mirroring and AirPlay video (sending a video from an app to the TV).

**Who it's for.** Anyone with a Google TV / Android TV and an Apple device. First and primary user: the author, on a Hisense Google TV ("Living Room TV", model `SmartTV 4K FFM`).

**Why build it (what the user said).**
- The best existing open-source receiver, `jqssun/android-airplay-server`, is not installable from Play on the Hisense TV.
- AirPlay video only works reliably from YouTube; Safari and Photos do not.
- The existing UI is phone-first and admin-like (Start/Stop server, logs tab, touch gestures).

**Success criteria.**
1. The app is installable from Google Play on the Hisense TV.
2. Screen mirroring from an iPhone starts in under 3 s and runs for 10 minutes without stalls on the Hisense.
3. AirPlay video works from Safari (MP4 and HLS), YouTube, Photos (photos and videos) and Vimeo, with play/pause/seek/stop controlled from the iPhone and the TV remote.
4. Every screen is fully usable with a D-pad remote; the app passes Google's TV app quality review.

**Assumptions (not stated by the user).** No phone/tablet support in v1. No monetization (no ads, no analytics).

## 2. Constraints

- **License.** Base code is GPL-3.0, so the whole app is GPL-3.0 with public source. No closed-source SDKs.
- **Trademark.** "AirPlay" is an Apple trademark. The app gets its own brand; "AirPlay" appears only descriptively ("for AirPlay"), with a "not affiliated with Apple" disclaimer. No Apple logos.
- **FairPlay.** Mirroring depends on the reverse-engineered `playfair` code bundled in UxPlay. Accepted risk: about 10 years with no known takedown, and unavoidable for modern iOS mirroring.
- **DRM.** Netflix, Apple TV+, Disney+ and similar DRM content are out of scope; no open-source receiver can play them.

## 3. Approach

Fork `jqssun/android-airplay-server` (Kotlin + JNI around the `FDH2/UxPlay` C core, which is in active development) into this repository. Keep the engine close to upstream so UxPlay and jqssun fixes keep merging cleanly. Rewrite the UI for TV, and fix AirPlay video on top.

Alternatives considered and rejected:
- **New app reusing only the engine.** Cleaner, but more upfront work and harder upstream merges.
- **Upstream contributions plus a rebrand.** Depends on a maintainer accepting changes; doesn't give a distinct app.
- **Other bases.** RPiPlay is abandoned (2022). dsafa22/AirplayServer was deleted. PhairPlay has a licence conflict (labelled Apache-2.0 but bundles GPL code). Several others have no licence.

**Working name.** "Sideplay", package `io.github.sudnep.sideplay`. This is a working name only; the final name and package are chosen before the first Play upload (section 8.2), and changing them is a mechanical rename.

## 4. Architecture

Three layers, matching the upstream layout:

| Layer | Location | Policy |
|---|---|---|
| Engine | `app/src/main/cpp/third_party/` (UxPlay, libplist, openssl-cmake, ffmpeg as git submodules) | **Unmodified.** Tracks upstream. |
| Engine patches | `app/src/main/cpp/patches/` plus the JNI bridge (`android_raop_callbacks.c`) | All our C changes live here, applied over UxPlay at build time. Each patch is small, documented, and offered upstream as a PR. |
| Service and rendering | `service/AirPlayService`, `renderer/*` (Kotlin) | Mostly unchanged. Fixes are minimal and isolated so jqssun's changes can still be merged in. |
| UI | `ui/*`, `MainActivity` | **Rewritten** for TV using Compose for TV (`androidx.tv`). |

**Data flow.**
1. The iPhone discovers the TV through the mDNS advertisement (`_airplay._tcp` / `_raop._tcp`).
2. **Mirroring:** RAOP mirror stream → H.264/H.265 → MediaCodec → Surface.
3. **AirPlay video:** `POST /play` → classifier (section 5) → ExoPlayer, either directly or through UxPlay's local HLS proxy.
4. **Audio:** AAC/ALAC → AudioTrack.
5. Session state (idle / mirroring / video / audio) is exposed as a `StateFlow` from the service to the UI.

**Removed from upstream:** touch gesture UI, video downloader, playback speed selector, rotation and lock controls, the Start/Stop server button, and the Logs tab as a top-level screen (it moves into Troubleshooting).

## 5. AirPlay video fixes

### 5.1 Request types (from reading UxPlay `lib/http_handlers.h`)

| Type | Trigger | Current behaviour | Fix |
|---|---|---|---|
| **A. Direct URL** | `Content-Location` begins `http(s)://` (Safari, websites, Vimeo) | Handed to ExoPlayer via `on_video_play` | Pass the right User-Agent and headers through ExoPlayer, follow redirects, report accurate duration, position and rate through `/playback-info`, and handle MP4 and HLS formats. |
| **B. FCUP HLS** | `Content-Location` ends `/master.m3u8`; UxPlay fetches the playlists from the phone over the reverse channel | Works only for `clientProcName == "YouTube"`, because the playlist rewriting is YouTube-specific (`YT-EXT-CONDENSED-URI`) | Generalize playlist rewriting to standard HLS, and keep the YouTube special case. |
| **C. Photos** | `PUT /photo` (pictures) plus Photos video sessions | No `/photo` route exists in `raop.c` | Add a `/photo` handler that decodes the JPEG and shows it full-screen through a new JNI callback `onPhoto(bytes)`; handle Photos video according to what the capture step finds. |

### 5.2 Method

1. **Capture first.** A debug-only logger records every `/play`, `/photo`, `/action` and `/playback-info` request (path, `clientProcName`, `Content-Location` shape, headers) and every ExoPlayer error, written to a file that can be exported from Troubleshooting.
2. Run the source matrix (section 7.3) on the Hisense TV and record pass/fail with the captured requests.
3. Fix by type, then re-run the matrix.

### 5.3 User-facing errors

When playback fails, or the content is DRM-protected, show a clear message on the TV ("This app's videos can't be played on this receiver — try Screen Mirroring"). Do not fail silently or crash.

## 6. TV user interface

All screens are built with Compose for TV and fully usable with a D-pad. The receiver is always on; there is no Start button.

1. **Idle (home).** Ambient full-screen background; large "*<TV name>* is ready"; three steps (Control Center → Screen Mirroring → *<TV name>*); IP address in small text; the PIN shown large when PIN pairing is on. The TV name defaults to the system device name.
2. **Mirroring.** Edge-to-edge video with no chrome. A 3-second "*<device>* connected" toast. Back → "Disconnect?" prompt.
3. **Video playback.** D-pad play/pause and seek, an auto-hiding progress bar, and title and artwork when the iPhone sends them. Controls from the iPhone and the TV remote stay in sync.
4. **Audio only.** Album art, title and artist on a blurred background.
5. **Settings.** Device name, PIN on/off, start at boot, H.265 on/off (default chosen automatically per decoder), then a Troubleshooting section: logs (view/export), network info, decoder info, and About with licences and source link.
6. **First run.** One screen to confirm the TV name and turn on start at boot, then Idle.

**Background receiving.** A foreground service keeps the receiver running. When a session starts, the activity is brought to the front using the "display over other apps" permission (`SYSTEM_ALERT_WINDOW`). **Risk:** some Google TV builds hide that setting. First plan task: verify this on the Hisense. Fallback: receiving only works while the app is open, and the Idle screen says so.

## 7. Testing

### 7.1 Automated

- **JVM unit tests:** the `/play` request classifier (types A/B/C), session state machine, settings repository.
- **Native tests:** C tests for the patch layer (HLS playlist rewriting, `/photo` parsing), built with host CMake.
- **Compose UI tests:** every screen can be reached and left by D-pad; focus is never lost.

### 7.2 Devices

- **Primary:** Hisense Google TV `SmartTV 4K FFM` (192.168.1.248). Every feature is accepted here first.
- **Secondary:** Sony BRAVIA XBR-55X800G, Android TV (192.168.1.160).
- **Comparison:** Fire TV running the commercial AirReceiver app (192.168.1.209).
- **Senders:** the author's available iPhone/iPad/Mac; the iOS/macOS version is recorded in each run.
- **Deployment:** ADB over Wi-Fi (requires Developer options and network debugging on the TV, plus `android-platform-tools` on the Mac).

### 7.3 Source matrix (acceptance)

| Source | Checks |
|---|---|
| Screen Mirroring (iPhone) | start < 3 s, 10 min with no stalls, portrait↔landscape, reconnect after the phone sleeps |
| Screen Mirroring (Mac) | start, 10 min with no stalls |
| Safari, MP4 page | play, pause, seek, stop |
| Safari, HLS page | play, pause, seek, stop |
| YouTube app | play, pause, seek, stop, ads → content |
| Photos, picture | shows, swipe to next |
| Photos, video | play, pause, seek, stop |
| Vimeo app | play, pause, seek, stop |
| Music (audio) | plays, metadata and artwork shown |
| Netflix / Apple TV+ | clear "not supported" message, no crash |

## 8. Release

### 8.1 Distribution
- GPL-3.0; source on a public GitHub repo; credits to jqssun, UxPlay, playfair, FFmpeg, libplist and OpenSSL in the README and the About screen.
- Play Console: Android TV form factor turned on; TV banner (320×180), TV screenshots, and a short video, all meeting the TV quality guidelines.
- Rollout: internal testing (installed **from Play** on the Hisense, which verifies success criterion 1) → open testing → production.
- ABIs: `arm64-v8a`, `armeabi-v7a`. FFmpeg trimmed to the codecs in use.

### 8.2 Before the first upload
- Final app name and package ID (replacing "Sideplay").
- Listing text using "for AirPlay" descriptively, plus the Apple disclaimer.
- Privacy policy page stating that no data is collected.

## 9. Out of scope (v1)

DRM content, AirPlay 2 multi-room audio, phone/tablet layouts, Fire TV / Amazon Appstore builds, and closed-source SDKs of any kind.
