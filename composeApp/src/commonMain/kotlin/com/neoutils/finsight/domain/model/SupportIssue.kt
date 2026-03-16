@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class SupportIssue(
    val id: String,
    val type: Type,
    val title: String,
    val description: String,
    val status: Status = Status.OPEN,
    val messages: List<SupportMessage>,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = createdAt,
) {
    val lastMessage: SupportMessage?
        get() = messages.maxByOrNull { it.createdAt }

    val isWaitingSupportReply: Boolean
        get() = when {
            status == Status.RESOLVED -> false
            lastMessage?.author == SupportMessage.Author.USER -> true
            lastMessage?.author == SupportMessage.Author.SUPPORT -> false
            else -> status == Status.OPEN || status == Status.IN_REVIEW
        }

    enum class Type {
        BUG,
        FEATURE,
        QUESTION,
    }

    enum class Status {
        OPEN,
        IN_REVIEW,
        ANSWERED,
        PLANNED,
        RESOLVED,
    }
}

data class SupportMessage(
    val id: String,
    val author: Author,
    val body: String,
    val createdAt: Instant = Clock.System.now(),
) {
    enum class Author {
        USER,
        SUPPORT,
    }
}
