# Freedom IPTV — UI & EPG Research / Design Specification

**Decision:** Modern premium interface with **red / blue / black** identity. Optimize TV first (remote/D-pad, couch readability), then design mobile/tablet layouts intentionally rather than shrinking TV UI.

## Research signals

- Android TV guidance stresses visually clear, immersive TV UI and predictable focus navigation. Every visible action must be reachable by D-pad; focus state must be obvious. [Android TV UI design](https://developer.android.com/design/ui/tv)
- Fire TV's 10-foot guidance emphasizes readable-at-distance text, simple layouts, focusable controls and remote navigation. [Amazon Fire TV UX guidelines](https://developer.amazon.com/docs/fire-tv/design-and-user-experience-guidelines.html)
- Recent TV UX guidance reiterates simplicity, generous spacing, low cognitive load and remote-first navigation. [Smashing Magazine: Designing for TV](https://www.smashingmagazine.com/2025/09/designing-tv-principles-patterns-practical-guidance/)
- TiviMate is repeatedly used as a reference for EPG timeline/grid, customizable guide, channel logos, program detail and TV-friendly interaction. [TiviMate](https://tivimate.com/) [TiviMate community](https://www.reddit.com/r/TiviMate/)
- Community discussions show EPG quality itself is often a frustration: missing/mismatched guide data, wrong channel mapping and stale schedules. Treat data health as a UI feature, not merely backend plumbing. [EPG source discussion](https://www.reddit.com/r/TiviMate/comments/1e72m7p/better_epg_sources/)
- User discussions also ask for simplified interfaces and choice over guide layout; preserve a polished default and optional customization rather than forcing one dense screen. [Simplified TiviMate UI discussion](https://www.reddit.com/r/TiviMate/comments/j2fslk/i_designed_a_simplified_interface_of_the_tivimate/)

Community posts are anecdotal; no single EPG arrangement is universally preferred. Provide a strong default plus 2-3 useful view options.

## Visual identity

- **Canvas:** near-black/charcoal (#080B10 / #111722), not pure black everywhere; reduce eye strain and create depth.
- **Freedom red:** #F04452 — live indicators, selected action accent, alerts, recording dot.
- **Freedom blue:** #2878FF — focus/selection, active navigation, progress and interactive links.
- **Neutral surfaces:** #18212D / #202B3A; borders #2B3747; primary text #F4F7FB; secondary #A8B4C4.
- Use red and blue as purposeful accents—not large competing blocks. Keep contrast accessible; never use color alone to communicate state.
- Motion: 120–200 ms focus/selection transitions; no distracting animated gradients or intro splash delays.
- TV: generous padding, strong focus outline/glow, large hit/focus targets. Phone: touch targets and compact bottom navigation.

## EPG: what people want

### Default guide — familiar timeline grid
- Channel rail at left: logo + short name/number, favorite state; readable and consistent.
- Timeline across the right: program cards proportional to duration, current-time vertical marker, clear now/next labels.
- Visible time scale and **Jump to Now** action.
- Current program card visibly highlighted; past programs dimmed; future programs normal.
- Selecting a program opens a concise details panel: title, channel, start/end, progress, synopsis, available catch-up/record action.
- Keep selected channel/program in view during navigation; avoid focus jumping or losing scroll position.
- Preview player optional and user-configurable (off by default on low-memory devices).

### Alternative guide views (optional)
1. **Timeline / Grid** — default, best for browsing across channels and time.
2. **Now & Next list** — quick scan, simpler/less dense, great for smaller screens.
3. **Channel-focused schedule** — one channel's day, with program cards and details.

Don't make users choose a view during initial setup. Put it in a Guide menu.

### EPG controls users should have
- Jump to Now; date picker; time +/-; channel +/-; category filter.
- Search program titles; search channel names.
- Favorites-only / selected groups toggle.
- Zoom density (e.g. 30/60/120-minute scale) and channel row height.
- Hide channels without guide data; optional show them at bottom.
- Program reminder and record action only when supported.
- Clear refresh timestamp and source health.
- EPG assignment override per channel and source priority; allow manual remapping.
- Timezone preference (device default plus explicit override); DST-safe display.

### EPG states (make failure understandable)
- Loading: keep cached guide visible; small refresh indicator.
- No data: "No guide data for this channel" with remap/source hint.
- Stale: retain old data and label last update; never silently blank everything.
- Refresh error: human-readable reason, retry action, diagnostics.
- Catch-up/recording unsupported: don't show misleading enabled controls.
- Program data mismatch: channel mapping tool, not a generic error.

## App navigation

TV sidebar / top-level destinations:
- **Home**
- **Live TV**
- **Guide**
- **Sports**
- **Favorites**
- **Search**
- **Settings**

Home should prioritize actual watching, not promotional cards:
1. Continue / Last channel
2. Favorites
3. Recently watched
4. Live categories
5. Sports favorites/events (only when configured)

## Player UI

- Fullscreen video with controls that fade but remain accessible via remote.
- Channel up/down and previous channel; channel number/name + now/next in compact banner.
- One clear Guide action; audio/subtitle tracks; aspect ratio; diagnostics.
- Red live dot; blue focused controls; restrained overlays.
- Catch-up/record buttons appear only if source/channel supports them.
- Optional info overlay; no permanent oversized logo/watermark.

## Sports experience

- User-managed leagues/teams/channels favorites (not scraping streams).
- Sports category and "on now / starting soon" view using available EPG.
- Optional reminders.
- If scores are added, use licensed/authorized data and keep them optional.
- Multiview as a later feature; expose hardware/source limitations clearly.

## Implementation acceptance checklist

- [ ] D-pad can reach every visible action; focus never disappears.
- [ ] Focus state legible from couch and distinct from playing/recording state.
- [ ] TV typography and logos readable at 10 feet; mobile layout separately designed.
- [ ] Timeline scrolls smoothly with large channel lists; no main-thread EPG parsing.
- [ ] Jump to Now is one action; selected channel stays stable.
- [ ] Cached guide remains visible while refresh runs or fails.
- [ ] EPG timezone/DST tests and mapping correction UI.
- [ ] No missing-logo blank spaces; fallbacks and lazy loading.
- [ ] Color contrast/accessibility; state not conveyed only by red/blue.
- [ ] Remote, touch, keyboard and back-button behavior tested.

## Reference links

- [Android TV design: https://developer.android.com/design/ui/tv
- [Fire TV design: https://developer.amazon.com/docs/fire-tv/design-and-user-experience-guidelines.html
- TV design principles: https://www.smashingmagazine.com/2025/09/designing-tv-principles-patterns-practical-guidance/
- TiviMate: https://tivimate.com/
- TiviMate community: https://www.reddit.com/r/TiviMate/
- EPG source reliability discussion: https://www.reddit.com/r/TiviMate/comments/1e72m7p/better_epg_sources/
- Simplified guide/UI discussion: https://www.reddit.com/r/TiviMate/comments/j2fslk/i_designed_a_simplified_interface_of_the_tivimate/
