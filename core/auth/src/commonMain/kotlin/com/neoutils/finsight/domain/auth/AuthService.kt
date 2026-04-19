package com.neoutils.finsight.domain.auth

interface AuthService {
    suspend fun getUserId(): String?
}
