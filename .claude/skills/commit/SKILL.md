---
name: commit
description: Stage and commit changes following the project's commit convention
---

Stage the relevant files and create a commit following this project's convention.

## Commit format

```
Type(Scope): Summary
```

**Types:** `Feat`, `Fix`, `Refactor`, `Chore`

**Scope:** the feature, module, or layer affected (e.g. `Dashboard`, `Invoice`, `CreditCard`, `Android`, `Project`, `UseCase`). Omit if the change is truly cross-cutting.

**Summary:** short, imperative sentence describing what changed and why — not how.

## Rules

- Keep commits focused and atomic (one logical change per commit).
- Do not use `--no-verify`.
- Do not amend existing commits.
- Stage only files related to the described change; leave unrelated changes unstaged.
- Never commit `.env`, secrets, or credentials.

## Steps

1. Run `git status` and `git diff` to understand what changed.
2. Identify the appropriate `Type` and `Scope` from the changes.
3. Stage the relevant files with `git add <files>`.
4. Commit using the format above.
5. Run `git status` to confirm the commit succeeded.

If `$ARGUMENTS` is provided, use it as a hint for the commit message or scope.