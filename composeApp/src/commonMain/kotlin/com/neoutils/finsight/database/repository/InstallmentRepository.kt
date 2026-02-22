package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.InstallmentDao
import com.neoutils.finsight.database.entity.InstallmentEntity
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.repository.IInstallmentRepository
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

    override suspend fun deleteInstallmentById(id: Long) {
        installmentDao.deleteById(id)
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
