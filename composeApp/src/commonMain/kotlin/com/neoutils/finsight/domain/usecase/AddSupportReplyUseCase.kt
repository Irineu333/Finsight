package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.error.SupportError
import com.neoutils.finsight.domain.repository.ISupportRepository

class AddSupportReplyUseCase(
    private val supportRepository: ISupportRepository,
) {
    suspend operator fun invoke(issueId: String, message: String): Either<SupportError, Unit> {
        if (message.isBlank()) return SupportError.EMPTY_MESSAGE.left()
        return runCatching { supportRepository.addReply(issueId, message) }.fold(
            onSuccess = { Either.Right(Unit) },
            onFailure = { Either.Left(SupportError.UNKNOWN) },
        )
    }
}
