# Sports Postseason Coverage Audit

Updated: 2026-11-13 (NCAA bracket mapping and Road to Nationals source inspection)

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
| Women's gymnastics | Road to Nationals `/api/women/schedule/{year}/1` + ESPN | RTN schedule adapter supplements ESPN; D1 meet filter uses RTN rankings (`/currentweek/{year}` + `/results/{year}/{week}/0/5`). Inspected The Five That Score's current RTN-derived 2026 schedule snapshot (342 meets, Jan 2–Apr 18); it includes Regional Round 1/2/Final, NCAA Championships Semifinal I/II, and NCAA Championships Finals. Provider round parsing was corrected to map those exact championship strings to NCAA Semifinal/Championship (rather than generic Conference Championship). App runtime feed validation remains pending. |

## Implementation and validation notes

- HenryGD NCAA bracket endpoint: `/brackets/{sport}/{division}/{year}`; parser consumes `championships[].games[]` directly, including contest IDs, bracket position IDs, epoch start times, teams, scores, and status.
- Validated 2025 upstream payloads for D1 men's/women's basketball, D2/D3 men's/women's basketball, women's volleyball, and D1 baseball. These checks establish endpoint shape and round-position mapping for those seasons—not future availability or completeness.
- NCAA women's gymnastics is not bracket-backed. RTN is now integrated as a dedicated schedule source using its documented public API. The provider filters D1 meets through RTN rankings, normalizes dates/times/team matchups into app Game objects, and tags postseason rounds from meet descriptions. Verify actual returned events/round names against RTN in runtime before claiming full coverage. Sources: https://www.ncaa.com/_flysystem/public-s3/images/2025/04/20/Bracket_25NC_WGY_Final.pdf and https://espnpressroom.com/press-release/ncaa-gymnastics-reaches-new-heights-in-2025-with-nearly-100-hours-of-live-action/
- Bracket results are merged with dated scoreboard results and deduplicated by game ID.
- ESPN labels postseason games using season type and extracts round hints from event names/competition notes when those strings exist. Generic postseason labels remain when specific rounds are unavailable.
- A green CI build confirms compilation/tests, not upstream feed completeness or live postseason availability.

## Remaining postseason work

1. Validate 2025 NCAA FCS playoff and FBS CFP/bowl event labeling against real ESPN event payloads.
2. Verify RTN gymnastics provider at runtime: confirm D1 meet counts, regional semifinal/final and NCAA semifinal/championship labels, and time zone conversion.\n3. Validate NCAA baseball/CWS schedule windows against the dated scoreboard feed (bracket payloads alone are not a complete daily schedule).
4. Keep monitoring provider changes and confirm upcoming-season data when it becomes available.
