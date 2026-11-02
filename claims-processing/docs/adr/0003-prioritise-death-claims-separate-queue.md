# 0003. Prioritise death claims on a separate queue

Status: Accepted
Date: 2026-11-02 · Decided by: The developer

## Context

Death claims are highly time-sensitive. The case study requires an SLA and gives no number. `solution.md` says a death claim never uses straight-through processing and always goes to a priority analyst queue with an SLA alarm. Its AWS mapping already names a normal queue, a death-claim priority queue, and a dead-letter queue, plus CloudWatch SLA alarms.

ADR-0001 defines one queue port and leaves a separate analyst-queue port open. ADR-0002 leaves open whether a death-claim payment status uses the priority queue. Its retry budget is about 7 seconds, chosen while this SLA was still unset.

## Options

1. **One queue, priority field.** The consumer sorts death claims ahead of the others. One structure. A worker can still be busy with an ordinary message it already took, and SQS has no priority sort, so the sample would diverge from the second queue already named in `solution.md`.
2. **Separate death-claim queue, drained first, one port.** The adapter behind the ADR-0001 queue port holds the normal queue and the death-claim queue. One worker takes every pending death-claim message before a normal one.
3. **Separate executor, one queue.** Isolation in the sample process only. The death-claim SQS queue in the AWS mapping would have nothing to do.

## Decision

Option 2. A separate queue can be drained first behind the one port ADR-0001 already set, and it is the death-claim SQS queue `solution.md` already names.

- The SLA is 4 clock hours from accept (`POST /claims` accepts the claim) until the analyst decision. If the claim is rejected with no analyst decision, the clock stops at that rejection. The number is an assumption pending business confirmation. The duration is a config value; the default is 4 hours.
- A warning alarm fires at 75% of the configured duration, so there is a warning before the breach. At the default that is 3 hours. A breach alarm fires at the configured duration.
- For a claim type of `DEATH`, the priority queue carries the client and policy checks, the analyst handoff, and the payment-status update. Ordinary claims, including their payment-status updates, use the normal queue.
- A death-claim message that exhausts its retries goes to the dead-letter queue and is flagged to the priority analyst queue, and the SLA clock keeps running.
- The separate analyst-queue port stays open. This ADR adds no port.

## Consequences

- The in-memory adapter implements both queues behind the one port. `config` supplies the SLA duration. AWS mapping (docs only): death-claim messages go to the death-claim SQS queue, other messages go to the normal SQS queue, and CloudWatch alarms fire at 75% and at the SLA. Showing that warning in the Claims System UI stays out of scope.
- The payment-status use case still calls the queue port from ADR-0001. The call carries that the claim is a death claim, using the type already stored on the claim, and the adapter selects the priority queue.
- ADR-0002's retry still applies on whichever queue holds the message: 4 receives, backoff 1s, 2s, 4s. Against a 4-hour SLA that budget is about 7 seconds. When a death-claim message exhausts those receives, it goes to the dead-letter queue, the claim is flagged to the priority analyst queue, and the SLA clock keeps running.
- One worker finishes the message it has, then drains the priority queue before the normal queue. A burst of ordinary claims waits. A normal message already in progress runs to the end of that attempt.
- This ADR does not set the straight-through limit, flags, borderline cases, or the analyst decision endpoint.
