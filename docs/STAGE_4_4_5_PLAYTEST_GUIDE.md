# SiegePlugin 1.1 Live Acceptance Guide

This guide covers the current finite-round build. Run it on the disposable development server with four fighter accounts, one spectator account, and at least two enabled clean-copy maps. Back up `plugins/SiegePlugin/` first. A passing Maven suite is necessary but does not replace this Paper/Towny acceptance run.

## Configuration gate

0. Migrate `plugins/SiegePlugin/config.yml`: add `scoring.winning-score` and the `rotation.preparation-timeout-seconds` block, and delete the retired `activity-cycle` block, `cleanup.map-reset-interval-hours`, and the `arena-reset` block. The plugin now refuses to start while any of them remain, naming each one. Old snapshot files under `plugins/SiegePlugin/snapshot/` are deliberately left on disk for manual cleanup.
1. Put immutable template folders under `plugins/SiegePlugin/maps/templates/<template-folder>`.
2. Configure and enable at least two entries in `maps.yml`, following the commented schema at the top of that file: both spawns, capture point/radius, and ordered X/Z bounds.
3. Start the server and run:

   ```text
   /siege admin rotation validate all
   /siege admin rotation status
   ```

4. Every enabled map must report `valid`. `validate` re-reads `maps.yml` from disk, so an edit needs no restart, and it fails loudly on malformed YAML instead of quietly reporting an empty pool. Validation runs in two stages and a map must clear both before it can host a round:
   - **Static** (manifest and template folder): real-path template containment, `level.dat`, map id restricted to `[A-Za-z0-9_-]`, every `bounds` edge present, finite coordinates, both spawns and the entire capture radius inside `bounds`, distinct team spawns, and registered supplies inside `bounds`.
   - **Loaded copy** (a throwaway copy is made, checked, then always unloaded): solid non-hazardous spawn footing, passable feet and head space, a supported capture position, and heights inside that world's own build range. Tagged supply chests are discovered from the template and do not veto rotation.

   Because it copies each template, `validate all` takes a moment and reports asynchronously. A map that does not validate is skipped when rotation picks candidates, so fix every problem before relying on it as a fallback.
5. Confirm the plugin depends only on Towny and CombatLog; Multiverse is not a lifecycle owner.
6. Check the startup log for a warning listing potion supplies still recorded against a literal world name. Those legacy records stay in `potion-storages.yml` but are inactive; re-register them per map.
7. Check the startup log for a warning listing generated `siege-active-*` folders with no durable record. These are reported for manual review and are never deleted automatically.
8. On the very first start after upgrading, confirm the log says the pre-rotation endless match was archived and that rotation began at intermission. There must be no transitional round on the old boot world, and `eternal-1` must show status `LEGACY` with its score ledger intact. With no enabled map, the server must sit in lobby recovery rather than starting anything.

## Normal winner-to-map flow

- [ ] Use four fighters split across Red/Blue and one spectator. Confirm unrelated lobby players are not auto-assigned merely by joining the server.
- [ ] Opt fighters in with `/siege join`. Towny membership is only the internal combat-team container. Confirm `/siege join` is phase-explicit: during `ACTIVE` it puts you straight into the smaller battlefield side with a fresh kit; during intermission or recovery it queues you and moves you to the lobby; during the brief changeover it is rejected with a temporary-state message rather than doing something surprising.
- [ ] Raise one side through `scoring.winning-score` (default 10,000). Test an overshoot, such as 9,950 + 150 = 10,100; the full 10,100 must persist.
- [ ] The first crossing write ends the match exactly once. Later queued score awards do not change totals or create ledger rows.
- [ ] Chat immediately shows winner/final score, Kills MVP with kills, Damage MVP with one-decimal damage, Banner MVP with minutes/seconds, Overall MVP name only, next map, and clickable `[Go to Lobby]`. A category nobody contributed to shows `None — 0`; an entirely uncontested round shows `Overall MVP: None`.
- [ ] Kills count only credited enemy-player kills. Self, environment, and friendly deaths announce `Battle Points +0` and do not change team score or kill MVP. Every eligible siege death produces exactly one announcement — including a kill landing right as the round closes, which must read `+0` and award no MVP credit or currency.
- [ ] Confirm nothing done outside the round counts. From the lobby, from another world, as a spectator, or after using `/siege lobby`: deaths award no score, damage does not reach the Damage MVP, standing at the banner earns no capture progress, shop purchases are refused, base potion chests do not open, placed blocks are not tracked as breakable, and siege TNT minecarts cannot be placed.
- [ ] Leave one participant on the old map. Others click the lobby button. Every queued player sees a per-second action-bar countdown; chat carries reminders only at 30, 10, and 5 seconds. At 40 seconds the holdout is forced into the lobby.
- [ ] Fighters and spectators are Adventure in the lobby. Spectators are also evacuated and are not Spectator-mode in the lobby.
- [ ] The next clean map activates as soon as it is ready and all online queued players are safely in the lobby.
- [ ] Fighters are shuffled and balanced within one player, moved through Towny, teleported to map spawns, set to Survival, and receive only their curated/default fresh kit. Prior inventory and purchases do not transfer; durable currency and kit choices do.
- [ ] Over several rounds with an odd number of fighters, confirm the extra player does not always land on the same team.
- [ ] Force one player's launch to fail (for example, block their spawn teleport). Confirm everyone else still launches, the failed player is returned to the lobby, told to use `/siege join`, and stays queued; the published teams still differ by at most one, and the old world is reported as quarantined rather than failing the round.
- [ ] The queued spectator retains spectator residency, enters the active map in Spectator mode, and receives no team.
- [ ] New scores begin at 0–0. Banner control, banner points, MVP stats, placed-block tracking, potion locks/labels, minecart tracking, and cooldown state are fresh.
- [ ] After all players leave the old active copy, it unloads without saving and only its generated directory is deleted. The template remains unchanged.
- [ ] Keep one player in the old world when the next round starts. The old copy is recorded in the cleanup queue, reported as deferred, and retried with backoff until it is empty — the new round is unaffected. Restart mid-cleanup and confirm the retry resumes.

## Queue and reconnect behavior

- [ ] Prior participants are queued automatically. An unrelated lobby player must use `/siege join` to opt in.
- [ ] Disconnect/reconnect a queued fighter and spectator during intermission. Both remain queued and return to the Adventure-mode lobby.
- [ ] Restart during ACTIVE. The recorded disposable world and durable score resume; if its folder is missing, the match becomes ABORTED with no winner, chat says the siege was abandoned, and the coordinator moves straight to intermission and prepares the next map. Recovery is only entered when no map can be prepared at all.
- [ ] Restart during INTERMISSION. The queue, deadline, candidate order, and prepared copy resume without another winner announcement or new match.
- [ ] Restart during ACTIVATING. The persisted plan is replayed rather than re-shuffled — confirm each player lands on the team recorded before the restart — and only one active match is published. If the plan cannot be read, the server must enter recovery rather than invent a new one.
- [ ] Restart twice after a win. The ceremony must appear exactly once in total; the second restart continues the intermission silently.
- [ ] Use `/siege lobby` mid-round, then reconnect. The player stays on the roster but is returned to the lobby, not the battlefield, until they run `/siege join` again. A player who was on the battlefield when they disconnected is restored to it on reconnect.

## Fallback and recovery

- [ ] Break the initially selected template and complete a match. Rotation announces the failure, tries every other enabled validated map once, and announces the fallback it settles on.
- [ ] Verify a fresh clean copy of the previous map is the final fallback candidate.
- [ ] Break all candidates. The completed match remains closed, everyone remains in the lobby, and no zero-score match is created.
- [ ] Make one map copy hang (for example, by making its template unreadable mid-copy). After `rotation.preparation-timeout-seconds`, the attempt is abandoned and the server enters recovery rather than holding everyone in intermission indefinitely.
- [ ] Run `/siege admin rotation retry` while a copy is still in flight. When the abandoned copy finally lands it must only delete itself — never activate a second round over the retry.
- [ ] Check `/siege admin rotation status`. Each candidate is listed with its outcome — `PENDING`, `FAILED`, or `PREPARED` — and those outcomes survive a restart.
- [ ] Repair a template, run `/siege admin rotation validate <map>`, then `/siege admin rotation retry <map>`. The repaired map should activate without replaying the old winner.

## Potion supplies

- [ ] During each map's calibration, claim a physical double chest using `/siege admin supply claim <red|blue>`, finish calibration, and verify `/siege admin supply list` shows it when that map is active.
- [ ] Only the owning team (or admin) can open it. One player at a time can access it; the second receives the red in-use message.
- [ ] Withdraw potions, close, reopen, disconnect, die, and rotate maps. Contents refill, locks clear, and only the active map's labels/storage definitions resolve. Registration is refused outside the active map and outside its `bounds`.
- [ ] Place a double chest in the lobby at the exact coordinates of a registered supply. It must behave as an ordinary chest, never as a team supply.
- [ ] After a rotation, confirm only the new map's labels exist — the previous map's floating labels are removed, and no unrelated `TextDisplay` entity in any world is touched.
- [ ] Confirm legacy world-name entries remain in `potion-storages.yml` but do not attach themselves to unrelated active copies.

## Continuous capture, shop, kits, and minecarts

- [ ] Capture has no Active/Break timer. It continues until the winning score closes the round.
- [ ] `/siege admin setbanner` refuses unless you are standing inside the active map and its bounds, moves the live banner, and writes the new coordinates into `runtime-map-overrides.yml` for that map so they survive rotation without a future deployment overwriting them.
- [ ] The sidebar has a bold gold title, gold labels, and nine lines: the active map's display name, ATK/DEF, both scores, the score target, banner control, and per-round Banner Points—no cycle timer. Map and Target change the moment a new round is published.
- [ ] Shop purchases and battle interactions are rejected outside ACTIVE. An in-flight purchase crossing the round boundary refunds instead of delivering into the lobby.
- [ ] Kit editing remains global across maps. Save & Equip equips immediately, and every new round starts from the validated personal/default kit.
- [ ] Tagged TNT minecarts require a published active map boundary. Explosions preserve terrain inside that map's X/Z bounds while entity damage remains active.
- [ ] Damage MVP uses final post-mitigation enemy-player damage capped at remaining health plus absorption, including projectiles and attributed siege explosives.

## Evidence to retain

For any failure, record the exact command/action, player UUID/name/team/role/game mode, coordinator status line, match ID/map/runtime world, relevant server log, and whether the issue survives restart. Do not deploy the rotating build publicly until the complete two-map run passes.
