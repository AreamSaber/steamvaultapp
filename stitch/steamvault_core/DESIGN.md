# Design System Strategy: The Vault Narrative

## 1. Overview & Creative North Star
**Creative North Star: "The Digital Monolith"**

To move beyond the generic "utility app" feel, this design system treats security not as a series of alerts, but as a prestigious, architectural experience. We are moving away from the "flat web" aesthetic and toward **Organic Brutalism**. The UI should feel like a high-end physical safe—heavy, intentional, and quiet. 

We break the standard Android template by utilizing **intentional asymmetry** and **tonal depth**. Instead of centering everything, we use wide margins and staggered element placement to create an editorial flow that guides the eye toward the most critical information: your secure keys.

---

## 2. Color & Surface Philosophy
The palette is rooted in deep, atmospheric charcoals (`#121416`) and surgical blues (`#005FB7`). 

### The "No-Line" Rule
**Explicit Instruction:** Designers are prohibited from using 1px solid borders to define sections or containers. Boundaries must be established exclusively through background color shifts or tonal transitions.
*   **Good:** A `surface-container-low` card resting on a `surface` background.
*   **Bad:** A white card with a grey outline.

### Surface Hierarchy & Nesting
We treat the screen as a series of stacked, physical layers. Use the following tiers to define importance:
*   **Base Layer:** `surface` (#121416) for the main application background.
*   **Content Containers:** `surface-container-low` (#1a1c1e) for primary cards and list items.
*   **Nested Elements:** Use `surface-container-high` (#282a2c) for interactive elements *inside* a card (e.g., a "Copy" button container).

### The "Glass & Gradient" Rule
To inject "visual soul," primary actions and hero moments (like the active OTP code) should utilize subtle radial gradients transitioning from `primary` (#a8c8ff) to `primary_container` (#005fb7). For floating navigation or overlays, apply **Glassmorphism**: use `surface_variant` at 60% opacity with a 16px-24px backdrop blur to create a "frosted vault" effect.

---

## 3. Typography: The Editorial Voice
We use a dual-typeface system to balance authority with technical precision.

*   **The Authority (Display & Headlines):** Use **Manrope**. Its geometric construction feels modern and architectural. Use `headline-lg` and `headline-md` for screen titles to create an "Editorial Header" look—tight letter spacing (-0.02em) and heavy weights.
*   **The Utility (Body & Labels):** Use **Inter**. It is designed for maximum legibility in high-stakes environments. 
*   **The Secret (Technical Keys):** For shared secrets and authentication codes, use **JetBrains Mono** or a similar high-quality Monospace font. This signals to the user that this data is "raw" and "technical."

**Hierarchy Tip:** Never use more than three font sizes on a single screen. Contrast should be achieved through weight and color (e.g., `on_surface` for titles vs. `on_surface_variant` for descriptions).

---

## 4. Elevation & Depth
In this system, "Elevation" is a color, not a shadow.

*   **The Layering Principle:** Depth is achieved by "stacking" tones. Place a `surface-container-lowest` (#0c0e10) element inside a `surface-container` to create a "recessed" or "engraved" look.
*   **Ambient Shadows:** If a floating action is required, shadows must be ultra-diffused. Use a blur of 32dp and a 6% opacity of the `surface_tint` color. This creates a "glow" of security rather than a harsh drop shadow.
*   **The Ghost Border Fallback:** For high-density data where separation is difficult, use a "Ghost Border": `outline_variant` (#414752) at 15% opacity. It should be felt, not seen.

---

## 5. Signature Components

### The OTP Card
*   **Style:** No borders. Background: `surface_container_low`. 
*   **Radius:** `xl` (1.5rem / 24dp).
*   **The "Pulse" Indicator:** Use `tertiary` (#ffb68f) for the countdown timer. As the code nears expiration, transition the tone from `primary` to `tertiary` using a subtle glow animation.

### Primary CTA (Bottom Anchor)
*   **Style:** Full-width (with 24dp horizontal padding). 
*   **Shape:** `full` (9999px) for a "pill" look that feels friendly yet professional.
*   **Color:** Use the `primary_container` (#005fb7) to `primary` (#a8c8ff) linear gradient.

### Input Fields
*   **Style:** Filled, not outlined. 
*   **Color:** `surface_container_highest` (#333537).
*   **Interaction:** On focus, do not change the border. Instead, shift the background color to `surface_bright` (#37393b) and add a 2px bottom "accent bar" using the `primary` token.

### Lists & Dividers
*   **Prohibition:** Dividers are forbidden.
*   **Alternative:** Use `md` (0.75rem) vertical spacing between list items. Each item is its own tonal block. This ensures that even if the user has 20 accounts, the UI remains "breathable."

---

## 6. Do’s and Don’ts

### Do:
*   **Embrace Negative Space:** Let the typography breathe. A "Secure" app should feel calm, not cluttered.
*   **Use Tonal Shifts:** Use `surface-dim` for inactive states to make active keys "pop" visually.
*   **Color-Code Context:** Use `tertiary` (Amber tones) for "Expiring Soon" and `error` for "Security Risk."

### Don't:
*   **Don't use pure black (#000000):** It destroys the "Digital Monolith" depth. Always use the specified `surface` tokens.
*   **Don't use 100% opacity for secondary text:** Always use `on_surface_variant` to maintain a clear visual hierarchy.
*   **Don't use standard Material 3 "Floating Action Buttons" (FABs):** They feel too "utility." Use the anchored Bottom CTA or a semi-transparent `surface_container` overlay for a more premium, integrated feel.