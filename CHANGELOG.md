# Changelog

All notable changes to OpenSefer are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] — 2026-06-18

First public release — a complete, minimalist reading loop on Android and iOS from one shared codebase.

### Added

- **Library** home screen showing only the books you add, with Kindle‑style resume‑on‑tap and
  long‑press to browse a book's table of contents.
- **Search** via Sefaria title autocomplete (Hebrew and English), with add‑to‑library.
- **Table of contents** for simple (numbered) and complex (named, e.g. a Siddur) books.
- **Continuous reader** — the whole book scrolls as one stream, with a drill‑down section tree, lazy
  per‑section text loading and neighbour prefetch, and numbered segments (instruction rubrics left
  un‑numbered).
- **About this book** screen — author, era, composition/publication details, and editions, from Sefaria.
- **Reading preferences** — font scale, Hebrew / English / bilingual modes, a nikud toggle, and three
  themes (light · sepia · dark), all persisted with Jetpack DataStore.
- **Persistence** of the library, last reading position, and preferences across restarts — no account.
- First‑class RTL/BiDi Hebrew support; native Compose rendering of Sefaria's inline HTML (no WebView).
- Reference‑grade project setup: Apache‑2.0, ATTRIBUTION, BLUEPRINT, ADRs, detekt, GitHub Actions CI
  (detekt + tests on both platforms), and unit tests for the data **and** presentation layers.

### Notes

- Network‑first by design: text is fetched from Sefaria on demand and cached in memory for the
  session; the app does not bulk‑download books. See [BLUEPRINT.md](BLUEPRINT.md) §8.
