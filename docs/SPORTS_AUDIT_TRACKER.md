# Freedom IPTV Sports Audit Tracker (single source of truth)

Updated: 2026-11-14 (hardcoded event-date scan completed)

## Completed audit-management work

- [x] Reviewed `.github/workflows/android-build.yml` and all three sports audit notes for duplicated audit ownership.
- [x] Classified CI steps as distinct contracts (CFBD division/live window, NCAA bracket positions, baseball bracket-to-scoreboard join, RTN feed, ESPN schema, Android compilation); no duplicate sport-completion rows remain.
- [x] Created this canonical tracker and linked the postseason, schedule, and combat documents here. Future findings are recorded against the existing row—not opened as another audit.


This is the **only checklist for audit completion**. Other sports documents are implementation notes/source evidence, not separate audit queues. A green Android CI run proves automated checks passed; it does not prove runtime UI or complete upstream schedules.

## Scope (one row per audit unit)

| Audit unit | Primary evidence/check (run once per unit) | Status | Remaining acceptance |
|---|---|---|---|
| Pro leagues (NFL, NBA, WNBA, MLB, NHL, MLS, EPL, UCL) | ESPN schema smoke; league supplements reviewed in provider code | **Checked:** Nov 13 ESPN-backed slate reference lists NBA 9 games + NHL 2; NFL Nov 12 WAS–NYG and Nov 15 full Week 10 slate cross-checked against official NFL schedule. MLB/WNBA off-season; MLS/EPL/UCL have no fixtures in this sampled window (international break/competition calendar). | App parity, postseason label/date/time UI and league-by-league feed completeness remain open |
| FBS football | ESPN + CFBD division/week smoke; CFP/bowl payload audit | Provider/code reviewed; CFP round normalization implemented | Validate dated CFP/bowl labels against actual event cards/runtime |
| FCS football | CFBD classification/week smoke | **Checked:** CFBD division filter and Nov 13 in-season slate spot-check; ESPN fallback explicitly degraded/mixed | FCS playoff round + dated game/UI verification |
| Men's basketball D1 | ESPN/NCAA + 2025 bracket smoke/round mapping | **Code/UI checked:** NCAA bracket roundName falls back to bracketPositionId mapping; cards render 🏆 round directly above local kickoff; date navigation/status filters preserve scheduled/live/final. Historical bracket shape verified | Need active postseason runtime sample to confirm source labels on device |
| Men's basketball D2 | NCAA + 2025 bracket smoke/round mapping | Historical bracket shape verified | Runtime season date window and tournament card verification |
| Men's basketball D3 | NCAA + 2025 bracket smoke/round mapping | Historical bracket shape verified | Runtime season date window and tournament card verification |
| Women's basketball D1 | ESPN/NCAA + 2025 bracket smoke/round mapping | Historical bracket shape verified | Runtime season date window and tournament card verification |
| Women's basketball D2 | NCAA + 2025 bracket smoke/round mapping | Historical bracket shape verified | Runtime season date window and tournament card verification |
| Women's basketball D3 | NCAA + 2025 bracket smoke/round mapping | Historical bracket shape verified | Runtime season date window and tournament card verification |
| Women's volleyball D1 | ESPN/NCAA + 2025 bracket smoke/round mapping | **Code/UI checked:** NCAA bracket roundName falls back to position mapping; tournament card renders 🏆 round with local kickoff. Historical shape verified | Need actual volleyball postseason runtime sample |
| Baseball D1 / CWS | NCAA bracket + dated-scoreboard cross-check (153 historical games) | **Code/UI checked:** bracket round mapping Regional/Super Regional/CWS/CWS Championship is carried to card's 🏆 label; 153 historical games joined | Need current-season live CWS runtime/card sample |
| Women's gymnastics | Road to Nationals schedule/ranking smoke + ESPN fallback | **Code/UI checked:** RTN schedule is filtered against Div I team ranking rows; kickoff parsing uses America/New_York (DST-aware); meet descriptions map conference/regionals/NCAA round labels into 🏆 schedule cards. 2026 feed inspected | Need live meet card/device validation and confirm edge-case description strings |
| Combat: UFC | ESPN event/bout parser + Combat provider event path | **ESPN schedule-source refactor + sample cross-check (Nov 14):** UFC event dates/times now only ESPN scoreboard date-range query; no hardcoded future events or SportsDB fallback. CI now asserts upcoming ESPN dated events and samples next upcoming Fightcenter card. ESPN/UFC schedule cross-check: UFC 334 Nov 14 (Early Prelims 5pm, Prelims 7pm, Main 9pm ET); Fight Night Nov 21 (Prelims 10am, Main 1pm); Dec 5 Fight Night (10am/1pm); UFC 335 Dec 12 (5pm/7pm/9pm). Fightcenter bouts render segment, weight class, matchup, Main Event marker; CI now checks Main Card final-bout position. Removed manually dated combat specials and weekly TV placeholders across WWE/AEW/TNA; promotions now appear only when sourced from actual event-day listings (ESPN for UFC). No recurring/hardcoded calendar entries remain | CI and ESPN live event/card validation; verify actual device UI; confirm third-party feed completeness for wrestling |
| Combat: Boxing | Combat provider / SportsDB event path | **Checked:** Nov 14 Viddal Riley–Seydi Coupe and Nov 20 Lauren Price–Michaela Kotaskova found on WBC/BBC calendars; SportsDB Fighting feed coverage still unverified | Validate app events against date samples; no comprehensive boxing calendar source |
| Combat: WWE | WWE official events calendar + TheSportsDB fallback | Official dated-calendar parser integrated in `a30d840`; date-range filtering and poster fallback retained. | Verify parsed event list against live calendar/device UI; source may include house shows/region-specific listings |
| Combat: AEW | AEW official events calendar + TheSportsDB fallback | Official date-heading/title/time parser integrated in `a30d840`; date filtering and poster fallback retained. | Verify combined/shifted broadcasts and PPVs against live page/device UI |
| Combat: TNA | TNA official events calendar + TheSportsDB fallback | Official dated-list parser integrated in `a30d840`; recurring weekly placeholders removed; poster fallback retained. | Verify live parsed listing, special-event dedupe and time zone/device display |

| Hardcoded event dates | Repo-wide sports-provider source scan + CI guard | **Checked Nov 14:** no fixed future event dates found in provider schedule code; only legitimate season-year arithmetic, date-window parsing and postseason round mappings remain. WWE/AEW/TNA weekly placeholder rows removed; UFC uses ESPN date-range schedule. CI now guards combat date literals and weekly placeholder regressions. | Reopen only if a hardcoded date/calendar regression is found |\n\n## Completed sport-audit evidence (do not repeat)

- [x] **FBS/FCS source separation:** repository prefers CFBD classification-specific FCS results; ESPN fallback is only used if CFBD yields no games and may mix divisions. CI validates FBS/FCS classification; Nov 13 spot-check confirmed both divisions had games that week. Reopen only for playoffs or a concrete regression.
- [x] **FBS CFP labels:** CFBD playoff round/roundName/bowlName normalization implemented and source payload reviewed. Reopen only for a dated card/runtime mismatch.
- [x] **NCAA bracket source shape:** 2025 endpoints and position groups checked for six basketball divisions, women's volleyball D1, and baseball D1. Historical endpoint audit is complete; runtime display remains open in the scope table.
- [x] **Baseball historical join:** 153 2025 bracket game IDs cross-confirmed against dated scoreboards; completed historical integration evidence, not a recurring audit.
- [x] **Women's gymnastics source:** RTN 2026 schedule/ranking endpoint smoke and inspection passed; runtime D1/timezone/round display remains open.
- [x] **Hardcoded combat calendar removal:** Removed fixed-date WWE/AEW/TNA specials and weekly broadcast placeholders; combat event list now relies on date-based event feed sources, with UFC exclusively ESPN. These manually entered calendar rows could drift or duplicate actual schedules.\n- [x] **UFC segment source check:** official UFC/Paramount event schedule confirms UFC 334 early prelim/prelim/main start times; ESPN competition records have bout data but no dependable segment classification. Event-level segment entries are appropriate; bout-level labels remain unsupported.
- [x] **Combat representative calendar check:** checked official WWE, AEW, TNA and independent boxing schedules for Nov 9–15 sample programming/cards; weekly TV placeholders and official specials are distinct from third-party feed completeness. Keep actual-source coverage and app runtime verification open.
- [x] **Pro-league dated slate sample:** Nov 13 NBA (9) and NHL (2) counts cross-checked against ESPN-derived public slate; NFL Thursday Nov 12 result and official Nov 15 Week 10 schedule checked. MLB/WNBA seasonal emptiness and soccer fixture-free window noted as expected, not provider failure. App/runtime parity remains outstanding.

## CI checks: intentionally distinct (not duplicate sport audits)

1. **CFBD live window smoke** — authenticated calendar + FBS/FCS classification and upcoming game IDs.
2. **NCAA bracket smoke** — historical bracket payloads and bracket-position groups/round mapping inputs (basketball D1-D3, women's volleyball D1, baseball D1).
3. **Baseball dated scoreboard cross-check** — joins 2025 bracket IDs to scoreboards across bracket dates; not a second bracket parser audit.
4. **Road to Nationals smoke** — women's gymnastics schedule, postseason strings, and ranking endpoint availability.
5. **ESPN daily schema smoke** — endpoint availability and events shape only; zero events is valid. It does not validate schedules, postseason labels, or UI.
6. **Android assembleDebug** — compilation only.

These checks may touch the same sport because they test separate contracts. Do not create a new audit task just because a sport appears in more than one check.

## Completion rule

Mark a row **Complete** only after source data, app mapping/UI, and a representative in-season/postseason date (where applicable) are checked. Offseason empty results are expected and must not be treated as failures. Do not add follow-up commits for documentation-only status changes; batch meaningful fixes and verification.


## Channel-to-game matching optimization (Nov 14)

- Existing `ChannelMatcher.match` was network/alias-centric and recomputed normalization for each channel on every invocation; it wasn't referenced by the app's current screens.
- Added `GameIndex`: build once after Xtream catalog refresh; normalize channel names and token postings once, then match each game against only relevant team-token candidates plus network fallback.
- Ranking: matchup-style/both-team channel labels > both team names > confirmed EPG containing both teams within ±180 minutes > single-team/partial > broadcast network fallback. EPG time-only is never marked confirmed. Scores/reasons are explainable and bounded top-K.
- Current work is a reusable matching engine; wiring the index into the sports event UI and measuring p50/p95 against real Xtream channel catalogs remains the next integration/performance acceptance.
