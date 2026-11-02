# 0002. Async payment status by HTTPS webhook

Status: Accepted
Date: 2026-11-02 · Decided by: The developer

## Context

The Payment System finishes a payment and then updates the claim. `solution.md` already records an asynchronous status callback and an idempotency key on the payment request. ADR-0001 leaves the callback mechanism, including EventBridge versus SNS, to this ADR.

The Client Registry and the Policy Manager are called during claim processing. A temporary failure is retried with backoff, and the message then goes to a dead-letter queue.

## Options

1. **Synchronous final status.** The initiate call blocks until Payment succeeds or fails. No callback. A slow payment holds the worker for the whole bank operation.
2. **Async direct HTTPS webhook.** Payment accepts the initiate call, then later calls a payment-status endpoint. No event bus.
3. **Async via SNS.** Payment publishes; SNS calls the endpoint. A second messaging service beside SQS, for one caller.
4. **Async via EventBridge.** Payment emits an event; a rule calls the endpoint. Fits several consumers. Heavier for one caller.

## Decision

Option 2. The Payment System is assumed external, based on its 'Third Party' label in the case-study context diagram. It therefore reports status through an HTTPS webhook, not our internal event bus. The brief does not confirm this.

EventBridge versus SNS is closed as neither needed for one external caller.

- The webhook comes through API Gateway to the payment-status endpoint. The callback is signed so only Payment can call it. A webhook that fails the signature check returns 401 and is not queued.
- The endpoint calls an inbound use case. That use case puts the status on the queue port from ADR-0001 and returns. The endpoint then returns 200. 200 means the message is on the queue, before the worker runs. If the message is not queued, the endpoint does not return 200, and Payment retries.
- A worker takes the message and updates the claim. `GET /claims/{id}` can still show the previous status until that worker finishes.
- Payment retries the webhook if this service is down.
- Duplicate callbacks are safe via the idempotency key. Payment echoes the key from the initiate request. A second delivery with the same key does not change the claim again.
- Client Registry and Policy Manager are synchronous HTTP calls made from a queue worker, so a temporary failure retries that message. Business rejection is not retried. The Payment initiate call stays under the same temporary-failure rule.
- A temporary failure (the system is down, the call times out, or the response is 503) gets 4 receives: the first attempt plus 3 retries, with backoff 1s, 2s, 4s. On SQS, `maxReceiveCount` is 4 and the backoff is the visibility timeout for that receive. The in-memory queue uses the same count and delays. After the last attempt the message goes to the dead-letter queue and the claim is flagged for an analyst. The wait is about 7 seconds: long enough for a short timeout or 503, and short enough that it is unlikely to use up a death-claim SLA that is not set yet.

## Consequences

- The sample implements the signed endpoint, the queue port, and the worker. API Gateway, SQS, and Secrets Manager (the signing secret) stay a documented AWS mapping.
- The web adapter does not call the queue adapter. The use case depends on the queue port.
- This ADR uses that one queue port. It does not decide a separate analyst-queue port, and it does not decide whether a death-claim payment status uses the priority queue.
- If Payment turns out to be internal and on AWS, publishing payment events to EventBridge is an easy upgrade (adapter and docs change, use cases don't). Add this to "Recommended changes" when that backlog item is written. This ADR does not fill that placeholder.
