# Sports Postseason Coverage Audit

Updated: 2026-11-13

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
| Men's basketball D1 | ESPN + NCAA scoreboard | ESPN postseason labels; NCAA scoreboard date route only |
| Men's basketball D2/D3 | NCAA scoreboard | Daily scoreboard; no dedicated bracket ingestion yet |
| Women's basketball D1 | ESPN + NCAA scoreboard | ESPN postseason labels; NCAA scoreboard date route only |
| Women's basketball D2/D3 | NCAA scoreboard | Daily scoreboard; no dedicated bracket ingestion yet |
| Women's volleyball | ESPN + NCAA scoreboard | Tournament matches depend on daily scoreboard/event feed |
| Baseball / CWS | ESPN + NCAA scoreboard | Postseason depends on event feed; no bracket ingestion yet |
| Women's gymnastics | ESPN + NCAA scoreboard | Championship meets depend on scoreboard/event feed |

## Important implementation facts

- The NCAA HenryGD adapter currently requests dated scoreboard endpoints for non-football competitions. It does **not** call the NCAA bracket endpoint.
- The HenryGD NCAA API documents a dedicated `/brackets/{sport}/{division}/{year}` endpoint with tournament bracket/live-score data. Integrating that endpoint is the next coverage improvement; its response needs a dedicated parser and deduplication against scoreboard events.
- ESPN labels postseason games using season type and extracts round hints from event names/competition notes when those strings exist. Generic postseason labels are retained if a specific round is absent.
- A green CI build confirms compilation/tests, not upstream feed completeness or live postseason availability.

## Next engineering step

Add a bracket-backed NCAA provider for tournament competitions, normalize bracket rounds into `Game.postseasonRound`, merge/deduplicate with scoreboard results, and validate representative D1 basketball, women's volleyball, baseball/CWS, and women's gymnastics bracket payloads before claiming complete postseason coverage.
