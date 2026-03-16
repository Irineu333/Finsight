package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.error.SupportError
import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.domain.model.form.SupportIssueDraft
import com.neoutils.finsight.domain.repository.ISupportRepository

class CreateSupportIssueUseCase(
    private val supportRepository: ISupportRepository,
) {
    suspend operator fun invoke(draft: SupportIssueDraft): Either<SupportError, SupportIssue> {
        if (draft.title.isBlank()) return SupportError.EMPTY_TITLE.left()
        if (draft.message.isBlank()) return SupportError.EMPTY_DESCRIPTION.left()
        return runCatching { supportRepository.createIssue(draft) }.fold(
            onSuccess = { Either.Right(it) },
            onFailure = { Either.Left(SupportError.UNKNOWN) },
        )
    }
}
