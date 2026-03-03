package com.neoutils.finsight.ui.modal.reportPreview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.reports_csv
import com.neoutils.finsight.resources.reports_preview_share
import com.neoutils.finsight.resources.reports_preview_share_failed
import com.neoutils.finsight.resources.reports_preview_share_success
import com.neoutils.finsight.resources.reports_preview_share_unsupported
import com.neoutils.finsight.resources.reports_pdf
import com.neoutils.finsight.resources.reports_preview_content
import com.neoutils.finsight.resources.reports_preview_file_name
import com.neoutils.finsight.resources.reports_preview_title
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.screen.reports.ReportExportResult
import com.neoutils.finsight.ui.screen.reports.ReportExportService
import com.neoutils.finsight.ui.screen.reports.GeneratedReportPreview
import com.neoutils.finsight.ui.screen.reports.ReportFormat
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class ReportPreviewModal(
    private val preview: GeneratedReportPreview,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val exportService = koinInject<ReportExportService>()
        val scope = rememberCoroutineScope()
        var exportResult by remember { mutableStateOf<ReportExportResult?>(null) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(Res.string.reports_preview_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = preview.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(text = stringResource(preview.requestedFormat.labelRes()))
                },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    disabledLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.reports_preview_file_name),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = preview.suggestedFileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.reports_preview_content),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            SelectionContainer {
                Text(
                    text = preview.content,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        exportResult = exportService.exportAndShare(preview)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.reports_preview_share))
            }

            exportResult?.let { result ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = when (result) {
                        is ReportExportResult.Success -> stringResource(
                            Res.string.reports_preview_share_success,
                            result.fileName,
                        )

                        is ReportExportResult.Unsupported -> stringResource(
                            Res.string.reports_preview_share_unsupported,
                            result.reason,
                        )

                        is ReportExportResult.Failure -> stringResource(
                            Res.string.reports_preview_share_failed,
                            result.reason,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReportFormat.labelRes() = when (this) {
    ReportFormat.PDF -> Res.string.reports_pdf
    ReportFormat.CSV -> Res.string.reports_csv
}
