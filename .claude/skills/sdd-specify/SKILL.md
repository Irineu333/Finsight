---
name: sdd-specify
description: >
  SDD Phase 1 — Specification. Gathers information about the feature, existing architecture,
  and third-party libraries. Produces spec.md and reference docs. Use when starting a new
  feature that follows the Spec-Driven Development workflow.
user-invocable: true
---

# SDD Phase 1 — Specification

Create the feature specification following the Spec-Driven Development workflow.
The spec defines *what* the system must do — never *how* to implement it.

## Input

`$ARGUMENTS` — feature name or short description. If empty, ask the user before proceeding.

## Steps

### 1. Gather context

Research everything needed to write a precise spec:

- **Existing code:** Search the codebase for related entities, screens, repositories, use cases,
  and models. Understand what the user can already do in this area.
- **Architecture:** Read the `kmp-architecture` skill references to understand layer responsibilities
  and patterns that apply.
- **Third-party libraries:** Identify any external libraries relevant to this feature. Check if
  `docs/reference/` already has docs for them.

Present a summary of findings to the user and ask for confirmation before proceeding.

### 2. Create reference docs (if needed)

For each relevant third-party library that does NOT already have a reference in `docs/reference/`:

- Research the library's official documentation (web search).
- Create a reference file at `docs/reference/{library-name}.md`.

For existing references, update them if new usage patterns are relevant for this feature.

Ask the user to validate the references before proceeding.

### 3. Write the spec

Create `docs/features/{feature-name}/spec.md` following the template at `.claude/skills/sdd-specify/references/spec-template.md`.

Fill in each section:

- **Contexto:** what already exists (from step 1 findings).
- **Objetivo:** one sentence — the problem this feature solves.
- **Comportamentos:** scenarios in `Dado / Quando / Então` format. Cover:
  - Main path (happy path)
  - Edge cases
  - Expected errors
- **Regras de negócio:** explicit rules governing the behaviors above.
- **Padroes obrigatorios:** only patterns NOT already covered by `CLAUDE.md`.
- **Fora do escopo:** what is explicitly excluded (prevents AI extrapolation).
- **Criterio de aceite:** manual validation steps + code review checklist.

### 4. Validate with user

Present the complete spec to the user for review. Ask specifically:

- Are the behaviors correct and complete?
- Are there missing edge cases or error scenarios?
- Is the scope correct (not too broad, not too narrow)?
- Is anything listed that should be out of scope, or vice-versa?

Do NOT proceed to planning until the user explicitly approves the spec.

## Rules

- Never include implementation details (queries, internal data structures, function bodies).
- Code in the spec is allowed ONLY for mandatory architectural patterns with justification.
- If the user suggests implementation details, redirect them to the planning phase.
- The spec is a product decision document — it changes only when intent changes, never because of technical difficulty.
