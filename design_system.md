# GupShup Design System Specifications

This document defines the strict, unified Design System foundation for **GupShup**, based on Material Design 3 (Material You) guidelines.

---

## 1. Color System

### Primary Identity
- **Light Mode Primary**: `#128C7E` (WhatsApp Teal)
- **Dark Mode Primary**: `#25D366` (Vibrant Emerald)

### Token Mapping

| Token Name | Light Mode | Dark Mode | Purpose |
|---|---|---|---|
| `colorPrimary` | `#128C7E` | `#25D366` | Primary brand accent & active states |
| `colorPrimaryContainer` | `#E7F9F1` | `#0B3D2E` | Container backgrounds, active pills |
| `surface` | `#FFFFFF` | `#0B141A` | Main page background |
| `colorSurfaceContainerLow` | `#F7F9FA` | `#111B21` | Resting card & item backgrounds |
| `colorSurfaceContainerHigh` | `#EEF3F1` | `#1F2C34` | Input fields, elevated headers |
| `onSurface` | `#0B141A` | `#E9EDEF` | High-emphasis primary text |
| `onSurfaceVariant` | `#667781` | `#8696A0` | Secondary text, captions, icons |
| `outline` | `#E0E4E2` | `#2A3942` | Borders, subtle dividers |
| `error` | `#D32F2F` | `#FF6B6B` | Error alerts, destructive actions |
| `bubbleSent` | `#DCF8C6` | `#005C4B` | Sent chat bubble background |
| `bubbleReceived` | `#FFFFFF` | `#202C33` | Received chat bubble background |

---

## 2. Spacing System (4dp Base Grid)

All margins, paddings, and layout gaps MUST strictly adhere to the 4dp base grid:

| Token Name | Value | Recommended Usage |
|---|---|---|
| `spacing_xs` | `4dp` | Tight internal padding, badge offsets |
| `spacing_sm` | `8dp` | Small item spacing, icon padding |
| `spacing_md` | `12dp` | Section spacing, list item vertical margins |
| `spacing_lg` | `16dp` | Standard screen margin, card internal padding |
| `spacing_xl` | `20dp` | Header spacing |
| `spacing_2xl` | `24dp` | Empty state element gaps, modal padding |
| `spacing_3xl` | `32dp` | Major section gaps, splash margins |
| `spacing_4xl` | `48dp` | Large screen padding |

*Rule*: No arbitrary dimension values (e.g. 5dp, 10dp, 15dp) are allowed in any XML layout.

---

## 3. Corner Radius Tokens

| Token Name | Value | Target Components |
|---|---|---|
| `radius_small` | `8dp` | Chips, badges, reaction pills |
| `radius_medium` | `14dp` | Buttons, text fields, list item cards |
| `radius_large` | `24dp` | Bottom sheets, dialogs, input bars |
| `radius_bubble` | `18dp` | Standard chat bubble corners |
| `radius_bubble_tail` | `4dp` | Chat bubble tail corner |

---

## 4. Elevation Levels

| Level | Value | Target Component |
|---|---|---|
| **Level 0** | `0dp` | Base screen surface |
| **Level 1** | `1dp` | List item cards, filled buttons |
| **Level 2** | `3dp` | Floating Action Buttons (FAB), input bars |
| **Level 3** | `6dp` | Dialogs, modals, bottom sheets |

---

## 5. Typography System

| Style Name | Size / Weight | Line Height / Specs | Usage |
|---|---|---|---|
| `TextAppearance.GupShup.Display` | 28sp Bold | Letter-spacing -0.03 | Splash, Onboarding headers |
| `TextAppearance.GupShup.Headline` | 22sp SemiBold/Bold | Standard | Screen titles, Empty state headers |
| `TextAppearance.GupShup.Title` | 18sp SemiBold/Bold | Standard | Card titles, User names in list |
| `TextAppearance.GupShup.Body` | 15sp Regular | Line-height 22sp | Chat message text, bio, descriptions |
| `TextAppearance.GupShup.Label` | 13sp Medium | Standard | Buttons, tabs, input hints |
| `TextAppearance.GupShup.Caption` | 12sp Regular | Color: `onSurfaceVariant` | Timestamps, read receipts, secondary status |

---

## 6. Component Base Styles (`styles.xml`)

### Button Style: `Widget.GupShup.Button.Filled`
- Height: `52dp` (`@dimen/button_height`)
- Corner Radius: `14dp` (`@dimen/radius_medium`)
- Ripple Color: `@color/colorPrimaryContainer`
- Elevation: `1dp` (`Level 1`)

### Input Field Style: `Widget.GupShup.TextField`
- Corner Radius: `14dp` (`@dimen/radius_medium`)
- Default Border: `1dp` solid `@color/outline`
- Focused Border: `2dp` solid `@color/colorPrimary`
- Minimum Height: `56dp` (`@dimen/input_height`)
- Floating Label Support

### Card Style: `Widget.GupShup.Card`
- Corner Radius: `14dp` (`@dimen/radius_medium`)
- Elevation: `1dp` (`Level 1`)
- Internal Content Padding: `16dp` (`@dimen/spacing_lg`)
- Background: `@color/colorSurfaceContainerLow`

### Avatar Style: `Widget.GupShup.Avatar`
- Shape: Circular (`50%` radius)
- Ring Border: `2dp` brand colored stroke (`@color/colorPrimary`) when online

### Empty State Style: `Widget.GupShup.EmptyState`
- Centered layout
- Illustration Slot Size: `120dp`
- Gap between title, subtitle, and image: `24dp` (`@dimen/spacing_2xl`)
- Subtitle: `TextAppearance.GupShup.Body` in `onSurfaceVariant`
