package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository

/**
 * Emptying the agent activity log, at the user's request.
 *
 * It has an owner of its own because it has **two** callers — the glance in the server section and
 * the full history — and an operation written twice is one edit away from being written differently
 * the second time.
 *
 * What it removes is the record of what was done and **nothing that was done**: every posting the
 * discarded acts produced stays exactly where it is. That is what makes offering it safe at all —
 * the log is a trace, and the ledger is the truth. It is deliberately not reachable by an agent: a
 * client able to erase its own trail leaves none.
 */
class ClearAgentActivityUseCase(
    private val repository: IAgentActivityRepository,
) {
    suspend operator fun invoke() = repository.clear()
}
