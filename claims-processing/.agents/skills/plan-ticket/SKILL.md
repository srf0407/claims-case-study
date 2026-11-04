---
name: plan-ticket
description: Runs a docs-only claims-service ticket (scope, assumptions, ADRs, diagrams, solution write-up) with no code, tests or build. Use for docs/backlog.md items under "Understand and decide" or "Documentation", or any ticket whose only output is files in docs/.
---

# plan-ticket

Every **STOP** means: end your turn and wait for the developer's reply. Never continue past a STOP on your own.

## Input
`ticket`: a `docs/backlog.md` title, `next` for the first unticked docs-only item, or free text.
If the title matches no item or several, ask which one. If the ticket needs code, say so, suggest
`implement-ticket`, and STOP.

## 1. Load context
Read `AGENTS.md`, `docs/solution.md` (Scope, Assumptions, Open table), and the `docs/adr/*` and
`docs/diagrams/*` the ticket touches. Change nothing.

## 2. Decisions - STOP
Post one message:
- **What this ticket records**: one or two lines.
- **Already settled**: each point with its source (`solution.md`, `ADR-0001`, diagram).
- **Open items it could settle**: only rows from the `solution.md` Open table that fit this ticket.
- **Questions**: numbered. For a real choice, give the options with trade-offs and mark your pick
  and why. Never fill a gap yourself.

STOP.

## 3. Draft and check - STOP
Draft with the agreed answers:

| Output | Use |
|---|---|
| ADR | `architecture-decision-record` |
| Diagram | `mermaid-diagrams` |
| `solution.md` section or other doc | write it here |

Below the draft, add a short **Consistency check**:
- conflicts with `solution.md`, ADRs, diagrams or `AGENTS.md` (or "none"),
- Open-table rows this draft settles, with the exact wording to move them out.

STOP.

## 4. Write - STOP
Only after the developer approves the text: write the file(s) and the approved Open-table change.
Show the files changed, one line each. Ask:
- whether to set a new ADR to `Accepted`,
- whether to tick the item off in `docs/backlog.md`.

Do not add a task log or new rows to `docs/ai-usage.md`. That write-up is finished. Later pull requests must not append an AI log.

STOP.

## Not in this skill
No test plan, no build, no `review-changes`; the consistency check replaces them.

## Needs explicit approval every time
Edits to `docs/` or ADRs, ADR status changes, ticking `docs/backlog.md`, commits, pushes. Do not append an AI log to `docs/ai-usage.md`.
