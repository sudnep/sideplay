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
  ["setup/index.html", "/setup/"],
  ["privacy/index.html", "/privacy/"],
  ["support/index.html", "/support/"],
  ["404.html", "/404.html"],
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
