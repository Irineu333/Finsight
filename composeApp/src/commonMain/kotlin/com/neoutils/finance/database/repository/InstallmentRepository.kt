package com.neoutils.finance.database.repository

import com.neoutils.finance.database.dao.InstallmentDao
import com.neoutils.finance.database.entity.InstallmentEntity
import com.neoutils.finance.domain.model.Installment
import com.neoutils.finance.domain.repository.IInstallmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InstallmentRepository(
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

    private fun toDomain(entity: InstallmentEntity): Installment {
        return Installment(
            id = entity.id,
            count = entity.count,
            number = 1,
            totalAmount = entity.totalAmount,
        )
    }
}
