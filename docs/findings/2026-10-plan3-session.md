# Plan 3 device session — Hisense

Date: 2026-10-04 (11:00–11:57)  ·  Build: Plan 3 Tasks 1–5, 7, 8 + session fix c7cdca5  ·  Device: Hisense SmartTV 4K FFM, Android 10  ·  Sender: iPhone (AirPlay client 980.77.1)

## Results by step

| Step | Result |
|---|---|
| 1. Name | ✅ iPhone lists **"Living Room TV (AirPlay)"** |
| 2. Overlay route | ✅ **The Hisense has Settings → Apps → Special app access → Display over other apps.** The user enabled it for Sideplay, so the GUIDED steps (Task 3) lead to a real switch. The deep link is still missing (Android 10 TvSettings), so the app-details route is the right one. |
| 3. Prompt | Covered by a simulated connection (Task 4): the toast appears on the Google TV home screen, and the notification is posted but not shown by the launcher. Not repeated live because the overlay is now allowed. |
| 4. Drive | ❌ audio only, frozen frame. **Root cause found** (see below). |
| 5. Safari vs AirReceiver | Direct-URL video **works** from the Messages in-app browser (`proc=MobileSMS`, `/play https://…mp4`, H.264 1080p). The **Safari app** connects but **never sends `/play`** (only an RTSP volume query), with PIN on or off. The user reports AirReceiver (Fire TV) plays the same Safari video as video. |
| 6. PIN + YouTube cast | ✅ The PIN cleared and the video showed (clearPin in onVideoPlay, 0cd9470) |
| 7. Instrumented suite | 48/49; the 1 failure was the TV launcher (uid 10055) opening Sideplay mid-test after the user pressed the tile. The class rerun passed 2/2. |
| Restart after kill (Task 1) | ✅ `am crash` → restarted in 6 s, launcher still in front |
| Restart after update | ✅ `MY_PACKAGE_REPLACED` → server started with nothing launched (4b3427b) |

Captures: `captures/drive-hls.txt`, `captures/safari-vs-airreceiver.txt` (redacted).

## Root causes and smallest fixes (input for Plan 3b)

### A. Portrait high-resolution VP9 fails on the MediaTek decoder (Drive, and YouTube Shorts)
- ExoPlayer selects VP9 HLS variants and adapts up to `vp09.00.50.08 1440x2560`. `OMX.MTK.VIDEO.DECODER.VP9` reports the format as supported (`format_supported=YES`), but fails: `MediaCodecVideoDecoderException: Decoder failed`. The player stops, and the iPhone loops `/play` → `/stop`.
- The same decoder plays 1080x1920 VP9 and 4K landscape. Its real limit is landscape-shaped (≈4096×2176); a portrait 2560-tall frame exceeds it.
- **Fix:** constrain ExoPlayer's track selection so no video track is taller than the decoder's landscape short side: `setMaxVideoSize(longSide, shortSide)` derived from the decoder's reported max, treating it as landscape-only. As a fallback on `ERROR_CODE_DECODING_FAILED`, retry once with VP9 excluded (`setPreferredVideoMimeType(H264)`).

### B. The PIN changes on every receiver start, so phones that paired before are locked out silently
- `native_bridge.cpp:93` generates `random_pin()` at each server start. The iPhone retries its cached code → `Client Authentication Failure (client proof not validated)` and never re-prompts. Any restart (update, crash, reboot, settings change) locks paired phones out while PIN mode is on.
- **Fix:** generate the PIN once, persist it in prefs, and pass it to `nativeInit`. Only regenerate when the user turns PIN mode on (or adds a "new code" action).

### C. The Safari app doesn't send `/play` to Sideplay
- Messages' in-app browser uses classic `/play` (works). The Safari app opens an RTSP connection and does nothing visible to our HTTP-only capture. AirReceiver (advertises `model=AppleTV3,1`, `features=0x527FFFF7,0x8`) receives it as video; Sideplay advertises `features=0x5A7FFEF7,0x400`.
- **Next step (needs one capture):** extend patch 0007 to also log RTSP requests (method + URL, excluding the frequent `GET_PARAMETER`/`SET_PARAMETER`) and capture a Safari-app cast. The likely fix is feature-bit or RTSP `SETUP` stream-type handling, but this is unconfirmed.

### Fixed during the session
- **The PIN lingered after a successful pairing when no stream followed** → now cleared on native `register_client` (c7cdca5). Verified: `PIN pairing completed` at 11:54:58 and the screen cleared.

## Process notes
- Never run instrumented tests while the user is casting. The test install restarted the receiver mid-cast (11:51:29), and instrumentation then **force-stopped** the app. A force-stop defeats both `START_STICKY` and `MY_PACKAGE_REPLACED`, so relaunch the app after every `connectedAndroidTest`.

## Addendum: playback when the iPhone locks (12:02–12:17)

- **YouTube (HLS from the phone):** at lock the iPhone sends `POST /rate?value=0` (pause), e.g. 12:07:02, and Sideplay obeys. One session (12:11:12) also ended on the receiver ~17 s after resume (`stopped (player)`), consistent with the phone no longer supplying media playlists over the reverse channel while locked.
- **Direct URL** (`https://media.w3.org/2010/05/bunny/movie.mp4` opened from Messages, `proc=com.apple.WebKit.GPU`): **keeps playing** with the phone locked; no pause is sent.
- **Open:** the same YouTube lock test against the Fire TV's AirReceiver, to confirm the pause is YouTube-app behaviour. The user couldn't run it this session; it's added to the Plan 3b device session.
