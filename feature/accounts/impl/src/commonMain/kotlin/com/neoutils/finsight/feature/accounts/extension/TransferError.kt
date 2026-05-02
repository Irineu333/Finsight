package com.neoutils.finsight.feature.accounts.extension

import com.neoutils.finsight.core.ui.util.UiText
import com.neoutils.finsight.feature.accounts.error.TransferError
import com.neoutils.finsight.feature.accounts.resources.Res
import com.neoutils.finsight.feature.accounts.resources.transfer_error_destination_account_not_found
import com.neoutils.finsight.feature.accounts.resources.transfer_error_future_date
import com.neoutils.finsight.feature.accounts.resources.transfer_error_invalid_amount
import com.neoutils.finsight.feature.accounts.resources.transfer_error_same_account
import com.neoutils.finsight.feature.accounts.resources.transfer_error_source_account_not_found
import com.neoutils.finsight.feature.accounts.resources.transfer_error_unknown

fun TransferError.toUiText() = when (this) {
    TransferError.InvalidAmount -> UiText.Res(Res.string.transfer_error_invalid_amount)
    TransferError.SameAccount -> UiText.Res(Res.string.transfer_error_same_account)
    TransferError.SourceAccountNotFound -> UiText.Res(Res.string.transfer_error_source_account_not_found)
    TransferError.DestinationAccountNotFound -> UiText.Res(Res.string.transfer_error_destination_account_not_found)
    TransferError.FutureDate -> UiText.Res(Res.string.transfer_error_future_date)
    TransferError.Unknown -> UiText.Res(Res.string.transfer_error_unknown)
}
