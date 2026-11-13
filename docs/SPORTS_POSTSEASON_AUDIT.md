# Sports Postseason Coverage Audit

Updated: 2026-11-13 (NCAA bracket mapping and gymnastics postseason source audit)

This is a provider/code audit, not a claim that every future event has been live-tested.

## Pro / major leagues

| Competition | Schedule source | Postseason metadata |
|---|---|---|
| NFL | ESPN scoreboard | ESPN season type; event/notes round extraction |
| NBA | ESPN scoreboard | ESPN season type; event/notes round extraction |
| WNBA | ESPN scoreboard | ESPN season type; event/notes round extraction |
| MLB | ESPN + MLB Stats API | MLB seriesDescription |
| NHL | ESPN + NHL web API | NHL gameType=3 and seriesTitle |

## NCAA

| Competition | Current schedule source | Tournament handling / caveat |
|---|---|---|
| FBS | ESPN + CFBD | ESPN postseason season type and expanded event/notes round extraction; CFBD seasonType=both |
| FCS | ESPN | Postseason season type plus expanded event/notes round extraction (specific round when exposed; otherwise generic postseason) |
| Men's basketball D1 | ESPN + NCAA scoreboard + NCAA bracket | Bracket position IDs mapped First Four through Championship |
| Men's basketball D2/D3 | NCAA scoreboard + NCAA bracket | 2025 bracket positions mapped through First Round, Second Round, Regional Semifinal/Final, National Semifinal, Championship |
| Women's basketball D1 | ESPN + NCAA scoreboard + NCAA bracket | Bracket position IDs mapped First Four through Championship |
| Women's basketball D2/D3 | NCAA scoreboard + NCAA bracket | 2025 bracket positions mapped through First Round, Second Round, Regional Semifinal/Final, National Semifinal, Championship |
| Women's volleyball | ESPN + NCAA scoreboard + NCAA bracket | 2025 position IDs mapped First Round through Championship |
| Baseball / CWS | ESPN + NCAA scoreboard + NCAA bracket | Correct HenryGD route is `baseball/d1`; 2025 positions map Regional, Super Regional, College World Series, CWS Championship |
| Women's gymnastics | ESPN scoreboard adapter + NCAA dated scoreboard adapter (configured, but live route validation failed) | **Not verified complete.** No NCAA bracket parser is wired. 2025 NCAA regionals (Apr 2–6) and national semifinals/final (Apr 17/19) are known from official NCAA/ESPN schedules, but direct ESPN scoreboard calls returned HTTP 400 and HenryGD `/scoreboard/gymnastics-women/d1/YYYY/MM/DD` returned HTTP 422. Do not treat empty provider results as proof of no meets. |

## Implementation and validation notes

- HenryGD NCAA bracket endpoint: `/brackets/{sport}/{division}/{year}`; parser consumes `championships[].games[]` directly, including contest IDs, bracket position IDs, epoch start times, teams, scores, and status.
- Validated 2025 upstream payloads for D1 men's/women's basketball, D2/D3 men's/women's basketball, women's volleyball, and D1 baseball. These checks establish endpoint shape and round-position mapping for those seasons—not future availability or completeness.
- NCAA women's gymnastics is intentionally not listed as bracket-backed. The app's configured ESPN/NCAA scoreboard paths are currently unverified: direct 2025 championship-date requests failed (ESPN HTTP 400; HenryGD HTTP 422). Official 2025 championship materials confirm regionals April 2–6 and national semifinals April 17/final April 19; ESPN's published coverage confirms those broadcasts. These sources establish the event calendar, not app fixture ingestion. Sources: https://www.ncaa.com/_flysystem/public-s3/images/2025/04/20/Bracket_25NC_WGY_Final.pdf and https://espnpressroom.com/press-release/ncaa-gymnastics-reaches-new-heights-in-2025-with-nearly-100-hours-of-live-action/
- Bracket results are merged with dated scoreboard results and deduplicated by game ID.
- ESPN labels postseason games using season type and extracts round hints from event names/competition notes when those strings exist. Generic postseason labels remain when specific rounds are unavailable.
- A green CI build confirms compilation/tests, not upstream feed completeness or live postseason availability.

## Remaining postseason work

1. Validate 2025 NCAA FCS playoff and FBS CFP/bowl event labeling against real ESPN event payloads.
2. Fix/replace NCAA women's gymnastics scoreboard ingestion; verify 2026 regionals and championship sessions are surfaced as actual app fixtures (not merely external event calendar data).\n3. Validate NCAA baseball/CWS schedule windows against the dated scoreboard feed (bracket payloads alone are not a complete daily schedule).
4. Keep monitoring provider changes and confirm upcoming-season data when it becomes available.
