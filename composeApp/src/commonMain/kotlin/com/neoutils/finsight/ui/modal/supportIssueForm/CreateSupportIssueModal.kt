package com.neoutils.finsight.ui.modal.supportIssueForm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.domain.model.form.SupportIssueDraft
import com.neoutils.finsight.domain.usecase.CreateSupportIssueUseCase
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.support_form_description_label
import com.neoutils.finsight.resources.support_form_submit
import com.neoutils.finsight.resources.support_form_title
import com.neoutils.finsight.resources.support_form_title_label
import com.neoutils.finsight.resources.support_form_type
import com.neoutils.finsight.resources.support_type_bug
import com.neoutils.finsight.resources.support_type_feature
import com.neoutils.finsight.resources.support_type_question
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class CreateSupportIssueModal(
    private val onIssueCreated: (String) -> Unit = {},
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val createSupportIssueUseCase = koinInject<CreateSupportIssueUseCase>()
        val modalManager = LocalModalManager.current
        val scope = rememberCoroutineScope()
        val titleState = rememberTextFieldState()
        val descriptionState = rememberTextFieldState()
        var selectedType by remember { mutableStateOf(SupportIssue.Type.BUG) }
        var isSubmitting by remember { mutableStateOf(false) }

        val title = titleState.text.toString().trim()
        val description = descriptionState.text.toString().trim()
        val canSubmit = title.isNotBlank() && description.isNotBlank() && !isSubmitting

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.support_form_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.support_form_type),
                    fontWeight = FontWeight.SemiBold,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SupportIssue.Type.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = {
                                Text(text = stringResource(type.toResource()))
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                state = titleState,
                label = {
                    Text(text = stringResource(Res.string.support_form_title_label))
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                state = descriptionState,
                label = {
                    Text(text = stringResource(Res.string.support_form_description_label))
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 5, maxHeightInLines = 8),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    scope.launch {
                        isSubmitting = true
                        createSupportIssueUseCase(
                            SupportIssueDraft(
                                type = selectedType,
                                title = title,
                                description = description,
                            )
                        ).fold(
                            ifLeft = { isSubmitting = false },
                            ifRight = { issue ->
                                isSubmitting = false
                                modalManager.dismiss()
                                onIssueCreated(issue.id)
                            },
                        )
                    }
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                    )
                }
                Text(
                    text = stringResource(Res.string.support_form_submit),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private fun SupportIssue.Type.toResource() = when (this) {
    SupportIssue.Type.BUG -> Res.string.support_type_bug
    SupportIssue.Type.FEATURE -> Res.string.support_type_feature
    SupportIssue.Type.QUESTION -> Res.string.support_type_question
}
