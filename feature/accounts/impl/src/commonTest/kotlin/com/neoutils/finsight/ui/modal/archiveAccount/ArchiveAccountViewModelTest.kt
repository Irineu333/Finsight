package com.neoutils.finsight.ui.modal.archiveAccount

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.RecordingAnalytics
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.usecase.ArchiveAccountUseCase
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.screen.accounts.FlatEntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveAccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private object Archives : ArchiveAccountUseCase {
        override suspend fun invoke(accountId: Long): Either<Throwable, Unit> = Unit.right()
    }

    private object SilentCrashlytics : Crashlytics {
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) = Unit
    }

    /**
     * Retiring and deleting are two different things the user does, and both reported
     * themselves under the same name — which left the retirement of an account
     * indistinguishable from its removal in every report built on these events.
     */
    @Test
    fun `retiring an account reports itself as an archive, not as a deletion`() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val viewModel = ArchiveAccountViewModel(
            account = Account(
                id = 1L,
                name = "Wallet",
                type = AccountType.ASSET,
                isArchived = false,
                currency = "BRL",
            ),
            archiveAccountUseCase = Archives,
            entryRepository = FlatEntryRepository,
            modalManager = ModalManager(),
            analytics = analytics,
            crashlytics = SilentCrashlytics,
        )

        viewModel.archiveAccount()
        runCurrent()

        assertEquals(listOf("archive_account"), analytics.events.map { it.name })
    }
}
