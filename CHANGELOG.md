# Changelog

All notable changes to OpenSefer are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Library shelf with designed covers** — saved books appear as generated book covers in their
  category's colour (Sefaria's palette), with the spine on the right, a gold frame and gold, auto‑fitted
  titles; plus a "continue reading" card, reading progress, an offline mark, an "add book" tile and
  recent bookmarks. Long‑press a cover for contents, offline download or removal (with undo).
- **Offline‑first text storage** — every index and passage fetched is stored on the device and served
  from there first (memory → disk → network), with stale‑copy fallback when offline, in‑flight request
  de‑duplication, and background download of saved books (ADR 0004).
- **Copy a passage** — word selection by long‑press, and a verse‑selection mode (tap verse numbers)
  that copies whole segments with their source, e.g. `(בראשית א׳:א-ג)`.
- **Bookmarks** at specific segments — from a verse, or "here" in the top bar; listed in the reader's
  contents sheet and on the home screen.
- **One‑tap save** to the library from search results, the book page and the reader.
- **Book page** (replaces the flat TOC): cover, details, read/continue, save, offline status, and a
  chapter grid or drill‑down tree.
- Talmud tractates addressed by daf and amud; chaptered sections of complex books expand into chapters.
- An "on‑device texts" section in About, with the space used and a way to free it.

### Changed

- The reader renders **one lazy row per segment** (not one item per chapter), loads passages two
  ahead/behind the screen, keeps the reader's place while text loads above it, and resumes on the
  exact segment and scroll offset. Position writes are debounced and flushed when the app stops.
- The whole interface is Hebrew and right‑to‑left; English reading text is laid out left‑to‑right.
- The app draws edge‑to‑edge with the system bars visible (one swipe for Back again); the reader's top
  bar slides away while reading.
- Text requests use `version=source`, so Aramaic originals (Talmud, Targum) load.
- Search keeps to texts (`type=ref`), autofocuses, offers well‑known books, and works above the keyboard.
- Errors are shown as friendly Hebrew messages (offline / not found / generic).

### Fixed

- A crash after relaunching from the back‑stack root: the DI graph is now created once per process
  (a second DataStore on the same file is fatal).
- Each screen now has its own ViewModel scope, cleared when it leaves the back stack — reopening a book
  or picking a chapter no longer reuses a stale reader (wrong scroll target), and returning from
  "About this book" keeps the reader's place.
- No startup flash of the light theme or the default books before saved data loads.
- Cancelled searches no longer surface a "cancelled" error; a cancelled load is never treated as a failure.
- Sefaria errors returned as HTTP 200 are recognised (and never cached); default (untitled) schema nodes
  no longer produce invalid refs; commentary segments align by position; numeric HTML entities decode.
- Material's default purple no longer leaks into switches, fields and sheets.

### Removed

- The `material-icons-extended` dependency (replaced by a dozen small in‑code vectors), and the unused
  `ksoup` / `koin-compose-viewmodel` catalog entries.

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
  *(Superseded in [Unreleased] by offline‑first storage — ADR 0004.)*
