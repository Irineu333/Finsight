package com.neoutils.finsight.ui.screen.support

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.support_status_answered
import com.neoutils.finsight.resources.support_status_in_review
import com.neoutils.finsight.resources.support_status_open
import com.neoutils.finsight.resources.support_status_planned
import com.neoutils.finsight.resources.support_status_resolved
import com.neoutils.finsight.resources.support_type_bug
import com.neoutils.finsight.resources.support_type_feature
import com.neoutils.finsight.resources.support_type_question
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.ui.theme.Success
import com.neoutils.finsight.ui.theme.Warning
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource

internal fun SupportIssue.Type.toResource(): StringResource = when (this) {
    SupportIssue.Type.BUG -> Res.string.support_type_bug
    SupportIssue.Type.FEATURE -> Res.string.support_type_feature
    SupportIssue.Type.QUESTION -> Res.string.support_type_question
}

internal fun SupportIssue.Status.toResource(): StringResource = when (this) {
    SupportIssue.Status.OPEN -> Res.string.support_status_open
    SupportIssue.Status.IN_REVIEW -> Res.string.support_status_in_review
    SupportIssue.Status.ANSWERED -> Res.string.support_status_answered
    SupportIssue.Status.PLANNED -> Res.string.support_status_planned
    SupportIssue.Status.RESOLVED -> Res.string.support_status_resolved
}

internal fun SupportIssue.Type.icon(): ImageVector = when (this) {
    SupportIssue.Type.BUG -> Icons.Default.BugReport
    SupportIssue.Type.FEATURE -> Icons.Default.AutoAwesome
    SupportIssue.Type.QUESTION -> Icons.AutoMirrored.Filled.HelpOutline
}

internal fun SupportIssue.Type.color(colorScheme: ColorScheme): Color = when (this) {
    SupportIssue.Type.BUG -> colorScheme.error
    SupportIssue.Type.FEATURE -> colorScheme.primary
    SupportIssue.Type.QUESTION -> Info
}

internal fun SupportIssue.Status.color(colorScheme: ColorScheme): Color = when (this) {
    SupportIssue.Status.OPEN -> Warning
    SupportIssue.Status.IN_REVIEW -> Info
    SupportIssue.Status.ANSWERED -> colorScheme.primary
    SupportIssue.Status.PLANNED -> Success
    SupportIssue.Status.RESOLVED -> Success
}

internal fun kotlin.time.Instant.toRelativeDateLabel(): String {
    return toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}

@Composable
internal fun SupportPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
