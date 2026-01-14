package com.neoutils.finance.extension

inline fun <T, R> Result<T>.then(block: (T) -> Result<R>): Result<R> {
    return if (isSuccess) {
        block(getOrThrow())
    } else {
        Result.failure(exceptionOrNull()!!)
    }
}
