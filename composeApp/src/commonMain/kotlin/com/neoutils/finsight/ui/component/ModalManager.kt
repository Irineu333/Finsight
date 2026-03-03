@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)

package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.day_of_week_friday
import com.neoutils.finsight.resources.day_of_week_monday
import com.neoutils.finsight.resources.day_of_week_saturday
import com.neoutils.finsight.resources.day_of_week_sunday
import com.neoutils.finsight.resources.day_of_week_thursday
import com.neoutils.finsight.resources.day_of_week_tuesday
import com.neoutils.finsight.resources.day_of_week_wednesday
import com.neoutils.finsight.resources.month_april
import com.neoutils.finsight.resources.month_august
import com.neoutils.finsight.resources.month_december
import com.neoutils.finsight.resources.month_february
import com.neoutils.finsight.resources.month_january
import com.neoutils.finsight.resources.month_july
import com.neoutils.finsight.resources.month_june
import com.neoutils.finsight.resources.month_march
import com.neoutils.finsight.resources.month_may
import com.neoutils.finsight.resources.month_november
import com.neoutils.finsight.resources.month_october
import com.neoutils.finsight.resources.month_september
import com.neoutils.finsight.util.DateFormats
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val LocalModalManager = compositionLocalOf<ModalManager> { error("No ModalManager provided") }
val LocalNavigator = compositionLocalOf<Navigator> { Navigator {} }

class Navigator(private val onNavigate: (NavigationAction) -> Unit) {
    fun navigate(action: NavigationAction) {
        onNavigate(action)
    }
}

sealed class NavigationAction {
    data class InvoiceTransactions(val creditCardId: Long) : NavigationAction()
    data class CreditCards(val creditCardId: Long) : NavigationAction()
    data class Accounts(val accountId: Long? = null) : NavigationAction()
    data object Installments : NavigationAction()
}

class ModalManager {

    private var modalState = mutableStateListOf<Modal>()

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
    onNavigate: (NavigationAction) -> Unit = {},
    content: @Composable () -> Unit
) {

    val modalManager = koinInject<ModalManager>()
    val navigator = Navigator(onNavigate)
    val formatter = koinInject<CurrencyFormatter>()
    val dateFormats = DateFormats(
        monthNames = MonthNames(
            stringResource(Res.string.month_january),
            stringResource(Res.string.month_february),
            stringResource(Res.string.month_march),
            stringResource(Res.string.month_april),
            stringResource(Res.string.month_may),
            stringResource(Res.string.month_june),
            stringResource(Res.string.month_july),
            stringResource(Res.string.month_august),
            stringResource(Res.string.month_september),
            stringResource(Res.string.month_october),
            stringResource(Res.string.month_november),
            stringResource(Res.string.month_december),
        ),
        dayOfWeekNames = DayOfWeekNames(
            stringResource(Res.string.day_of_week_monday),
            stringResource(Res.string.day_of_week_tuesday),
            stringResource(Res.string.day_of_week_wednesday),
            stringResource(Res.string.day_of_week_thursday),
            stringResource(Res.string.day_of_week_friday),
            stringResource(Res.string.day_of_week_saturday),
            stringResource(Res.string.day_of_week_sunday),
        ),
    )

    CompositionLocalProvider(
        LocalModalManager provides modalManager,
        LocalNavigator provides navigator,
        LocalCurrencyFormatter provides formatter,
        LocalDateFormats provides dateFormats,
    ) {
        content()
        modalManager.Content()
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

        ModalBottomSheet(
            onDismissRequest = {
                manager.dismiss(this@ModalBottomSheet)
            },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
            content = {

                CompositionLocalProvider(providedValue) {
                    BottomSheetContent()
                }

                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
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
