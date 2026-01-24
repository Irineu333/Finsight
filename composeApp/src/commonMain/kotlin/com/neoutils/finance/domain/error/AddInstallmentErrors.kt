package com.neoutils.finance.domain.error

data class AddInstallmentErrors(
    val creditCardRequired: String = "Transação parcelada requer cartão de crédito",
    val invalidInstallmentCount: String = "O número de parcelas deve ser maior que 0",
) {
    fun blockedInvoice(number: Int, status: String): String = "Parcela $number cairia em uma fatura $status"
}
