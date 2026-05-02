package com.neoutils.finsight.feature.support.repository

import com.neoutils.finsight.feature.support.model.SupportIssue
import com.neoutils.finsight.feature.support.model.SupportMessage
import com.neoutils.finsight.feature.support.model.form.SupportIssueDraft
import kotlinx.coroutines.flow.Flow

interface ISupportRepository {

    fun observeIssues(): Flow<List<SupportIssue>>

    fun observeIssueById(issueId: String): Flow<SupportIssue?>

    fun observeMessages(issueId: String): Flow<List<SupportMessage>>

    suspend fun createIssue(draft: SupportIssueDraft): SupportIssue

    suspend fun addReply(issueId: String, message: String)
}
