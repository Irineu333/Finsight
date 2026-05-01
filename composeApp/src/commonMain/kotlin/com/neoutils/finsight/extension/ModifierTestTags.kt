package com.neoutils.finsight.extension

import androidx.compose.ui.Modifier

/**
 * Marca a subárvore para que `Modifier.testTag(...)` seja exposto como
 * `resource-id` na hierarquia de acessibilidade — necessário em janelas
 * que escapam à raíz da Activity (e.g. Material3 `ModalBottomSheet`,
 * `Dialog`, `Popup`), onde a flag aplicada na MainActivity não propaga.
 *
 * No iOS, `testTag` já vira `accessibilityIdentifier` automaticamente, então
 * a implementação é no-op. Em JVM/Desktop também é no-op.
 */
expect fun Modifier.testTagsAsResourceIdSemantics(): Modifier
