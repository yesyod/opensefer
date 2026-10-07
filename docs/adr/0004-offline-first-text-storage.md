# 4. Offline‑first text storage

- **Date:** 2026-10-07
- **Status:** Accepted (supersedes the "network‑first, memory‑only" caching of BLUEPRINT §8 and the
  "out of scope" note at the end of [ADR 0002](0002-persistence-with-preferences-datastore.md))

## Context

Texts were cached only in memory for the session: every cold start refetched each index and passage,
nothing opened without a connection, and a passage that scrolled off screen mid‑load threw its download
away. Readers want the books they keep — and anything they've opened — to open instantly, offline too.

## Decision

`DefaultTextRepository` reads through three tiers, fastest first:

1. **Memory** — parsed chapters in the existing LRU (`SectionCache`); a book's contents and "about"
   data memoized.
2. **Disk** — a small okio‑based `DiskCache`: one JSON file per resource at
   `<root>/v1/<namespace>/<sha256(key)>.json`, written to a temp file and atomically moved into place.
   It stores the (trimmed) Sefaria DTOs, not parsed domain objects, so parser fixes apply to old
   entries. Entries are re‑validated after a while (texts and author/edition data 30 days, the index
   7 days — Sefaria does correct texts); when the network fails, a stale entry is served instead.
   Error answers (Sefaria reports unknown refs as HTTP 200 + `{"error"}`) and empty texts are never
   stored. The `v1` segment lets a future format change start a fresh folder.
3. **Network** — Sefaria, with concurrent requests for one resource de‑duplicated into a single call
   that runs in the app scope (a caller going away doesn't cancel a download another caller awaits).

Parsing runs on `Dispatchers.Default`, file IO on `Dispatchers.IO`. Failures map to a small domain
`DataError` (Offline / NotFound / Server) that the UI turns into Hebrew messages.

`BookDownloader` keeps **saved** books fully on the device: it downloads every passage (three at a
time, one book at a time, stopping at the first sign of being offline) and marks the book `offline`.
Saved books up to 300 passages download automatically; bigger ones on request.

**Where:** Android `Context.noBackupFilesDir` (persistent, excluded from Auto Backup); iOS Application
Support, flagged `NSURLIsExcludedFromBackupKey`. Both are re‑downloadable content the OS shouldn't back
up; neither is purged behind the user's back. About → "on‑device texts" shows the size and clears it.

## Alternatives considered

- **SQLDelight / Room KMP** — structured queries we don't need; adds a schema, a driver per platform and
  migrations for what is a key → JSON blob store.
- **Storing parsed domain models** — faster to load, but freezes parser bugs into the cache and couples
  the stored format to UI‑facing models.
- **The platform cache dirs** (`cacheDir`, `NSCachesDirectory`) — simplest, but the OS may purge them,
  silently undoing a book the user saved for offline reading.
- **Whole‑book/ranged requests** for downloads — fewer calls, but unverified response shapes; per‑passage
  requests reuse the exact path the reader already uses (and caches).

## Consequences

- Books reopen instantly and read offline; the library shows which are fully on the device.
- Disk use grows with reading (a few KB per passage); the user can free it from About.
- Tests cover the disk tier, stale fallback, error‑answer handling, de‑duplication and downloads
  (`DiskCacheTest`, `OfflineTextRepositoryTest`, `BookDownloaderTest`).
