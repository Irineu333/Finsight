package com.neoutils.finance.database.mapper

import com.neoutils.finance.database.entity.InvoiceEntity
import com.neoutils.finance.domain.model.Invoice

class InvoiceMapper {

    fun toDomain(entity: InvoiceEntity): Invoice {
        return Invoice(
            id = entity.id,
            creditCardId = entity.creditCardId,
            openingMonth = entity.openingMonth,
            closingMonth = entity.closingMonth,
            status = entity.status.toDomain(),
            createdAt = entity.createdAt,
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
            status = domain.status.toEntity(),
            createdAt = domain.createdAt,
            closedAt = domain.closedAt,
            paidAt = domain.paidAt
        )
    }

    fun InvoiceEntity.Status.toDomain(): Invoice.Status {
        return when (this) {
            InvoiceEntity.Status.OPEN -> Invoice.Status.OPEN
            InvoiceEntity.Status.CLOSED -> Invoice.Status.CLOSED
            InvoiceEntity.Status.PAID -> Invoice.Status.PAID
        }
    }

    fun Invoice.Status.toEntity(): InvoiceEntity.Status {
        return when (this) {
            Invoice.Status.OPEN -> InvoiceEntity.Status.OPEN
            Invoice.Status.CLOSED -> InvoiceEntity.Status.CLOSED
            Invoice.Status.PAID -> InvoiceEntity.Status.PAID
        }
    }
}
