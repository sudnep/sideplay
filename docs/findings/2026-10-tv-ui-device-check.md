# TV UI device check — Hisense

Date: 2026-10-04  ·  Build: Sideplay 0.1.0 (Plan 2, after fixes 0bd65d8, 9133fb0, 8d130f8)  ·  Device: Hisense SmartTV 4K FFM, Android 10, 1920×1080 @ 320 dpi

Driven over ADB (`input keyevent`, `screencap`, `uiautomator dump`); no iPhone involved.

| Check | Result | Screenshot |
|---|---|---|
| First run on clean data | Pass: title, name pre-filled "Living Room TV", start-at-boot on, Done focused (white ring). Focus lands ~6–10 s after a cold start (receiver start-up). | screens/tv-firstrun.png |
| Idle after Done (name, steps, IP, focus) | Pass: "Living Room TV is ready", 3 steps, "TV address: 192.168.1.248", Settings focused | screens/tv-idle.png |
| Prefs written | Pass: `first_run_done=true`, `boot_auto_start=true` | — |
| Settings reachable, rows by D-pad | Pass, after fix 8d130f8 (last row was squashed before) | screens/tv-settings.png, screens/tv-settings-bottom.png |
| Troubleshooting content | Pass: address + port 7000, decoders "H.264: OMX.MTK.VIDEO.DECODER.AVC · H.265: OMX.MTK.VIDEO.DECODER.HEVC" (after review fix; was always "No video playing"), About, licence, credits, Apple disclaimer; D-pad scrolls to "Recent log" | screens/tv-troubleshooting.png, screens/tv-troubleshooting-scrolled.png |
| Back returns to Idle | Pass: Troubleshooting → Settings → Idle | screens/tv-idle-after-back.png |
| Still advertised as "Living Room TV" | Pass (`dns-sd -B _airplay._tcp`) | — |
| Overlay UI before grant | Shows "Keep Sideplay open to receive the picture" (`OverlayAccess.UNAVAILABLE`) | screens/tv-idle.png |
| Overlay UI after `appops ... SYSTEM_ALERT_WINDOW allow` + resume | Pass: note hidden | screens/tv-idle-overlay-granted.png |

## Defects found and fixed during the check

1. **Name field text nearly invisible on first run.** The TV runs in light mode, so `AirPlayTheme` picked a light scheme (dark `onSurface`) on our dark background. Fix: `TvTheme` forces dark (0bd65d8; `TvThemeTest`, RED luminance 0.011 → GREEN).
2. **No visible focus ring on filled buttons.** `dpadFocus` draws in `colorScheme.primary`, the same colour as the button fill. Fix: `tvFocus`, a white ring around a 5 dp inset (9133fb0; `TvFocusTest`, RED edge pixel lavender → GREEN; a mutation back to `dpadFocus` fails it).
3. **Last Settings row squashed to 22 dp, label clipped.** Caused by the inset from fix 2. Fix: Settings scrolls (8d130f8; `SettingsScreenTest.dpadDownReachesEveryRowInOrder` now asserts ≥ 56 dp per row).

## Findings that affect design

- **No "Display over other apps" screen on this Hisense.** `cmd package resolve-activity -a android.settings.action.MANAGE_OVERLAY_PERMISSION` → "No activity found". Users can't grant the overlay permission on this TV, so whether a session can bring Sideplay to the front by itself depends on Plan 1 Task 3 test A. The permission was reset to `default` after this check so that test stays clean.
- **One unexplained exit to the launcher.** During a manual key sequence the remote input ended up in the system launcher, which opened Netflix (Google sign-in prompt shown; nothing played, nobody signed in). Replaying the same keys afterwards (Idle → Settings → Back, Center + Down×4) stayed inside Sideplay every time, and the logs don't show where focus left. Not reproduced; watch for it in the next TV session.
- The grey "P: 0/0 X: …" bar in the screenshots is the TV's own **Pointer location** developer option (`settings get system pointer_location` = 1), not Sideplay.

## Needs the iPhone (next TV session)

- Mirroring: picture shows under TvApp, "Device connected" toast, Back → Disconnect works and the receiver comes back.
- Video: Safari/YouTube cast shows TvVideoScreen; remote play/pause/seek; Back stops.
- Audio: Music shows TvAudioScreen with art.
- PIN: with "Require a code" on, the PIN screen appears and pairing completes.
