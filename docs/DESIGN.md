---
name: Cinematic Obsidian
colors:
  surface: '#141418'
  surface-dim: '#131317'
  surface-bright: '#39393d'
  surface-container-lowest: '#0e0e12'
  surface-container-low: '#1B1B20'
  surface-container: '#222228'
  surface-container-high: '#2B2B32'
  surface-container-highest: '#353439'
  on-surface: '#e4e1e7'
  on-surface-variant: '#e6bdb9'
  inverse-surface: '#e4e1e7'
  inverse-on-surface: '#303034'
  outline: '#6E6E78'
  outline-variant: '#33333A'
  surface-tint: '#ffb3ae'
  primary: '#E11D2E'
  on-primary: '#FFFFFF'
  primary-container: '#4A0A0F'
  on-primary-container: '#FFDAD6'
  inverse-primary: '#c0001f'
  secondary: '#c6c5d1'
  on-secondary: '#2f3039'
  secondary-container: '#3A2326'
  on-secondary-container: '#bbbbc6'
  tertiary: '#fabd00'
  on-tertiary: '#3f2e00'
  tertiary-container: '#926d00'
  on-tertiary-container: '#fff9f3'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffdad7'
  primary-fixed-dim: '#ffb3ae'
  on-primary-fixed: '#410004'
  on-primary-fixed-variant: '#930015'
  secondary-fixed: '#e2e1ed'
  secondary-fixed-dim: '#c6c5d1'
  on-secondary-fixed: '#1a1b23'
  on-secondary-fixed-variant: '#45464f'
  tertiary-fixed: '#ffdf9e'
  tertiary-fixed-dim: '#fabd00'
  on-tertiary-fixed: '#261a00'
  on-tertiary-fixed-variant: '#5b4300'
  background: '#131317'
  on-background: '#e4e1e7'
  surface-variant: '#353439'
  canvas-base: '#0E0E11'
  brand-text: '#FF4D5A'
  rating-amber: '#FFC107'
  badge-scrim: rgba(0, 0, 0, 0.6)
  amoled-canvas: '#000000'
  light-canvas: '#FAFAFB'
  light-brand-text: '#C4000F'
typography:
  display-hero:
    fontFamily: Montserrat
    fontSize: 40px
    fontWeight: '800'
    lineHeight: 48px
  display-hero-mobile:
    fontFamily: Montserrat
    fontSize: 30px
    fontWeight: '800'
    lineHeight: 36px
  headline-lg:
    fontFamily: Montserrat
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 32px
  headline-md:
    fontFamily: Montserrat
    fontSize: 20px
    fontWeight: '700'
    lineHeight: 26px
  headline-sm:
    fontFamily: Montserrat
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
  title-medium:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '600'
    lineHeight: 20px
  body-large:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  body-medium:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  label-large:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 18px
  label-medium:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  label-small:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-compact: 0.75rem
  margin: 1rem
  margin-expanded: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system delivers an immersive, theatrical streaming experience optimized for Android and cross-platform responsive surfaces. It bridges the discipline of Material 3 Expressive guidelines with high-end cinematic minimalism, treating video key art and photography as the singular hero while the interface recedes gracefully into the background.

### Design Principles
- **Theatrical Immersion:** Deep, neutral-biased dark surfaces reduce bezel distraction, conserve OLED battery life, and eliminate optical fatigue during prolonged entertainment consumption.
- **Surgical Chromatic Intent:** Red is reserved for focused intent, critical system cues, and deliberate high-priority calls to action. The brand avoids decorative redness to eliminate visual noise.
- **Adaptive Ergonomics:** Compact handheld viewports favor one-handed, gesture-driven thumb zones with a floating `NavigationBar`. Expanded, foldable, and tablet contexts pivot instantly to `NavigationRail` and split-pane supporting view architectures.
- **Universal Accessibility:** Strict adherence to WCAG 2.1 AA contrast thresholds, robust 48dp minimum touch targets, clean multi-script typography fallbacks, and complete TalkBack semantic descriptors.

## Colors

The color architecture is built directly on Material 3 token specifications, calibrated across dark, AMOLED true black, and light modes.

### Key M3 Tokens & Dark Hierarchy (Default)
- **Primary (`#E11D2E`):** A custom, proprietary high-vibrancy cinema crimson. Applied to primary CTAs, active media scrubbers, and high-emphasis controls. Provides crisp 4.8:1+ contrast with `#FFFFFF` text.
- **Primary Container (`#4A0A0F`) & On-Primary Container (`#FFDAD6`):** Low-luminosity crimson chamber used for active selection indicators and highlighted pill items, ensuring clear status without visual competition.
- **Secondary Container (`#3A2326`):** Muted brick container reserved for navigation indicators, unselected active pills, and secondary interaction wells.
- **Brand Text Role (`#FF4D5A`):** Custom high-contrast crimson with an accessible 5.9:1 contrast ratio against the `#0E0E11` canvas. Used exclusively for "See all" actions, active text tabs, and inline links.
- **Neutral Grey (`#A8A8B3`):** Unified `secondary` and `on-surface-variant` value. Prevents pink/red tint drift across secondary metadata (year, runtime, audio tracks, genres).
- **Outline Roles:**
  - `outline` (`#6E6E78`): 3.8:1 contrast baseline for input borders, switch tracks, and focused container boundaries.
  - `outline-variant` (`#33333A`): Subdued structural hairlines for subtle list dividers and card separators.
- **Star Rating (`#FFC107`):** Amber tone paired with a 60% black pill container (`rgba(0, 0, 0, 0.6)`) positioned at top-start for universal RTL compatibility.

### Multi-Theme Adaptations
- **AMOLED True Black Mode:** Canvas maps to `#000000`. `surface` shifts to `#0A0A0D`, `surface-container-low` to `#121216`, and `surface-container` to `#1A1A1E`. Accents and outlines retain dark-mode values.
- **Light Mode:** Canvas shifts to `#FAFAFB`, `surface` to `#FFFFFF`, and structural strokes to `#E2E2E8`. The brand text role transitions to `#C4000F` to preserve strict WCAG AA (7.2:1) contrast against light surfaces.

## Typography

The type scale combines the theatrical, editorial strength of **Montserrat** for display headers with the clean legibility of **Inter** for dense metadata and interface structures.

### Hierarchy & Usage
- **Hero & Section Titles:** Montserrat (weights 700 to 800) drives `display-hero`, `headline-lg`, and category carousels. Tracking is calibrated to `-0.02em` for dense impact.
- **Content Titles (`title-medium`):** Inter at weight 600. Card titles clamp to a strict 2-line maximum with ellipsis overflow. Marquee truncation or all-caps transformations are forbidden on title names to maintain reading cadence.
- **Body & Information (`body-large`, `body-medium`):** Inter at weight 400 for synopsis, cast lists, and expanded descriptions.
- **Labels & Badges (`label-medium`, `label-small`):** Inter at weights 500 to 600. `label-small` sits at a hard floor of 11sp (11px) to guarantee readability across low-density screens and accessibility compliance.
- **Fallback Chain:** All typography stacks support CJK and Arabic scripts gracefully via system chains:
  - Western/Latin: `Montserrat`, `Inter`
  - Arabic Fallback: `Noto Sans Arabic`
  - CJK Fallback: `Noto Sans CJK SC`, `Noto Sans CJK JP`, `Noto Sans CJK KR`

## Layout & Spacing

Layout geometry responds cleanly to standard Android and responsive window size classes, ensuring fluid adaptation across phone, foldable, tablet, and TV displays.

### Window Size Classes
- **Compact (<600dp):**
  - Outer margins: `16px` (`margin`). Gutters: `12px` (`gutter-compact`).
  - Navigation: Anchored bottom `NavigationBar` with a minimum touch height of `56dp` (inner icon targets `48dp`).
  - Carousels: Edge-to-edge horizontally scrolling carousels bleed to the display bounds, with the first poster aligned to the `16px` margin and trailing items clipped cleanly to invite interaction.
- **Medium (600dp - 839dp, Foldables & Small Tablets):**
  - Outer margins: `24px` (`margin-expanded`). Gutters: `16px` (`gutter`).
  - Navigation: Transitions from bottom bar to a vertical `NavigationRail` docked to the start side.
  - Content: 2-column or 3-column responsive catalog shelves.
- **Expanded (>=840dp, Tablets & Desktops):**
  - Navigation: Expanded `NavigationRail` or persistent side navigation drawer.
  - Grid: 4 to 6 poster columns per shelf.
  - Detail Panels: Supporting pane layout pattern (primary media details in the start 60% pane; episodes, cast, and recommendations in the supporting 40% pane).

### Layout Rules & Spacing Rhythm
- Section vertical gaps consistently maintain `space-xl` (`32px`) to distinguish curated catalog rows.
- Minimum interactive touch targets remain strictly `48dp × 48dp` across all form factors.

## Elevation & Depth

Visual hierarchy uses tonal surface stacking rather than opaque skeuomorphic shadows, creating a seamless cinema feel on OLED displays.

### Tonal Tiers
- **Level 0 (Canvas `#0E0E11`):** Structural root plane.
- **Level 1 (Surface `#141418`):** Carousel shelves, persistent navigation backgrounds, and collapsed utility trays.
- **Level 2 (Surface Container Low `#1B1B20`):** Resting cards, poster tiles, search pill backgrounds, and unselected chips.
- **Level 3 (Surface Container `#222228`):** Elevated cards, dropdown menus, and search overlays.
- **Level 4 (Surface Container High `#2B2B32`):** Modals, bottom sheets, context dialogs, and detached HUD controls.

### Glass & Scrim Overlays
- **Backdrop Frosting:** Player overlays and detached navigation elements leverage `rgba(20, 20, 24, 0.75)` with a `16px` to `24px` blur filter.
- **Gradient Scrims:** Bottom and top edges of hero banners blend into the canvas via multi-stop linear gradients (`rgba(14, 14, 17, 0)` to `#0E0E11` over 120dp vertical offset) to preserve text contrast.

## Shapes

The design system maintains a balanced `roundedness: 2` (0.5rem / 8px base) visual language, blending soft modern curves with focused media geometry.

### Radius Allocations
- **Base Components (`rounded` / 8px):** Text input containers, dialog panes, and small utility menus.
- **Media Posters & Backdrops (`rounded-lg` / 16px):** Standard movie posters (2:3 aspect ratio) and episode backdrops (16:9 aspect ratio) use 16px rounding, preserving artwork edges while feeling soft and modern.
- **Interactive Triggers & Chips (`rounded-full` / 9999px):** Hero buttons, category chips, badge containers, scrubber thumbs, and floating navigation pills use full pill geometry.
- **Bottom Sheets:** Top-start and top-end corners use `24px` radius, while bottom corners sit flush against device bezels.

## Components

### Buttons & CTAs
- **Primary Hero Button:** Full pill geometry. Fill `#E11D2E`, label `#FFFFFF` in `label-large`. Minimum height 48dp, padding 12dp vertical / 24dp horizontal. Active/pressed state dims to `#4A0A0F`.
- **Secondary Glass Button:** Full pill geometry. Fill `#222228`, outline 1px solid `#6E6E78`, label `#FFFFFF`. Pressed state shows ripple fill in `#3A2326`.
- **Text & Inline Actions:** Label rendered in `brand-text` (`#FF4D5A`), font `label-large`. Padding 8dp around hit area to preserve 48dp touch compliance.

### Media Cards & Posters
- **Poster Container:** 2:3 fixed ratio with `16px` radius. Resting background uses `#1B1B20`.
- **Title Clamp:** inter `title-medium`, 2-line clamp with ellipsis. Marquee animation and all-caps styling are disabled.
- **Star Rating Badge:** Amber `#FFC107` star glyph with `label-small` rating text, encapsulated within `badge-scrim` (`rgba(0, 0, 0, 0.6)`), positioned at top-start with `8dp` inset for universal RTL support.
- **Skeleton State:** Pulse animation cycling between `#1B1B20` and `#222228` with smooth opacity ease over a 1.5s period.

### Navigation Components
- **NavigationBar (Compact):** Docked to screen bottom, height 64dp, fill `#141418` with subtle 1px border-top in `#33333A`. Active tab sits inside a pill pill-container with `#3A2326` background, icon tinted `#FF4D5A`. Inactive icons render in `#A8A8B3`.
- **NavigationRail (Medium & Expanded):** Docked to start, width 80dp, surface `#141418`, border-end 1px solid `#33333A`. Active destination indicator mirrors the `#3A2326` pill treatment.

### Chips & Interactive Filters
- **Filter Chips:** Full pill geometry, height 36dp. Inactive state: `#1B1B20` surface, 1px stroke `#33333A`, label `#A8A8B3`. Active state: `#4A0A0F` container, 1px stroke `#E11D2E`, label `#FFDAD6`.

### Inputs & Text Fields
- **Search & Text Input:** Surface `#222228`, 8px corner radius (`rounded`), 1px outline in `#6E6E78`. Leading icon `#A8A8B3`. On focus: stroke thickens to 2px `#FF4D5A`, background brightens to `#2B2B32`. Minimum touch height 48dp.

### System & State Specs
- **Empty States:** Centered icon in `#A8A8B3` (48dp), title in `headline-sm` (`#FFFFFF`), description in `body-medium` (`#A8A8B3`), paired with a primary CTA button.
- **Offline States:** Persistent banner anchored beneath the top app bar in `#3A2326` with 1px border `#6E6E78`. Icon in `#FFC107`, copy in `label-medium` `#FFDAD6`.
- **TalkBack & Accessibility Semantics:** All posters must expose combined content descriptions: `"[Title], [Year], Rating [X] out of 10, [Genre]"`. Buttons must state intent: `"Play [Title], button"`. Floating cards must group children to avoid disjointed screen reader focus.