@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.target_selector_account
import com.neoutils.finsight.resources.target_selector_credit_card
import com.neoutils.finsight.resources.target_selector_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun TargetSelector(
    selectedTarget: Transaction.Target,
    onTargetSelected: (Transaction.Target) -> Unit,
    availableTargets: List<Transaction.Target>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = when (selectedTarget) {
                Transaction.Target.ACCOUNT -> stringResource(Res.string.target_selector_account)
                Transaction.Target.CREDIT_CARD -> stringResource(Res.string.target_selector_credit_card)
            },
            onValueChange = {},
            readOnly = true,
            label = {
                Text(text = stringResource(Res.string.target_selector_label))
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableTargets.forEach { target ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = when (target) {
                                Transaction.Target.ACCOUNT -> stringResource(Res.string.target_selector_account)
                                Transaction.Target.CREDIT_CARD -> stringResource(Res.string.target_selector_credit_card)
                            },
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        onTargetSelected(target)
                        expanded = false
                    }
                )
            }
        }
    }
}
