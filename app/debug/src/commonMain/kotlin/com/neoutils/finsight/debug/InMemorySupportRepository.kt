@file:OptIn(ExperimentalTime::class, ExperimentalAtomicApi::class)

package com.neoutils.finsight.debug

import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.domain.model.SupportMessage
import com.neoutils.finsight.domain.model.form.SupportIssueDraft
import com.neoutils.finsight.feature.support.api.ISupportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Support, answered by this process instead of by Firestore.
 *
 * The shipped repository talks to a backend: it signs in anonymously, writes to a collection and
 * reads its snapshots back. That makes the support journey untestable on a device — a flow would
 * need network, credentials and a real project, and would leave rows behind in it. This one holds
 * the same conversation in memory, so the screens can be driven the way every other feature's are.
 *
 * **It is memory, not storage.** Everything here dies with the process, so a flow that relaunches
 * the app — as the credit-card and recurring stories do to move the clock — finds support empty
 * again. That is a property to write flows around, not a bug: the issue and its replies have to be
 * created and read back inside one run.
 *
 * It reads the injected [Clock] rather than `Clock.System`, so an issue created after a time jump
 * is dated where the rest of the app thinks it is.
 */
class InMemorySupportRepository(
    private val clock: Clock,
) : ISupportRepository {

    private val issues = MutableStateFlow<List<SupportIssue>>(emptyList())
    private val messages = MutableStateFlow<Map<String, List<SupportMessage>>>(emptyMap())

    // Firestore hands out an id per document; here a counter does, and being sequential is worth
    // more than being opaque — a failure log names "issue 1", not a random string.
    private val ids = AtomicLong(0)

    init {
        // Logged for the same reason the clock shift is: a substitution that did not take looks
        // exactly like a screen that is slow, and this line is the only place that says which
        // repository answered.
        println("$TAG: support answered in memory; nothing reaches Firestore from this build")
    }

    /** Newest conversation first, which is the order the backend query asks for. */
    override fun observeIssues(): Flow<List<SupportIssue>> = issues.map { list ->
        list.sortedByDescending { it.updatedAt }
    }

    override fun observeIssueById(issueId: String): Flow<SupportIssue?> = issues.map { list ->
        list.find { it.id == issueId }
    }

    /** Oldest message first: a conversation is read downwards. */
    override fun observeMessages(issueId: String): Flow<List<SupportMessage>> = messages.map { byIssue ->
        byIssue[issueId].orEmpty().sortedBy { it.createdAt }
    }

    override suspend fun createIssue(draft: SupportIssueDraft): SupportIssue {
        val now = clock.now()

        val issue = SupportIssue(
            id = ids.incrementAndFetch().toString(),
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
        val now = clock.now()

        messages.update { byIssue ->
            val reply = SupportMessage(
                id = ids.incrementAndFetch().toString(),
                author = SupportMessage.Author.USER,
                body = message.trim(),
                createdAt = now,
                // The flag means "written, not yet acknowledged by the backend". There is no
                // backend here, so a reply is acknowledged the moment it is made.
                isPending = false,
            )

            byIssue + (issueId to byIssue[issueId].orEmpty() + reply)
        }

        // The same two fields the shipped repository updates beside the write: replying puts the
        // conversation back in support's court and lifts it to the top of the list.
        issues.update { list ->
            list.map { issue ->
                if (issue.id == issueId) {
                    issue.copy(isWaitingSupportReply = true, updatedAt = now)
                } else {
                    issue
                }
            }
        }
    }
}

private const val TAG = "InMemorySupport"
