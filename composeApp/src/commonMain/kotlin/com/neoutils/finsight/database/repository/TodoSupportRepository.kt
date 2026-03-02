@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.domain.model.SupportMessage
import com.neoutils.finsight.domain.model.form.SupportIssueDraft
import com.neoutils.finsight.domain.repository.ISupportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

// TODO: replace this in-memory repository with the backend/service chosen for support.
class TodoSupportRepository : ISupportRepository {

    private val issues = MutableStateFlow(seedIssues())

    override fun observeIssues(): Flow<List<SupportIssue>> = issues

    override fun observeIssueById(issueId: String): Flow<SupportIssue?> {
        return issues.map { items -> items.firstOrNull { it.id == issueId } }
    }

    override suspend fun createIssue(draft: SupportIssueDraft): SupportIssue {
        val now = Clock.System.now()
        val issueId = "issue-${now.toEpochMilliseconds()}-${issues.value.size}"
        val messageId = "message-${now.toEpochMilliseconds()}"

        val issue = SupportIssue(
            id = issueId,
            type = draft.type,
            title = draft.title.trim(),
            status = SupportIssue.Status.OPEN,
            messages = listOf(
                SupportMessage(
                    id = messageId,
                    author = SupportMessage.Author.USER,
                    body = draft.message.trim(),
                    createdAt = now,
                )
            ),
            createdAt = now,
            updatedAt = now,
        )

        issues.update { current ->
            listOf(issue) + current
        }

        return issue
    }

    override suspend fun addReply(issueId: String, message: String) {
        val now = Clock.System.now()

        issues.update { current ->
            current.map { issue ->
                if (issue.id != issueId) {
                    issue
                } else {
                    issue.copy(
                        status = when (issue.status) {
                            SupportIssue.Status.ANSWERED,
                            SupportIssue.Status.PLANNED,
                            SupportIssue.Status.RESOLVED -> SupportIssue.Status.IN_REVIEW
                            else -> issue.status
                        },
                        messages = issue.messages + SupportMessage(
                            id = "message-${now.toEpochMilliseconds()}-${issue.messages.size}",
                            author = SupportMessage.Author.USER,
                            body = message.trim(),
                            createdAt = now,
                        ),
                        updatedAt = now,
                    )
                }
            }.sortedByDescending { it.updatedAt }
        }
    }

    private fun seedIssues(): List<SupportIssue> {
        val now = Clock.System.now()

        val featureIssue = SupportIssue(
            id = "seed-feature",
            type = SupportIssue.Type.FEATURE,
            title = "Exportar relatórios em PDF",
            status = SupportIssue.Status.PLANNED,
            messages = listOf(
                SupportMessage(
                    id = "seed-feature-1",
                    author = SupportMessage.Author.USER,
                    body = "Seria útil exportar um resumo mensal em PDF para compartilhar com o contador.",
                    createdAt = now - 5.days,
                ),
                SupportMessage(
                    id = "seed-feature-2",
                    author = SupportMessage.Author.SUPPORT,
                    body = "Pedido registrado. Essa melhoria entrou no backlog e está sendo avaliada para uma próxima versão.",
                    createdAt = now - 4.days - 6.hours,
                ),
            ),
            createdAt = now - 5.days,
            updatedAt = now - 4.days - 6.hours,
        )

        val bugIssue = SupportIssue(
            id = "seed-bug",
            type = SupportIssue.Type.BUG,
            title = "Saldo demora para atualizar após excluir transação",
            status = SupportIssue.Status.OPEN,
            messages = listOf(
                SupportMessage(
                    id = "seed-bug-1",
                    author = SupportMessage.Author.USER,
                    body = "Depois de excluir uma transação na tela de conta, o saldo só atualiza quando saio e entro novamente.",
                    createdAt = now - 18.hours,
                )
            ),
            createdAt = now - 18.hours,
            updatedAt = now - 18.hours,
        )

        return listOf(bugIssue, featureIssue)
    }
}
