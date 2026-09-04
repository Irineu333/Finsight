package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.error.TransferException
import kotlinx.datetime.LocalDate

/**
 * Correcting a transfer that is already registered, in place.
 *
 * It is the counterpart of [TransferBetweenAccountsUseCase] and shares its rules
 * wholesale — a transfer is no more or less admissible for having been written once
 * already. What differs is the write: the legs of an existing operation are rewritten
 * rather than created, so the operation keeps its identity instead of becoming a new one.
 *
 * Crossing currencies takes no branch here either. The intent arrives at the write
 * boundary incomplete and is completed there, conversion legs and all, exactly as on
 * creation.
 */
interface UpdateTransferUseCase {

    /**
     * The transaction and both accounts are resolved **when the operation runs**; an
     * identity that matches nothing is refused and nothing is written.
     *
     * @param destinationAmount what arrives, when it is not what left. `null` means the
     * two ends are the same number — the whole of the mono-currency case.
     * @param title what the operation is to be called from now on, and `null` for no
     * name at all. Deliberately without a default: the form shows this field, so what
     * arrives here is a statement about it — and a caller that omitted it would be
     * erasing a name rather than leaving it alone.
     */
    suspend operator fun invoke(
        transactionId: Long,
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: Double,
        date: LocalDate,
        title: String?,
        destinationAmount: Double? = null,
    ): Either<TransferException, Unit>
}
