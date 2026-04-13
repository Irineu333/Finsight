---
name: sdd-plan
description: >
  SDD Phase 2 — Planning. Reads the approved spec and creates the implementation plan
  with verifiable steps. Use after the spec has been validated by the user.
user-invocable: true
---

# SDD Phase 2 — Planning

Create the implementation plan and step files from an approved spec.
The plan defines *how* to deliver what the spec defines.

## Input

`$ARGUMENTS` — feature name. If empty, look for the most recent spec in `docs/features/` or ask.

## Pre-conditions

- `docs/features/{feature-name}/spec.md` must exist and be approved by the user.
- If the spec does not exist, tell the user to run `/sdd-specify` first.

## Steps

### 1. Analyze the spec

Read the spec and identify:

- Which layers are affected (domain / database / ui).
- Which existing files will be modified vs. created.
- Dependencies between behaviors (what must be built first).
- Which external libraries are relevant and whether they are already in the project.
- Technical risks or unknowns.

Present the analysis to the user and ask for confirmation before proceeding.

### 2. Define and validate the step breakdown

Propose the step breakdown to the user BEFORE writing any files. Each step should:

- Be independently verifiable (has its own acceptance criteria).
- Build on previous steps without requiring rework.
- Respect natural dependencies (e.g. an interface before its implementation).

Derive the breakdown from the spec — let the problem shape the steps.

Present the proposed steps as a numbered list with a brief description of each.
Ask the user:
- Does the breakdown look right?
- Are the steps too coarse or too fine?
- Is the order correct?
- Any steps to add, remove, or merge?

Do NOT write any files until the user approves the step breakdown.

### 3. Write the plan ("How do we get there?")

Create `docs/features/{feature-name}/plan.md` following the template at `.claude/skills/sdd-plan/references/plan-template.md`.

Fill in:

- **Contexto tecnico:** existing code, prior decisions, known risks.
- **Etapas:** checklist with links to step files.
- **Registro de desvios:** leave empty (filled during execution).
- **Issues:** leave empty (filled during execution).

### 4. Write step files (“Divide to conquer”)

For each step, create `docs/features/{feature-name}/steps/{NN}-{slug}.md` following
the template at `.claude/skills/sdd-plan/references/step-template.md`.

Fill in:

- **O que fazer:** objective description of what to implement.
- **Arquivos afetados:** list of files to create or modify, with what changes.
- **Criterio de aceite:** manual validation + code review checklist for this step only.
- **Desvio:** leave empty (filled during execution).

### 5. Validate with user

Present the complete plan to the user. Ask specifically:

- Is the step order correct?
- Are the steps small enough to be verifiable?
- Are there missing steps or unnecessary ones?
- Are the technical risks identified realistic?

Do NOT proceed to execution until the user explicitly approves the plan.

## Rules

- The plan must cover everything in the spec — no behavior should be left unassigned to a step.
- Steps should not overlap in scope. Each file change belongs to exactly one step.
- The plan can (and should) be adjusted during execution — that's what the desvios section is for.
- Never modify the spec from within this phase. If the spec needs changes, tell the user.