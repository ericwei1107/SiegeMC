# SiegePlugin 1.1 Expansion

## Purpose

This is a future-looking ideas brief, not an approved implementation plan. It records the parts of the `refs/Sieges-master` server snapshot worth considering after the current Stage 4.4 work is stable.

Sieges is a finite, rotating SiegeGame minigame on Paper 1.20.1. SiegePlugin is a Towny-backed Paper 1.21.11 game. Its current persistent-score model was deliberately retained during Stage 4.4 testing, but that testing-only decision is now superseded: SiegePlugin 1.1 will rotate maps and begin each newly rotated map with reset team scores.

Reuse the player-facing ideas below, but do not copy Sieges' legacy plugin stack wholesale.

### To do

- Infinite Potion Storages for both sides — Approach A implemented in the working tree; pending live multiplayer staging verification
- Map implementation
- Map rotation
- Lobby Build
- Kit editing
- Client side border walls

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

`ACTIVE → COMPLETING → LOBBY_CEREMONY → PREPARING_COPY → REVEAL → ACTIVATING → ACTIVE`

- **COMPLETING:** atomically reject new scoring/capture activity, persist the winning match result, and cancel active capture sessions.
- **LOBBY_CEREMONY:** send players safely to the lobby, announce the winner, and run the countdown.
- **PREPARING_COPY:** choose a non-repeating validated template, copy it asynchronously to a unique active-world folder while excluding `session.lock` and `uid.dat`, then create/load the world on the main thread with autosave disabled.
- **REVEAL:** show the selected map name only after its world and manifest are ready.
- **ACTIVATING:** bind every world-dependent service to the active map, spawn teams, create the new 0–0 match record, and reopen scoring.

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
4. Reset ephemeral round state: banner control, capture sessions, activity-cycle state, BAT session points, temporary borders, and per-round cooldowns.
5. Move players safely through the lobby while worlds unload/load, then assign or restore them to their active team and spawn.

The existing `/siege admin resetscores` command remains an emergency administrative tool. Normal score resets should happen only as part of a fully successful map rotation; a failed rotation must leave the old map and its scores active.

Before implementation, refactor current startup-bound services to accept the active map: `CaptureService`, team spawn locations, arena region/reset, minecart cleanup/protection, score-match definition, and potion-storage resolution all currently assume a static `siegeworld`. Towny continues to determine a player's Red/Blue membership; the maps do not import Sieges' legacy territory/team data. Map templates must also be versioned, validated, backed up, and protected from accidental editing.

Best first increment: add a read-only map-template registry and an admin-only dry-run validator before any world-copy or unload operation.

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

Reuse `KitSnapshot`, `KitProfile`, `KitItemSpec`, `KitSlotKind`, `KitValidator`, `KitLoadoutDao`, and the edit-session concept. Replace the old/dormant GUI workflow with an explicit launcher/editor/choice/saving state machine so close events, repeated clicks, disconnects, and asynchronous saves cannot lose or partially apply a selection.

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

1. **Map-template registry and validation** — configure and validate the five selected templates; no world swapping yet.
2. **Native clean-copy loader** — asynchronous folder copy plus main-thread Bukkit/Paper load, safe unload, and generated-folder deletion.
3. **Transactional map rotation and match rollover** — winner ceremony, lobby countdown/map reveal, archive the prior match, and create the zero-score match only after activation succeeds.
4. **Global personal kit editor** — configured replacement menus with validated Save & Equip; the same saved kit applies to every map.
5. **Client-side border preview and enforcement** — bind it to the active map's arena/base geometry. Map voting, kit voting, and MVP statistics are explicitly later features; they are not part of this release.
