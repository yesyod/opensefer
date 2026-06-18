# 3. Two-module structure for v1

- **Date:** 2026-06-18
- **Status:** Accepted

## Context

BLUEPRINT §5 describes an aspirational, finely‑grained module graph: a `:build-logic` with custom
convention plugins, a `:core:*` family (`model`, `common`, `designsystem`, `network`, `datastore`,
`data`, `domain`) and a `:feature:*` family (`library`, `search`, `toc`, `reader`). That is the shape
a large, Now‑in‑Android‑style codebase converges on. OpenSefer's v1 is well under 5k lines.

## Decision

Ship **two Gradle modules**:

- **`:shared`** — the KMP library: `core/network`, `core/data`, `core/domain`, `core/model`, `core/di`.
  No Compose, no UI; this is the boundary that keeps the domain framework‑free.
- **`:composeApp`** — the Compose Multiplatform UI + platform entry points, depending on `:shared`.

The package layout inside `:shared` already mirrors the finer split, so promoting any package
(`core/network`, `core/data`, …) to its own Gradle module later is a mechanical move, not a redesign.

Because `:shared` deliberately has no Compose compiler, Compose can't infer the stability of its
(immutable) domain models. Rather than leak `androidx.compose.runtime.@Immutable` into the domain
layer, `:composeApp` ships a **Compose stability configuration file**
(`composeApp/compose_compiler_config.conf`) marking `app.opensefer.core.model.*` and
`ReadingPreferences` as stable. UI‑local state holders (`ReaderUiState`, `SearchUiState`) carry
`@Immutable` directly, since they live in the Compose module.

## Alternatives considered

- **Full multi‑module now** (`:core:*` + `:feature:*` + `:build-logic`) — rejected: real overhead
  (build graph, Gradle wiring, convention plugins) for little payoff at this size; premature.
- **A single module** — rejected: it would erase the `:shared` boundary that forbids Compose/Ktor in
  the domain — the project's central teaching point.
- **`@Immutable` on the shared models** — rejected: it would pull a Compose dependency into the
  framework‑neutral domain layer. The stability‑config file achieves the same recomposition benefit
  without that coupling.

## Consequences

- Faster builds and simpler onboarding for v1; the architecture story (`:shared` is UI‑free) stays
  crisp and enforceable.
- Migrating to the finer split + convention plugins remains available without restructuring code.
  That migration, plus a Dokka API‑docs site, is recorded as future work (BLUEPRINT §5, §12).
