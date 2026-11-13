# Sports Postseason Coverage Audit

Updated: 2026-11-13 (bracket ingestion implemented; live payload validation pending)

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

| Competition | Current schedule source | Tournament limitation |
|---|---|---|
| FBS | ESPN + CFBD | CFP/bowls depend on ESPN season/event metadata; CFBD seasonType=both |
| FCS | ESPN | Bracket rounds depend on ESPN event metadata |
| Men's basketball D1 | ESPN + NCAA scoreboard + NCAA bracket | ESPN labels plus bracket matchups/rounds; payload validation pending |
| Men's basketball D2/D3 | NCAA scoreboard + NCAA bracket | Bracket matchups/rounds; payload validation pending |
| Women's basketball D1 | ESPN + NCAA scoreboard + NCAA bracket | ESPN labels plus bracket matchups/rounds; payload validation pending |
| Women's basketball D2/D3 | NCAA scoreboard + NCAA bracket | Bracket matchups/rounds; payload validation pending |
| Women's volleyball | ESPN + NCAA scoreboard + NCAA bracket | Bracket matchups/rounds; payload validation pending |
| Baseball / CWS | ESPN + NCAA scoreboard + NCAA bracket | Bracket matchups/rounds; payload validation pending |
| Women's gymnastics | ESPN + NCAA scoreboard + NCAA bracket | Bracket route wired; payload validation pending |

## Important implementation facts

- The NCAA HenryGD adapter requests dated scoreboard endpoints and now also queries `/brackets/{sport}/{division}/{year}` for supported tournament sports (including women's gymnastics).
- The HenryGD NCAA API documents a dedicated `/brackets/{sport}/{division}/{year}` endpoint with tournament bracket/live-score data. Bracket parsing is implemented recursively for common team/date/round fields; representative upstream payload validation and ID deduplication still need attention.
- ESPN labels postseason games using season type and extracts round hints from event names/competition notes when those strings exist. Generic postseason labels are retained if a specific round is absent.
- A green CI build confirms compilation/tests, not upstream feed completeness or live postseason availability.

## Next engineering step

Add a bracket-backed NCAA provider for tournament competitions, normalize bracket rounds into `Game.postseasonRound`, merge/deduplicate with scoreboard results, and validate representative D1 basketball, women's volleyball, baseball/CWS, and women's gymnastics bracket payloads before claiming complete postseason coverage.
