# Plan 3: Reliability, auto-open, and video investigation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- Keep the receiver alive after Android kills it.
- Give Sideplay a name that can't be confused with the TV's Chromecast.
- Help users reach "Display over other apps" where the TV allows it, and prompt them to open Sideplay where it doesn't.
- Add the instrumentation needed to diagnose the Google Drive video failure and the Safari-mirrors-instead-of-casting behaviour, then capture both on the Hisense.

**Architecture:** Each fix is a small pure decision function with JVM tests (`StartPolicy`, `ServerName.withAirPlaySuffix`, `resolveOverlayAccess`, `ConnectLaunch`), wired into the existing service and TV UI. The investigation work is a debug-only UxPlay patch (0008) that logs the HLS playlists the receiver serves, plus a Kotlin log of the tracks ExoPlayer picks. A hands-on session then captures Drive and runs the Safari comparison against the Fire TV's AirReceiver. **The Drive and Safari fixes themselves are a follow-up (Plan 3b).** Their root causes are unknown until Task 6's captures exist, and this plan doesn't guess at the code.

**Tech Stack:** Kotlin, Compose, UxPlay C (patch layer), JUnit 4, Compose UI tests on the Hisense over ADB.

**Spec:** `docs/superpowers/specs/2026-10-03-airplay-tv-receiver-design.md` (section 5 "AirPlay video fixes", section 6 "Background receiving", section 3 working name)
**Evidence this plan is written from:** `docs/findings/2026-10-hisense-baseline.md`, `docs/findings/2026-10-video-capture-matrix.md`

## Global Constraints

- Branch: `plan-1-foundation` (continue). Gradle via `./gw`.
- Kotlin package `io.github.jqssun.airplay`. New C changes only as `app/src/main/cpp/patches/UxPlay/0008-*.patch`.
- Capture logging is debug-only: native code behind `#ifndef NDEBUG`, Kotlin behind `BuildConfig.DEBUG`. Release `.so` and dex must contain 0 `AIRCAP` strings.
- Receiver name ≤ 50 UTF-8 bytes, never empty (mDNS label rule from Plan 1).
- Primary device: Hisense `SmartTV 4K FFM`, Android 10, 192.168.1.248. Waking it remotely is allowed (user-approved 2026-10-04); always revert `svc power stayon false` and send `KEYCODE_SLEEP` afterwards. Never launch apps on the TV while it is awake unless you are in a device step, because the user may be watching.
- On the user's TV, keep `SYSTEM_ALERT_WINDOW` = `allow` (the user's choice) except where a step resets it for a test; restore it at the end of that step.
- Instrumented test command: `ANDROID_SERIAL=192.168.1.248:5555 ./gw :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`. Afterwards, relaunch Sideplay, because the test install stops the receiver.

## Review Focus

1. **Stopping the server on purpose must not restart it.** A phone-UI Stop or a settings restart calls `stopSelf()`; Android must not bring the receiver back. Tested in Task 1 (`explicitStopIsNotRestarted` covers the policy; the device check in Task 1 Step 6 covers `stopSelf`).
2. **A name that is already at 50 bytes plus the suffix**: the base must be trimmed so the whole name fits, without splitting a character, and the suffix must survive. Tested in Task 2.
3. **The prompt appears while Sideplay is already in front**: no toast or notification when the UI is visible. Tested in Task 4 (`visibleUiNeedsNothing`).
4. **The prompt notification stays after the user opens Sideplay or the iPhone disconnects**: it must be cancelled. Covered by Task 4 Step 5 wiring and the Task 6 device check.
5. **Playlist logging must not leak signed URLs** in debug logcat. The patch strips query strings itself. Tested by inspecting the Task 5 smoke output and the Task 6 captures.

---

### Task 1: Receiver restarts after Android kills it

**Files:**
- Create: `app/src/main/kotlin/io/github/jqssun/airplay/service/StartPolicy.kt`
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/service/AirPlayService.kt` (`onStartCommand`, around line 333)
- Test: `app/src/test/kotlin/io/github/jqssun/airplay/service/StartPolicyTest.kt`

**Interfaces:**
- Produces: `object StartPolicy { data class Decision(val startServer: Boolean, val sticky: Boolean); fun decide(action: String?, startAction: String, runInBackground: Boolean): Decision }`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.jqssun.airplay.service

import org.junit.Assert.assertEquals
import org.junit.Test

class StartPolicyTest {
    private val start = "io.github.jqssun.airplay.START_SERVER"

    @Test fun explicitStartRunsAndStaysAlive() =
        assertEquals(StartPolicy.Decision(startServer = true, sticky = true), StartPolicy.decide(start, start, runInBackground = true))

    // android restarts a killed sticky service with a null intent
    @Test fun systemRestartAfterKillStartsServer() =
        assertEquals(StartPolicy.Decision(startServer = true, sticky = true), StartPolicy.decide(null, start, runInBackground = true))

    @Test fun noBackgroundModeMeansNoRestart() {
        assertEquals(StartPolicy.Decision(startServer = true, sticky = false), StartPolicy.decide(start, start, runInBackground = false))
        assertEquals(StartPolicy.Decision(startServer = false, sticky = false), StartPolicy.decide(null, start, runInBackground = false))
    }

    @Test fun unknownActionDoesNothing() =
        assertEquals(StartPolicy.Decision(startServer = false, sticky = false), StartPolicy.decide("other", start, runInBackground = true))

    // stopServer() calls stopSelf(); a stopped service is never restarted regardless of the sticky flag,
    // so the policy only needs to avoid starting on non-start intents
    @Test fun explicitStopIsNotRestarted() =
        assertEquals(false, StartPolicy.decide("other", start, runInBackground = true).startServer)
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gw :app:testDebugUnitTest --tests 'io.github.jqssun.airplay.service.StartPolicyTest'`
Expected: compilation FAILS with `Unresolved reference 'StartPolicy'`.

- [ ] **Step 3: Implement**

```kotlin
package io.github.jqssun.airplay.service

/** What onStartCommand does. A null action is Android restarting a sticky service it killed. */
object StartPolicy {
    data class Decision(val startServer: Boolean, val sticky: Boolean)

    fun decide(action: String?, startAction: String, runInBackground: Boolean): Decision = when (action) {
        startAction -> Decision(startServer = true, sticky = runInBackground)
        null -> Decision(startServer = runInBackground, sticky = runInBackground)
        else -> Decision(startServer = false, sticky = false)
    }
}
```

- [ ] **Step 4: Run it and confirm it passes**

Run: same as Step 2. Expected: 5 tests passed.

- [ ] **Step 5: Wire it into `onStartCommand`**

Replace:
```kotlin
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SERVER) {
            promoteToForeground()
            val name = prefs.getString(Prefs.SERVER_NAME, null) ?: ServerName.default(this)
            startServer(name, ensureServiceStarted = false)
            if (_serverState.value != ServerState.RUNNING) stopSelf(startId)
        }
        return START_NOT_STICKY
    }
```
with:
```kotlin
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val decision = StartPolicy.decide(
            intent?.action, ACTION_START_SERVER,
            runInBackground = prefs.getBoolean(Prefs.RUN_IN_BACKGROUND, Prefs.DEF_RUN_IN_BACKGROUND),
        )
        if (decision.startServer) {
            promoteToForeground()
            val name = prefs.getString(Prefs.SERVER_NAME, null) ?: ServerName.default(this)
            startServer(name, ensureServiceStarted = false)
            if (_serverState.value != ServerState.RUNNING) stopSelf(startId)
        }
        return if (decision.sticky) START_STICKY else START_NOT_STICKY
    }
```

Note that `startServer(name, ensureServiceStarted = true)`, used by the UI, starts the service with `ACTION_START_SERVER` itself (check with `grep -n "ACTION_START_SERVER" app/src/main/kotlin -r`). If it uses `startForegroundService` without that action, the null-action branch covers it.

Run: `./gw :app:testDebugUnitTest :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Device check: kill the process and watch it come back**

Wake the TV (Global Constraints), install, and launch Sideplay. Then:
```bash
a(){ ~/Library/Android/sdk/platform-tools/adb -s 192.168.1.248:5555 "$@"; }
a shell input keyevent KEYCODE_HOME; sleep 2
PID=$(a shell pidof io.github.sudnep.sideplay | tr -d '\r'); echo "pid $PID"
a shell run-as io.github.sudnep.sideplay kill -9 $PID
sleep 15
a shell pidof io.github.sudnep.sideplay
a shell dumpsys activity services io.github.sudnep.sideplay | grep -c ServiceRecord
(dns-sd -B _airplay._tcp local. & p=$!; sleep 3; kill $p) 2>&1 | grep -c "Living Room TV"
```
Expected: a new pid, `1` service record, and the receiver advertised again. Android's restart back-off can take 1–60 s; if it isn't back, wait 60 s and check again.

Review Focus 1 (an explicit stop is not restarted) isn't device-testable on TV, which has no Stop control. Record "explicit stop: covered by stopServer() → stopSelf(); a stopped service is never restarted by Android" in the findings.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/io/github/jqssun/airplay/service/StartPolicy.kt app/src/test/kotlin/io/github/jqssun/airplay/service/StartPolicyTest.kt app/src/main/kotlin/io/github/jqssun/airplay/service/AirPlayService.kt
git commit -m "fix: restart the receiver after Android kills it (START_STICKY)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Default name gets " (AirPlay)"

**Files:**
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/ServerName.kt`
- Test: `app/src/test/kotlin/io/github/jqssun/airplay/ServerNameTest.kt`

**Interfaces:**
- Produces: `ServerName.SUFFIX = " (AirPlay)"`, `ServerName.withAirPlaySuffix(base: String): String`. `ServerName.default(context)` now returns `withAirPlaySuffix(choose(...))`. `choose()` is unchanged; renames and first run keep using it, so a user's own name gets no forced suffix.

- [ ] **Step 1: Write the failing tests** (append to `ServerNameTest`)

```kotlin
    @Test fun suffixAppended() =
        assertEquals("Living Room TV (AirPlay)", ServerName.withAirPlaySuffix("Living Room TV"))

    @Test fun suffixedNameFitsByTrimmingTheBase() {
        val name = ServerName.withAirPlaySuffix("A".repeat(50))
        assertEquals(ServerName.MAX_BYTES, name.toByteArray(Charsets.UTF_8).size)
        assertTrue(name.endsWith(ServerName.SUFFIX))
    }

    @Test fun suffixTrimNeverSplitsMultiByteCharacters() {
        val name = ServerName.withAirPlaySuffix("📺".repeat(20))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= ServerName.MAX_BYTES)
        assertEquals("📺".repeat(10) + ServerName.SUFFIX, name) // 40 + 10 bytes
    }

    @Test fun suffixNotDoubled() =
        assertEquals("Den (AirPlay)", ServerName.withAirPlaySuffix("Den (AirPlay)"))
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `./gw :app:testDebugUnitTest --tests 'io.github.jqssun.airplay.ServerNameTest'`
Expected: compilation FAILS with `Unresolved reference 'withAirPlaySuffix'`.

- [ ] **Step 3: Implement** (in `ServerName`)

Make `truncate` take a byte limit:
```kotlin
    private fun truncate(name: String, maxBytes: Int = MAX_BYTES): String {
        if (name.toByteArray(Charsets.UTF_8).size <= maxBytes) return name
        val out = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < name.length) {
            val cp = name.codePointAt(i)
            val chunk = String(Character.toChars(cp))
            val n = chunk.toByteArray(Charsets.UTF_8).size
            if (bytes + n > maxBytes) break
            out.append(chunk)
            bytes += n
            i += Character.charCount(cp)
        }
        return out.toString().trimEnd()
    }
```
Add:
```kotlin
    // tells sideplay apart from the tv's built-in chromecast, which uses the same device name
    const val SUFFIX = " (AirPlay)"

    fun withAirPlaySuffix(base: String): String {
        if (base.endsWith(SUFFIX)) return truncate(base)
        val room = MAX_BYTES - SUFFIX.toByteArray(Charsets.UTF_8).size
        return truncate(base, room) + SUFFIX
    }
```
Change `default`:
```kotlin
    fun default(context: Context): String {
        val deviceName = if (Build.VERSION.SDK_INT >= 25)
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) else null
        return withAirPlaySuffix(choose(deviceName, Build.MANUFACTURER, Build.MODEL))
    }
```

- [ ] **Step 4: Run all unit tests**

Run: `./gw :app:testDebugUnitTest`. Expected: all pass, and the 4 new tests are included.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jqssun/airplay/ServerName.kt app/src/test/kotlin/io/github/jqssun/airplay/ServerNameTest.kt
git commit -m "feat: default receiver name adds ' (AirPlay)' to avoid clashing with the TV's Chromecast

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Reach the overlay setting where the TV allows it

**Files:**
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/ui/tv/OverlayAccess.kt`
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/ui/tv/IdleScreen.kt`
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/ui/tv/SettingsScreen.kt` (Troubleshooting ADB tip)
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/ui/tv/TvApp.kt` (`onAllowAutoOpen`)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/io/github/jqssun/airplay/ui/tv/OverlayAccessTest.kt`, `app/src/androidTest/kotlin/io/github/jqssun/airplay/ui/tv/IdleScreenTest.kt`

**Interfaces:**
- Produces: `enum class OverlayAccess { GRANTED, GRANTABLE, GUIDED, UNAVAILABLE }`, `fun resolveOverlayAccess(canDraw: Boolean, overlayScreen: Boolean, appDetailsScreen: Boolean): OverlayAccess`, `OverlayPermission.intentFor(ctx, access): Intent?`, and Idle tag `open_app_settings`.

- [ ] **Step 1: Write the failing tests**

Replace `OverlayAccessTest`:
```kotlin
package io.github.jqssun.airplay.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayAccessTest {
    @Test fun grantedWinsRegardless() =
        assertEquals(OverlayAccess.GRANTED, resolveOverlayAccess(canDraw = true, overlayScreen = false, appDetailsScreen = false))
    @Test fun overlayScreenIsGrantable() =
        assertEquals(OverlayAccess.GRANTABLE, resolveOverlayAccess(canDraw = false, overlayScreen = true, appDetailsScreen = true))
    // android 10 tv settings: no overlay deep link, but the app page opens and special app access is one level up
    @Test fun onlyAppDetailsIsGuided() =
        assertEquals(OverlayAccess.GUIDED, resolveOverlayAccess(canDraw = false, overlayScreen = false, appDetailsScreen = true))
    @Test fun nothingReachableIsUnavailable() =
        assertEquals(OverlayAccess.UNAVAILABLE, resolveOverlayAccess(canDraw = false, overlayScreen = false, appDetailsScreen = false))
}
```
Append to `IdleScreenTest`:
```kotlin
    @Test fun guidedShowsAppSettingsButtonAndSteps() {
        var opened = false
        show(access = OverlayAccess.GUIDED, onAllow = { opened = true })
        rule.onNodeWithTag("open_app_settings").assertIsDisplayed().performClick()
        assertTrue(opened)
        rule.onNodeWithTag("overlay_steps").assertTextContains("Special app access", substring = true)
    }
```

- [ ] **Step 2: Run the unit test and confirm it fails**

Run: `./gw :app:testDebugUnitTest --tests 'io.github.jqssun.airplay.ui.tv.OverlayAccessTest'`
Expected: compilation FAILS (`No parameter with name 'overlayScreen'`, `Unresolved reference 'GUIDED'`).

- [ ] **Step 3: Implement `OverlayAccess.kt`**

```kotlin
package io.github.jqssun.airplay.ui.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Whether the service may bring the app to the front when a device connects (Android 10+ background start rules). */
enum class OverlayAccess { GRANTED, GRANTABLE, GUIDED, UNAVAILABLE }

fun resolveOverlayAccess(canDraw: Boolean, overlayScreen: Boolean, appDetailsScreen: Boolean): OverlayAccess = when {
    canDraw -> OverlayAccess.GRANTED
    overlayScreen -> OverlayAccess.GRANTABLE
    appDetailsScreen -> OverlayAccess.GUIDED
    else -> OverlayAccess.UNAVAILABLE
}

object OverlayPermission {
    private fun overlay(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
    // android 10 tvsettings has the page but no deep link; the app page sits one back-press from special app access
    private fun appDetails(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    fun access(ctx: Context): OverlayAccess {
        val pm = ctx.packageManager
        return resolveOverlayAccess(
            canDraw = Settings.canDrawOverlays(ctx),
            overlayScreen = overlay(ctx).resolveActivity(pm) != null,
            appDetailsScreen = appDetails(ctx).resolveActivity(pm) != null,
        )
    }

    fun intentFor(ctx: Context, access: OverlayAccess): Intent? = when (access) {
        OverlayAccess.GRANTABLE -> overlay(ctx)
        OverlayAccess.GUIDED -> appDetails(ctx)
        else -> null
    }
}
```
In `AndroidManifest.xml`, add a second `<intent>` to the existing `<queries>` block:
```xml
        <intent>
            <action android:name="android.settings.APPLICATION_DETAILS_SETTINGS" />
            <data android:scheme="package" />
        </intent>
```

- [ ] **Step 4: Strings and Idle UI**

`strings.xml`:
```xml
    <string name="tv_idle_open_app_settings">Open Sideplay\'s settings</string>
    <string name="tv_idle_overlay_steps">To let Sideplay open by itself: TV Settings → Apps → Special app access → Display over other apps → Sideplay</string>
    <string name="tv_ts_overlay_adb">If your TV has no such setting, a computer can allow it once: adb shell appops set %1$s SYSTEM_ALERT_WINDOW allow</string>
```
In `TvIdleScreen`, extend the `when (overlayAccess)` block with:
```kotlin
                OverlayAccess.GUIDED -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAllowAutoOpen, modifier = Modifier.tvFocus().testTag("open_app_settings")) {
                        Text(stringResource(R.string.tv_idle_open_app_settings))
                    }
                    Text(
                        stringResource(R.string.tv_idle_overlay_steps),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvMuted,
                        modifier = Modifier.testTag("overlay_steps"),
                    )
                }
```
In `TvApp`, change `onAllowAutoOpen`:
```kotlin
                    onAllowAutoOpen = {
                        OverlayPermission.intentFor(context, overlayAccess)?.let {
                            runCatching { context.startActivity(it) }
                        }
                    },
```
In `TvTroubleshootingScreen`, after the disclaimer item, add:
```kotlin
        item { Text(stringResource(R.string.tv_ts_overlay_adb, LocalContext.current.packageName), color = muted) }
```
(import `androidx.compose.ui.platform.LocalContext`).

- [ ] **Step 5: Run unit tests and the Idle tests on the TV**

Run: `./gw :app:testDebugUnitTest`, then the instrumented command with `-Pandroid.testInstrumentationRunnerArguments.class=io.github.jqssun.airplay.ui.tv.IdleScreenTest`.
Expected: all pass, including `guidedShowsAppSettingsButtonAndSteps`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/io/github/jqssun/airplay/ui/tv app/src/main/res/values/strings.xml app/src/main/AndroidManifest.xml app/src/test/kotlin/io/github/jqssun/airplay/ui/tv/OverlayAccessTest.kt app/src/androidTest/kotlin/io/github/jqssun/airplay/ui/tv/IdleScreenTest.kt
git commit -m "feat(tv): guide users to 'Display over other apps' when the TV has no deep link

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Prompt to open Sideplay when it can't open itself

**Files:**
- Create: `app/src/main/kotlin/io/github/jqssun/airplay/service/ConnectLaunch.kt`
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/service/AirPlayService.kt` (`launchMainActivity`, `onConnectionDestroy`, new prompt notification and channel, `uiVisible`)
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/MainActivity.kt` (`onStart`/`onStop`, `onServiceConnected`)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/io/github/jqssun/airplay/service/ConnectLaunchTest.kt`

**Interfaces:**
- Produces: `enum class ConnectLaunch { NONE, LAUNCH, PROMPT }`, `fun decideConnectLaunch(uiVisible: Boolean, canDrawOverlays: Boolean): ConnectLaunch`, `AirPlayService.uiVisible: Boolean` (setter cancels the prompt when true).

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.jqssun.airplay.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectLaunchTest {
    @Test fun visibleUiNeedsNothing() {
        assertEquals(ConnectLaunch.NONE, decideConnectLaunch(uiVisible = true, canDrawOverlays = false))
        assertEquals(ConnectLaunch.NONE, decideConnectLaunch(uiVisible = true, canDrawOverlays = true))
    }
    @Test fun overlayAllowedLaunchesDirectly() =
        assertEquals(ConnectLaunch.LAUNCH, decideConnectLaunch(uiVisible = false, canDrawOverlays = true))
    // android 10+ blocks the background start; ask the user instead
    @Test fun otherwisePrompt() =
        assertEquals(ConnectLaunch.PROMPT, decideConnectLaunch(uiVisible = false, canDrawOverlays = false))
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gw :app:testDebugUnitTest --tests 'io.github.jqssun.airplay.service.ConnectLaunchTest'`
Expected: compilation FAILS with `Unresolved reference 'decideConnectLaunch'`.

- [ ] **Step 3: Implement the decision**

```kotlin
package io.github.jqssun.airplay.service

enum class ConnectLaunch { NONE, LAUNCH, PROMPT }

fun decideConnectLaunch(uiVisible: Boolean, canDrawOverlays: Boolean): ConnectLaunch = when {
    uiVisible -> ConnectLaunch.NONE
    canDrawOverlays -> ConnectLaunch.LAUNCH
    else -> ConnectLaunch.PROMPT
}
```
Run Step 2's command. Expected: 3 passed.

- [ ] **Step 4: Strings**

```xml
    <string name="tv_prompt_title">A device is connecting</string>
    <string name="tv_prompt_text">Open Sideplay to show it on the TV</string>
    <string name="tv_prompt_channel">Connection prompts</string>
```

- [ ] **Step 5: Service wiring**

In `AirPlayService`:
- Add:
```kotlin
    @Volatile var uiVisible: Boolean = false
        set(value) {
            field = value
            if (value) cancelOpenPrompt()
        }
```
- Replace the body of `launchMainActivity()` with:
```kotlin
    private fun launchMainActivity() {
        Handler(Looper.getMainLooper()).post {
            when (decideConnectLaunch(uiVisible, android.provider.Settings.canDrawOverlays(this))) {
                ConnectLaunch.NONE -> Unit
                ConnectLaunch.LAUNCH -> {
                    val launchIntent = Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    try {
                        startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to launch activity", e)
                    }
                }
                ConnectLaunch.PROMPT -> showOpenPrompt()
            }
        }
    }

    // a tap on a notification is a system-sent PendingIntent, which background-start rules allow
    private fun showOpenPrompt() {
        android.widget.Toast.makeText(this, getString(R.string.tv_prompt_text), android.widget.Toast.LENGTH_LONG).show()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(PROMPT_CHANNEL_ID, getString(R.string.tv_prompt_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, PROMPT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.tv_prompt_title))
            .setContentText(getString(R.string.tv_prompt_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(PROMPT_NOTIFICATION_ID, n)
    }

    private fun cancelOpenPrompt() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(PROMPT_NOTIFICATION_ID)
    }
```
(Check which notification builder the file already uses with `grep -n "NotificationCompat\|Notification.Builder" service/AirPlayService.kt`, and use the same one; add `import androidx.core.app.NotificationCompat` if missing.)
- In `onConnectionDestroy()`, when the count reaches 0, call `cancelOpenPrompt()`.
- In the companion object, add `private const val PROMPT_CHANNEL_ID = "connect_prompt"` and `private const val PROMPT_NOTIFICATION_ID = 2`.

In `MainActivity`:
- In `onServiceConnected`, after `service = svc`: `svc.uiVisible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)`.
- Override `onStart()`: `super.onStart(); service?.uiVisible = true`.
- In `onStop()`, after `super.onStop()`: `service?.uiVisible = false`.

Run: `./gw :app:testDebugUnitTest :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/io/github/jqssun/airplay/service/ConnectLaunch.kt app/src/test/kotlin/io/github/jqssun/airplay/service/ConnectLaunchTest.kt app/src/main/kotlin/io/github/jqssun/airplay/service/AirPlayService.kt app/src/main/kotlin/io/github/jqssun/airplay/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "feat: prompt to open Sideplay when Android blocks bringing it to the front

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Instrumentation for the Drive investigation (UxPlay patch 0008 and track log)

**Files:**
- Create: `app/src/main/cpp/patches/UxPlay/0008-log-served-hls-playlists.patch` (generated)
- Temporarily edited, never committed: `app/src/main/cpp/third_party/UxPlay/lib/http_handlers.h`
- Modify: `app/src/main/kotlin/io/github/jqssun/airplay/renderer/AirPlayVideoPlayer.kt` (`onTracksChanged`)

**Interfaces:**
- Produces logcat lines (tag `AirPlayNative`): `AIRCAP HLS <master|media> <url> <n>: <playlist line with query strings replaced by ?<redacted>>`, at most 80 lines per playlist. Tag `AirPlayVideoPlayer`: `AIRCAP TRACK type=<int> selected=<bool> container=<mime> sample=<mime> codecs=<str> <w>x<h>`.

- [ ] **Step 1: Stage the existing patches as the baseline** (same method as Plan 1 Task 4)

```bash
./gw :app:applyUxplayPatches -q
U=app/src/main/cpp/third_party/UxPlay; git -C $U add -A
```

- [ ] **Step 2: Add the logger and call sites in `http_handlers.h`**

Insert directly above `static void\nhttp_handler_hls(`:
```c
#ifndef NDEBUG
/* AIRCAP: log a served playlist line by line (debug only); query strings may carry signed tokens */
static void aircap_log_playlist(raop_t *raop, const char *kind, const char *url, const char *text) {
    char line[512];
    int n = 0;
    const char *p = text;
    while (p && *p && n < 80) {
        const char *eol = strchr(p, '\n');
        size_t len = eol ? (size_t) (eol - p) : strlen(p);
        size_t o = 0;
        for (size_t i = 0; i < len && o < sizeof(line) - 1; i++) {
            if (p[i] == '?') {
                while (i < len && p[i] != '"' && p[i] != ',') i++;
                if (o + 11 < sizeof(line) - 1) { memcpy(line + o, "?<redacted>", 11); o += 11; }
                if (i < len && o < sizeof(line) - 1) line[o++] = p[i];
                continue;
            }
            line[o++] = p[i];
        }
        line[o] = '\0';
        logger_log(raop->logger, LOGGER_ERR, "AIRCAP HLS %s %s %d: %s", kind, url, n, line);
        n++;
        p = eol ? eol + 1 : NULL;
    }
}
#endif

```
In `http_handler_hls`, after `*response_datalen = (int ) len;` in the master branch, add:
```c
#ifndef NDEBUG
            aircap_log_playlist(raop, "master", url, data);
#endif
```
and after `*response_datalen = strlen(data);` in the media branch, add:
```c
#ifndef NDEBUG
            aircap_log_playlist(raop, "media", url, data);
#endif
```

- [ ] **Step 3: Generate patch 0008, restore, and re-apply from clean**

```bash
U=app/src/main/cpp/third_party/UxPlay
git -C $U diff > app/src/main/cpp/patches/UxPlay/0008-log-served-hls-playlists.patch
git -C $U reset -q && git -C $U checkout -- . && ./gw :app:applyUxplayPatches -q
grep -c "aircap_log_playlist" $U/lib/http_handlers.h
```
Expected: `3` (one definition, two calls).

- [ ] **Step 4: Log the tracks ExoPlayer picks**

In `AirPlayVideoPlayer`'s `_listener`, add:
```kotlin
        override fun onTracksChanged(tracks: Tracks) {
            if (!BuildConfig.DEBUG) return
            tracks.groups.forEach { g ->
                for (i in 0 until g.length) {
                    val f = g.getTrackFormat(i)
                    Log.w(TAG, "AIRCAP TRACK type=${g.type} selected=${g.isTrackSelected(i)} container=${f.containerMimeType} " +
                        "sample=${f.sampleMimeType} codecs=${f.codecs} ${f.width}x${f.height}")
                }
            }
        }
```
and `import androidx.media3.common.Tracks`.

- [ ] **Step 5: Release check and smoke test**

```bash
./gw :app:assembleRelease -q && W=$(mktemp -d) && unzip -q -o app/build/outputs/apk/release/app-release-unsigned.apk 'lib/arm64-v8a/*.so' 'classes*.dex' -d $W && echo "so=$(strings $W/lib/arm64-v8a/*.so | grep -c AIRCAP) dex=$(strings $W/classes*.dex | grep -c AIRCAP)"
```
Expected: `so=0 dex=0`.

The smoke test of the actual lines happens in Task 6, because it needs an iPhone cast.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/cpp/patches/UxPlay/0008-log-served-hls-playlists.patch app/src/main/kotlin/io/github/jqssun/airplay/renderer/AirPlayVideoPlayer.kt
git commit -m "feat: debug-only logging of served HLS playlists and selected tracks (patch 0008)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Device session (hands-on with the user)

**Files:**
- Create: `docs/findings/2026-10-plan3-session.md`
- Create: `docs/findings/captures/drive-hls.txt`, `docs/findings/captures/safari-vs-airreceiver.txt` (redacted)

Install the build from Task 5 and start a full logcat recording, as in the 2026-10-04 session. Then, with the user:

- [ ] **Step 1: Name.** The iPhone's Screen Mirroring list shows **"Living Room TV (AirPlay)"**, and YouTube's cast list now shows two distinct names.
- [ ] **Step 2: Restart check, if Task 1 Step 6 wasn't done.**
- [ ] **Step 3: Overlay route.** The user opens TV Settings → Apps → **Special app access**. Is **"Display over other apps"** listed, with Sideplay in it? Record yes or no. Then reset the permission for the next step: `appops set … SYSTEM_ALERT_WINDOW default`.
- [ ] **Step 4: Prompt.** The user presses Home and starts mirroring. Expected: a Toast "Open Sideplay to show it on the TV" and/or a notification. Record which of the two appeared. Opening Sideplay shows the picture and clears the notification. Restore `SYSTEM_ALERT_WINDOW allow`.
- [ ] **Step 5: Drive capture.** The user casts the same Google Drive video via the AirPlay menu. Save all `AIRCAP HLS` and `AIRCAP TRACK` lines, plus `Unexpected start code` counts, to `captures/drive-hls.txt`. Then do the same for a YouTube app video for comparison (append, with a header line).
- [ ] **Step 6: Safari vs AirReceiver.** The user stops mirroring and AirPlays a Safari MP4 to **AFTKA-209 (the Fire TV's AirReceiver)**. Does the phone show "Playing on AFTKA-209", with only the video on the TV? Then the same to Living Room TV (AirPlay). Record both, and AirReceiver's TXT `features`, `model` and `srcvers` (`dns-sd -L "AFTKA-209[AirPlay]" _airplay._tcp`) next to ours.
- [ ] **Step 7: PIN + YouTube cast** (open item from the Plan 1 review). Turn PIN on, pair, and AirPlay a YouTube video: the PIN screen must not cover the video. Turn PIN off again.
- [ ] **Step 8: Rerun the full instrumented suite.** Expected: everything passes. Relaunch Sideplay afterwards.
- [ ] **Step 9: Write the findings doc**, with one section per step, and for Drive and Safari a "Root cause hypothesis and the smallest fix" paragraph that Plan 3b is written from. Restore the TV (PIN off, overlay allowed, `stayon false`, sleep). Commit:

```bash
git add docs/findings/2026-10-plan3-session.md docs/findings/captures/drive-hls.txt docs/findings/captures/safari-vs-airreceiver.txt
git commit -m "docs: Plan 3 device session (name, restart, overlay route, prompt, Drive HLS, Safari vs AirReceiver)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: New app icon and TV banner

The user asked for a new icon (2026-10-04); the current launcher icon and banner are jqssun's.

**Files:**
- Replace: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` and their foreground/background drawables (find them with `ls app/src/main/res/mipmap-* app/src/main/res/drawable*`)
- Replace: `app/src/main/res/drawable*/banner*` (TV banner, 320×180 dp)
- Create: `docs/design/icon-concepts/` (concept PNGs shown to the user)

**Constraints:** no Apple AirPlay glyph (rectangle + triangle) and no Apple marks; vector drawables only; readable at 48 dp; the banner shows the "Sideplay" wordmark.

- [ ] **Step 1:** Draw 3 distinct concepts as SVG, render them to PNG (icon at 512 px plus a banner), and show them to the user with AskUserQuestion (previews). Stop until the user picks one or asks for changes.
- [ ] **Step 2:** Convert the chosen concept to adaptive-icon vector XML (foreground plus solid background) and a banner vector, replacing the files above. Keep resource names the same so the manifest is unchanged.
- [ ] **Step 3:** `./gw :app:assembleDebug`, install, then screenshot the Google TV launcher's app row (`adb exec-out screencap`) to check the banner and icon render. Commit:

```bash
git add app/src/main/res docs/design/icon-concepts
git commit -m "feat: Sideplay icon and TV banner

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: TV-only app (remove the phone/tablet side)

The user decided on 2026-10-04 that the app targets Android TV only. The phone UI, touch gestures and video downloader are removed, and the manifest makes Play offer the app to TVs only.

**Files:**
- Delete: `app/src/main/kotlin/io/github/jqssun/airplay/ui/MainScreen.kt`, `ui/SettingsScreen.kt`, `ui/VideoControls.kt`, `ui/LogsScreen.kt`, `ui/PlaybackSpeedSelector.kt`, `ui/gestures/` (all), `download/VideoDownloader.kt`
- Modify: `MainActivity.kt` (always `TvApp`), `viewmodel/MainViewModel.kt` and `service/AirPlayService.kt` (remove members only the deleted UI used), `ui/Tv.kt` (drop `isTv()`/`isTvDevice()` if unused), `AndroidManifest.xml`, `res/values/strings.xml` (drop strings only the deleted UI used)

- [ ] **Step 1: Baseline.** `./gw :app:testDebugUnitTest :app:assembleDebug` passes. Record the unit test count.
- [ ] **Step 2: Delete the phone UI files and the downloader**, then make `MainActivity.setContent` always use `TvApp` (remove the `tv` switch, the `MainScreen` import and `isTvDevice`).
- [ ] **Step 3: Remove dead members.** Build, and fix compile errors by removing the downloader hooks: `videoDownloader` and `downloadVideo()` in the service, `videoDownloadProgress` and `toggleVideoDownload()` in the ViewModel. Then list every public `fun`/`val` in `MainViewModel.kt` with no remaining reference outside its own file:
```bash
cd app/src/main/kotlin/io/github/jqssun/airplay
for n in $(grep -oE "^\s+(fun|val) [a-zA-Z_]+" viewmodel/MainViewModel.kt | awk '{print $2}' | sort -u); do
  c=$(grep -rlw "$n" --include=*.kt . | grep -v viewmodel/MainViewModel.kt | wc -l); [ "$c" -eq 0 ] && echo "$n"
done
```
Remove each listed member, and its private backing field, unless it is used inside the ViewModel by something still referenced. Do the same check for `service/AirPlayService.kt` public members referenced only by the deleted files. Rebuild after each batch.
- [ ] **Step 4: Manifest.** Set `<uses-feature android:name="android.software.leanback" android:required="true" />`, keep touchscreen and faketouch `required="false"`, and remove `<category android:name="android.intent.category.LAUNCHER" />` (keep `LEANBACK_LAUNCHER`). Verify:
```bash
~/Library/Android/sdk/build-tools/36.0.0/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "uses-feature|launchable"
```
Expected: `uses-feature: name='android.software.leanback'` (required), `leanback-launchable-activity`, and no plain `launchable-activity`.
- [ ] **Step 5: Strings.** Remove string resources no longer referenced (`grep -rn "R.string.<name>"` per candidate; the lint task `./gw :app:lintDebug` lists `UnusedResources` and can be used instead).
- [ ] **Step 6: Verify.** `./gw :app:testDebugUnitTest` (same count as Step 1), `./gw :app:assembleDebug assembleRelease`, and the full instrumented suite on the Hisense. Then install and screenshot Idle, Settings and Troubleshooting to check nothing regressed. Report the line count removed (`git diff --stat`).
- [ ] **Step 7: Commit.**
```bash
git add -A app/src/main
git commit -m "refactor: TV-only app; remove phone UI, gestures and video downloader

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

## Done when

- `./gw :app:testDebugUnitTest` passes (+5 StartPolicy, +4 ServerName, +1 net OverlayAccess, +3 ConnectLaunch), and the full instrumented suite passes on the Hisense.
- The receiver comes back by itself after `kill -9`, and is advertised as "Living Room TV (AirPlay)".
- `docs/findings/2026-10-plan3-session.md` has root-cause hypotheses for Drive and Safari, and Plan 3b can be written from it.
