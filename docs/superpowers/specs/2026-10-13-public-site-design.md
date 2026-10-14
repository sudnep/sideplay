# Sideplay public website — design

Date: 2026-10-13 · Status: approved in chat, awaiting spec review

## Goal

A small public website for Sideplay at **https://sideplay.luklalogic.ai**, in the style of latch.luklalogic.ai. It gives Play Store visitors a place to understand the app and get it working, and it becomes the home of the privacy policy and the Play listing's website link.

Success: the Play listing's website and privacy-policy links point at the site, and a visitor from the Play Store can install Sideplay and connect their iPhone using only the site.

## Decisions

| Topic | Decision |
|---|---|
| Domain | `sideplay.luklalogic.ai` (luklalogic.ai zone is on the same Cloudflare account as latch) |
| Hosting | Cloudflare Worker with static assets only (no Worker script), deployed by hand with `npx wrangler deploy`, like latch |
| Source | `site/` in this repo; `wrangler.jsonc` at the repo root |
| Stack | Hand-written HTML + one `_style.css` + a few lines of inline JS for the theme; no framework, no build step, no package.json |
| Analytics | None. No cookies, no third-party scripts, no web fonts |
| Terms page | None (free GPL app) |

## Pages

| Path | Content |
|---|---|
| `/` | Hero ("Your iPhone and iPad on your Android TV"), Google Play badge, TV screenshots (ready, Settings), 3 connect steps, what works (Screen Mirroring, AirPlay video from YouTube/Photos/Safari, music with album art, optional on-screen code, starts with the TV), "Free, open source (GPL-3.0), no ads, no tracking", GitHub link |
| `/setup/` | Install from Play; open once; allow **Display over other apps** (Settings → Apps → Special app access → Display over other apps; with the in-app button as the first route); phone and TV on the same Wi-Fi; optional PIN; troubleshooting: TV not listed, Google Drive sends audio only (use Screen Mirroring), black screen while mirroring (stop and start mirroring again) |
| `/privacy/` | `docs/privacy-policy.md` as HTML, plus: "This website sets no cookies and uses no analytics." Same effective-date pattern |
| `/support/` | support@luklalogic.ai, GitHub issues link, Play listing link, short FAQ pointer to `/setup/` |
| `/404.html` | Not-found page with links home and to setup |
| `/robots.txt`, `/sitemap.xml` | Allow all; sitemap lists the four pages |

Every page: canonical URL, title and description, Open Graph/Twitter tags with a 1200×630 card image, theme-color, favicon from the app icon. Home also has JSON-LD `SoftwareApplication` (free, Android TV).

## Look

- Latch's structure (header with brand + nav + theme toggle, content, footer) with Sideplay's colours as CSS custom properties: indigo `#1e1b4b`, coral `#ff6b4a`, peach `#ffd2c7`, plus light/dark surface and text tokens chosen for WCAG AA contrast.
- System font stack; light and dark themes from `prefers-color-scheme`, overridden by the toggle (saved in `localStorage`, wrapped in try/catch; applied in `<head>` before first paint so the theme never flashes).
- Works from 320 px wide; no horizontal scroll; images have explicit width/height and `loading="lazy"` below the fold.
- Header/footer copied into each page (as latch does). Footer: © Lukla Logic LLC, Privacy, Support, GitHub, and "Sideplay is not affiliated with or endorsed by Apple Inc. AirPlay, iPhone and iPad are trademarks of Apple Inc."
- Naming follows the app's brand rule: "Sideplay" / "Sideplay for AirPlay"; never implies Apple endorsement.

## Assets

Reused from the repo: `fastlane/metadata/android/en-US/images/` (icon, feature graphic, TV screenshots). New: `og-card.jpg` (1200×630, rendered from the feature-graphic source the same way as the store graphics), favicon PNG/SVG from the icon, and Google's official "Get it on Google Play" badge.

## Edge config

- `site/_headers`: long cache for `/img/*` and `/badges/*`; 1 hour for `_style.css` (cache-busted with `?v=`); `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`.
- `site/.assetsignore`: exclude any README from publishing.
- `wrangler.jsonc`: `name: "sideplay-site"`, `assets.directory: "./site"`, `not_found_handling: "404-page"`, route `{ "pattern": "sideplay.luklalogic.ai", "custom_domain": true }`, `compatibility_date` current.

## Launch follow-ups

1. Set the Play listing's website (`contactWebsite`) to the site via the Play API.
2. The user changes the privacy-policy URL in Play Console to `https://sideplay.luklalogic.ai/privacy/` (Console-only).
3. `docs/privacy-policy.md` stays the source text; the site page is generated from it by hand and both carry the same effective date. README links to the site.

## Testing

- Local: `npx wrangler dev`; every page in light and dark at 375 px and 1280 px (headless Chrome screenshots), theme toggle persists, no console errors.
- Link check across all pages (internal links resolve, external links return 200).
- After deploy: each URL returns 200 over HTTPS; an unknown path returns the 404 page with status 404; response headers match `_headers`.

## Out of scope

Waitlist/D1, email sending, guides/blog, other languages, analytics, App Store badge (no iOS app).
