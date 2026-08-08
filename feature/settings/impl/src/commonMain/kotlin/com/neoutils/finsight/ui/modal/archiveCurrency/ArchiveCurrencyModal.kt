package com.neoutils.finsight.ui.modal.archiveCurrency

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.currencies_archive_confirm_action
import com.neoutils.finsight.resources.currencies_archive_confirm_message
import com.neoutils.finsight.resources.currencies_archive_confirm_title
import com.neoutils.finsight.ui.component.ModalBottomSheet
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Archiving a currency: it stops being offered, and **nothing else happens**.
 *
 * The message says so because the word invites the wrong reading. An account in this
 * currency stays active and goes on taking entries; its rate observations stay in the
 * archive and go on being read, so it still serves as a conversion pivot. Archiving
 * answers *"stop offering me this"*, never *"this is no longer valid"*.
 */
class ArchiveCurrencyModal(
    private val code: String,
    private val label: String,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<ArchiveCurrencyViewModel> { parametersOf(code) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(Res.string.currencies_archive_confirm_title, label),
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.currencies_archive_confirm_message),
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.archive() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
            ) {
                Text(
                    text = stringResource(Res.string.currencies_archive_confirm_action),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
