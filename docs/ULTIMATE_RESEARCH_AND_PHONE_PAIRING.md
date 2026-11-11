# Freedom IPTV Ultimate Research — Engineering Deep Dive

Research blueprint, not implemented feature claims. Brand: red/blue/black eagle; Android phone + Android TV/Google TV/Fire TV.

## Player engine, channel zapping & tracks
- Foundation: AndroidX Media3 ExoPlayer, one app-owned player/session lifecycle, MediaSession, DefaultTrackSelector and TrackSelectionParameters. Reuse the player/surface across fullscreen and EPG mini-player; never spawn a player for each channel/screen.
- Keep Xtream/M3U URL resolution and headers separate from UI/player state. Channel selection cancels stale requests, resolves off-main, replaces MediaItem in-place, shows channel/loading feedback, ignores stale callbacks.
- Expose available audio languages/tracks, subtitles/captions, Auto/Off selection, aspect ratio and stream diagnostics. Persist preferences only when tracks are actually available.
- Buffering: start with Media3 DefaultLoadControl. Do not blindly increase buffers (zap latency/memory) or shrink them (rebuffering). Measure separate Live quick-zap, Live stability and VOD profiles; tune thresholds on real streams/devices. Avoid multi-channel preloading on constrained TV. Bounded exponential retry+jitter for transient errors; never loop auth failures. UI states: Connecting, Buffering, Playing, Reconnecting, Provider rejected, Unsupported stream, Offline, with Retry/Back.
- Test HLS, MPEG-TS, supported MP4; audio-only/no audio/alternate tracks/subtitles; redirects, weak Wi-Fi, packet loss, network handoff, offline recovery, codec/device limits, 1080p/4K and low RAM. Record time-to-first-frame, zap p50/p95, rebuffer ratio and fatal errors.
Evidence: Media3 ExoPlayer uses injectable TrackSelector and LoadControl, with defaults a sound baseline and custom buffering possible but requiring measurement. https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer ; https://android-developers.googleblog.com/2025/09/a-deep-dive-into-media3-preloadmanager.html

## Xtream compatibility, authentication & resilience
- Xtream is a de-facto compatibility surface, not a universal spec. Common Player API uses /player_api.php with username/password/action, JSON catalogs and stream paths/XMLTV; panels differ.
- Login checks user_info.auth/status/expiry and server_info. HTTP 200 can still contain auth=0. Distinguish bad/expired/disabled credentials, wrong port/TLS, timeout, malformed JSON, rate/flood limit, max connections and unsupported output format.
- Tolerant parsing: numeric strings or numbers; optional/null/missing fields; unknown fields; duplicate names; absent icons/category IDs; empty arrays; HTML error page; partial/truncated payload. Never crash on malformed response.
- URL encode parameters safely; normalize base path/port carefully, don't silently switch HTTP/HTTPS or guess. Redact username/password/token from logs and crash reports. Cooldown failed login; no credential brute force.
- Fetch live categories then streams; lazy VOD/series. Cache stable IDs, categories, icons and EPG IDs; bounded concurrency, cancellation and last-good cache. Respect Retry-After/rate limits, exponential backoff+jitter, avoid repeated catalog requests and request storms. Provider-specific adapters only with confirmed support.
- Fixtures: valid/auth=0 HTTP 200, 401/403, expired, missing status, numeric strings, unknown fields, empty/HTML/malformed JSON, 429, timeout/TLS/redirect, partial catalog, duplicate channels, port change, stream 403/connection cap.
References (community/implementation-specific, not normative): https://github.com/worldofiptvcom/xtream-codes-api/blob/master/docs/player-api/overview.md ; https://github.com/worldofiptvcom/xtream-codes-api/blob/master/docs/player-api/live-streams.md ; https://github.com/worldofiptvcom/xtream-codes-api/blob/master/docs/authentication.md ; https://xui.live/docs/api/xtream/

## EPG & sports experience
- Timeline/grid, Now/Next, channel schedule, program detail; current-time marker, Jump to Now, timezone/DST correct.
- Map EPG IDs with normalized matching + user override; never rely solely on channel display name. Parse XMLTV incrementally and load bounded time windows; cache guide and preserve last-good data.
- Sports discovery from provider categories, channel names/aliases, EPG descriptions/time intervals; user tags/favorites for missing metadata. “Live now” only when event interval and metadata say so; highlight next event and favorite teams/channels where identifiable.
- My Sports shortcut, favorites, event list, search, recently watched and quick jump. Do not invent schedules/events if EPG lacks data. Reminders/catch-up/record only if provider advertises and playback format supports.
- EPG mini-player reuses one Media3 player, no second decoder by default; disable/fallback on constrained devices. Tests: mismatched EPG IDs, overlaps/gaps, DST, timezone change, stale guide and huge channel catalog.

## Fire TV / Android TV compatibility
- True TV UX: D-pad/center/back, clear focus ring, no focus traps, predictable focus restoration after dialogs/player/mini-player; no touch-only controls. Large typography/high contrast for 10-foot viewing; primary actions within few clicks.
- Test physical remote, keyboard, pointer/air mouse, back/resume and voice where supported. Declare Leanback launcher and touchscreen not required; banner/icon, 4K assets, accessibility labels.
- Feature-detect codecs, RAM, resolution, APIs and Google services; Fire TV and Android TV are not identical. Use graceful fallbacks.
- Low RAM: ActivityManager.isLowRamDevice; reduce image cache, concurrent jobs, guide window and animation; release decoder/player resources, no duplicate ExoPlayer; Paging/Room, lazy images, bounded concurrency. Test memory pressure, launcher/background transitions, decoder exhaustion and low storage.
- Respect ambient mode and system PiP rules; in-guide mini-player differs from system PiP. Add Baseline Profile and measure startup/jank.
Evidence: https://developer.android.com/docs/quality-guidelines/tv-app-quality ; https://developer.android.com/training/tv/playback/memory ; https://developer.android.com/design/ui/tv

## Security, privacy & safe backups
- Encrypt provider secrets using Android Keystore-backed keys; ciphertext in app-private DB/files, never plaintext preferences. Keep credentials/tokens out of logs, analytics, crash traces, QR, clipboard and notifications. Redact URLs containing credentials.
- HTTPS-first; reject invalid TLS certs, no trust-all clients. Validate imported URLs/redirects and minimize permissions.
- Backup by default: safe settings, favorites, categories, UI preferences only; exclude credentials, bearer tokens, signed playlist URLs. Clearly tell users they must reauthenticate after restore. Configure Android Auto Backup/data extraction rules and inspect real backup/device migration.
- If encrypted export ever added: opt-in, passphrase, authenticated encryption, versioned format, no passphrase stored, tamper/wrong-password tests.
- Remove provider/revoke devices/clear catalog/cache; explain deletion and retention. Privacy policy covers local data, pairing backend, diagnostics opt-in and deletion.
- Security tests: logs contain no secrets, archive inspection, tamper restore, invalid TLS, expired/replayed/wrong-device QR, rate limits and revocation.
References: https://developer.android.com/privacy-and-security/keystore ; https://developer.android.com/identity/data/autobackup ; https://developer.android.com/identity/data/autobackup#backup-rules

## Freedom Link phone-to-TV (locked design)
TV QR + short code; mobile web setup for Xtream/M3U; TV identity confirmation and explicit approval. QR is temporary session reference only. Cross-network pairing requires HTTPS backend; QR alone does not transfer securely. One-time grant, short expiry, rate limits, replay prevention, revoke controls. Manual remote and same-LAN fallback. TV encrypts credentials and starts real cancellable import.
References: https://www.rfc-editor.org/rfc/rfc8628 ; https://www.rfc-editor.org/rfc/rfc10027

## Large playlists 58k+
Streaming parser (never giant string), Room batch transactions, staging generation/atomic promotion, cancellation/resume, Paging 3, SQL filters, stable IDs, FTS/prefix search, lazy images, Live-first, last-good cache. Benchmark 1k/10k/58k/100k on phone + low-RAM TV; measure import, heap/GC, search, scroll, EPG and zap. Targets pending profiling: app shell <500ms, local page <300ms p95, search <300ms p95 after debounce; no main-thread parsing.

## Priorities
P0: Xtream/M3U; secure credential store; live player/zap; channel list/category/search/favorites; import progress/cancel; TV D-pad/mobile; EPG Now/Next/timeline; Freedom Link.
P1: EPG mini-player; sports events/favorites based on metadata; provider profiles; safe backup; advanced track controls/reconnect.
P2: multiview, DVR/timeshift, advanced customization only after provider/device proof.

## Release gates
Unit tests: parsers, URL normalization, auth states, EPG/timezones, pairing. Integration: Room rollback, player/zap race, tracks/reconnect, Keystore/backup. UI: D-pad/focus/back/remotes/low-RAM. Performance: 58k import/search/scroll/EPG/startup/zap on real devices. Security: redaction/TLS/replay/rate-limit/backup inspection. Release only after critical crash/security issues closed and capability caveats/privacy disclosures clear.

## Research caveats
Xtream references are community/vendor-specific; server behavior varies. Sports metadata may be absent. Targets are not measured guarantees. Competitor sentiment needs structured app-by-app review. This is a research blueprint, not proof features have shipped.
