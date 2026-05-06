package com.neoutils.finsight.feature.accounts.mapper

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.accounts.model.AccountUi
import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.datetime.YearMonth

interface IAccountUiMapper {
    fun toUi(
        account: Account,
        transactions: List<Transaction>,
        month: YearMonth,
    ): AccountUi
}