package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.transfer_error_destination_account_not_found
import com.neoutils.finsight.resources.transfer_error_future_date
import com.neoutils.finsight.resources.transfer_error_invalid_amount
import com.neoutils.finsight.resources.transfer_error_same_account
import com.neoutils.finsight.resources.transfer_error_source_account_not_found
import com.neoutils.finsight.resources.transfer_error_unknown
import com.neoutils.finsight.util.UiText

sealed class TransferError(val message: String) {
    data object InvalidAmount : TransferError("Transfer amount must be greater than zero.")
    data object SameAccount : TransferError("Source account must be different from destination account.")
    data object SourceAccountNotFound : TransferError("Source account not found.")
    data object DestinationAccountNotFound : TransferError("Destination account not found.")
    data object FutureDate : TransferError("Transfer date cannot be in the future.")
    data object Unknown : TransferError("Could not complete the transfer.")
}

class TransferException(val error: TransferError) : Exception(error.message)

fun TransferError.toUiText() = when (this) {
    TransferError.InvalidAmount -> UiText.Res(Res.string.transfer_error_invalid_amount)
    TransferError.SameAccount -> UiText.Res(Res.string.transfer_error_same_account)
    TransferError.SourceAccountNotFound -> UiText.Res(Res.string.transfer_error_source_account_not_found)
    TransferError.DestinationAccountNotFound -> UiText.Res(Res.string.transfer_error_destination_account_not_found)
    TransferError.FutureDate -> UiText.Res(Res.string.transfer_error_future_date)
    TransferError.Unknown -> UiText.Res(Res.string.transfer_error_unknown)
}
