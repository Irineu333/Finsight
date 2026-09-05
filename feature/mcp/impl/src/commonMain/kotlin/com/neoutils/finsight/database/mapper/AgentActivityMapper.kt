package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.AgentActivityEntity
import com.neoutils.finsight.feature.mcp.api.AgentActivity

/**
 * Between the stored row and the act.
 *
 * The two enums are declared twice on purpose and not by oversight: `:core:database` may not
 * name a feature, so the stored vocabulary and the feature's vocabulary cannot be the same
 * declaration. The `when` below is what keeps them the same set — adding a value on either side
 * stops the build until the other side has it too.
 *
 * The reference is a pair of columns on the way in and one nullable value on the way out. Only
 * both columns together mean anything, and the paired form is what the rest of the app reads.
 */
class AgentActivityMapper {

    fun toDomain(entity: AgentActivityEntity) = AgentActivity(
        id = entity.id,
        at = entity.at,
        operation = entity.operation,
        summary = entity.summary,
        outcome = when (entity.outcome) {
            AgentActivityEntity.Outcome.APPLIED -> AgentActivity.Outcome.APPLIED
            AgentActivityEntity.Outcome.REFUSED -> AgentActivity.Outcome.REFUSED
        },
        detail = entity.detail,
        reference = entity.referenceKind?.let { kind ->
            entity.referenceId?.let { id -> AgentActivity.Reference(toDomain(kind), id) }
        },
    )

    fun toEntity(domain: AgentActivity) = AgentActivityEntity(
        id = domain.id,
        at = domain.at,
        operation = domain.operation,
        summary = domain.summary,
        outcome = when (domain.outcome) {
            AgentActivity.Outcome.APPLIED -> AgentActivityEntity.Outcome.APPLIED
            AgentActivity.Outcome.REFUSED -> AgentActivityEntity.Outcome.REFUSED
        },
        detail = domain.detail,
        referenceKind = domain.reference?.let { toEntity(it.kind) },
        referenceId = domain.reference?.id,
    )

    private fun toDomain(kind: AgentActivityEntity.ReferenceKind) = when (kind) {
        AgentActivityEntity.ReferenceKind.TRANSACTION -> AgentActivity.Reference.Kind.TRANSACTION
        AgentActivityEntity.ReferenceKind.ACCOUNT -> AgentActivity.Reference.Kind.ACCOUNT
        AgentActivityEntity.ReferenceKind.CATEGORY -> AgentActivity.Reference.Kind.CATEGORY
        AgentActivityEntity.ReferenceKind.CREDIT_CARD -> AgentActivity.Reference.Kind.CREDIT_CARD
        AgentActivityEntity.ReferenceKind.INVOICE -> AgentActivity.Reference.Kind.INVOICE
        AgentActivityEntity.ReferenceKind.INSTALLMENT -> AgentActivity.Reference.Kind.INSTALLMENT
        AgentActivityEntity.ReferenceKind.RECURRING -> AgentActivity.Reference.Kind.RECURRING
        AgentActivityEntity.ReferenceKind.BUDGET -> AgentActivity.Reference.Kind.BUDGET
    }

    private fun toEntity(kind: AgentActivity.Reference.Kind) = when (kind) {
        AgentActivity.Reference.Kind.TRANSACTION -> AgentActivityEntity.ReferenceKind.TRANSACTION
        AgentActivity.Reference.Kind.ACCOUNT -> AgentActivityEntity.ReferenceKind.ACCOUNT
        AgentActivity.Reference.Kind.CATEGORY -> AgentActivityEntity.ReferenceKind.CATEGORY
        AgentActivity.Reference.Kind.CREDIT_CARD -> AgentActivityEntity.ReferenceKind.CREDIT_CARD
        AgentActivity.Reference.Kind.INVOICE -> AgentActivityEntity.ReferenceKind.INVOICE
        AgentActivity.Reference.Kind.INSTALLMENT -> AgentActivityEntity.ReferenceKind.INSTALLMENT
        AgentActivity.Reference.Kind.RECURRING -> AgentActivityEntity.ReferenceKind.RECURRING
        AgentActivity.Reference.Kind.BUDGET -> AgentActivityEntity.ReferenceKind.BUDGET
    }
}
