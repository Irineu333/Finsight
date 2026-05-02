package com.neoutils.finsight.feature.accounts.error

sealed class TransferError(val message: String) {
    data object InvalidAmount : TransferError("Transfer amount must be greater than zero.")
    data object SameAccount : TransferError("Source account must be different from destination account.")
    data object SourceAccountNotFound : TransferError("Source account not found.")
    data object DestinationAccountNotFound : TransferError("Destination account not found.")
    data object FutureDate : TransferError("Transfer date cannot be in the future.")
    data object Unknown : TransferError("Could not complete the transfer.")
}
