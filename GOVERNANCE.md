# TrustKin Governance

## Maintainers

The approved maintainers for the Phase 0 baseline are:

- `@pmchanel5`
- `@xMrBobo`

Maintainers control the official repository, releases, signing decisions, issue
priorities, and approval of security-sensitive changes. Maintainer status does not
limit the GPL rights of recipients or forks.

## Merge policy

The `main` branch is protected. Changes require:

- a pull request;
- at least one approving review;
- CODEOWNER review where applicable;
- approval after the most recent push;
- resolved review conversations;
- passing required checks once those checks exist on `main`;
- linear history with force pushes and branch deletion disabled.

Security/privacy guarantees and release gates cannot be weakened without an approved
requirements or architecture change, an updated threat analysis, and explicit human
approval.

## Decisions

- Product requirements change in `docs/architecture/requirements.md`.
- Architecture changes update `docs/architecture/design.md` and add an ADR.
- Sequencing and implementation-plan changes update `docs/architecture/task.md`.
- Release approval remains a human product-owner decision.

## Community and forks

Community members may inspect, fork, modify, and redistribute TrustKin under GPLv3 or
later. Compatible third-party clients and relays are encouraged to follow the public
protocol specification and conformance tests as they become available.
