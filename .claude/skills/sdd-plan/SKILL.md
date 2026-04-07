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

- Which behaviors from the spec are independent vs. dependent on each other.
- Which existing files will be modified vs. created.
- Whether any infrastructure is needed before visible behavior can be delivered.
- Technical risks or unknowns.
- Which references from `docs/references/` are relevant.

Present the analysis to the user and ask for confirmation before proceeding.

### 2. Define and validate the step breakdown

Propose the step breakdown to the user BEFORE writing any files.

**Slice by behavior, not by layer.** Each step delivers a user-visible behavior that can be
validated by running the app — not a technical layer that only becomes testable several steps later.
Code review follows naturally from behavior validation: once the behavior works, the code that
produces it is reviewed in context.

Each step must:
- Deliver one complete, observable behavior from the spec.
- Implement all layers necessary for that behavior to work end-to-end.
- Include the full navigation/access flow so the developer can reach and exercise it in the app.
- Have acceptance criteria that require running the app, not just reading code.

If infrastructure that doesn't produce visible behavior is needed (e.g. DB schema, base navigation wiring), group it into a single "Fundação" step 0 at the start — and keep it minimal.

Typical step progression:
1. (Optional) Fundação — minimal infrastructure with no visible behavior yet
2. Behavior A — first scenario from the spec, end-to-end
3. Behavior B — next scenario, building on A
4. ...

Order behaviors by dependency: if B requires A to exist first, A comes first.

Present the proposed steps as a numbered list. For each step include:
- The behavior being delivered (reference the spec scenario)
- The layers it touches
- How the developer will access it in the app to validate

Ask the user:
- Does the breakdown look right?
- Are the steps too coarse or too fine?
- Is the order correct?
- Any steps to add, remove, or merge?

Do NOT write any files until the user approves the step breakdown.

### 3. Write the plan

Create `docs/features/{feature-name}/plan.md` following the template at `docs/sdd/plan-template.md`.

Fill in:

- **Contexto tecnico:** existing code, prior decisions, known risks.
- **Referencias:** links to relevant `docs/references/` files.
- **Etapas:** checklist with links to step files.
- **Registro de desvios:** leave empty (filled during execution).
- **Issues:** leave empty (filled during execution).

### 4. Write step files

For each step, create `docs/features/{feature-name}/steps/{NN}-{slug}.md` following
the template at `docs/sdd/step-template.md`.

Fill in:

- **O que fazer:** objective description of what to implement, including all layers needed.
- **Arquivos afetados:** list of files to create or modify, with what changes.
- **Criterio de aceite:**
  - Manual validation: step-by-step instructions to access and exercise the behavior in the app,
    starting from where to tap/navigate to reach it. The developer must be able to run the app
    and follow these steps to confirm the behavior works.
  - Code review: what to verify in the implementation after the behavior is confirmed.
- **Desvio:** leave empty (filled during execution).

### 5. Validate with user

Present the complete plan to the user. Ask specifically:

- Is the step order correct?
- Are the acceptance criteria actionable in a running app?
- Are there missing steps or unnecessary ones?
- Are the technical risks identified realistic?

Do NOT proceed to execution until the user explicitly approves the plan.

## Rules

- The plan must cover everything in the spec — no behavior should be left unassigned to a step.
- Steps should not overlap in scope. Each file change belongs to exactly one step.
- The plan can (and should) be adjusted during execution — that's what the desvios section is for.
- Never modify the spec from within this phase. If the spec needs changes, tell the user.
