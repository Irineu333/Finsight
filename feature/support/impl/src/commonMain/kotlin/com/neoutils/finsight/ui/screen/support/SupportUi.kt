@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.screen.support

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.twotone.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.SupportIssue
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.support_status_doing
import com.neoutils.finsight.resources.support_status_done
import com.neoutils.finsight.resources.support_status_open
import com.neoutils.finsight.resources.support_status_planned
import com.neoutils.finsight.resources.support_type_bug
import com.neoutils.finsight.resources.support_type_feature
import com.neoutils.finsight.resources.support_type_question
import com.neoutils.finsight.ui.component.LocalAnimatedVisibilityScope
import com.neoutils.finsight.ui.component.LocalSharedTransitionScope
import com.neoutils.finsight.ui.theme.BackgroundTileRipple
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.ui.theme.Success
import com.neoutils.finsight.ui.theme.Warning
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun SupportIssue.Type.toResource(): StringResource = when (this) {
    SupportIssue.Type.BUG -> Res.string.support_type_bug
    SupportIssue.Type.FEATURE -> Res.string.support_type_feature
    SupportIssue.Type.QUESTION -> Res.string.support_type_question
}

internal fun SupportIssue.Status.toResource(): StringResource = when (this) {
    SupportIssue.Status.OPEN -> Res.string.support_status_open
    SupportIssue.Status.PLANNED -> Res.string.support_status_planned
    SupportIssue.Status.DOING -> Res.string.support_status_doing
    SupportIssue.Status.DONE -> Res.string.support_status_done
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
    SupportIssue.Status.PLANNED -> colorScheme.primary
    SupportIssue.Status.DOING -> Info
    SupportIssue.Status.DONE -> Success
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

private val SupportIssueCardShape = RoundedCornerShape(16.dp)

/** The card's shadow at rest, the one every state below is measured against. */
private val SupportIssueCardRestElevation = 2.dp

@Composable
internal fun SupportIssueCard(
    issue: SupportIssue,
    onClick: (() -> Unit)? = null,
    descriptionMaxLines: Int = 2,
    modifier: Modifier = Modifier,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val sharedModifier = sharedTransitionScope?.run {
        animatedVisibilityScope?.let {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "support_issue_${issue.id}"),
                animatedVisibilityScope = it,
            )
        }
    } ?: Modifier

    val cardModifier = modifier
        .then(sharedModifier)
        .testTag("support_issue_card")
        .fillMaxWidth()

    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
    )

    if (onClick == null) {
        Card(
            modifier = cardModifier,
            shape = SupportIssueCardShape,
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = SupportIssueCardRestElevation),
        ) {
            SupportIssueCardContent(issue = issue, descriptionMaxLines = descriptionMaxLines)
        }
    } else {
        // The press belongs to the card, not to a layer wrapped around it. `Surface` clips to the
        // shape only *after* the caller's modifier, so an indication mounted out there paints the
        // corners the 16.dp radius cuts away; and a card that takes no `onClick` never hands its
        // interactions to its own elevation, which is then free to answer nothing. Given the click,
        // the card clips the ripple to its shape and lifts its shadow under a finger, a pointer or
        // the focus ring.
        //
        // How far the state layer itself moves from rest is [BackgroundTileRipple]'s to say, not
        // this card's: the dark scheme defines `surface` as `surfaceContainer`'s own colour, the
        // ground that function was written for, and Material's white state layer over it reads as
        // the card turning into a lighter surface rather than as a touch.
        BackgroundTileRipple {
            Card(
                onClick = onClick,
                modifier = cardModifier,
                shape = SupportIssueCardShape,
                colors = colors,
                elevation = CardDefaults.cardElevation(
                    defaultElevation = SupportIssueCardRestElevation,
                    pressedElevation = 8.dp,
                    hoveredElevation = 4.dp,
                    focusedElevation = 4.dp,
                ),
            ) {
                SupportIssueCardContent(issue = issue, descriptionMaxLines = descriptionMaxLines)
            }
        }
    }
}

@Composable
private fun SupportIssueCardContent(
    issue: SupportIssue,
    descriptionMaxLines: Int,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typeColor = issue.type.color(colorScheme)
    val dateFormats = LocalDateFormats.current

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = typeColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = issue.type.icon(),
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = stringResource(issue.type.toResource()).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            SupportPill(
                text = stringResource(issue.status.toResource()),
                color = issue.status.color(colorScheme),
            )
        }

        Text(
            text = issue.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp,
            modifier = Modifier.testTag("support_issue_title"),
        )

        Text(
            text = issue.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = descriptionMaxLines,
            overflow = TextOverflow.Ellipsis,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = dateFormats.formatInstantDate(issue.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.weight(1f))

            if (issue.isWaitingSupportReply) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = Warning,
                            shape = CircleShape,
                        )
                )
            }
        }
    }
}
