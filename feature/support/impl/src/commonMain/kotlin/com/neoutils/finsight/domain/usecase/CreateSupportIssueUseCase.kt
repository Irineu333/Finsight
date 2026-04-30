package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.error.SupportError
import com.neoutils.finsight.domain.exception.SupportException
import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.domain.model.form.SupportIssueDraft
import com.neoutils.finsight.domain.repository.ISupportRepository

class CreateSupportIssueUseCase(
    private val supportRepository: ISupportRepository,
) {
    suspend operator fun invoke(draft: SupportIssueDraft): Either<Throwable, SupportIssue> = either {
        ensure(draft.title.isNotBlank()) { SupportException(SupportError.EMPTY_TITLE) }
        ensure(draft.description.isNotBlank()) { SupportException(SupportError.EMPTY_DESCRIPTION) }
        catch { supportRepository.createIssue(draft) }.bind()
    }
}
