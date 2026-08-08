package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.extension.CurrencySymbols
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The port of `:core:common` bound to the table that actually holds the symbols.
 *
 * This is the only place the two ends meet: `:core:designsystem` collects the port, and
 * neither it nor `:core:common` ever names `:core:model`. Only `String` crosses.
 *
 * Every row is offered, archived ones included: archiving is about what a form offers,
 * and a value already denominated in an archived currency still has to render its glyph.
 *
 * **It is hot, and that is what the state is for.** `CurrencyFormatter` resolves a glyph
 * while formatting — synchronously, and off composition in the two form view models — so
 * the table has to have a *current value* rather than a subscription each caller would
 * have to await. The scope is the application's: this is a `single`, it is collected for
 * as long as the app runs, and there is nothing to cancel it against.
 */
class RepositoryCurrencySymbols(
    repository: ICurrencyRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : CurrencySymbols {

    override val symbols: StateFlow<Map<String, String>> = repository.observeAll()
        .map { currencies -> currencies.associate { it.code to it.symbol } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())
}
