# 0001. Single service with outbound ports

Status: Accepted
Date: 2026-11-02 · Decided by: The developer

## Context

The Claims System exists and is not connected to the Client Registry, the Policy Manager, or the Payment System. `claims-processing` is the Claims System API: one deployable that owns input checks, the claim lifecycle, step order, failure handling, and death-claim priority. Client rules stay in the Client Registry. Policy, plan, and benefit rules stay in the Policy Manager. Portfolio information is context only.

`AGENTS.md` already places outbound ports in `application.port.out` and their clients in `adapter.out`. `solution.md` puts the Client Registry, the Policy Manager, Payment, claim storage, and queues behind those ports. This sample keeps the adapters in memory and mocks the three external HTTP systems with WireMock. How payment status is delivered, and how death claims are prioritised, are later ADRs.

## Options

1. **Microservices, one per step** (validate, assess, pay). Each step is its own deployable. Claim status, priority, and the payment callback cross a network. This is the natural evolution later if load or team size demands it. Rejected for now: it adds network calls between our own steps, distributed failure handling and three deployments, while the sample has one orchestration flow and one team.
2. **Single service, no ports.** The use cases call the external systems directly. Fewer types. `application` would take HTTP and external payloads, which `AGENTS.md` keeps out of that layer, and the use cases would have no fake port to test against.
3. **Single service with outbound ports.** One deployable. Use cases depend on ports for the Client Registry, the Policy Manager, Payment, claim storage (`ClaimRepositoryPort`), and one queue. Adapters implement the ports. `config` wires them.

## Decision

Option 3. `claims-processing` stays one service, and every call out of a use case goes through an outbound port. This is the shape already set in `AGENTS.md` and `solution.md`. Option 1 remains the later path if load or team size demands it.

The queue is one port. Whether the analyst queue is a separate port stays on the open list in `solution.md`.

## Consequences

- Use cases stay plain Java. HTTP, external payloads, and the in-memory stand-ins stay in the adapters. Unit tests fake the ports. WireMock stays on the Client Registry, Policy Manager, and Payment adapters.
- Because every external call is behind a port, a step can later be split into its own service by swapping the adapter, without touching the use cases.
- All steps ship in one deployable until that split. There is one more type per outbound dependency than option 2.
- The single queue port may need a follow-up ADR if the analyst queue becomes its own port.
- This ADR leaves the other open items in `solution.md` unset: the death-claim SLA number, the payment callback mechanism, the straight-through limit, flags, borderline cases, retry and backoff, and the analyst decision endpoint.
- AWS mapping (docs only): one service on ECS Fargate behind API Gateway, SQS behind the queue port, DynamoDB behind claim storage. The three external systems stay outside this service. EventBridge versus SNS is not decided here.

## Implementation note (2026-11-04)

The analyst decision endpoint is `POST /claims/{id}/decision`. Profile `localstack` adds SQS and DynamoDB adapters behind the same ports. The Policy Manager adapter does not read portfolio. See [solution.md](../solution.md).
