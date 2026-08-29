# Current Priority

Map rotation is implemented and has been through a correctness-hardening pass:
attempt tokens, compare-and-set lifecycle persistence, a guarded winning
transaction, roster-authoritative eligibility, two-stage map admission, durable
world cleanup, and restart reconciliation for every phase. The Maven suite
covers the coordinator lifecycle, restart hydration, eligibility, strict
manifest parsing, and the award transaction.

It is **not yet accepted**: no map is enabled and no live multiplayer run has
been done. Automated tests do not substitute for that run.

Before enabling maps publicly, work the release gates in
`memory/2026-08-28-map-rotation-design.md` and run
`docs/STAGE_4_4_5_PLAYTEST_GUIDE.md` with four fighters, one spectator, and at
least two maps that pass strict validation.

The live `plugins/SiegePlugin/config.yml` has been migrated
(`scoring.winning-score` and `rotation.preparation-timeout-seconds` added;
`activity-cycle`, `cleanup.map-reset-interval-hours`, and `arena-reset`
removed) and `maps.yml` installed. Startup fails loudly if a retired key
reappears. Old snapshot files under `plugins/SiegePlugin/snapshot/` are
intentionally left on disk.

Keep the existing clean-copy loader and potion-storage behaviour; do not rewrite
them. Map/kit voting and client-side border walls remain deferred.
