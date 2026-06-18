# 2. Persistence with Preferences DataStore

- **Date:** 2026-06-18
- **Status:** Accepted

## Context

Reading preferences (font scale, theme, language, nikud) and the user's library + reading positions
were held in `MutableStateFlow`s and **reset on every app restart**. We need local, account‑free
persistence that works on Android and iOS from the shared module. BLUEPRINT §6/§8 commit to Jetpack
DataStore (Preferences, multiplatform).

## Decision

- **Jetpack DataStore (Preferences), multiplatform** — two stores, `reading_prefs` and `library`
  (independent write cadence; a corrupt library file can't take preferences down with it).
- Reading prefs use typed preference keys; the **library list is stored as a kotlinx.serialization
  JSON string** under one key. The list is tiny, so this keeps a single dependency
  (`datastore-preferences-core`) and matches the blueprint's "Preferences" choice.
- The **platform file path** reuses the project's existing `expect/actual` pattern (mirroring
  `httpClientEngine()` in `core/network`): `NSDocumentDirectory` on iOS; on Android the application
  `Context` is captured by a **Jetpack App Startup `Initializer`** declared in `:shared`'s manifest.
- Repository **setters stay fire‑and‑forget** (non‑suspend, no caller changes); writes run on an
  injected app `CoroutineScope`. DataStore serialises writes, and the library's read‑modify‑write
  happens **inside the `edit{}` transaction** so rapid successive writes can't lose updates.
- Exactly **one DataStore instance per file**, enforced by named Koin singletons.

## Alternatives considered

- **Typed / Proto DataStore for the library** — more "correct" for structured data, but adds
  `datastore-core-okio` + a custom serializer for no real benefit at this size. Revisit if per‑book
  state grows complex.
- **An Android `Application` subclass** to hold the `Context` — works, but leaks an Android requirement
  up into `composeApp`; the app deliberately has no Application class. App Startup keeps all Android
  specifics inside `:shared`.
- **One combined store** — rejected; two stores give cleaner domain separation and corruption isolation.

## Consequences

- Settings, selected books, and last reading position survive restarts on both platforms — verified by
  unit tests (`DataStore*RepositoryTest`, which assert a *fresh instance reads what a prior instance
  wrote*) and an on‑device iOS simulator run (write → clean rebuild → state survived).
- Adds `okio` (pinned 3.9.0) and `androidx.startup` dependencies.
- **Out of scope (future):** persisting the cached book index/TOC ("warm" tier, BLUEPRINT §8).
