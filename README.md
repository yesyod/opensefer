<div align="center">

# OpenSefer

**A minimalist, open‑source reader for Jewish texts — Android & iOS from one Kotlin Multiplatform codebase.**

No accounts. No onboarding. No bloat. Open the app and read.

[![CI](https://github.com/yesyod/opensefer/actions/workflows/ci.yml/badge.svg)](https://github.com/yesyod/opensefer/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%20%7C%20iOS-2ea44f)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.7.3-4285F4?logo=jetpackcompose&logoColor=white)
[![Contributor Covenant 2.1](https://img.shields.io/badge/Contributor%20Covenant-2.1-4baaaa.svg)](CODE_OF_CONDUCT.md)
[![PRs welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

*Texts by [Sefaria](https://www.sefaria.org). Not affiliated with or endorsed by Sefaria.*

</div>

---

OpenSefer is two things at once:

1. **A genuinely useful app** — a calm, fast, beautiful reader that shows only the books *you* chose, fetches text from Sefaria's open API, and keeps every book you open on the device so it opens instantly — even offline.
2. **A reference‑grade example** of how to build a modern **Kotlin Multiplatform + Compose Multiplatform** app *correctly* — clean architecture, unidirectional data flow, a shared UI for both platforms, offline‑first data with a layered cache, and full RTL/Hebrew support. **Fork it as the foundation for your own KMP app.**

The complete design rationale lives in **[BLUEPRINT.md](BLUEPRINT.md)** — read it to understand *why* every decision was made.

## ✨ Features

- **Zero onboarding** — instant reading, fully local, no login, no Firebase.
- **Your library, as a shelf of covers** — the home screen shows only the books you saved, each with a generated, category‑coloured **book cover** (leather board, spine on the right, gold lettering), reading progress and an "available offline" mark. A **continue reading** card sits on top; tap any cover to resume exactly where you stopped; long‑press for its contents, offline download or removal (with undo).
- **One‑tap save** — save a book from search, from its book page or from inside the reader.
- **Offline‑first** — every index and passage you open is kept on the device (memory → disk → network), so books reopen instantly and stay readable without a connection; saved books download completely in the background. See [ADR 0004](docs/adr/0004-offline-first-text-storage.md).
- **Copy a passage** — long‑press to select words, or tap verse numbers to pick whole verses and copy them **with their source** (e.g. `(בראשית א׳:א-ג)`).
- **Bookmarks at exact places** — bookmark any verse/halacha (or "here" from the top bar); find them in the reader's contents sheet and on the home screen.
- **Exact resume** — the reader reopens on the very segment (and scroll offset) you were reading.
- **Built for Hebrew** — a right‑to‑left Hebrew interface, first‑class BiDi (English stays left‑to‑right), and a client‑side **nikud** (vowels) toggle; Talmud tractates are addressed by daf and amud (ב. / ב:).
- **A real reading experience** — one continuous scroll through the whole book, adjustable text size, **Hebrew / English / bilingual** display, three reading **themes** (light · sepia · dark), a top bar that slides away while you read, and a chapter grid / section tree a tap away.
- **Segment‑addressable** — every halacha/verse is an individually addressable unit, so commentaries (Phase 2) drop in without reworking the reader.

## 🚀 Quick start

**Requirements:** JDK 17, the Android SDK (set in `local.properties` as `sdk.dir=…`), and — for iOS — Xcode 15+ on a Mac.

```bash
git clone https://github.com/yesyod/opensefer.git
cd opensefer

# Android — build a debug APK
./gradlew :composeApp:assembleDebug
# → composeApp/build/outputs/apk/debug/composeApp-debug.apk

# Android — install on a running emulator/device
./gradlew :composeApp:installDebug
```

Open the project in **Android Studio** (latest, with the Kotlin Multiplatform plugin) to run on an emulator with one click.

**iOS:** the shared Compose UI is exposed to iOS via `MainViewController()` ([composeApp/src/iosMain](composeApp/src/iosMain)). Generate/refresh the `iosApp` Xcode wrapper with the [Kotlin Multiplatform plugin](https://www.jetbrains.com/help/kotlin-multiplatform-dev/) and run from Xcode, or build the framework with:

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

> **Verified in CI/dev:** the Android debug build. iOS shares 100% of the UI and is configured; build it from a Mac with Xcode.

## 🏗️ Architecture

Clean Architecture in three layers with **unidirectional data flow** (UDF). Dependencies point inward; the domain knows nothing about Ktor or Compose.

```
Compose UI (screens) ── ViewModel ──exposes── StateFlow<UiState>
        │ intents (function calls)
        ▼
   Use cases / Repositories (domain interfaces)
        ▼
   Ktor + kotlinx.serialization → Sefaria API → HTML parser → domain models
        ▲
   memory LRU → on‑device store (okio, offline‑first) → network (de‑duplicated)
```

- **State:** one immutable `UiState` per screen, exposed as `StateFlow`; events are plain function calls. No MVI framework — the Google‑recommended pattern.
- **Navigation:** a tiny **backstack‑as‑state** `Navigator` (the Navigation 3 mental model) — the exact seam where Nav3 / `navigation-compose` plugs in later (see [BLUEPRINT §10.2](BLUEPRINT.md)).
- **Text rendering:** Sefaria's inline HTML is parsed into a framework‑neutral `RichText` in the shared module (no WebView, no Compose), then rendered to a Compose `AnnotatedString` in the UI layer.

### Project structure

```
opensefer/
├── composeApp/                 # Shared Compose UI + platform entry points
│   └── src/commonMain/kotlin/app/opensefer/
│       ├── App.kt              # Koin start + theme + navigation host
│       └── ui/                 # theme · text · navigation · library · search · toc · reader · about
├── shared/                     # KMP library — no UI
│   └── src/commonMain/kotlin/app/opensefer/core/
│       ├── model/              # pure domain models + RichText
│       ├── network/            # Ktor client + Sefaria DTOs + API service
│       ├── data/               # repositories · cache · HTML parser · mappers
│       ├── domain/             # repository interfaces · use cases · preferences
│       └── di/                 # Koin module
├── BLUEPRINT.md                # full architecture & product specification
└── gradle/libs.versions.toml   # single source of truth for versions
```

> This running MVP uses a clean **2‑module** split (`:shared` + `:composeApp`) with internal package layering that already mirrors the finer‑grained `core:*` / `feature:*` module target described in [BLUEPRINT §5](BLUEPRINT.md). Promoting a package to its own Gradle module is a mechanical next step — the boundaries are already in place.

## 🧰 Tech stack

| Concern | Choice |
| --- | --- |
| Language / UI | Kotlin · Compose Multiplatform (shared UI, Android + iOS) |
| Networking | Ktor (OkHttp on Android, Darwin on iOS) + kotlinx.serialization |
| State | `androidx.lifecycle` ViewModel (multiplatform) + `StateFlow` |
| DI | Koin |
| Async | kotlinx.coroutines + Flow |
| Caching | offline‑first: in‑memory LRU → on‑device JSON store (okio) → network, with in‑flight de‑duplication |
| Persistence | Jetpack DataStore (Preferences, multiplatform) — settings, library, reading position & bookmarks |

Exact versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## 🧭 Status & roadmap

**Working today:** Library shelf with generated covers · Search (Sefaria autocomplete, one‑tap save) · Book page (chapter grid / section tree) · **continuous reader** — the whole book scrolls as one stream, one row per segment (simple *and* complex books such as a Siddur; Talmud by daf/amud), RTL Hebrew + LTR English, numbered segments (instruction rubrics un‑numbered), copy with source, bookmarks, font scaling, themes, nikud toggle · **offline‑first text storage** with background download of saved books · **everything persists across restarts** (settings, library, exact reading position, bookmarks — Jetpack DataStore) · About/attribution.

**Documented next steps** (interfaces already in place):
- **Phase 2 — Commentaries:** tap a segment → bottom sheet of commentators → open via the existing reader path (`/api/links`).
- Bundle a dedicated nikud font (Frank Ruhl Libre, SIL OFL); footnote‑in‑sheet UX; side‑by‑side bilingual; convention‑plugin module split; tests + CI.

See [BLUEPRINT §14](BLUEPRINT.md) for the full milestone plan.

## 🤝 Contributing

Contributions are welcome — this repo is meant to be cloned, learned from, and improved. See [CONTRIBUTING.md](CONTRIBUTING.md). Before a PR, please make sure the project builds:

```bash
./gradlew :composeApp:assembleDebug
```

## 📜 License & attribution

- **Application code:** [Apache‑2.0](LICENSE).
- **Texts:** © their publishers, provided via Sefaria under per‑edition Creative Commons licenses (CC0 / CC‑BY / CC‑BY‑SA). See [ATTRIBUTION.md](ATTRIBUTION.md).

OpenSefer is an independent project and is not affiliated with, endorsed by, or sponsored by Sefaria.
