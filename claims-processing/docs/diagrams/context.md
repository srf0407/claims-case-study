These systems are not connected today. This diagram shows the links the solution proposes.

```mermaid
flowchart LR
  subgraph people["Channels and people"]
    Claimant["Claimant"]
    Analyst["Claims Analyst"]
    Channel["Channel System<br/>Web form"]
  end

  subgraph api["This API"]
    Service["claims-service"]
  end

  subgraph existing["Existing systems"]
    UI["Claims System UI"]
    Registry["Client Registry"]
    Policy["Policy Manager"]
    Payment["Payment System"]
  end

  Claimant -->|"submits claim"| Channel
  Channel -->|"POST /claims"| Service
  Channel -->|"GET /claims/{id}"| Service
  Analyst -->|"reviews queued claim"| UI
  UI -->|"GET /claims/{id}"| Service
  UI -->|"approve or reject"| Service
  Service -->|"sync HTTP: client check, bank details"| Registry
  Service -->|"sync HTTP: policy, plan, benefits; portfolio for context"| Policy
  Service -->|"initiate payment, idempotency key"| Payment
  Payment -->|"HTTPS webhook, signed"| Service

  style UI fill:#d6e6f5,stroke:#2f5d8a,stroke-width:2px
```

## Legend

- Blue box: the Claims System UI already exists and is out of scope. This repo builds `claims-service`.
- A queued claim is approved or rejected in that UI. On approval, `claims-service` initiates payment. The decision endpoint is still open.
- Portfolio information is context only and does not decide pay-or-not.
- Client Registry and Policy Manager are synchronous HTTP calls from a queue worker.
- Payment is assumed external (Third Party on the case-study diagram). The brief does not confirm this. Status arrives by a signed HTTPS webhook. In the AWS mapping the webhook enters through API Gateway, the endpoint queues it, and a worker updates the claim (ADR-0002). EventBridge and SNS are not used.
