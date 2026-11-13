# Freedom IPTV Sports Hub

## Competition scope
Professional: NFL, NBA, MLB, NHL, WNBA, MLS, Premier League, Champions League; UFC, boxing, WWE, AEW, TNA.

College (only these):
- Football: FBS and FCS
- Men's basketball: D1, D2, D3
- Women's basketball: D1, D2, D3
- Women's volleyball: NCAA D1
- Baseball: NCAA D1
- Women's gymnastics: NCAA (Road to Nationals)

Removed from the sports hub: motorsports (Formula 1, NASCAR, IndyCar, MotoGP), tennis, and golf. Do not silently reintroduce these categories.

## Engineering
- Provider-neutral data interfaces, local cache, deterministic channel matching.
- Channel availability states distinguish schedule, network, lineup match, EPG/user verification, and playable stream.
- NCAA schedule-alt endpoints; pace under published 3 requests/sec/IP limit.
- ESPN feeds supplemental/unofficial; licensed provider can be plugged in later.
- Foreground polling only for live scores; WorkManager handles periodic maintenance.
- Cache logos and use stable Compose list keys.
