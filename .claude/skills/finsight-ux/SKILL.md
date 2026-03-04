---
name: finsight-ux
description: >
  Finsight design system and UX expert. ALWAYS trigger when writing any UI code for this project.
  Enforces the project's color tokens, typography scale, spacing rhythm, card patterns, modal
  structure, and component conventions. Prevents use of hardcoded colors, arbitrary font sizes,
  inconsistent padding, or patterns that diverge from the established design system.
user-invocable: false
---

# Finsight UX & Design System

Practical enforcement of the Finsight design system. Every UI change must use the tokens,
patterns, and components defined here — no hardcoded values, no one-off styles.

## Workflow

### 1. Identify what's being built
- New card? → `references/cards.md`
- New modal/bottom sheet? → `references/modals.md`
- Color usage? → `references/colors.md`
- Text or typography? → `references/typography.md`
- Spacing, layout, padding? → `references/spacing-layout.md`
- New component or selector? → `references/components.md`

### 2. Apply the design system
- Use `colorScheme.*` for all semantic colors (surface, onSurface, error, etc.)
- Use named color tokens (`Income`, `Expense`, `Adjustment`, `InvoicePayment`) for financial values
- Use `MaterialTheme.typography.*` for text styles
- Use the spacing rhythm (multiples of 4dp, standard breakpoints at 8/12/16/20/24/32dp)
- Use `shapes.large` for cards, `RoundedCornerShape(12.dp)` for buttons and fields

### 3. Flag violations
Proactively point out any hardcoded colors, arbitrary font sizes, or inconsistent spacing
found in the code being reviewed or modified.

## Key Principles

1. **No hardcoded colors.** Always use `colorScheme.*` or named tokens from `Color.kt`.
   The theme handles dark/light — raw hex values break both.

2. **No hardcoded font sizes without `MaterialTheme.typography`.**
   Direct `fontSize = X.sp` is acceptable only for components with fixed config objects
   (like `BalanceCardConfig`) — never in ad-hoc composables.

3. **Spacing is rhythmic.** Padding and spacing use multiples of 4dp.
   Standard values: `4, 8, 12, 16, 20, 24, 32dp`.

4. **Cards use `colorScheme.surfaceContainer`** as their default container color.
   Never use raw `Surface2` or hardcoded hex in card backgrounds.

5. **Financial values have semantic colors.** Income → `Income`, Expense → `Expense`,
   Adjustment → `Adjustment`, Invoice payment → `InvoicePayment`. These are non-negotiable.

6. **Modals extend `ModalBottomSheet`.** Never use `ModalBottomSheet` directly in a composable
   — always subclass the project's abstract `ModalBottomSheet` and use `ModalManager.show()`.
