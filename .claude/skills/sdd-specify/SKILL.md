---
name: sdd-specify
description: >
  SDD Phase 1 — Specification. Maps the current state of the system and defines the expected
  behavior. Produces spec.md. Use when starting a new feature that follows the Spec-Driven
  Development workflow.
user-invocable: true
---

# SDD Phase 1 — Specification

Create the feature specification following the Spec-Driven Development workflow.
The spec defines *where we are* and *where we're going* — never *how to get there*.

## Input

`$ARGUMENTS` — feature name or short description. If empty, ask the user before proceeding.

## Steps

### 1. Map current state ("where we are")

Research what already exists in the system related to this feature:

- Which entities, models, and data already exist
- Which screens, flows, and actions the user can already perform in this area
- Which limitations or dependencies constrain this feature

Present a summary of findings to the user and ask for confirmation before proceeding.

### 2. Write the spec ("where we're going")

Create `docs/features/{feature-name}/spec.md` following the template at `.claude/skills/sdd-specify/references/spec-template.md`.

Fill in each section:

- **Contexto:** the current state from step 1.
- **Objetivo:** one sentence — the problem this feature solves or what it enables.
- **Comportamentos:** scenarios in `Dado / Quando / Então` format. Cover:
  - Main path (happy path)
  - Edge cases
  - Expected errors
- **Regras de negócio:** explicit rules governing the behaviors above.
- **Padroes:** only patterns specific to this feature, not already covered by context project.
- **Fora do escopo:** what is explicitly excluded (prevents AI extrapolation).
- **Criterio de aceite:** manual validation steps + code review checklist.

### 3. Validate with user

Present the complete spec to the user for review. Ask specifically:

- Are the behaviors correct and complete?
- Are there missing edge cases or error scenarios?
- Is the scope correct (not too broad, not too narrow)?
- Is anything listed that should be out of scope, or vice-versa?

Do NOT proceed to planning until the user explicitly approves the spec.

## Rules

- Never include implementation details (queries, internal data structures, function bodies).
- Code in the spec is allowed ONLY for patterns specific to this feature, with justification.
- If the user suggests implementation details, redirect them to the planning phase.
- The spec is a product decision document — it changes only when intent changes, never because of technical difficulty.
