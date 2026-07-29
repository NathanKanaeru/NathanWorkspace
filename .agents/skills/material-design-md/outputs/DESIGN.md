# NathanWorkspace

---
version: alpha
name: NathanWorkspace
description: Clean, modern, and simple but elegant
colors:
  primary: "#a6c8ff"
  surfaceTint: "#a6c8ff"
  onPrimary: "#02315e"
  primaryContainer: "#234776"
  onPrimaryContainer: "#d5e3ff"
  secondary: "#bdc7dc"
  onSecondary: "#273141"
  secondaryContainer: "#3d4758"
  onSecondaryContainer: "#d9e3f8"
  tertiary: "#dabde2"
  onTertiary: "#3d2846"
  tertiaryContainer: "#553f5d"
  onTertiaryContainer: "#f7d8ff"
  error: "#ffb4ab"
  onError: "#690005"
  errorContainer: "#93000a"
  onErrorContainer: "#ffdad6"
  background: "#111318"
  onBackground: "#e1e2e9"
  surface: "#111318"
  onSurface: "#e1e2e9"
  surfaceVariant: "#43474e"
  onSurfaceVariant: "#c4c6cf"
  outline: "#8d9199"
  outlineVariant: "#43474e"
  shadow: "#000000"
  scrim: "#000000"
  inverseSurface: "#e1e2e9"
  inverseOnSurface: "#2e3035"
  inversePrimary: "#3d5f90"
  primaryFixed: "#d5e3ff"
  onPrimaryFixed: "#001c3b"
  primaryFixedDim: "#a6c8ff"
  onPrimaryFixedVariant: "#234776"
  secondaryFixed: "#d9e3f8"
  onSecondaryFixed: "#121c2b"
  secondaryFixedDim: "#bdc7dc"
  onSecondaryFixedVariant: "#3d4758"
  tertiaryFixed: "#f7d8ff"
  onTertiaryFixed: "#27142f"
  tertiaryFixedDim: "#dabde2"
  onTertiaryFixedVariant: "#553f5d"
  surfaceDim: "#111318"
  surfaceBright: "#37393e"
  surfaceContainerLowest: "#0c0e13"
  surfaceContainerLow: "#191c20"
  surfaceContainer: "#1d2024"
  surfaceContainerHigh: "#282a2f"
  surfaceContainerHighest: "#32353a"
typography:
  display-large:
    fontFamily: Inter
    fontSize: 57px
    fontWeight: 400
    lineHeight: 1.1
  display-medium:
    fontFamily: Inter
    fontSize: 48px
    fontWeight: 400
    lineHeight: 1.1
  display-small:
    fontFamily: Inter
    fontSize: 40px
    fontWeight: 400
    lineHeight: 1.1
  headline-large:
    fontFamily: Inter
    fontSize: 33px
    fontWeight: 400
    lineHeight: 1.2
  headline-medium:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: 400
    lineHeight: 1.2
  headline-small:
    fontFamily: Inter
    fontSize: 23px
    fontWeight: 400
    lineHeight: 1.2
  title-large:
    fontFamily: Inter
    fontSize: 19px
    fontWeight: 500
    lineHeight: 1.2
  title-medium:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: 500
    lineHeight: 1.2
  title-small:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.2
  body-large:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.5
  body-medium:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.5
  body-small:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: 400
    lineHeight: 1.5
  label-large:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.4
  label-medium:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: 500
    lineHeight: 1.4
  label-small:
    fontFamily: Inter
    fontSize: 9px
    fontWeight: 500
    lineHeight: 1.4
rounded:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  full: 9999px
spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 48px
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.onPrimary}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    padding: "{spacing.md}"
    height: "40px"
  button-primary-hover:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.onPrimary}"
    stateLayer: "{colors.onPrimary}"
    stateLayerOpacity: "8%"
  button-primary-pressed:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.onPrimary}"
    stateLayer: "{colors.onPrimary}"
    stateLayerOpacity: "10%"
  button-primary-disabled:
    backgroundColor: "{colors.onSurface}"
    backgroundOpacity: "12%"
    textColor: "{colors.onSurface}"
    textOpacity: "38%"
  button-secondary:
    backgroundColor: "{colors.secondaryContainer}"
    textColor: "{colors.onSecondaryContainer}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    padding: "{spacing.md}"
    height: "40px"
  button-secondary-hover:
    backgroundColor: "{colors.secondaryContainer}"
    textColor: "{colors.onSecondaryContainer}"
    stateLayer: "{colors.onSecondaryContainer}"
    stateLayerOpacity: "8%"
  button-text:
    backgroundColor: "transparent"
    textColor: "{colors.primary}"
    typography: "{typography.label-large}"
    rounded: "{rounded.full}"
    padding: "{spacing.sm}"
    height: "40px"
  button-text-hover:
    backgroundColor: "transparent"
    textColor: "{colors.primary}"
    stateLayer: "{colors.primary}"
    stateLayerOpacity: "8%"
  input-field:
    backgroundColor: "{colors.surfaceContainerHighest}"
    textColor: "{colors.onSurface}"
    typography: "{typography.body-large}"
    rounded: "{rounded.xs}"
    padding: "{spacing.md}"
    height: "56px"
    outlineColor: "{colors.outline}"
    outlineWidth: "1px"
  input-field-focused:
    backgroundColor: "{colors.surfaceContainerHighest}"
    textColor: "{colors.onSurface}"
    outlineColor: "{colors.primary}"
    outlineWidth: "2px"
  input-field-error:
    backgroundColor: "{colors.surfaceContainerHighest}"
    textColor: "{colors.error}"
    outlineColor: "{colors.error}"
    outlineWidth: "2px"
  input-field-disabled:
    backgroundColor: "{colors.onSurface}"
    backgroundOpacity: "4%"
    textColor: "{colors.onSurface}"
    textOpacity: "38%"
    outlineColor: "{colors.onSurface}"
    outlineOpacity: "12%"
    outlineWidth: "1px"
  card:
    backgroundColor: "{colors.surfaceContainerLow}"
    textColor: "{colors.onSurface}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"
  chip:
    backgroundColor: "{colors.surfaceContainerLow}"
    textColor: "{colors.onSurfaceVariant}"
    typography: "{typography.label-large}"
    rounded: "{rounded.sm}"
    padding: "{spacing.sm}"
    height: "32px"
  chip-selected:
    backgroundColor: "{colors.secondaryContainer}"
    textColor: "{colors.onSecondaryContainer}"
  tooltip:
    backgroundColor: "{colors.inverseSurface}"
    textColor: "{colors.inverseOnSurface}"
    typography: "{typography.body-small}"
    rounded: "{rounded.xs}"
    padding: "{spacing.sm}"
---

## Overview

NathanWorkspace is a developer's environment, not a consumer toy. Every pixel is pared to what matters — code, output, navigation — and nothing more. The aesthetic is clean to the point of being invisible: dark surfaces recede so that your work stays foreground, while a restrained blue accent (`primary`, `#a6c8ff`) appears only where a decision is needed, never for decoration.

The interface is built for focus sessions and long terminal hours. No glossy surfaces, no decorative flourishes, no animation for its own sake. The dark scheme (`background` settles at `#111318`) is the default because most coding happens at night or in low-light environments, and a luminous interface would fight the user's concentration. The few color accents — slate (`secondary`, `#bdc7dc`) for metadata, lavender (`tertiary`, `#dabde2`) for rare highlights — are desaturated so they sit quietly beside syntax-highlighted code without competing.

Image content, where it appears, follows the same rule: minimalist photography with clean compositions, high signal-to-noise ratio. Every element that survives the edit earns its place.

- **Main color scheme:** Dark
- **Design framework:** Material Design 3
- **Iconography:** Material Symbols, Rounded
- **Image style:** Minimalist photography with clean compositions

## Colors

The palette orbits a single origin — deep navy (`#1e3a5f`) — which the M3 algorithm expands into a full 49-role system. The result is a restrained, professional palette that never feels playful or trendy.

- **Sky blue** (`primary`, `#a6c8ff`) is the single interactive accent. It appears on buttons, focus indicators, and links — never on surfaces or backgrounds. In light mode, the same role deepens to a more assertive `#3d5f90`.
- **Slate** (`secondary`, `#bdc7dc`) carries utilitarian text: captions, metadata, secondary actions. It is the quiet workhorse of the system.
- **Lavender** (`tertiary`, `#dabde2`) is reserved for rare highlights — badges, experimental features, or anything that needs to be noticed without triggering urgency.
- **Red** (`error`, `#ffb4ab`) follows the M3 error convention: used only for destructive actions and validation failures.
- **Surfaces** step through five tonal levels from `surfaceContainerLowest` (`#0c0e13`) to `surfaceContainerHighest` (`#32353a`). In light mode, these invert to a warm paper scale from `#ffffff` down to `#e1e2e9`. The progression is engineered to feel like stacking cards on a desk — each layer reads as physically closer, never as a separate color.

The fixed color variants (`primaryFixed`, `secondaryFixed`, `tertiaryFixed`) stay constant across light and dark modes, ensuring brand-owned elements like the product logo or status badges never shift hue.

## Typography

One typeface, two roles. **Inter** serves at every level — its even rhythm and generous x-height make it equally comfortable at 57px display sizes and 11px label sizes. Using a single family eliminates the pairing friction that plagues multi-font systems and enforces visual consistency across the interface.

The hierarchy is conventional M3: display levels (`display-large`, `57px`) are for hero moments like welcome screens or empty states; headlines (`headline-large`, `33px`) introduce sections; titles (`title-medium`, `16px`, weight 500) label cards, dialogs, and nav destinations. Body text (`body-large`, `16px`) and its smaller siblings (`body-medium`, `13px`; `body-small`, `11px`) carry content at weight 400 — light enough to read comfortably at length when set in Inter's open counters.

Labels (`label-large` through `label-small`) are the only levels that shift tracking: they gain weight (500) but remain the same font family, so they stand out without introducing a new visual voice. Letter-spacing is left at Inter's default — the font is metrically balanced for its natural tracking, and forcing uppercase or wider spacing would work against its design.

## Layout

The layout is fluid on mobile devices, transitioning to a maximum content width of 1200px on larger screens. Components align to a four-column grid on narrow viewports and a twelve-column grid on wide ones, with 16px (`spacing.md`) gutters throughout.

The spacing scale follows the geometric progression set by `spacing.md` (16px): `xs` (4px) for the tightest inner padding within dense UIs like toolbars or status bars; `sm` (8px) for chip and tag interiors; `md` (16px) for card padding and section gutters; `lg` (24px) for separating major regions; and `xl` (48px) for page-level margins and large empty states. Every distance is a named step — arbitrary px values are not used.

Containers and cards hold their children at `spacing.md` internal padding. When nested, each card indents by one spacing step to create an implied tree structure without relying on lines or borders.

## Elevation & Depth

NathanWorkspace uses tonal layering, not shadows, to convey depth. The M3 surface container system defines five elevation levels: the page sits on `surfaceContainerLowest` (`#0c0e13`); cards and panels lift to `surfaceContainerLow` (`#191c20`); modals, menus, and pickers stand on `surfaceContainer` (`#1d2024`); dialogs and bottom sheets on `surfaceContainerHigh` (`#282a2f`); and the highest interactive elements — snackbars, tooltips — on `surfaceContainerHighest` (`#32353a`).

The progression is subtle — each step is roughly 12–14% brighter in luminance than the one below. The eye registers the depth as physical stacking without needing shadow to reinforce it. Shadows (`shadow`, `#000000`) exist in the token set but are reserved for the system chrome (window manager decorations) and never used inside the application layout.

Tonal elevation keeps the interface feeling flat in the best sense — no drop shadows competing with code indentation guides, no floating buttons casting shadows on the editor surface. What matters is hierarchy, not spectacle.

## Shapes

The shape language is architectural — minimal rounding throughout, because sharpness signals precision and professional tooling. The scale starts at `rounded.xs` (4px) for inputs and tooltips — just enough to soften the hardest corner without losing the industrial edge. Cards and panels use `rounded.md` (12px), a deliberate middle ground that reads as intentional but not decorative.

Buttons are the sole exception: they take `rounded.full` (9999px) to read unambiguously as pressable targets. This is a functional distinction, not an aesthetic one — the pill shape signals affordance at a glance, which matters when the primary action button shares the screen with code that has similar color and weight.

Chips use `rounded.sm` (8px), between inputs and cards. Every radius is drawn from the named scale — no arbitrary values — so that a button, a card, and an input on the same screen share a mathematical relationship even though their radii differ.

## Components

Components follow M3's tonal hierarchy with a developer-tool sensibility: the primary action on any screen is the one that saves state or triggers execution, and it earns the filled button treatment (`button-primary` on `primary` container with `label-large` typography). Supporting actions use tonal buttons (`button-secondary` on `secondaryContainer`), and dismissible actions are text-only (`button-text`).

All buttons are pill-shaped (height 40px, `rounded.full`) and use `label-large` font for consistent sizing. Disabled states reduce background opacity to 12% of `onSurface` and text opacity to 38% — the element is still legible but clearly out of play.

Input fields are filled (`surfaceContainerHighest` as the track) rather than outlined, keeping the visual noise low. The resting state shows a 1px bottom edge in `outline`; on focus, the edge widens to 2px and shifts to `primary`. On error, it shifts to `error`. The field height (56px) exceeds the M3 default to accommodate code input at `body-large` size without crowding.

Cards are containers at `surfaceContainerLow` with `rounded.md` and `spacing.md` padding — used for file lists, output panels, and settings groups. Chips, compact and 32px tall, surface tags and status filters.

## Do's and Don'ts

- Do use `primary` (`#a6c8ff`) for exactly one interactive accent per screen — typically the action that persists the user's work.
- Don't use `primary` for backgrounds, decorative elements, or non-interactive headers. It is for actions only.
- Do use tonal elevation (`surfaceContainerLow` through `surfaceContainerHighest`) to show hierarchy. Don't introduce box-shadows.
- Don't mix multiple font families — Inter is the only typeface in the system.
- Do use `spacing` scale steps by name (`xs` through `xl`). Don't use arbitrary px values for margins or padding.
- Don't use `rounded.full` on anything that isn't a button — inputs, cards, chips, and surfaces all take fixed radii from the rounded scale.
- Do use `error` (`#ffb4ab`) only for destructive actions and validation errors, never for highlighting or branding.
- Don't introduce new color tokens outside the 49-role palette without first verifying that an existing role (`primaryFixed`, `tertiaryContainer`, etc.) doesn't already cover the use case.
