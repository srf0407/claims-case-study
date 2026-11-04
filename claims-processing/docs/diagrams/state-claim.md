Statuses a stored claim can sit in, and the transitions between them.

```mermaid
stateDiagram-v2
    [*] --> ACCEPTED: POST /claims accepts

    ACCEPTED --> REJECTED: clear business rejection
    ACCEPTED --> AWAITING_ANALYST: analyst handoff
    ACCEPTED --> AWAITING_ANALYST: retries exhausted
    ACCEPTED --> PAYMENT_PENDING: straight through

    AWAITING_ANALYST --> REJECTED: analyst rejects
    AWAITING_ANALYST --> APPROVED: analyst approves

    APPROVED --> PAYMENT_PENDING: initiate succeeds

    PAYMENT_PENDING --> PAID: webhook paid
    PAYMENT_PENDING --> PAYMENT_FAILED: webhook failed

    REJECTED --> [*]
    PAID --> [*]
```

## Legend

- Straight through means the claim is not a death claim, the client and policy checks pass, the amount is strictly under the limit from `claims.straight-through.limit` (default 10000.00), passed into the straight-through use case, there is no flag, and payment initiate has succeeded. A flag is a boolean on the straight-through command. Who sets it stays open.
- Analyst handoff means a death claim that is not a clear business rejection, a high amount, a flag, or a borderline case. Who sets the flag, and what counts as borderline, stay on the Open table.
- Clear business rejection means unknown client, lapsed policy, or no cover. It is not retried.
- Retries exhausted change the status only from `ACCEPTED`, to `AWAITING_ANALYST`. Analyst rejection ends at `REJECTED`. The Claims System UI makes that decision by calling `POST /claims/{id}/decision`; approve moves the claim to APPROVED and starts payment.
- Analyst approves moves the claim from `AWAITING_ANALYST` to `APPROVED`. `APPROVED` moves to `PAYMENT_PENDING` when payment initiate succeeds. Until then the claim stays `APPROVED`.
- Retries exhausted in `APPROVED` leave the claim `APPROVED` and flag it for an analyst. The SLA clock has already stopped.
- A payment-status message that exhausts its retries leaves the claim `PAYMENT_PENDING` and flags it for an analyst. On a death claim the SLA clock keeps running.
- The webhook worker sets `PAID` or `PAYMENT_FAILED`. Queuing the webhook, a bad signature, and a repeat of the same idempotency key leave the status unchanged.
- `REJECTED` and `PAID` end. `PAYMENT_FAILED` has no outgoing arrow. What happens next is the new Open row: retry payment, analyst review, or terminal.
- For a death claim the SLA clock starts at `ACCEPTED` and stops at the analyst decision, or at `REJECTED` when no analyst decided. Approval is the move into `APPROVED`.
- A payload that fails input validation is HTTP 400 and never becomes a claim. The normal queue and the death-claim priority queue are not statuses.
