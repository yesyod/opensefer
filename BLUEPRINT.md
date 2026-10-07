# OpenSefer — Technical Architecture & Implementation Blueprint

> A minimalist, network‑first **Jewish‑texts reader** for Android & iOS, built with Kotlin Multiplatform and Compose Multiplatform — and, deliberately, a **reference‑grade open‑source showcase** of how to build a modern KMP app *the right way*.
>
> Powered by the open [Sefaria](https://www.sefaria.org) API. *Not affiliated with or endorsed by Sefaria.*

**Status:** Specification v1.1 · **Audience:** maintainers & contributors · **Language of record:** English

> **What changed in v1.1.** UI structure locked after reviewing Sefaria's own open‑source apps: 5 screens + 3 bottom sheets, **chapter‑based reading with a quick chapter‑picker**, **segment‑addressable** reading surface, **reading themes** (light / sepia / dark), and **commentaries scheduled as Phase 2** (architecture made ready now). See [§2](#2-product-scope-mvp), [§10](#10-presentation-layer).

> ⚠️ *“OpenSefer”* is a working placeholder name. It intentionally does **not** include “Sefaria” to respect Sefaria’s [name & logo policy](https://developers.sefaria.org/docs/usage-of-our-name-and-logo). Rename freely before release.

---

## Table of Contents

1. [Context & Vision](#1-context--vision)
2. [Product Scope (MVP)](#2-product-scope-mvp)
3. [Guiding Principles](#3-guiding-principles)
4. [Architecture Overview](#4-architecture-overview)
5. [Module Structure](#5-module-structure)
6. [Technology Stack](#6-technology-stack)
7. [Sefaria API Integration](#7-sefaria-api-integration)
8. [Data Layer & Caching Strategy](#8-data-layer--caching-strategy)
9. [Domain Layer](#9-domain-layer)
10. [Presentation Layer](#10-presentation-layer)
11. [Hebrew · RTL · Typography](#11-hebrew--rtl--typography)
12. [Quality, Tooling & CI/CD](#12-quality-tooling--cicd)
13. [Repository Hygiene & Documentation](#13-repository-hygiene--documentation)
14. [Implementation Roadmap](#14-implementation-roadmap)
15. [Risks & Mitigations](#15-risks--mitigations)
16. [Open Decisions](#16-open-decisions)
17. [Appendix A — README Draft](#appendix-a--readme-draft)

---

## 1. Context & Vision

**The problem.** Existing Torah‑text apps are heavy: accounts, onboarding flows, cluttered UIs, multi‑hundred‑MB offline databases. A reader who simply wants to open *Mishneh Torah, Hilchot Yesodei HaTorah* and read it in clean, large type has to wade through everything else first.

**The product.** OpenSefer is the opposite: open the app and you are already reading. No account, no login, no Firebase, no onboarding. The home screen shows **only the specific books you chose** — nothing else. Text is fetched **on‑the‑fly** from Sefaria over the network (no bulk download), cached intelligently so reading feels instant.

**The second purpose.** This repository is also a **teaching artifact**. It is meant to be cloned and used as a clean, professional foundation for *any* KMP + Compose Multiplatform app. Every decision here is made to the standard of a flagship open‑source library (think Google’s *Now in Android*, JetBrains’ Compose Multiplatform samples). The code is the documentation of how to do it correctly.

**Intended outcome.** A pixel‑perfect, minimalist, fast reader on Android & iOS from a **single shared codebase**, plus a blueprint others can fork to bootstrap a high‑quality KMP project in an afternoon.

---

## 2. Product Scope (MVP)

### In scope

| Capability | Description |
| --- | --- |
| **Library (Home)** | Shows only the user’s explicitly selected books/volumes, each with a “continue reading” position. Empty‑state invites adding the first book. Long‑press to remove. |
| **Add / Search** | Search Sefaria by title (autocomplete) to find a specific book/volume; preview its table of contents and add it. |
| **Table of contents** | A book’s structure as a **chapter grid** (and an intermediate level for deep works like the full Mishneh Torah: book → halachot → chapter). Reached from search (preview) and from the library (navigate). Tap a chapter to read. |
| **Read** | Clean reading view. **Unit of reading = a chapter** (`perek`), scroll within it, swipe to next/previous chapter (prefetched). Hebrew (with/without nikud) and/or English, large legible type, **dynamic font resizing**, **reading themes** (light / sepia / dark). Each halacha/verse is a **numbered, tappable segment**. |
| **Chapter navigation** | A **quick chapter‑picker** opens from the reader header (tap the title) to jump to any chapter without leaving the reading flow — plus the full TOC and swipe paging. |
| **Persistence** | Remembers selected books + last reading position + reading preferences (font scale, language mode, nikud, theme). No account — local only. |
| **About / Credits** | Small screen: required Sefaria attribution + per‑edition source/translator credit, and a link to the repo. |
| **Offline‑graceful** | Recently read chapters are cached and remain readable without a connection. |

### Reading features adopted from Sefaria’s reader (the “A/א” menu), right‑sized for minimalism

**In MVP:** font size, language mode (Hebrew‑only / English‑only / **bilingual, stacked**), nikud toggle, reading theme (light/sepia/dark), TOC + chapter‑picker + swipe paging, persisted reading position.
**Deferred (cheap to add later):** cantillation (te’amim) toggle — same client‑side mechanism as nikud; bilingual **side‑by‑side** layout; a second font choice.
**Skipped:** multiple font families, aliyot markers, accounts/sync, source sheets, daily calendar.

### Phase 2 (planned, architecture made ready now)

**Commentaries / connections** (“mefarshim” — Rashi etc.). Deferred from MVP to protect the minimalist, zero‑onboarding feel, **but** the reader is built **segment‑addressable from day one** so this slots in additively: tap a segment → bottom sheet listing commentators (Rashi first) → open the commentary (it’s just another text, e.g. `Rashi on Genesis.1.1`, loaded by the same code path). Data cost is one endpoint (`/api/links/{ref}`); the real cost is the segment‑level interaction, which we pay for once, now. See [§10.5](#105-phase-2--commentaries-design-ready-not-built).

### Out of scope (MVP) — candidates for later

Full‑text search within a book · user notes/highlights · audio · sharing · cross‑device sync · downloading whole books for offline. *(All are natural extensions the architecture leaves room for.)*

---

## 3. Guiding Principles

1. **Single shared codebase, shared UI.** One Compose Multiplatform UI for Android and iOS. Platform code only where the platform genuinely differs (HTTP engine, file paths).
2. **Clean Architecture + UDF.** Strict layer boundaries (Data → Domain → Presentation). Unidirectional Data Flow: immutable state flows down, events flow up. No MVI framework — the plain Google‑recommended pattern.
3. **Network‑first, cache‑smart.** Fetch from Sefaria on demand; cache aggressively in memory; persist only what’s tiny and valuable (selections, positions, the rarely‑changing index).
4. **Minimalism is a feature.** Every screen, dependency, and module must justify its existence. Reductive by default. Ship MVP small; defer everything that fights the “open and read” feel.
5. **Segment‑addressable reading.** The reader renders a chapter as an ordered list of individually addressable, tappable segments — not one merged blob. This costs nothing now (the v3 API already returns segment arrays) and is the single hook that lets commentaries, footnotes, copy/share, and highlights land later **without reworking the reader**.
6. **Reference‑grade quality.** Convention‑plugin Gradle, version catalogs, static analysis, tests, CI for both platforms, generated API docs, exemplary README/CONTRIBUTING. If a top‑tier Google/JetBrains repo does it, we do it.
7. **Accessible & correct for Hebrew.** First‑class RTL, bidirectional text, nikud rendering, and user‑controlled text size are non‑negotiable.

---

## 4. Architecture Overview

Clean Architecture with three concentric layers. Dependencies point **inward** only; `:domain` knows nothing about Ktor, Compose, or Sefaria DTOs.

```
        ┌──────────────────────────────────────────────────────────┐
        │                    PRESENTATION (UI)                     │
        │   Compose Multiplatform screens · ViewModels · UiState   │
        │   Navigation 3 · Design System · RTL/typography          │
        └───────────────▲──────────────────────────┬───────────────┘
                        │ observes StateFlow<UiState>│ calls use cases
        ┌───────────────┴──────────────────────────▼───────────────┐
        │                       DOMAIN                              │
        │   Pure Kotlin models · Repository interfaces · UseCases   │
        │   No framework dependencies. The stable core.            │
        └───────────────▲──────────────────────────┬───────────────┘
                        │ implements interfaces      │ maps DTO→model
        ┌───────────────┴──────────────────────────▼───────────────┐
        │                        DATA                               │
        │  Repository impls · in‑memory cache · DataStore           │
        │  Ktor client · Sefaria DTOs · HTML→AnnotatedString parser │
        └──────────────────────────────────────────────────────────┘
```

**Data flow for “open a section”:**

```
ReaderScreen ─event→ ReaderViewModel ─call→ GetSectionTextUseCase
   → TextRepository.getSection(ref)
        → in‑memory cache hit?  → return immediately
        → else Ktor GET /api/v3/texts/{tref} → parse → cache → return
   ← Result<SectionText>
ReaderViewModel updates StateFlow<ReaderUiState> ─→ ReaderScreen recomposes
(parallel) prefetch next/prev sections into cache
```

State management is **plain UDF**: each ViewModel exposes one `StateFlow<XxxUiState>` and receives user intents as function calls. This is exactly the pattern in Google’s architecture guidance and *Now in Android* — chosen over Orbit/Ballast/Decompose for zero ceremony, maximum legibility, and teaching value.

---

## 5. Module Structure

Multi‑module Gradle build with **convention plugins** in an included `build-logic` build (the *Now in Android* pattern). This is the single most important structural decision for a showcase: it eliminates per‑module boilerplate and demonstrates how large teams scale a build.

```
opensefer/
├── build-logic/                      # Included build: custom Gradle convention plugins
│   └── convention/
│       └── src/main/kotlin/
│           ├── KmpLibraryConventionPlugin.kt        # id("opensefer.kmp.library")
│           ├── ComposeMultiplatformConventionPlugin.kt
│           ├── KmpFeatureConventionPlugin.kt        # bundles kmp+compose+viewmodel+koin+detekt
│           ├── AndroidApplicationConventionPlugin.kt
│           └── DetektConventionPlugin.kt
│
├── gradle/
│   └── libs.versions.toml            # The single source of truth for versions ★
│
├── composeApp/                       # Thin app shell (was the wizard's default module)
│   ├── src/commonMain/               # App composable, Nav host, DI graph start, theme wiring
│   ├── src/androidMain/              # Android Activity + Application
│   └── src/iosMain/                  # iOS entry point (MainViewController)
│
├── iosApp/                           # Native Xcode project (SwiftUI hosting Compose)
│
├── core/
│   ├── model/        # :core:model       — pure domain models (Book, Toc, SectionText…)
│   ├── common/       # :core:common      — Result, dispatchers, expect/actual platform bits
│   ├── designsystem/ # :core:designsystem— theme, typography, fonts, atoms, RTL helpers
│   ├── network/      # :core:network     — Ktor client, Sefaria DTOs + API service
│   ├── datastore/    # :core:datastore   — DataStore (selections, prefs, last position)
│   ├── data/         # :core:data        — repository impls + in‑memory cache + mappers
│   └── domain/       # :core:domain      — repository interfaces + use cases
│
└── feature/
    ├── library/      # :feature:library  — Home: the user's selected books
    ├── search/       # :feature:search   — Add a book (title autocomplete)
    ├── toc/          # :feature:toc      — Book table of contents (chapter grid); shared by search-preview & library
    └── reader/       # :feature:reader   — The reading experience (chapter unit, segments, sheets)
```

> The **About / Credits** screen is a single static composable — it lives in `composeApp` rather than its own feature module (over‑modularizing a legal/credits page earns nothing). `:feature:toc` *is* its own module because it’s reached from two places (search preview + library navigation) and we don’t want `:feature:search` and `:feature:library` depending on each other.

**Dependency rules (enforced by convention plugins & module structure):**

- `feature:*` → `core:domain`, `core:designsystem`, `core:model` (never `core:network`/`core:data` directly).
- `core:data` → `core:domain`, `core:network`, `core:datastore`, `core:model`.
- `core:domain` → `core:model` only. **No** Android/Compose/Ktor.
- `composeApp` → everything (it wires the graph and navigation).

> **Right‑sizing note.** This is intentionally a little more granular than a 3‑screen app strictly needs — because the repository’s second job is to *teach* modularization. Each module is small and single‑purpose. If you fork this for a tiny app, collapsing `core:domain` into `core:data` is a one‑line change.

---

## 6. Technology Stack

> **On versions:** The numbers below are the *target* modern stack at time of writing (mid‑2026). **Do not hardcode them from this document.** Generate the project from the current [KMP wizard](https://kmp.jetbrains.com), then let `gradle/libs.versions.toml` be the single source of truth and bump via Renovate. Compose Multiplatform, Kotlin, Nav3, and the AndroidX‑multiplatform artifacts move fast and must be kept in a tested, mutually‑compatible set (see JetBrains’ [compatibility matrix](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)).

| Concern | Choice | Rationale |
| --- | --- | --- |
| **Language** | Kotlin (latest stable, 2.4.x line) | — |
| **UI** | Compose Multiplatform (1.1x line; iOS stable since 1.8) | One shared UI, Android + iOS. |
| **Navigation** | **Navigation 3** (`androidx.navigation3` runtime + JetBrains CMP UI port) — *with a documented fallback* | Newest Google nav model: backstack‑as‑state. See [§10](#10-presentation-layer) and [§15](#15-risks--mitigations) for the multiplatform‑maturity caveat and the `navigation-compose` fallback. |
| **State holder** | `androidx.lifecycle` **ViewModel** (multiplatform) + `StateFlow<UiState>` | Google‑standard UDF; multiplatform ViewModel is stable. |
| **DI** | **Koin** (+ Koin Annotations for compile‑time checks) | Ubiquitous in KMP, lowest contributor friction, first‑class Compose/ViewModel integration. *(Compile‑time alternative — Metro / kotlin‑inject — noted in [§16](#16-open-decisions).)* |
| **Networking** | **Ktor 3.x** client (OkHttp engine on Android, Darwin on iOS) | The KMP standard HTTP client. |
| **Serialization** | **kotlinx.serialization** (JSON, `ignoreUnknownKeys = true`) | Sefaria sends large JSON; tolerant parsing is essential. |
| **Concurrency** | kotlinx.coroutines + Flow | — |
| **Caching** | **Hand‑rolled in‑memory LRU** in the repository (no heavy dep) | Minimalist, dependency‑free, *educational*. Store5 noted as a scaling option ([§16](#16-open-decisions)). |
| **Persistence** | **Jetpack DataStore (Preferences, multiplatform)** — ✅ *implemented* | Reactive `Flow`, tiny footprint; persists settings + library + reading position. Android `Context` captured via App Startup (no `Application` class). See [ADR 0002](docs/adr/0002-persistence-with-preferences-datastore.md). |
| **HTML parsing** | **Ksoup** (KMP Jsoup port) → `AnnotatedString` | Sefaria embeds HTML; we render **natively, no WebView**. (Android’s `AnnotatedString.fromHtml` is JVM‑only — unusable in `commonMain`.) |
| **Images** | Coil 3 (only if needed) | KMP image loading. MVP has almost no imagery. |
| **Testing** | kotlin.test · Turbine (Flow) · kotlinx‑coroutines‑test · Compose UI test · Roborazzi (screenshots) | Realistic KMP test stack. |
| **Static analysis** | detekt (+ compose rules) · ktlint (or Spotless) · `.editorconfig` | — |
| **Coverage / Docs** | Kover · Dokka | — |
| **CI** | GitHub Actions (ubuntu: test+Android; macOS: iOS framework) · Renovate | — |

---

## 7. Sefaria API Integration

Base URL: **`https://www.sefaria.org/api`**. **No API key, no auth** required for reads. Responses are JSON. Full docs: [developers.sefaria.org](https://developers.sefaria.org).

### 7.1 Endpoint set (exactly what this app calls)

| # | Purpose | Method & path | Notes |
| --- | --- | --- | --- |
| 1 | **Title autocomplete** (search to add a book) | `GET /api/name/{query}?type=ref&limit=10` | Returns matching refs/books/authors. Filter `type=ref` for texts. |
| 2 | **Book index / TOC** | `GET /api/index/{title}` | Returns the `schema` (depth, `sectionNames`, `addressTypes`, `lengths`) describing structure. Rarely changes → **persist it**. |
| 3 | **Available versions** | `GET /api/versions/{title}` | Lists every version: `versionTitle`, `language`, `languageCode`, license. Drives the He/En + nikud version picker. |
| 4 | **Section text** | `GET /api/v3/texts/{tref}` | v3 is current. Params: `version`, `return_format`, `fill_in_missing_segments`. Returns a `versions[]` array with the text as a nested (jagged) array. |
| 5 | *(opt.)* **Links / commentary** | `GET /api/links/{tref}` | For a future “connections” feature. |
| 6 | *(opt.)* **Full‑text search** | `POST /api/search-wrapper` | JSON body `{query,type:"text",field:"exact",filters}`. Post‑MVP. |

**Reference (`tref`) format.** Refs are title + dotted indices matching the schema depth, with spaces as underscores. Examples:
- `Genesis.1` (depth‑2 work: a whole chapter) · `Genesis.1.1` (one verse)
- `Shabbat.2a` (Talmud uses the `Talmud`/Daf address type)
- `Mishneh_Torah,_Foundations_of_the_Torah.1.1` (a Rambam chapter) · `…1.1.1` (a single halacha)

Paging next/previous section = incrementing the appropriate index per the book’s `schema.depth` and `lengths`.

### 7.2 The user flow → endpoint sequence

```
Add a book:     type query → (1) /api/name → pick a ref
Open the book:  (2) /api/index/{title}  +  (3) /api/versions/{title}   [persist index]
Read a section: (4) /api/v3/texts/{tref}?version=…&return_format=default
Page on:        recompute next tref → (4) again   [prefetched, so instant]
```

### 7.3 Text format & native rendering (critical)

Sefaria returns text with **inline HTML** plus Hebrew **Unicode combining marks** for nikud/te’amim. Tags seen in the wild: `<b>/<strong>`, `<i>/<em>`, `<big>/<small>`, `<sup>` (footnote markers, often `class="footnote-marker"`), `<i class="footnote">…</i>` (the footnote body, inlined), and `<a data-ref="…">` (internal cross‑references).

We render this **natively in Compose, without a WebView**, via a pure‑Kotlin parser in `commonMain`:

1. **Parse** the HTML string with **Ksoup**.
2. **Walk** the node tree, building an `AnnotatedString`:
   - `<b>` → `SpanStyle(fontWeight = Bold)`; `<i>` → `Italic`; `<big>/<small>` → relative `fontSize`.
   - `<sup>` footnote markers → a small superscript glyph wrapped in a **`LinkAnnotation.Clickable`** (Compose 1.7+/1.8 text‑link API) tagged with the footnote id; the footnote *body* is lifted out of the inline flow and surfaced in a bottom sheet / popover on tap.
   - `<a data-ref>` → `LinkAnnotation.Clickable` that navigates to that ref inside the app.
3. **Nikud toggle:** when the reader chooses “no nikud”, strip combining marks client‑side by removing the `U+0591–U+05C7` range (regex). This is more reliable than depending on a specific consonantal `versionTitle` existing for every book.

> Use `return_format=default` to keep the markup we want; `text_only` is available if we ever need a plain string (e.g. share/extract).

A trimmed v3 response shape the parser/DTO target:

```jsonc
{
  "ref": "Mishneh Torah, Foundations of the Torah 1:1",
  "heRef": "משנה תורה, יסודי התורה א:א",
  "versions": [
    {
      "versionTitle": "Torat Emet 363",
      "language": "he",
      "text": ["יְסוֹד הַיְסוֹדוֹת ...", "וִידִיעַת דָּבָר זֶה ..."]  // segments of this section
    }
    // ...an English version object, etc.
  ]
}
```

### 7.4 Performance, limits, licensing

- **No documented hard rate limit**, but treat the service with care: cache aggressively, dedupe in‑flight requests, prefetch only ±1 section, and handle `429` with backoff. Typical latency is ~100–500 ms — fine for on‑the‑fly reading *because* we cache and prefetch.
- **Persist the index** (TOC) and **cache section text in memory**; both change rarely within a session.
- **Licensing / attribution.** Sefaria texts are CC0 / CC‑BY / CC‑BY‑SA *per edition*. The app must:
  - Show a **“Texts from Sefaria”** credit and link, plus the **per‑version** source/translator attribution (available from `/api/versions`) on the reading screen or an “About this text” affordance.
  - Carry an `ATTRIBUTION.md` / in‑app “Credits” screen.
  - Avoid implying Sefaria endorsement; respect their name/logo policy (hence the neutral app name).

---

## 8. Data Layer & Caching Strategy

> **Superseded:** texts are now stored on the device and read from there first (memory → disk →
> network), and saved books download in full — see
> [ADR 0004](docs/adr/0004-offline-first-text-storage.md). The contract below still describes the
> repository's shape.

The repository is where “network‑first but feels instant” is engineered.

```kotlin
// :core:domain  — the contract (pure Kotlin, no framework types)
interface TextRepository {
    suspend fun getIndex(title: String): Result<BookIndex>
    suspend fun getVersions(title: String): Result<List<TextVersion>>
    suspend fun getSection(ref: SectionRef, options: ReadOptions): Result<SectionText>
}
```

```kotlin
// :core:data — network-first impl with an in-memory LRU + in-flight dedupe
internal class DefaultTextRepository(
    private val api: SefariaApi,                 // Ktor-backed, returns DTOs
    private val mapper: SectionMapper,           // DTO → domain (runs HTML→AnnotatedString)
    private val cache: LruCache<String, SectionText> = LruCache(maxSize = 64),
    private val inFlight: MutableMap<String, Deferred<Result<SectionText>>> = mutableMapOf(),
    private val scope: CoroutineScope,
) : TextRepository {

    override suspend fun getSection(ref: SectionRef, options: ReadOptions): Result<SectionText> {
        val key = ref.cacheKey(options)
        cache[key]?.let { return Result.success(it) }            // 1. memory hit → instant
        // 2. dedupe concurrent requests for the same key
        val job = inFlight.getOrPut(key) {
            scope.async {
                runCatchingApi { mapper.map(api.getText(ref.tref, options)) }
                    .onSuccess { cache[key] = it }
            }
        }
        return job.await().also { inFlight.remove(key) }
    }
    // getIndex/getVersions delegate to DataStore-backed cache (index rarely changes)
}
```

**Caching tiers:**

| Tier | What | Where | Lifetime |
| --- | --- | --- | --- |
| Hot | Current + prefetched ±1 sections | In‑memory LRU (64 entries) | Process |
| Warm | Book index / TOC, versions list | DataStore (serialized) | Until invalidated |
| Cold | Selected books, last position, prefs | DataStore | Persistent |

**Prefetch.** When a section loads, the ViewModel fires non‑blocking `getSection(next)` / `getSection(prev)` so swiping is instantaneous. Prefetch failures are silent.

**Offline behavior.** Cache hits serve without network. On a miss with no connectivity, the Reader shows a friendly “offline — connect to load this section” state while keeping already‑loaded sections readable.

**Error model.** All boundaries return `Result<T>` (or a small sealed `DataError`). No exceptions cross into the domain/UI. Errors map to explicit UiState branches (Loading / Content / Empty / Error‑with‑retry).

---

## 9. Domain Layer

Pure Kotlin. Models are immutable `data class`/`value class`; no Ktor, Compose, or DTO types leak in.

```kotlin
@JvmInline value class BookTitle(val value: String)

data class BookIndex(
    val title: BookTitle,
    val heTitle: String,
    val schema: TextSchema,           // depth, sectionNames, addressTypes, lengths
    val categories: List<String>,
)

data class RichSegment(
    val id: SegmentId,                // stable, addressable — the hook for commentary/footnotes/share
    val number: String,              // displayed label: "א", "1", a daf, …
    val content: AnnotatedString,     // already HTML-parsed
)

data class SectionText(
    val ref: SectionRef,
    val heRef: String,
    val chapter: ChapterContext,      // title, position (index / total) → drives header + chapter-picker
    val hebrew: List<RichSegment>?,   // ordered, individually addressable segments (never one merged blob)
    val english: List<RichSegment>?,
    val attribution: Attribution,     // per-version credit + license
)

// Phase 2 (modelled now, not wired into MVP UI): a connection on a segment.
data class Link(
    val commentator: String,          // "Rashi" / "רש״י"
    val category: LinkCategory,        // Commentary, Midrash, …
    val targetRef: SectionRef,         // e.g. "Rashi on Genesis.1.1" — loaded via the SAME text path
)
```

Use cases are thin, single‑responsibility, and testable — they orchestrate repositories and encode the few business rules:

`GetLibraryUseCase` · `SearchBooksUseCase` · `GetBookTocUseCase` · `GetSectionTextUseCase` · `AddBookToLibraryUseCase` · `RemoveBookUseCase` · `ObserveReadingPrefsUseCase` · `SaveReadingPositionUseCase`. *(Phase 2 adds `GetSegmentLinksUseCase`.)*

---

## 10. Presentation Layer

### 10.1 ViewModel + UiState (UDF)

```kotlin
class ReaderViewModel(
    private val getSection: GetSectionTextUseCase,
    private val prefs: ObserveReadingPrefsUseCase,
    private val savePosition: SaveReadingPositionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    fun onOpen(ref: SectionRef) { /* load + prefetch ±1 chapter; update _uiState */ }
    fun onNextChapter() { /* … */ }
    fun onPrevChapter() { /* … */ }
    fun onJumpToChapter(index: Int) { /* chapter-picker sheet → load + persist position */ }
    fun onToggleNikud() { /* … */ }
    fun onFontScaleChange(scale: Float) { /* persist + reflect */ }
    fun onChangeTheme(theme: ReadingTheme) { /* light / sepia / dark */ }
    fun onChangeLanguage(mode: ReadingLanguage) { /* He / En / bilingual-stacked */ }
    fun onSelectSegment(id: SegmentId?) { /* Phase 2: opens commentary sheet; null = dismiss */ }
}

data class ReaderUiState(
    val content: SectionContent = SectionContent.Loading,   // Loading / Content(segments) / Empty / Error
    val chapter: ChapterContext? = null,                    // title + index/total → header & picker
    val showNikud: Boolean = true,
    val fontScale: Float = 1f,
    val theme: ReadingTheme = ReadingTheme.Light,           // Light · Sepia · Dark
    val language: ReadingLanguage = ReadingLanguage.HebrewEnglish,
    val selectedSegmentId: SegmentId? = null,               // Phase 2 commentary sheet anchor
)
```

One immutable state object in, intents as function calls out. Trivial to unit‑test with Turbine. The three bottom sheets (chapter‑picker, display options, commentary) are **UI state inside this screen**, not navigation destinations.

### 10.2 Navigation — Navigation 3 (with fallback)

Five destinations as type‑safe `@Serializable` routes: `Library` (start) · `Search` · `BookToc(title)` · `Reader(ref)` · `About`. The **bottom sheets** (chapter‑picker, display options, commentary) are **not** routes — they’re reader UI state (see [§10.1](#101-viewmodel--uistate-udf)).

**Primary:** Navigation 3 — the backstack is **state you own** (`SnapshotStateList`), rendered by a `NavDisplay` with an `entryProvider`, ViewModels scoped per entry. This is the direction Google is taking navigation.

```kotlin
val backStack = rememberNavBackStack(Library)         // backstack-as-state
NavDisplay(
    backStack = backStack,
    entryProvider = entryProvider {
        // Library: tap a book → continue reading from last position (Kindle-style)
        entry<Library> { LibraryScreen(
            onAddBook = { backStack.add(Search) },
            onContinue = { ref -> backStack.add(Reader(ref)) },
            onBrowse = { title -> backStack.add(BookToc(title)) },   // long-press / overflow
            onAbout = { backStack.add(About) }) }
        entry<Search>  { SearchScreen(onPreview = { backStack.add(BookToc(it)) }) }
        entry<BookToc> { BookTocScreen(it.title, onChapter = { backStack.add(Reader(it)) }) }
        entry<Reader>  { ReaderScreen(it.ref) }       // owns its 3 sheets internally
        entry<About>   { AboutScreen() }
    },
)
```

> ⚠️ **Maturity caveat — read [§15](#15-risks--mitigations).** Nav3’s **runtime** is multiplatform, but the **shared‑UI (CMP/iOS) `NavDisplay`** is the newest, least‑proven piece. **Mitigation:** keep all navigation behind a thin internal `Navigator` abstraction and `@Serializable` routes. If the Nav3 CMP UI isn’t production‑ready at build time, swap to JetBrains’ stable `org.jetbrains.androidx.navigation:navigation-compose` (the multiplatform Navigation 2 port) **without touching screens or routes**. This keeps the modern target while de‑risking delivery.

### 10.3 Screens (pixel‑perfect minimalism)

Five screens, stack navigation, **no tab bar**.

1. **Library (Home).** A quiet list of *only* the chosen volumes (Hebrew title primary, large) each showing a “continue reading” position. Generous whitespace, one unobtrusive `+`, strong empty state, long‑press to remove. **UX decision (Kindle‑style):** tapping a book **resumes reading from the last position** — chapter jump is always one tap away from the reader header. *(Sefaria opens the TOC on tap; we chose resume‑to‑read to honor “open and read.” Browse‑to‑TOC remains available via long‑press/overflow.)*
2. **Search / Add.** One field, debounced autocomplete (`/api/name`). Tap a result → its TOC as a preview with **“Add to library.”** No filters, no chrome.
3. **Table of contents.** The book’s structure as a **chapter grid** (א, ב, ג…). Deep works (full Mishneh Torah) get one intermediate level (book → halachot → chapter). Reached from search (preview/add) and from the library (browse/navigate).
4. **Reader** — the heart. Unit = **a chapter**; scroll within, **swipe** to next/prev chapter (prefetched). Each halacha/verse is a **numbered, tappable segment**. Edge‑to‑edge, comfortable measure, large adjustable type, distraction‑free (controls auto‑hide). Header shows “Hilchot Teshuva · Perek ב ⌄”; tapping it opens the **chapter‑picker** sheet. Footnotes open in a sheet.
5. **About / Credits.** Static: required Sefaria attribution + per‑edition source/translator credit, repo link, license note.

**Three bottom sheets (reader‑owned, not screens):**
- **Chapter‑picker** — opens from the reader header; all chapters with the current one marked; jump without leaving the reading flow.
- **Display options (“A/א”)** — font size, language mode (He / En / bilingual‑stacked), nikud toggle, reading theme (light/sepia/dark).
- **Notes / commentary** — MVP: a tapped footnote marker shows its note here. **Phase 2:** tapping a segment shows its commentators ([§10.5](#105-phase-2--commentaries-design-ready-not-built)).

### 10.4 Screen map & navigation graph

```
                    ┌─────────────┐
              ┌────▶│   Search    │──preview──┐
              │     └─────────────┘           ▼
   (+)        │                        ┌─────────────┐
┌─────────────┐  browse / long-press   │   BookToc   │   tap chapter
│   Library   │───────────────────────▶│ chapter grid│──────────────┐
│   (start)   │                        └─────────────┘               ▼
└─────────────┘                                              ┌─────────────┐
   │   │  tap a book → continue reading (Kindle-style)       │   Reader    │
   │   └────────────────────────────────────────────────────▶│  (chapter)  │
   │                                                          └─────────────┘
   │  about                                                     │  ▲   ▲  ▲
   ▼                                                  swipe ⇄ chapters │  │  │
┌─────────────┐                                  ┌────────────────────┘  │  │
│    About    │                                  │  sheets (UI state, not routes):
└─────────────┘                                  ▼  ① chapter-picker (header)
                                                    ② display options "A/א"
                                                    ③ notes / commentary (Phase 2)
```

### 10.5 Phase 2 — Commentaries (design‑ready, not built)

Deferred from MVP to protect minimalism, but **no rework** will be needed because the reader is already segment‑addressable:

- **Trigger:** tap a segment → `onSelectSegment(id)` → the notes/commentary **sheet** opens for that segment.
- **Data:** `GetSegmentLinksUseCase` → `GET /api/links/{ref}` → group by `commentator` (Rashi first). One endpoint, cached, deduped.
- **Open a commentary:** it is just another text (`Rashi on Genesis.1.1`) loaded by the **same** `GetSectionTextUseCase` / Reader path — commentary‑on‑commentary is free recursion.
- **No new screens, no new navigation model.** The connections panel is a sibling sheet, fully decoupled from the base reader.

This is the payoff of [Guiding Principle #5](#3-guiding-principles): we pay the segment‑interaction cost once, now, and commentaries become additive later.

---

## 11. Hebrew · RTL · Typography

This is where most “generic” apps fail; for a Torah reader it must be flawless.

- **RTL & BiDi.** Compose Multiplatform applies the Unicode Bidi Algorithm and mirrors layout for RTL locales. For the reading surface we force `LocalLayoutDirection = Rtl` (Hebrew‑primary), and let mixed He/En/numbers resolve per BiDi. Verify on **iOS** specifically (a couple of minor caret/digit BiDi issues have historically been tracked upstream — see Risks).
- **Fonts (bundled, open‑licensed SIL OFL).** Ship a serif optimized for nikud as the reading face — **Frank Ruhl Libre** (classic Hebrew book face) or **Noto Serif Hebrew**; for cantillation, **Taamey Frank CLM**. Bundle via `compose.components.resources` (`Font(Res.font.…)`), so both platforms use identical glyphs. OFL permits bundling/redistribution in an open‑source app (include each font’s license in `THIRD_PARTY_NOTICES`).
- **Nikud handling.** Render with combining marks by default; the “no nikud” toggle strips `U+0591–U+05C7` client‑side (see [§7.3](#73-text-format--native-rendering-critical)). **Cantillation (te’amim)** uses the same client‑side stripping and is deferred to a later toggle (relevant only to Tanakh; Rambam has none).
- **Reading themes.** Three reading surfaces — **light, sepia (warm paper), dark** — defined in `:core:designsystem` as small color sets and selected from the display‑options sheet (persisted in DataStore). Sepia/dark are high‑value, low‑cost for a long‑form reader; theme affects the reading surface and chrome consistently.
- **Bilingual layout.** MVP renders Hebrew and English **stacked** (Hebrew segment, then its English) — simplest and most legible on phones. **Side‑by‑side** is a documented later option, gated behind the same language‑mode setting.
- **Dynamic text resizing.** Hoist a `fontScale` (persisted in DataStore) through a `CompositionLocal`; the design system multiplies the type ramp’s `fontSize`/`lineHeight` by it. Because Compose uses `sp`, system accessibility font scaling is also respected. Provide a simple in‑reader slider/stepper.

```kotlin
val LocalFontScale = staticCompositionLocalOf { 1f }

@Composable
fun OpenSeferTheme(fontScale: Float, content: @Composable () -> Unit) {
    val typography = rememberScaledTypography(base = OpenSeferType, scale = fontScale)
    CompositionLocalProvider(LocalFontScale provides fontScale) {
        MaterialTheme(colorScheme = OpenSeferColors, typography = typography, content = content)
    }
}
```

---

## 12. Quality, Tooling & CI/CD

| Area | Tool | Wiring |
| --- | --- | --- |
| **Build structure** | Convention plugins in `build-logic` + `libs.versions.toml` | Each module applies one `opensefer.*` plugin. |
| **Lint/style** | **detekt** (+ compose rules) · **ktlint** *or* Spotless · `.editorconfig` | `./gradlew detekt ktlintCheck`; fail CI on violations. |
| **Tests** | kotlin.test · **Turbine** · kotlinx‑coroutines‑test · Compose UI test | `commonTest` for shared logic; platform tests where needed. |
| **Screenshots** | Roborazzi (CMP‑aware) | Guard the Reader/Library against visual regressions. |
| **Coverage** | **Kover** (JVM/shared targets) | `koverHtmlReport`; advisory threshold (e.g. 70% of shared logic). |
| **API docs** | **Dokka** | Publish to GitHub Pages on release. |
| **Deps** | **Renovate** (better Gradle + version‑catalog support than Dependabot) | Grouped, scheduled PRs. |
| **Commits/PRs** | Conventional Commits + PR template + CODEOWNERS | Clean history, easy changelog. |
| **Pre‑commit** *(opt.)* | Lefthook/git hook running detekt+ktlint on staged files | Fast local feedback. |

**GitHub Actions matrix (realistic):**

```yaml
jobs:
  verify:                # ubuntu — fast feedback on every PR
    runs-on: ubuntu-latest
    steps: [checkout, setup-java(21), setup-gradle,
            "./gradlew detekt ktlintCheck test koverXmlReport"]
  android:               # ubuntu — assemble the app
    runs-on: ubuntu-latest
    needs: verify
    steps: ["./gradlew :composeApp:assembleDebug"]
  ios:                   # macOS — compile the shared framework for iOS
    runs-on: macos-14
    needs: verify
    steps: [cache(~/.konan), "./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64",
            "./gradlew iosSimulatorArm64Test"]
```

Cache Gradle (`gradle/actions/setup-gradle`) and Kotlin/Native (`~/.konan`) to keep the macOS job to ~2–3 min.

---

## 13. Repository Hygiene & Documentation

Because this repo is also a *teaching artifact*, the meta‑files matter as much as the code.

```
README.md                 # vision, screenshots, 60-second clone-and-run, architecture map, contribution pointer
CONTRIBUTING.md           # build, run on Android+iOS, run checks, coding standards, PR flow
CODE_OF_CONDUCT.md        # Contributor Covenant
LICENSE                   # Apache-2.0 (patent grant; matches Kotlin/Google ecosystem)
ATTRIBUTION.md            # Sefaria + per-text credits; data licensing explainer
THIRD_PARTY_NOTICES.md    # bundled fonts (OFL) and library licenses
CHANGELOG.md              # Keep a Changelog + Conventional Commits
SECURITY.md
docs/
  ARCHITECTURE.md         # this blueprint, trimmed to the living design doc
  ADR/0001-*.md           # Architecture Decision Records for the big calls
.github/
  workflows/ci.yml
  ISSUE_TEMPLATE/{bug_report,feature_request}.yml
  pull_request_template.md
  CODEOWNERS
  renovate.json
```

**License:** **Apache‑2.0** (explicit patent grant; the de‑facto choice across Kotlin/JetBrains/Google OSS). Document Sefaria’s separate *data* licensing distinctly from our *code* license.

The README must make the “clone, run, extend” story effortless and explicitly frame the repo as a **canonical example of doing KMP correctly** (draft in [Appendix A](#appendix-a--readme-draft)).

---

## 14. Implementation Roadmap

Sequenced milestones; each ends green (builds + passes checks on both platforms).

- **M0 — Foundation.** Generate from the KMP wizard → refactor into the [§5](#5-module-structure) module layout. Stand up `build-logic` convention plugins, `libs.versions.toml`, detekt/ktlint/Kover, CI skeleton (both platforms building). *Exit:* empty app launches on Android & iOS from shared UI.
- **M1 — Data backbone.** Ktor client + Sefaria DTOs + `SefariaApi`; `TextRepository` with in‑memory cache + dedupe; DataStore; HTML→AnnotatedString parser (with unit tests against real fixtures). *Exit:* fetch + parse a Rambam section in a test.
- **M2 — Reader.** Design system (fonts, RTL, scaled typography, **3 themes**); `ReaderScreen` + `ReaderViewModel`; **segment‑addressable** chapter rendering; **chapter‑picker** + swipe paging + prefetch; display‑options sheet (font/language/nikud/theme); footnote sheet. *Exit:* read a full Rambam volume smoothly — chapters, He/En‑stacked, resizable, themed.
- **M3 — Library, Search & TOC.** DataStore‑backed selected books; `LibraryScreen` (home, Kindle‑style resume); `SearchScreen` (autocomplete); `BookTocScreen` (chapter grid, preview/add); `AboutScreen`. Navigation 3 wiring. *Exit:* full loop — search → preview → add → home → resume read → jump chapter → persists across restart.
- **M4 — Hardening.** Offline/error states, accessibility pass (TalkBack/VoiceOver, dynamic type), iOS RTL verification, screenshot tests, raise coverage. *Exit:* all states covered; CI fully green.
- **M5 — Release polish.** README + CONTRIBUTING + ADRs + ATTRIBUTION, Dokka site, app icons/splash, tag `v0.1.0`. *Exit:* a stranger can clone and run in minutes and a contributor can land a PR confidently.
- **M6 — Phase 2: Commentaries** *(post‑MVP, no rework).* `GetSegmentLinksUseCase` + `/api/links`; tap‑segment → commentary sheet (Rashi first) → open via existing reader path ([§10.5](#105-phase-2--commentaries-design-ready-not-built)). *Exit:* read a verse with Rashi without leaving the reader.

---

## 15. Risks & Mitigations

| Risk | Likelihood | Mitigation |
| --- | --- | --- |
| **Navigation 3 CMP/iOS UI not production‑ready** at build time | Medium | Thin `Navigator` + `@Serializable` routes behind it; fall back to stable `navigation-compose` multiplatform with zero screen changes ([§10.2](#102-navigation--navigation-3-with-fallback)). |
| **Version drift / incompatible matrix** (Kotlin↔CMP↔Nav3↔AndroidX‑MP) | Medium | Never hardcode versions from this doc; pin via wizard output + catalog; follow JetBrains compatibility matrix; Renovate in grouped PRs with CI gating. |
| **Sefaria rate limiting / latency** under heavy paging | Low–Med | Aggressive cache, in‑flight dedupe, ±1 prefetch only, `429` backoff, persist index. |
| **iOS BiDi/caret edge cases** in Compose | Low–Med | Verify on device early (M2); isolate reading surface; keep a WebView‑free fallback renderer option documented if a blocker appears. |
| **HTML variety** across Sefaria texts (unexpected tags) | Medium | Parser defaults to “append text, ignore unknown tag”; fixture‑driven tests across diverse works (Tanakh, Talmud daf, Rambam, commentary). |
| **DI choice churn** (Koin vs compile‑time) | Low | DI confined to `composeApp` + per‑module modules; swappable. ADR records the decision. |
| **Scope creep** diluting minimalism | Med | MVP scope ([§2](#2-product-scope-mvp)) is contract; extras go to a backlog, not the MVP. |

---

## 16. Open Decisions

Recommendations made; flagged for explicit sign‑off (each will get an ADR):

1. **DI — Koin (recommended) vs compile‑time (Metro / kotlin‑inject).** Koin = ubiquity + lowest contributor friction (this is a base others fork). Compile‑time = stronger “Google‑grade” safety, costs friction/build time. *Leaning Koin; revisit if maximal compile‑time guarantees are a hard requirement.*
2. **Caching — hand‑rolled in‑memory (recommended) vs Store5.** Hand‑rolled = zero deps, minimalist, teachable. Store5 = batteries‑included network+SoT but an alpha API. *Leaning hand‑rolled for MVP; Store5 documented as the scale‑up path.*
3. **Reading font default — Frank Ruhl Libre vs Noto Serif Hebrew.** Both OFL. Frank Ruhl is the more “sefer”‑authentic book face. *Leaning Frank Ruhl Libre.*
4. **App name & package id** — placeholder “OpenSefer” / `app.opensefer`. Needs a final, non‑“Sefaria” name before release.

**Locked in v1.1** (no longer open): segment‑addressable reader · commentaries = Phase 2 (architecture ready) · reading themes light/sepia/dark in MVP · chapter‑based reading + quick chapter‑picker · 5‑screen + 3‑sheet UI structure · Kindle‑style resume‑on‑tap · bilingual = stacked for MVP.

---

## Appendix A — README Draft

```markdown
# OpenSefer

A minimalist, beautiful reader for Jewish texts — Android & iOS from one Kotlin
Multiplatform codebase. No accounts, no onboarding, no bloat. Open the app and read.

Texts from **Sefaria** (https://www.sefaria.org). Not affiliated with Sefaria.

> Built as a reference example of how to build a modern Kotlin Multiplatform +
> Compose Multiplatform app **correctly** — clean architecture, fully modular,
> tested, and CI'd for both platforms. Fork it as a foundation for your own app.

## ✨ Features
- Zero onboarding — instant reading, fully local, no login
- Your library, only your books — nothing you didn't choose
- Clean reading: Hebrew (nikud optional) & English, large adjustable type, RTL-perfect
- Network-first: fetched on the fly from Sefaria, cached so it feels instant

## 🚀 Quick start (≈60 seconds)
    git clone https://github.com/<you>/opensefer.git && cd opensefer
    ./gradlew :composeApp:assembleDebug      # Android
    open iosApp/iosApp.xcodeproj             # iOS (or run from Android Studio)
Requires JDK 21, Android Studio (latest), and Xcode for iOS.

## 🏗️ Architecture
Clean Architecture (Data → Domain → Presentation) · UDF with ViewModel +
StateFlow · Compose Multiplatform shared UI · Navigation 3 · Koin · Ktor +
kotlinx.serialization · DataStore. Full design doc: docs/ARCHITECTURE.md.

## 🤝 Contributing
See CONTRIBUTING.md. Run `./gradlew detekt ktlintCheck test` before a PR.

## 📜 License
Code: Apache-2.0. Texts: © their publishers via Sefaria (CC0/CC-BY/CC-BY-SA, per
edition) — see ATTRIBUTION.md. Bundled fonts: SIL OFL — see THIRD_PARTY_NOTICES.md.
```

---

*End of blueprint v1.1.*
