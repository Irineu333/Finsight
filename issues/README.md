# Known issues

Defects that are known, reproduced and not yet fixed. One file each, named
`NNN-short-slug.md`.

An entry earns a place here when it changes what the app does — not when a document is
merely out of date. Each one states where it lives with `file:line`, how to reach it, what
it costs, and what the fix would be. An entry with no reproduction is a suspicion, and
belongs in a discussion rather than here.

Delete the file in the commit that fixes it. A list of issues that outlives the defects is
worse than no list, because it teaches people to skim.

## How these were found

Most of these came out of an adversarial review of the `local-backup` change, run before
archiving it. Two reviewers read the branch independently — one for correctness and risk,
one for architecture and conventions — with the suite green at 1311 tests. Each entry says
where it was found; the ones that do not name the review were found somewhere else, and
`007` came out of fixing `003`.

The review paid for itself on a defect that is already fixed and so has no file here:
the restore copied rows with `INSERT INTO main.X SELECT * FROM candidate.X`, which matches
columns **by position**. An installation that reached the current schema by migrating holds
`budgets.currency` last, while one installed at it holds the column sixth, because
`ALTER TABLE … ADD COLUMN` appends. Restoring between them wrote the period into the
currency and committed without a word, over an archive the same transaction had already
deleted. Fixed in `34ea0d3fb`, with a test that migrates a v11 file through the chain —
the shape every installation in the field actually has.

Worth carrying forward from that: a green suite was not evidence. Both databases in the
restore tests were created fresh, so both carried the entity order, and the two
ingredients of the defect never met.
