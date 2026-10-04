# Plan 3b: Portrait video, persistent PIN, Safari diagnosis

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- Make portrait high-resolution video (Google Drive, YouTube Shorts) play on TVs whose decoders over-report portrait support.
- Keep the pairing PIN stable across receiver restarts, so paired phones aren't locked out.
- Capture what the Safari app sends before it gives up, so the Safari fix can be written from evidence.

**Architecture:** Two small pure functions with JVM tests (`VideoSizeCap`, `PersistentPin`), wired into `AirPlayVideoPlayer` and `AirPlayService`/`native_bridge.cpp`. One debug-only UxPlay patch (0009) logs RTSP requests. One short device session.

**Tech Stack:** Kotlin, Media3 ExoPlayer 1.11 (`TrackSelectionParameters`), C/JNI, UxPlay patch layer, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-03-airplay-tv-receiver-design.md` (section 5)
**Evidence:** `docs/findings/2026-10-plan3-session.md` (root causes A, B, C), `docs/findings/captures/drive-hls.txt`, `docs/findings/captures/safari-vs-airreceiver.txt`

## Global Constraints

Same as Plan 3, plus:
- **Never run `connectedAndroidTest` or install while the user is casting.** After any instrumented run, relaunch Sideplay, because instrumentation force-stops it and a force-stop defeats `START_STICKY` and `MY_PACKAGE_REPLACED`.
- Hisense decoders declare `size max="4096x2304"` for AVC, HEVC and VP9 (`/vendor/etc/media_codecs.xml`). Android lets either orientation through, which is why `1440x2560` is wrongly accepted.

## Review Focus

1. **Landscape 4K must still be allowed** (3840×2160 within 4096×2304). Tested in Task 1.
2. **A TV with no hardware decoder info** (empty caps) must not block all video. Tested in Task 1.
3. **The PIN is stable across restarts but must be 4 digits, including leading zeros** (e.g. `0042`), because UxPlay takes `pin + 10000`. Tested in Task 2.
4. **The VP9 fallback retry must happen at most once per `/play`**, so a non-VP9 failure can't loop. Tested in Task 1.

---

### Task 1: Cap video size to what the decoder really handles, with a VP9 fallback

**Files:**
- Create: `app/src/main/kotlin/io/github/jqssun/airplay/renderer/VideoSizeCap.kt`
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/renderer/AirPlayVideoPlayer.kt` (`play()`, `_listener.onPlayerError`)
- Test: `app/src/test/kotlin/io/github/jqssun/airplay/renderer/VideoSizeCapTest.kt`

**Interfaces:**
- Produces: `object VideoSizeCap { data class Size(val width: Int, val height: Int); fun limit(decoders: List<Size>): Size?; fun shouldRetryWithoutVp9(decodingFailed: Boolean, failedMime: String?, alreadyRetried: Boolean): Boolean; fun decoderLimits(): List<Size> }`. Here `decoderLimits()` reads `MediaCodecList`, returning (widest width, max height at that width) per hardware video decoder.

- [ ] **Step 1: Failing tests**

```kotlin
package io.github.jqssun.airplay.renderer

import io.github.jqssun.airplay.renderer.VideoSizeCap.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSizeCapTest {
    // hisense mt5895: every decoder declares 4096x2304
    private val hisense = listOf(Size(4096, 2304), Size(4096, 2304), Size(4096, 2304))

    @Test fun capIsLandscapeShaped() = assertEquals(Size(4096, 2304), VideoSizeCap.limit(hisense))

    private fun fits(cap: Size, w: Int, h: Int) = w <= cap.width && h <= cap.height

    @Test fun portrait1440pIsExcluded() = assertFalse(fits(VideoSizeCap.limit(hisense)!!, 1440, 2560))
    @Test fun portrait1080pIsAllowed() = assertTrue(fits(VideoSizeCap.limit(hisense)!!, 1080, 1920))
    @Test fun landscape4kIsAllowed() = assertTrue(fits(VideoSizeCap.limit(hisense)!!, 3840, 2160))

    @Test fun smallestDecoderWins() =
        assertEquals(Size(1920, 1088), VideoSizeCap.limit(listOf(Size(4096, 2304), Size(1920, 1088))))

    @Test fun noDecoderInfoMeansNoCap() = assertNull(VideoSizeCap.limit(emptyList()))

    @Test fun retryOnlyForVp9DecodeFailureOnce() {
        assertTrue(VideoSizeCap.shouldRetryWithoutVp9(true, "video/x-vnd.on2.vp9", alreadyRetried = false))
        assertFalse(VideoSizeCap.shouldRetryWithoutVp9(true, "video/x-vnd.on2.vp9", alreadyRetried = true))
        assertFalse(VideoSizeCap.shouldRetryWithoutVp9(true, "video/avc", alreadyRetried = false))
        assertFalse(VideoSizeCap.shouldRetryWithoutVp9(false, "video/x-vnd.on2.vp9", alreadyRetried = false))
    }
}
```

- [ ] **Step 2:** Run `./gw :app:testDebugUnitTest --tests 'io.github.jqssun.airplay.renderer.VideoSizeCapTest'`. Expected: compilation FAILS (`Unresolved reference 'VideoSizeCap'`).

- [ ] **Step 3: Implement**

```kotlin
package io.github.jqssun.airplay.renderer

import android.media.MediaCodecList
import android.os.Build

/**
 * Decoders declare a landscape max (e.g. 4096x2304) but android lets either orientation through, so a
 * portrait 1440x2560 stream passes the capability check and then fails in the hardware decoder.
 */
object VideoSizeCap {
    data class Size(val width: Int, val height: Int)

    fun limit(decoders: List<Size>): Size? =
        if (decoders.isEmpty()) null else Size(decoders.minOf { it.width }, decoders.minOf { it.height })

    fun shouldRetryWithoutVp9(decodingFailed: Boolean, failedMime: String?, alreadyRetried: Boolean): Boolean =
        decodingFailed && !alreadyRetried && failedMime == VP9

    fun decoderLimits(): List<Size> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder && (Build.VERSION.SDK_INT < 29 || it.isHardwareAccelerated) }
            .flatMap { info ->
                info.supportedTypes.filter { it.startsWith("video/") && it in VIDEO_TYPES }.mapNotNull { type ->
                    runCatching {
                        val caps = info.getCapabilitiesForType(type).videoCapabilities
                        val widest = caps.supportedWidths.upper
                        Size(widest, caps.getSupportedHeightsFor(widest).upper)
                    }.getOrNull()
                }
            }
    }.getOrDefault(emptyList())

    const val VP9 = "video/x-vnd.on2.vp9"
    private val VIDEO_TYPES = setOf("video/avc", "video/hevc", VP9)
}
```

- [ ] **Step 4:** Rerun the Step 2 command. Expected: 7 tests pass.

- [ ] **Step 5: Wire it into the player**

In `AirPlayVideoPlayer`:
- Add the fields `private var _vp9Retried = false` and `private var _lastLocation: String? = null`.
- In `play(location, startPositionSeconds)`, right after the `ExoPlayer.Builder(context).build()` player is created (look for `val p = ExoPlayer.Builder(context).build().also {`), set the parameters on `p`:
```kotlin
        _vp9Retried = false
        _lastLocation = location
        VideoSizeCap.limit(VideoSizeCap.decoderLimits())?.let { cap ->
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(cap.width, cap.height)
                .build()
        }
```
- In `_listener.onPlayerError`, before `onEnded?.invoke()`:
```kotlin
            val failedMime = (error as? androidx.media3.exoplayer.ExoPlaybackException)?.rendererFormat?.sampleMimeType
            val decodingFailed = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED
            val p = _player
            if (p != null && VideoSizeCap.shouldRetryWithoutVp9(decodingFailed, failedMime, _vp9Retried)) {
                _vp9Retried = true
                Log.w(TAG, "vp9 decode failed; retrying with h264/hevc preferred")
                val pos = p.currentPosition
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setPreferredVideoMimeTypes(androidx.media3.common.MimeTypes.VIDEO_H264, androidx.media3.common.MimeTypes.VIDEO_H265)
                    .build()
                p.prepare()
                p.seekTo(pos)
                p.play()
                return
            }
```
Use the player field name the class actually holds (`grep -n "private var .*ExoPlayer" renderer/AirPlayVideoPlayer.kt`); if it differs from `_player`, use that name and record it in the ledger.

Run: `./gw :app:testDebugUnitTest :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/io/github/jqssun/airplay/renderer/VideoSizeCap.kt app/src/test/kotlin/io/github/jqssun/airplay/renderer/VideoSizeCapTest.kt app/src/main/kotlin/io/github/jqssun/airplay/renderer/AirPlayVideoPlayer.kt
git commit -m "fix: cap video to the decoder's landscape limit; retry without VP9 on decode failure

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Stable pairing PIN across restarts

**Files:**
- Create: `app/src/main/kotlin/io/github/jqssun/airplay/service/PersistentPin.kt`
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/Prefs.kt` (add `PAIRING_PIN`)
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/bridge/NativeBridge.kt` (`nativeInit(..., pin: Int)`)
- Modify: `app/src/main/cpp/native_bridge.cpp` (`nativeInit` takes `jint pin`; use it when `0 <= pin <= 9999`, otherwise keep `random_pin()`)
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/service/AirPlayService.kt` (the `NativeBridge.nativeInit(` call)
- Test: `app/src/test/kotlin/io/github/jqssun/airplay/service/PersistentPinTest.kt`

**Interfaces:**
- Produces: `object PersistentPin { data class Result(val pin: Int, val generated: Boolean); fun resolve(stored: Int, random: () -> Int): Result; fun format(pin: Int): String }`, and `Prefs.PAIRING_PIN = "pairing_pin"`, `Prefs.DEF_PAIRING_PIN = -1`.

- [ ] **Step 1: Failing tests**

```kotlin
package io.github.jqssun.airplay.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistentPinTest {
    @Test fun storedPinIsReused() =
        assertEquals(PersistentPin.Result(4821, generated = false), PersistentPin.resolve(4821) { 1111 })

    @Test fun missingPinIsGeneratedOnce() =
        assertEquals(PersistentPin.Result(1111, generated = true), PersistentPin.resolve(-1) { 1111 })

    @Test fun outOfRangeStoredPinIsReplaced() =
        assertEquals(PersistentPin.Result(2222, generated = true), PersistentPin.resolve(123456) { 2222 })

    @Test fun zeroIsAValidPin() =
        assertEquals(PersistentPin.Result(0, generated = false), PersistentPin.resolve(0) { 9 })

    @Test fun formatKeepsLeadingZeros() {
        assertEquals("0042", PersistentPin.format(42))
        assertEquals("0000", PersistentPin.format(0))
    }
}
```

- [ ] **Step 2:** Run `./gw :app:testDebugUnitTest --tests 'io.github.jqssun.airplay.service.PersistentPinTest'`. Expected: compilation FAILS.

- [ ] **Step 3: Implement**

```kotlin
package io.github.jqssun.airplay.service

/** One pairing PIN per install: iPhones cache the code, so a new PIN per server run locks them out silently. */
object PersistentPin {
    data class Result(val pin: Int, val generated: Boolean)

    fun resolve(stored: Int, random: () -> Int): Result =
        if (stored in 0..9999) Result(stored, generated = false) else Result(random(), generated = true)

    fun format(pin: Int): String = "%04d".format(pin)
}
```
In `Prefs.kt` add: `const val PAIRING_PIN = "pairing_pin"; const val DEF_PAIRING_PIN = -1`.

- [ ] **Step 4:** Rerun the Step 2 command. Expected: 5 tests pass.

- [ ] **Step 5: Pass the PIN to native code**

`NativeBridge.kt`: add the parameter `pin: Int` after `requirePin: Boolean` in `nativeInit`.

`native_bridge.cpp`: change the signature to end with `jboolean nohold, jboolean requirePin, jint pinArg)` and replace:
```cpp
        int pin = random_pin();
        if (pin < 0) {
```
with:
```cpp
        int pin = (pinArg >= 0 && pinArg <= 9999) ? (int) pinArg : random_pin();
        if (pin < 0) {
```
`AirPlayService`, before the `NativeBridge.nativeInit(` call:
```kotlin
        val pinResult = PersistentPin.resolve(prefs.getInt(Prefs.PAIRING_PIN, Prefs.DEF_PAIRING_PIN)) {
            java.security.SecureRandom().nextInt(10_000)
        }
        if (pinResult.generated) prefs.edit().putInt(Prefs.PAIRING_PIN, pinResult.pin).apply()
```
and pass `pinResult.pin` as the new last argument.

Run: `./gw :app:testDebugUnitTest :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/io/github/jqssun/airplay/service/PersistentPin.kt app/src/test/kotlin/io/github/jqssun/airplay/service/PersistentPinTest.kt app/src/main/kotlin/io/github/jqssun/airplay/Prefs.kt app/src/main/kotlin/io/github/jqssun/airplay/bridge/NativeBridge.kt app/src/main/cpp/native_bridge.cpp app/src/main/kotlin/io/github/jqssun/airplay/service/AirPlayService.kt
git commit -m "fix: keep the pairing PIN across receiver restarts

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Debug-only RTSP request logging (UxPlay patch 0009)

**Files:**
- Create: `app/src/main/cpp/patches/UxPlay/0009-log-rtsp-requests.patch` (generated)
- Temporarily edited: `app/src/main/cpp/third_party/UxPlay/lib/raop.c`

**Interfaces:**
- Produces logcat lines (tag `AirPlayNative`): `AIRCAP RTSP <METHOD> <url> handled=<0|1> ct=<Content-Type or ->`.

- [ ] **Step 1:** `./gw :app:applyUxplayPatches -q && git -C app/src/main/cpp/third_party/UxPlay add -A` (stage 0001–0008 as the baseline).
- [ ] **Step 2:** In `raop.c`, inside the existing `#ifndef NDEBUG` AIRCAP block (from patch 0007, just above `if (handler != NULL) {`), add after the `HTTP/1.1` branch:
```c
    if (!strcmp(protocol, "RTSP/1.0") && strcmp(method, "GET_PARAMETER") && strcmp(method, "SET_PARAMETER")) {
        const char *cap_ct = http_request_get_header(request, "Content-Type");
        logger_log(raop->logger, LOGGER_ERR, "AIRCAP RTSP %s %s handled=%d ct=%s",
                   method, url, handler != NULL, cap_ct ? cap_ct : "-");
    }
```
- [ ] **Step 3:** Generate `0009-log-rtsp-requests.patch` with `git -C <U> diff`, reset the submodule, re-apply from clean, and check `grep -c "AIRCAP RTSP" <U>/lib/raop.c` = 1.
- [ ] **Step 4:** Release check: `assembleRelease`, then `strings` on the arm64 `.so` and dex → `AIRCAP` count is 0.
- [ ] **Step 5:** Commit:
```bash
git add app/src/main/cpp/patches/UxPlay/0009-log-rtsp-requests.patch
git commit -m "feat: debug-only RTSP request logging (patch 0009)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Device session (hands-on with the user)

**Files:**
- Create: `docs/findings/2026-10-plan3b-session.md`, `docs/findings/captures/safari-app-rtsp.txt` (redacted)

Install the Task 1–3 build while the user is **not** casting, relaunch Sideplay, start a logcat recording, then:

- [ ] **Step 1: Drive portrait.** The user AirPlays the same Google Drive video. Expected: picture and sound; `AIRCAP TRACK` selected tracks ≤ 4096×2304; no `Decoder failed`. Also a YouTube **Short** (portrait).
- [ ] **Step 2: Stable PIN.** Turn PIN on, pair from the iPhone and note the code. Then restart the receiver (Settings → toggle H.265 twice, which restarts the server), and reconnect from the iPhone. Expected: the same code shown, and the iPhone connects (no `client proof not validated`). Turn PIN off afterwards.
- [ ] **Step 3: Safari app.** Screen Mirroring off; the user plays the test MP4 in the **Safari app** → AirPlay icon → Living Room TV (AirPlay). Save every `AIRCAP RTSP`/`AIRCAP REQ` line from connect to disconnect to `captures/safari-app-rtsp.txt` (redacted). Also capture `dns-sd -L` TXT for Sideplay and AirReceiver side by side.
- [ ] **Step 4: Findings and commit.** Write the doc with results, and for Safari a "Root cause hypothesis and smallest fix" paragraph (the Plan 3c input if a fix is needed). Restore the TV state (PIN off, overlay allow).

```bash
git add docs/findings/2026-10-plan3b-session.md docs/findings/captures/safari-app-rtsp.txt
git commit -m "docs: Plan 3b device session (portrait video, stable PIN, Safari app RTSP)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

## Done when

- Unit tests pass (+7 VideoSizeCap, +5 PersistentPin).
- On the Hisense, the Drive video and a YouTube Short play with picture; a paired iPhone reconnects after a receiver restart with PIN on.
- The Safari app's request sequence is captured, with a written hypothesis.
