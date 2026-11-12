# Freedom IPTV Sports Hub

## Competition scope
Professional: NFL, NBA, MLB, NHL, WNBA, MLS; Premier League and Champions League; UFC/boxing/WWE/AEW; Formula 1/NASCAR; ATP/WTA; PGA/LPGA.

College (only these):
- Football: FBS and FCS
- Men's basketball: D1, D2, D3
- Women's basketball: D1, D2, D3
- Women's volleyball: NCAA
- Baseball: NCAA
- Women's gymnastics: NCAA
No additional college sports.

## Engineering
- Provider-neutral data interfaces, local cache, deterministic channel matching.
- Channel availability states must distinguish schedule, network, lineup match, EPG/user verification, and playable stream.
- NCAA schedule-alt endpoints; pace under published 3 requests/sec/IP limit.
- ESPN feeds supplemental/unofficial; licensed provider can be plugged in later.
- Foreground polling only for live scores; WorkManager handles periodic maintenance.
- Cache logos and use stable Compose list keys.
