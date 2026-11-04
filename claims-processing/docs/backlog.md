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
- [ ] Remove ArchUnit empty-layer allowances (withOptionalLayers, failOnEmptyShould) once each layer has classes



## 4. Adapters

- [x] Claim intake endpoint (`POST /claims`)
- [x] Claim status endpoint (`GET /claims/{id}`)
- [x] Payment status callback endpoint
- [x] Client Registry client + WireMock tests
- [x] Policy Manager client + WireMock tests
- [x] Payment client + WireMock tests
- [x] In-memory claim repository
- [ ] Error handling and validation responses



## 5. Documentation

- [ ] Solution description
- [ ] Assumptions
- [ ] Recommended changes to existing systems
- [ ] System interactions (sequence diagrams)
- [ ] AI usage write-up
- [ ] README: how to run and test



## 6. Optional
- [ ] Death-claim SLA warning and breach alarms (config, monitoring)
- [ ] LocalStack integration (SQS / DynamoDB)
- [ ] Final check against the brief
