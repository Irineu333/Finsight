package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.error.SupportError
import com.neoutils.finsight.domain.exception.SupportException
import com.neoutils.finsight.domain.repository.ISupportRepository

class AddSupportReplyUseCase(
    private val supportRepository: ISupportRepository,
) {
    suspend operator fun invoke(issueId: String, message: String): Either<Throwable, Unit> = either {
        ensure(message.isNotBlank()) { SupportException(SupportError.EMPTY_MESSAGE) }
        catch { supportRepository.addReply(issueId, message) }.bind()
    }
}
