# SiegePlugin 1.1 Expansion

## Purpose

This is the SiegePlugin 1.1 expansion brief and implementation record. It separates completed working-tree behavior, release gates, and later ideas inspired by the `refs/Sieges-master` server snapshot.

Sieges is a finite, rotating SiegeGame minigame on Paper 1.20.1. SiegePlugin is a Towny-backed Paper 1.21.11 game. Its current persistent-score model was deliberately retained during Stage 4.4 testing, but that testing-only decision is now superseded: SiegePlugin 1.1 will rotate maps and begin each newly rotated map with reset team scores.

Reuse the player-facing ideas below, but do not copy Sieges' legacy plugin stack wholesale.

> **Current priority:** Map rotation and score rollover are implemented in the working tree. Do not enable the five maps publicly until their manifests/template folders are configured and the live-player acceptance checklist passes with four fighters, one spectator, and at least two maps.

### To do

- Infinite Potion Storages for both sides — Approach A implemented; pending live multiplayer staging verification
- Map implementation — five manifests selected; coordinates/templates still require operator configuration against the commented schema in `maps.yml`
- Map rotation, score rollover, lobby ceremony, MVPs, two-stage map admission, tolerant launch, and durable recovery — implemented and hardened (attempt tokens, compare-and-set lifecycle writes, roster-authoritative eligibility, durable world cleanup); pending live multiplayer acceptance
- Lobby Build
- Global curated kit editing — implemented; replacement choices remain operator-configured
- Client side border walls
- Book-based tutorial upon first join

## Supporting plugins and integrations

The supplied Sieges snapshot uses a broad plugin stack around its custom `SiegeGame` jar:

- **Required by SiegeGame:** NBTAPI, ProtocolLib, LunarClient API, and CombatLogX.
- **Presentation and administration:** TAB, PlaceholderAPI, HolographicDisplays, EssentialsX, LuckPerms, CoreProtect, WorldEdit, spark, ServerRestart, CommandWhitelist, and PremiumVanish configuration.
- **Combat tuning:** CombatPlus, OldCombatMechanics, SplashPotionVelocity, CPSLimiter, DurabilityChanger, and KnockbackMaster configuration.
- **Compatibility:** ViaVersion and ViaBackwards.

### Takeaway for SiegePlugin

Do not adopt this stack as a bundle. SiegePlugin already owns its scoreboards, team display, shop, kits, combat-tag integration, and arena protection. Additional plugins should only be evaluated one at a time for a concrete gap:

- `spark` is useful as a diagnostic/profiling tool for a live server.
- `CoreProtect` is worth considering for staff audit/recovery, independent of game mechanics.
- TAB and PlaceholderAPI could be useful only if a future public-server presentation needs richer tab/header/footer content.
- Lunar Client should remain optional. Any feature depending on it needs a vanilla fallback.

The historical snapshot has legacy versions, `online-mode=false`, BungeeCord enabled, and historical connection configuration. It must not be copied into the SiegePlugin server configuration.

Sources: `refs/Sieges-master/README.md`, `refs/Sieges-master/plugins/`, and `refs/Sieges-master/server.properties`.

## Infinite team potion storages

### Goal

Make a designated, physical double chest feel like a public base supply: a player on the owning team opens it, takes the potions they need, and the chest is full again for the next visit. It must never alter an ordinary chest, allow an opponent to use the supply, or show two players inconsistent contents.

### Selected approach — config-backed physical double chest

Each supply is an explicitly registered double chest. An administrator places one or more identical potion items inside the chest, looks at either half, and registers it with one command for Red or Blue. Registration only succeeds when the inventory contains a single, valid potion item type; it records that exact `ItemStack` (including normal/splash/lingering form, potion effects, custom name, and item metadata) as the supply template.

The plugin then treats that chest pair as a managed supply. It fills all 54 slots with copies of the template whenever an eligible player opens it and again when they close it. Potions are therefore available on demand without an accumulating server-side stockpile.

### Player flow

1. A siege admin prepares a double chest with a sample potion and runs the register command for `red` or `blue` while targeting it.
2. The plugin validates the chest pair and sample, records the storage, fills it with the configured potion, and creates a floating label above its centre, for example `RED TEAM • Strength II`.
3. A player on that team opens it and removes any number of potions. Shift-clicking potions into their inventory is allowed.
4. On close, the chest is restored to 54 copies of the same potion for the next teammate.
5. If another player tries to open the same supply while it is in use, their open is cancelled and only they receive a red chat message: `This potion storage is currently in use.`

### Access and safety rules

- Registration is opt-in only. Unregistered chests retain vanilla behavior and are never inspected, cleared, relabelled, or refilled by this feature.
- A storage must be a real double chest. Single chests, mixed inventories, non-potion items, and mismatched potions are rejected at registration.
- During an active siege, only the storage's team may withdraw. Operators/siege admins may have a deliberate staff bypass; spectators and the opposing team are denied.
- The managed inventory is withdrawal-only: placing, swapping, dragging, hotbar-number swapping, or offhand-swapping items into it is blocked. Hoppers and similar inventory transfers to or from it are also blocked.
- A per-storage `UUID` holder lock is acquired before the first player views the inventory. The server's main event thread makes this atomic; all close, disconnect, death, world-unload, and shutdown paths release it. A conservative failsafe clears a stale lock rather than leaving a base chest unusable.
- Normal players cannot break a registered storage. Unregistering is an explicit admin action that removes the label and managed protections, leaving its current inventory as an ordinary chest.

### Configuration and administration

Use the existing `/siege admin` command family and a dedicated persisted supplies configuration, rather than the match database. The first release needs only:

- `register <red|blue>` — target an eligible double chest containing the sample potion.
- `unregister` — target a managed storage and return it to an ordinary chest.
- `list` — show each storage's team, potion label, and location.
- `info` — target a storage to show its configuration and whether it is occupied.

Store a canonical identity for both chest halves plus the exact serialized sample item, team, label entity identity, and location. The internal location model should be map-aware from the beginning: a map key and template-relative chest coordinates, resolved to the active map world. Until map rotation exists, the current world acts as that key. This prevents a future rotation from silently attaching a supply to the wrong copied world.

Use a native Minecraft `TextDisplay` for the label, tagged so it can be restored on startup and removed on unregistration. This keeps the feature plugin-free and lets the label follow SiegePlugin's team presentation rather than adding a hologram dependency.

### Why this is the first version

It preserves the familiar chest workflow, needs no custom inventory UI, and keeps configuration as fast as placing a sample potion and issuing one admin command. The only state with a lifetime longer than an open chest is its small configuration record; refill inventory contents and locks are deliberately ephemeral.

### Deferred decisions

- Exact permission nodes and the final wording/formatting of the floating label.
- Whether storage interaction is allowed outside an active siege, during setup, or in the lobby.
- Whether future maps share a standard supply layout or define their own layouts. The chosen map-aware storage identity supports either choice.
- A later upgrade could present the supply through a virtual inventory. It is not needed for the initial public-base-chest experience.

### Implementation status and staging sequence

Completed in the working tree:

1. Persisted `potion-storages.yml` registry, sample-potion validation, `/siege admin supply` commands, and tab completion.
2. Team access checks, one-player lock, refill-on-open/close, deposit/drag/hotbar/hopper protection, and lifecycle cleanup for close, quit, death, world change/unload, unregister, and shutdown.
3. Native `TextDisplay` labels, storage-break/explosion protection, and admin list/info/unregister support.

Before public use, exercise it on a staging map with simultaneous team members, disconnects, deaths, hopper attempts, and a map/world transition. The current unit suite covers the pure storage key and lock rules; Paper's item/registry objects require a live server for full chest interaction testing.

Operator commands:

- `/siege admin supply register <red|blue>` — look at a double chest containing only the desired potion sample(s).
- `/siege admin supply unregister` — look at a managed chest to return it to ordinary behavior.
- `/siege admin supply list` and `/siege admin supply info` — inspect configured supplies.

## Map rotation via clean world copies

### Decision: native clean-copy loader

SiegePlugin 1.1 will use a plugin-owned native Bukkit/Paper loader, based on Sieges' `FileMapLoader` lifecycle. Multiverse-Core will not own world loading or rotation. It may remain installed for operator tooling, but it must not become a second match-lifecycle authority.

The first rotation pool is deliberately limited to five supplied Sieges templates, selected at random for initial conversion:

- `al_quds`
- `iron_mountain1`
- `kansas_city_outpost`
- `kazan`
- `murmansk`

Each must pass SiegePlugin validation before it can enter the pool: a clean template folder; Red and Blue team spawns; capture-banner position/radius; arena boundary; lobby handoff; and, if used, registered team potion supplies. The world terrain is reused, but SiegePlugin's kit and capture-banner gameplay replace Sieges' territory/team rules.

### Round experience

When a team reaches the configurable score limit, scoring and capture rewards freeze only after the score write is durable. SiegePlugin announces the winner, takes all participants to the lobby, begins a visible countdown, reveals the selected map, then starts the fresh round at 0–0 on its clean active copy.

A rotation failure does **not** replay the completed match, duplicate its winner, or create a zero-score match. Players remain in the lobby while the controller retries the prepared map or uses a validated fallback; the completed match remains archived exactly once.

### Rotation state machine

`BOOTSTRAPPING → ACTIVE → COMPLETING → INTERMISSION → ACTIVATING → ACTIVE`

Any exhausted preparation path enters `RECOVERY`; an administrator can inspect, validate, and retry it without creating a new match. The durable coordinator records its generation, current/prepared map and world, fallback candidates, queue, roster, and deadline so restart recovery cannot duplicate a winner or silently reopen scoring.

- **COMPLETING:** atomically reject new scoring/capture activity, persist the winning match result, and cancel active capture sessions.
- **INTERMISSION:** immediately show winner/final score, category MVPs, Overall MVP, next-map reveal, and a clickable lobby button. Prior fighters/spectators remain queued; unrelated players opt in with `/siege join`; a 40-second sweep forces all queued players to the Adventure-mode lobby while the clean copy loads.
- **ACTIVATING:** bind every world-dependent service to the active map, spawn teams, create the new 0–0 match record, and reopen scoring.
- **RECOVERY:** keep the completed match closed and every player in the lobby after all enabled maps (including a fresh previous-map copy last) fail. No empty match is created.

After every player has left the old active world, unload it without saving and delete only its generated active-copy folder. Template folders are never loaded as the match world and are never deleted.

### What Sieges does

Each map is a complete Minecraft world folder. At the start of a match, SiegeGame chooses a configured map, copies its clean world folder, runs the match in that copy, and removes the active copy when the match ends. Its source documents map folders as reusable templates rather than worlds players permanently modify.

The supplied snapshot includes these preserved map worlds:

`al_quds`, `barcelona`, `calgary`, `cube`, `datblock`, `edgerton`, `fort_yamdena`, `iron_mountain1`, `kansas_city_outpost`, `kazan`, `murmansk`, `srdjanopole`, and `tython`.

### Why it is useful

- Every new round begins from a known-clean map.
- It supports true map rotation rather than restoring one arena in place.
- It makes testing a new map reversible: keep the pristine template unchanged.

### Fit for SiegePlugin

For SiegePlugin 1.1, map rotation is the new round boundary. A successful rotation must:

1. Close the prior match record and retain its scores and score ledger for history.
2. Create a new match record with both team scores at zero.
3. Load a clean copy of the selected map and make it the active battlefield.
4. Reset ephemeral round state: banner control/sessions, per-round banner points, statistics, minecart tracking, placed blocks, inventory, purchases, and storage locks.
5. Move fighters and spectators through the Adventure-mode lobby, randomly balance online fighters within one player through Towny's internal combat containers, then use Survival for fighters and Spectator only inside the active siege.

This is implemented around one atomic `ActiveRoundContext`. Capture geometry, team spawns, minecart boundary/sweeper/damage, placed blocks, scoring identity, player launches, and potion-storage resolution rebind before publication. Potion supplies persist as `map_id + chest-half coordinates`; legacy world-name entries remain stored but inactive unless their world is current.

Manual score reset, the Active/Break cycle, six-hour in-place reset, and snapshot capture/restore workflows are retired. Scores reset only by creating a newly activated durable match.

Operator recovery commands:

- `/siege admin rotation status` — phase, generation, current/prepared map, and each fallback candidate with its `PENDING`/`FAILED`/`PREPARED` outcome.
- `/siege admin rotation validate [map|all]` — re-reads `maps.yml` from disk and reports every admission problem per map. A map that does not validate is never copied for a live round.
- `/siege admin rotation retry [map]`

The score cutoff is configured as `scoring.winning-score` (default `10000`), grouped with the other scoring keys. `rotation.preparation-timeout-seconds` (default `300`) bounds how long one map copy may take before its attempt is abandoned.

The winning transaction is guarded: it commits only when durable rotation state moves to `COMPLETING` for that exact match in one row. Otherwise the score, ledger entry, final statistics, winner, and status all roll back, so a match can never be closed without a ceremony to follow it.

Sources: `refs/Sieges-master/README.md`; `SiegeGame`'s `FileMapLoader`/map configuration model.

## Kit editing and saving

### What Sieges does

SiegeGame provides `/kits set <map|allmaps>` and captures the player's current inventory as a persistent kit. A map-specific record is preferred and `allmaps` acts as the fallback. Its shop can distinguish free/base items from bought items so paid items are not folded into a saved kit.

The persistent-player-choice idea is useful, but its implementation should not be copied. SiegePlugin does not need per-map kits, should not capture arbitrary live inventory contents, and already has a safer normalized validation and SQLite persistence foundation.

### Selected design: global curated personal kit

Every player may save one personalized Siege kit that works on every map. `/siege kit` opens two choices:

1. **Equip My Siege Kit** — equip the saved personal kit, or the default kit when no valid saved record exists.
2. **Customize My Siege Kit** — open an editor initialized from the saved kit or current default.

The editor exposes only configurable supply slots. Armor, the primary sword, and offhand are immutable and omitted from the editor. Clicking a supply opens a staff-configured list of legal replacements; players never place arbitrary inventory items into the GUI.

The main editor provides Save & Equip, Reset Draft to Default, and Cancel Without Saving. Save & Equip reconstructs the full kit from trusted configured choices, merges the current default essentials, validates and persists it, closes the GUI, and immediately equips it. If persistence fails, the previous saved kit remains authoritative and no partial kit is equipped.

Implementation now uses `KitSnapshot`, `KitItemSpec`, `KitSlotKind`, and an exact-reconstruction `KitValidator`, plus normalized configured-choice IDs in `kit_loadout_choices`. The old hard-coded `KitProfile`/`KitAllowance` palette and serialized `KitLoadoutDao` path were retired. The launcher/editor/choice/saving state machine uses generation tokens so close events, repeated clicks, disconnects, and asynchronous saves cannot lose or partially apply a selection.

This is implemented in the working tree. The packaged catalog intentionally contains no active replacement presets: administrators opt in by adding slot groups under `kit.editor.slots` using the commented schema in `config.yml`. Invalid groups are disabled independently and logged with their configuration path.

Per-map kits, kit voting, arbitrary player-provided choices, and essential-item customization are explicitly deferred. The approved detailed design is recorded in `memory/2026-08-28-global-personal-kit-editor-design.md`.

Sources: `refs/Sieges-master/plugins/SiegeGame-1.0-SNAPSHOT.jar`; `SiegeGame` kit service/controller, model, and repository source.

## Client-side red-glass border walls

### What Sieges does

SiegeGame defines a map boundary and each team safe area. It uses ProtocolLib packet changes to show a red stained-glass wall to a player who approaches a boundary, while separately enforcing the boundary server-side. It also blocks or rolls back projectiles that would cross a protected border.

This is more readable than an invisible collision rule: players can immediately see why movement, projectiles, or base entry are blocked.

### Fit for SiegePlugin

This is the strongest visual idea to borrow. A SiegePlugin implementation should:

- Define one configured arena boundary and optional base-safe boundaries.
- Enforce movement and projectile crossing on the server; the glass wall is visual feedback, never the security boundary.
- Use the existing configured red/blue team identity only where a team-specific border is needed; otherwise use neutral red glass for a global arena edge.
- Hide the wall when the player moves away and ensure it is rebuilt after join, world change, team switch, spectate/rejoin, and map reset.
- Measure packet and CPU cost with spark before enabling it for a large player count.

It requires ProtocolLib or an equivalent packet-level implementation. That is a new optional dependency and should be designed as an isolated adapter with a graceful no-ProtocolLib fallback: server-side boundary enforcement and a normal chat/action-bar warning.

Best first increment: implement a pure boundary-geometry and crossing-policy test suite, then an admin preview command that displays a temporary boundary only to the admin.

Sources: `SiegeGame` classes under `player/border/`, including `FakeBorderWall`, `PlayerBorderHandler`, and `ProjectileFollowTask` in `refs/Sieges-master/plugins/SiegeGame-1.0-SNAPSHOT.jar`.

## Recommended priority

1. **Configure and live-validate the five selected map templates** — manifests, clean folders, supplies, Towny behavior, and two-map acceptance.
2. **Client-side border preview and enforcement** — bind it to the active map's arena/base geometry.
3. **Lobby build/tutorial** — clearly present `/siege join` opt-in and round state.
4. **Global personal kit editor** — configured replacement menus with validated Save & Equip; the same saved kit applies to every map.
5. **Future voting** — map, kit-mode, and other votes remain deferred. MVP statistics are now part of the round ceremony.

--------------------------

##### CLAUDE FOLLOW ALONG PLAN:

# SiegePlugin 1.1 — Map-Rotation Integration Plan

## Summary

Replace the eternal single-world match with a durable clean-copy round lifecycle owned by one `RotationCoordinator` and one immutable `ActiveRoundContext`.

A match ends when the first committed score reaches or exceeds the configurable 10,000-point target. Players immediately receive a chat results card and lobby button, map preparation begins, and everyone is moved to the lobby within 40 seconds. The next match starts only after the map and lobby rosters are ready.

Use the existing native map copier, Towny team containers, kits, lobby, and potion-storage mechanics. Retire the `ACTIVE`/`BREAK` cycle and the complete arena snapshot/reset workflow.

---

## Core Architecture and Persistence

### Round state machine

```
BOOTSTRAPPING → ACTIVE → COMPLETING → INTERMISSION → ACTIVATING → ACTIVE
INTERMISSION → RECOVERY → INTERMISSION   (handles exhausted map candidates and operator retries)
```

- **`ACTIVE`**: scoring, capture, combat statistics, shops, and battlefield entry are enabled.
- **`COMPLETING`**: the threshold-crossing score transaction is in flight; reject additional scoring and statistics.
- **`INTERMISSION`**: the old match is closed, results are announced, players move to the lobby, and map preparation runs concurrently.
- **`ACTIVATING`**: freeze the queue, assign teams, create the next durable match, publish its context, distribute kits, and teleport everyone.
- **`RECOVERY`**: every automatic map candidate failed. Keep everyone in the lobby with no active match until an operator retries.
- Activation requires **two gates**: the prepared map has passed validation, and every online previous-match participant/spectator has left the old battlefield or disconnected.

### Public lifecycle types

- **`ActiveRoundContext`** — immutable match ID, map identity/display name, active world, spawns, capture point/radius, arena bounds, score limit, and runtime potion supplies.
- **`ActiveRoundProvider`** — exposes the currently published context to world-aware services.
- **`PreparedRound`** — loaded but unpublished candidate map with completed validation.
- **`RotationCoordinator`** — owns state transitions, generation tokens, player queue, fallback sequence, recovery, and old-world cleanup.
- **`RoundActivityStatus`** — replaces `SiegePhaseStatus`; active only while the coordinator is `ACTIVE`.
- **`RoundRole`** — `PLAYER` or `SPECTATOR`.
- **`AwardOutcome`** — accepted score, completed match, rejected closed match, or failed persistence.

All lifecycle callbacks carry a rotation generation so stale copy, teleport, database, and cleanup completions cannot affect a later round.

### Database changes

- Extend `matches` with map ID, runtime-world name, score limit, winner, end time, and statuses `LEGACY`, `ACTIVE`, `COMPLETED`, and `ABORTED`.
- Add singleton durable rotation state containing phase, generation, active/completed match IDs, previous map, prepared runtime world, and lobby deadline.
- Persist ordered fallback candidates and their pending/failed/prepared state.
- Persist the intermission queue with player UUID, role, and automatic/opt-in source.
- Persist each match roster with assigned team or spectator role.
- Persist per-player match statistics: last known name, kills, applied damage, and banner seconds.
- Archive `eternal-1` as `LEGACY` on first rotation startup, retaining its score ledger without presenting a results ceremony. The first rotating map begins at 0–0.
- Existing snapshot files and legacy world-bound potion records are preserved on disk but ignored after migration; do not delete operator data automatically.

---

## Match Completion, Ceremony, and MVPs

### Durable score cutoff

- Configure `rotation.winning-score: 10000`.
- Serialize score awards so only one award decision is unresolved at a time.
- When the known score plus an award reaches the limit, close the local scoring gate before submitting it.
- In one database transaction: apply the full award, append its ledger entry, persist final player statistics, set the winner/end time/status, and move durable rotation state to intermission.
- **Preserve overshoot**: 9,950 plus 150 finishes at 10,100.
- Reject all later score writes with no ledger, currency, session-point, or MVP effects.
- If the completion transaction fails, return to `ACTIVE` and report the failure; do not announce a winner.
- Remove the activity-cycle scheduler, `ACTIVE`/`BREAK` phases, timer configuration, break/resume commands, and sidebar timer. Retain banner-generated points as per-round "Banner Points," reset at activation.

### Results card

Immediately after durable completion, broadcast a gold/team-colored Adventure chat component:

- Winning team and final score.
- **Kill MVP** — player name and kill count.
- **Damage MVP** — player name and applied damage to one decimal.
- **Banner MVP** — player name and `Xm Ys`.
- **Overall MVP** — player name only.
- "Preparing next map: `<display name>` — it will be loaded shortly."
- Clickable **[Go to Lobby]** using the normal Siege command path with a specific intermission action and hover text.

If a candidate fails, broadcast the failure and updated fallback map. Chat history is not edited.

### MVP rules

Track only while the round is `ACTIVE`.

- **Kill**: direct credited enemy-player kills; exclude team kills, environmental deaths, and suicides.
- **Damage**: actual final enemy-player damage after mitigation, capped at remaining health plus absorption. Resolve direct attacks, projectiles, and player-owned siege explosives; exclude cancelled, self, friendly, and environmental damage.
- **Banner time**: one second for every eligible player present in the capture zone on each capture tick, including simultaneous players and existing controllers.
- Checkpoint dirty statistics every five seconds, flush on orderly shutdown, and include the final snapshot in the match-closing transaction.
- Calculate each percentage against the match-wide total across both teams; a zero-total category contributes zero:
  ```
  overall = 0.45 × banner_time_pct + 0.45 × kills_pct + 0.10 × damage_pct
  ```
- Category ties use the primary statistic, then Overall MVP score, then UUID.
- Overall ties use weighted score, banner time, kills, damage, then UUID.
- If nobody contributed to a category, display `None — 0`; if every overall score is zero, display `Overall MVP: None`.

---

## Intermission, Teams, and Active-Map Binding

### Lobby and queue behavior

- Configure a fixed **40-second** force-lobby deadline.
- Previous-round players and spectators enter the next-round queue automatically.
- Other lobby players must run `/siege join`; during intermission this queues them instead of teleporting them.
- Previous spectators remain logically registered as spectators but use Adventure mode in the lobby.
- At match end: close inventories, discard all battlefield inventory, clear obsolete saved-round inventory, bypass combat/capture exit restrictions, and teleport to the lobby.
- Show an action-bar countdown; send chat reminders at 30, 10, and 5 seconds.
- At the deadline, force any remaining online participants and spectators to the lobby. Offline players count as safely removed.
- A player who cannot be teleported is excluded from launch and leaves the old world quarantined from deletion; reconnect handling places them in the lobby.
- Unrelated lobby players remain unqueued. Disconnecting removes a player from the current launch attempt.

### Team assignment and launch

- Wait until both the lobby and prepared-map gates are ready.
- Freeze online queued players immediately before activation.
- Shuffle players, assign each next player to the currently smaller team, and randomize which team receives the odd player.
- Persist planned assignments, then apply them idempotently through Towny. Balance online round participants, not total Towny residents.
- A failed Towny move leaves that player in the lobby and does not cancel everyone else; successful assignments remain balanced by assigning sequentially to the smaller successful side.
- Logical spectators stay in the spectator Towny container and receive no competitive team.
- Create the new `ACTIVE` match and durable roster before publishing `ActiveRoundContext`.
- Players receive Survival mode, a fresh curated kit or default fallback, and their team spawn. No purchases, supplies, or other inventory carry forward.
- Spectators receive Spectator mode only after entering the active battlefield.
- Currency and saved kit choices persist.
- Mid-round `/siege join` assigns the player to the smaller active online roster and gives a fresh kit; intermission `/siege join` only queues.

### Service rebinding

Make services read the published context rather than boot-time world configuration:

- **Capture**: new banner location/radius, cleared sessions/control, continuous capture until match end.
- **Player transitions and team switching**: dynamic team spawns and coordinator-aware lobby/queue behavior.
- **Scoring/death/currency**: active match ID and round activity gate.
- **Sidebar**: active map, score target, scores, banner control, and per-round banner points.
- **Minecarts**: active world and manifest bounds; clear cooldown/headcount/sweeper state.
- **Placed blocks**: retain in-round tracking but clear it at activation.
- **Potion supplies**: resolve map ID plus template coordinates into the generated world, rebuild labels, refill, and clear locks.
- **Shop and battlefield interactions**: reject during intermission/recovery.
- **Kit system**: retain global saved selection; activation always reconstructs a fresh trusted loadout.

Publish the context in one main-thread operation after all bindable data has been prevalidated. Activation hooks may reset ephemeral state but must perform no database or filesystem work and must not throw under validated input.

---

## Maps, Supplies, Recovery, and Retired Systems

### Map configuration and selection

- Load `maps.yml` independently from `config.yml`.
- An enabled map requires a safe template folder, display name, Red/Blue spawns, capture point/radius, arena bounds, and valid optional supply definitions.
- Validate path containment, template metadata, coordinates within bounds, safe spawn blocks, capture placement, and configured double-chest supplies before admitting a map to rotation.
- Use a shuffled non-repeating map bag. Normally exclude the map that just ended.
- On match completion, select and announce the first candidate immediately, then copy/load asynchronously.
- **Automatic fallback order**: selected candidate → every other enabled validated map once in shuffled order → a fresh copy of the map that just ended.
- Clean every failed partial copy through the guarded generated-folder deletion path.
- Old active worlds unload without saving only after no players remain; cleanup failure is reported and retried but does not corrupt the new match.

### Potion-storage migration

- Keep the existing physical double-chest behavior, lock, refill rules, labels, and in-game registration commands.
- Change durable identity from literal runtime world name to map ID plus both template chest-half coordinates.
- Registration while standing in an active copied map resolves and saves its map ID; the same coordinates bind to every future copy.
- Rebuild only the active map's supplies on activation.
- Preserve legacy world-name entries as inactive legacy records and warn operators to re-register them per map.

### Durable recovery

"Candidates exhausted" means no validated template could be safely copied, loaded, and bound, including the prior map fallback.

- Persist `RECOVERY`, candidate errors, completed match ID, queue, and prior map.
- Keep players in Adventure mode in the lobby.
- Do not create a new match, reset scores, assign teams, distribute kits, or reopen scoring.
- Add admin commands:
    - `/siege admin rotation status`
    - `/siege admin rotation validate [map|all]` — reload and validate `maps.yml`
    - `/siege admin rotation retry [map]`
- On restart:
    - **`ACTIVE`**: reload the recorded active copy and resume scores/stat checkpoints; if unrecoverable, mark the match `ABORTED` and enter intermission without declaring a winner.
    - **`COMPLETING`**: trust the atomic database result — either resume `ACTIVE` or continue the completed intermission.
    - **`INTERMISSION`/`RECOVERY`**: put joining players in the lobby, discard incomplete generated copies, and resume candidate preparation/recovery.
    - **`ACTIVATING`**: replay persisted Towny assignments idempotently and publish the already-created match/context.
- Never reopen a completed match or announce its winner twice.

### Retire snapshot and activity-cycle systems

- Remove the six-hour reset scheduler and `cleanup.map-reset-interval-hours`.
- Remove arena snapshot capture/restore services, maintenance coordinator, snapshot limits/configuration, and their startup warnings.
- Remove `/siege admin setresetpos1`, `setresetpos2`, `savesnapshot`, and `resetmap`.
- Replace reset recovery with rotation retry/fresh-copy operations.
- Replace snapshot-derived minecart protection with active `MapBounds`.
- Remove break/resume commands, activity-cycle configuration, tests, and documentation.
- Leave old snapshot files untouched for manual operator cleanup.

---

## Test and Release Plan

### Automated tests

- Pure coordinator tests for every valid transition, stale generation, duplicate completion callback, simultaneous readiness gates, deadline flush, disconnect, and recovery retry.
- DAO concurrency tests proving exactly one threshold-crossing award completes a match, overshoot is retained, later awards are rejected, and transaction failure leaves the match active.
- Restart hydration tests for every durable state and legacy `eternal-1` migration.
- Fallback tests covering primary failure, alternate success, previous-map final fallback, total exhaustion, partial-copy cleanup, and invalid manifests.
- Roster tests for randomization, difference no greater than one, opt-in players, spectators, disconnects, Towny failures, and idempotent replay.
- MVP tests for attribution, zero totals, percentage calculation, weighted ranking, deterministic ties, formatting, checkpoint recovery, and atomic final persistence.
- Service-context tests proving no service observes a mixed old/new map and every ephemeral tracker resets once.
- Potion tests proving map-relative records bind to different generated world names without crossing maps.
- Update command, configuration, sidebar, and canonical-config tests after removing cycle/snapshot behavior.

### Live Paper acceptance matrix

Use at least four players plus one spectator and two validated maps.

- End matches through both a kill award and banner award; verify one winner, full overshoot, one ledger close, and no post-win rewards.
- Verify results formatting, MVP values, clickable lobby button, reminders, forced 40-second movement, and early launch when both gates become ready.
- Verify Adventure lobby mode, spectator lobby transition, restored Spectator mode on the battlefield, balanced random Towny teams, and visual team refresh.
- Verify fresh curated/default kits, discarded purchases, persistent currency/preferences, and clean map terrain.
- Delay and fail map copies; verify fallback updates, previous-map fallback, complete recovery, admin retry, and no phantom 0–0 match.
- Restart during `ACTIVE`, completion, intermission, activation, and recovery; verify no duplicated ceremony, match, score, assignment, or inventory.
- Verify map-relative potion supplies, labels, locking, refill, minecart bounds, sweeper reset, placed-block reset, and old-world cleanup.
- Profile async copying and five-second stat checkpoints; copying must not block server ticks.

### Rollout assumptions

- Approach B (prepared map plus atomic active context) is the approved architecture.
- Rotation becomes the only production match mode; do not deploy until at least two maps pass validation so fallback can be staged.
- All five selected maps remain disabled until their coordinates, bounds, and optional supplies are configured and tested.
- Map/kit voting and client-side border walls remain deferred.
- Update the expansion brief, map-rotation design, playtest guide, command reference, and standing reminder when implementation is complete.

### CLAUDE VALIDATION PLAN

# Map-Rotation Correctness and Release-Hardening Plan

## Summary

Retain the other agent's useful foundation — clean-copy loading, finite scoring, MVP calculation, balanced roster planning, service rebinding, and retired snapshots — but do not enable maps yet.

All 206 tests pass, but the central coordinator is effectively untested, and several release-blocking issues remain: stale async callbacks can activate the wrong map, lobby teleport failure can stall rotation forever, combat outside the active arena can award points, restart recovery can lose participants or reopen closed matches, and loaded maps are not fully validated.

---

## Core Lifecycle and Persistence

- Refactor the large coordinator behind injectable persistence, scheduler, player-transition, and world-lifecycle ports so its state machine can be tested deterministically without rewriting the clean-copy loader.
- Give every preparation/activation operation a unique attempt token. Retry, shutdown, and recovery invalidate earlier attempts; stale completions may only unload their generated world.
- Add compare-and-set lifecycle persistence using phase, generation, and revision. Never acknowledge a queue, phase change, roster change, abort, or active publication before its database write succeeds.
- Require the winning transaction's guarded `rotation_state` update to affect exactly one row; otherwise roll back the score, ledger, final statistics, winner, and status.
- Validate existing match status and map/runtime metadata before `ScoringService` activates it. Reconcile restart mismatches as follows:
  - **`ACTIVE`** match resumes.
  - **`COMPLETED`** match continues completion/intermission.
  - **`ABORTED`** match transfers its roster to intermission without a winner.
  - A failed roster read during `ACTIVATING` enters recovery; it must never reshuffle or replace the persisted plan.
- Atomically abort an unrecoverable match and transfer its full roster to the intermission queue. Abort failure leaves the server in `RECOVERY`.
- On first rotation startup, archive `eternal-1` without ceremony and enter intermission for rotation-1 at 0–0. If no enabled map exists, enter lobby recovery instead of running a transitional legacy match.
- Bind MVP tracking to an explicit match ID. Reset/bind it before changing scoring identity, checkpoint only during matching `ACTIVE` context, and replace the complete stored snapshot so old-player rows cannot leak into a new match.

### Persistence additions

- Add `rotation_state.revision`.
- Replace candidate CSV as the authority with ordered candidate rows containing map ID, status, and sanitized failure reason; migrate existing CSV records non-destructively.
- Add a durable generated-world cleanup queue with attempt count, last error, and next retry time.
- Extend match roster entries with `PLANNED`, `BATTLEFIELD`, and `LOBBY` presence.
- Preserve all existing matches, ledgers, inventories, potion records, templates, and snapshot files.

---

## Players, Eligibility, and Ceremony

- Make the durable current-match roster authoritative instead of Towny residency:
  - Load it during `ACTIVE` recovery.
  - Preserve presence across disconnects and restart.
  - Rehydrate battlefield fighters/spectators on reconnect.
  - Keep voluntarily returned players in the lobby until they run `/siege join`.
- Make `/siege join` phase-explicit:
  - **`ACTIVE`**: persist a mid-round assignment, join the smaller active online roster, and issue a fresh curated/default kit.
  - **`INTERMISSION`/`RECOVERY`**: durably queue and move to the lobby.
  - **`BOOTSTRAPPING`/`COMPLETING`/`ACTIVATING`**: reject with a temporary-state message.
- At the 40-second deadline, attempt forced evacuation once. Players whose teleport fails are excluded from that launch, remain safely queued, and quarantine the old world; they must not block activation indefinitely.
- Catch fighter and spectator Towny/teleport exceptions independently. One failed player cannot strand the round in `ACTIVATING`.
- Count only rostered online battlefield fighters when balancing teams or processing team switches; update the roster after a successful switch.
- Add one shared active-combat eligibility policy requiring `ACTIVE`, current runtime world, battlefield presence, and fighter role. Apply it to deaths, damage MVP, capture participation, shop delivery, potion access, placed blocks, and siege-minecart placement.
- Every eligible siege death receives exactly one Siege message. A kill whose score write is rejected displays `Battle Points +0` and grants no kill MVP or currency.
- Preserve the current result layout, but add the lobby-button hover text and change the action bar to describe lobby transfer rather than promising the next siege will start at that exact second.

---

## Maps, Potion Supplies, and Cleanup

- Parse `maps.yml` strictly so malformed YAML fails instead of becoming an empty manifest. Retain the last-good live manifest after a failed reload.
- Validate enabled map IDs against `[A-Za-z0-9_-]+`, require every bounds field, reject non-finite coordinates, and use the loaded world's actual minimum/maximum height.
- Use two-stage admission:
  - **Static**: real-path template containment, `level.dat`, required metadata, ordered bounds, coordinates, capture radius, and registered-supply bounds.
  - **Loaded copy**: solid non-hazardous spawn footing, passable feet/head space, valid capture placement/support, and both halves of every configured double chest.
- Share loaded-copy admission between automatic rotation and asynchronous `/siege admin rotation validate`; validation copies are always unloaded and cannot mutate round state.
- Register and resolve potion storage only when the physical chest belongs to the published active runtime world and lies within that map's bounds. A lobby chest at matching coordinates must never resolve as a supply.
- Replace the overloaded storage `worldName` identity with an explicit map-relative location type while retaining legacy world-name records as inactive warnings.
- Keep the existing copy/delete safeguards, adding a five-minute configurable preparation timeout and composed cleanup. Persist old, failed, and stale generated worlds before cleanup begins; retry with capped backoff up to five minutes until player-free.
- Report pre-existing untracked generated folders for manual review rather than deleting them automatically.
- Track spawned potion-label UUIDs so activation removes only SiegePlugin's prior labels instead of scanning every entity in every world.
- Make `/siege admin setbanner` update and validate the active map's `maps.yml` capture coordinates; it must no longer mutate obsolete boot-world coordinates.

---

## Tests and Acceptance

- Add coordinator tests covering every phase, duplicate completion, simultaneous readiness gates, overlapping retries, stale callbacks, deadline teleport failure, candidate exhaustion, previous-map fallback, and cleanup recovery.
- Add restart tests for all durable phases, match/state mismatches, roster/presence hydration, idempotent assignment replay, aborted roster transfer, and first-rotation migration.
- Add transaction tests proving missing/mismatched rotation state and injected final-stat failures roll back the complete winning award.
- Add player-transition tests for fighter/spectator success and failures, phase-specific joins, active-roster balancing, reconnects, and inventory/kit behavior.
- Add eligibility tests proving lobby, other-world, unrostered, friendly, self, and environmental activity cannot affect score, currency, capture, or MVPs.
- Add strict manifest, runtime spawn/capture/chest, wrong-world potion registration, map-relative rebinding, label cleanup, timeout, and stale-copy tests.
- Run the complete Maven suite, package build, `git diff --check`, and stale-reference searches for retired snapshot/cycle/reset APIs.
- Keep all maps disabled until two maps pass strict validation and the four-fighter/one-spectator playtest — including win by kill/banner, forced-lobby failure, fallback, and restart in every phase — passes.
- After acceptance, update the expansion brief, map-rotation memory, playtest guide, and repository priority notice. Keep `scoring.winning-score: 10000`, the 40-second lobby deadline, native clean-copy loading, existing potion behavior, and deferred voting/border-wall features.