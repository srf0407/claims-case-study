# Solution

## Description

Placeholder. Written under the "Solution description" backlog item.

## Recommended changes

Placeholder. Written under the "Recommended changes to existing systems" backlog item.

## Scope

`claims-service` is the Claims System API. The Claims System exists and is not connected to the other systems, so this service builds that orchestration end to end. It owns input checks, the claim lifecycle, step order, failure handling, and death-claim priority.

The Claims System UI is out of scope. This repo has no frontend.

Client rules stay in the Client Registry. Policy, plan, and benefit rules stay in the Policy Manager. This service calls those systems and acts on the result. Portfolio information is read for context only and does not drive the pay-or-not decision.

The only intake channel in this exercise is the web form. The sample does not implement the Client Registry, the Policy Manager, or the Payment System. Those stay external and are mocked with WireMock in tests.

### What the API accepts

The channel submits a claim as JSON. The minimum fields are:

- `clientId`
- `policyNumber`
- `claimType`, including `DEATH`
- `amount`
- `incidentDate`

A death claim also includes `dateOfDeath` and `deathCertificateRef`.

Payee bank details come from the Client Registry, not the form.

Input that fails validation gets HTTP 400 and is never queued.

`GET /claims/{id}` returns the claim for the channel (status) and for the analyst.

### How a claim is processed

Death claims never use straight-through processing. For every other claim, when the client check and the policy check both pass, and the amount is under a set limit, payment starts automatically.

A high amount, or a flag, sends the claim to an analyst queue.

A death claim uses a priority queue for its client and policy checks, its analyst handoff, and its payment-status update. The worker drains that queue before the normal queue. The SLA is 4 clock hours from accept (`POST /claims`) until the analyst decision, or until the claim is rejected with no analyst decision. The duration is an assumption pending business confirmation and is a config value (default 4 hours). A warning alarm fires at 75% of the duration (3 hours at the default). A death-claim message that exhausts its retries goes to the dead-letter queue and is flagged to the priority analyst queue, and the SLA clock keeps running. Recorded in ADR-0003.

A queued claim is approved or rejected in the Claims System UI. The UI calls claims-service, and on approval claims-service initiates payment.

Business rejection (unknown client, lapsed policy, no cover) is not retried. The claim is rejected, or sent to an analyst when the case is borderline.

A temporary failure (the system is down, the call times out, or the response is 503) gets 4 receives: the first attempt plus 3 retries, with backoff 1s, 2s, 4s. On SQS, `maxReceiveCount` is 4. After retries are exhausted the message goes to a dead-letter queue and the claim is flagged for an analyst.

A payment request carries an idempotency key. The Payment System reports status later by a signed HTTPS webhook to the payment-status endpoint (API Gateway in the AWS mapping). The endpoint queues the status and returns 200 once the message is queued. A worker then updates the claim. Payment retries the webhook if this service is down. Payment echoes the idempotency key, and a repeat delivery with the same key does not change the claim again. Recorded in ADR-0002.

Client Registry and Policy Manager calls are synchronous HTTP from a queue worker. A temporary failure retries that message, as described above.

### Boundaries the later tickets implement

No types are added in this document. The later code tickets add:

- inbound: submit a claim, get claim status, receive a payment status
- outbound: Client Registry, Policy Manager, Payment System (idempotency key on the payment request), claim storage, and queues

Queues and storage are behind ports. Sample code keeps them in memory.

### Out of scope

- The Claims System UI and any other frontend
- Channels other than the web form
- Authentication of claimants, analysts, and the channel
- Partial payments
- AWS calls and LocalStack, unless the optional LocalStack backlog item is started

## Assumptions

- Queues and claim storage sit behind ports. Implementations in this sample are in memory.
- The design maps the running system to AWS: API Gateway, ECS Fargate, SQS (a normal queue, a death-claim priority queue, and a dead-letter queue), DynamoDB, CloudWatch SLA alarms, and Secrets Manager. Payment status uses neither EventBridge nor SNS.
- The Payment System is assumed external, based on its 'Third Party' label in the case-study context diagram. It therefore reports status through an HTTPS webhook, not our internal event bus. The brief does not confirm this.
- LocalStack SQS is an optional profile, added only if that backlog item is picked up.
- Authentication of claimants, analysts, and the channel is out of scope for the sample. The payment webhook signature is in scope and is recorded in ADR-0002.
- Partial payments are out of scope for the sample.
- External systems are reached only through outbound ports and mocked with WireMock in tests.

## Open — set in a later ADR

Leave these unset. Do not invent values for them in code or in this file.

| Item | Where it is set |
|---|---|
| Straight-through amount limit | Not assigned to a ticket yet |
| What counts as a flag | Not assigned to a ticket yet |
| What counts as borderline (analyst versus reject) | Not assigned to a ticket yet |
| Whether the analyst queue is a separate port from the SQS queues | Not assigned to a ticket yet |
| Analyst decision endpoint (approve or reject a queued claim, for example `POST /claims/{id}/decision`) | Not assigned to a ticket yet |
