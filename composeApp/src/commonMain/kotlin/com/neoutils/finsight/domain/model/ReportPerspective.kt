package com.neoutils.finsight.domain.model

sealed class ReportPerspective {
    data class AccountPerspective(val accountIds: List<Long>) : ReportPerspective()
    data class CreditCardPerspective(val creditCardId: Long) : ReportPerspective()
}
