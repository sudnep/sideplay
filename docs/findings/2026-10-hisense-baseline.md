# Baseline on Hisense

Date: 2026-10-03/04  ·  App: Sideplay 0.1.0 (upstream c8defdd + Plan 1/2 changes)  ·  iPhone: model and iOS not recorded (AirPlay client `AirPlay/980.77.1`)

## Devices
| TV | Model prop | Android | Result |
|---|---|---|---|
| Hisense | SmartTV 4K FFM (MT5895, Mali-G52) | 10 (API 29) | Tested |
| Sony | BRAVIA XBR-55X800G | — | Not run (debugging not enabled) |

## Mirroring baseline
| Check | Hisense |
|---|---|
| Name shown on iPhone | "Living Room TV" (TV's device name) |
| Time to first frame | ~0.5 s from connect to decoder start |
| Smooth / lag | Smooth, no noticeable lag (user) |
| Codec | H.264 `OMX.MTK.VIDEO.DECODER.AVC`; H.265 is advertised since cf82f5d but the iPhone still chose H.264 |
| Audio | AAC, low-latency output |
| Rotation | Follows portrait ↔ landscape |
| Portrait framing | Centred (after 87ecff5) |
| Disconnect (TV Back → Disconnect) | Returns to Idle; receiver restarted in 42 ms and re-advertised |

## Background launch
| Condition | Hisense |
|---|---|
| Default permissions: app came to front? | **No.** `W/ActivityTaskManager: Background activity start [callingPackage: io.github.sudnep.sideplay … isCallingUidForeground: false]`, launcher stays in front; the picture appears once Sideplay is opened manually |
| Overlay granted via `appops ... SYSTEM_ALERT_WINDOW allow`: app came to front? | **Yes**, switches on its own |
| "Display over other apps" visible in TV settings? | **No.** `cmd package resolve-activity -a android.settings.action.MANAGE_OVERLAY_PERMISSION` → "No activity found" |

Captures: `captures/background-start-default.txt`, `captures/background-start-overlay-granted.txt`.

## Receiver lifetime
- The foreground service survived 2+ hours of TV standby (2026-10-03).
- **Overnight the process was killed at 05:22** (`am_proc_died … oom 200`; no reboot, uptime 8 days, load average ~33) and **was not restarted**, because `AirPlayService.onStartCommand` returns `START_NOT_STICKY` and only starts the server for `ACTION_START_SERVER`. Result: not discoverable until the app is opened again.

## Decision for Plan 2 / next plan
- Overlay works but is only grantable via ADB on this TV → the Idle screen's "Keep Sideplay open to receive the picture" note (Plan 2) is correct for stock installs. Making it automatic needs a different mechanism (to be designed: e.g. full-screen-intent notification, or an ADB/setup-guide path), which is outside Plan 1.
- The receiver must be restarted after a low-memory kill (`START_STICKY` and handling a null intent) for "always on" to hold.
