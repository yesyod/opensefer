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
  de‑duplication, and background download of saved books (ADR 0004). Sections Sefaria has no text for
  (a daf without Gemara, an uncommented chapter) are remembered as empty: they take no room in the
  reader, aren't asked for again, and don't stop a book from finishing its download.
- **Copy a passage** — tap a verse (or several) to select it and copy whole segments with their source,
  e.g. `(בראשית א׳:א׳-ג׳)`; long‑press still selects single words.
- **Bookmarks** at specific segments — from a selected verse, or "here" in the top bar; listed in the
  reader's contents sheet and on the home screen. Removing a bookmark (or a book) can be undone.
- **One‑tap save** to the library from search results, the book page and the reader.
- **Book page** (replaces the flat TOC): cover, details, read/continue, save, offline status, and a
  chapter grid or drill‑down tree.
- Talmud tractates addressed by daf and amud; chaptered sections of complex books expand into chapters.
- An "on‑device texts" section in About, with the space used and a **free up space** action (after a
  confirmation) that deletes what was merely read and keeps the saved books whole.
- An **automatic** theme that follows the device's light / dark setting — now the default.
- A gentle "save this book?" (once, after reading on) in a book that isn't in the library.

### Changed

- The reader renders **one lazy row per segment** (not one item per chapter), loads passages two
  ahead/behind the screen, keeps the reader's place while text loads above it, and resumes on the
  exact segment and scroll offset. Position writes are debounced and flushed when the app stops.
- The whole interface is Hebrew and right‑to‑left; English reading text is laid out left‑to‑right.
  Buttons use gender‑neutral action nouns (שמירה, העתקה, הסרה…).
- Hebrew is the default reading language (bilingual and English are one tap away).
- Hebrew numerals in running text carry geresh / gershayim (פרק ל״א, דף ב׳ ע״א), so a chapter number
  can't read as a word; the margins and the chapter grid keep bare letters.
- The app draws edge‑to‑edge with the system bars visible (one swipe for Back again); on Android the
  reader's top bar slides away while reading (on iOS, where it holds the only way back, it stays put).
  The bars follow the reading theme, also on Android 7–9.
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
  no longer produce invalid refs; commentary segments align by position; character references —
  numeric ones included — decode in a single pass (`&#38;lt;` stays `&lt;`).
- Hiding nikud no longer deletes the maqaf, paseq and sof pasuq ("עַל־פְּנֵי" became "עלפני") — in the
  reader, in copied text and in bookmark previews.
- A corrupt or unreadable settings or library file no longer leaves the app on a blank screen: the
  file is replaced, reads are retried, and the defaults are used if all else fails.
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
