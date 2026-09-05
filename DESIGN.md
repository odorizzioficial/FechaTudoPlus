---
name: Kinetic Utility
colors:
  surface: '#141317'
  surface-dim: '#141317'
  surface-bright: '#3a383d'
  surface-container-lowest: '#0e0e11'
  surface-container-low: '#1c1b1f'
  surface-container: '#201f23'
  surface-container-high: '#2b292d'
  surface-container-highest: '#353438'
  on-surface: '#e5e1e7'
  on-surface-variant: '#cac4d0'
  inverse-surface: '#e5e1e7'
  inverse-on-surface: '#313034'
  outline: '#948f9a'
  outline-variant: '#49454f'
  surface-tint: '#d0bcff'
  primary: '#e9ddff'
  on-primary: '#37265e'
  primary-container: '#d0bcff'
  on-primary-container: '#594983'
  inverse-primary: '#665590'
  secondary: '#d0bcff'
  on-secondary: '#381e72'
  secondary-container: '#4f378a'
  on-secondary-container: '#c0a7ff'
  tertiary: '#9befff'
  on-tertiary: '#00363d'
  tertiary-container: '#00daf3'
  on-tertiary-container: '#005b66'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e9ddff'
  primary-fixed-dim: '#d0bcff'
  on-primary-fixed: '#210f48'
  on-primary-fixed-variant: '#4d3d76'
  secondary-fixed: '#e9ddff'
  secondary-fixed-dim: '#d0bcff'
  on-secondary-fixed: '#22005c'
  on-secondary-fixed-variant: '#4f378a'
  tertiary-fixed: '#9cf0ff'
  tertiary-fixed-dim: '#00daf3'
  on-tertiary-fixed: '#001f24'
  on-tertiary-fixed-variant: '#004f58'
  background: '#141317'
  on-background: '#e5e1e7'
  surface-variant: '#353438'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 57px
    fontWeight: '700'
    lineHeight: 64px
    letterSpacing: -0.25px
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
  title-lg:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '500'
    lineHeight: 28px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.5px
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  edge-margin-mobile: 16px
  edge-margin-desktop: 24px
  gutter: 16px
---

## Brand & Style

This design system is built for high-performance system utilities and developer tools. It blends the structural reliability of Material 3 with an expressive, high-energy aesthetic. The brand personality is "Electric Efficiency"—it feels fast, precise, and sophisticated.

The design style utilizes **Corporate Modernism** infused with **Vibrant Accents**. It relies on deep neutral surfaces to provide a stable foundation for high-contrast, glowing interactive elements. The emotional response should be one of total control and modern capability, leaning into a "pro-tool" aesthetic that remains accessible through familiar Material 3 patterns.

## Colors

The palette centers on a "Sleek Dark" default experience, though it fully supports Light mode. 

- **Primary:** A luminous violet (#D0BCFF) serves as the main interactive driver.
- **Secondary:** A deep, regal indigo (#381E72) provides low-luminance contrast for containers.
- **Tertiary:** A vibrant "Electric Cyan" (#00E5FF) is used sparingly for data visualization, success states, or high-priority calls to action, creating the "eye-catching" spark.
- **Neutral:** The background uses a deep "charcoal-ink" (#1C1B1F) to reduce eye strain and allow primary accents to pop.

In Dark Mode, surfaces use tonal elevation—adding a higher percentage of the primary color overlay as elements move closer to the user. In Light Mode, the primary shifts to a more saturated #6750A4 to maintain legibility.

## Typography

This design system utilizes **Inter** for all UI copy to ensure maximum legibility and a contemporary, neutral feel. To lean into the "system utility" narrative, **JetBrains Mono** is introduced for labels, metadata, and technical readouts.

- **Headlines:** Use tight letter-spacing and semi-bold weights to feel impactful.
- **Body:** Standardized on a 16px base for comfort.
- **Labels:** Use the monospaced font to differentiate data from prose, reinforcing the precision of the tool.

## Layout & Spacing

The design system follows a strict **8-dp grid system** for spacing, with a **4-dp baseline** for fine-tuning typography and icons.

- **Mobile:** Uses a 4-column fluid grid with 16dp margins.
- **Tablet/Foldable:** Transitions to an 8-column grid; often utilizes a Navigation Rail to preserve vertical space.
- **Desktop:** A 12-column grid with a maximum content width of 1240dp. 

The layout model emphasizes **Navigation Rails** for top-level app switching on larger screens, ensuring the utility feels expansive and organized. Gutters are kept at a consistent 16dp to maintain a dense, informative layout without feeling cluttered.

## Elevation & Depth

Depth is communicated through **Tonal Layers** and **Subtle Glows**. 

Instead of traditional grey shadows, this system uses "Ambient Shadows" that carry a slight primary-tinted hue (violet) in dark mode to simulate light emission from the surfaces.
- **Level 0 (Background):** Base neutral color.
- **Level 1 (Cards/Containers):** Primary color overlay at 5% opacity.
- **Level 2 (Dialogs/Menus):** Primary color overlay at 8% opacity with a 4dp blurred shadow.

Glassmorphism is applied specifically to **Top Bars** and **Bottom Sheets** using a 20px backdrop blur and 60% opacity on the surface color to maintain context of the content scrolling beneath.

## Shapes

The shape language is **Modern Rounded**, moving away from the extreme pill shapes of early Material 3 toward a more structured "Squircle" feel for cards.

- **Small Components (Buttons, Inputs):** 8dp (rounded-md) to feel approachable but professional.
- **Medium Components (Cards, Menus):** 12dp to 16dp (rounded-lg) for a distinct, modern container feel.
- **Large Components (Modals):** 24dp (rounded-xl) on top corners to soften the UI.

## Components

### Buttons
Primary buttons use a solid violet fill with high-contrast text. Secondary buttons use a "Tonal" style—a low-opacity violet fill with a subtle 1px border.

### Cards
Cards are the primary layout vehicle. They must use `ElevatedCard` or `OutlinedCard` with a 12dp corner radius. In Dark Mode, Outlined cards should use a 10% opacity primary border to feel "charged."

### Inputs
Text fields use the "Filled" Material 3 variant but with the bottom indicator replaced by a subtle 1px frame. Focus states trigger a 2px "Electric Cyan" border to signal active input.

### Navigation Rail & Bar
For system utilities, the Navigation Rail (side) is preferred for Desktop/Tablet. Icons should use the "Sharp" or "Two-Tone" Material symbol sets to match the technical aesthetic.

### Chips & Badges
Use JetBrains Mono for the text inside chips. Status badges (e.g., "Active", "System Error") should use the Tertiary Cyan or a dedicated error red, with a subtle outer glow (0.5dp blur) to simulate an LED indicator.