---
name: sdd-execute
description: >
  SDD Phase 3 — Execution. Implements the plan one step at a time, validates each step,
  documents deviations and issues. Use after the plan has been validated by the user.
user-invocable: true
---

# SDD Phase 3 — Execution

Implement the approved plan, one step at a time, with validation after each step.

## Input

`$ARGUMENTS` — feature name, optionally with step number (e.g. `debts 3` to resume from step 3).
If empty, look for features with pending steps in `docs/features/` or ask.

## Pre-conditions

- `docs/features/{feature-name}/spec.md` must exist (approved).
- `docs/features/{feature-name}/plan.md` must exist (approved).
- Step files must exist in `docs/features/{feature-name}/steps/`.
- If any are missing, tell the user to run the previous phases first.

## Context loading

Before each step, load the full context as documented in the SDD workflow:

1. Read `spec.md` — the agent needs the full intended behavior.
2. Read `plan.md` — the agent needs to know what comes after.
3. Read the current step file.
4. Read deviations from previous steps (in `plan.md` and completed step files).

Without the full picture, decisions will be locally correct but globally harmful.

## Execution loop

For each pending step:

### 1. Present the step

Show the user what will be implemented:
- Step objective
- Files to create or modify
- Acceptance criteria

Ask the user for confirmation before starting implementation.

### 2. Implement

Write the code following:
- The spec's mandatory patterns.
- The project's conventions (`CLAUDE.md`).
- The relevant architecture skill references.
- The step's file list and scope.

**Stay within the step's scope.** Do not implement behavior from future steps.
Do not refactor code outside the step's scope. Do not add features not in the spec.

### 3. Validate

After implementation, verify the step's acceptance criteria:

**Code review checklist:**
- Run through each checkbox in the step's "Revisao de codigo" section.
- Verify the dependency rule is respected.
- Check that no implementation leaked outside the step's scope.

**Automated checks (when applicable):**
- Run `./gradlew allTests` if tests were added or modified.
- Run `./gradlew check` for general verification.

### 4. Report to user

Present the implementation result:
- What was implemented (files created/modified).
- Test results (if applicable).
- Any concerns or observations.
- The step's manual validation steps for the user to verify.

**Wait for the user to validate before proceeding to the next step.**

### 5. Handle deviations

If the implementation diverged from the plan:

- Fill in the "Desvio" section of the step file with:
  - What was expected
  - What was done
  - Why
  - Impact on following steps
- Add a summary to the "Registro de desvios" section in `plan.md`.

### 6. Handle issues

If bugs or uncovered cases are discovered during implementation or validation:

- Create an issue file in `docs/features/{feature-name}/issues/{NN}-{slug}.md`
  following the template at `.claude/skills/sdd-execute/references/issue-template.md`.
- Add the issue to the "Issues" section in `plan.md`.
- Discuss with the user whether to fix now or defer.

### 7. Mark step complete

After user approval:
- Check off the step in `plan.md` (`- [x]`).
- Commit the implementation using the `/commit` skill.
- Proceed to the next step (back to step 1 of the loop).

## Rules

- Never skip user validation between steps.
- Never implement multiple steps at once.
- Never modify the spec. If the spec needs changes, stop and discuss with the user.
- The plan can be adjusted — record deviations, don't hide them.
- If a step turns out to be too large, propose splitting it and update the plan.
- If a new step is needed that wasn't in the plan, propose it and update the plan.