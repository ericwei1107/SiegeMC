# Stage 4.4 and 4.5 Playtest Guide

This is the manual test guide for the current SiegePlugin build. It covers every player-facing Stage 4.4 system and the Stage 4.5 TNT-minecart mechanic. Run it on the development server or another disposable test server, not on the production map.

## Before you start

- Back up the test world and `plugins/SiegePlugin/` before changing configuration or saving an arena snapshot. A snapshot is the clean map state that `/siege admin resetmap` restores.
- Use at least two players for team, display, capture, and friendly-fire checks. The exact 7-player minecart threshold requires seven eligible players on one team inside the capture zone.
- Record the plugin version, test date, server log, players involved, configuration changes, and pass/fail result for every failed check.
- The packaged defaults are in `src/main/resources/config.yml`; the server uses `plugins/SiegePlugin/config.yml`. There is no `/siege reload` command. Restart the server after editing configuration.

The current minecart-related defaults are:

```yaml
cleanup:
  minecart-stationary-cleanup-seconds: 300
  minecart-placement-cooldown-seconds: 30

minecart:
  damage:
    balanced-coefficient: 0.825
    full-damage-deficit: 7
```

For a quicker cycle test, temporarily set `activity-cycle.active-duration-seconds: 30`, `activity-cycle.break-duration-seconds: 30`, and `cleanup.map-reset-interval-hours: 0.05` (three minutes). Restart after changing them, then restore the normal values when finished.

Currency gains and shop prices are deliberately `0` in the current defaults. To test currency credit, deductions, and the insufficient-balance path, temporarily set a non-zero `currency.per-kill`, `currency.per-capture-tick`, and one or more `shop.prices.*` values, then restart. Record those temporary values and restore the intended balance configuration after the test.

## Command and permission reference

All plugin actions use `/siege`; there are no separate minecart commands.

| Command | Permission | Expected use |
| --- | --- | --- |
| `/siege team` | `siege.team` | Show the caller's team or `no team`. |
| `/siege switch <red|blue>` | `siege.switch` | Request a balanced team switch. |
| `/siege shop` | `siege.shop` | Open the shop GUI. |
| `/siege kit` | `siege.kit` | Replace the player's inventory and equipped armor with the configured global kit snapshot. A five-minute server-side cooldown starts after success. |
| `/siege join` | `siege.join` | Leave the lobby and enter the siege. |
| `/siege lobby` | `siege.lobby` | Return to the lobby, when eligible. |
| `/siege spectate` | `siege.spectate` | Enter spectator mode and SpectatorTown. |
| `/siege rejoin` | `siege.rejoin` | Leave SpectatorTown and rejoin the smaller siege team. |
| `/siege admin setbanner` | `siege.admin` | Put the capture banner at the admin's current location. |
| `/siege admin resetscores confirm` | `siege.admin` and `siege.admin.resetscores` | Permanently zero both persistent siege scores. Without `confirm`, it only warns. |
| `/siege admin break [seconds]` | `siege.admin` | Start or extend a capture-control break. The optional duration must be positive. |
| `/siege admin resume` | `siege.admin` | End a forced break and return banner control to active. |
| `/siege admin setresetpos1` | `siege.admin` | Save the first arena-snapshot corner at the admin's location. |
| `/siege admin setresetpos2` | `siege.admin` | Save the second arena-snapshot corner at the admin's location. |
| `/siege admin savesnapshot confirm` | `siege.admin` | Save the current selected arena as the clean reset snapshot. Without `confirm`, it only describes the overwrite. |
| `/siege admin savekit confirm` | `siege.admin` | Capture the admin's current inventory, equipped armor, and offhand as the global kit and activate it immediately. Without `confirm`, it only warns. |
| `/siege admin resetmap` | `siege.admin` | Queue restoration from the saved snapshot. It is disabled until a snapshot exists. |

`siege.minecart.cooldown.bypass` defaults to operators. It bypasses the TNT-minecart placement cooldown, but it does **not** bypass the requirement that a shop-tagged cart needs an arena snapshot.

Useful test-only commands, entered by an operator or server console, are:

```mcfunction
/give <player> minecraft:tnt_minecart 1
/lp user <player> permission set siege.minecart.cooldown.bypass true
/lp user <player> permission unset siege.minecart.cooldown.bypass
```

The `/give` cart is intentionally untagged. It is the control case for Stage 4.5: it receives the normal vanilla explosion and terrain behavior. A cart bought through `/siege shop` is tagged and receives the Stage 4.5 custom rules.

## Recommended test order

1. Start the server and complete the smoke checks.
2. Prepare the banner and a clean arena snapshot.
3. Run Stage 4.4 team, state, capture, scoring, cleanup, shop, kit, and spectator checks.
4. Establish the vanilla TNT-minecart baseline, then test the tagged shop cart.
5. Repeat failed cases with the server log open. Do not diagnose a failure by changing several settings at once.

## 1. Startup and administrator smoke checks

- [ ] Start the server. Confirm SiegePlugin enables, SQLite initializes, and the `eternal-1` match loads without a stack trace.
- [ ] Confirm Towny, CombatLog, and Multiverse-Core are enabled before SiegePlugin.
- [ ] Confirm `teamRed`, `teamBlue_`, `SpectatorTown`, `lobby`, and `siegeworld` match the live configuration. On a fresh server, SpectatorTown should be created with no claimed land.
- [ ] Confirm the capture world does **not** have the Minecart Improvements experiment enabled. The plugin must reject that world at startup because the experiment changes minecart stacking and fall behavior.
- [ ] Run `/siege` and verify the displayed usage lists the player commands. Run `/siege admin` as an admin and verify the admin usage lists the eight subcommands.
- [ ] As a non-admin, run `/siege admin resetmap`; verify it is denied. As a player without a relevant permission, verify the matching player command is denied.

### Prepare the snapshot before TNT tests

Stand at opposite corners of the clean arena and run:

```mcfunction
/siege admin setresetpos1
/siege admin setresetpos2
/siege admin savesnapshot
/siege admin savesnapshot confirm
```

- [ ] The first two commands report the selected coordinates and, after the second, the normalized block/tile count.
- [ ] The first `savesnapshot` warns that it overwrites the clean-map snapshot; only the confirmed command captures it.
- [ ] Restart the server and run `/siege admin resetmap`. It should queue a restore rather than report a missing snapshot.

## 2. Stage 4.4 gameplay checklist

### Teams, switching, display, and direct PvP

- [ ] With a player in each town, run `/siege team` on both and verify the correct team is reported. A player in neither siege town reports `no team`.
- [ ] Join three fresh test accounts. The first two should be assigned to opposite teams; the third should join the smaller side. Confirm this through Towny residency as well as `/siege team`.
- [ ] Run `/siege switch red` or `/siege switch blue`. A switch which would leave the destination at least two players larger is rejected; an equal or smaller destination succeeds, moves Towny residency, and teleports the player to the destination spawn without clearing their inventory.
- [ ] Immediately repeat a successful switch. It is blocked by the 15-minute switch cooldown and states the remaining time.
- [ ] Try switching while CombatLog-tagged and while actively capturing. Both are rejected. A failed Towny change must not leave the plugin's team record and Towny residency mismatched.
- [ ] Two teammates see one another as friendly (green) in the tab list and above-head name. Opponents see one another as enemy (red). A team switch updates every viewer without reconnecting.
- [ ] Teammates cannot damage one another with direct melee, arrows, tridents, or other direct PvP. They can pass through one another without collision.

### Lobby, siege, death, capture, sidebar, and cycle

- [ ] From the lobby, run `/siege join`. Verify the expected siege inventory, team spawn teleport, survival state, and visible sidebar. Repeating it says the player is already in the siege.
- [ ] While not combat-tagged or capturing, run `/siege lobby`. Verify the lobby inventory and lobby teleport. Repeat to confirm it says the player is already in the lobby. While combat-tagged or capturing, it must be rejected.
- [ ] Stand within 16 horizontal blocks and 16 vertical blocks of the banner. A capture boss bar starts and reaches completion after the configured 420 seconds. Leaving the cylinder resets progress to zero; re-entering begins a new session. A player over 16 blocks above/below the banner does not start a session.
- [ ] With two eligible players controlling the banner, verify the sidebar's banner-control line, session points, and persistent team scores update in real time. Restart mid-siege and confirm persistent scores survive unchanged.
- [ ] Run `/siege admin resetscores` without confirmation: it only warns. Run `/siege admin resetscores confirm`: both persistent scores become zero. Do this only after recording the pre-reset score.
- [ ] Force `/siege admin break 30` while a capture session is active. Existing sessions cancel, new ones cannot start, session points freeze, and direct PvP still works. After 30 seconds, or after `/siege admin resume`, capture control becomes active again. Restart during a break and confirm the server returns in `ACTIVE`, not a stuck break.
- [ ] Kill team members with a real killer and by environmental damage. The dying player's opposing team receives the configured `scoring.kill-reward-points` in active play; an environmental death grants no player currency. A teammate's TNT-minecart kill still credits the *dying player's opponent*.

### Arena cleanup, shop, kit, and spectator state

- [ ] Place a shop-bought building block or cobweb in the arena, then break it as the owner and as an enemy. Both must be able to break tracked player-placed blocks, while natural terrain remains protected by Towny. After restart, the accepted limitation is that pre-restart tracked blocks are no longer recognized as player-placed; the next map reset still removes them.
- [ ] Make a deliberate crater or other arena change. Run `/siege admin resetmap` and verify the snapshot restores in place, the banner returns, and scores, currency, kits, and Towny claims are unaffected. Players stay in the world. With a short test reset interval, verify scheduled warnings and no visible full-server pause.
- [ ] Open `/siege shop`. Every bundle appears; successful purchase delivers the correct item bundle and deducts currency, while insufficient currency is rejected cleanly. The TNT-minecart purchase supplies one cart.
- [ ] Put unrelated items in the player's inventory, then run `/siege kit`. The GUI does not open. The old inventory is replaced immediately, slots 0-35 match `kit.default-loadout.slots`, armor slots 36-39 are equipped, and slot 40 is placed in the offhand.
- [ ] Immediately run `/siege kit` again. It must not replace the inventory and must report roughly `5m 0s` remaining. Moving, dropping, or consuming kit items must not bypass the cooldown.
- [ ] Disconnect and reconnect during the five minutes. The same UUID remains blocked for the remaining time. A full server restart intentionally clears this temporary cooldown.
- [ ] Wait five minutes and run `/siege kit` again. It succeeds and starts a fresh cooldown. For faster testing, temporarily lower `kit.command-cooldown-seconds`, restart, and restore it to `300` afterward. Setting it to `0` disables the cooldown.
- [ ] Build the desired kit on an admin by arranging inventory slots, equipping armor, and setting the offhand. Run `/siege admin savekit` without confirmation and verify it only warns. Run `/siege admin savekit confirm`, then use `/siege kit` on another player. The captured snapshot applies immediately without a restart.
- [ ] Change one material, amount, enchantment, potion type, or slot under `kit.default-loadout.slots`, restart, and run `/siege kit` again. The new global snapshot is applied. Respawn and normal siege entry must use the same snapshot. Restore the intended configuration after testing.
- [ ] Put an invalid slot, material, amount, or potion type in the snapshot and restart. SiegePlugin must reject the configuration with a clear startup message instead of silently creating a partial kit.
- [ ] Run `/siege spectate` while eligible. The player becomes an invisible spectator, has inventory stored and cleared, and moves to SpectatorTown. `/siege team` then reports `no team`.
- [ ] Run `/siege rejoin` from SpectatorTown. The player becomes survival, is assigned to the smaller real team, receives their stored inventory, teleports to that team spawn, is visible/damageable, and cannot fly. Running it while not spectating is rejected. Spectating/rejoining must be blocked while combat-tagged or in a capture session where applicable.

## 3. Stage 4.5 TNT-minecart checklist

### Baseline first: determine actual vanilla damage

Do this before judging whether the 0.825 coefficient feels right. Use an untagged cart from `/give`, standardized armor/effects, one fixed impact location, and one fixed fall height. Test the heights players will use on the map, not only the theoretical maximum.

| Test | Setup | Record |
| --- | --- | --- |
| Untagged baseline | Drop or trigger the `/give` cart at each planned tower height. | Fall height, armor/enchantments, effects, distance, health lost, whether it kills, and terrain crater. |
| Tagged comparison | Repeat the exact setup with a cart purchased from `/siege shop`. | The same values, plus the capture-zone headcounts. |

Notes:

- A fall-triggered cart uses fall distance, not its horizontal speed, for its explosion bonus. A normal 20-block drop is not the same as the theoretical maximum-strength explosion.
- Armor, Protection, Blast Protection, Resistance, absorption, shields, distance, and the explosion direction all affect final health. The plugin changes the raw event damage, so equal final-heart loss is not a valid requirement across different equipment setups.
- Minecarts normally detonate when they fall more than three blocks onto non-rail terrain, or when an entity presses into a moving cart. Test the actual delivery method your map supports.

### Shop tag and snapshot guard

- [ ] Buy a TNT minecart from `/siege shop`. It is the tagged Stage 4.5 cart. A cart created with `/give` is untagged and must remain a vanilla control.
- [ ] On a fresh test server with no saved arena snapshot, try placing a shop cart. It is blocked with the message that an administrator must save an arena snapshot. The item is not consumed and no cooldown should start.
- [ ] After the confirmed snapshot sequence above, place the shop cart successfully. Its special rules are now active on the entity.
- [ ] If a tagged cart is broken without exploding and vanilla produces a TNT-minecart item drop, pick it up and place it later. The replacement cart must still behave as tagged. This verifies tag preservation across the item/entity cycle.

### Placement cooldown and bypass

The placement cooldown applies to **every** TNT minecart placement, not only shop carts. The special damage and terrain rules apply only to tagged shop carts.

- [ ] Put TNT minecarts in two hotbar slots. Place one successfully on rail, then immediately try the other. The second placement is cancelled and says, `You must wait before placing another TNT minecart.` The item remains.
- [ ] During the same 30 seconds, move the stack between slots, drop it and pick it up, buy or receive another cart, and try every stack. None can place.
- [ ] Log out and back in before expiry. The visual material cooldown returns and the server still blocks placement for the remaining duration. A full server restart intentionally clears this in-memory cooldown.
- [ ] Attempt a failed placement, such as using the cart where no rail can accept it. It must not start a cooldown; a subsequent valid placement is allowed.
- [ ] Grant the bypass temporarily, then test rapid placements:

  ```mcfunction
  /lp user <player> permission set siege.minecart.cooldown.bypass true
  ```

  The player can place carts without the cooldown. Remove it afterwards:

  ```mcfunction
  /lp user <player> permission unset siege.minecart.cooldown.bypass
  ```

- [ ] The bypass does not override the no-snapshot guard for a shop-tagged cart.
- [ ] There is no per-player or per-team minecart cap. Confirm that a permitted/bypassed player can exceed any old cap value; rate limiting is the only placement control.

### Damage scaling at the capture banner

For every tagged-cart damage test, only count online, alive, non-spectator team members inside the configured capture cylinder: 16 blocks horizontally and at most 16 blocks above or below the banner. The headcount is captured once per explosion, so all victims of that explosion use the same count.

| Victim's team presence at the banner | Expected raw damage from a tagged shop cart |
| --- | --- |
| Victim team is outnumbered | Vanilla raw damage × `0.825`. |
| Victim team leads by 0–6 | Vanilla raw damage × `0.825`. |
| Victim team leads by **7 or more** | Full vanilla raw damage. |

- [ ] Keep armor, effects, fall height, and victim distance identical. Compare a tagged-cart result with the untagged baseline while the victim's team is tied, outnumbered, and leading by fewer than seven. Tagged raw damage should use the configured coefficient.
- [ ] Put seven eligible members of the victim's team and no opposing eligible member in the capture cylinder. The victim belongs to that seven. Trigger a tagged cart and compare it against the untagged baseline: it receives full vanilla raw damage at the exact threshold of seven.
- [ ] Move one of those seven players outside the cylinder, make one a spectator, or move one more than 16 blocks vertically from the banner. The lead becomes six and the tagged damage returns to the coefficient.
- [ ] Put two teammates in the same tagged-cart blast radius. Both take explosion damage. Direct-PvP friendly-fire protection must not suppress TNT-minecart damage.
- [ ] Damage an unteamed player, spectator, or non-player with a tagged cart. The plugin must not apply a team scaling rule to that target. An untagged `/give` cart must never receive custom scaling regardless of team counts.

### Terrain suppression and boundary

The protected terrain is the saved snapshot's full X/Z footprint, at every height. It is not limited to the banner's 16-block capture cylinder.

- [ ] Detonate a tagged shop cart inside the saved snapshot footprint. Nearby blocks inside that X/Z footprint do not break, while players in range still take damage.
- [ ] Repeat the test high above the snapshot's configured Y range, such as on a tower inside the same X/Z bounds. Blocks are still protected.
- [ ] Put equivalent blocks immediately inside and immediately outside the saved footprint's X/Z edge. A tagged explosion preserves the inside block and may destroy the outside block.
- [ ] Detonate an untagged `/give` cart inside the same footprint. It follows vanilla terrain behavior, proving suppression is tied to the shop tag rather than a global explosion cancellation.
- [ ] Save a new snapshot with different corners, then repeat the boundary test. The new saved footprint takes effect immediately for new tagged-cart explosions.

### Stationary-cart cleanup

- [ ] Leave a riderless minecart stationary in `siegeworld`. It remains for the configured five-minute threshold, then is removed on the next 30-second sweeper pass. Expect removal roughly 5–5.5 minutes after its first observed stationary sweep, not exactly at five minutes.
- [ ] Move the cart before expiry. Its stationary age resets and it is not removed based on the old time.
- [ ] Put a passenger in the cart before expiry. Its stationary age is forgotten. When it is riderless and stationary again, a new age begins.
- [ ] Leave it in an unloaded area, then reload it. It starts a fresh observed stationary age rather than being removed immediately from stale tracking.

## 4. End-of-test handoff

For each failure, capture:

1. Exact command or player action.
2. Account name, team, game mode, location, and whether the player was inside the capture cylinder.
3. Cart origin: shop purchase or `/give`.
4. Relevant configuration values and whether the server was restarted after editing them.
5. Chat feedback, console log lines, screenshots/video, and the expected versus actual result.

Restore normal test configuration, revoke temporary bypass permissions, and take a fresh clean snapshot only after confirming the map is in its intended clean state.
