package com.neoutils.finsight.core.auth

interface AuthService {
    suspend fun getUserId(): String?
}
