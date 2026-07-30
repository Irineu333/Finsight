package com.neoutils.finsight

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * No term of a money figure is dropped in silence.
 *
 * A figure can hold more than one term, and some surfaces genuinely fit one — a limit meter,
 * the label of a progress bar, a cell of the exported document. The rule is not that they must
 * show everything; it is that leaving a term out is a **declaration**, made in one place, that
 * marks the line and says a term is missing. A surface that reaches past that declaration and
 * takes the first term for itself shows an incomplete number as if it were whole, and the
 * reader has no way to tell.
 *
 * The compiler cannot reach this: `primary` is an ordinary property, and reading it is exactly
 * what the declaration does internally. So the rule is stated over the sources, with the one
 * legitimate reader of the terms named explicitly.
 *
 * The caveat is the same as [ConsolidationBoundaryTest]'s: this catches the accidental, not the
 * adversarial. That is the failure it is here for.
 */
class DeclaredDegradationTest {

    /**
     * Where a figure's terms may be read directly.
     *
     * `MoneyFigure.kt` holds both the stacking of every term and the single-line declaration,
     * which are the two ways a figure is allowed to become text.
     *
     * `ReportExportLayout.kt` is a debt with a date: the exported document has a grammar of one
     * term per line, so it belongs on the declaration — task 8.1 moves it there, along with the
     * footnote that names what was left out. Deleting this entry is that task's proof.
     */
    private val termReaders = listOf(
        "core/common/src/commonMain/kotlin/com/neoutils/finsight/extension/MoneyFigure.kt",
        "feature/report/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/report/viewer/ReportExportLayout.kt",
    )

    @Test
    fun `only the declaration may show less of a figure than it holds`() {
        val offenders = sourcesUnder("core", "feature", "app")
            .filterNot { it.path in termReaders }
            .filter { file -> TERM_ACCESS.containsMatchIn(file.code) }
            .map { it.path }

        assertEquals(
            emptyList(),
            offenders,
            "A surface with room for one term declares it through " +
                "CurrencyFormatter.formatSingleLine, which marks the line and says a term was " +
                "left out. Taking the first term directly shows an incomplete figure as whole.",
        )
    }

    /**
     * Reaching into a figure's terms. `isSingleTerm` is deliberately absent: asking how many
     * terms there are discards nothing, and the card footer that explains the mark has to ask.
     *
     * `colorScheme.primary` is the theme's, not a figure's, and is excluded by the receiver.
     */
    private companion object {
        val TERM_ACCESS = Regex("(?<!colorScheme)\\.(primary|rest|terms)\\b")
    }
}
