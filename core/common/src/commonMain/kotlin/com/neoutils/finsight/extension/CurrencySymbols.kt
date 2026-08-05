package com.neoutils.finsight.extension

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * The glyph each offered currency shows over a value, keyed by ISO code.
 *
 * **It is a port, and that is the whole reason it lives here.** The set of offered
 * currencies is a table read through a repository in `:core:model`, but the composables
 * that render a symbol reach it through `:core:designsystem`, which sees only `common`
 * and `resources`. Declaring the interface in the lower module and binding it where both
 * ends are visible is what `LegacyRelabel` already does in this codebase — and **only
 * `String` crosses the boundary**, so no model type has to follow it down.
 *
 * **A `StateFlow`, and not a plain `Flow`.** [CurrencyFormatter] resolves a glyph while
 * formatting, which is synchronous and happens off composition as well — a form view
 * model formatting a field cannot await a table. The state is the same one the
 * composables collect; what the two differ in is when they read it, not what they read.
 */
interface CurrencySymbols {
    val symbols: StateFlow<Map<String, String>>
}

/**
 * The glyph for a code, **falling back to the code itself** — which is the worst case,
 * exactly as it was before the table existed.
 *
 * It is one function because the fallback is one decision: a currency the table has
 * nothing to say about prints as its own code, over a value and beside an account name
 * alike. Two copies of that rule are how a value and a selector end up disagreeing about
 * the same currency.
 */
fun Map<String, String>.symbolOf(code: String): String = this[code] ?: code

/**
 * The glyph a composable shows for a code, falling back to the code itself.
 *
 * No default, on purpose, and for the same reason [LocalCurrencyFormatter] has none: a
 * default would have to fabricate a source, and the only source is the table. A surface
 * that reads this outside `FormattingLocalsHost` is a bug, and it says so instead of
 * silently rendering a symbol nobody stored.
 */
val LocalCurrencySymbols = staticCompositionLocalOf<(String) -> String> {
    error("No CurrencySymbols provided")
}
