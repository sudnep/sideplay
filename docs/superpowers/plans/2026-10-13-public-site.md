# Sideplay Public Website Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a small static website for Sideplay at https://sideplay.luklalogic.ai (home, setup, privacy, support, 404) and point the Play listing at it.

**Architecture:** Hand-written HTML pages and one stylesheet in `site/`, served as static assets by a Cloudflare Worker (no Worker script) with a custom-domain route, exactly like latch.luklalogic.ai. A zero-dependency Node test (`node --test`) checks every page's structure, links, metadata and privacy promises.

**Tech Stack:** HTML, CSS, a few lines of inline JS; Cloudflare Workers static assets via `npx wrangler`; Node 24 built-in test runner; headless Chrome for rendering the social card and screenshots.

**Spec:** `docs/superpowers/specs/2026-10-13-public-site-design.md`

## Global Constraints

- Domain: `sideplay.luklalogic.ai`; canonical URLs are `https://sideplay.luklalogic.ai/<path>`.
- No analytics, no cookies, no third-party scripts, no web fonts, no external stylesheets. Only same-origin `<script>` (inline) is allowed.
- Wrangler config: `name: "sideplay-site"`, `assets.directory: "./site"`, `not_found_handling: "404-page"`, route `{ "pattern": "sideplay.luklalogic.ai", "custom_domain": true }`.
- Colours: indigo `#1e1b4b`, coral `#ff6b4a`, peach `#ffd2c7`; text contrast WCAG AA in light and dark.
- Every page footer carries: "Sideplay is not affiliated with or endorsed by Apple Inc. AirPlay, iPhone and iPad are trademarks of Apple Inc."
- Name usage: "Sideplay" or "Sideplay for AirPlay"; never implies Apple endorsement.
- Works from 320 px wide with no horizontal scroll; theme follows `prefers-color-scheme`, toggle overrides and persists (`localStorage` in try/catch), applied before first paint.
- Play listing URL: `https://play.google.com/store/apps/details?id=io.github.sudnep.sideplay`. Support email: `support@luklalogic.ai`. Source: `https://github.com/sudnep/sideplay`.
- Deploys are manual (`npx wrangler deploy`); nothing is deployed before Task 4.

## Review Focus

1. A link to a page or image that doesn't exist (typo in a path) → every internal `href`/`src` must resolve to a file in `site/` (test in Task 1, extended in Task 2).
2. A third-party script, font or tracker slipping in (e.g. copying latch's GA block) → the site must stay "no tracking" (test in Task 1).
3. A page missing its canonical/description/OG tags or the Apple disclaimer → every page must carry them (test in Task 1, applied to all pages in Task 2).
4. Unknown URLs → visitors get the 404 page with HTTP 404, not a blank or 200 (checked in Task 3 locally and Task 4 live).
5. Privacy page drifting from `docs/privacy-policy.md` (different effective date or contact) → both must state the same date and email (test in Task 2).

---

## File structure

| Path | Responsibility |
|---|---|
| `site/_style.css` | All styling: tokens (light/dark), layout, header/footer, hero, cards, doc pages |
| `site/index.html` | Home |
| `site/setup/index.html` | Setup and troubleshooting |
| `site/privacy/index.html` | Privacy policy (HTML copy of `docs/privacy-policy.md`) |
| `site/support/index.html` | Support |
| `site/404.html` | Not found |
| `site/img/` | `icon-512.png`, `favicon.png` (64), `apple-touch-icon.png` (180), `tv-ready.png`, `tv-settings.png`, `og-card.jpg` |
| `site/badges/google-play.png` | Official "Get it on Google Play" badge |
| `site/robots.txt`, `site/sitemap.xml`, `site/_headers`, `site/.assetsignore` | Crawling and edge config |
| `wrangler.jsonc` | Cloudflare static-assets deployment |
| `tools/site/site.test.mjs` | Structural tests for the site (not published) |
| `docs/store/src/og-card-1200x630.html` | Source for the social card image |

---

### Task 1: Test harness, stylesheet, assets and home page

**Files:**
- Create: `tools/site/site.test.mjs`, `site/_style.css`, `site/index.html`, `site/img/*`, `site/badges/google-play.png`, `docs/store/src/og-card-1200x630.html`

**Interfaces:**
- Produces: `PAGES` array in `tools/site/site.test.mjs` (Task 2 appends to it); CSS classes used by every page: `.top`, `.wrap`, `.brand`, `.nav`, `.theme-toggle`, `.hero`, `.lede`, `.btn`, `.card`, `.grid`, `.steps`, `.tv`, `.doc`, `.muted`, `.foot`.

- [ ] **Step 1: Write the failing test**

Create `tools/site/site.test.mjs`:

```js
// Structural checks for the static site in site/. Run: node --test tools/site/
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";

const ROOT = new URL("../../site/", import.meta.url).pathname;
const ORIGIN = "https://sideplay.luklalogic.ai";
// path on disk -> canonical URL path
export const PAGES = [
  ["index.html", "/"],
];
const DISCLAIMER = "Sideplay is not affiliated with or endorsed by Apple Inc. AirPlay, iPhone and iPad are trademarks of Apple Inc.";

const read = (p) => readFileSync(join(ROOT, p), "utf8");
const attrs = (html, tag, attr) =>
  [...html.matchAll(new RegExp(`<${tag}\\b[^>]*\\s${attr}="([^"]+)"`, "gi"))].map((m) => m[1]);

for (const [file, path] of PAGES) {
  test(`${file}: head metadata`, () => {
    const html = read(file);
    assert.match(html, /^<!doctype html>/i);
    assert.match(html, /<html lang="en">/);
    assert.match(html, /<meta name="viewport" content="width=device-width, initial-scale=1">/);
    assert.match(html, /<title>[^<]{10,70}<\/title>/);
    assert.match(html, /<meta name="description" content="[^"]{50,170}">/);
    assert.ok(html.includes(`<link rel="canonical" href="${ORIGIN}${path}">`), "canonical");
    assert.ok(html.includes(`<meta property="og:url" content="${ORIGIN}${path}">`), "og:url");
    assert.ok(html.includes(`<meta property="og:image" content="${ORIGIN}/img/og-card.jpg">`), "og:image");
    assert.match(html, /<meta name="twitter:card" content="summary_large_image">/);
    assert.match(html, /<link rel="icon" href="\/img\/favicon.png">/);
  });

  test(`${file}: no tracking, no third-party code`, () => {
    const html = read(file);
    for (const src of attrs(html, "script", "src")) assert.fail(`external script ${src}`);
    for (const href of attrs(html, "link", "href")) {
      assert.ok(href.startsWith("/") || href.startsWith(ORIGIN), `external link tag ${href}`);
    }
    assert.doesNotMatch(html, /googletagmanager|gtag\(|google-analytics|plausible|fonts\.googleapis|document\.cookie/i);
  });

  test(`${file}: footer disclaimer and theme toggle`, () => {
    const html = read(file);
    assert.ok(html.includes(DISCLAIMER), "Apple disclaimer");
    assert.match(html, /<button class="theme-toggle" type="button"/);
    assert.match(html, /localStorage\.getItem\('sideplay-theme'\)/);
  });

  test(`${file}: every internal link and image resolves`, () => {
    const html = read(file);
    const refs = [...attrs(html, "a", "href"), ...attrs(html, "img", "src"), ...attrs(html, "link", "href")]
      .map((u) => (u.startsWith(ORIGIN) ? u.slice(ORIGIN.length) || "/" : u))
      .filter((u) => u.startsWith("/"))
      .map((u) => u.split(/[?#]/)[0]);
    assert.ok(refs.length > 0);
    for (const u of refs) {
      const onDisk = u.endsWith("/") ? join(u, "index.html") : u;
      assert.ok(existsSync(join(ROOT, onDisk)), `missing ${u} (from ${file})`);
    }
  });

  test(`${file}: images have size and alt`, () => {
    const html = read(file);
    for (const img of html.match(/<img\b[^>]*>/g) ?? []) {
      assert.match(img, /\swidth="\d+"/, img);
      assert.match(img, /\sheight="\d+"/, img);
      assert.match(img, /\salt="/, img);
    }
  });
}

test("home: Play badge links to the listing", () => {
  const html = read("index.html");
  assert.ok(html.includes('href="https://play.google.com/store/apps/details?id=io.github.sudnep.sideplay"'));
  assert.ok(html.includes('"@type": "SoftwareApplication"'));
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tools/site/`
Expected: FAIL — `ENOENT ... site/index.html`.

- [ ] **Step 3: Assets**

```bash
mkdir -p site/img site/badges
M=fastlane/metadata/android/en-US/images
cp $M/icon.png site/img/icon-512.png
sips -z 64 64 $M/icon.png --out site/img/favicon.png
sips -z 180 180 $M/icon.png --out site/img/apple-touch-icon.png
cp $M/tvScreenshots/1_ready.png site/img/tv-ready.png
cp $M/tvScreenshots/2_settings.png site/img/tv-settings.png
curl -fsSL -o site/badges/google-play.png "https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
sips -g pixelWidth -g pixelHeight site/badges/google-play.png
```

The badge is Google's official artwork (use unmodified, per Google's badge guidelines); it has transparent padding, so it is displayed at height 60 with `margin-left:-10px`. Record its pixel size for the `<img>` width/height (expected 646×250; display 155×60).

Create `docs/store/src/og-card-1200x630.html` (same artwork as the feature graphic, card size):

```html
<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:#1e1b4b}svg{display:block}</style></head><body>
<svg width="1200" height="630" viewBox="0 0 1200 630"><rect width="1200" height="630" fill="#1e1b4b"/>
<g transform="translate(70 150) scale(3)"><g transform="rotate(-12 36 54)"><rect x="22" y="28" width="28" height="52" rx="7" fill="#ff6b4a"/><rect x="31" y="32" width="10" height="4" rx="2" fill="#1e1b4b"/></g>
<path d="M61 37 L85 54 L61 71 Z" fill="#ffd2c7" stroke="#ffd2c7" stroke-width="5" stroke-linejoin="round"/></g>
<text x="420" y="300" font-family="Helvetica, Arial" font-weight="700" font-size="110" fill="#ffffff">Sideplay</text>
<text x="424" y="370" font-family="Helvetica, Arial" font-size="36" fill="#c7c4f0">Your iPhone and iPad on your Android TV</text>
<text x="424" y="420" font-family="Helvetica, Arial" font-size="28" fill="#8f8bc4">Free · for AirPlay mirroring and video</text></svg></body></html>
```

Render it and convert to JPEG:

```bash
C="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
"$C" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --window-size=1200,630 \
  --screenshot="$PWD/site/img/og-card.png" "file://$PWD/docs/store/src/og-card-1200x630.html"
sips -s format jpeg -s formatOptions 85 site/img/og-card.png --out site/img/og-card.jpg && rm site/img/og-card.png
```

View `site/img/og-card.jpg` and check no text is clipped.

- [ ] **Step 4: Stylesheet**

Create `site/_style.css`:

```css
/* Sideplay site. Colours from the app icon: indigo #1e1b4b, coral #ff6b4a, peach #ffd2c7. System fonts only. */
:root {
  --bg: #F6F5FB; --bg-2: #ECEAF6; --card: #FFFFFF; --ink: #17153A; --muted: #55527A;
  --line: rgba(23, 21, 58, 0.12); --accent: #C2391B; --fill: #1e1b4b; --on-fill: #FFFFFF;
  --tv: #0B0A16;
  --sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  color-scheme: light dark;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --bg: #12111C; --bg-2: #1A1830; --card: #1F1D35; --ink: #EDEBFA; --muted: #A9A6C8;
    --line: rgba(237, 235, 250, 0.12); --accent: #FF8A70; --fill: #ffd2c7; --on-fill: #1e1b4b; --tv: #05040B;
  }
}
:root[data-theme="dark"] {
  --bg: #12111C; --bg-2: #1A1830; --card: #1F1D35; --ink: #EDEBFA; --muted: #A9A6C8;
  --line: rgba(237, 235, 250, 0.12); --accent: #FF8A70; --fill: #ffd2c7; --on-fill: #1e1b4b; --tv: #05040B;
}

* { box-sizing: border-box; }
html { -webkit-text-size-adjust: 100%; }
body { margin: 0; background: var(--bg); color: var(--ink); font: 17px/1.5 var(--sans); overflow-x: hidden; -webkit-font-smoothing: antialiased; }
img { display: block; max-width: 100%; height: auto; }
a { color: inherit; }
a:focus-visible, button:focus-visible { outline: 3px solid var(--accent); outline-offset: 3px; border-radius: 6px; }
.wrap { width: 100%; max-width: 1080px; margin: 0 auto; padding: 0 16px; }
@media (min-width: 720px) { .wrap { padding: 0 32px; } }
.muted { color: var(--muted); }

/* Header */
.top { position: sticky; top: 0; z-index: 10; background: color-mix(in srgb, var(--bg) 88%, transparent); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); border-bottom: 1px solid var(--line); }
.top .wrap { display: flex; align-items: center; justify-content: space-between; height: 60px; gap: 12px; }
.brand { display: flex; align-items: center; gap: 10px; text-decoration: none; font-weight: 650; }
.brand img { width: 30px; height: 30px; border-radius: 8px; }
.top-right { display: flex; align-items: center; gap: 18px; }
.nav { display: flex; gap: 18px; font-size: 15px; }
.nav a { text-decoration: none; color: var(--muted); }
.nav a:hover, .nav a[aria-current="page"] { color: var(--ink); }
.theme-toggle { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 50%; border: 1px solid var(--line); background: var(--card); color: var(--ink); cursor: pointer; }
.theme-toggle .i-sun { display: none; }
:root[data-theme="dark"] .theme-toggle .i-sun { display: block; }
:root[data-theme="dark"] .theme-toggle .i-moon { display: none; }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .theme-toggle .i-sun { display: block; }
  :root:not([data-theme="light"]) .theme-toggle .i-moon { display: none; }
}
@media (max-width: 420px) { .nav .hide-sm { display: none; } }

/* Type */
h1, h2, h3 { line-height: 1.1; letter-spacing: -0.025em; margin: 0; font-weight: 700; }
h1 { font-size: clamp(36px, 7vw, 64px); }
h2 { font-size: clamp(28px, 4.5vw, 40px); }
h3 { font-size: 20px; letter-spacing: -0.015em; }
h1 em, h2 em { font-style: normal; color: var(--accent); }
.lede { font-size: 20px; line-height: 1.45; color: var(--muted); max-width: 38ch; margin: 18px 0 0; }
section { padding: 72px 0; }
@media (max-width: 640px) { section { padding: 52px 0; } }
.section-head { max-width: 640px; margin-bottom: 32px; }
.section-head p { color: var(--muted); margin: 12px 0 0; }

/* Hero */
.hero { padding: 56px 0 24px; display: grid; gap: 40px; align-items: center; }
@media (min-width: 900px) { .hero { grid-template-columns: 1fr 1.15fr; padding-top: 80px; } }
.cta { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; margin-top: 28px; }
.cta .play img { height: 60px; width: auto; margin-left: -10px; }
.btn { display: inline-block; padding: 11px 18px; border-radius: 999px; background: var(--fill); color: var(--on-fill); text-decoration: none; font-weight: 600; font-size: 15px; }
.btn.ghost { background: transparent; color: var(--ink); border: 1px solid var(--line); }
.small { font-size: 14px; }

/* TV frame around screenshots */
.tv { background: var(--tv); padding: 10px; border-radius: 14px; box-shadow: 0 30px 60px -30px rgba(30, 27, 75, 0.55); }
.tv img { border-radius: 6px; }
.tv-stand { width: 28%; height: 10px; margin: 0 auto; background: var(--tv); border-radius: 0 0 10px 10px; opacity: 0.85; }

/* Cards and grids */
.grid { display: grid; gap: 16px; }
@media (min-width: 720px) { .grid.two { grid-template-columns: 1fr 1fr; } .grid.three { grid-template-columns: repeat(3, 1fr); } }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 18px; padding: 22px; }
.card p { margin: 8px 0 0; color: var(--muted); }
.steps { list-style: none; counter-reset: s; padding: 0; margin: 0; display: grid; gap: 16px; }
@media (min-width: 720px) { .steps { grid-template-columns: repeat(3, 1fr); } }
.steps li { counter-increment: s; background: var(--card); border: 1px solid var(--line); border-radius: 18px; padding: 22px; }
.steps li::before { content: counter(s); display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; background: var(--fill); color: var(--on-fill); font-weight: 700; margin-bottom: 12px; }
.band { background: var(--bg-2); }

/* Document pages */
.doc { max-width: 720px; margin: 0 auto; padding: 48px 16px 72px; }
.doc h1 { font-size: clamp(32px, 6vw, 48px); }
.doc h2 { font-size: 24px; margin: 36px 0 8px; }
.doc h3 { margin: 24px 0 6px; }
.doc ul, .doc ol { padding-left: 22px; }
.doc li { margin: 6px 0; }
.doc table { width: 100%; border-collapse: collapse; font-size: 15px; }
.doc th, .doc td { text-align: left; padding: 8px; border-bottom: 1px solid var(--line); vertical-align: top; }
.doc .card { margin: 24px 0; }
.doc code { font-size: 0.92em; background: var(--bg-2); padding: 1px 5px; border-radius: 5px; }

/* Footer */
.foot { border-top: 1px solid var(--line); padding: 32px 0 48px; font-size: 14px; color: var(--muted); }
.foot nav { display: flex; flex-wrap: wrap; gap: 18px; margin-bottom: 12px; }
.foot a { color: var(--muted); }
.foot p { margin: 6px 0; }
```

- [ ] **Step 5: Home page**

Create `site/index.html`. The `<head>` block, header and footer below are the shared template every later page copies verbatim, changing only `<title>`, description, canonical/og:url, og:title and the `aria-current` nav item.

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Sideplay · AirPlay receiver for Android TV</title>
<meta name="description" content="Free AirPlay receiver for Android TV and Google TV. Mirror your iPhone or iPad, or send videos and music to the big screen. No ads, no account, no tracking.">
<link rel="canonical" href="https://sideplay.luklalogic.ai/">
<meta property="og:title" content="Sideplay · AirPlay receiver for Android TV">
<meta property="og:description" content="Mirror your iPhone or iPad on your Android TV, or send videos and music to it. Free and open source.">
<meta property="og:image" content="https://sideplay.luklalogic.ai/img/og-card.jpg">
<meta property="og:url" content="https://sideplay.luklalogic.ai/">
<meta property="og:type" content="website">
<meta name="twitter:card" content="summary_large_image">
<meta name="theme-color" content="#F6F5FB" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#12111C" media="(prefers-color-scheme: dark)">
<link rel="icon" href="/img/favicon.png">
<link rel="apple-touch-icon" href="/img/apple-touch-icon.png">
<link rel="stylesheet" href="/_style.css?v=20261013">
<script>
  // Before first paint: apply a saved theme so the page doesn't flash the wrong one.
  try { var t = localStorage.getItem('sideplay-theme'); if (t === 'light' || t === 'dark') document.documentElement.dataset.theme = t; } catch (e) {}
</script>
<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  "name": "Sideplay for AirPlay",
  "operatingSystem": "Android TV",
  "applicationCategory": "MultimediaApplication",
  "offers": { "@type": "Offer", "price": "0", "priceCurrency": "USD" },
  "url": "https://sideplay.luklalogic.ai/",
  "downloadUrl": "https://play.google.com/store/apps/details?id=io.github.sudnep.sideplay",
  "publisher": { "@type": "Organization", "name": "Lukla Logic LLC" }
}
</script>
</head>
<body>
<header class="top">
  <div class="wrap">
    <a class="brand" href="/"><img src="/img/icon-512.png" width="30" height="30" alt="">Sideplay</a>
    <div class="top-right">
      <nav class="nav" aria-label="Main">
        <a href="/setup/">Setup</a>
        <a href="/support/" class="hide-sm">Support</a>
      </nav>
      <button class="theme-toggle" type="button" aria-label="Switch to dark mode" title="Switch appearance">
        <svg class="i-moon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>
        <svg class="i-sun" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>
      </button>
    </div>
  </div>
</header>

<main>
  <div class="wrap hero">
    <div>
      <h1>Your iPhone on your <em>Android TV</em>.</h1>
      <p class="lede">Sideplay turns your Android TV or Google TV into an AirPlay receiver. Mirror your screen, or send videos and music from your iPhone or iPad.</p>
      <div class="cta">
        <a class="play" href="https://play.google.com/store/apps/details?id=io.github.sudnep.sideplay"><img src="/badges/google-play.png" width="155" height="60" alt="Get it on Google Play"></a>
        <a class="btn ghost" href="/setup/">How to set it up</a>
      </div>
      <p class="muted small">Free and open source. No ads, no account, no tracking.</p>
    </div>
    <div>
      <div class="tv"><img src="/img/tv-ready.png" width="1920" height="1080" alt="Sideplay on a TV: Living Room TV (AirPlay) is ready, with steps to connect an iPhone or iPad"></div>
      <div class="tv-stand"></div>
    </div>
  </div>

  <section>
    <div class="wrap">
      <div class="section-head"><h2>Connect in three steps</h2><p>Your phone and TV need to be on the same Wi-Fi network.</p></div>
      <ol class="steps">
        <li><h3>Open Sideplay on the TV</h3><p class="muted">It shows the name your iPhone will see, like "Living Room TV (AirPlay)".</p></li>
        <li><h3>Open Control Center</h3><p class="muted">On your iPhone or iPad, swipe down from the top-right corner and tap Screen Mirroring.</p></li>
        <li><h3>Choose your TV</h3><p class="muted">Your screen appears on the TV. In apps like YouTube or Photos, tap the AirPlay icon to send just the video.</p></li>
      </ol>
    </div>
  </section>

  <section class="band">
    <div class="wrap">
      <div class="section-head"><h2>What it does</h2></div>
      <div class="grid three">
        <div class="card"><h3>Screen Mirroring</h3><p>Show your whole iPhone or iPad screen on the TV, in portrait or landscape.</p></div>
        <div class="card"><h3>AirPlay video</h3><p>Send a video from YouTube, Photos or Safari. It plays on the TV while your phone stays free.</p></div>
        <div class="card"><h3>Music</h3><p>AirPlay audio plays through the TV with the album art and track title.</p></div>
        <div class="card"><h3>Optional code</h3><p>Turn on an on-screen code so only people in the room can connect.</p></div>
        <div class="card"><h3>Ready when the TV is</h3><p>Sideplay can start with the TV, so it's there whenever you want it.</p></div>
        <div class="card"><h3>Private by design</h3><p>Everything stays on your home network. Read the <a href="/privacy/">privacy policy</a>.</p></div>
      </div>
    </div>
  </section>

  <section>
    <div class="wrap grid two" style="align-items:center">
      <div>
        <h2>Simple settings</h2>
        <p class="muted">Rename the TV, require a code to connect, start with the TV, and switch video formats if mirroring stutters.</p>
        <p><a class="btn" href="/setup/">Setup and troubleshooting</a></p>
      </div>
      <div class="tv"><img src="/img/tv-settings.png" width="1920" height="1080" loading="lazy" alt="Sideplay settings on a TV: TV name, require a code to connect, start when the TV turns on, use H.265"></div>
    </div>
  </section>

  <section class="band">
    <div class="wrap section-head">
      <h2>Free and open source</h2>
      <p>Sideplay is free software under the GPL-3.0 licence, built on UxPlay and jqssun/android-airplay-server. <a href="https://github.com/sudnep/sideplay">Read the source on GitHub</a>.</p>
    </div>
  </section>
</main>

<footer class="foot">
  <div class="wrap">
    <nav aria-label="Footer"><a href="/setup/">Setup</a><a href="/support/">Support</a><a href="/privacy/">Privacy</a><a href="https://github.com/sudnep/sideplay">GitHub</a></nav>
    <p>© 2026 Lukla Logic LLC</p>
    <p>Sideplay is not affiliated with or endorsed by Apple Inc. AirPlay, iPhone and iPad are trademarks of Apple Inc.</p>
  </div>
</footer>
<script>
  (function () {
    var root = document.documentElement, btn = document.querySelector('.theme-toggle');
    var media = window.matchMedia('(prefers-color-scheme: dark)');
    function isDark() { return root.dataset.theme ? root.dataset.theme === 'dark' : media.matches; }
    function sync() { btn.setAttribute('aria-label', isDark() ? 'Switch to light mode' : 'Switch to dark mode'); }
    btn.addEventListener('click', function () {
      root.dataset.theme = isDark() ? 'light' : 'dark';
      try { localStorage.setItem('sideplay-theme', root.dataset.theme); } catch (e) {}
      sync();
    });
    media.addEventListener('change', sync);
    sync();
  })();
</script>
</body>
</html>
```

Note: the home page links to `/setup/`, `/support/` and `/privacy/`, which don't exist until Task 2, so the "internal link resolves" test for `index.html` stays red until then. To keep Task 1 green on its own, create the three directories now with a minimal placeholder `index.html` each containing only `<!doctype html><title>Sideplay</title>` (Task 2 overwrites them; `PAGES` doesn't include them yet so they aren't checked).

- [ ] **Step 6: Run the tests**

Run: `node --test tools/site/`
Expected: PASS (6 tests: 5 per-page + home badge).

- [ ] **Step 7: Commit**

```bash
git add tools/site site docs/store/src/og-card-1200x630.html
git commit -m "feat(site): home page, stylesheet and assets"
```

---

### Task 2: Setup, privacy, support and 404 pages

**Files:**
- Create/overwrite: `site/setup/index.html`, `site/privacy/index.html`, `site/support/index.html`, `site/404.html`
- Modify: `tools/site/site.test.mjs` (extend `PAGES`, add privacy-sync test)

**Interfaces:**
- Consumes: Task 1's shared head/header/footer/theme-script template and CSS classes (`.doc`, `.card`, `.muted`).
- Produces: final page list for Task 3's sitemap: `/`, `/setup/`, `/privacy/`, `/support/`.

- [ ] **Step 1: Extend the tests (failing)**

In `tools/site/site.test.mjs` replace `PAGES` with:

```js
export const PAGES = [
  ["index.html", "/"],
  ["setup/index.html", "/setup/"],
  ["privacy/index.html", "/privacy/"],
  ["support/index.html", "/support/"],
  ["404.html", "/404.html"],
];
```

and append:

```js
test("privacy page matches docs/privacy-policy.md", () => {
  const md = readFileSync(new URL("../../docs/privacy-policy.md", import.meta.url), "utf8");
  const html = read("privacy/index.html");
  const date = md.match(/Effective date: (.+)/)[1].trim();
  assert.ok(html.includes(`Effective ${date}`), `effective date ${date}`);
  assert.ok(html.includes("support@luklalogic.ai"));
  assert.match(html, /This website sets no cookies and uses no analytics\./);
  for (const heading of md.matchAll(/^## (.+)$/gm)) assert.ok(html.includes(`<h2>${heading[1]}</h2>`), heading[1]);
});

test("404 page is not indexed", () => {
  assert.match(read("404.html"), /<meta name="robots" content="noindex">/);
});
```

Run: `node --test tools/site/`
Expected: FAIL — the placeholder pages lack metadata, and the privacy test fails.

- [ ] **Step 2: Setup page**

Create `site/setup/index.html` with the Task 1 template (title `Set up Sideplay · AirPlay on Android TV`, description `How to install Sideplay, connect your iPhone or iPad, allow it to open by itself, and fix common problems like a TV that doesn't appear.`, canonical/og:url `https://sideplay.luklalogic.ai/setup/`, `aria-current="page"` on the Setup nav link; no JSON-LD). Body `<main class="doc">`:

```html
<main class="doc">
  <h1>Set up Sideplay</h1>
  <p class="lede">Five minutes, once. After that, your TV shows up whenever you open Control Center.</p>

  <h2>1. Install it on your TV</h2>
  <p>Install <a href="https://play.google.com/store/apps/details?id=io.github.sudnep.sideplay">Sideplay from Google Play</a> on your Android TV or Google TV, or ask the TV's Google Assistant to install "Sideplay for AirPlay". It's free.</p>

  <h2>2. Open it once</h2>
  <p>Open Sideplay from the TV's apps. It shows the name your iPhone will see, like <strong>Living Room TV (AirPlay)</strong>. You can rename it in Settings.</p>

  <h2>3. Let it open by itself (recommended)</h2>
  <p>So the TV can switch to Sideplay when you start casting, allow <strong>Display over other apps</strong>:</p>
  <ol>
    <li>In Sideplay, select <strong>Allow auto-open</strong> if you see it. It takes you straight to the switch.</li>
    <li>Otherwise open the TV's <strong>Settings → Apps → Special app access → Display over other apps</strong> and turn it on for Sideplay.</li>
  </ol>
  <p class="muted">Without it Sideplay still works, but you may need to open it yourself before casting.</p>

  <h2>4. Connect your iPhone or iPad</h2>
  <ol>
    <li>Make sure the phone and TV are on the <strong>same Wi-Fi network</strong>.</li>
    <li>Open Control Center, tap <strong>Screen Mirroring</strong> and choose your TV.</li>
    <li>To send just a video, tap the AirPlay icon in YouTube, Photos or Safari and choose your TV.</li>
  </ol>

  <h2>5. Optional: require a code</h2>
  <p>In Sideplay's Settings, turn on <strong>Require a code to connect</strong>. The TV shows a 4-digit code the first time each phone connects; after that the phone reconnects without it.</p>

  <h2>Troubleshooting</h2>
  <h3>My TV doesn't appear on the iPhone</h3>
  <ul>
    <li>Check both devices are on the same Wi-Fi (not a guest network).</li>
    <li>Open Sideplay on the TV once, then try again.</li>
    <li>Restart the TV's Wi-Fi or the router if it still doesn't appear.</li>
  </ul>
  <h3>Google Drive plays sound but no picture</h3>
  <p>Google Drive only sends audio over AirPlay, to any receiver. Use <strong>Screen Mirroring</strong> for Drive videos instead.</p>
  <h3>The screen stays black while mirroring</h3>
  <p>Stop mirroring on the phone and start it again. If it keeps happening, turn off <strong>Use H.265 (HEVC)</strong> in Sideplay's Settings.</p>
  <h3>Video stops when my phone locks</h3>
  <p>Some apps, like YouTube, pause AirPlay when the phone locks. Videos from Safari and Photos keep playing.</p>
  <p>Still stuck? See <a href="/support/">Support</a>.</p>
</main>
```

- [ ] **Step 3: Privacy page**

Create `site/privacy/index.html` with the Task 1 template (title `Privacy policy · Sideplay`, description `Sideplay collects no personal data: no accounts, no ads, no analytics. What it handles on your TV and why, and what this website does.`, canonical/og:url `https://sideplay.luklalogic.ai/privacy/`). Body is `docs/privacy-policy.md` converted to HTML with these exact headings (`<h2>` per `##`), the table as `<table>`, plus a "This website" section:

```html
<main class="doc">
  <h1>Privacy policy</h1>
  <p class="muted">Sideplay · Lukla Logic LLC · Effective October 4, 2026</p>

  <p>Sideplay is a free, open-source app for Android TV that lets an iPhone or iPad show its screen, videos and music on your TV over AirPlay. This policy explains what happens to your information when you use it.</p>

  <div class="card">
    <h2>The short version</h2>
    <p>Sideplay does not collect, store on any server, sell or share any personal information. It has no accounts, no ads, no analytics and no crash reporting. Everything happens on your home network, between your device and your TV.</p>
  </div>

  <h2>What Sideplay handles, and where it stays</h2>
  <ul>
    <li><strong>What you cast.</strong> The screen, video and audio your device sends are shown on the TV and are never recorded or sent anywhere else. When you AirPlay a video, your device may tell the TV the web address of that video (for example on YouTube's servers). The TV then downloads the video directly from that service, the same way your device would. That service's own privacy policy applies to that download.</li>
    <li><strong>Settings on the TV.</strong> The TV name, the on-screen code setting, the pairing code and the start-with-the-TV setting are saved on the TV only.</li>
    <li><strong>Pairing key.</strong> Sideplay creates a key on the TV so your devices can recognise it. It never leaves the TV.</li>
    <li><strong>Activity log.</strong> Sideplay keeps a short log of connection events on the TV to help with troubleshooting. It stays on the TV and is never sent anywhere.</li>
    <li><strong>Network discovery.</strong> Sideplay announces itself on your local network so your devices can find it. This announcement includes the TV name and network address and stays on your local network.</li>
  </ul>
  <p>Uninstalling Sideplay deletes all of this from the TV.</p>

  <h2>Permissions</h2>
  <p>Sideplay uses these Android permissions only to work as a receiver:</p>
  <table>
    <tr><th>Permission</th><th>Why</th></tr>
    <tr><td>Internet, network and Wi-Fi state, multicast</td><td>Receive AirPlay from devices on your network and announce the TV to them</td></tr>
    <tr><td>Foreground service, wake lock</td><td>Keep receiving while the TV shows other apps</td></tr>
    <tr><td>Run at startup</td><td>Start the receiver when the TV turns on, if you enable it</td></tr>
    <tr><td>Notifications</td><td>Show that the receiver is running and when a device wants to connect</td></tr>
    <tr><td>Display over other apps</td><td>Bring Sideplay to the front when a device starts casting, if you allow it</td></tr>
  </table>

  <h2>This website</h2>
  <p>This website sets no cookies and uses no analytics. It is served by Cloudflare, which processes standard request data (such as IP addresses) to deliver and protect the site.</p>

  <h2>Children</h2>
  <p>Sideplay does not collect information from anyone, including children.</p>

  <h2>Changes</h2>
  <p>If this policy changes, the new version will be published at this address with a new effective date.</p>

  <h2>Contact</h2>
  <p>Questions about this policy: <a href="mailto:support@luklalogic.ai">support@luklalogic.ai</a></p>
</main>
```

Note the test requires every `##` heading in `docs/privacy-policy.md` to appear as `<h2>…</h2>`; "The short version" is inside the card as `<h2>`, which satisfies it. Before writing, re-read `docs/privacy-policy.md` and copy its current text if it differs from the above (the md file is the source of truth). Also add the "This website" section to `docs/privacy-policy.md` (same sentence) so the two stay identical in content.

- [ ] **Step 4: Support page**

Create `site/support/index.html` with the Task 1 template (title `Support · Sideplay`, description `Get help with Sideplay, the free AirPlay receiver for Android TV: setup guide, troubleshooting, email support and bug reports on GitHub.`, canonical/og:url `https://sideplay.luklalogic.ai/support/`, `aria-current="page"` on Support):

```html
<main class="doc">
  <h1>Support</h1>
  <p class="lede">Most problems are covered in the <a href="/setup/">setup and troubleshooting guide</a>.</p>
  <div class="grid two" style="margin-top:28px">
    <div class="card"><h3>Email</h3><p>Write to <a href="mailto:support@luklalogic.ai">support@luklalogic.ai</a>. Tell us your TV model and what you were casting from.</p></div>
    <div class="card"><h3>Report a bug</h3><p>Open an issue on <a href="https://github.com/sudnep/sideplay/issues">GitHub</a>. Sideplay is open source, so you can follow fixes there.</p></div>
  </div>
  <h2>Get Sideplay</h2>
  <p><a href="https://play.google.com/store/apps/details?id=io.github.sudnep.sideplay">Sideplay for AirPlay on Google Play</a> (Android TV and Google TV).</p>
</main>
```

- [ ] **Step 5: 404 page**

Create `site/404.html` with the Task 1 template (title `Page not found · Sideplay`, description `This page doesn't exist. Go back to Sideplay, the free AirPlay receiver for Android TV, or read the setup guide.`, canonical/og:url `https://sideplay.luklalogic.ai/404.html`, plus `<meta name="robots" content="noindex">` in `<head>`):

```html
<main class="doc">
  <h1>Page not found</h1>
  <p class="lede">That page doesn't exist. Try the <a href="/">home page</a> or the <a href="/setup/">setup guide</a>.</p>
</main>
```

- [ ] **Step 6: Run the tests**

Run: `node --test tools/site/`
Expected: PASS (5 pages × 5 + home badge + privacy sync + 404 noindex = 28 tests).

- [ ] **Step 7: Commit**

```bash
git add site tools/site docs/privacy-policy.md
git commit -m "feat(site): setup, privacy, support and 404 pages"
```

---

### Task 3: Crawling, edge config, Wrangler, local check

**Files:**
- Create: `site/robots.txt`, `site/sitemap.xml`, `site/_headers`, `site/.assetsignore`, `wrangler.jsonc`
- Modify: `tools/site/site.test.mjs`, `.gitignore`

**Interfaces:**
- Consumes: Task 2's page list.
- Produces: `wrangler.jsonc` used by Task 4's deploy.

- [ ] **Step 1: Failing tests**

Append to `tools/site/site.test.mjs`:

```js
test("sitemap lists exactly the indexable pages", () => {
  const xml = read("sitemap.xml");
  const locs = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]).sort();
  assert.deepEqual(locs, ["/", "/privacy/", "/setup/", "/support/"].map((p) => ORIGIN + p).sort());
});

test("robots points at the sitemap", () => {
  assert.match(read("robots.txt"), /Sitemap: https:\/\/sideplay\.luklalogic\.ai\/sitemap\.xml/);
});

test("wrangler serves site/ on the custom domain with a 404 page", () => {
  const cfg = JSON.parse(readFileSync(new URL("../../wrangler.jsonc", import.meta.url), "utf8").replace(/^\s*\/\/.*$/gm, ""));
  assert.equal(cfg.name, "sideplay-site");
  assert.equal(cfg.assets.directory, "./site");
  assert.equal(cfg.assets.not_found_handling, "404-page");
  assert.deepEqual(cfg.routes, [{ pattern: "sideplay.luklalogic.ai", custom_domain: true }]);
  assert.equal(cfg.main, undefined);
});
```

Run: `node --test tools/site/` — Expected: FAIL (`ENOENT sitemap.xml`).

- [ ] **Step 2: Files**

`site/robots.txt`:
```
User-agent: *
Allow: /

Sitemap: https://sideplay.luklalogic.ai/sitemap.xml
```

`site/sitemap.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url><loc>https://sideplay.luklalogic.ai/</loc><lastmod>2026-10-13</lastmod></url>
  <url><loc>https://sideplay.luklalogic.ai/setup/</loc><lastmod>2026-10-13</lastmod></url>
  <url><loc>https://sideplay.luklalogic.ai/privacy/</loc><lastmod>2026-10-13</lastmod></url>
  <url><loc>https://sideplay.luklalogic.ai/support/</loc><lastmod>2026-10-13</lastmod></url>
</urlset>
```

`site/_headers`:
```
/*
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
/img/*
  Cache-Control: public, max-age=604800
/badges/*
  Cache-Control: public, max-age=604800
/_style.css
  Cache-Control: public, max-age=3600
```

`site/.assetsignore`:
```
README.md
.DS_Store
```

`wrangler.jsonc`:
```jsonc
{
  // Static site for https://sideplay.luklalogic.ai (no Worker script). Deploy: npx wrangler deploy
  "name": "sideplay-site",
  "compatibility_date": "2026-10-13",
  "assets": {
    "directory": "./site",
    "not_found_handling": "404-page"
  },
  "routes": [{ "pattern": "sideplay.luklalogic.ai", "custom_domain": true }]
}
```

`.gitignore` — append `.wrangler/`.

Run: `node --test tools/site/` — Expected: PASS (31 tests).

- [ ] **Step 3: Local check**

Run `npx wrangler dev --port 8787` in the background, then:

```bash
for p in / /setup/ /privacy/ /support/ /robots.txt /sitemap.xml /img/og-card.jpg; do
  printf "%s %s\n" "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8787$p)" "$p"; done
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8787/nope    # Expected: 404
curl -sI http://localhost:8787/ | grep -i -E 'x-content-type-options|referrer-policy'
```
Expected: 200 for each path, 404 for `/nope`, both headers present.

Screenshots in light and dark at 375 and 1280 px (headless Chrome with `--force-prefers-color-scheme` is not available, so use the toggle's storage: open each page once per theme via a tiny wrapper that sets `localStorage['sideplay-theme']`). Simplest reliable route: temporarily serve and capture with

```bash
SCRATCH=$(mktemp -d)   # or the session scratchpad
C="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
for w in 375 1280; do for p in "" setup privacy support; do
  "$C" --headless=new --disable-gpu --hide-scrollbars --window-size=$w,2200 \
    --screenshot="$SCRATCH/site-$w-${p:-home}-light.png" "http://localhost:8787/$p/"
  "$C" --headless=new --disable-gpu --hide-scrollbars --window-size=$w,2200 --blink-settings=preferredColorScheme=0 \
    --screenshot="$SCRATCH/site-$w-${p:-home}-dark.png" "http://localhost:8787/$p/"
done; done
```

(`preferredColorScheme=0` is dark, `1` is light.) View every screenshot: no horizontal overflow at 375 px, text readable in both themes, TV frames intact, badge not distorted. Fix any CSS issue found, rerun the tests, and stop `wrangler dev`.

- [ ] **Step 4: Commit**

```bash
git add site wrangler.jsonc tools/site .gitignore
git commit -m "feat(site): sitemap, robots, headers and Cloudflare config"
```

---

### Task 4: Deploy and point Play at the site

**Files:**
- Modify: `README.md`, `docs/store/listing.md`
- Memory: `sideplay-hosting` (record the site URL)

- [ ] **Step 1: Deploy** (outward-facing: confirm with the user first)

Run: `npx wrangler deploy`
Expected: `Deployed sideplay-site` with `sideplay.luklalogic.ai (custom domain)`. If it reports the zone isn't found, stop and ask the user (luklalogic.ai must be on this Cloudflare account, as latch is).

- [ ] **Step 2: Live checks**

```bash
for p in / /setup/ /privacy/ /support/ /robots.txt /sitemap.xml; do
  printf "%s %s\n" "$(curl -s -o /dev/null -w '%{http_code}' https://sideplay.luklalogic.ai$p)" "$p"; done
curl -s -o /dev/null -w '%{http_code}\n' https://sideplay.luklalogic.ai/nope   # Expected: 404
curl -sI https://sideplay.luklalogic.ai/ | grep -i -E 'x-content-type-options|referrer-policy'
```
Expected: 200s, 404, both headers. DNS/TLS for a new custom domain can take a few minutes; retry for up to 10 minutes before treating a failure as real.

- [ ] **Step 3: Play listing website**

Set `contactWebsite` with the user's application-default credentials (the service account can't edit listing details yet): `edits.insert` → `edits.details.patch {"contactWebsite": "https://sideplay.luklalogic.ai/"}` (keep `defaultLanguage` `en-US` and `contactEmail` `support@luklalogic.ai`) → `edits.commit`, with header `x-goog-user-project: latch-509922`, via the same Python `urllib` pattern used for earlier listing edits (`SSL_CERT_FILE=/etc/ssl/cert.pem`). Read back `edits.details.get` and confirm the website.

Tell the user to set **Policy and programs → App content → Privacy policy** to `https://sideplay.luklalogic.ai/privacy/` (Console-only).

- [ ] **Step 4: Docs**

- `README.md`: add a line under the title: `Website: https://sideplay.luklalogic.ai · Privacy: https://sideplay.luklalogic.ai/privacy/`.
- `docs/store/listing.md`: privacy policy URL → `https://sideplay.luklalogic.ai/privacy/`; add `Website: https://sideplay.luklalogic.ai/`.
- Update memory `sideplay-hosting.md` with the site URL and "deploy with `npx wrangler deploy` from the repo root".

Run: `node --test tools/site/` — Expected: PASS.

```bash
git add README.md docs/store/listing.md
git commit -m "docs: link the Sideplay website"
```
