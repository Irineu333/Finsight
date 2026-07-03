@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.e2e.support

import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.domain.model.SupportMessage
import com.neoutils.finsight.domain.model.form.SupportIssueDraft
import com.neoutils.finsight.domain.repository.ISupportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class InMemorySupportRepository : ISupportRepository {

    private val issues = MutableStateFlow<List<SupportIssue>>(emptyList())
    private val messages = MutableStateFlow<Map<String, List<SupportMessage>>>(emptyMap())

    override fun observeIssues(): Flow<List<SupportIssue>> = issues.asStateFlow()

    override fun observeIssueById(issueId: String): Flow<SupportIssue?> =
        issues.map { list -> list.firstOrNull { it.id == issueId } }

    override fun observeMessages(issueId: String): Flow<List<SupportMessage>> =
        messages.map { map -> map[issueId].orEmpty() }

    override suspend fun createIssue(draft: SupportIssueDraft): SupportIssue {
        val now = Clock.System.now()
        val issue = SupportIssue(
            id = "e2e-issue-${issues.value.size + 1}",
            type = draft.type,
            title = draft.title.trim(),
            description = draft.description.trim(),
            status = SupportIssue.Status.OPEN,
            isActive = true,
            isWaitingSupportReply = true,
            createdAt = now,
            updatedAt = now,
        )
        issues.update { it + issue }
        return issue
    }

    override suspend fun addReply(issueId: String, message: String) {
        val now = Clock.System.now()
        messages.update { map ->
            val current = map[issueId].orEmpty()
            val newMessage = SupportMessage(
                id = "e2e-msg-${current.size + 1}",
                author = SupportMessage.Author.USER,
                body = message.trim(),
                createdAt = now,
            )
            map + (issueId to (current + newMessage))
        }
        issues.update { list ->
            list.map { issue ->
                if (issue.id == issueId) {
                    issue.copy(isWaitingSupportReply = true, updatedAt = now)
                } else issue
            }
        }
    }
}
