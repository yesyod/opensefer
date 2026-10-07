# Contributing to OpenSefer

Thanks for taking the time to contribute! OpenSefer is meant to be cloned, learned
from, and improved — whether you're fixing a typo, adding a feature, or forking the
whole thing as the basis for your own Kotlin Multiplatform app. This guide gets you
from a fresh clone to a merged pull request.

If anything here is unclear or out of date, that's a bug too — open an issue or a PR.

---

## Table of contents

- [Code of Conduct](#code-of-conduct)
- [Prerequisites](#prerequisites)
- [Project setup](#project-setup)
- [Build & run](#build--run)
- [Running tests](#running-tests)
- [Static analysis (detekt)](#static-analysis-detekt)
- [Coding standards](#coding-standards)
- [Architecture & layering rules](#architecture--layering-rules)
- [Commit style](#commit-style)
- [Pull request flow](#pull-request-flow)
- [Reporting bugs & requesting features](#reporting-bugs--requesting-features)
- [Licensing of contributions](#licensing-of-contributions)

---

## Code of Conduct

This project ships a [Code of Conduct](CODE_OF_CONDUCT.md) (Contributor Covenant).
By participating you agree to uphold it. Be kind; assume good faith.

---

## Prerequisites

| Tool | Version | Why |
| --- | --- | --- |
| **JDK** | **17** (Temurin recommended) | Both modules target `JvmTarget.JVM_17`. |
| **Android SDK** | compileSdk 35, minSdk 24 | Android build + emulator. Install via Android Studio's SDK Manager. |
| **Android Studio** | Latest stable, with the **Kotlin Multiplatform** plugin | One-click run on an emulator; best KMP tooling. |
| **Xcode** | 15+ (macOS only) | Required to build/run the iOS app. Not needed for Android-only work. |

You do **not** need a Sefaria API key — the reads OpenSefer makes are public and
unauthenticated.

---

## Project setup

1. **Fork** the repo on GitHub and **clone** your fork:

   ```bash
   git clone https://github.com/<your-username>/opensefer.git
   cd opensefer
   ```

2. **Point Gradle at your Android SDK.** Create a `local.properties` file in the repo
   root (it is git-ignored and machine-specific — never commit it):

   ```properties
   sdk.dir=/Users/you/Library/Android/sdk
   ```

   On Linux this is usually `~/Android/Sdk`; on Windows
   `C:\\Users\\you\\AppData\\Local\\Android\\Sdk`. Android Studio writes this file for
   you when you open the project.

3. **Open the project** in Android Studio (latest) so Gradle syncs and indexes. From
   there you can run the Android app on an emulator with one click.

> The first Gradle sync downloads dependencies and the Kotlin/Native toolchain; give
> it a few minutes.

---

## Build & run

All commands run from the repo root. The repository's verified-green build is the
Android debug build.

### Android

```bash
# Build a debug APK
./gradlew :composeApp:assembleDebug
# → composeApp/build/outputs/apk/debug/composeApp-debug.apk

# Install on a running emulator/device
./gradlew :composeApp:installDebug
```

Or just hit **Run** in Android Studio.

### iOS (macOS + Xcode)

The whole UI is shared Compose Multiplatform; iOS hosts it through
`MainViewController()` in [`composeApp/src/iosMain`](composeApp/src/iosMain). The
easiest path is the JetBrains **Kotlin Multiplatform** plugin / Android Studio, which
runs the iOS target on a simulator directly. To compile just the framework from the
command line:

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

> iOS shares 100% of the UI. The Android debug build is verified in CI; please build
> and sanity-check the iOS target from a Mac when your change touches shared UI,
> RTL/BiDi, or fonts.

---

## Running tests

Shared logic (parsers, mappers, repositories, use cases) is tested in
`shared/src/commonTest` with `kotlin.test`, **Turbine**, `kotlinx-coroutines-test`,
and Ktor's `MockEngine`.

```bash
# Run all shared targets' tests (Android + iOS simulator + ...)
./gradlew :shared:allTests

# Faster local loop — just the JVM/Android unit tests (shared logic + ViewModels)
./gradlew :shared:testDebugUnitTest :composeApp:testDebugUnitTest
```

Please add or update tests for any behavior you change, especially in the HTML→
`AnnotatedString` parser and the network/data layer (use real Sefaria fixtures where
practical).

---

## Static analysis (detekt)

We use **detekt** (pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml))
plus `.editorconfig` for Kotlin-official formatting. Run it before pushing:

```bash
./gradlew detekt
```

CI runs the same check; PRs that fail detekt won't be merged. Most formatting issues
are auto-fixable in the IDE (**Code → Reformat Code** with the project's
`.editorconfig` applied).

---

## Coding standards

- **Kotlin official style.** `kotlin.code.style=official` is set in
  `gradle.properties`; `.editorconfig` enforces 4-space indent, a 120-column limit,
  a trailing newline, and `ktlint_official`. No wildcard imports.
- **No Material-icons artifact.** OpenSefer deliberately avoids
  `material-icons-extended`. The dozen icons it needs are tiny in-code vectors in
  [`ui/icons/AppIcons.kt`](composeApp/src/commonMain/kotlin/app/opensefer/ui/icons/AppIcons.kt)
  (add one there, with a Hebrew `contentDescription` where it's used) — it keeps the
  binary small and the look minimalist.
- **Immutable state.** One immutable `data class XxxUiState` per screen, exposed as a
  `StateFlow`. Events are plain function calls on the ViewModel — no MVI framework.
- **`Result`, not exceptions, at boundaries.** The data layer returns `Result<T>` (or
  a small sealed error); exceptions must not cross into the domain/UI.
- **Pure domain.** Domain models are immutable `data class` / `value class` with no
  Ktor, Compose, or DTO types.
- **Naming & packages.** Code lives under `app.opensefer.core.*` (in `:shared`) and
  `app.opensefer` / `app.opensefer.ui.*` (in `:composeApp`). Keep new code in the
  matching package/layer.
- **Keep it minimal.** Minimalism is a product feature (BLUEPRINT §3). Every new
  dependency, screen, or option must justify itself. When in doubt, leave it out.

---

## Architecture & layering rules

OpenSefer follows Clean Architecture with **unidirectional data flow** (see
[BLUEPRINT.md](BLUEPRINT.md) §4–§5 and §10). Dependencies point **inward**; the domain
is the stable core and knows nothing about frameworks.

Today the app is a clean **2-module** split whose internal packages mirror the
finer-grained `core:*` / `feature:*` target in BLUEPRINT §5:

- `:shared` — `app.opensefer.core.{model, network, data, domain, di}` (no UI).
- `:composeApp` — `app.opensefer` + `app.opensefer.ui.{theme, text, icons, components,
  navigation, library, book, search, toc, reader, about}`.

**Layering rules to respect in any PR:**

- **Domain depends on `model` only** — no Ktor, Compose, Koin, or DTOs. Repository
  *interfaces* and use cases live here.
- **Data implements domain interfaces** and may use `network`, the cache, mappers, and
  the HTML parser. DTOs stay inside `network`/`data` and are mapped to domain models
  before crossing the boundary.
- **UI (`composeApp`) depends on the domain**, never on `network`/`data` internals.
  Screens observe a `StateFlow<UiState>` and send intents as function calls.
- **Sefaria HTML is parsed to a framework-neutral `RichText` in `:shared`** (no
  WebView, no Compose), then rendered to a Compose `AnnotatedString` in the UI layer.
- **Reader stays segment-addressable.** A chapter is an ordered list of individually
  addressable, tappable segments — never one merged blob. This is the hook for
  commentaries/footnotes (Phase 2); don't collapse it.

Promoting a package to its own Gradle module should stay a mechanical step — keep the
boundaries clean.

---

## Commit style

We use [**Conventional Commits**](https://www.conventionalcommits.org/). This keeps the
history readable and makes changelogs easy.

```
<type>(<optional scope>): <short, imperative summary>

<optional body explaining what & why>

<optional footer, e.g. "Closes #123" or "BREAKING CHANGE: ...">
```

Common types: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`,
`perf`, `style`. Examples:

```
feat(reader): add sepia reading theme
fix(parser): strip U+0591–U+05C7 only when nikud is disabled
docs(contributing): document iOS framework build
ci: cache ~/.konan on the macOS job
```

---

## Pull request flow

1. **Branch** off `main`: `git checkout -b feat/short-description`.
2. **Make your change** with tests and docs as needed. Keep PRs focused — one logical
   change per PR.
3. **Run the local gates** before pushing:

   ```bash
   ./gradlew :shared:allTests detekt
   ./gradlew :composeApp:assembleDebug
   ```

4. **Push** to your fork and **open a PR** against `main`. Fill in the
   [PR template](.github/pull_request_template.md) checklist (builds, tests, detekt,
   docs).
5. **CI runs** the `verify`, `android`, and `ios` jobs. Address any failures.
6. A maintainer reviews. Once approved and green, it gets merged. 🎉

Small, well-described PRs get reviewed fastest. If your change is large or
architectural, open an issue first to discuss the approach.

---

## Reporting bugs & requesting features

Use the issue forms:

- 🐞 [Bug report](.github/ISSUE_TEMPLATE/bug_report.yml)
- 💡 [Feature request](.github/ISSUE_TEMPLATE/feature_request.yml)

For anything security-related, **do not** open a public issue — see
[SECURITY.md](SECURITY.md).

---

## Licensing of contributions

OpenSefer's code is licensed under [Apache-2.0](LICENSE). By submitting a contribution
you agree it is provided under the same license (Apache-2.0 §5). Texts displayed by the
app come from Sefaria under their own per-edition licenses — see
[ATTRIBUTION.md](ATTRIBUTION.md); please don't bundle or commit copyrighted text into
the repository.

Happy hacking — and thank you for helping keep Torah texts open and accessible. 🙏
