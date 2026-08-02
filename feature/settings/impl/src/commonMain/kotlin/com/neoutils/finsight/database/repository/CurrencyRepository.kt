package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.CurrencyDao
import com.neoutils.finsight.database.entity.CurrencyEntity
import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.extension.platformCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The offered set of currencies, over the single table that holds it.
 *
 * Its whole job beyond persistence is **resolving the name at every read**: the row's own
 * when it stores one, the platform's in the current language when it does not, and the
 * code itself when the platform cannot name it either. Resolving it here rather than
 * storing it is what keeps a name from freezing in the language of the run that wrote it.
 *
 * No rule of refusal lives here. Which currency may be deleted, and which may not be
 * archived, are decisions with owners of their own in the use cases above.
 */
class CurrencyRepository(
    private val dao: CurrencyDao,
) : ICurrencyRepository {

    override fun observeOffered(): Flow<List<CurrencyInfo>> =
        dao.observeOffered().map { rows -> rows.map(::toDomain) }

    override fun observeAll(): Flow<List<CurrencyInfo>> =
        dao.observeAll().map { rows -> rows.map(::toDomain) }

    override suspend fun getOffered(): List<CurrencyInfo> = dao.getOffered().map(::toDomain)

    override suspend fun getAll(): List<CurrencyInfo> = dao.getAll().map(::toDomain)

    override suspend fun get(code: String): CurrencyInfo? =
        dao.getByCode(code.uppercase())?.let(::toDomain)

    override suspend fun exists(code: String): Boolean = dao.exists(code.uppercase())

    override suspend fun save(code: String, symbol: String, name: String?) {
        dao.upsert(
            CurrencyEntity(
                code = code.uppercase(),
                symbol = symbol,
                // Blank is absence, not a name: it would render as an empty label where
                // the platform's own answer would have read fine.
                name = name?.trim()?.takeIf { it.isNotBlank() },
                isArchived = dao.getByCode(code.uppercase())?.isArchived ?: false,
            )
        )
    }

    override suspend fun archive(code: String) = dao.archive(code.uppercase())

    override suspend fun unarchive(code: String) = dao.unarchive(code.uppercase())

    override suspend fun delete(code: String) = dao.deleteByCode(code.uppercase())

    private fun toDomain(entity: CurrencyEntity) = CurrencyInfo(
        code = entity.code,
        symbol = entity.symbol,
        name = entity.name ?: platformCurrency(entity.code)?.name,
    )
}
