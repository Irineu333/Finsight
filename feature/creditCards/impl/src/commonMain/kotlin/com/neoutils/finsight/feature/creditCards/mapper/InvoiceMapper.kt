package com.neoutils.finsight.feature.creditCards.mapper

import com.neoutils.finsight.core.database.entity.InvoiceEntity
import com.neoutils.finsight.core.domain.model.Invoice

class InvoiceMapper {

    fun toDomain(
        entity: InvoiceEntity,
    ): Invoice {
        return Invoice(
            id = entity.id,
            creditCardId = entity.creditCardId,
            openingMonth = entity.openingMonth,
            closingMonth = entity.closingMonth,
            dueMonth = entity.dueMonth,
            status = entity.status.toDomain(),
            createdAt = entity.createdAt,
            openedAt = entity.openedAt,
            closedAt = entity.closedAt,
            paidAt = entity.paidAt
        )
    }

    fun toEntity(domain: Invoice): InvoiceEntity {
        return InvoiceEntity(
            id = domain.id,
            creditCardId = domain.creditCardId,
            openingMonth = domain.openingMonth,
            closingMonth = domain.closingMonth,
            dueMonth = domain.dueMonth,
            status = domain.status.toEntity(),
            createdAt = domain.createdAt,
            openedAt = domain.openedAt,
            closedAt = domain.closedAt,
            paidAt = domain.paidAt
        )
    }

    fun InvoiceEntity.Status.toDomain(): Invoice.Status {
        return when (this) {
            InvoiceEntity.Status.FUTURE -> Invoice.Status.FUTURE
            InvoiceEntity.Status.OPEN -> Invoice.Status.OPEN
            InvoiceEntity.Status.CLOSED -> Invoice.Status.CLOSED
            InvoiceEntity.Status.PAID -> Invoice.Status.PAID
            InvoiceEntity.Status.RETROACTIVE -> Invoice.Status.RETROACTIVE
        }
    }

    fun Invoice.Status.toEntity(): InvoiceEntity.Status {
        return when (this) {
            Invoice.Status.FUTURE -> InvoiceEntity.Status.FUTURE
            Invoice.Status.OPEN -> InvoiceEntity.Status.OPEN
            Invoice.Status.CLOSED -> InvoiceEntity.Status.CLOSED
            Invoice.Status.PAID -> InvoiceEntity.Status.PAID
            Invoice.Status.RETROACTIVE -> InvoiceEntity.Status.RETROACTIVE
        }
    }
}
