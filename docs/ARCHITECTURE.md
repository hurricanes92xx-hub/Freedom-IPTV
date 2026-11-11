# Freedom IPTV — Architecture

## Product direction
A general-purpose Android IPTV client with a sports-first experience and Android TV support. Optimize startup, channel browsing, and zapping without compromising stream compatibility.

## Proposed stack
- Kotlin + Jetpack Compose
- AndroidX Media3 ExoPlayer
- Room (SQLite) for channels, groups, favorites, recents, and EPG
- Kotlin Coroutines + Flow for asynchronous ingestion and UI state
- WorkManager for provider refresh and EPG sync
- DataStore for preferences
- Android Keystore-backed encryption for provider secrets

## Modules (target structure)
- `app`: navigation, screens, dependency wiring
- `core/model`: provider, channel, EPG, playback models
- `core/database`: Room entities, DAOs, migrations
- `core/network`: HTTP client, retries, timeouts
- `data/provider-xtream`: Xtream login, categories, live/VOD/series catalogs
- `data/provider-m3u`: playlist parsing and metadata enrichment
- `feature/live`: channel guide, groups, favorites, recents
- `feature/player`: Media3 player, controls, track selection, zapping
- `feature/epg`: XMLTV parsing and program lookup

## Performance principles
1. Render cached channel rows immediately; refresh in the background.
2. Parse playlists incrementally and batch database writes.
3. Keep one player instance across channel changes; do not recreate it per zap.
4. Separate UI browsing from playback/network work.
5. Use bounded concurrency, request timeouts, and cancellation.
6. Load EPG on demand for visible channels/time ranges.
7. Avoid decoding/loading logos for off-screen rows; cache images.
8. Measure cold start, playlist import, guide scroll, and zap latency.

## Playback notes
- Use Media3's adaptive buffering defaults as a baseline; tune only with real-device measurements and stream tests.
- Report connection, buffering, ready, and error states distinctly.
- Support HLS/DASH and common authorized IPTV transport formats where Media3 supports them; evaluate additional extractors only as needed.
- Keep player lifecycle tied to app/screen lifecycle and TV focus behavior.

## Security
- Never commit playlist URLs, usernames, passwords, tokens, or sample real provider data.
- Redact secrets from logs and crash reports.
- Store credentials encrypted; provide delete-provider/logout controls.

## Milestones
- [ ] Android Gradle/Kotlin app scaffold
- [ ] Provider setup and credential storage
- [ ] Xtream catalog sync
- [ ] M3U import
- [ ] Room-backed channel catalog
- [ ] Live player + fast zapping
- [ ] Favorites, recents, groups
- [ ] EPG integration
- [ ] Android TV UI and remote controls
- [ ] CI build, tests, and release APK workflow
