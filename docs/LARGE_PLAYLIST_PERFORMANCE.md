# Freedom IPTV — Large Playlist Performance Plan

## Goal
Handle user-authorized Xtream Codes catalogs and M3U playlists containing **58,000+ channels** without making startup, navigation, search, guide browsing, or playback feel blocked. This is a design target, not a benchmark claim: actual performance depends on device, provider response, playlist format, EPG volume, and network.

## Core rule: don't load 58k channel objects into the UI
Persist the catalog locally and query only the slice needed for the current screen. Never parse the playlist, enumerate all channels, download logos, or build the full EPG on the main/UI thread.

## Xtream import
- Authenticate and fetch only the catalog endpoints needed for the selected content types (live first; VOD/series on demand).
- Treat API responses as untrusted and potentially huge; stream/read incrementally where possible, avoid retaining raw JSON plus mapped objects plus UI copies simultaneously.
- Parse off-main-thread; map each item to a compact Room entity; write batches inside transactions.
- Keep IDs/provider identifiers stable; upsert in batches and avoid duplicate rows on refresh.
- Load categories separately; don't fetch or render every category's channel list at once.
- Import live channels first so users can start watching while remaining catalog work continues.
- Avoid eagerly fetching channel logos, stream URLs, or details for all channels. Resolve/cache logos and playback URL only when needed.
- Respect server limits and avoid parallel request storms; bounded concurrency, retry/backoff for transient failures, and cancellation on account/playlist switch.

## M3U import
- Read with a streaming parser (buffered input), not readText()/one giant String.
- Parse each EXTINF and its following URL as a record; tolerate BOM, CRLF/LF, long metadata lines, missing fields, and malformed entries.
- Do not split the entire file into a giant list or create all Channel objects before saving.
- Insert/upsert in bounded batches (e.g. 250–1,000 rows, tune by profiling) using Room transactions. Yield/check cancellation between batches.
- Keep import progress/count and last successful snapshot; use a staging playlist/import generation so interrupted refreshes don't destroy the currently usable catalog.
- Deduplicate according to a documented key (playlist identity + normalized stream URL/provider ID); do not collapse genuinely distinct channels just because names match.

## Storage/query architecture
- Room/SQLite is the catalog source of truth; UI receives Paging 3 pages or small query results, never a 58k in-memory list.
- Separate tables: Playlist, Category/Group, Channel, Favorite, RecentChannel, EpgProgram (and optional logo cache metadata).
- Store compact fields needed for list rendering: stable ID, playlist ID, group/category ID/name, display name, stream reference, logo URL, sort order, EPG key, capability flags. Don't store large raw JSON per row unless needed for diagnostics.
- Index common predicates/orderings: playlist ID, category/group ID, favorite relation/channel ID, normalized searchable name (and provider ID). Use composite indexes that match actual WHERE + ORDER BY queries; verify with SQLite EXPLAIN QUERY PLAN. Avoid indiscriminate indexes (they add import/update cost and storage).
- Use SQL filtering, sorting, and LIMIT/OFFSET or keyset paging—not Kotlin filtering/sorting of all rows.
- Use stable IDs in Compose LazyColumn/LazyRow; render only visible rows plus modest prefetch. Avoid nested scroll traps and expensive per-row recomposition.
- Search: normalized/searchable columns and SQL prefix/FTS index where useful; debounce input, cancel previous search, limit results, and avoid LIKE '%term%' over every row without measured strategy.
- Favorites and recents should be separate small tables; don't rebuild the complete channel list to update them.

## First-run and refresh experience
1. Show app shell/home immediately; never wait for complete catalog import.
2. Save credentials securely; show provider connection status and progress.
3. Import live catalog in background, publish batches/snapshot as usable, and allow viewing/playing imported channels.
4. Import categories/other sections and optional guide data after live is ready.
5. Refresh incrementally where provider supports it; otherwise stage and swap a fresh catalog safely.
6. Cache last-known-good catalog; provider/network failures must not blank a previously imported playlist.
7. Provide cancel, retry, progress, and useful errors. Don't claim "loaded" until count and completion state are accurate.

## EPG scale
- EPG is a separate dataset and can dwarf channels. Fetch/process by time window and source capability, not an unlimited all-history dump.
- Index by channel/EPG ID and start/end time; query only visible channel rows and the selected time range.
- Use one bulk XMLTV parse pipeline on IO/CPU dispatcher, batch writes, and preserve cached guide while refreshing.
- Map channel IDs explicitly; report unmapped/stale data rather than silently assigning incorrect schedules.
- Mini-player must reuse the existing Media3 player; browsing EPG must not start a second decoder or interrupt live playback.

## Memory, threading, and cancellation
- Use Dispatchers.IO for file/network/database I/O and a CPU dispatcher for expensive parsing/normalization; never block main.
- Use structured coroutines; cancel work when the user switches playlist/account, exits import, or starts a replacement refresh.
- Bound queues and concurrency. Avoid unbounded Flow buffering, thousands of simultaneous logo downloads, and retaining raw response bodies.
- Close streams/responses promptly; cap metadata field sizes and handle malformed input safely.
- Avoid loading artwork bitmaps at original size; use Coil-style lazy image loading with size-aware caching and placeholders.
- Measure heap and GC during import and long scrolling; memory must remain bounded as playlist size grows.

## Performance test plan (targets, not guarantees)
Test at **1k / 10k / 58k / 100k** entries, both clean import and refresh, on low-memory Android TV hardware and a modern phone:
- App shell/first frame appears before import completes; no catalog parse on main.
- Import throughput, peak heap, allocations/GC, database size, cancellation and recovery.
- Open Live TV and switch groups; query page of 50–100 channels.
- Search latency for exact/prefix/partial terms; favorites and recents.
- Scroll 10k/58k rows with logos; track frame jank and dropped frames.
- Open EPG with mini-player; scroll and tune channels without playback interruption.
- Provider timeout, malformed M3U, duplicate rows, 401/expired credentials, offline refresh, and interrupted import.
- Use Android Studio Profiler/Perfetto, Macrobenchmark, and SQL query plans; establish device-specific p50/p95 before setting release claims.

## Practical UX targets to validate
- First usable app frame: aim under 500 ms on representative hardware; don't wait for catalog.
- First channel page from local DB: aim under 300 ms p95.
- Search results: aim under 300 ms p95 after debounce for common queries.
- Guide navigation and scrolling: no blocking frame; target smooth 60 fps where device/display supports it.
- Large import: background progress, no ANR, no memory growth proportional to retaining all parsed objects.
Targets are engineering goals and must be measured—not advertised as universal guarantees.

## Sources
- Android startup and main-thread guidance: https://developer.android.com/topic/performance/issues/launch-time
- Android startup analysis, defer noncritical work and use Macrobenchmark/Perfetto: https://developer.android.com/topic/performance/appstartup/analysis-optimization
- Android performance measurement and jank: https://developer.android.com/topic/performance/measuring-performance
- Android Baseline Profiles: https://developer.android.com/topic/performance/appstartup/best-practices
- Room: https://developer.android.com/training/data-storage/room
- Paging 3: https://developer.android.com/topic/libraries/architecture/paging/v3-overview
- SQLite query planner/indexes: https://www.sqlite.org/queryplanner.html
- SQLite FTS5: https://www.sqlite.org/fts5.html
- Media3 ExoPlayer: https://developer.android.com/media/media3/exoplayer
