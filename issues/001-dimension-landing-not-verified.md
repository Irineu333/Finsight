# A restored file is never checked for where its dimensions land

**Severity:** medium — a spec promises an invariant that nothing enforces
**Found in:** the `local-backup` review, by both reviewers independently

## What

`openspec/changes/local-backup/specs/database-snapshot/spec.md:77-79` requires that after a
replacement the database satisfy "the same invariants demanded of any write — zero sum per
transaction and currency, **a dimension landing on the right account type**, and referential
integrity."

Nothing checks the landing.

`CandidateVerifier.ledgerRejection()`
(`core/database/src/commonMain/kotlin/com/neoutils/finsight/database/snapshot/CandidateVerifier.kt:192-209`)
runs three guards — `verifyLedgerBalanced`, `verifyNoOrphanDimensions`, `verifyForeignKeys`.
None of them is the landing rule. The requirement's own scenario (`spec.md:94-97`) lists
only those three, so the spec contradicts itself within twenty lines.

`DimensionKind.landsOn` is enforced in exactly one place —
`core/ledger/src/commonMain/kotlin/com/neoutils/finsight/database/repository/LedgerEntryWriter.kt:196`
— which is the boundary design decision D5 deliberately bypasses: a restore must not go
through the single write point, because that point *completes intent* and would
reinterpret entries that are already complete.

## How to reach it

A file holding an entry whose `dimensionId` names an invoice dimension while the entry
posts to a nominal account. Σ = 0 holds, no dimension is orphaned, every foreign key
resolves — the file passes all five layers and is restored.

## What it costs

An archive where a facade's total is computed over legs that do not belong to it. No error,
no signal; the figures are simply wrong, and every read that separates by dimension inherits
the mistake.

Nothing this app writes can violate it, since only the writer produces entries. Reaching it
takes a hand-edited or third-party file — which is exactly what the verification gate exists
to judge.

## The fix

Either verify it or stop promising it. Verifying is the better half, and does not need a
second copy of the rule: read the `(kind, type)` pairs out of the candidate in SQL, and
decide with `DimensionKind.landsOn` in Kotlin, so the rule keeps its single owner in
`:core:ledger`.

```sql
SELECT d.kind, a.type
FROM entries e
JOIN dimensions d ON d.id = e.dimensionId
JOIN accounts a ON a.id = e.accountId
WHERE e.dimensionId IS NOT NULL
```

If it is not verified, the clause and the sentence in D5 that echoes it have to go — a spec
that describes a guarantee nobody implements is the kind nobody revisits, because it reads
as already met.
