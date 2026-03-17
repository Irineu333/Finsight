@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.domain.model.SupportMessage
import com.neoutils.finsight.domain.model.form.SupportIssueDraft
import com.neoutils.finsight.domain.repository.ISupportRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class FirebaseSupportRepository : ISupportRepository {

    private val auth get() = Firebase.auth
    private val collection get() = Firebase.firestore.collection("support_issues")

    private suspend fun currentUserId(): String {
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }

        return requireNotNull(auth.currentUser?.uid) {
            "Failed to authenticate user for support."
        }
    }

    override fun observeIssues(): Flow<List<SupportIssue>> = flow {
        val userId = currentUserId()
        emitAll(
            collection
                .where { "userId" equalTo userId }
                .snapshots
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        runCatching {
                            doc.data<IssueDocument>().toDomain(doc.id)
                        }.getOrNull()
                    }.sortedByDescending {
                        it.updatedAt
                    }
                }
        )
    }

    override fun observeIssueById(issueId: String): Flow<SupportIssue?> = flow {
        emitAll(
            collection.document(issueId).snapshots.map { doc ->
                if (doc.exists) {
                    runCatching {
                        doc.data<IssueDocument>().toDomain(doc.id)
                    }.getOrNull()
                } else null
            }
        )
    }

    override fun observeMessages(issueId: String): Flow<List<SupportMessage>> = flow {
        emitAll(
            collection.document(issueId)
                .collection("messages")
                .snapshots(includeMetadataChanges = true)
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        runCatching {
                            doc.data<MessageDocument>().toDomain(doc.id, doc.metadata.hasPendingWrites)
                        }.getOrNull()
                    }.sortedBy { it.createdAt }
                }
        )
    }

    override suspend fun createIssue(draft: SupportIssueDraft): SupportIssue {
        val userId = currentUserId()
        val now = Clock.System.now()

        val document = IssueDocument(
            userId = userId,
            type = draft.type.name,
            title = draft.title.trim(),
            description = draft.description.trim(),
            status = SupportIssue.Status.OPEN.name,
            pendingForSupportReply = true,
            createdAtMs = now.toEpochMilliseconds(),
            updatedAtMs = now.toEpochMilliseconds(),
        )

        val docRef = collection.add(document)
        return document.toDomain(docRef.id)
    }

    override suspend fun addReply(issueId: String, message: String) {
        val now = Clock.System.now()
        val docRef = collection.document(issueId)
        val current = docRef.get().data<IssueDocument>()

        val newMessage = MessageDocument(
            author = SupportMessage.Author.USER.name,
            body = message.trim(),
            createdAtMs = now.toEpochMilliseconds(),
        )

        val newStatus = when (SupportIssue.Status.valueOf(current.status)) {
            SupportIssue.Status.ANSWERED,
            SupportIssue.Status.PLANNED,
            SupportIssue.Status.RESOLVED -> SupportIssue.Status.IN_REVIEW.name

            else -> current.status
        }

        collection.document(issueId)
            .collection("messages")
            .add(newMessage)

        docRef.update(
            "pendingForSupportReply" to true,
            "updatedAtMs" to now.toEpochMilliseconds(),
            "status" to newStatus,
        )
    }
}

@Serializable
private data class IssueDocument(
    val userId: String = "",
    val type: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "",
    val pendingForSupportReply: Boolean = false,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
)

@Serializable
private data class MessageDocument(
    val author: String = "",
    val body: String = "",
    val createdAtMs: Long = 0L,
)

private fun IssueDocument.toDomain(id: String): SupportIssue {
    return SupportIssue(
        id = id,
        type = SupportIssue.Type.valueOf(type),
        title = title,
        description = description,
        status = SupportIssue.Status.valueOf(status),
        isWaitingSupportReply = pendingForSupportReply,
        createdAt = Instant.fromEpochMilliseconds(createdAtMs),
        updatedAt = Instant.fromEpochMilliseconds(updatedAtMs),
    )
}

private fun MessageDocument.toDomain(id: String, isPending: Boolean = false) = SupportMessage(
    id = id,
    author = SupportMessage.Author.valueOf(author),
    body = body,
    createdAt = Instant.fromEpochMilliseconds(createdAtMs),
    isPending = isPending,
)
