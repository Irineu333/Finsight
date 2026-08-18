@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.mcpActivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.ClearAgentActivityUseCase
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.ui.screen.mcp.McpActivityUi
import com.neoutils.finsight.ui.screen.mcp.toUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The whole agent log, newest first — what the section's glance is a window onto.
 *
 * Reading and clearing are all it offers, because they are all the log has: an entry is never
 * edited and never removed on its own. What clearing removes is the record of what was done and
 * **nothing that was done** — every posting stays exactly where it is, which is what makes offering
 * it safe at all.
 */
class McpActivityViewModel(
    activityRepository: IAgentActivityRepository,
    private val transactionRepository: ITransactionRepository,
    private val clearAgentActivity: ClearAgentActivityUseCase,
) : ViewModel() {

    val uiState = activityRepository.observeAll()
        .mapLatest { entries ->
            McpActivityUiState(entries = entries.toUi(transactionRepository), isLoading = false)
        }
        // The whole log is mapped on every emission, and an insert or a prune is an emission.
        // Without a context of its own the mapping would run in the collector's — `viewModelScope`,
        // which is `Main.immediate` — so a page of five thousand rows would be mapped on the frame
        // the screen is drawing. Named rather than inherited: `Unconfined` would run the mapping
        // wherever the log happens to emit, which makes "off the main thread" a property of the
        // upstream rather than a guarantee made here.
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = McpActivityUiState(),
        )

    fun onClear() {
        viewModelScope.launch { clearAgentActivity() }
    }
}

data class McpActivityUiState(
    val entries: List<McpActivityUi> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()
}
