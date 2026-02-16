package com.neoutils.finance.domain.repository

import com.neoutils.finance.domain.model.Installment
import kotlinx.coroutines.flow.Flow

interface IInstallmentRepository {
    fun observeAllInstallments(): Flow<List<Installment>>
    suspend fun getAllInstallments(): List<Installment>
    suspend fun getInstallmentById(id: Long): Installment?
    suspend fun createInstallment(
        count: Int,
        totalAmount: Double,
    ): Long
}
