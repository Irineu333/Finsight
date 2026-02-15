package com.neoutils.finance.domain.error

import com.neoutils.finance.resources.Res
import com.neoutils.finance.resources.transfer_error_destination_account_not_found
import com.neoutils.finance.resources.transfer_error_future_date
import com.neoutils.finance.resources.transfer_error_invalid_amount
import com.neoutils.finance.resources.transfer_error_same_account
import com.neoutils.finance.resources.transfer_error_source_account_not_found
import com.neoutils.finance.resources.transfer_error_unknown
import com.neoutils.finance.util.UiText

sealed class TransferError(val message: String) {
    data object InvalidAmount : TransferError("O valor da transferência deve ser maior que zero.")
    data object SameAccount : TransferError("A conta de origem deve ser diferente da conta de destino.")
    data object SourceAccountNotFound : TransferError("Conta de origem não encontrada.")
    data object DestinationAccountNotFound : TransferError("Conta de destino não encontrada.")
    data object FutureDate : TransferError("A data da transferência não pode ser futura.")
    data object Unknown : TransferError("Não foi possível concluir a transferência.")
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
