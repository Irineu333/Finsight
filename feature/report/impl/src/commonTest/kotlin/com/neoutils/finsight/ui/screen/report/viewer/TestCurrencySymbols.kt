package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.extension.CurrencySymbols
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.dsl.module

/**
 * The table a formatter under test resolves its glyphs against.
 *
 * `commonModule` binds the formatter over this port, because the glyph over a value is
 * the registry's answer and not the platform's — so a test that wants a formatter has to
 * say what the registry holds, exactly as the app does.
 */
class TestCurrencySymbols(
    symbols: Map<String, String>,
) : CurrencySymbols {
    override val symbols: StateFlow<Map<String, String>> = MutableStateFlow(symbols)
}

val testSymbolsModule = module {
    single<CurrencySymbols> {
        TestCurrencySymbols(mapOf("BRL" to "R$", "USD" to "$", "EUR" to "€"))
    }
}
