# Known issues

One file per defect, written in Portuguese and named in English —
`kebab-case-describing-the-defect.md`. A file moves to `archive/` when the bug stops being open,
renamed with the day it closed (`AAAA-MM-DD-<slug>.md`), and is never deleted.

How a file is written, when it is archived and under which verdict, and what raises or lowers a
severity all live in one place: **`.claude/skills/issues`** — `SKILL.md`, `TEMPLATE.md`,
`SEVERITY.md`, `FIXING.md`. This file does not repeat any of it.

There is no index here, and adding one would be the second thing to keep up to date. The listing
is derived from the frontmatter:

```bash
grep -l "severity: critical" issues/*.md          # the critical ones
grep -l "area: creditcards" issues/*.md           # by area
grep -rl "confirmed: no" issues/*.md              # not yet confirmed against the code
grep -l "verdict: incidental" issues/archive/*.md # closed with the hole still open
```

## Worth carrying forward

Two lessons that cost more than one round to learn. Neither belongs to a single bug file, which
is why they are here.

**A green suite is not evidence.** The adversarial review of the `local-backup` change found a
restore that copied rows with `INSERT INTO main.X SELECT * FROM candidate.X`, which matches
columns **by position**. An installation that reached the current schema by migrating holds
`budgets.currency` last, while one installed at it holds the column sixth, because
`ALTER TABLE … ADD COLUMN` appends. Restoring between them wrote the period into the
currency and committed without a word, over an archive the same transaction had already
deleted. Both databases in the restore tests had been created fresh, so both carried the
entity order and the two ingredients of the defect never met. Fixed in `34ea0d3fb`, with a
test that migrates a v11 file through the chain — the shape every installation in the field
actually has.

**What closes a class of bug is looking for where else it lives** — not fixing the occurrence and
re-reading the list of occurrences already known. The rule "a category classifies one direction
only" was closed at six points over four rounds, and the first three rounds were each presented
as the last:
[1](archive/2026-08-18-transaction-form-drops-arguments-silently.md),
[2](archive/2026-08-18-update-transaction-drops-the-category-silently.md),
[3](archive/2026-08-19-create-installment-drops-the-category-silently.md),
[4](archive/2026-08-19-update-recurring-stores-an-incoherent-template.md),
[5](archive/2026-08-19-confirm-recurring-writes-the-wrong-direction.md).
The first five occurrences are all tools that build a form, and that framing is exactly what hid
the sixth: `confirm_recurring` builds no form, and is the only write on the surface that reaches
the ledger without one. The fourth round was ended differently — instead of re-reading the known
occurrences, **every** point that builds a contra-leg was enumerated and checked one by one for
what holds its direction. Three carry a category; two were already closed. That map is at the end
of the last file linked above, so the next doubt has something to re-check rather than an
assertion to trust.
