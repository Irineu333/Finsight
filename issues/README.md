# Known issues

Defects that are known, reproduced and not yet fixed. One file each, named
`NNN-short-slug.md`.

An entry earns a place here when it changes what the app does — not when a document is
merely out of date. Each one states where it lives with `file:line`, how to reach it, what
it costs, and what the fix would be. An entry with no reproduction is a suspicion, and
belongs in a discussion rather than here.

Delete the file in the commit that fixes it. A list of issues that outlives the defects is
worse than no list, because it teaches people to skim.

## Worth carrying forward

A green suite is not evidence. The adversarial review of the `local-backup` change found a
restore that copied rows with `INSERT INTO main.X SELECT * FROM candidate.X`, which matches
columns **by position**. An installation that reached the current schema by migrating holds
`budgets.currency` last, while one installed at it holds the column sixth, because
`ALTER TABLE … ADD COLUMN` appends. Restoring between them wrote the period into the
currency and committed without a word, over an archive the same transaction had already
deleted. Both databases in the restore tests had been created fresh, so both carried the
entity order and the two ingredients of the defect never met. Fixed in `34ea0d3fb`, with a
test that migrates a v11 file through the chain — the shape every installation in the field
actually has.
