---
version: alpha
name: <Brand>-design-analysis
description: <One dense paragraph on a SINGLE line — mood, the signature accent WITH its hex inline, type personality, recurring motif, button shape, chrome behavior, and any light/dark polarity shift. Plain scalar, not a `>` block.>

colors:
  # ~18–30+ FLAT roles. Carry polarity here (no second file). Tier mutes by ~opacity.
  primary: "#______"
  primary-focus: "#______"
  primary-on-dark: "#______"
  on-primary: "#ffffff"
  ink: "#______"            # default text on light
  body-muted: "#______"
  ink-muted-64: "#______"
  ink-muted-40: "#______"
  body-on-dark: "#ffffff"
  canvas: "#ffffff"
  canvas-soft: "#______"    # name it if iconic (e.g. parchment)
  surface-dark-1: "#______"
  surface-dark-2: "#______"
  divider-soft: "#______"
  hairline: "#______"
  on-dark: "#ffffff"
  # accents / semantic (success / error / warning) as the brand uses them

typography:
  # ~12–20 SEMANTIC roles. letterSpacing in px. Capture rare real weights.
  hero-display:
    fontFamily: "<Display>, system-ui, sans-serif"
    fontSize: 56px
    fontWeight: 600
    lineHeight: 1.07
    letterSpacing: -0.28px
  display-lg:
    fontFamily: "<Display>, system-ui, sans-serif"
    fontSize: 40px
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: 0px
  lead:
    fontFamily: "<Display>, system-ui, sans-serif"
    fontSize: 28px
    fontWeight: 400
    lineHeight: 1.14
    letterSpacing: 0.2px
  tagline:
    fontFamily: "<Display>, system-ui, sans-serif"
    fontSize: 21px
    fontWeight: 600
    lineHeight: 1.19
    letterSpacing: 0.2px
  body-strong:
    fontFamily: "<Text>, system-ui, sans-serif"
    fontSize: 17px
    fontWeight: 600
    lineHeight: 1.24
    letterSpacing: -0.224px
  body:
    fontFamily: "<Text>, system-ui, sans-serif"
    fontSize: 17px
    fontWeight: 400
    lineHeight: 1.47
    letterSpacing: -0.224px
  caption:
    fontFamily: "<Text>, system-ui, sans-serif"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.43
    letterSpacing: -0.224px
  button-utility:
    fontFamily: "<Text>, system-ui, sans-serif"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.29
    letterSpacing: -0.224px
  fine-print:
    fontFamily: "<Text>, system-ui, sans-serif"
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.3
    letterSpacing: -0.12px
  nav-link:
    fontFamily: "<Text>, system-ui, sans-serif"
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.0
    letterSpacing: -0.12px
  # add display-md, lead-airy, caption-strong, button-large, dense-link, micro-legal …

rounded:
  none: 0px
  xs: 5px
  sm: 8px
  md: 11px
  lg: 18px
  pill: 9999px
  full: 9999px

spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 17px
  lg: 24px
  xl: 32px
  xxl: 48px
  section: 80px

components:
  # ~15–30+ entries. Variants are SEPARATE keys. NEVER document hover.
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
  button-secondary:
    backgroundColor: transparent
    textColor: "{colors.primary}"
    typography: "{typography.body}"
    rounded: "{rounded.pill}"
    padding: 11px 22px
  text-link:
    backgroundColor: transparent
    textColor: "{colors.primary}"
    typography: "{typography.body}"
  text-link-on-dark:
    backgroundColor: transparent
    textColor: "{colors.primary-on-dark}"
    typography: "{typography.body}"
  global-nav:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.nav-link}"
    height: 44px
  card:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: 24px
  text-input:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: 12px 16px
  footer:
    backgroundColor: "{colors.canvas-soft}"
    textColor: "{colors.ink-muted-64}"
    typography: "{typography.fine-print}"
    padding: 64px
  # + brand-specific tiles, chips, sticky bars, configurator cells, etc.
---

## Overview

<2–4 paragraphs (with **bold** emphasis) on philosophy, color strategy, type
personality, and how surfaces/polarity behave.>

**Key Characteristics:**
- <signature trait, citing token refs like `{colors.primary}` and the accent hex>
- <signature trait>
- <signature trait>
- <signature trait>
- <signature trait>
- <signature trait>

## Colors

> **Source pages analyzed:** <urls / surfaces inspected>

### Brand & Accent
- **<Friendly Name>** (`{colors.primary}` — #______): role / where it appears.

### Surface
- **Canvas** (`{colors.canvas}` — #______): default background.
- **<Soft surface>** (`{colors.canvas-soft}` — #______): alt section background.
- **<Dark tile>** (`{colors.surface-dark-1}` — #______): primary dark surface.

### Text
- **Ink** (`{colors.ink}` — #______): default body text.
- **Body Muted** (`{colors.body-muted}` — #______): secondary copy.
- **Body On Dark** (`{colors.body-on-dark}` — #______): text on dark surfaces.

### Hairlines & Borders
- **Divider Soft** (`{colors.divider-soft}` — #______): soft ring tone.
- **Hairline** (`{colors.hairline}` — #______): 1px borders.

### Brand Gradient
<the gradient/mesh recipe — OR a one-line note that the brand uses no decorative gradients.>

### Semantic
<success / error / warning, or note the brand has none>

## Typography

### Font Family
<Display face vs Text/UI face, fallbacks.>

### Hierarchy

| Token | Size | Weight | Line Height | Letter Spacing | Use |
|---|---|---|---|---|---|
| `{typography.hero-display}` | 56px | 600 | 1.07 | -0.28px | Hero headline |
| `{typography.display-lg}` | 40px | 600 | 1.1 | 0px | Section heading |
| `{typography.lead}` | 28px | 400 | 1.14 | 0.2px | Lead / subcopy |
| `{typography.body}` | 17px | 400 | 1.47 | -0.224px | Default body |
| `{typography.caption}` | 14px | 400 | 1.43 | -0.224px | Caption / button |
| `{typography.fine-print}` | 12px | 400 | 1.3 | -0.12px | Fine print |
| `{typography.nav-link}` | 12px | 400 | 1.0 | -0.12px | Nav items |

### Principles
- <weight ladder; tracking habits; body-size convention; any absent weight>

### Note on Font Substitutes
<open-source substitute for any proprietary face, plus concrete tweaks (tracking,
line-height, font-feature-settings) to approximate the original.>

## Layout

### Spacing System
- **Base unit**: <4px / 8px>.
- **Tokens**: `{spacing.xxs}` 4px · `{spacing.xs}` 8px · `{spacing.sm}` 12px · `{spacing.md}` 17px · `{spacing.lg}` 24px · `{spacing.xl}` 32px · `{spacing.xxl}` 48px · `{spacing.section}` 80px.

### Grid & Container
<max width, columns, gutters, collapse breakpoints>

### Whitespace Philosophy
<density / air>

## Elevation & Depth

| Level | Treatment | Use |
|---|---|---|
| Flat | No shadow, no border | <default surfaces> |
| Soft | `box-shadow: ...` / 1px border | <cards> |
| Floating | `backdrop-filter: blur(...)` | <sticky bars> |

**Shadow philosophy.** <how/when shadow is used — or that it is largely avoided.>

### Decorative Depth
- <gradient mesh / glow borders / backdrop blur / surface alternation>

## Shapes

### Border Radius Scale

| Token | Value | Use |
|---|---|---|
| `{rounded.none}` | 0px | Full-bleed / square |
| `{rounded.sm}` | 8px | Compact utility |
| `{rounded.md}` | 11px | <use> |
| `{rounded.lg}` | 18px | Cards |
| `{rounded.pill}` | 9999px | Pills / primary CTA |
| `{rounded.full}` | 9999px | Circular controls |

### Photography Geometry
<crops, aspect ratios, treatment — or product mockups / illustration over photos>

## Components

### Top Navigation
**`global-nav`** — <composing tokens, geometry, default/active states.>

### Buttons
**`button-primary`** — <tokens, geometry, active/focus states. No hover.>
**`button-secondary`** — <…>

### Cards & Containers
**`card`** — <…>

### Inputs & Forms
**`text-input`** — <…; note if error/validation states were not surfaced>

### Footer
**`footer`** — <…>

### Signature Components
<the brand's hard-to-miss motifs as named component keys>

## Do's and Don'ts

### Do
- <guardrail citing tokens>

### Don't
- <anti-pattern>

## Responsive Behavior

### Breakpoints

| Name | Width | Key Changes |
|---|---|---|
| Desktop | ≥ 1024px | <changes> |
| Tablet | 768–1023px | <changes> |
| Mobile | < 768px | <changes> |

### Touch Targets
<min sizes>

### Collapsing Strategy
<how the layout collapses>

### Image Behavior
<srcset / art-direction / aspect ratios across breakpoints>

## Iteration Guide

1. Focus on ONE component at a time; reference its key directly.
2. Variants (`-active`, `-focus`, `-2`) live as separate `components:` keys.
3. Use token references (`{colors.x}`, `{rounded.x}`) everywhere — never inline hex. No hover keys.
4. Default body to `{typography.body}`.
5. Respect the non-negotiable signature motifs above.

## Known Gaps

- <states not surfaced (error/validation)>
- <dark-mode counterparts not observed>
- <dynamic/rotating content that varies per surface>
- <platform-dependent values (blur radii) not formalized as tokens>
- <content assets (photography) that are not design tokens>
