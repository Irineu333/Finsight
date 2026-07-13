@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.detail_pane_close
import com.neoutils.finsight.resources.detail_pane_empty_title
import com.neoutils.finsight.ui.util.isExtraWideWindow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Fixed width of the reserved detail pane in wide windows. */
private val DetailPaneWidth = 400.dp

val LocalDetailPaneController = compositionLocalOf<DetailPaneController> {
    error("No DetailPaneController provided")
}

/**
 * A detail (`view*`) surface whose presentation adapts to window width: a `ModalBottomSheet` in narrow
 * windows and a reserved pane on the right in wide ones. It carries its own [ViewModelStore], so a
 * `koinViewModel()` inside [DetailContent] is scoped to this object — the same instance renders on both
 * surfaces, so crossing the width breakpoint transforms sheet ⇄ pane without losing state.
 *
 * Presented via [DetailPaneController], never via `ModalManager`; hence [Content] is unused.
 */
abstract class AdaptiveModal : Modal(), ViewModelStoreOwner {

    override val viewModelStore = ViewModelStore()

    @Composable
    abstract fun title(): String

    @Composable
    protected abstract fun DetailContent()

    /** Renders the pure detail content with the ViewModelStore scoped to this modal. */
    @Composable
    fun RenderContent() {
        CompositionLocalProvider(LocalViewModelStoreOwner provides this) {
            DetailContent()
        }
    }

    // Adaptive details are presented by DetailPaneController (pane or sheet), not by the ModalManager
    // overlay stack, so the Modal.Content() entry point is intentionally unused.
    @Composable
    override fun Content() = Unit

    override fun onDismissed() {
        viewModelStore.clear()
    }
}

/**
 * Single-slot controller for the adaptive detail surface, distinct from the `ModalManager` overlay
 * stack. Opening a detail while another is open **replaces** it (no internal back history).
 */
class DetailPaneController {

    var current by mutableStateOf<AdaptiveModal?>(null)
        private set

    fun show(detail: AdaptiveModal) {
        val previous = current
        current = detail
        if (previous !== detail) previous?.onDismissed()
    }

    fun dismiss() {
        val previous = current ?: return
        current = null
        previous.onDismissed()
    }
}

/**
 * Provides [LocalDetailPaneController] and renders the narrow-window detail sheet in the overlay layer.
 * Mount inside `ModalManagerHost` so transient modals (forms/confirmations) draw above the detail.
 */
@Composable
fun DetailPaneHost(
    content: @Composable () -> Unit,
) {
    val controller = koinInject<DetailPaneController>()

    CompositionLocalProvider(
        LocalDetailPaneController provides controller,
    ) {
        content()
        DetailSheetHost(controller)
    }
}

@Composable
private fun DetailSheetHost(
    controller: DetailPaneController,
) {
    if (isExtraWideWindow()) return
    val current = controller.current ?: return

    key(current.key) {
        ModalBottomSheet(
            onDismissRequest = { controller.dismiss() },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
            content = {
                current.RenderContent()
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            },
            contentWindowInsets = {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            }
        )
    }
}

/**
 * The reserved detail pane for wide windows — a fixed column on the right with a header (title + close)
 * and the scrollable detail content, or an empty-state when nothing is selected. Mount as a sibling of
 * the content in the shell's wide-branch `Row`, only when [isExtraWideWindow].
 */
@Composable
fun DetailPane(
    modifier: Modifier = Modifier,
) {
    val controller = LocalDetailPaneController.current
    val current = controller.current

    Row(modifier) {
        VerticalDivider()
        Surface(
            color = colorScheme.surface,
            modifier = Modifier
                .width(DetailPaneWidth)
                .fillMaxHeight(),
        ) {
            if (current != null) {
                key(current.key) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .padding(start = 24.dp, end = 12.dp)
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                text = current.title(),
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                            )
                            IconButton(onClick = { controller.dismiss() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(Res.string.detail_pane_close),
                                    tint = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 16.dp),
                        ) {
                            current.RenderContent()
                        }
                    }
                }
            } else {
                DetailPaneEmptyState()
            }
        }
    }
}

@Composable
private fun DetailPaneEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .size(40.dp),
        )
        Text(
            text = stringResource(Res.string.detail_pane_empty_title),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
