package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType

/**
 * A flat, display-ready view of an archived account — the fields the archived listing
 * and its detail render. Carries no domain graph, so the domain [Account] stays on the
 * ViewModel side of the boundary and never reaches a Composable.
 */
data class ArchivedAccountUi(
    val accountId: Long,
    val iconKey: String,
    val name: String,
    val type: AccountType,
)

fun Account.toArchivedUi() = ArchivedAccountUi(
    accountId = id,
    iconKey = iconKey,
    name = name,
    type = type,
)
