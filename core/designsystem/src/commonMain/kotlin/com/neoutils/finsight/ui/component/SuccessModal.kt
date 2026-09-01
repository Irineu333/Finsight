package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.success_modal_dismiss
import com.neoutils.finsight.ui.theme.OnSuccess
import com.neoutils.finsight.ui.theme.Success
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource

/**
 * What a modal action achieved, shown over the modal whose action just completed.
 *
 * There is deliberately no title, for the same reason [ErrorModal] has none: the sentence
 * is the content, and a heading that says "done" only restates the first half of it below
 * it. Unlike a refusal, a success names nothing left to act on — there is no balance to
 * resolve, no category still in use — so it does not need the modal beneath it to stay
 * reachable, and dismissing this one is the whole of what the user has left to do.
 *
 * [Success] has no `colorScheme` role of its own — Material 3 does not define one — so the
 * circle and its icon are this fixed pair rather than a theme role: [Success] behind,
 * [OnSuccess] on top, the same in light and dark because neither changes with the theme.
 */
class SuccessModal(
    private val message: UiText,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val manager = LocalModalManager.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                color = Success,
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = OnSuccess,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Text(
                text = stringUiText(message),
                style = typography.titleMedium,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = { manager.dismiss(this@SuccessModal) },
                // The sheet is its own composition root and covers everything under it, so a
                // journey that passes through a success has to dismiss it before it can read
                // anything again — and reaching a button by its label is what breaks the next
                // time the copy changes (`.maestro/README.md` §5.2, padrão 1).
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("success_modal_dismiss"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.success_modal_dismiss),
                    style = typography.titleSmall,
                )
            }
        }
    }
}
