package com.neoutils.finsight.feature.support

import com.neoutils.finsight.feature.support.model.SupportIssue
import com.neoutils.finsight.feature.support.model.SupportMessage
import com.neoutils.finsight.feature.support.model.form.SupportIssueDraft
import com.neoutils.finsight.feature.support.repository.ISupportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class UnsupportedSupportRepository : ISupportRepository {

    override fun observeIssues(): Flow<List<SupportIssue>> = flowOf(emptyList())

    override fun observeIssueById(issueId: String): Flow<SupportIssue?> = flowOf(null)

    override fun observeMessages(issueId: String): Flow<List<SupportMessage>> = flowOf(emptyList())

    override suspend fun createIssue(draft: SupportIssueDraft): SupportIssue {
        error("Support is not available on Desktop")
    }

    override suspend fun addReply(issueId: String, message: String) {
        error("Support is not available on Desktop")
    }
}
