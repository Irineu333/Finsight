package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.extension.CurrencySymbols
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The port of `:core:common` bound to the table that actually holds the symbols.
 *
 * This is the only place the two ends meet: `:core:designsystem` collects the port, and
 * neither it nor `:core:common` ever names `:core:model`. Only `String` crosses.
 *
 * Every row is offered, archived ones included: archiving is about what a form offers,
 * and a value already denominated in an archived currency still has to render its glyph.
 */
class RepositoryCurrencySymbols(
    private val repository: ICurrencyRepository,
) : CurrencySymbols {

    override val symbols: Flow<Map<String, String>> =
        repository.observeAll().map { currencies -> currencies.associate { it.code to it.symbol } }
}
