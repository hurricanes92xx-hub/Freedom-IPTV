# Freedom IPTV — Ultimate Research & Product Blueprint

**Status:** Research/design specification; implementation not yet complete.
**Brand:** Red / blue / black, bald eagle identity, Freedom IPTV.
**Platform:** Android phones, Android TV, Google TV, Fire TV/Android TV-class devices.

## 1. Executive direction

Build a premium, fast, remote-friendly IPTV player. Optimize for reliable playback, fast channel switching, large playlists, a useful EPG, and frictionless setup. Avoid feature bloat before the core experience is stable.

## 2. Freedom Link: phone-to-TV onboarding

### User experience
1. TV opens a branded **Pair with Phone** screen with a QR code, short human-readable code, and countdown.
2. User scans QR using any phone camera (no app required for the first pairing path).
3. A first-party HTTPS mobile page opens. It displays the TV/device name and asks the user to confirm the code shown on their TV.
4. User chooses **Add IPTV Provider**: Xtream Codes or M3U.
5. User enters server URL, username/password, or playlist URL on phone; optionally saves a provider profile securely.
6. User reviews the target TV/device and presses **Connect to TV**.
7. TV receives a one-time authorization result, stores credentials securely, and begins import. Phone shows progress and success.
8. The TV remains usable with cached content where possible; import runs in the background and is cancellable.

### Fallbacks
- Enter a short code at the pairing website if QR camera is unavailable.
- Manual TV setup using remote (server, username, password, URL keyboard).
- Re-pair/revoke device from Settings.
- If phone and TV share LAN, optional local discovery/companion control can be explored later; do not require same Wi-Fi for cloud QR pairing.

### Pairing architecture
- TV generates a high-entropy one-time pairing secret/code and server-side pairing session.
- QR carries only a short-lived pairing/session reference, not IPTV credentials or a reusable access token.
- Mobile confirms the TV code/device identity and explicitly approves.
- TV polls or receives a push/WebSocket notification over TLS; polling is simplest for MVP and must respect server-specified interval.
- Exchange short-lived, single-use grant; TV then receives provider configuration encrypted in transit and persists it locally encrypted.
- Expire pending sessions quickly (e.g. 5–10 minutes); rate limit, throttle, invalidate after approval/attempt limit, and bind approval to exact TV session.
- Display clear device identity on both screens; never auto-approve.
- Avoid logging codes, provider credentials, playlist URLs containing tokens, or authorization payloads.
- Device pairing service is a backend requirement: do not pretend QR alone transfers data securely across networks. If backend is unavailable, use a clearly labeled same-network/local transfer or manual fallback.
- Security review against OAuth device authorization (RFC 8628) and cross-device phishing risks (RFC 10027).

## 3. Provider onboarding

### Xtream
- Fields: base server URL, username, password; normalize URL carefully and validate HTTPS where supported.
- Verify credentials and fetch account/status + live categories first; avoid full VOD/series import until user enables them.
- Handle common endpoint variations, trailing slashes, ports, redirects, TLS failures, expired accounts, and malformed responses gracefully.
- Support provider profiles and safe switching; redact credentials from UI/logs.

### M3U
- URL import and local file import.
- Streaming/buffered parser; no loading entire playlist into a giant string.
- Parse EXTINF metadata and URL records incrementally; recover from malformed entries.
- Detect duplicate URLs/IDs and preserve stable channel identity when possible.
- Offer group/category mapping and optional logo/EPG URL.
- Warn before importing a playlist URL that contains credentials/tokens.

## 4. Large playlist performance (58k+)

- Never parse, sort, filter, or build a 58k list on the main thread.
- Stream parsing with bounded memory and coroutine cancellation.
- Use Room/SQLite as source of truth; batch writes in measured transactions (initial range 250–1,000 records, benchmark).
- Use Paging 3 and SQL filtering/ordering; stable IDs; avoid full collection materialization.
- Import into staging generation; atomically promote when ready; preserve previous catalog if import fails.
- Render app shell immediately and display truthful import progress. Do not artificially delay startup.
- Load Live TV first; VOD/series/EPG/logo work can be lazy or independently scheduled.
- Lazy image loading, bounded concurrency, retries/backoff, connection reuse.
- Index only query paths that are actually used; inspect SQLite query plans.
- FTS/prefix search, debounce, cancellation, recent/favorite local queries.
- Cache last-good guide/catalog and keep it usable during refresh.
- Track memory, GC, CPU, DB size, import throughput, and jank.

### Performance test matrix
Playlist sizes: 1k / 10k / 58k / 100k.
Devices: current phone, low-memory Android TV, Fire TV-class device, slow storage.
Network: fast, high latency, offline, dropped connection.
Measure: first frame, import throughput, peak heap, GC pauses, DB queries, search latency, scroll jank, channel-zap p50/p95, EPG render, recovery/cancel time.

Targets are engineering goals, not guarantees until measured:
- app shell/first frame under ~500ms on reference hardware
- cached channel page query under ~300ms p95
- search under ~300ms p95 after debounce
- no main-thread parsing or long blocking transactions
- stable scrolling and responsive remote focus

## 5. Playback / channel switching

- AndroidX Media3 ExoPlayer.
- Reuse player when moving between EPG and mini-player; avoid duplicate decoder/player instances.
- Separate channel metadata from playback URL resolution.
- Keep controls minimal and TV-readable: play/pause, live, tracks, aspect ratio, audio/subtitles, info, favorites.
- Handle network reconnect, retry/backoff, stream errors, unsupported codecs, audio-only streams, and provider-specific headers.
- Clearly distinguish provider/network errors from app errors; don't blame buffering generically.
- Channel switching should cancel stale requests and avoid racing player state.
- Keep a configurable buffer policy; measure channel-zap and stability rather than claiming universal fastest settings.
- Timeshift/catch-up/DVR only when provider and stream format actually support it; never promise unsupported recording.

## 6. EPG and live mini-player

- Timeline/grid guide with channel rail, logos, program duration cards, current-time marker, Jump to Now, date/time navigation.
- Now/Next and channel schedule views as complementary modes.
- Search, categories, favorites, density, hide channels without guide data.
- Program details with accurate timezone and DST handling.
- Cached/stale guide indicator, refresh progress, retry/error state.
- Mini-player stays live in a corner while browsing; one shared Media3 playback instance/surface, top-right default, remote-focus navigable, mobile draggable, expand to full-screen.
- If device decoding/rendering is constrained, offer mini-player disable/fallback without crashing.
- Sports-friendly guide: favorite teams/channels, event/program highlighting, quick jump to live sports channels (where metadata permits), not fake event metadata.

## 7. TV-first UX and mobile UX

- Android TV: D-pad focus is always visible, predictable, no focus traps; large text and cards; actions reachable in few clicks; Back behavior consistent.
- Mobile: touch-first compact layout, gestures only where discoverable, mobile-specific navigation.
- Main navigation: Home, Live TV, Guide, Sports, Favorites, Search, Settings.
- Home: Continue Watching, last channel, favorites, recents, recently added categories.
- Empty/error/loading states must include recovery action.
- Accessibility: contrast, scalable text, TalkBack labels, reduced motion option.
- Eagle loading animation: tasteful looping wing motion; respect reduced motion and device performance. Progress must reflect real import stages, not fabricated percentage/time.

## 8. Security and privacy

- Android Keystore-backed encryption for provider secrets; never store plaintext credentials in preferences/database.
- Redact URLs containing usernames/passwords/tokens.
- HTTPS-first pairing backend; validate certificate and avoid custom insecure trust managers.
- Pairing code expiry, single-use grants, rate limits, anti-enumeration, clear device confirmation.
- Local backups exclude secrets by default or require explicit protected export.
- No analytics containing channel names, provider URLs, credentials, or viewing history.
- Explain permissions and collect minimum necessary data.
- Provide remove provider, clear catalog, revoke devices, and delete app data pathways.

## 9. Product priorities

### P0 — MVP/core
1. Xtream and M3U setup (TV manual + Freedom Link phone QR).
2. Fast Live TV list and reliable playback.
3. Favorites, recents, categories, search.
4. EPG timeline and now/next.
5. Large playlist incremental import, progress, cancel/retry.
6. TV remote navigation and mobile layout.
7. Credential encryption and privacy.
8. Eagle branding and import/loading animation tied to actual work.

### P1 — Differentiators
- Phone companion: pair, send credentials, manage favorites, remote text entry, channel launch.
- EPG live mini-player.
- Sports guide filters/event highlighting.
- Provider profiles, backup/restore (secure).
- Reminders/catch-up only where supported.

### P2 — Advanced (after reliability)
- Multiview if device decoding allows.
- DVR/timeshift with verified stream/provider support.
- Advanced customization, parental controls, external player options.

## 10. Quality gates before release

- Unit tests: playlist parsers, URL normalization, stable channel IDs, EPG timezone/DST, pairing session state.
- Integration tests: Room import transactions, player lifecycle, retries, credential encryption.
- UI tests: TV D-pad traversal, focus restore, mobile pairing, accessibility, guide mini-player.
- Security tests: expired/replayed pairing code, brute force/rate limit, wrong TV approval, credential redaction, session revocation.
- Performance tests: 58k+ playlist, search, scroll, EPG, startup, channel switching on low-end hardware.
- Release checklist: crash-free sessions, no known critical security issues, clean logs, privacy policy, user-facing provider compatibility caveats.

## 11. Research references

- Android TV setup/phone-assisted setup: https://support.google.com/androidtv/answer/6121424
- Google TV QR setup: https://support.google.com/googletv/answer/10050221
- Android TV phone remote/code pairing: https://support.google.com/androidtv/answer/6122465
- OAuth 2.0 Device Authorization Grant, RFC 8628: https://www.rfc-editor.org/rfc/rfc8628
- Cross-device security best practice, RFC 10027: https://www.rfc-editor.org/rfc/rfc10027
- Device-flow overview: https://oauth.net/2/device-flow/
- Xtream-compatible API reference (implementation-specific): https://xui.live/docs/api/xtream/
- Android TV app design / remote navigation: https://developer.android.com/design/ui/tv
- Media3: https://developer.android.com/media/media3
- Room: https://developer.android.com/training/data-storage/room
- Paging: https://developer.android.com/topic/libraries/architecture/paging/v3-overview

## 12. Decisions to lock

- **Freedom Link** is the phone-to-TV pairing feature name.
- QR + short code + manual TV fallback.
- QR is temporary pairing only; no provider credentials inside QR.
- First mobile setup supports Xtream and M3U.
- TV starts import immediately after approval; progress is real, cancellable.
- App uses red/blue/black and eagle branding.
- This document is a blueprint, not proof that features are implemented.
