package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.domain.model.SystemAccount

class GetAccountCurrenciesUseCaseImpl(
    private val accountDao: AccountDao,
) : GetAccountCurrenciesUseCase {

    override suspend fun invoke(): AccountCurrencies {
        return AccountCurrencies(
            // The system stand-ins for facades deleted before closure existed are not
            // currencies the user chose, so they are named out here rather than
            // filtered by whoever asks.
            inUse = accountDao.currenciesInUse(
                systemNames = listOf(SystemAccount.CLOSED_ACCOUNT, SystemAccount.CLOSED_CARD),
            ),
            ofDefaultAccount = accountDao.getDefaultAccount()?.currency,
        )
    }
}
