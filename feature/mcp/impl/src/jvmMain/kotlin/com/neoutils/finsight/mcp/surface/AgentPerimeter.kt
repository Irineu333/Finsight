package com.neoutils.finsight.mcp.surface

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * **What an aggregate figure covers, and what it deliberately leaves out.**
 *
 * A sum of account balances and a net worth are different numbers, and nothing about either one
 * says which it is: `18400.00` reads the same both ways. In the simulation this surface was designed
 * against, an agent spent two calls establishing whether card debt had been subtracted — and the
 * cheaper failure is the one where it does not check and reports the wrong meaning confidently.
 *
 * So the perimeter travels **with the figure**, not only in the tool's description. The description
 * is what the agent reads before choosing; this is what it has in hand while writing the sentence.
 *
 * [seeAlso] names the tools that answer the neighbouring question — the one whose figure differs by
 * exactly what [excludes] lists. A name it can call, never advice in prose.
 */
@Serializable
internal data class AgentPerimeter(
    /** What the figure is a total of, in one sentence. */
    val covers: String,
    /** What a reader might reasonably assume is inside it and is not. */
    val excludes: List<String> = emptyList(),
    @SerialName("see_also")
    val seeAlso: List<String> = emptyList(),
)
