package com.neoutils.finsight.ui.screen.mcp

import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.feature.mcp.api.AgentActivity

/**
 * An act as the section and the full history both show it — one mapping, so the glance and the log
 * cannot describe the same act differently.
 *
 * The posting is the one reference this asks about before offering it, because it is the one that
 * opens a detail of its own: a removed posting would open an empty sheet and report a defect that
 * never happened. The entry itself is shown either way — it is testimony about the past, and what
 * it created having been removed since does not make it less true.
 */
internal suspend fun AgentActivity.toUi(
    transactionRepository: ITransactionRepository,
): McpActivityUi {
    val target = reference?.toTarget()
    return McpActivityUi(
        id = id,
        at = at,
        operation = operation,
        summary = summary,
        isRefused = outcome == AgentActivity.Outcome.REFUSED,
        detail = detail,
        target = target,
        isTargetGone = target is McpActivityTarget.Posting &&
            transactionRepository.getTransactionById(target.transactionId) == null,
    )
}
