@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.detail_pane_close
import com.neoutils.finsight.resources.detail_pane_empty_title
import com.neoutils.finsight.resources.detail_pane_error
import com.neoutils.finsight.resources.detail_pane_error_title
import com.neoutils.finsight.ui.util.isExtraWideWindow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val DetailPaneWidth = 400.dp

val LocalDetailPaneController = compositionLocalOf<DetailPaneController> {
    error("No DetailPaneController provided")
}

abstract class AdaptiveModal : Modal(), ViewModelStoreOwner {

    override val viewModelStore = ViewModelStore()

    @Composable
    protected abstract fun DetailContent()

    @Composable
    protected open fun DetailActions() = Unit

    @Composable
    fun RenderBody() {
        CompositionLocalProvider(LocalViewModelStoreOwner provides this) {
            DetailContent()
        }
    }

    @Composable
    fun RenderActions() {
        CompositionLocalProvider(LocalViewModelStoreOwner provides this) {
            DetailActions()
        }
    }

    @Composable
    override fun Content() = Unit

    // The ViewModelStore is cleared by the host (DetailPane/DetailSheetHost) when this
    // modal leaves composition — not eagerly on dismiss — so the exit animation keeps
    // reusing the same ViewModels instead of rebuilding fresh ones mid-teardown.
}

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

// Mount inside ModalManagerHost so transient modals (forms/confirmations) draw above the detail sheet.
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
        DisposableEffect(current) {
            onDispose {
                if (controller.current !== current) current.viewModelStore.clear()
            }
        }
        ModalBottomSheet(
            onDismissRequest = { controller.dismiss() },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    current.RenderBody()
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                    current.RenderActions()
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
                }
            },
            contentWindowInsets = {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            }
        )
    }
}

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
            AnimatedContent(
                targetState = current,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                contentKey = { it?.key },
                label = "detail_pane_content",
            ) { detail ->
                if (detail != null) {
                    DisposableEffect(detail) {
                        onDispose {
                            if (controller.current !== detail) detail.viewModelStore.clear()
                        }
                    }
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            contentAlignment = Alignment.CenterEnd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 12.dp)
                                .padding(vertical = 8.dp),
                        ) {
                            IconButton(onClick = { controller.dismiss() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(Res.string.detail_pane_close),
                                    tint = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val scrollState = rememberScrollState()
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            // The body is at least as tall as the pane viewport, so placeholder
                            // states (loading/error) that fillMaxSize center vertically, while
                            // regular content stays top-aligned and scrolls when it overflows.
                            val viewportHeight = maxHeight
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState),
                            ) {
                                Box(
                                    // Propagate the min height so placeholder states (which
                                    // fillMaxSize) get the full viewport and center vertically,
                                    // while regular column content stays top-aligned.
                                    propagateMinConstraints = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = viewportHeight),
                                ) {
                                    detail.RenderBody()
                                }
                            }
                        }
                        // Actions pinned at the pane bottom; the shadow appears only while the body
                        // can still scroll, separating the footer from the content beneath it.
                        Surface(
                            color = colorScheme.surface,
                            shadowElevation = if (scrollState.canScrollForward) 6.dp else 0.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            detail.RenderActions()
                        }
                    }
                } else {
                    DetailPaneEmptyState()
                }
            }
        }
    }
}

@Composable
fun DetailLoadingState(
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        CircularProgressIndicator(
            color = colorScheme.primary,
        )
    }
}

@Composable
fun DetailErrorState(
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Surface(
            color = colorScheme.errorContainer,
            shape = CircleShape,
            modifier = Modifier.size(72.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = colorScheme.onErrorContainer,
                modifier = Modifier.padding(18.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.detail_pane_error_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.detail_pane_error),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
