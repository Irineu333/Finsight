package com.neoutils.finance.database.repository

import com.neoutils.finance.database.dao.AccountDao
import com.neoutils.finance.database.mapper.AccountMapper
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.repository.IAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountRepository(
    private val dao: AccountDao,
    private val mapper: AccountMapper
) : IAccountRepository {

    override fun observeAllAccounts(): Flow<List<Account>> {
        return dao.observeAllAccounts().map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override suspend fun getAllAccounts(): List<Account> {
        return dao.getAllAccounts().map { mapper.toDomain(it) }
    }

    override suspend fun getAccountById(accountId: Long): Account? {
        return dao.getAccountById(accountId)?.let { mapper.toDomain(it) }
    }

    override fun observeAccountById(accountId: Long): Flow<Account?> {
        return dao.observeAccountById(accountId).map { entity ->
            entity?.let { mapper.toDomain(it) }
        }
    }

    override suspend fun getDefaultAccount(): Account? {
        return dao.getDefaultAccount()?.let { mapper.toDomain(it) }
    }

    override fun observeDefaultAccount(): Flow<Account?> {
        return dao.observeDefaultAccount().map { entity ->
            entity?.let { mapper.toDomain(it) }
        }
    }

    override suspend fun getAccountCount(): Int {
        return dao.getAccountCount()
    }

    override suspend fun insert(account: Account): Long {
        return dao.insert(mapper.toEntity(account))
    }

    override suspend fun update(account: Account) {
        dao.update(mapper.toEntity(account))
    }

    override suspend fun delete(account: Account) {
        dao.delete(mapper.toEntity(account))
    }
}
