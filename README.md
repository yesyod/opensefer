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

1. **A genuinely useful app** — a calm, fast, beautiful reader that shows only the books *you* chose and fetches text on the fly from Sefaria's open API.
2. **A reference‑grade example** of how to build a modern **Kotlin Multiplatform + Compose Multiplatform** app *correctly* — clean architecture, unidirectional data flow, a shared UI for both platforms, network‑first data with smart caching, and full RTL/Hebrew support. **Fork it as the foundation for your own KMP app.**

The complete design rationale lives in **[BLUEPRINT.md](BLUEPRINT.md)** — read it to understand *why* every decision was made.

## ✨ Features

- **Zero onboarding** — instant reading, fully local, no login, no Firebase.
- **Your library, only your books** — the home screen shows nothing you didn't add. Tap a book to *resume* where you left off (Kindle‑style); long‑press to browse its chapters.
- **Network‑first reading** — text is fetched on demand from Sefaria and cached in memory (with neighbour‑chapter prefetch) so paging feels instant.
- **Built for Hebrew** — first‑class RTL/BiDi, segment‑by‑segment chapters, and a client‑side **nikud** (vowels) toggle.
- **A real reading experience** — adjustable text size, **Hebrew / English / bilingual** display, three reading **themes** (light · sepia · dark), and a quick **chapter picker** from the reader header.
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
   in‑memory LRU cache (network‑first, neighbour prefetch)
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
| Caching | hand‑rolled in‑memory LRU (network‑first) |
| Persistence | Jetpack DataStore (Preferences, multiplatform) — settings, library & reading position |

Exact versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## 🧭 Status & roadmap

**Working today:** Library (resume‑on‑tap) · Search (Sefaria autocomplete) · **continuous reader** — the whole book scrolls as one stream (simple *and* complex books such as a Siddur) with a drill‑down **section tree**, RTL Hebrew + English, numbered segments (instruction rubrics un‑numbered), font scaling, themes, nikud toggle · **everything persists across restarts** (settings, library, reading position — Jetpack DataStore) · About/attribution.

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
