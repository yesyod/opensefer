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

A section Sefaria has **no text** for (it answers HTTP 404 — an empty daf, an uncommented chapter) is
noted in an `empty` namespace: it is not asked for again, stays "empty" (not "offline") without a
connection, takes no room in the reader, and counts as done for a download.

`BookDownloader` keeps **saved** books fully on the device: it downloads every passage (three at a
time, one book at a time), marks the book `offline` only once every passage is really on disk (a write
that fails — a full disk — is a failure, not a success), stops at the first sign of being offline or
of a full disk, and eases off — then gives up for now — when Sefaria keeps erroring. Saved books up to
300 passages download automatically; bigger ones on request.

**Where:** Android `Context.noBackupFilesDir` (persistent, excluded from Auto Backup); iOS
`Application Support/OfflineTexts`, flagged `NSURLIsExcludedFromBackupKey` (only that folder). Both are
re‑downloadable content the OS shouldn't back up; neither is purged behind the user's back. Because the
*library* is backed up but the texts aren't, a book marked `offline` whose index isn't on the device
(after a restore) loses the mark at startup and downloads again.

**Freeing space:** About shows the size used; "free up space" (after a confirmation) deletes everything
except the saved books' texts, indexes and "about" data.

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
- Disk use grows with reading (a few KB per passage); the user can free it from About without losing
  the books they saved.
- Tests cover the disk tier, stale fallback, error‑answer handling, empty sections, freeing space,
  de‑duplication and downloads (`DiskCacheTest`, `OfflineTextRepositoryTest`, `BookDownloaderTest`).
