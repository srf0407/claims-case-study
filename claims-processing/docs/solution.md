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

A death claim always goes to a priority analyst queue, with an SLA alarm.

A queued claim is approved or rejected in the Claims System UI. The UI calls claims-service, and on approval claims-service initiates payment.

Business rejection (unknown client, lapsed policy, no cover) is not retried. The claim is rejected, or sent to an analyst when the case is borderline.

A temporary failure (the system is down, the call times out, or the response is 503) is retried with backoff. After retries are exhausted the message goes to a dead-letter queue and the claim is flagged for an analyst.

A payment request carries an idempotency key. The Payment System reports status later by calling a payment-status endpoint on this API. That callback is asynchronous. The sync-versus-async ADR records the detail.

### Boundaries the later tickets implement

No types are added in this document. The later code tickets add:

- inbound: submit a claim, get claim status, receive a payment status
- outbound: Client Registry, Policy Manager, Payment System (idempotency key on the payment request), claim storage, and queues

Queues and storage are behind ports. Sample code keeps them in memory.

### Out of scope

- The Claims System UI and any other frontend
- Channels other than the web form
- Authentication
- Partial payments
- AWS calls and LocalStack, unless the optional LocalStack backlog item is started

## Assumptions

- Queues and claim storage sit behind ports. Implementations in this sample are in memory.
- The design maps the running system to AWS: API Gateway, ECS Fargate, SQS (a normal queue, a death-claim priority queue, and a dead-letter queue), DynamoDB, CloudWatch SLA alarms, and Secrets Manager. EventBridge versus SNS for payment status is still open.
- LocalStack SQS is an optional profile, added only if that backlog item is picked up.
- Authentication is out of scope for the sample.
- Partial payments are out of scope for the sample.
- External systems are reached only through outbound ports and mocked with WireMock in tests.

## Open — set in a later ADR

Leave these unset. Do not invent values for them in code or in this file.

| Item | Where it is set |
|---|---|
| Numeric death-claim SLA | Death-claim prioritisation ADR |
| Payment callback mechanism, including EventBridge versus SNS | Sync vs async integration ADR |
| Straight-through amount limit | Not assigned to a ticket yet |
| What counts as a flag | Not assigned to a ticket yet |
| What counts as borderline (analyst versus reject) | Not assigned to a ticket yet |
| Retry count and backoff | Not assigned to a ticket yet |
| Whether the analyst queue is a separate port from the SQS queues | Not assigned to a ticket yet |
| Analyst decision endpoint (approve or reject a queued claim, for example `POST /claims/{id}/decision`) | Not assigned to a ticket yet |
