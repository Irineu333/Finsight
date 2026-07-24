package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction

class TransactionMapper {

    /**
     * Builds the domain [Transaction] from its row plus its hydrated ledger legs.
     *
     * There is nothing else to build it from. What the transaction *is* comes from
     * the entries; the row carries only what the ledger cannot express — a title, a
     * date, and the installment/recurring identities. Resolving those identities, or
     * the card, invoice and category the legs point at, belongs to the feature that
     * owns each facade (design D6), so no lookup reaches this far down.
     */
    fun toDomain(
        entity: TransactionEntity,
        entries: List<Entry>,
    ): Transaction? {
        if (entries.isEmpty()) return null

        return Transaction(
            id = entity.id,
            title = entity.title,
            date = entity.date,
            recurringId = entity.recurringId,
            recurringCycle = entity.recurringCycle,
            installmentId = entity.installmentId,
            installmentNumber = entity.installmentNumber,
            entries = entries,
        )
    }
}
