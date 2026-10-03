# Plan 1 — Foundation: Fork, Rebrand, Deploy, Capture

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Get a rebranded build of `jqssun/android-airplay-server` running on the Hisense "Living Room TV". Answer the background-launch question (spec section 6). Capture the real AirPlay video requests from each source in the spec's matrix (spec section 5.2, steps 1–2).

**Architecture:** Merge jqssun's git history into this repo so future upstream merges work. Change only the `applicationId` (keep the Kotlin package `io.github.jqssun.airplay` so upstream diffs stay small). All C changes go in as patch files under `app/src/main/cpp/patches/UxPlay/`, using jqssun's existing `applyUxplayPatches` Gradle task. Request capture goes through logcat on debug builds and is collected over ADB.

**Tech Stack:** Kotlin 2.1, AGP 8.9.2, Gradle 8.11.1 (requires JDK 17), NDK 27.0.12077973, CMake 3.22.1, UxPlay (C), JUnit 4, ADB over Wi-Fi.

**Spec:** `docs/superpowers/specs/2026-10-03-airplay-tv-receiver-design.md`

**Later plans (not this one):** Plan 2: TV UI (spec section 6). Plan 3: AirPlay video fixes (spec section 5.1, written from this plan's capture findings). Plan 4: Release (spec section 8).

## Global Constraints

- License GPL-3.0; whole app open source; no closed-source SDKs.
- "AirPlay" appears only descriptively ("for AirPlay"); the app's own brand is the working name **Sideplay**, applicationId `io.github.sudnep.sideplay`.
- Engine submodules (`app/src/main/cpp/third_party/*`) are never committed modified. All C changes are `.patch` files in `app/src/main/cpp/patches/UxPlay/`.
- Kotlin package / Android `namespace` stays `io.github.jqssun.airplay` (eases upstream merges).
- Primary device: Hisense Google TV `SmartTV 4K FFM`, 192.168.1.248. Secondary: Sony BRAVIA XBR-55X800G, 192.168.1.160.
- Upstream base pinned to jqssun commit `c8defdd70d7e6a04f4f1b71d353653682d594106` (v0.0.31, 2026-08-23).
- All Gradle commands run through `./gw` (Task 1), which pins JDK 17 and the SDK path.
- Request-capture logging must not exist in release builds.

## Review Focus

1. **Long, emoji, or blank TV names.** The receiver name must stay a valid mDNS label. UxPlay advertises RAOP as `<12-hex-MAC>@<name>`, and labels are capped at 63 bytes, so the name must be ≤ 50 UTF-8 bytes without splitting a multi-byte character, and never empty. Tested in Task 2.
2. **Model already contains the manufacturer.** For example, manufacturer "Hisense" with model "Hisense 55U8" must give "Hisense 55U8", not "Hisense Hisense 55U8". Tested in Task 2.
3. **Capture logs reaching release builds.** A release `.so` must contain no `AIRCAP` string. Checked in Task 4.
4. **Patches on a clean checkout.** A fresh clone with clean submodules must build: the `0007` patch has to apply after `0001`–`0006`. Checked in Task 4.
5. **Personal tokens in committed captures.** Video URLs often carry signed query strings. Query strings must be redacted before any capture is committed. Enforced in Task 5.

---

### Task 1: Toolchain, upstream import, first build

**Files:**
- Create: `gw` (Gradle wrapper launcher)
- Create: `local.properties` (gitignored by upstream's `.gitignore`)
- Modify: `.gitignore` (merge with upstream's)
- Import: entire upstream tree via git merge

**Interfaces:**
- Produces: `./gw <gradle args>` used by every later task; the debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 1: Add the upstream remote and fetch**

```bash
cd /Users/neupane/repos/phone-mirror
git remote add upstream https://github.com/jqssun/android-airplay-server.git
git fetch upstream
git cat-file -t c8defdd70d7e6a04f4f1b71d353653682d594106
```
Expected: last command prints `commit`.

- [ ] **Step 2: Merge upstream history at the pinned commit**

Our only tracked files are `.gitignore` (contains `.omc/`) and `docs/`. Upstream has its own `.gitignore`, so expect a conflict on it.

```bash
git merge --allow-unrelated-histories --no-commit c8defdd70d7e6a04f4f1b71d353653682d594106 || true
git status --short | head
```
If `.gitignore` is conflicted, replace it with this exact content:

```gitignore
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
/build/
/app/build/
/captures
.externalNativeBuild/
.cxx/

*.jks
.omc/
```
Then:
```bash
git add .gitignore
git commit -m "Merge jqssun/android-airplay-server v0.0.31 (c8defdd) as upstream base

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Initialize submodules**

```bash
git submodule update --init --recursive --depth 1
git submodule status
```
Expected: four lines (UxPlay, libplist, openssl-cmake, ffmpeg), none prefixed with `-`.

- [ ] **Step 4: Create the `gw` launcher**

Gradle 8.11.1 cannot run on the system's default JDK 25. Homebrew `openjdk@17` is installed.

Create `gw`:
```bash
#!/usr/bin/env bash
# Runs ./gradlew with the JDK and SDK this project needs.
set -euo pipefail
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
cd "$(dirname "$0")"
exec ./gradlew "$@"
```
```bash
chmod +x gw
printf 'sdk.dir=%s\n' "$HOME/Library/Android/sdk" > local.properties
./gw --version
```
Expected: output includes `Gradle 8.11.1` and `Launcher JVM:  17.`

- [ ] **Step 5: Install NDK and CMake if missing**

```bash
ls ~/Library/Android/sdk/ndk/27.0.12077973 ~/Library/Android/sdk/cmake/3.22.1 2>&1
```
If either is missing:
```bash
brew install --cask android-commandlinetools
yes | sdkmanager --sdk_root="$HOME/Library/Android/sdk" "ndk;27.0.12077973" "cmake;3.22.1"
```
Expected: both directories exist afterward.

- [ ] **Step 6: Build the debug APK**

Run in the background; the first build compiles OpenSSL and FFmpeg and can take 15+ minutes.

```bash
./gw :app:assembleDebug 2>&1 | tail -20
ls -la app/build/outputs/apk/debug/app-debug.apk
```
Expected: `BUILD SUCCESSFUL`, and the APK exists.

- [ ] **Step 7: Commit**

```bash
git add gw
git commit -m "build: add gw launcher pinning JDK 17 and Android SDK

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Rebrand, default receiver name, unit-test setup, README

**Files:**
- Modify: `app/build.gradle.kts` (`applicationId`, `versionCode`, `versionName`, `buildConfig`, test dependency)
- Modify: `gradle/libs.versions.toml` (add junit)
- Modify: `settings.gradle.kts` (`rootProject.name`)
- Modify: `app/src/main/res/values/strings.xml` (`app_name`, `notification_title`)
- Create: `app/src/main/kotlin/io/github/jqssun/airplay/ServerName.kt`
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/service/AirPlayService.kt:335,348`
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/viewmodel/MainViewModel.kt:107`
- Test: `app/src/test/kotlin/io/github/jqssun/airplay/ServerNameTest.kt`
- Modify: `README.md` (full replacement)

**Interfaces:**
- Consumes: `./gw` (Task 1).
- Produces: `ServerName.choose(deviceName: String?, manufacturer: String?, model: String?): String`, `ServerName.default(context: Context): String`, `ServerName.MAX_BYTES = 50`, `ServerName.FALLBACK = "Sideplay"`. The `BuildConfig.DEBUG` flag is used by Task 4. The installed package is `io.github.sudnep.sideplay`.

- [ ] **Step 1: Add JUnit and turn on BuildConfig**

In `gradle/libs.versions.toml`, under `[versions]` add:
```toml
junit = "4.13.2"
```
Under `[libraries]` add:
```toml
junit = { group = "junit", name = "junit", version.ref = "junit" }
```
In `app/build.gradle.kts`, change `buildFeatures` to:
```kotlin
    buildFeatures {
        compose = true
        prefab = true
        buildConfig = true
    }
```
At the end of the `dependencies { }` block add:
```kotlin
    testImplementation(libs.junit)
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/kotlin/io/github/jqssun/airplay/ServerNameTest.kt`:
```kotlin
package io.github.jqssun.airplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerNameTest {
    @Test fun usesDeviceNameWhenSet() =
        assertEquals("Living Room TV", ServerName.choose("Living Room TV", "Hisense", "SmartTV 4K FFM"))

    @Test fun trimsDeviceName() =
        assertEquals("Living Room TV", ServerName.choose("  Living Room TV \n", "Hisense", "X"))

    @Test fun blankDeviceNameFallsBackToManufacturerAndModel() =
        assertEquals("Hisense SmartTV 4K FFM", ServerName.choose("   ", "Hisense", "SmartTV 4K FFM"))

    @Test fun modelAlreadyContainingManufacturerIsNotDuplicated() =
        assertEquals("Hisense 55U8", ServerName.choose(null, "hisense", "Hisense 55U8"))

    @Test fun nothingKnownGivesFallback() =
        assertEquals(ServerName.FALLBACK, ServerName.choose(null, " ", null))

    @Test fun longAsciiNameIsCappedAtMaxBytes() {
        val name = ServerName.choose("A".repeat(80), null, null)
        assertEquals(ServerName.MAX_BYTES, name.toByteArray(Charsets.UTF_8).size)
    }

    @Test fun truncationNeverSplitsMultiByteCharacters() {
        // each "📺" is 4 UTF-8 bytes (a surrogate pair in Kotlin); 20 of them = 80 bytes
        val name = ServerName.choose("📺".repeat(20), null, null)
        val bytes = name.toByteArray(Charsets.UTF_8)
        assertTrue(bytes.size <= ServerName.MAX_BYTES)
        assertEquals("📺".repeat(12), name) // 48 bytes; a 13th would be 52
    }

    @Test fun truncationDropsTrailingSpace() =
        assertEquals("A".repeat(49), ServerName.choose("A".repeat(49) + " " + "B".repeat(10), null, null))
}
```

- [ ] **Step 3: Run the test and confirm it fails**

Run: `./gw :app:testDebugUnitTest --tests 'io.github.jqssun.airplay.ServerNameTest'`
Expected: compilation FAILS with `Unresolved reference 'ServerName'`.

- [ ] **Step 4: Implement `ServerName`**

Create `app/src/main/kotlin/io/github/jqssun/airplay/ServerName.kt`:
```kotlin
package io.github.jqssun.airplay

import android.content.Context
import android.os.Build
import android.provider.Settings

/** Default receiver name: the TV's own name, so the iPhone shows e.g. "Living Room TV". */
object ServerName {
    const val FALLBACK = "Sideplay"

    // RAOP is advertised as "<12 hex MAC>@<name>"; mDNS labels max out at 63 bytes
    const val MAX_BYTES = 50

    fun choose(deviceName: String?, manufacturer: String?, model: String?): String {
        val device = deviceName?.trim().orEmpty()
        if (device.isNotEmpty()) return truncate(device)
        val mfr = manufacturer?.trim().orEmpty()
        val mdl = model?.trim().orEmpty()
        val hw = when {
            mdl.isEmpty() -> mfr
            mfr.isEmpty() || mdl.startsWith(mfr, ignoreCase = true) -> mdl
            else -> "$mfr $mdl"
        }
        return if (hw.isEmpty()) FALLBACK else truncate(hw)
    }

    fun default(context: Context): String {
        val deviceName = if (Build.VERSION.SDK_INT >= 25)
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) else null
        return choose(deviceName, Build.MANUFACTURER, Build.MODEL)
    }

    private fun truncate(name: String): String {
        if (name.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) return name
        val out = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < name.length) {
            val cp = name.codePointAt(i)
            val chunk = String(Character.toChars(cp))
            val n = chunk.toByteArray(Charsets.UTF_8).size
            if (bytes + n > MAX_BYTES) break
            out.append(chunk)
            bytes += n
            i += Character.charCount(cp)
        }
        return out.toString().trimEnd()
    }
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `./gw :app:testDebugUnitTest --tests 'io.github.jqssun.airplay.ServerNameTest'`
Expected: `BUILD SUCCESSFUL`, 8 tests passed.

- [ ] **Step 6: Use `ServerName.default` wherever the name falls back**

In `app/src/main/kotlin/io/github/jqssun/airplay/service/AirPlayService.kt`, replace line 335:
```kotlin
            val name = prefs.getString(Prefs.SERVER_NAME, Prefs.DEF_SERVER_NAME) ?: Prefs.DEF_SERVER_NAME
```
with:
```kotlin
            val name = prefs.getString(Prefs.SERVER_NAME, null) ?: ServerName.default(this)
```
and replace line 348:
```kotlin
        val effectiveName = name.ifBlank { Prefs.DEF_SERVER_NAME }
```
with:
```kotlin
        val effectiveName = name.ifBlank { ServerName.default(this) }
```
Add `import io.github.jqssun.airplay.ServerName` if the file's imports don't already cover the package.

In `app/src/main/kotlin/io/github/jqssun/airplay/viewmodel/MainViewModel.kt`, replace line 107:
```kotlin
    private val _serverName = MutableStateFlow(prefs.getString(Prefs.SERVER_NAME, Prefs.DEF_SERVER_NAME)!!)
```
with:
```kotlin
    private val _serverName = MutableStateFlow(prefs.getString(Prefs.SERVER_NAME, null) ?: ServerName.default(app))
```
and add `import io.github.jqssun.airplay.ServerName`.

Check that nothing else falls back to the old constant:
```bash
grep -rn "DEF_SERVER_NAME" app/src/main/kotlin
```
Expected: only the definition in `Prefs.kt`, which stays so upstream merges don't conflict.

- [ ] **Step 7: Rebrand the build identity and strings**

In `app/build.gradle.kts`, in `defaultConfig`:
```kotlin
        applicationId = "io.github.sudnep.sideplay"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
```
Leave `namespace = "io.github.jqssun.airplay"` unchanged.

In `settings.gradle.kts`: `rootProject.name = "Sideplay"`

In `app/src/main/res/values/strings.xml`:
```xml
    <string name="app_name">Sideplay</string>
```
```xml
    <string name="notification_title">Sideplay is running</string>
```
Check the other resource folders don't override these:
```bash
grep -rn "AirPlay Server" app/src/main/res
```
Expected: no output.

- [ ] **Step 8: Replace README.md**

```markdown
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
```

- [ ] **Step 9: Build, run all unit tests, commit**

```bash
./gw :app:testDebugUnitTest :app:assembleDebug 2>&1 | tail -5
"$HOME/Library/Android/sdk/build-tools/36.0.0/aapt2" dump packagename app/build/outputs/apk/debug/app-debug.apk
```
Expected: `BUILD SUCCESSFUL`; package prints `io.github.sudnep.sideplay`.

```bash
git add -A app/build.gradle.kts gradle/libs.versions.toml settings.gradle.kts app/src/main/res/values/strings.xml \
  app/src/main/kotlin/io/github/jqssun/airplay/ServerName.kt app/src/test \
  app/src/main/kotlin/io/github/jqssun/airplay/service/AirPlayService.kt \
  app/src/main/kotlin/io/github/jqssun/airplay/viewmodel/MainViewModel.kt README.md
git commit -m "feat: rebrand as Sideplay and default receiver name to the TV's device name

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Deploy to the Hisense; baseline and background-launch check (hands-on with the user)

This task needs the user at the TV with an iPhone. The agent runs the commands and writes down what the user reports.

**Files:**
- Create: `docs/findings/2026-10-hisense-baseline.md`

**Interfaces:**
- Consumes: debug APK with package `io.github.sudnep.sideplay` (Task 2).
- Produces: a working ADB connection to `192.168.1.248:5555`; the findings doc that Plan 2 reads to decide the background-receiving design.

- [ ] **Step 1: User enables debugging on the Hisense**

Ask the user to: Settings → System → About → click **Android TV OS build** 7 times; then Settings → System → Developer options → turn on **USB debugging** and **Network debugging** (on some Hisense builds it's labelled "Wireless debugging" / "ADB over network").

- [ ] **Step 2: Connect and install**

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb connect 192.168.1.248:5555
adb -s 192.168.1.248:5555 shell getprop ro.product.model
adb -s 192.168.1.248:5555 shell getprop ro.build.version.release
adb -s 192.168.1.248:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: the first `connect` may say `failed to authenticate`. The TV then shows "Allow USB debugging?", which the user accepts with "Always allow"; re-run `connect`. The model and Android version print, and the install ends with `Success`.

- [ ] **Step 3: Baseline mirroring**

Launch the app:
```bash
adb -s 192.168.1.248:5555 shell monkey -p io.github.sudnep.sideplay -c android.intent.category.LEANBACK_LAUNCHER 1
```
Ask the user to mirror from the iPhone (Control Center → Screen Mirroring → the TV's name). Record: the name shown on the iPhone (it should be the TV's device name, from Task 2), time to first frame, picture smoothness over 2 minutes, audio, and what happens on rotation.

- [ ] **Step 4: Background-launch check, default permissions**

```bash
adb -s 192.168.1.248:5555 shell appops get io.github.sudnep.sideplay SYSTEM_ALERT_WINDOW
adb -s 192.168.1.248:5555 logcat -c
```
The user presses **Home** on the remote, then starts mirroring from the iPhone.
```bash
adb -s 192.168.1.248:5555 logcat -d | grep -iE "background activity start|BAL|Abort|io.github.sudnep.sideplay" | tail -20
```
Record whether the app came to the front, plus the relevant log lines.

- [ ] **Step 5: Background-launch check, overlay permission granted**

```bash
adb -s 192.168.1.248:5555 shell appops set io.github.sudnep.sideplay SYSTEM_ALERT_WINDOW allow
adb -s 192.168.1.248:5555 shell am force-stop io.github.sudnep.sideplay
adb -s 192.168.1.248:5555 shell monkey -p io.github.sudnep.sideplay -c android.intent.category.LEANBACK_LAUNCHER 1
```
The user disconnects the iPhone, presses Home, and starts mirroring again. Record whether the app came to the front.

Then ask the user to look for the setting in the TV's own menus: Settings → Apps → Special app access → **Display over other apps**. Record whether it exists and whether Sideplay is listed.

- [ ] **Step 6: Repeat Steps 2–5 on the Sony (192.168.1.160)**, if the user has turned on debugging there. If not, write "not run" for the Sony.

- [ ] **Step 7: Write the findings doc**

Create `docs/findings/2026-10-hisense-baseline.md` with this structure, filled in with the recorded results:
```markdown
# Baseline on Hisense (and Sony)

Date: <date run>  ·  App: Sideplay 0.1.0 (upstream c8defdd)  ·  iPhone: <model>, iOS <version>

## Devices
| TV | Model prop | Android | Result |
|---|---|---|---|
| Hisense | <ro.product.model> | <version> | |
| Sony | ... | ... | |

## Mirroring baseline
| Check | Hisense | Sony |
|---|---|---|
| Name shown on iPhone | | |
| Time to first frame | | |
| Smooth for 2 min | | |
| Audio | | |
| Rotation | | |

## Background launch
| Condition | Hisense | Sony |
|---|---|---|
| Default permissions: app came to front? | | |
| Overlay granted via appops: app came to front? | | |
| "Display over other apps" visible in TV settings? | | |

Relevant log lines:
    <paste>

## Decision for Plan 2
<one of: "overlay works and user-grantable → request it in first-run" / "overlay works only via adb → app must be open; idle screen says so" / "neither works → app must be open">
```

- [ ] **Step 8: Commit**

```bash
git add docs/findings/2026-10-hisense-baseline.md
git commit -m "docs: record Hisense baseline and background-launch findings

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Request-capture logging (UxPlay patch 0007 and player errors)

**Files:**
- Create: `app/src/main/cpp/patches/UxPlay/0007-log-airplay-http-requests.patch` (generated)
- Temporarily edited, never committed: `app/src/main/cpp/third_party/UxPlay/lib/raop.c`, `.../lib/http_handlers.h`
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/renderer/AirPlayVideoPlayer.kt:50-53`
- Create: `scripts/capture.sh`

**Interfaces:**
- Consumes: `BuildConfig.DEBUG` (Task 2), ADB connection (Task 3).
- Produces: logcat lines in this exact format, which Task 5 and Plan 3 parse:
  - `AIRCAP REQ <METHOD> <url> handled=<0|1> ua=<User-Agent or -> ct=<Content-Type or ->` (tag `AirPlayNative`)
  - `AIRCAP UNHANDLED_HEADERS <headers joined with " | ">` (tag `AirPlayNative`, only when handled=0)
  - `AIRCAP PLAY proc=<clientProcName or -> start=<seconds> location=<Content-Location>` (tag `AirPlayNative`)
  - `AIRCAP PLAYER_ERROR code=<errorCodeName> msg=<message> cause=<cause>` (tag `AirPlayVideoPlayer`)
  - `scripts/capture.sh <tv-ip> <outfile>`

- [ ] **Step 1: Apply existing patches and stage them as the baseline**

```bash
./gw :app:applyUxplayPatches
U=app/src/main/cpp/third_party/UxPlay
git -C $U add -A
git -C $U status --short
```
Expected: the files touched by patches 0001–0006 are listed as staged (`M `).

- [ ] **Step 2: Add request logging in `raop.c`**

In `app/src/main/cpp/third_party/UxPlay/lib/raop.c`, find this line (around line 480):
```c
    if (handler != NULL) {
        handler(conn, request, *response, &response_data, &response_datalen);
```
Insert directly above it:
```c
#ifndef NDEBUG
    /* AIRCAP: request capture for receiver development (debug builds only) */
    if (!strcmp(protocol, "HTTP/1.1") && strcmp(url, "/playback-info")) {
        const char *cap_ua = http_request_get_header(request, "User-Agent");
        const char *cap_ct = http_request_get_header(request, "Content-Type");
        logger_log(raop->logger, LOGGER_ERR, "AIRCAP REQ %s %s handled=%d ua=%s ct=%s",
                   method, url, handler != NULL, cap_ua ? cap_ua : "-", cap_ct ? cap_ct : "-");
        if (handler == NULL) {
            char *cap_hdr = NULL;
            http_request_get_header_string(request, &cap_hdr);
            if (cap_hdr) {
                for (char *p = cap_hdr; *p; p++) {
                    if (*p == '\r' || *p == '\n') *p = ' ';
                }
                logger_log(raop->logger, LOGGER_ERR, "AIRCAP UNHANDLED_HEADERS %s", cap_hdr);
                free(cap_hdr);
            }
        }
    }
#endif
```
(`/playback-info` is skipped because senders poll it every second.)

- [ ] **Step 3: Add `/play` logging in `http_handlers.h`**

In `app/src/main/cpp/third_party/UxPlay/lib/http_handlers.h`, find:
```c
    set_start_position_seconds(airplay_video, (float) start_position_seconds);
```
Insert directly below it:
```c
#ifndef NDEBUG
    {
        char *cap_proc = NULL;
        plist_t cap_proc_node = plist_dict_get_item(req_root_node, "clientProcName");
        if (cap_proc_node) {
            plist_get_string_val(cap_proc_node, &cap_proc);
        }
        logger_log(raop->logger, LOGGER_ERR, "AIRCAP PLAY proc=%s start=%.1f location=%s",
                   cap_proc ? cap_proc : "-", start_position_seconds, playback_location);
        if (cap_proc) {
            plist_mem_free(cap_proc);
        }
    }
#endif
```

- [ ] **Step 4: Generate the patch and restore the submodule**

```bash
U=app/src/main/cpp/third_party/UxPlay
git -C $U diff > app/src/main/cpp/patches/UxPlay/0007-log-airplay-http-requests.patch
git -C $U reset -q
grep -c "AIRCAP" app/src/main/cpp/patches/UxPlay/0007-log-airplay-http-requests.patch
```
Expected: a count of `4` (the comment, REQ and UNHANDLED_HEADERS lines in raop.c, and the PLAY line in http_handlers.h). `git -C $U diff` (unstaged only) captured just our edits, because 0001–0006 were staged in Step 1.

- [ ] **Step 5: Check all patches apply from a clean submodule (Review Focus 4)**

```bash
U=app/src/main/cpp/third_party/UxPlay
git -C $U checkout -- . && git -C $U status --short
./gw :app:applyUxplayPatches
git -C $U diff --stat
```
Expected: the first status prints nothing (clean). After applying, the diff stat lists `lib/raop.c` and `lib/http_handlers.h` among the changed files, with no errors.

- [ ] **Step 6: Log player errors in Kotlin**

In `app/src/main/kotlin/io/github/jqssun/airplay/renderer/AirPlayVideoPlayer.kt`, replace:
```kotlin
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.w(TAG, "playback error", error)
            onEnded?.invoke()
        }
```
with:
```kotlin
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.w(TAG, "playback error", error)
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "AIRCAP PLAYER_ERROR code=${error.errorCodeName} msg=${error.message} cause=${error.cause}")
            }
            onEnded?.invoke()
        }
```
Add `import io.github.jqssun.airplay.BuildConfig` to the imports.

- [ ] **Step 7: Create `scripts/capture.sh`**

```bash
#!/usr/bin/env bash
# Streams AirPlay request-capture lines from a TV running a debug build.
# Usage: scripts/capture.sh <tv-ip> <outfile>
set -euo pipefail
TV="$1:5555"
OUT="$2"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" connect "$TV" >/dev/null
"$ADB" -s "$TV" logcat -c
"$ADB" -s "$TV" logcat -v time AirPlayNative:V AirPlayVideoPlayer:V AirPlayService:I '*:S' \
  | grep --line-buffered -E 'AIRCAP|AirPlay Video' | tee "$OUT"
```
```bash
chmod +x scripts/capture.sh
```

- [ ] **Step 8: Verify on the Hisense**

```bash
./gw :app:assembleDebug 2>&1 | tail -3
~/Library/Android/sdk/platform-tools/adb -s 192.168.1.248:5555 install -r app/build/outputs/apk/debug/app-debug.apk
scripts/capture.sh 192.168.1.248 /tmp/aircap-smoke.txt
```
(Run `capture.sh` in the background, or stop it with Ctrl-C once the lines appear.) The user casts any YouTube video to the TV from the iPhone YouTube app.
Expected in the output: an `AIRCAP REQ POST /play handled=1 ...` line, an `AIRCAP PLAY proc=YouTube ...` line, and the `AirPlay Video play:` line from AirPlayService.

- [ ] **Step 9: Check release builds don't contain capture code (Review Focus 3)**

```bash
./gw :app:assembleRelease 2>&1 | tail -3
APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
mkdir -p /tmp/relcheck && rm -rf /tmp/relcheck/* && unzip -q -o "$APK" 'lib/arm64-v8a/*.so' -d /tmp/relcheck
strings /tmp/relcheck/lib/arm64-v8a/*.so | grep -c AIRCAP || true
```
Expected: `0`. (The release APK is unsigned without a keystore; that's fine for this check. In release, the Kotlin `AIRCAP` string is removed by R8 because `BuildConfig.DEBUG` is the constant `false`.)

- [ ] **Step 10: Commit**

Commit only the patch, Kotlin and script. The submodule stays at its pinned commit; its working-tree changes come from the patch task and must not be staged.
```bash
git add app/src/main/cpp/patches/UxPlay/0007-log-airplay-http-requests.patch \
  app/src/main/kotlin/io/github/jqssun/airplay/renderer/AirPlayVideoPlayer.kt scripts/capture.sh
git status --short | grep third_party || true
git commit -m "feat: debug-only AirPlay request capture (UxPlay patch 0007, player errors)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```
Expected: the `grep third_party` line may show ` m app/src/main/cpp/third_party/UxPlay` (modified content, unstaged). That's expected and must not be committed.

---

### Task 5: Run the video capture matrix (hands-on with the user)

**Files:**
- Create: `docs/findings/2026-10-video-capture-matrix.md`
- Create: `docs/findings/captures/<source>.txt` (redacted)

**Interfaces:**
- Consumes: `scripts/capture.sh` and the AIRCAP line formats (Task 4).
- Produces: the matrix doc that Plan 3 is written from. For each source it records the request type (spec 5.1: **A** direct URL, **B** FCUP HLS, **C** Photos/other), the result, and the evidence lines.

- [ ] **Step 1: For each source, capture one session**

Sources, in order (from spec 7.3; mirroring rows were covered in Task 3):
`safari-mp4`, `safari-hls`, `youtube`, `photos-picture`, `photos-video`, `vimeo`, `music`, `netflix`, `appletv`.

Suggested test pages for the user: Safari MP4 at `https://test-videos.co.uk` (any MP4 entry); Safari HLS at `https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8` opened directly in Safari.

For each `<source>`:
```bash
scripts/capture.sh 192.168.1.248 /tmp/aircap-<source>.txt
```
The user casts from that source, tries play → pause → seek → stop from the iPhone, and says what the TV did. Stop the capture.

- [ ] **Step 2: Redact query strings and tokens, then save (Review Focus 5)**

```bash
mkdir -p docs/findings/captures
for f in /tmp/aircap-*.txt; do
  name=$(basename "$f" .txt | sed 's/^aircap-//')
  [ "$name" = "smoke" ] && continue
  sed -E -e 's/\?[^ ]*/?<redacted>/g' -e 's/(X-Apple-Session-ID|X-Apple-Device-ID|DACP-ID|Active-Remote): [^|]*/\1: <redacted>/g' \
    "$f" > "docs/findings/captures/$name.txt"
done
grep -l -E '\?[^<]' docs/findings/captures/*.txt || echo "no unredacted query strings"
```
Expected: `no unredacted query strings`.

- [ ] **Step 3: Write the matrix doc**

Create `docs/findings/2026-10-video-capture-matrix.md` with this structure, filled in:
```markdown
# AirPlay video capture matrix — Hisense

Date: <date>  ·  Build: Sideplay 0.1.0 + patch 0007  ·  iPhone: <model>, iOS <version>

Type key (spec 5.1): A = direct http(s) URL → ExoPlayer · B = FCUP HLS (master.m3u8) · C = other (/photo, unhandled)

| Source | Type | Play | Pause | Seek | Stop | Key evidence (AIRCAP lines) | Player error |
|---|---|---|---|---|---|---|---|
| safari-mp4 | | | | | | | |
| safari-hls | | | | | | | |
| youtube | | | | | | | |
| photos-picture | | | | | | | |
| photos-video | | | | | | | |
| vimeo | | | | | | | |
| music | | | | | | | |
| netflix | | | | | | | |
| appletv | | | | | | | |

## Unhandled requests seen
<every distinct `AIRCAP REQ ... handled=0` method+url, with sources>

## Observations for Plan 3
<per failing source: which type, what broke (no /play? player error code? missing endpoint?), and the smallest fix suggested by the evidence>
```

- [ ] **Step 4: Commit**

```bash
git add docs/findings/2026-10-video-capture-matrix.md docs/findings/captures
git commit -m "docs: AirPlay video capture matrix on Hisense (input for Plan 3)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

## Done when

- `./gw :app:testDebugUnitTest :app:assembleDebug` passes on a fresh clone (with `--recurse-submodules`).
- Sideplay (`io.github.sudnep.sideplay`) is installed on the Hisense and mirroring works, as recorded in `docs/findings/2026-10-hisense-baseline.md`.
- The background-launch decision for Plan 2 is recorded.
- `docs/findings/2026-10-video-capture-matrix.md` has every row filled in, with redacted captures committed.
