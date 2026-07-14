package com.neoutils.finsight.domain.exception

class DetailNotFoundException(
    entity: String,
    id: Long,
) : IllegalStateException("Detail entity '$entity' with id $id not found")
