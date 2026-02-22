package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Installment
import kotlinx.coroutines.flow.Flow

interface IInstallmentRepository {
    fun observeAllInstallments(): Flow<List<Installment>>
    suspend fun getAllInstallments(): List<Installment>
    suspend fun getInstallmentById(id: Long): Installment?
    suspend fun createInstallment(
        count: Int,
        totalAmount: Double,
    ): Long
    suspend fun deleteInstallmentById(id: Long)
}
