package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.domain.model.form.SupportIssueDraft
import kotlinx.coroutines.flow.Flow

interface ISupportRepository {

    fun observeIssues(): Flow<List<SupportIssue>>

    fun observeIssueById(issueId: String): Flow<SupportIssue?>

    suspend fun createIssue(draft: SupportIssueDraft): SupportIssue

    suspend fun addReply(issueId: String, message: String)
}
