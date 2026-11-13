# Freedom IPTV Sports Audit Tracker (single source of truth)

Updated: 2026-11-13 (combat pass)

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
| Men's basketball D1 | ESPN/NCAA + 2025 bracket smoke/round mapping | Historical bracket shape verified | Runtime season date window and tournament card verification |
| Men's basketball D2 | NCAA + 2025 bracket smoke/round mapping | Historical bracket shape verified | Runtime season date window and tournament card verification |
| Men's basketball D3 | NCAA + 2025 bracket smoke/round mapping | Historical bracket shape verified | Runtime season date window and tournament card verification |
| Women's basketball D1 | ESPN/NCAA + 2025 bracket smoke/round mapping | Historical bracket shape verified | Runtime season date window and tournament card verification |
| Women's basketball D2 | NCAA + 2025 bracket smoke/round mapping | Historical bracket shape verified | Runtime season date window and tournament card verification |
| Women's basketball D3 | NCAA + 2025 bracket smoke/round mapping | Historical bracket shape verified | Runtime season date window and tournament card verification |
| Women's volleyball D1 | ESPN/NCAA + 2025 bracket smoke/round mapping | Historical bracket shape verified | Runtime regular season + postseason display verification |
| Baseball D1 / CWS | NCAA bracket + dated-scoreboard cross-check (153 historical games) | Historical integration check passed | Current-season runtime and CWS display verification |
| Women's gymnastics | Road to Nationals schedule/ranking smoke + ESPN fallback | RTN endpoint and 2026 schedule inspected | Runtime D1 filtering, time zone, regional/NCAA labels |
| Combat: UFC | ESPN event/bout parser + Combat provider event path | Event listing and UFC card source reviewed; bout segment gaps known | Confirm upcoming/recent event & prelim coverage; reliable bout-level segment labels |
| Combat: Boxing | Combat provider / SportsDB event path | **Checked:** Nov 14 Viddal Riley–Seydi Coupe and Nov 20 Lauren Price–Michaela Kotaskova found on WBC/BBC calendars; SportsDB Fighting feed coverage still unverified | Validate app events against date samples; no comprehensive boxing calendar source |
| Combat: WWE | SportsDB + recurring TV supplements | **Checked:** Nov 9 Raw, Nov 13 SmackDown, weekly Tuesday NXT cadence, and Nov 28 Survivor Series confirmed; provider slots are recurring placeholders, not event-feed completeness | Runtime date/title display + source event dedupe |
| Combat: AEW | SportsDB + recurring TV supplements | **Checked:** Nov 11 Dynamite/Collision special and Nov 14 Full Gear verified against AEW schedule; weekly slots may misstate special combined/shifted broadcasts | Runtime schedule accuracy and PPV dedupe |
| Combat: TNA | SportsDB + recurring TV supplements | **Checked:** Nov 12 Impact and Nov 15 Destination X confirmed; weekly Thursday placeholder matches current broadcast cadence | Runtime display + special-event dedupe/source timing |

## Completed sport-audit evidence (do not repeat)

- [x] **FBS/FCS source separation:** repository prefers CFBD classification-specific FCS results; ESPN fallback is only used if CFBD yields no games and may mix divisions. CI validates FBS/FCS classification; Nov 13 spot-check confirmed both divisions had games that week. Reopen only for playoffs or a concrete regression.
- [x] **FBS CFP labels:** CFBD playoff round/roundName/bowlName normalization implemented and source payload reviewed. Reopen only for a dated card/runtime mismatch.
- [x] **NCAA bracket source shape:** 2025 endpoints and position groups checked for six basketball divisions, women's volleyball D1, and baseball D1. Historical endpoint audit is complete; runtime display remains open in the scope table.
- [x] **Baseball historical join:** 153 2025 bracket game IDs cross-confirmed against dated scoreboards; completed historical integration evidence, not a recurring audit.
- [x] **Women's gymnastics source:** RTN 2026 schedule/ranking endpoint smoke and inspection passed; runtime D1/timezone/round display remains open.
- [x] **UFC segment source check:** official UFC/Paramount event schedule confirms UFC 334 early prelim/prelim/main start times; ESPN competition records have bout data but no dependable segment classification. Event-level segment entries are appropriate; bout-level labels remain unsupported.
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
