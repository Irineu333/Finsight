package com.neoutils.finsight

import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The registry as a list, archived rows included and marked by [archived].
 *
 * The name arrives already resolved, exactly as the contract promises — resolving it is
 * the real repository's job and is tested there, so a fake that re-derived it would only
 * be testing itself.
 */
internal class FakeCurrencyRepository(
    currencies: List<CurrencyInfo> = DEFAULT,
    private val archived: Set<String> = emptySet(),
) : ICurrencyRepository {

    private val rows = MutableStateFlow(currencies)

    override fun observeOffered(): Flow<List<CurrencyInfo>> =
        rows.map { list -> list.filter { it.code !in archived } }

    override fun observeAll(): Flow<List<CurrencyInfo>> = rows

    override suspend fun getOffered(): List<CurrencyInfo> = rows.value.filter { it.code !in archived }

    override suspend fun getAll(): List<CurrencyInfo> = rows.value

    override suspend fun get(code: String): CurrencyInfo? = rows.value.firstOrNull { it.code == code }

    override suspend fun exists(code: String): Boolean = get(code) != null

    override suspend fun save(code: String, symbol: String, name: String?) {
        rows.value = rows.value.filterNot { it.code == code } + CurrencyInfo(code, symbol, name)
    }

    override suspend fun archive(code: String) = Unit

    override suspend fun unarchive(code: String) = Unit

    override suspend fun delete(code: String) {
        rows.value = rows.value.filterNot { it.code == code }
    }

    companion object {
        val DEFAULT = listOf(
            CurrencyInfo("BRL", "R$", "Real brasileiro"),
            CurrencyInfo("USD", "US$", "Dólar americano"),
            CurrencyInfo("EUR", "€", "Euro"),
        )
    }
}
