package com.neoutils.finsight.database.mapper

import com.neoutils.finsight.database.entity.AgentActivityEntity
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.AgentActivityOutcome
import kotlinx.serialization.json.Json

/**
 * The journal's two shapes, and the **single** place `affected` changes form.
 *
 * The column is a string because the type converters of `AppDatabase` are shared by every
 * facade table, and a `List<String>` converter added for this one column would silently become
 * available to all of them. The list is serialised as a JSON array — a shape the mapper owns,
 * and owns alone.
 */
class AgentActivityMapper {

    private val json = Json

    fun toDomain(entity: AgentActivityEntity) = AgentActivity(
        id = entity.id,
        timestamp = entity.timestamp,
        client = entity.client,
        tool = entity.tool,
        arguments = entity.arguments,
        outcome = toDomain(entity.outcome),
        affected = decode(entity.affected),
    )

    fun toEntity(domain: AgentActivity) = AgentActivityEntity(
        id = domain.id,
        timestamp = domain.timestamp,
        client = domain.client,
        tool = domain.tool,
        arguments = domain.arguments,
        outcome = toEntity(domain.outcome),
        affected = json.encodeToString(domain.affected),
    )

    fun toDomain(outcome: AgentActivityEntity.Outcome) = when (outcome) {
        AgentActivityEntity.Outcome.OK -> AgentActivityOutcome.OK
        AgentActivityEntity.Outcome.REFUSED -> AgentActivityOutcome.REFUSED
        AgentActivityEntity.Outcome.FAILED -> AgentActivityOutcome.FAILED
    }

    fun toEntity(outcome: AgentActivityOutcome) = when (outcome) {
        AgentActivityOutcome.OK -> AgentActivityEntity.Outcome.OK
        AgentActivityOutcome.REFUSED -> AgentActivityEntity.Outcome.REFUSED
        AgentActivityOutcome.FAILED -> AgentActivityEntity.Outcome.FAILED
    }

    /**
     * A stored value that cannot be read becomes "no identifiers", never an exception. The
     * identifiers are a convenience — they carry the badge and the way to the entity — while
     * the record itself is what the retention policy and the investigation are about, and
     * losing the whole row over one unreadable column would throw away the part that matters.
     */
    private fun decode(raw: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
}
