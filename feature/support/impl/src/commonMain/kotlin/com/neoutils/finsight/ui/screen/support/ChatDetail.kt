package com.neoutils.finsight.ui.screen.support

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.neoutils.finsight.ui.component.AdaptivePane
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The support conversation presented in the detail pane on extra-wide windows. Pane-only: it is
 * shown exclusively in the pane and dismissed (never demoted to a bottom sheet) when the window
 * shrinks. Hosts the [SupportIssueViewModel] in its own [viewModelStore] and renders the shared
 * [ChatContent] full-bleed, with the composer pinned at the pane bottom.
 */
class ChatDetail(
    private val issueId: String,
) : AdaptivePane() {

    @Composable
    override fun PaneContent() {
        val controller = LocalDetailPaneController.current
        val viewModel = koinViewModel<SupportIssueViewModel> { parametersOf(issueId) }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    SupportIssueEvent.IssueDeleted -> controller.dismiss()
                }
            }
        }

        ChatContent(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
