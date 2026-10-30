---
name: mermaid-diagrams
description: Creates or updates Mermaid diagrams in docs/diagrams/ for claims-service - system context, claim submission and payment-callback sequences, and the claim status lifecycle. Use when asked to draw, diagram or visualise the architecture or a claim flow, or when implement-ticket or review-changes finds that a flow, claim state or external interaction changed.
---

# mermaid-diagrams

**Called by:** the developer, `implement-ticket` (flow or state changed), or as a follow-up suggested by
`review-changes` (diagram out of date).
**Input:** what changed (e.g. "new `PENDING_PAYMENT` status", "Policy Manager now called before
Client Registry") and which diagram(s) it affects.
**Returns:** the proposed diagram text and a one-line note per change, then STOP. Save it only once
the developer approves, then hand control back to the caller.

## Files
One concept per Markdown file in `docs/diagrams/`: a one-line purpose, one ```` ```mermaid ```` block,
a short legend. Typical files: `context.md`, `sequence-submit-claim.md`,
`sequence-payment-callback.md`, `state-claim.md`. Ask before adding a new file.

## Pick the type
- Who talks to whom → `flowchart LR` with `subgraph` boxes. **No C4 keywords** (`C4Context`,
  `Person()`, `Rel()`...): GitHub shows them as raw text.
- Calls in order → `sequenceDiagram`.
- Claim status changes → `stateDiagram-v2`.
- AWS services appear only in the docs' deployment view, labelled as proposed.

## Examples (adapt to what the docs actually say)
```mermaid
sequenceDiagram
    participant W as Web form
    participant S as claims-service
    participant P as Payment (WireMock)
    W->>S: POST /claims (type DEATH)
    S->>S: mark priority = SLA
    S->>P: initiate payment
    P-->>S: 202 accepted
    P-)S: POST /payments/callback (PAID)
```
```mermaid
stateDiagram-v2
    [*] --> SUBMITTED
    SUBMITTED --> VALIDATED: client + policy OK
    SUBMITTED --> REJECTED: check failed
    VALIDATED --> PAYMENT_PENDING
    PAYMENT_PENDING --> PAID: callback PAID
    PAYMENT_PENDING --> PAYMENT_FAILED: callback FAILED
```

Before proposing: keep labels short, make sure every participant/state exists in the code or docs,
and check the block parses (paste into mermaid.live or preview it).
