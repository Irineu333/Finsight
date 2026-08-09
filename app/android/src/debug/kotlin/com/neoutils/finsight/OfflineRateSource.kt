package com.neoutils.finsight

import com.neoutils.finsight.domain.repository.IRemoteRateSource
import com.neoutils.finsight.domain.repository.RemoteQuote

/**
 * The rate source a debug build answers with: **it quotes nothing, and says so.**
 *
 * The app synchronises rates on every launch, against every currency it offers. On a
 * device with a network that writes real `REMOTE` rows behind the test, which makes any
 * consolidated figure a function of what a public API happened to publish that morning —
 * the same figure asserted twice on different days is two different numbers. An E2E suite
 * cannot assert money under that, and the answer is not to assert loosely: it is to take
 * the network out, exactly as [InMemorySupportRepository] does for support.
 *
 * **It refuses rather than fails, and the difference is the whole design.** [RemoteQuote]
 * has three shapes because *unavailable* and *not covered* lead the user to opposite
 * actions, and `NotCovered` is the one that is **permanent and already handled**: the
 * screen's answer to it is "enter the rate by hand", which is precisely what a flow does.
 * Answering `Unavailable` would have the app quietly retrying a source that will never
 * answer, and would leave the suite asserting against a transient state.
 *
 * What that buys is control: the archive holds exactly the rows a flow wrote, so a figure
 * that needed a rate is reproducible, and the state of holding **no** rate at all becomes
 * reachable — it cannot be reached at all while a real source keeps filling the archive
 * in the background.
 *
 * `coverage()` answers the empty set and not `null`, and the port's own words say why:
 * `null` is *unknown coverage*, which falls back to asking pair by pair, while an empty
 * set is *quotes nothing*. Only the second is true here, and only the second stops the
 * questions.
 */
class OfflineRateSource : IRemoteRateSource {

    override suspend fun coverage(): Set<String> = emptySet()

    override suspend fun quote(currency: String, against: String): RemoteQuote =
        RemoteQuote.NotCovered
}
