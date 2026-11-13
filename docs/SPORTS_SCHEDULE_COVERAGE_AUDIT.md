# Sports Schedule Coverage & Offseason Audit

Audit date: 2026-11-13
Repository: Freedom IPTV

## Competition/provider matrix

| Competition | Primary schedule | Supplement/fallback | Coverage notes |
|---|---|---|---|
| NFL, NBA, WNBA, MLB, NHL | ESPN scoreboard | MLB Stats API / NHL Web API for MLB/NHL | ESPN date scoreboards; league APIs supplement. |
| FBS | ESPN college football | CFBD | CFBD supplements schedule data when configured. |
| FCS | CFBD filtered to FCS | ESPN college-football fallback | ESPN fallback can mix FBS/FCS; CFBD is division-clean. |
| Men's basketball D1 | ESPN | NCAA scoreboard + NCAA bracket endpoint | Bracket endpoint supplements dates; round labels inferred from bracket position when needed. |
| Men's basketball D2/D3 | NCAA scoreboard | NCAA bracket endpoint | Separate division routes and bracket routes exist. |
| Women's basketball D1 | ESPN | NCAA scoreboard + bracket endpoint | D1 tournament rounds mapped. |
| Women's basketball D2/D3 | NCAA scoreboard | NCAA bracket endpoint | Separate division routes and bracket routes exist. |
| Women's volleyball D1 | ESPN | NCAA scoreboard + bracket endpoint | Bracket rounds include regional and national stages. |
| NCAA baseball D1 | ESPN | NCAA scoreboard + bracket endpoint | Regional, super-regional, and CWS stage labels inferred. |
| Women's gymnastics | ESPN | Road to Nationals dedicated schedule | Dedicated RTN provider supplements ESPN; retain this competition. |
| EPL/UCL/MLS | ESPN | API-Football when configured | API-Football is key-gated and supplements partial ESPN dates. |
| Combat events | Combat provider | SportsDB where applicable | Events/bouts use event model rather than team game schedule; UFC prelims must remain discoverable. |

## Seasonal behavior and known limits

- Schedule calls are date-window based and return empty for reversed windows; providers gracefully return empty when unsupported or unavailable.
- ESPN feeds are unofficial and not every competition has a dedicated ESPN route. NCAA's alternate provider is the key division-specific source for D2/D3 and bracket coverage.
- Offseason empty schedules are expected; they are not proof of a broken feed. A meaningful audit must test dates inside each sport's season and separately test postseason date windows.
- Provider data may be partial, delayed, or rate-limited. The repository merges/deduplicates supplemental sources and caches schedule results for 90 seconds.
- FCS ESPN fallback is not division-pure. Prefer CFBD and treat mixed fallback results as degraded coverage rather than clean FCS results.
- Combat events are queried through the event pathway, not `schedule()`; do not use empty team-game schedules to judge UFC/boxing/wrestling coverage.
- College brackets are season-dependent. Bracket APIs are queried for adjacent years to handle calendar/season boundaries; individual rounds still depend on source payload fields or inferred bracket positions.
- Road to Nationals is the dedicated women's gymnastics source; ESPN alone is insufficient as a coverage test.

## Follow-up validation checklist

- [ ] During-season sample windows for every catalog competition (including D2/D3).
- [ ] Postseason windows: FBS CFP/bowls, FCS playoffs, D1/D2/D3 basketball, women's volleyball, baseball, gymnastics championships.
- [ ] Verify each competition's UI category is reachable and maps to its canonical competition ID.
- [ ] Verify postseason round labels and date boundaries against authoritative brackets.
- [ ] Check empty offseason states do not show stale cached games or false errors.
- [ ] Verify combat event cards include main card and prelims (UFC especially).
- [ ] Verify ESPN/NCAA/CFBD fallback behavior when a source returns empty or times out.
