# Realistic Demo Guide — DESIGN.md → a believable single-file website/app

This is the recipe for the on-demand demo offered after a `DESIGN.md` is written.
The output is **one `.html` file, zero external dependencies**, that looks like a
**real product page** (or a **real mobile app screen**) — not a swatch sheet.

> `preview.html` documents the design system (swatches, type scale, component
> samples). `demo.html` *uses* the system to ship a page that could pass for the
> real site. Build the second one only when the user asks.

## Hard rules (the "no dependencies" contract)
- **One file.** All CSS in a single `<style>`; all JS in a single inline `<script>`.
- **No network.** No CDN links, no `<link href="https://…">`, no external fonts,
  no hot-linked images, no analytics. Opening it offline must look identical.
- **Fonts**: use the brand's stack with system fallbacks ending in
  `-apple-system, system-ui, sans-serif` (or `…, serif` / `…, monospace`). If the
  brand font is proprietary, the system fallback is fine — it's a demo, not a pixel
  match. (If you truly need the exact face, inline a base64 `@font-face` — but
  prefer the fallback to keep the file small.)
- **Imagery**: inline SVG, CSS gradients, and emoji only. No `<img src="http…">`.
  Album art, avatars, hero visuals → CSS gradients or simple inline SVG.
- **Tokens are the source of truth.** Define every `DESIGN.md` token as a CSS
  custom property in `:root` and build everything from `var(--…)`. Don't invent
  colors or sizes that aren't in the spec.

## Pick a form factor
Ask if unclear; default to **Web**.

### Web — responsive landing page
A vertical marketing page, max-width container, that scrolls like a real site:
1. **Sticky top nav** — wordmark left, links center, sign-in + primary CTA right;
   condenses/blurs on scroll.
2. **Hero** — eyebrow, big display headline (use the top type token), sub-paragraph,
   primary + secondary CTA, and the brand's signature visual (gradient mesh, glow,
   product mockup) built in CSS/SVG.
3. **Social proof** — a muted row of "as seen in" logos (inline SVG wordmarks or
   styled text).
4. **Feature section** — a 3-up grid of cards using the card component tokens, each
   with an icon (inline SVG), title, and body.
5. **Pricing** — 2–3 tiers with the featured tier styled per the spec.
6. **Testimonial / metric band** — a quote or big-number strip.
7. **CTA band** — full-width, on-brand background, one strong CTA.
8. **Footer** — multi-column links, social icons, legal row.

Responsive: collapse the nav to a hamburger and grids to one column under ~768px.

### Mobile — a real app screen in a phone frame
A centered phone frame (rounded ~44px corners, subtle bezel/notch, status bar with
time + battery) wrapping a believable app screen for that brand:
- **Top bar** — title or logo, action icons.
- **Body** — the brand's actual surface: a feed, list, cards, player, dashboard,
  or onboarding — whatever fits the product. Use real-looking content.
- **Bottom** — a tab bar (3–5 inline-SVG icons, one active in the brand accent) or a
  sticky primary action / now-playing bar.
Keep it to one screen; optionally show two frames side by side (e.g. list + detail).

## Make it feel real (not a template)
- **Plausible product + copy.** Invent a believable product name and benefit-driven
  copy in the brand's voice — never "Lorem ipsum", never "Feature One / Feature Two".
- **Honor the signature motif** from the spec's Overview/Signature Components — it's
  what makes the page recognizable (Stripe's mesh, Linear's glow hairline, Spotify's
  green play button).
- **Respect the brand's rules** from Do's & Don'ts — e.g. one filled CTA per section,
  accent used sparingly, correct text-on-accent color.
- **Micro-interactions** — a small inline script for nav-on-scroll, hover lifts, a
  tab switch, or a mobile toggle. Subtle, not flashy.
- **Depth & polish** — use the elevation tokens for real shadows; align to the
  spacing scale; round with the radius scale.

## Save
- Save next to the `DESIGN.md`: `demo.html` (web) or `demo-mobile.html` (mobile).
  Both can coexist.

## Verify
- Open via double-click / `file://` (or a static server) — it must render fully
  **offline** with **no console errors** and no failed network requests.
- It should read like a real site/app in the brand's language at a glance.
- Resize to mobile width (Web) to confirm the responsive collapse.
