# Freedom IPTV Sports Audit Tracker (single source of truth)

Updated: 2026-11-13

## Completed audit-management work

- [x] Reviewed `.github/workflows/android-build.yml` and all three sports audit notes for duplicated audit ownership.
- [x] Classified CI steps as distinct contracts (CFBD division/live window, NCAA bracket positions, baseball bracket-to-scoreboard join, RTN feed, ESPN schema, Android compilation); no duplicate sport-completion rows remain.
- [x] Created this canonical tracker and linked the postseason, schedule, and combat documents here. Future findings are recorded against the existing row—not opened as another audit.


This is the **only checklist for audit completion**. Other sports documents are implementation notes/source evidence, not separate audit queues. A green Android CI run proves automated checks passed; it does not prove runtime UI or complete upstream schedules.

## Scope (one row per audit unit)

| Audit unit | Primary evidence/check (run once per unit) | Status | Remaining acceptance |
|---|---|---|---|
| Pro leagues (NFL, NBA, WNBA, MLB, NHL, MLS, EPL, UCL) | ESPN schema smoke; league supplements reviewed in provider code | Feed schema checked; event completeness/runtime open | In-season sample, league-specific postseason labels, UI date/state checks |
| FBS football | ESPN + CFBD division/week smoke; CFP/bowl payload audit | Provider/code reviewed; CFP round normalization implemented | Validate dated CFP/bowl labels against actual event cards/runtime |
| FCS football | CFBD classification/week smoke | Division filter implemented; live slate spot-check done | FCS playoff round + dated game/UI verification |
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
| Combat: Boxing | Combat provider / SportsDB event path | Code-reviewed; feed completeness unverified | Check representative actual dates and event cards |
| Combat: WWE | SportsDB + recurring TV supplements | Selector/filter/UI wired; source completeness unverified | Verify Raw/SmackDown/NXT + PPV sample dates |
| Combat: AEW | SportsDB + recurring TV supplements | Selector/filter/UI wired; source completeness unverified | Verify Dynamite/Collision + PPV sample dates |
| Combat: TNA | SportsDB + recurring TV supplements | Selector/filter/UI wired; source completeness unverified | Verify Impact + PPV sample dates |

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
