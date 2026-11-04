# Backlog

Tick items off as they're done. Titles only; details are clarified per ticket with `implement-ticket`.
Draft order; reorder or cut freely.

## 0. AI workspace setup
- [x] Research AI context files and skills for the workflow
- [x] Write AGENTS.md (golden rule, layer map, conventions)
- [x] Set up project skills (implement-ticket, test-strategy, architecture-decision-record, mermaid-diagrams, review-changes)
- [x] Create this backlog

## 1. Understand and decide

- [x] Confirm scope and assumptions from the case study
- [x] Context diagram of all systems
- [x] ADR: single service with outbound ports
- [x] ADR: sync vs async integration (payment callback)
- [x] ADR: death-claim prioritisation approach
- [x] Claim status lifecycle (state diagram)

## 2. Project setup

- [x] Spring Boot + Maven build and wrapper
- [x] ArchUnit architecture test
- [x] WireMock test setup
- [x] CI: verify on PRs and main
- [x] Dockerfile + docker-compose

## 3. Domain and use cases

- [x] Claim domain model (types, statuses, transition rules)
- [x] Submit claim use case
- [x] Validate client use case
- [x] Assess policy and benefits use case
- [x] Initiate payment use case
- [x] Straight-through payment for eligible accepted claims (needs limit and flag decided)
- [x] Apply payment status use case
- [x] Enqueue accepted claims for the worker (queue port, in-memory fake)
- [x] Death-claim priority handling
- [x] Claim worker use case (receive, run client and policy checks, route)
- [x] Remove ArchUnit empty-layer allowances (withOptionalLayers, failOnEmptyShould) once each layer has classes



## 4. Adapters

- [x] Claim intake endpoint (`POST /claims`)
- [x] Claim status endpoint (`GET /claims/{id}`)
- [x] Payment status callback endpoint
- [x] Client Registry client + WireMock tests
- [x] Policy Manager client + WireMock tests
- [x] Payment client + WireMock tests
- [x] In-memory claim repository
- [x] Error handling and validation responses



## 5. Close the end-to-end flow

- [x] Wire the claim flow: beans for the worker, client, policy, straight-through and payment use cases; scheduled worker; straight-through limit in config; analyst decision endpoint (approve starts payment, reject uses ANALYST_REJECTED); end-to-end test from submit to PAID with WireMock
- [ ] Runnable stack and hardening: docker compose up works (localstack profile, service-name endpoints, WireMock stubs for Client Registry, Policy Manager and Payment); callback secret from an environment variable; LocalStack tests in CI; DLQ redrive in init-aws.sh or listed as Open

## 6. Documentation

- [x] solution.md: concise description, assumptions (review), recommended changes to existing systems, and how the systems interact (sequence diagrams for the happy path, death claim, analyst decision and payment callback, plus a short reliability section); fix stale lines (in-memory only, LocalStack out of scope)
- [x] README: what it is, project structure and layers, how to run (local, Docker, LocalStack) and test, links to solution.md, the diagrams and the ADRs
- [x] ai-usage.md: how AI was used, what for, and the approach (AGENTS.md, skills, review gates)

## 7. Final

- [x] Death-claim SLA warning and breach alarms
- [x] LocalStack integration (SQS / DynamoDB)
- [ ] Final check against the brief
