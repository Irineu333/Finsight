@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.mapper

import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.isReopenable
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.Limit
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.toUiText
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.ui.extension.color
import com.neoutils.finsight.ui.model.InvoiceUi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class InvoiceUiMapperImpl(
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    // Only to denominate the three figures below: an invoice's money is the card's
    // money, and the card states its currency through its `LIABILITY` account (D17).
    private val accountRepository: IAccountRepository,
    private val clock: Clock,
) : InvoiceUiMapper {
    override suspend fun toUi(
        invoice: Invoice,
        cardInvoices: List<Invoice>,
        limit: Limit,
    ): InvoiceUi {
        val outstandingDebt = calculateInvoiceUseCase(invoice).coerceAtLeast(0.0)
        // Mono-currency by construction, so exact: nothing was converted to get here.
        val currency = accountRepository.currencyOf(invoice.creditCard)
        val hasProgress = outstandingDebt > 0 && limit.usage != 0.0
        val status = invoice.status
        val currentDate = clock.today()

        // The status is decomposed into flat facts here, so no UI model or component
        // re-derives an invoice rule — they consume what the domain already decided.
        return InvoiceUi(
            id = invoice.id,
            amount = DisplayAmount.magnitude(outstandingDebt, currency, isApproximate = false),
            totalUnpaidAmount = DisplayAmount.magnitude(
                limit.committedAmount,
                currency,
                isApproximate = false,
            ),
            availableLimit = DisplayAmount.magnitude(
                limit.available,
                currency,
                isApproximate = false,
            ),
            usagePercentage = if (hasProgress) limit.usage else 0.0,
            showProgress = hasProgress,
            closingDate = invoice.closingDate,
            dueDate = invoice.dueDate,
            isClosable = invoice.isClosableOn(currentDate),
            canReopen = invoice.isReopenable(cardInvoices),
            isOpen = status.isOpen,
            isClosed = status.isClosed,
            isRetroactive = status.isRetroactive,
            isEditable = status.isEditable,
            statusColor = status.color,
            statusLabel = status.toUiText(),
        )
    }
}
