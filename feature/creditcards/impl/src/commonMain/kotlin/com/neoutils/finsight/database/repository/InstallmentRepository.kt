package com.neoutils.finsight.database.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.InstallmentDao
import com.neoutils.finsight.database.entity.InstallmentEntity
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InstallmentRepository(
    private val database: AppDatabase,
    private val installmentDao: InstallmentDao,
) : IInstallmentRepository {

    override fun observeAllInstallments(): Flow<List<Installment>> {
        return installmentDao.observeAll().map { installments ->
            installments.map { toDomain(it) }
        }
    }

    override suspend fun getAllInstallments(): List<Installment> {
        return installmentDao.getAll().map(::toDomain)
    }

    override suspend fun getInstallmentById(id: Long): Installment? {
        return installmentDao.getById(id)?.let(::toDomain)
    }

    override suspend fun createInstallment(
        count: Int,
        totalAmount: Double,
    ): Long {
        return installmentDao.insert(
            InstallmentEntity(
                count = count,
                totalAmount = totalAmount,
            )
        )
    }

    override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) {
        installmentDao.updateById(id = id, count = count, totalAmount = totalAmount)
    }

    /**
     * Removing the installment and detaching the transactions that pointed at it are
     * one unit of work: a transaction left naming an installment that no longer
     * exists would render as a instalment of nothing (design D12).
     */
    override suspend fun deleteInstallmentById(id: Long) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                installmentDao.detachTransactions(id)
                installmentDao.deleteById(id)
            }
        }
    }

    private fun toDomain(entity: InstallmentEntity): Installment {
        return Installment(
            id = entity.id,
            count = entity.count,
            totalAmount = entity.totalAmount,
        )
    }
}
