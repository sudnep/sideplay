# AirPlay video capture matrix — Hisense

Date: 2026-10-04  ·  Build: Sideplay 0.1.0 + patch 0007 (+ Plan 2 UI)  ·  iPhone: model and iOS not recorded (AirPlay client `AirPlay/980.77.1`)

Type key (spec 5.1): **A** = direct http(s) URL → ExoPlayer · **B** = FCUP HLS (`mlhls://…/master.m3u8`) · **C** = other (`/photo`, unhandled) · **M** = iOS chose screen mirroring instead (no `/play`)

| Source | Type | Play | Pause | Seek | Stop | Key evidence (AIRCAP lines) | Player error |
|---|---|---|---|---|---|---|---|
| safari-mp4 | **M** | ✅ (mirrored) | ✅ | ✅ | ✅ | Choosing Living Room TV in the Safari player's AirPlay menu started `mirror running: 1`; no `/play` | — |
| safari-hls | not run | | | | | | |
| youtube (app, AirPlay menu) | **B** | ✅ picture + sound | ✅ | ✅ | ✅ (`POST /stop`) | `AIRCAP PLAY proc=YouTube … mlhls://localhost/master.m3u8`; resumes (`start=46.1`, `start=114.3`); 0 bad start codes | none |
| google-drive-video | **B** | ⚠️ audio only, picture spins | — | — | ✅ | `proc=YouTube` (Drive uses YouTube's player); video decoder starts 10 s late, 22× `PesReader: Unexpected start code prefix` | none logged |
| youtube-music | **B** | ✅ picture (track video) + sound | — | — | — | `AIRCAP PLAY proc=YouTubeMusic … master.m3u8` | none |
| photos-picture | **M** | ✅ swipe follows | n/a | n/a | ✅ | `mirror running: 1`; **0** `PUT /photo` requests | — |
| photos-video | **M** | ✅ picture + sound | ✅ | — | ✅ | still mirroring; no `/play` | — |
| vimeo | not run | | | | | | |
| music (Spotify) | audio | ✅ Sideplay music screen: art, title, artist · album | — | — | — | `Audio format: ct=2 spf=352 screen=false` | — |
| netflix / appletv | not run | | | | | (user has no subscriptions) | |

Captures (redacted): `captures/*.txt`.

## Unhandled requests seen
None. Every `AIRCAP REQ` in the session was `handled=1`. No `PUT /photo` was ever sent: on this receiver iOS mirrors Photos instead.

## Observations for Plan 3
1. **Google Drive video (type B) has broken video, working audio.** The YouTube app over the same HLS path is clean, so the problem is specific to Drive's streams (itag segments rewritten by UxPlay's HLS proxy): malformed TS segments (`Unexpected start code prefix`) and a 10 s decoder start. Matches jqssun #49. Smallest next step: capture one Drive master/media playlist and segment through the proxy and compare with a YouTube one; check UxPlay's `YT-EXT-CONDENSED-URI` / playlist rewrite for Drive's variant.
2. **Type A (direct URL) was never exercised.** iOS chose mirroring for Safari MP4. Either current iOS prefers mirroring for Safari's player, or our advertised features don't satisfy what Safari needs for URL handoff. Compare our `/info`/TXT feature bits with an Apple TV and AirReceiver (Fire TV on this LAN, AFTKA-209) before touching `/play` code.
3. **Photos never uses `/photo`.** It works via mirroring, so the spec's planned `/photo` handler is not needed for current iOS on this receiver. Drop it from Plan 3 unless a different sender uses it.
4. **The YouTube cast menu is confusing.** The TV's built-in Chromecast and Sideplay are both called "Living Room TV". Consider a default receiver name such as "Living Room TV (AirPlay)".
5. **Switching apps during YouTube AirPlay with mirroring on** sends `POST /stop` and goes back to mirroring. That's iOS behaviour, not a receiver bug.
