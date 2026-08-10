@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)

package com.neoutils.finsight.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
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

/**
 * The sheet's drag handle, which is also how the keyboard is put away.
 *
 * A sheet that asks for text has to offer a way to stop asking, and tapping the chrome above the
 * fields is the gesture people already try. It is also the only spot on a sheet guaranteed to be
 * neither a control nor under the keyboard, whatever the sheet holds — which is what makes it the
 * one handle an E2E flow can reach. `hideKeyboard` is not that handle: on iOS Maestro implements it
 * as two small swipes in the middle of the screen, which on a scrollable sheet scrolls the sheet
 * instead of dismissing anything, and the command fails.
 *
 * Both sheet presentations render it — this one, and the phone presentation of an `AdaptiveModal`
 * in `DetailSheetHost` — so "the handle" means the same thing wherever a sheet came from.
 */
@Composable
internal fun ReleaseFocusDragHandle() {

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RELEASE_FOCUS_TEST_TAG)
            .pointerInput(Unit) {
                detectTapGestures {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BottomSheetDefaults.DragHandle()
    }
}

private const val RELEASE_FOCUS_TEST_TAG = "modal_release_keyboard"

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
            dragHandle = { ReleaseFocusDragHandle() },
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

    @Composable
    protected abstract fun ColumnScope.BottomSheetContent()
}
