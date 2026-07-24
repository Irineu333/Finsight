package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_error_closed_account
import com.neoutils.finsight.resources.ledger_error_closed_credit_card
import com.neoutils.finsight.resources.ledger_error_closed_removal_account
import com.neoutils.finsight.resources.ledger_error_closed_removal_credit_card
import com.neoutils.finsight.resources.ledger_error_unbalanced
import com.neoutils.finsight.util.UiText

/**
 * What a ledger error says to the user.
 *
 * It lives here, not with [LedgerError], because it is the one part of the error
 * that is presentation: it needs the string resources, and those would drag Compose
 * into a module that is domain and data only. The ledger owns the failure; the app
 * owns the sentence.
 */
fun LedgerError.toUiText() = when (this) {
    LedgerError.Unbalanced -> UiText.Res(Res.string.ledger_error_unbalanced)
    LedgerError.MisplacedDimension -> UiText.Res(Res.string.ledger_error_unbalanced)
    is LedgerError.ClosedAccount -> when (facade) {
        ClosedFacade.ACCOUNT -> UiText.Res(Res.string.ledger_error_closed_account)
        ClosedFacade.CREDIT_CARD -> UiText.Res(Res.string.ledger_error_closed_credit_card)
    }

    is LedgerError.ClosedAccountRemoval -> when (facade) {
        ClosedFacade.ACCOUNT -> UiText.Res(Res.string.ledger_error_closed_removal_account)
        ClosedFacade.CREDIT_CARD -> UiText.Res(Res.string.ledger_error_closed_removal_credit_card)
    }
}
