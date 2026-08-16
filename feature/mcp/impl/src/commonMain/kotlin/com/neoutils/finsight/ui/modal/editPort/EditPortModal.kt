package com.neoutils.finsight.ui.modal.editPort

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.mcp_port_apply
import com.neoutils.finsight.resources.mcp_port_error_invalid
import com.neoutils.finsight.resources.mcp_port_helper
import com.neoutils.finsight.resources.mcp_port_label
import com.neoutils.finsight.resources.mcp_port_title
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import org.jetbrains.compose.resources.stringResource

/**
 * Choosing the port the server listens on.
 *
 * The port is part of the address, so this is reached from the address and not from a field of its
 * own standing in the section: what the user is changing is where a client points, and that is one
 * decision rather than a setting that happens to be nearby.
 *
 * **What is typed here is a draft until it is confirmed.** The check runs on confirmation and not
 * per keystroke, because a port applied as it is typed would rebind on "8", then "84", then "847"
 * on the way to 8477 — and each of those is a real bind against a real socket. Confirming is
 * refused while the number is not a port or is the one already in use, so the sheet never closes
 * having done nothing.
 */
class EditPortModal(
    private val current: Int,
    private val onConfirm: (Int) -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val modalManager = LocalModalManager.current
        val modal = this@EditPortModal

        var draft by remember { mutableStateOf(current.toString()) }

        val port = draft.toIntOrNull()
        val isPort = port in McpServerController.VALID_PORTS
        val canConfirm = isPort && port != current

        // Silent while the field is being filled: an error under a half-typed number is about a
        // port the user has not finished naming.
        val showsError = draft.isNotEmpty() && !isPort

        fun confirm() {
            if (!canConfirm) return
            onConfirm(requireNotNull(port))
            modalManager.dismiss(modal)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(Res.string.mcp_port_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.filter { char -> char.isDigit() }.take(PORT_DIGITS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mcp_port_field"),
                label = { Text(text = stringResource(Res.string.mcp_port_label)) },
                singleLine = true,
                isError = showsError,
                supportingText = {
                    Text(
                        text = stringResource(
                            if (showsError) Res.string.mcp_port_error_invalid else Res.string.mcp_port_helper
                        ),
                        modifier = Modifier.testTag("mcp_port_supporting_text"),
                        fontSize = 12.sp,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = ::confirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mcp_port_apply"),
                enabled = canConfirm,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.mcp_port_apply),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    private companion object {

        /** 65535 is five digits; anything longer is not a port being typed. */
        const val PORT_DIGITS = 5
    }
}
