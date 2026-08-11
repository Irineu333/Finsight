package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.FeatureIconCatalog

class SuggestAccountIconUseCaseImpl(
    private val accountRepository: IAccountRepository,
) : SuggestAccountIconUseCase {

    override suspend fun invoke(): AppIcon {

        // `getAllAccounts` already answers with the open ones only — that is exactly
        // the recut of "in use" the suggestion needs.
        val usedKeys = accountRepository.getAllAccounts().mapTo(mutableSetOf()) { it.iconKey }

        // The universe is the account catalog itself, not the extended list the
        // selector displays: `withGeneral` exists so the picker has no gaps, while
        // the suggestion is the feature's editorial preference.
        return FeatureIconCatalog.accounts.firstOrNull { it.key !in usedKeys } ?: AppIcon.WALLET
    }
}
