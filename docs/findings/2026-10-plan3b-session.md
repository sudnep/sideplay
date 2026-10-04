# Plan 3b device session — Hisense

Date: 2026-10-04 (12:23–13:16)  ·  Build: Plan 3b Tasks 1–3 + session fix 758678d  ·  Device: Hisense SmartTV 4K FFM, Android 10  ·  Sender: iPhone (AirPlay client 980.77.1)

## Results by step

| Step | Result |
|---|---|
| 1. Drive portrait | ❌ **Not ours to fix.** Every Drive video (including the w3.org bunny MP4 uploaded to Drive) arrives as AirPlay **audio only**: RTSP `SETUP`/`RECORD` with `screen=false`, and no `/play` request. Reinstalling Drive, restarting the phone, renaming the receiver and "available offline" changed nothing. The same files play as video from Safari and Messages. This matches public reports of Drive AirPlaying audio only even to an Apple TV. Workaround: Screen Mirroring. The Plan 3 VP9 failure in Drive's HLS path no longer occurs, because Drive no longer takes that path. |
| 1b. YouTube Short | ✅ picture and sound. ExoPlayer selected H.264 up to 1080x1920 (the size cap works), with 0 `Decoder failed`. |
| 2. Stable PIN | ✅ PIN on, paired at 13:14:08 (`PIN pairing completed`). Sideplay was force-stopped and relaunched (new process, server up at 13:14:50). The iPhone reconnected at 13:15:29 with `/pair-verify` only: no code prompt and no `client proof not validated`. PIN turned off afterwards. |
| 3. Safari app | ✅ The Safari app now sends classic `/play` (`proc=com.apple.WebKit.GPU`, direct MP4, H.264 853x480) and plays as video. Capture: `captures/safari-app-rtsp.txt`. The Plan 3 "no `/play`" observation did not recur, so no fix is needed (no Plan 3c). |
| 3b. YouTube lock vs AirReceiver | ⏸ Deferred: the Fire TV wasn't available. |

## Fixed during the session

- **Stale music screen after the iPhone stopped audio** (758678d). iOS keeps the control connection open (`/feedback` every 2 s) after `TEARDOWN`, so the TV stayed on the album-art screen. Now an audio teardown leaves audio mode unless mirroring is active, and the idle "Connecting…" label falls back to "ready" after 10 s. Verified by the user.

## Open: intermittent dark mirroring (not reproduced)

- 12:59–13:02: four mirroring sessions in a row (home screen and Photos) were black. Each started normally: `mirror running: 1`, 1920x1080, OMX.MTK H.264 decoder on a 1920x1088 native window, EGL success. SurfaceFlinger listed Sideplay's SurfaceView layer, but it had no buffer.
- The same process (no install, no restart) then mirrored correctly from 13:03 on, in both portrait (498x1080) and landscape (1920x1080).
- These sessions followed a receiver rename with the on-screen keyboard open in Settings (12:57–13:01). Repeating the rename at 13:10 did **not** reproduce the black screen.
- Next time: note the time and pull the log while the screen is dark. Check `dumpsys SurfaceFlinger` and whether `VideoPipeline` received a display surface.
