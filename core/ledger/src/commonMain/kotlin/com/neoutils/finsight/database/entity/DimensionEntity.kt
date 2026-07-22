package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.neoutils.finsight.domain.model.DimensionKind

/**
 * An identity the ledger owns and a facade borrows, exactly mirroring the
 * `facade.accountId` pattern already in use: a category or an invoice keeps a
 * `dimensionId`, and a leg tagged with it is counted in that facade's total.
 * Collision between two facade tables is impossible by construction.
 *
 * [kind] is not needed by any query. It exists so the writer can reject a leg that
 * lands where its dimension does not belong — an invoice dimension on a nominal
 * leg would otherwise be silently wrong, skewing every category total with no
 * error anywhere. Persisted by name, which also keeps the raw schema readable.
 */
@Entity(tableName = "dimensions")
data class DimensionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val kind: DimensionKind,
)
