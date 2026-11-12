# Freedom IPTV EPG Coverage and Accuracy Strategy

Goal: minimize blank/incorrect listings through layered sources, conservative matching, coverage diagnostics, and fast local caching. No source guarantees universal coverage; measure coverage honestly.

## Source layers
1. Xtream provider EPG: use provider epg_id/channel IDs, get_short_epg for now/next, get_simple_data_table for channel schedules, and xmltv.php when supported. Best lineup match but provider coverage/horizon varies.
2. User-configured XMLTV feeds: provider URL, user URLs, or licensed lineup service. Parse gzip/plain XML and map XMLTV channel IDs to app channel keys.
3. Premium licensed source: evaluate Schedules Direct (US/Canada/Mexico; typically 12-13 days XML, ~20 days JSON with decreasing certainty further out), Gracenote/Xperi/TV Media contracts for coverage, cost, SLA, attribution and redistribution rights. Keep vendor keys server-side.
4. Community/public feeds: iptv-org/epg, epgshare, epg.pw, open-epg etc. Useful for experiments and supplemental coverage only; verify freshness, IDs, terms and redistribution rights. Public URL is not a license.
5. TVmaze can enrich episode/show metadata and some schedules, not replace a universal linear guide; honor CC BY-SA attribution/share-alike and rate limits.

## Architecture
- Stable ChannelKey = provider account + stream ID. Preserve provider epg_id, name, category, region, language, callsign, network, HD/SD and feed/zone.
- Separate GuideChannel/XMLTV identities and aliases from provider IDs; never overwrite originals.
- Programme records store UTC start/end, title/subtitle/description/category/episode metadata, source, fetched time and confidence.
- Room/SQLite indexes (channelKey,startUtc,endUtc) and (startUtc,endUtc). Parse off main thread; render only visible windows.
- Streaming gzip XML parser (SAX/StAX), external entities/DTD disabled, size guards, conditional HTTP (ETag/Last-Modified), cancellation/retries, staging transaction and atomic publish. Preserve last-good guide on failure.

## Matching, ranked
1. Explicit user mapping (highest priority).
2. Exact provider EPG ID == XMLTV channel ID or approved alias.
3. Exact canonical callsign/network/feed/region.
4. Exact normalized name plus country/language/feed qualifiers.
5. Fuzzy candidate scoring only above strict threshold and margin from second candidate.
6. Duplicate propagation only for verified same feed; don't merge East/West, affiliates, languages or SD/HD blindly.
Never silently assign low-confidence candidates. Provide manual mapping with preview and record confidence/source.

Normalize provider prefixes, punctuation, whitespace, known aliases/callsigns and cosmetic resolution suffixes; preserve meaningful region, timezone, language and feed distinctions.

## Quality repair
Per channel/day track horizon, freshness, count, timeline gaps/overlaps, now coverage, mapping confidence, conflicting sources and timezone anomalies. Detect empty guide, now/next-only, expired listings, suspicious time shifts, duplicate titles and impossible durations. Distinguish 24/7 loops/radio from missing EPG.
Merge per interval/field using source priority: provider current listings can outrank generic XMLTV; licensed lineup data can fill gaps. Deduplicate by time/title. Parse XMLTV offsets (UTC if omitted); store UTC and render in selected/channel timezone. Never shift all data automatically based on local timezone; allow explicit per-channel offset with preview.

## Refresh and performance
- First launch: fetch now/next for favorites/visible channels, show guide immediately; import full XMLTV in background.
- WorkManager stale-aware sync, network/battery constraints, exponential backoff+jitter, request coalescing and cancellation.
- Cache 3-7 days initially, configurable; prioritize now/next and visible guide. Batch sources; avoid one request per channel for whole guide.
- Streaming parse large feeds; avoid full XML in memory. Keep EPG independent of player; use lazy rows/stable keys and database range queries.
- Diagnostics screen: matched X/Y, now coverage, next-24h coverage, horizon, last refresh, unmatched channels, source health and attribution.

## Phases
1. Xtream EPG models/parser + now/next and full channel schedule.
2. Room guide/channel/source/mapping tables and DAO.
3. XMLTV streaming importer + multi-source manager.
4. Exact IDs/aliases, manual mapping and unmatched diagnostics.
5. Merge/conflict ranking, stale fallback, coverage QA.
6. Guide grid: time ruler, rows, current/next, days, programme details, play channel, favorites/categories/remote focus.
7. WorkManager refresh/cache/source priority/time shift settings, tests.
8. Licensed provider integration after commercial terms and coverage evaluation.

## Tests/targets
Fixtures for timezone offsets, DST spring/fall, malformed gzip/XML, huge feeds, duplicate IDs, provider timestamps, aliases, East/West ambiguity, overlaps/gaps and failed refresh rollback. Measure matching precision/recall; wrong mapping is worse than no mapping. Target 100% exact-ID deterministic matches, >98% fuzzy precision on labeled tests, no low-confidence silent assignments; report actual coverage rather than promise it.

## Rights/security
Only user-authorized feeds or sources whose terms permit use. For commercial distribution get written schedule display/cache/derived-data/territory/user-scale rights. Encrypt credentials, keep vendor keys server-side, redact provider URLs/passwords from logs. EPG and stream access are separate.

## References
- Xtream endpoint reference: https://github.com/worldofiptvcom/xtream-codes-api-documentation/blob/master/ENDPOINT_REFERENCE.md
- Xtream EPG: https://github.com/worldofiptvcom/xtream-codes-api/blob/master/docs/player-api/epg.md
- Community guides and channel mapping: https://github.com/iptv-org/epg
- Schedules Direct: https://www.schedulesdirect.org/faq
- XMLTV Listings API: https://www.xmltvlistings.com/help/api/docs
- TVmaze API/licensing: https://www.tvmaze.com/api
- EPG mapping diagnosis: https://openatv.github.io/enigma2-doku/en/epg/fehlende-daten/