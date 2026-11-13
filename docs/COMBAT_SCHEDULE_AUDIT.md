> Audit tracking is centralized in [SPORTS_AUDIT_TRACKER.md](SPORTS_AUDIT_TRACKER.md). This file is evidence/details only; don't count its findings as a separate audit queue.

# Combat Schedule Audit (November 2026)

## Current implementation

- `CombatScheduleProvider` queries TheSportsDB's daily Fighting events in bounded batches (four concurrent dates), filters event metadata by promotion, and supplements its results with local recurring television slots and selected event entries.
- UFC event supplements now distinguish **Early Prelims**, **Prelims**, and **Main Card** for UFC 334; the November/December Fight Night fixtures have prelim and main-broadcast entries; UFC 335 has a prelim slot.
- WWE/AEW/TNA television slots are broadcast listings, not venue start times. Countdown/pre-show labels must remain distinct from the main PPV event.
- UFC prelims are schedule rows, not a guarantee that every prelim bout is represented.

## Important limitations / follow-up

1. **No fight-card ingestion yet.** TheSportsDB parser currently maps event ID/title/date/poster/status only. It does not populate `CombatEvent.bouts` or `CombatBout`; consequently individual matchup, weight-class, prelim/main-card bout, and main-event detail are not sourced by this provider.
2. **Do not infer card completeness from an event being present.** A promotion/event schedule and a fight-by-fight card are separate coverage requirements.
3. **Hardcoded future event supplements need official-date verification.** Keep them only while promotion announcements confirm the event/date; use source-backed feeds for dynamic schedules where possible. Avoid treating guessed card names, opponents, or broadcast times as verified.
4. **Time zones:** The local supplements use America/New_York and TheSportsDB event times are currently parsed as UTC. Provider feed time semantics should be validated per event to avoid display shifts.
5. **Offseason and quiet weeks:** no combat event on a date is normal; recurring weekly wrestling broadcasts are the exception.

## Next audit target: ESPN UFC bout-card coverage

- Event-level UFC scoreboard parsing now falls back to ESPN Fightcenter card buckets for individual bouts. `CombatBout.cardSegment` carries Early Prelims / Prelims / Main Card only when Fightcenter explicitly groups the bout; scoreboard-only fallback leaves segment null rather than guessing. Main-event flag is assigned to the last bout in the Main Card bucket (not the last overall scoreboard competition).
- Audit ESPN competition ordering, participant names, and event main-event designation; preserve source order and avoid inventing prelim segment labels.
- Follow up with a bounded bout-card model/UI only when source data reliably distinguishes bouts and broadcast segments.

## Acceptance checks

- UFC events expose separate Early Prelims, Prelims, and Main Card schedule entries whenever the official broadcast has all three.
- UFC Fight Night events expose prelim and main-card entries where announced.
- PPV Countdown/pre-show entries are distinct from PPV main card.
- Event title/date/poster/state can load without fabricated participant or bout data.
- CI now probes a historical/recent ESPN Fightcenter event and validates participant/card-bucket structure; Android compile verifies the new model/parser. Confirm runtime card rendering and compare one full card with UFC's official listing before calling bout-level coverage complete. ESPN scoreboard-only fallback deliberately does not infer segment labels.
- CI checks stable parsing/labeling logic and does not require a live event to exist during an offseason.
