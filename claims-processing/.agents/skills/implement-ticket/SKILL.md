---
name: implement-ticket
description: Orchestrates delivery of a claims-service ticket, task, story, bug or feature in small gated slices - clarify, choose an option, build, verify, self-review - calling the other project skills at the right points. Use when asked to implement, build, add, fix or pick up a ticket, story, task or feature, including a `docs/backlog.md` item.
---

# implement-ticket (orchestrator)

Every **STOP** means: end your turn and wait for the developer's reply. Never continue past a STOP on your own.

## Input
`ticket`: one of
- a `docs/backlog.md` item, by its title (e.g. `ticket: Submit claim use case`) or `next` for the first unticked item,
- free text or a pasted ticket.

If the title matches no backlog item, or matches several, ask which one. Never pick one silently.

## 1. Load context
Read the ticket, `AGENTS.md`, `docs/solution.md`, and whichever `docs/adr/*` and `docs/diagrams/*`
touch the ticket (e.g. death-claim SLA, payment callback, claim states). Look at the code it affects.
Change nothing yet.

## 2. Clarify gate - STOP
Post:
- **Ticket in my words**: each point tagged with its source (`solution.md`, `ADR-0002`, ticket).
- **Assumptions to confirm**: numbered; anything the docs don't state goes here or in questions.
- **Questions**: numbered, each answerable in a line (e.g. "Does the death-claim SLA start at
  submission or at policy confirmation?").

STOP.

## 3. Options gate - STOP
Give 2-3 ways to build it. For each: classes/ports touched per layer, trade-offs, risk. Mark your
pick and why, and split the work into slices (each one buildable and testable alone).
If an option implies an architectural decision (new port, sync vs async, priority mechanism,
storage), say so: it will need an ADR. STOP.

## 4. Test plan - STOP
Use the `test-strategy` skill for this slice (every slice), passing the slice and the chosen option.
It proposes a short test list; STOP until the developer approves or edits it.

## 5. Build one slice
Write the code and the approved tests, following `test-strategy` and `AGENTS.md` (layers, placement,
naming). Call other skills when their trigger appears:

| Trigger during the slice | Call | Then |
|---|---|---|
| A decision that is hard to reverse or crosses layers | `architecture-decision-record` | Draft only, STOP for approval before coding on top of it |
| A test not on the approved list turns out to be needed | `test-strategy` | Propose the addition, STOP for approval |
| Claim flow, claim status, or an external interaction changed | `mermaid-diagrams` | Proposed diagram diff, STOP for approval |

Something new and unclear mid-slice? Stop and ask; don't pick for the developer.

## 6. Verify
Run `./mvnw verify` (`mvn verify` if no wrapper). Quote the summary line and any failing test or
ArchUnit message as printed. If the build isn't set up yet, say so instead of claiming success.
Fix failures only inside this slice; never touch ArchUnit rules (see `AGENTS.md` rule 1).

## 7. Self-review
Use the `review-changes` skill, passing this slice's diff, the ticket and the verify result. Don't act on its findings yet.

## 8. Hand over - STOP
Show: files changed (one line each), verify result, the review report, and questions. Ask:
- which review findings to act on,
- whether to log this with `scripts/log-ai.ps1` (propose the `-Task`/`-Outcome`/`-Notes` text),
- whether to start the next slice, or, once the last slice is approved, whether to tick the item off in `docs/backlog.md`.

STOP.

## Needs explicit approval every time
Commits, pushes, PRs, new dependencies, edits to `docs/` or ADRs, running `log-ai.ps1`, ticking `docs/backlog.md`. Answers
to questions go into `docs/` or an ADR only after the developer agrees on the wording.
