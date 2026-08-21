@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)

package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.neoutils.finsight.ui.util.exposeTestTags
import com.neoutils.finsight.util.UiText
import org.koin.compose.koinInject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val LocalModalManager = compositionLocalOf<ModalManager> { error("No ModalManager provided") }

class ModalManager {

    private var modalState = mutableStateListOf<Modal>()

    /** The modal the user is interacting with — everything below it is covered. */
    val top: Modal? get() = modalState.lastOrNull()

    /**
     * Surfaces why an action was refused, as a modal over the one that tried it.
     *
     * A modal action that fails used to record the exception and stop there: the
     * sheet simply did not close and the user was told nothing. Solving that per
     * modal meant plumbing in every ViewModel, and the ones that were missed stayed
     * silent — so it lives here, where every modal already is.
     */
    fun showError(message: UiText) {
        show(ErrorModal(message))
    }

    /**
     * States that a modal action's result is one the user cannot otherwise see.
     *
     * An export leaves for a place the screen does not read from, and a restore closes its
     * own confirmation without a trace — both look identical to nothing having happened at
     * all unless something says so. Solving that per modal meant plumbing in every
     * ViewModel, and the ones that were missed stayed silent — so it lives here, beside
     * [showError], where every modal already is.
     */
    fun showSuccess(message: UiText) {
        show(SuccessModal(message))
    }

    fun show(modal: Modal) {
        modalState.add(modal)
    }

    @Composable
    fun Content() {
        modalState.forEach { modal ->
            key(modal.key) {
                modal.Content()
            }
        }
    }

    fun dismiss() {
        modalState.lastOrNull()?.let(::dismiss)
    }

    fun dismiss(modal: Modal) {
        if (!modalState.remove(modal)) return
        modal.onDismissed()
    }

    fun dismissAll() {
        modalState.forEach(Modal::onDismissed)
        modalState.clear()
    }
}

@Composable
fun ModalManagerHost(
    content: @Composable () -> Unit
) {
    val modalManager = koinInject<ModalManager>()

    // Opening a modal is an interaction with something else: whatever the user was typing into
    // below it gives up the focus, so the keyboard does not stay over the modal that covers it.
    DismissKeyboardWhenCovered(covered = modalManager.top != null)

    CompositionLocalProvider(
        LocalModalManager provides modalManager,
    ) {
        content()
        modalManager.Content()
    }
}

/**
 * Releases the focus — and with it the keyboard — of the layer that just got covered.
 *
 * Android closes the keyboard on its own when a modal opens, because the new sheet takes the
 * window focus; iOS keeps the same window, so the field stays focused and the keyboard is left
 * standing over the modal. This makes the behaviour the platform gives us for free explicit,
 * and therefore the same everywhere.
 */
@Composable
internal fun DismissKeyboardWhenCovered(covered: Boolean) {

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(covered) {
        if (covered) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
}

abstract class Modal {

    val key = Uuid.random().toString()

    open fun onDismissed() = Unit

    @Composable
    abstract fun Content()
}

abstract class ModalBottomSheet : Modal(), ViewModelStoreOwner {

    override val viewModelStore = ViewModelStore()

    private val providedValue get() = LocalViewModelStoreOwner provides this

    @Composable
    override fun Content() {

        val manager = LocalModalManager.current
        val modal = this
        val dismissible = isDismissible()

        ModalBottomSheet(
            onDismissRequest = {
                manager.dismiss(modal)
            },
            // A sheet is its own composition root, so the app window's opt-in does not reach it:
            // without this, no test tag inside any modal is visible to the E2E driver.
            modifier = Modifier.exposeTestTags(),
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
            // A refusal closes the three ways out at their source rather than ignoring the
            // request they raise: the swipe has already played the hide animation by the time
            // it asks, so answering "no" then leaves a sheet nobody can see over a scrim
            // nobody can dismiss.
            sheetGesturesEnabled = dismissible,
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = dismissible,
                shouldDismissOnClickOutside = dismissible,
            ),
            content = {

                // Called from inside the sheet: the field that gives up the focus is the one this
                // sheet owns, and only this sheet's focus scope can reach it.
                DismissKeyboardWhenCovered(covered = manager.top !== modal)

                CompositionLocalProvider(providedValue) {
                    BottomSheetContent()
                }

                // The keyboard is a bottom inset like any other — union, not sum, because the
                // navigation bar sits behind it while it is up.
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars.union(WindowInsets.ime)))
            },
            contentWindowInsets = {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            }
        )
    }

    override fun onDismissed() {
        viewModelStore.clear()
    }

    /**
     * Whether the user may close this sheet — the scrim, the swipe and the back press alike.
     *
     * Read in composition, because the answer changes while the sheet is up: a modal is built
     * once and rendered by the manager that holds it, so a value fixed at construction would
     * still describe the moment it was created. Dismissible unless a subclass says otherwise —
     * a sheet the user cannot leave is the exception, and it is the exception that argues for
     * itself.
     *
     * Refusing takes nothing away from the manager: [ModalManager.dismiss] still removes the
     * modal, so whatever put the sheet up can always take it down.
     */
    @Composable
    protected open fun isDismissible(): Boolean = true

    @Composable
    protected abstract fun ColumnScope.BottomSheetContent()
}
