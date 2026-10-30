---
name: architecture-decision-record
description: Drafts or supersedes an ADR in docs/adr/ for a significant claims-service design choice (e.g. death-claim SLA prioritisation, sync vs async payment status, in-memory storage, port boundaries, retry/timeout policy). Use when a hard-to-reverse decision comes up, when asked to record or justify a decision, or when implement-ticket or review-changes flags one.
---

# architecture-decision-record

**Called by:** the developer, `implement-ticket` (decision during a slice), or as a follow-up suggested by
`review-changes`.
**Input:** the question being decided, the options on the table, and the developer's chosen option (or a
request to compare first). Ask for whatever is missing; don't invent context or a choice.
**Returns:** a draft ADR shown in the chat, then STOP. Write the file only after the developer approves the
text, then hand control back to the caller.

## Worth an ADR?
Yes if it constrains later work, spans layers or external systems, or picks between real
alternatives. No for naming, formatting, or anything cheap to undo; just mention those.

## File
`docs/adr/NNNN-short-kebab-title.md`, where `NNNN` = highest existing number + 1 (first is `0001`).
Numbers are never reused. To replace a decision, write a new ADR and set the old one's status to
`Superseded by NNNN`, linking both ways (only with the developer's OK, since it edits an existing ADR).

## Shape
```md
# 0004. Prioritise death claims with a separate queue

Status: Proposed | Accepted | Superseded by NNNN
Date: 2026-10-30 · Decided by: The developer

## Context
Death claims must be settled within the SLA in docs/solution.md; other claims share the same
pipeline. In-memory storage only; Payment answers asynchronously via callback.

## Options
1. Priority field + ordered in-memory queue - simple; one worker can still starve.
2. Separate executor for death claims - isolation; more wiring in `config`.

## Decision
Option 2, because ... (the developer's reasoning, not yours).

## Consequences
Good: ... · Costs: ... · AWS mapping (docs only): e.g. separate SQS queue.
```
Keep it under a page. Leave `Status: Proposed` until the developer says it's accepted.
