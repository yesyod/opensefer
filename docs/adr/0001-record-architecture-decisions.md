# 1. Record architecture decisions

- **Date:** 2026-06-18
- **Status:** Accepted

## Context

OpenSefer is also a teaching reference (see [BLUEPRINT.md](../../BLUEPRINT.md)), so the *why* behind
the code needs to be discoverable by contributors — not just the *what*.

## Decision

We keep **Architecture Decision Records** (Michael Nygard's format): one short, immutable Markdown
file per decision under `docs/adr/`, numbered sequentially. BLUEPRINT.md holds the high‑level design
rationale; ADRs capture individual, dated decisions and their trade‑offs.

## Consequences

Each significant decision gets a small record with its context and alternatives. Superseded decisions
are marked (not deleted), preserving the history of the design.
