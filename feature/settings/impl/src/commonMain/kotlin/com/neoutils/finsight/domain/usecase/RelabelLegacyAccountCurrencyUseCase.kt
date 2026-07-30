@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.AccountCurrencyRelabelDao
import com.neoutils.finsight.database.entity.AccountCurrencyRelabelLogEntity
import com.neoutils.finsight.database.entity.AppMigrationLogEntity
import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.neoutils.finsight.extension.deviceCurrencyCode
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The one-off step that makes an existing database say what its user always read.
 *
 * Every database written before this change holds `'BRL'` in every row — not because anyone
 * chose it, but because it was the model's default. Meanwhile the formatter always rendered
 * the *device's* currency, so a user in the United States **saw dollars for years** while
 * their data said reais. The divergence never surfaced.
 *
 * It would surface now, and badly: the currency of the data decides the symbol from here on,
 * and it is immutable, so without this step every user outside Brazil would watch the whole
 * app turn into `R$` with no way to correct it — their accounts have entries, so they cannot
 * be deleted and recreated. Relabelling makes the data say what the screen has been saying.
 *
 * **It is relabelling, not conversion.** No amount changes, no entry is touched, and `Σ = 0`
 * per currency keeps holding because the denomination of every account moves together.
 *
 * **The accepted consequence, stated rather than hidden:** a Brazilian whose device is set to
 * a foreign region has their accounts relabelled to that region's currency, silently, and the
 * immutability rule means the app offers no way back. Two things narrow it — the locale's
 * *region* decides and not its language, so an English interface on a Brazilian device does
 * nothing; and a currency outside the offered catalog does nothing either. The alternative
 * was asking every non-BRL user once, which would have cost a migration screen for everyone;
 * zero friction won.
 *
 * It runs **once**, and the claim that it ran is written in the same transaction as the work,
 * so a later change of region cannot fire it again — which would be exactly the silent
 * restatement of meaning the base currency is forbidden from doing.
 */
class RelabelLegacyAccountCurrencyUseCase(
    private val database: AppDatabase,
    private val dao: AccountCurrencyRelabelDao,
    private val deviceCurrency: () -> String? = ::deviceCurrencyCode,
) {

    suspend operator fun invoke() {
        val target = deviceCurrency()?.takeIf(CurrencyCatalog::offers) ?: return
        if (target == LEGACY_CURRENCY) return

        database.useWriterConnection {
            it.immediateTransaction {
                // Read inside the transaction that would do the work: checking outside it and
                // writing inside leaves a window where two runs both decide to go ahead.
                if (dao.hasRun(STEP)) return@immediateTransaction

                val now = Clock.System.now().toEpochMilliseconds()
                val touched = dao.accountsDenominatedIn(LEGACY_CURRENCY)

                // The snapshot goes first, so that the rewrite is never the only record of
                // itself — support has to be able to answer "what was it before?".
                dao.log(
                    touched.map { accountId ->
                        AccountCurrencyRelabelLogEntity(
                            accountId = accountId,
                            previousCurrency = LEGACY_CURRENCY,
                            newCurrency = target,
                            migratedAt = now,
                        )
                    }
                )
                dao.relabel(from = LEGACY_CURRENCY, to = target)
                dao.markRun(AppMigrationLogEntity(step = STEP, migratedAt = now))
            }
        }
    }

    private companion object {
        /**
         * What every legacy row says, and it is deliberately a literal of its own rather than
         * a reference to the last-resort constant: this names a fact about databases already
         * written, and it must not follow if that constant is ever changed.
         */
        const val LEGACY_CURRENCY = "BRL"

        const val STEP = "relabel_legacy_account_currency"
    }
}
