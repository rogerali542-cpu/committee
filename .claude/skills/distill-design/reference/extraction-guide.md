# Extraction Guide — Live Site → DESIGN.md tokens

How to turn a real website into accurate, measured design tokens. The goal is
**fidelity**: values you read off the rendered page, not values you guess. A
generic-looking DESIGN.md is a failed DESIGN.md.

## Tooling, in strict priority order
Always drive a **real headless browser** so values are *measured*, not guessed.
Try these in order and stop at the first that works:

1. **Playwright (preferred)** — script Chromium via Bash and read computed styles
   directly. This is the gold path. If the browsers aren't installed, install them
   first (`npx -y playwright install chromium`, or `bunx playwright install chromium`),
   then run a small script (`npx` and `bunx` are interchangeable everywhere below):
   ```bash
   node -e '
   const { chromium } = require("playwright");
   (async () => {
     const b = await chromium.launch();
     const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
     await p.goto("https://example.com", { waitUntil: "networkidle" });
     const read = (sel, props) => p.$eval(sel, (el, props) => {
       const s = getComputedStyle(el);
       return Object.fromEntries(props.map(k => [k, s[k]]));
     }, props).catch(() => null);
     console.log(JSON.stringify({
       hero: await read("h1", ["color","fontFamily","fontSize","fontWeight","lineHeight","letterSpacing"]),
       body: await read("p",  ["color","fontSize","lineHeight","letterSpacing"]),
       cta:  await read("a.button, button", ["backgroundColor","color","borderRadius","padding"]),
       nav:  await read("nav", ["backgroundColor","backdropFilter","height","borderColor"]),
     }, null, 2));
     await b.close();
   })();'
   ```
   Sweep heros, body text, buttons, cards, nav, inputs, and footer; also screenshot
   (`await p.screenshot({ path: "..." })`) so you can Read it for layout/motifs.
2. **Puppeteer (fallback)** — same approach if Playwright is unavailable: `require("puppeteer")`,
   `page.goto`, `page.$eval(sel, el => getComputedStyle(el).…)`. Install with
   `npx -y puppeteer browsers install chrome` (or `bunx`) if needed. Identical output quality.
3. **Browser MCP (Chrome / Preview), if already connected** — navigate, screenshot,
   and run `getComputedStyle` through the MCP. Also measured; use it when a browser
   MCP is live and you'd rather not shell out.
4. **WebFetch / fetch (LAST RESORT ONLY)** — returns the page as markdown/text. Use it
   ONLY when none of the above can run. It will NOT give computed pixel values, so you
   must combine it with brand knowledge and **label the result an *inspired
   interpretation*** in the Colors source note and `## Known Gaps`.

**Supplementary (use alongside any tier above, not instead of measuring):**
- **Official design-token sources** — many brands publish real tokens: a public
  design-system repo (e.g. `coinbase/cds`, `primer` for GitHub), a Tailwind config, a
  `theme.ts`/`tokens.json`, or CSS custom properties in the page's stylesheet. These give
  *authoritative* hex/type values — search the brand's GitHub org and inspect CSS vars.
- **Brand knowledge** — for well-known brands you may already know the palette and type.
  Still verify against the live site or its published tokens.

Inspect **multiple page types**, not just the homepage: marketing home, pricing,
docs, and the app/dashboard if reachable. Many brands flip polarity (light
marketing → dark app) — capture both.

## What to pull, in order

### Colors
- Read computed `color` / `background-color` / `border-color` across heros,
  body text, cards, buttons, nav, and footer.
- Sort into roles: **brand/primary**, **accents**, **surface** (canvas, soft,
  elevated), **text** (ink, secondary, muted, on-primary), **hairline/border**,
  **semantic** (success/error/warning) if present.
- Note the **CTA logic**: which color is reserved for the one filled action per
  section. Over-using the primary is the most common fidelity error.
- Dedupe near-identical hexes into one role.

### Typography
- Read `font-family` (capture the real proprietary face AND the fallback stack).
- Build the scale from the rendered sizes: hero/display → headings → body →
  caption → micro. For each capture `font-size`, `font-weight`, `line-height`,
  `letter-spacing`.
- Watch for **signatures**: thin display weights, heavy negative tracking,
  tabular numerals (`tnum`) on numeric/money UI, stylistic sets (`ss01`),
  uppercase eyebrows with positive tracking.
- Pick an **open-source substitute** for any proprietary font (Inter, Geist,
  IBM Plex, Space Grotesk, etc.) and state how to approximate the original.

### Shape & spacing
- Read `border-radius` on buttons, inputs, cards → build the radius scale
  (note pill = 9999px if buttons are fully round).
- Infer the **spacing base unit** (usually 4px or 8px) from padding/margins and
  build the stepped scale.

### Elevation
- Read `box-shadow` values on cards and floating panels → build the level table.
- Identify any **non-shadow depth**: gradient meshes, glow borders, backdrop
  blur, layered surfaces.

### Components
For buttons, cards, inputs, nav, pills: record background, text color,
typography role, radius, padding, border, and every **state** (hover, pressed,
focused, disabled, featured). Express each as a composition of the tokens above.

### Signature motifs
The 2–3 things that make the brand instantly recognizable — Stripe's gradient
mesh, Linear's subtle glow borders and grain, Spotify's pill-everything green.
These go in `### Signature Components` and the `**Key Characteristics**` list.

## Quality bar
- **Depth matches a real brand**: ~18–30+ colors, ~12–20 typography roles,
  ~15–30+ components. A thin 8-token file is a failed distillation.
- Every component value is a `{token}` reference, not a raw literal.
- Every `{token}` in the body resolves to a frontmatter key.
- Colors are role-named and deduped; polarity carried via `*-on-dark` + `surface-dark-*`.
- `letterSpacing` is in px; rare real weights captured; **no `hover`** anywhere.
- All 11 body sections present, including the required `## Known Gaps`.
- Proprietary fonts have an open-source substitute (in `### Note on Font Substitutes`).
- The `description` (one inline line) would let someone picture the site with eyes closed.
- A skeptical reviewer comparing the file to the live site finds the hexes right.

## Naming
Use the brand name lowercased and URL-safe for the folder (`stripe`, `linear`,
`together.ai`). Set frontmatter `name:` to `<Brand>-design-analysis`. When values are
interpreted (WebFetch-only) rather than measured, say so in the Colors source note and
`## Known Gaps` instead of changing the name.
