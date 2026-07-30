# DESIGN.md Format Specification

A `DESIGN.md` is a single plain-text file that fully captures a website's visual
language so any AI agent can generate on-brand UI from it. It has two parts:

1. **YAML frontmatter** — machine-readable design tokens.
2. **Markdown body** — human-readable prose organized into 10 fixed sections.

The body references tokens from the frontmatter using `{group.token}` interpolation
(e.g. `{colors.primary}`, `{typography.display-xl}`, `{rounded.pill}`). Every
`{...}` reference in the body MUST resolve to a key defined in the frontmatter.

---

## Part 1 — YAML Frontmatter

```yaml
---
version: alpha
name: <Brand>-design-analysis
description: One dense paragraph on a SINGLE line (3–6 sentences) capturing the brand's visual essence — mood, the signature accent WITH its hex inline (#0066cc), type personality, recurring motif, button shape, chrome behavior, and any polarity shifts (light marketing ↔ dark app). The single most important field; an agent reads it first. Plain scalar — NOT a folded `>` block.

colors:
  # semantic-name: "#hex"  — name by ROLE, not by hue. Keep the map FLAT (no nesting).
  # Aim for ~18–30+ roles. Real brands have many; do not collapse to a token 8-pack.
  # ACCENT:  primary, primary-focus / primary-press, primary-on-dark, secondary accents
  primary: "#533afd"
  primary-focus: "#2e2b8c"     # focus-ring / pressed sibling
  primary-on-dark: "#7b8cff"   # accent variant for dark surfaces
  # TEXT:    ink/body + opacity-tiered mutes (name by the %) + on-dark variants
  ink: "#0d253d"               # default body text on light
  body-muted: "#64748d"        # secondary copy
  ink-muted-64: "#5b6b7d"      # opacity-tiered ink (name by the approx %)
  ink-muted-40: "#9aa6b2"
  body-on-dark: "#ffffff"      # text on dark surfaces
  # SURFACE: canvas + named off-whites + dark tiles (carry polarity here, not a 2nd file)
  canvas: "#ffffff"            # default background
  canvas-soft: "#f6f9fc"       # alt section background (name it if iconic, e.g. parchment)
  surface-dark-1: "#0b0d12"    # primary dark tile/app surface
  surface-dark-2: "#12151c"    # micro-step sibling for adjacent dark blocks
  # BORDERS: a soft ring tone + a hard hairline
  divider-soft: "#eef1f5"
  hairline: "#e3e8ee"          # 1px borders
  # ON-* fills
  on-primary: "#ffffff"        # text on primary fills
  on-dark: "#ffffff"
  # add semantic (success/error/warning) tokens when the brand uses them

typography:
  # named SEMANTIC roles (not just t-shirt sizes). Aim for ~12–20 roles spanning
  # display → lead/tagline → body/strong → caption/button/nav → fine-print/micro-legal.
  # letterSpacing in PX (e.g. -0.374px), matching how it reads off the rendered page.
  # Capture rare weights when real (300 airy display, 600 vs 700 headlines); the
  # Principles section then explains the weight ladder (e.g. "500 is absent").
  hero-display:
    fontFamily: "Söhne, 'SF Pro Display', system-ui, sans-serif"
    fontSize: 56px
    fontWeight: 600
    lineHeight: 1.07
    letterSpacing: -0.28px
    fontFeature: ss01      # optional OpenType feature (ss01, tnum, …)
  body:
    fontFamily: "Söhne, system-ui, sans-serif"
    fontSize: 17px
    fontWeight: 400
    lineHeight: 1.47
    letterSpacing: -0.224px

rounded:
  # include none:0 and full; use MEASURED values (5px, 11px, 18px), not idealized 4/8/12.
  none: 0px
  xs: 5px
  sm: 8px
  md: 11px
  lg: 18px
  pill: 9999px
  full: 9999px

spacing:
  # base unit (commonly 4px or 8px) + stepped scale; keep MEASURED oddities (e.g. 17px).
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 17px
  lg: 24px
  xl: 32px
  xxl: 48px
  section: 80px

components:
  # Aim for ~15–30+ entries. Each composes tokens via {group.token} interpolation and
  # may add LITERAL geometry (padding, height, size). Group by type in the body:
  # navigation, buttons (+ variants), cards/containers (+ tiles), inputs, footer.
  # Document state VARIANTS as SEPARATE keys: button-primary-active, *-focus,
  # *-selected, product-tile-dark-2. NEVER document hover — default + active/pressed
  # + focus + disabled only (hover is non-deterministic and the reference omits it).
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.body}"
    rounded: "{rounded.pill}"
    padding: 11px 22px
  button-primary-active:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.pill}"
    # e.g. transform: scale(0.95) — note the press micro-interaction in the body
---
```

### Frontmatter rules
- **Name colors by role** (`primary`, `ink`, `canvas`, `hairline`), never by hue
  (`blue1`, `gray2`). Roles survive a re-theme; hues don't.
- **Go deep, don't collapse.** Real brands have many tokens. Target ~**18–30+ colors**,
  ~**12–20 typography roles**, ~**15–30+ components**. A thin 8-token file is a failed
  distillation. Only dedupe hexes that are within ~2% lightness AND serve the same role.
- **Carry polarity inside `colors:`**, not in a second file: add `*-on-dark` text
  variants and `surface-dark-*` tiles. Tier muted inks by approximate opacity in the
  name (`ink-muted-64`, `ink-muted-40`).
- **Typography roles are SEMANTIC** (`hero-display`, `lead`, `tagline`, `body-strong`,
  `dense-link`, `caption-strong`, `button-large`, `fine-print`, `micro-legal`,
  `nav-link`) — not just t-shirt sizes. Include `fontWeight`, `lineHeight`,
  `letterSpacing` **in px**; add `fontFeature` only when the brand uses it. Capture rare
  real weights (300 / 600 vs 700) so the Principles section can explain the ladder.
- **`rounded` includes `none: 0px` and `full`**; use measured values (5/11/18px), not
  idealized 4/8/12. **`spacing`** keeps measured oddities (e.g. 17px) and a `section`
  step (~64–96px).
- **Components reference tokens, never raw values** — that's what makes the file
  re-themeable. `padding`, `height`, `size` and similar one-off geometry may be literal.
- Document **state variants** (`-active`, `-pressed`, `-focused`, `-selected`, `-2`/`-3`
  surface micro-steps) as their own component keys. **Do not create `-hover` component
  keys** — hover is non-deterministic. You may mention a signature hover *behavior*
  briefly in component prose (a lift, a glow), but the documented STATES are default +
  active/pressed + focus + disabled.

---

## Part 2 — Markdown Body (11 sections, in this exact order)

### 1. `## Overview`
2–4 prose paragraphs on the brand's design philosophy, color strategy, and type
personality (use **bold** inline emphasis). End with a **`**Key Characteristics:**`**
bulleted list (6–8 bullets) naming the signature, instantly-recognizable traits, each
citing token refs (e.g. `{colors.primary}`) and the accent hex.

### 2. `## Colors`
Lead with a `> **Source pages analyzed:**` blockquote naming the URLs/surfaces.
Group swatches under `### Brand & Accent`, `### Surface`, `### Text`,
`### Hairlines & Borders`, and **either** `### Brand Gradient` (the gradient/mesh
recipe) **or** a one-line note that the brand uses no decorative gradients. Add
`### Semantic` if the brand has status colors. Each line:
`- **Friendly Name** (\`{colors.x}\` — #hex): role, where it appears, any alpha note.`

### 3. `## Typography`
- `### Font Family` — Display vs Text/UI faces, fallbacks.
- `### Hierarchy` — a table: `| Token | Size | Weight | Line Height | Letter Spacing | Use |`.
- `### Principles` — bullets on the brand's typographic rules (weight ladder, tracking
  habits, body-size convention, any deliberately-absent weight).
- `### Note on Font Substitutes` — the **open-source substitute** for any proprietary
  face (e.g. Inter for SF Pro / Söhne) and concrete tweaks to approximate it.

### 4. `## Layout`
`### Spacing System` (base unit + tokens), `### Grid & Container` (max width,
column counts, collapse breakpoints), `### Whitespace Philosophy`.

### 5. `## Elevation & Depth`
A level table (`| Level | Treatment | Use |`) from flat up, with **real shadow/blur
values**. Follow with a **`**Shadow philosophy.**`** paragraph and a
`### Decorative Depth` subsection (gradient mesh, glow borders, backdrop-blur, surface
alternation — whatever supplies depth beyond shadows).

### 6. `## Shapes`
`### Border Radius Scale` table and `### Photography Geometry` (image crops,
aspect ratios, treatment — or that the brand favors product mockups over photos).

### 7. `## Components`
Organize by type: `### Top Navigation`, `### Buttons`, `### Cards & Containers`,
`### Inputs & Forms`, `### Footer` (add `### Signature Components` for hard-to-miss
motifs). Document each component as a bold key — `**\`button-primary\`**` — then prose
giving its composing token refs (`{colors.x}`/`{typography.x}`/`{rounded.x}`), literal geometry, and **default + active/focus**
states. No hover.

### 8. `## Do's and Don'ts`
Two bulleted lists, `### Do` and `### Don't`, with concrete guardrails an agent
can follow ("Reserve `{colors.primary}` for one filled CTA per band").

### 9. `## Responsive Behavior`
`### Breakpoints` table (`| Name | Width | Key Changes |`), `### Touch Targets`,
`### Collapsing Strategy`, and `### Image Behavior`.

### 10. `## Iteration Guide`
A short numbered list telling an agent how to use the file: focus on one component at a
time, reference tokens by name, variants live as separate keys, never document hover,
respect the non-negotiable signature motifs.

### 11. `## Known Gaps`
An honest bulleted list of what the analysis could NOT observe or formalize: states not
surfaced (error/validation), dark-mode counterparts not seen, dynamic/rotating content,
platform-dependent blur radii, content assets that aren't design tokens. **Required** —
it signals a measured, non-fabricated spec.

---

## Companion file: `preview.html`
Each `DESIGN.md` may ship with a self-contained `preview.html` that renders the spec
**in the brand's own tokens** — grouped color swatches, the full type scale, spacing +
radius scales, a live Light/Dark preview pane, and live component samples (buttons,
links, card, input, pills). It is the visual proof of the spec.

`preview.html` is **not hand-written per brand**. It is one canonical, data-driven
template (`reference/preview-template.html`) whose shell, CSS, and renderer are byte-
identical for every website — only an injected JSON token block differs. The template
already provides the **sticky top nav** (brand label, quick-jump anchors, **Source ↗**
link, and a **light/dark toggle** with `localStorage`) and stays dependency-free. To
produce one, copy the template and replace only its `<script id="design-data">` JSON
with tokens derived from this file's frontmatter (see SKILL.md → "Generate the token
preview" for the schema).
