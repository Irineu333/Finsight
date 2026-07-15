package com.neoutils.finsight.domain.exception

class DetailNotFoundException(
    entity: String,
    id: Any,
) : IllegalStateException("Detail entity '$entity' with id $id not found")
