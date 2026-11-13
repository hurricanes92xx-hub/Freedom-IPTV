# Sports Postseason Coverage Audit

Updated: 2026-11-13 (NCAA bracket mapping and Road to Nationals source inspection)

This is a provider/code audit, not a claim that every future event has been live-tested.

## Combat / wrestling schedule audit\n\n| Promotion | Current implementation | Audit result / limitation |\n|---|---|---|\n| WWE | TheSportsDB Fighting daily events; filter checks WWE / World Wrestling Entertainment in league, sport, alternate event, and event metadata | Promotion filter and UI wiring confirmed in source. Coverage of weekly Raw, SmackDown, NXT, specials, and PPVs is **not live-verified**; missing source records will not be recovered by this filter. |\n| AEW | Same Fighting daily feed; checks AEW / All Elite Wrestling metadata | Filter/UI confirmed. Dynamite, Collision, special events, and PPVs are not independently guaranteed; no date-by-date feed validation completed. |\n| TNA Wrestling | Same Fighting daily feed; checks TNA / Impact Wrestling / Total Nonstop Action metadata | Filter/UI confirmed. Impact weekly shows and PPVs are not independently guaranteed; no date-by-date feed validation completed. |\n\n**Audit conclusion:** These three promotions were previously wired into the Combat selector, but that is not proof of reliable schedule coverage. The current provider makes one `eventsday.php` request per calendar date with `s=Fighting`, then filters returned event metadata. It does not query promotion-specific league IDs or supplement known weekly TV schedules. Treat WWE/AEW/TNA as **code-reviewed, feed completeness unverified** until actual upcoming and recent dates are checked against official schedules and source payloads. TheSportsDB documents the date endpoint and optional sport/league filters; see <https://www.thesportsdb.com/docs/api> and <https://www.thesportsdb.com/docs_api_guide>.\n\n## Pro / major leagues

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

## Live schedule coverage spot-check (2026-11-13)

- Compared the upcoming Nov 13–14 FBS/FCS slate against NCAA.com and ESPN Press Room schedule listings. Both divisions have games during the current week; an empty date must not be described as a league offseason.
- **Known data-model limitation:** ESPN adapter maps both `ncaaf-fbs` and `ncaaf-fcs` to the same `football/college-football` scoreboard. NCAA alternate adapter intentionally returns empty for both football divisions, while CFBD supplement currently only populates FBS. Consequently, FCS selection can show the shared ESPN feed (potential FBS leakage) and has no independent FCS-only feed guarantee. This is an upstream/provider separation issue, not an offseason condition.
- Follow-up: add division-aware FCS source/filtering and validate FBS/FCS event counts against official slates before marking coverage complete. Source spot-check: NCAA week 11 schedule (Nov 12, 2026) and ESPN Week 11 press release (Nov 9, 2026).
