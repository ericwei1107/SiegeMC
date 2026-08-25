# Build Brief: Stage 4.4 — Core Siege Plugin Mechanics

**Audience:** an AI coding agent (e.g. Claude Code) picking up this project with no prior conversation history. Everything you need to build correctly is in this document — read all of it before writing any code.

---

## 1. What this project is

A custom Minecraft (Java Edition, Paper 1.21.11) plugin reviving **SiegeWar**, a banner-capture PvP gamemode popular on Towny servers during COVID and since fallen out of use. The goal is a casual, no-grind, log-on/log-off PvP experience: two fixed teams fight indefinitely over a single central capture point. No town-building, no economy grind, no progression — players log in, get equipped instantly, and fight.

This is a first-time server-hosting and first-time-at-this-scale coding project for the person you're building this for. Write clearly, comment non-obvious logic, and don't assume they'll catch subtle bugs by reading the code themselves — they're relying on you to get it right and to explain what you did.

## 2. Non-negotiable ground rules

**Rule 1 — SiegeWar's source is the source of truth for mechanic behavior, checked at every step, not just once at the start.** Before implementing any mechanic below (control sessions, point accrual, death penalties), read the corresponding file in the real SiegeWar plugin's source (path given in §5) and confirm the exact behavior — timing, thresholds, edge cases — before writing your own version. Implement to match what SiegeWar actually does, not a remembered approximation. This project is a deliberate, faithful port of a working mechanic into a standalone plugin — not a reinvention from a vague idea of "how siege games work."

**Rule 2 — One system at a time.** Build one sub-step from §6 below, get it compiling and working, and verify it against that step's checklist before moving to the next. Do not implement multiple sub-steps in one large change — if something breaks, the person you're building this for needs to be able to tell which change caused it.

**Rule 3 — You do not commit or push. The project owner does.** After each sub-step is built and verified, stop and hand back a **suggested commit message** describing exactly what that increment does (e.g. "Add team switch command with post-move headcount check", not "updates") — specific enough that the commit history reads as a clear log of what happened and why. The project owner will review the change and run `git commit`/`git push` themselves. Do not run these commands on their behalf, even if asked to move quickly — this checkpoint is intentional, not a formality.

**Rule 4 — This plugin is standalone.** It does not depend on Towny or SiegeWar as runtime plugins. Towny is installed and used, but only for its permission/residency system (see §3). SiegeWar and SiegeGame are reference material only, cloned locally, never executed.

**Rule 5 — Don't invent scope.** Build exactly what's specified in §6, in the order given. If something seems missing or ambiguous, flag it rather than guessing a design decision that hasn't been made.

## 3. Project architecture (already decided, do not re-litigate)

- **Two fictitious teams** = two real Towny towns (`teamRed`, `teamBlue`), used purely as a permissions/residency mechanism — not for Towny's economy, claiming, or war features.
- **(Revised) Towny town residency IS team membership — there is no separate internal record.** An earlier draft of this brief specified a second, plugin-owned SQLite table as the "authoritative" team record, with Towny treated as a downstream system to be corrected into agreement. That's been deliberately dropped: **query Towny directly, every time you need to know a player's team.** The project owner is the only admin, town creation/claiming is already permission-locked to them alone, and regular players only ever change residency through this plugin's own commands — so the desync risk that justified a separate record is narrow (essentially: the admin's own account accidentally left resident of the wrong town during manual Towny work), and that risk is accepted as-is rather than engineered around. **Do not build a `team_memberships` table or any parallel membership store.**
- **Persistent state vs. reconstructed state vs. ephemeral state** — this distinction matters throughout:
  - *Persistent* (SQLite, loaded on boot, never auto-reset): team scores, player currency, kit loadouts, current match identifier. (Team membership is **not** in this list — see above; it lives in Towny only.)
  - *Reconstructed at startup*: banner block/entity, boss bars, cached service handles. (Note: an earlier draft of this brief also listed "scoreboards" here as something SiegeWar uses — that was wrong and has been removed. Checked directly against SiegeWar's source: it has no `Scoreboard` usage anywhere. Its actual on-screen display is boss bars only — see the note on 4.4f below.)
  - *Ephemeral*: timers, transient UI, runtime caches. **Confirmed explicitly: the "currently controlling players" set from 4.4f/4.4g belongs in this category.** A server restart resets all in-progress control sessions — players must physically re-enter the capture radius and complete a fresh session before points resume accruing for their team. This is separate from the accumulated team *score*, which is persistent (see 4.4g) and is never affected by this reset. Do not attempt to persist or restore in-progress sessions across a restart.
- **The siege is server-triggered and persistent.** It's live the instant the server finishes booting — no player action starts it. For now it runs indefinitely with no win condition; a points-based round system is a future layer, not part of this build. Represent it as a single open-ended match record (e.g. `eternal-1`) with an ID, status, start time, scores, capture-point ID, and a score-change log — not an unversioned global singleton — so a future round system doesn't require a storage redesign later.
- **Database and network I/O never runs on the server tick thread.** Capture required Bukkit state on the main thread, convert to immutable records, persist asynchronously.

## 4. Current project state — do not redo or contradict this

- **Location:** `~/mcserver/plugin/siegeplugin/`
- **Build tool:** Maven, Java 21, targeting Paper API `1.21.11-R0.1-SNAPSHOT`
- **Group/Artifact:** `groupId: woo`, `artifactId: siegemc`
- **Package:** `woo.siegePlugin`
- **Main class:** `woo.siegePlugin.SiegePlugin` (extends `JavaPlugin`)
- **Plugin loader class:** `woo.siegePlugin.SiegePluginLoader`
- **plugin.yml `name:` field:** `SiegePlugin` — note this differs from the artifact name (`siegemc`) and project folder (`siegeplugin`). This mismatch is known and accepted; **do not rename it** unless explicitly asked. It determines the live data folder: `~/mcserver/dev/plugins/SiegePlugin/`.
- **Git:** already initialized, remote configured, `.gitignore` excludes `.idea/` and `target/`. Continue the existing history — don't re-init.
- **Build output:** the Maven Shade plugin is configured with an explicit `<outputFile>` pointing directly at `~/mcserver/dev/plugins/siegemc-1.0-SNAPSHOT.jar` — the build auto-deploys, no manual copy step needed. Running `mvn clean package` (or IntelliJ's Maven → Lifecycle → package) is sufficient to deploy a new build; just restart the dev server afterward.
- **`onEnable()` already has startup config validation implemented** — it calls `saveDefaultConfig()`, then validates required fields, logging every problem found (not just the first) and calling `disablePlugin(this)` with a `return` if anything's invalid. **Extend this validation as you add config keys in the steps below — don't replace or bypass it.**
- **Current `config.yml` schema — this is what exists today, before your work** (both the source template at `src/main/resources/config.yml` and the live deployed copy at `~/mcserver/dev/plugins/SiegePlugin/config.yml` — these are different files serving different purposes; see note below):
  ```yaml
  teams:
    red:
      town: "teamRed"
    blue:
      town: "teamBlue"

  capture-point:
    world: "siegeworld"
    x: 0
    y: 64
    z: 0
  ```
  Town-name lookups in Towny are case-insensitive (confirmed directly from Towny's source — `TownyUniverse.getTown()` lowercases before matching), so casing of the `town:` values doesn't need to match Towny's stored casing exactly.
  **Important distinction:** `src/main/resources/config.yml` is the packaged default, bundled into the jar — editing it does nothing to the live server until rebuilt and redeployed. `~/mcserver/dev/plugins/SiegePlugin/config.yml` is the live file the running server actually reads, and `saveDefaultConfig()` will never overwrite it once it exists. When you add new config keys for the steps below, add them to **both** files, or the live server won't have them until an admin manually adds them.

- **Target `config.yml` schema — what it should look like when Stage 4.4 is complete.** Every value below has been explicitly decided by the project owner; nothing here is a guess you need to second-guess. Build toward this. Values marked `# FILL IN` are deliberately empty — the project owner will supply them; validate their presence at startup (per 4.4a's config validation, and Rule/4.2's startup validation pattern) and fail with a clear error if they're still empty, rather than running with broken coordinates.
  ```yaml
  teams:
    red:
      town: "teamRed"
      display-name: "Red Team"     # shown on sidebar as "ATK: Red Team"
      color: "RED"                 # standard Minecraft RED
      spawn:
        world: ""                  # FILL IN
        x: 0                       # FILL IN
        y: 0                       # FILL IN
        z: 0                       # FILL IN
    blue:
      town: "teamBlue"
      display-name: "Blue Team"    # shown on sidebar as "DEF: Blue Team"
      color: "BLUE"                # standard Minecraft BLUE
      spawn:
        world: ""                  # FILL IN
        x: 0                       # FILL IN
        y: 0                       # FILL IN
        z: 0                       # FILL IN

  spectator:
    town: "SpectatorTown"

  lobby:
    world: ""                      # FILL IN
    spawn:
      x: 0                         # FILL IN
      y: 0                         # FILL IN
      z: 0                         # FILL IN

  capture-point:
    world: "siegeworld"
    x: 0
    y: 64
    z: 0
    radius: 16                     # source-verified: SiegeWar's default is -1, meaning
                                   # "use one Town Block size" = 16 blocks. Applies BOTH
                                   # horizontally and vertically.
    session-duration-seconds: 420  # source-verified: 7 minutes

  scoring:
    tick-interval-seconds: 20      # source-verified: SiegeWar ticks every 20 seconds
    points-per-controller-per-tick: 10   # source-verified default
    kill-reward-points: 150        # source-verified (source has separate attacker/defender
                                   # values, but BOTH default to 150 — collapsing to one
                                   # shared value matches source defaults exactly)

  activity-cycle:
    enabled: true
    active-duration-seconds: 2700  # 45 min
    break-duration-seconds: 120    # 2 min

  cleanup:
    map-reset-interval-hours: 6
    minecart-stationary-cleanup-seconds: 300   # 5 min — deliberately generous, players
                                               # often park a minecart waiting to strike
    minecart-placement-cooldown-seconds: 30

  sidebar:
    title: "Siege Status"

  currency:
    per-kill: 0                    # PLACEHOLDER — untuned, will be stress-tested
    per-capture-tick: 0            # PLACEHOLDER — untuned, will be stress-tested

  shop:
    # PLACEHOLDER PRICES — all untuned, will be adjusted after playtesting.
    # Structure them so prices are trivially editable without touching code.
    prices:
      building-blocks: 0
      golden-apples: 0
      cobwebs: 0
      enchanted-bow: 0
      arrows: 0
      trident: 0
      rails: 0
      tnt-minecart: 0
  ```

- **Live server environment already set up:**
  - Paper 1.21.11, Java 21
  - Installed plugins: LuckPerms, Vault, Towny (0.103.2.0), TownyChat, Multiverse-Core
  - Two Towny towns exist: `teamRed`, `teamBlue`, each with an NPC mayor (`/ta set mayor <town> npc`), grouped into one shared nation (to bypass Towny's homeblock-distance rule between them), with permissions set per town: `build` off, `destroy` off (all levels), `switch` on for `resident` only.
  - `towny.command.town.new` and `towny.command.town.claim.*` are denied to the LuckPerms `default` group (only admins can create towns/claim land).
  - A world named `siegeworld` exists via Multiverse-Core.
  - `pvp` gamerule is `true` (note: `pvp` is a gamerule as of Minecraft 1.21.2+, not a `server.properties` key).

## 5. Reference materials

| What | Path/Link | Use it for |
|---|---|---|
| **SiegeWar source** (source of truth for mechanics) | `~/mcserver/reference/SiegeWar/` | Banner control session logic (`SiegeWarBannerControlUtil.java`), point accrual/tick logic, death penalty logic, config defaults (`ConfigNodes.java`, `SiegeWarSettings.java`) |
| **Towny source** | `~/mcserver/reference/Towny/` | `TownyAPI`/`TownyUniverse` for resident/town lookups, permission node names, event classes |
| **SiegeGame source** (architecture reference ONLY) | `~/mcserver/reference/SiegeGame/` | Kit system structure, shop GUI structure, match lifecycle patterns. **Do not reference this for capture-mechanic behavior — it doesn't implement one.** It's a kill-count team deathmatch with a war-themed skin, not a SiegeWar clone. |
| TownyAPI developer guide | https://github.com/TownyAdvanced/Towny/wiki/TownyAPI | Resident/town API usage |
| Paper plugin dev docs | https://docs.papermc.io/paper/dev/ | General Paper API patterns |
| Paper API javadocs | https://jd.papermc.io | Exact method signatures |

## 6. Build order — Stage 4.4a through 4.4l, plus 4.4d.1, 4.4d.2, 4.4h.1, and 4.4i.1

Build in this exact order. Each step lists: what it does, what to check against SiegeWar's source (if applicable), what it touches, and how to verify it's actually working before moving on. **4.4d.1** (relative team-color display), **4.4d.2** (persistent sidebar), **4.4h.1** (periodic activity cycle), and **4.4i.1** (player-placed blocks stay breakable) are project-owner-agreed additions inserted between existing steps — not part of the original numbered sequence, but build them in the positions given. **4.4l** now covers two commands, `/siege spectate` and `/siege rejoin`, not just one.

---

### 4.4a — Towny adapter (team queries)

**What:** A thin integration layer that's the *only* place in the codebase allowed to call Towny's API directly. **(Revised — no internal team repository.)** Team membership lives in Towny alone; this layer just gives the rest of the plugin a clean way to ask "what team is this player on" without every other class needing to know Towny's API directly.

**Build:**
- A `TownyAdapter` class (or similar) containing every Towny-specific call — resident lookup, `setTown()`, permission checks.
- A `Team` enum — `RED`, `BLUE` — mapped to the config's `teams.red.town` / `teams.blue.town` values.
- A method like `getPlayerTeam(Player)` that checks the player's current Towny resident/town via `TownyAPI`/`TownyUniverse`, and returns `RED`, `BLUE`, or "no team" (e.g. `Optional<Team>` or `null`) based on which of the two configured towns they're currently resident of.
- On plugin startup: verify Towny is present and both configured towns (`teams.red.town`, `teams.blue.town`) actually exist. If either is missing, fail the same way the existing config validation does — log the specific problem and disable the plugin. Add this as a **new check appended to the existing `validateConfig()` method** (or a parallel startup check run right after it) — don't build a second, separate failure path.
- Declare Towny as a hard dependency in `plugin.yml` (`depend: [Towny]`) so load order is guaranteed.
- A command to check your own current team (e.g. `/siege team`), calling `getPlayerTeam()` — this is a direct live Towny query every time, not a cached/stored value.

**Check against source:** Towny's `TownyAPI.getTown(String)` / `TownyUniverse.getTown(String)` for lookup patterns; the `Resident.setTown(Town)` + `save()` pattern for force-joining (already discussed and confirmed correct earlier in this project — no invite step, direct assignment).

---

### 4.4c — Team assignment on join

**What:** New players get auto-assigned to whichever team currently has fewer players.

**Build:**
- On `PlayerJoinEvent`: check via the Towny adapter (4.4a) whether the player is already resident of either configured town. If not (first-ever join), count current residents of each town directly through Towny's API, assign to the smaller one. **On a tie, always assign to RED** (project owner decision — chosen for predictability, not balance). Then force-join them via `setTown()` + `save()`.
- Returning players (already resident of one of the two towns) need no action on join — Towny already reflects their team.

---

### 4.4d — Team switch command, hardened

**What:** A command letting a player switch teams, with balance protection.

**Build:**
- Command (e.g. `/siege switch <team>`).
- **Post-move headcount check, not pre-move:** compute what both teams' sizes would be *after* the hypothetical move (counting directly from Towny residency), and block if the destination team would then outnumber the player's new team by 2 or more. (This is a deliberate fix to a specific bug: checking pre-move sizes allows a switch that still leaves the destination ahead, since one player moving changes both counts simultaneously.)
- Reject if the player is currently combat-tagged (check whatever combat-tag plugin/state is active on the server — Simple Combat Log is installed; check its API or a shared tag-state check).
- Reject if the player is currently an active participant in a capture session (this depends on 4.4f existing — if built before 4.4f, stub this check and revisit once capture sessions exist).
- On success: call the Towny adapter's `setTown()` + `save()` directly — **there is no second record to keep in sync**, so no atomicity concern here beyond normal Towny error handling (if the Towny call throws, just surface the failure to the player and don't proceed with the rest of the switch — teleport, etc.).
- Teleport the player to the destination team's spawn point (add a config key for each team's spawn coordinates if one doesn't exist yet).
- Clear any team-specific temporary state (kit-in-progress, etc. — coordinate with whatever 4.4e defines).
- **(Project owner decision) The player KEEPS their stored inventory across a team switch** — switching sides does not cost them their gear or reset them to a base kit. Do not clear or reset stored inventory here.
- Log every switch (player, from-team, to-team, timestamp) — a simple log line is sufficient, doesn't need its own database table.
- **(Revised) 15-minute cooldown per team switch.** A player who switches teams cannot switch again until 15 minutes have passed. Track the last-switch timestamp per player — this is small enough state that an in-memory map (player UUID → timestamp) is fine; it doesn't need its own SQLite table, and doesn't need to survive a restart (a restart resetting everyone's cooldown is an acceptable, low-stakes edge case, not worth persisting for). Reject a switch attempt during the cooldown with a clear message stating how much time remains.

---

### 4.4d.1 — Relative team-color display (tab list + nametags) — new addition, not from SiegeWar

**What:** From each player's own perspective, teammates appear green and members of the other team appear red — in both the Tab player list and the nametag shown above each player's head. Confirmed with the project owner: this uses the **same relative scheme in both places**, not an absolute per-team color for tab and a relative one for nametags. This is a genuinely new addition layered on top of the ported mechanic, not something to check against SiegeWar's source — already confirmed earlier in this project that SiegeWar has zero `Scoreboard` usage of any kind.

**Why this needs a specific technique, not just "set a team color":** the coloring must be *relative* — the same player needs to appear green to their own teammates and red to the enemy team simultaneously. Bukkit's shared main scoreboard can't do this on its own, because a `Team`'s color is one fixed value, the same for every viewer, and each player can only belong to one team within a given scoreboard. Concretely: if PlayerA and PlayerC are both really on `teamRed`, and everyone shares one scoreboard, you can only pick one global color for the `teamRed` group — every viewer sees it the same way, including PlayerA looking at their own teammate PlayerC. There's no way to make PlayerC show green to PlayerA while simultaneously showing red to a `teamBlue` viewer using one shared team assignment. The standard way to get genuinely per-viewer-relative coloring, without needing any packet library, is to give **each player their own personal `Scoreboard` instance** rather than everyone sharing the default one — a long-established, foundational part of the Bukkit API (`ScoreboardManager.getNewScoreboard()` + assigning it per-player), not something new to this Minecraft version. **Before writing this, look up the exact current signatures on Paper's javadocs (`jd.papermc.io`, 1.21.11)** for `ScoreboardManager`, `Scoreboard`, `Team`, `Team.Option`, and `Team.OptionStatus`. This is a lookup task, not an open design question — the approach is settled, only the exact API surface needs confirming. Specifically watch for the color API: Bukkit's legacy `ChatColor` and Paper's Adventure `NamedTextColor` both exist and are used in different methods, so check which one each setter actually expects rather than assuming they're interchangeable.

**Build:**
- On player join (and once for anyone already online when the plugin starts): give the player their own personal scoreboard, separate from the shared main one.
- Within each player's personal scoreboard, create exactly two teams — e.g. `"friendly"` (green) and `"enemy"` (red).
- Populate every currently-online player (including the viewer themselves, categorized as `"friendly"`) as an entry in the correct team, based on whether their team (via 4.4a's `getPlayerTeam()`) matches the viewer's own team.
- **Rebuild triggers — this needs to stay correct as state changes, not just be set once:**
  - **Player joins:** build their personal scoreboard from everyone currently online; also add the new player as an entry into every other online player's existing personal scoreboard, correctly categorized from each viewer's own perspective.
  - **Player quits:** remove their entry from every other online player's personal scoreboard.
  - **Player switches teams (4.4d) — the case most likely to be gotten wrong, two things must happen, not one:** (1) the switching player's *own* personal scoreboard must be entirely rebuilt, since every other player's relative categorization from their perspective just flipped; (2) *every other online player's* personal scoreboard must have the switcher's single entry moved from one team to the other. Missing either half leaves stale/wrong coloring for someone.
  - **Players with no team** (mid-spectate, or not yet assigned) — **(revised)** as a *viewer*, they see everyone using **absolute per-team color**, not the relative friend/foe scheme — there's no "friendly" side for them to be relative to, so relative coloring doesn't apply. Concretely: for a no-team viewer's personal scoreboard, instead of building `"friendly"`/`"enemy"` teams, build two teams representing each side's own fixed identity color (e.g. `"display-red"` colored to match `teamRed`'s own designated color, `"display-blue"` colored to match `teamBlue`'s) and populate each player straight from `getPlayerTeam()` — `teamRed` players into `"display-red"`, `teamBlue` players into `"display-blue"` — with no comparison to the viewer's own team, since they don't have one.
    - This needs each team's own absolute identity color defined somewhere — add it to the existing config schema (§4) rather than hardcoding it in Java:
      ```yaml
      teams:
        red:
          town: "teamRed"
          color: "RED"
        blue:
          town: "teamBlue"
          color: "BLUE"
      ```
      Sensible defaults if omitted (`RED` for the red slot, `BLUE` or `AQUA` for the blue slot) are fine, but don't hardcode without the config option existing.
    - **How a no-team player appears to others — resolved, and it needs no color at all.** Project owner decision: spectators use Minecraft's built-in `GameMode.SPECTATOR`, which already makes them **invisible to other players** — there is no nametag rendered for a normal player to see, so there is nothing to color. Do not build a third "neutral" display team for this. The only no-team case that could theoretically be visible to others is a player who has somehow not been assigned a team yet while still in survival mode — if that state is even reachable given 4.4c auto-assigns on join, treat it as an edge case not worth special handling.

**Team behavior beyond color (build these now — not just display, real gameplay decisions):**
- **Friendly fire: disabled for direct PvP — TNT minecarts are unaffected by this setting, by default, with nothing extra to build.** Call `Team.setAllowFriendlyFire(false)` on both teams' `"friendly"` team object (i.e. set on the team representing "my own side" from each viewer's scoreboard — since friendly fire is evaluated based on team co-membership, this needs to be set consistently across every player's personal scoreboard, not just one). This stops teammates from damaging each other with direct attacks — melee, arrows, tridents. **TNT minecart explosions always damage every player in range regardless of team, teammates included — this matches long-standing Towny-server convention, and it requires no special-case code.** In Bukkit's `EntityDamageByEntityEvent`, the friendly-fire check compares the *damager* against the victim's team; for a direct hit the damager resolves to the attacking `Player`, but for an explosion it resolves to the TNT entity itself, never to a `Player`. Since it's never resolved to a player for that comparison, the friendly-fire flag has nothing to check against and simply doesn't apply to explosion damage — this is default behavior, not something to implement. Do not write any code that filters TNT minecart damage by team membership; 4.5's existing damage-scaling formula (which operates on raw headcounts, with no notion of "same team as the placer") already produces the correct outcome automatically. Confirm this holds empirically once both steps exist, rather than assuming — see item 4.4d.1 in the Verification Checklist (Section 9).
- **Collision: disabled.** Call `Team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)` (confirm exact enum names against current Paper javadocs) on the same team objects. Prevents teammates from physically blocking each other's movement — relevant specifically at a contested capture point where your own side clusters together and pathing around each other would otherwise be a constant annoyance. Same rationale: reduces friction, not a ported mechanic.
- Both of these need to be set on **every player's personal scoreboard**, applied to whichever team object represents "my side" from that viewer's perspective — build this into the same rebuild logic already required for team coloring (join/quit/switch), not as a separate one-time setup step, since a newly-created personal scoreboard needs both settings applied immediately, not just team membership and color.

### 4.4d.2 — Persistent sidebar score display (spec confirmed against a reference image)

**What:** an always-visible sidebar, built from a reference image the project owner provided — the image itself turned out to be Towny's `TownStatusScreenEvent` output (a command-triggered chat block SiegeWar hooks into), **not** a persistent sidebar, so the exact source layout isn't being copied verbatim — the project owner explicitly chose a real persistent sidebar despite that, adapting the reference image's content to it. **Must be built into the same per-player `Scoreboard` object created in 4.4d.1**, not a second one — a player can only have one active `Scoreboard` at a time, and since 4.4d.1 already assigns everyone a personal board for team coloring, a sidebar `Objective` on the shared main scoreboard would be invisible to them. Unlike 4.4d.1's team coloring, this content is **identical for every viewer** — no per-viewer categorization needed, just write the same computed values into every online player's personal `Objective`.

**Line-by-line content, top to bottom:**
1. **Title** — `sidebar.title` config key, value `"Siege Status"` (project owner decided).
2. **`ATK: Red Team`** — the literal label `ATK: ` followed by `teams.red.display-name` ("Red Team"), with the team name rendered in `teams.red.color` (standard Minecraft `RED`). Hardcoded mapping: ATK always means the red team. This project has no attacker/defender logic (no Towny war) — the label is kept from source purely for familiarity, not because it means anything mechanically here.
3. **`DEF: Blue Team`** — same structure: `DEF: ` + `teams.blue.display-name` ("Blue Team") in `teams.blue.color` (standard Minecraft `BLUE`). Use plain, conventional red/blue shades — no special palette needed.
4. **Red points: `<red team's real persistent score>`** — pulled directly from 4.4g's stored score. Split from the source's single net "Siege Balance" value into two independent numbers, since this project's scoring model is two separate cumulative totals, not a net balance.
5. **Blue points: `<blue team's real persistent score>`**
6. **Banner Control: `<controlling side> (<count>)`** — confirmed from source (`SiegeWarStatusScreenListener.java` line 403): the side currently holding banner control, and `siege.getBannerControllingResidents().size()` — the exact same "currently controlling players" count already tracked in 4.4f/4.4g. **Count-only, no player name list** — confirmed as a deliberate choice by the project owner, not an oversight from how the reference screenshot was cropped. Source does list names; this project intentionally does not, since the list could get very long with 15+ controllers on a single sidebar line.
7. **ATK BAT Points: `<red team's session points>`** — **new tracked value, not the same as line 4.** See below.
8. **DEF BAT Points: `<blue team's session points>`**
9. **BAT Time Left: `<remaining time in current cycle phase>`** — maps directly onto 4.4h.1's countdown. During `ACTIVE`, shows time remaining until `BREAK`. During `BREAK`, shows time remaining until `ACTIVE` resumes (same field, whichever countdown is currently running).

**New requirement — session points (lines 7–8), separate from the real score:**
- Add a second, lightweight per-team counter — "points accrued during the current `ACTIVE` window" — distinct from 4.4g's real persistent score.
- **Reset to zero** every time a new `ACTIVE` window begins (on the `BREAK → ACTIVE` transition from 4.4h.1, and once at server boot for the first window).
- **Increments alongside** the real score during every point-accrual tick while `ACTIVE` — same tick, same trigger, just also adding to this second counter.
- **Frozen (not reset, not incremented) during `BREAK`** — still displayed at its final value from the just-ended session, so players can see "how that session went" until the next one starts.
- **This is purely a display value.** It does not get reconciled into, subtracted from, or otherwise combined with the real persistent score at any point — that would reintroduce the two-layer battle-points/balance system already deliberately rejected in 4.4h.1. It exists only to answer "how is this session going," side by side with the real running total.
- No `+`/`−` sign prefix on these numbers, unlike the source image — that framing implied a net balance transfer, which doesn't apply here since there's no reconcile step.

**Build:**
- Add a `Scoreboard.Objective` (display slot: sidebar) to each player's personal scoreboard from 4.4d.1, alongside the existing team objects.
- A single update routine computes all 9 lines once (since content is identical for every viewer) and writes them into every online player's personal `Objective` — no per-viewer branching needed here, unlike 4.4d.1's coloring.
- Trigger updates whenever any displayed value changes: score ticks (4.4g), session-point ticks, banner control set changes (4.4f), cycle phase transitions (4.4h.1), and on player join (to populate their new board immediately, not wait for the next tick).
- Team display names and identity colors read from the config additions already specified in 4.4d.1 (`teams.red.color` / `teams.blue.color`).

---

### 4.4e — Lobby, siege, and spectator state transitions

**What:** Explicit, defined behavior for every point a player's inventory/state changes context. Build this before kits/shop (4.4j/4.4k) — those systems assume these transitions are already defined.

**Build, as explicit event handlers, one per transition:**
- **Lobby → siege entry:** what happens to their inventory (should be their kit loadout — coordinate with 4.4k once it exists; for now, a placeholder/empty kit is fine if 4.4k isn't built yet).
- **Siege → lobby return:** the player's siege inventory is **stored** (saved) and **cleared** from what they're carrying — it does not visibly carry into the lobby with them. **Lobby → siege re-entry (resolved):** the player gets back exactly the inventory that was stored from their previous session — not a fresh reapplication of their 4.4k kit loadout. This matters specifically because it means loose shop-purchased items picked up mid-session (which aren't necessarily part of the saved kit template) persist across a lobby trip, not just the base kit. This storage needs to be per-player and durable across a normal session (SQLite, not just an in-memory map — a server restart or a player disconnecting between sessions shouldn't lose it). A brand-new player with no prior stored inventory falls back to their 4.4k kit loadout on first siege entry, since there's nothing stored yet to restore.
- **Death/respawn:** vanilla respawn timing (already decided) — on respawn, kit reapplies.
- **Spectator entry/exit:** entering spectator mode should also trigger the "removal from town" behavior (this connects to 4.4l — build the plumbing now, wire up the actual command in 4.4l).
- **Disconnect mid-kit-edit:** if a kit-editing UI is open when a player disconnects (again, coordinate with 4.4k — if it doesn't exist yet, note this as a TODO to revisit), ensure no item duplication or loss on rejoin.
- **Server restart while a player holds shop-purchased items:** items should persist in their inventory across a restart like any normal Minecraft item — verify this isn't accidentally broken by anything else being built (e.g. don't clear inventories on plugin disable).

---

### 4.4f — Capture point and control sessions

**This is a core mechanic step — read SiegeWar's source before writing any code here.**

**Check against source first:** `~/mcserver/reference/SiegeWar/.../utils/SiegeWarBannerControlUtil.java` and `ConfigNodes.java`/`SiegeWarSettings.java` for the exact banner control session logic — radius handling, session start/duration, what happens if a player leaves the radius mid-session.

**Build:**
- Capture point location read from config (`capture-point.world/x/y/z`, already present).
- Admin command to relocate the banner for testing (e.g. `/siege admin setbanner`, using the admin's current location).
- A radius check using the `capture-point.radius` config key (**16 blocks**). Source-verified: SiegeWar's `banner_control_session_radius_blocks` defaults to `-1`, which its own config comment defines as "use the size of a Town Block" — i.e. 16. Source also specifies this radius **applies both horizontally AND vertically** (a player must be within 16 on Y as well as within 16 horizontally) — implement it as a full 3D distance check, not horizontal-only. Note this differs from the siege-zone radius (200 blocks, horizontal-only) referenced elsewhere in source; they are genuinely different checks, don't conflate them.
- Per-player control session: when a player enters the radius, start a timer, duration from `capture-point.session-duration-seconds` (**420 seconds / 7 minutes**, source-verified). Don't hardcode it.
- **If a player leaves the radius before their session completes, the session RESETS to zero — it does not pause.** Confirmed from source (`SiegeWarBannerControlUtil.java`): when a player stops meeting session requirements, `removeBannerControlSession()` is called and a failure message is sent — progress is discarded entirely. The project owner independently chose reset for design reasons (it punishes disengaging and discourages players from repeatedly ducking out of a fight to preserve progress), so this matches both source behavior and intended design. Re-entering the radius starts a completely fresh session.
- On session completion, the player is added to the "controlling" set for their team.
- Display session progress via a boss bar (Paper API: `BossBar` class). **Exactly one boss bar per player — the capture session countdown, and nothing else.** Source (`BossBarUtil.java`) has a second boss bar for its battle-session timer; **do not replicate that** — this project's equivalent (the 4.4h.1 activity cycle countdown) is already shown on the sidebar as "BAT Time Left", and a second boss bar would be redundant clutter (project owner decision). Source also lets players individually disable their boss bars via a per-resident toggle — **skip that too**, explicitly deferred as unnecessary scope for now.

---

### 4.4g — Persistent scores and restart recovery

**Check against source:** SiegeWar's point-accrual tick — confirmed elsewhere in this project to be a recurring tick (SiegeWar uses a 20-second tick) awarding points scaled by the number of controlling players: `points = controllingPlayers.size() * basePointsPerTick`. Confirm the exact tick interval and formula directly from `SiegeWarBannerControlUtil.java` / `SiegeWarSettings.java` before implementing — don't rely on this summary alone.

**Build:**
- A recurring scheduled task (Paper's `BukkitScheduler` or the newer region-scheduler-aware APIs) that, on each tick, checks the controlling set from 4.4f and awards points to that team, scaled by headcount, using SiegeWar's actual formula.
- Scores persist in SQLite (§7) as part of the match record (`matches` table — `match_id`, `status`, `start_time`, `red_score`, `blue_score`, or a normalized `team_scores` table keyed by match+team).
- **Scores load from the database on boot and are never zeroed automatically** — this is a direct fix for a known design contradiction (the original plan said scores accumulate indefinitely, but an earlier build order would have reconstructed them from scratch on every restart). Confirm on restart testing that this actually holds.
- A score-change log/ledger — even a simple append-only table (`score_ledger`: `id`, `match_id`, `team`, `delta`, `reason`, `timestamp`) — recording every point change with a reason string ("banner_control_tick", "death_penalty", etc.). This supports 4.4h and any future auditing.
- An admin command `/siege admin resetscores`, gated by a distinct permission node (e.g. `siege.admin.resetscores`), requiring a confirmation step (e.g. running the command once shows a warning and requires a second confirming argument or a follow-up `/siege admin resetscores confirm`) — this should be the *only* way scores ever reach zero after the match starts.
- Represent the ongoing siege as a single match record with a stable ID (e.g. `eternal-1`), status field, and start time — not an unversioned global singleton — so a future round system can close this match and open new ones without a schema change.

---

### 4.4h — Kill reward (renamed from "death penalty")

**Check against source, then deliberately diverge from it:** confirmed from `SiegeWarScoringUtil.awardPenaltyPoints()` — the method's own comment states *"Give battle points to opposing side."* The code calls `siege.adjustDefenderBattlePoints(...)` when an attacker dies, and `siege.adjustAttackerBattlePoints(...)` when a defender dies — **the opposing (killer's) team is credited**, not the dying player's own team debited. In SiegeWar's net-balance model those two framings are equivalent, but they are **not** equivalent in this project's two-independent-scores model — crediting the killer's team is the only version consistent with the already-established invariant (Section 9, 4.4g) that team scores only ever increase, never decrease, except via the admin resetscores command. Renamed this step from "death penalty" to "kill reward" to reflect the corrected mechanic.

**Deliberate deviation from source (project owner decision):** real SiegeWar only awards this within a wide "siege zone" radius (`isInSiegeZone()` / `getWarSiegeZoneRadiusBlocks()`) around the capture point. **This project drops that restriction entirely** — any kill anywhere on the server counts, not just kills near the siege. Simpler to build (no radius check, no new config key needed) and makes every kill server-wide rewarding rather than only combat near the banner. Do not add a zone/radius gate here.

**Confirmed, not revisited (project owner reviewed the source-verified "credit to killer's team" model against a straight deduction and chose to keep this one — may reconsider later, not a mistake to fix):**

**One rule — no killer-tracking needed at all, this matches source more closely than an earlier draft of this brief implied:** re-checked directly against `PlayerDeath.java` — real SiegeWar's points logic never checks whether a real killer exists. The `getPlayerKiller()` lookup in source is used only to build the death *notification message*, not to gate whether points are awarded. The actual rule is simpler: **any qualifying death credits the dead player's opposing team, full stop** — the cause doesn't matter.
- **Environmental death (fall, drowning, lava, etc.):** still credits the opposing team, exactly like a PvP kill. An earlier draft of this brief said "no killer = no reward" — that was an invented simplification, not source behavior, and it's now corrected: there is no killer check at all.
- **Friendly-fire TNT minecart kill:** the opposing team is credited exactly the same as any other death — even though the actual cause was a teammate's minecart. Not a special case; it's the same one rule applied consistently. (If this specific outcome — a team scoring off its own teammate's death — ever feels wrong in practice, that's worth revisiting alongside the credit-vs-deduction question generally, not something to patch in isolation now.)

**Build:**
- On `PlayerDeathEvent`, determine the dying player's team via 4.4a's `getPlayerTeam()`.
- If they have a team, credit the *other* team's persistent score by `scoring.kill-reward-points` (**150**, source-verified). **(Project owner decision) Use one shared value for both teams, not two.** Source technically has separate attacker-death and defender-death settings — but both default to 150 in source anyway, and this project's ATK/DEF labels are purely cosmetic (there is no real attacker/defender asymmetry here), so a single shared value matches source's actual defaults exactly while being simpler. Do not build two separate configurable numbers.
- **No killer check** — fires on any qualifying death, environmental or PvP, matching source exactly (see above). This is a team-level credit, not tied to a specific killer's identity, so there's nothing to check for.
- No radius/location check of any kind — this fires the same way regardless of where on the map the death happened.
- **(Simplified, per project owner decision) This lives in the same single death handler as 4.4j's kill-currency award, not a separate listener.** 4.4h's team credit (here) fires unconditionally on any qualifying death — no killer needed. 4.4j's currency check happens in that same handler, gated separately on whether a valid killer exists. See 4.4j for the full combined-handler structure; this section just defines the team-credit half of it.
- Record this in the score ledger from 4.4g with a clear reason string — e.g. `"enemy_death_bonus"` — describing what actually happened to the score (an addition to the killer's side), not source's internal naming convention.

---

### 4.4h.1 — Periodic activity cycle (agreed addition — not in the original numbered list, inserted here to avoid renumbering 4.4i–4.4l)

**What:** a scheduled `ACTIVE`/`BREAK` cycle, added on top of the continuous single-score model from 4.4g — **not** SiegeWar's full battle-points/siege-balance reconciliation system. This was a deliberate project-owner decision: SiegeWar's real battle-session model (confirmed from `BattleSession.java`) uses a two-layer score (temporary "battle points" during an active window, reconciled into a persistent "siege balance" only when the session ends, with all banner control wiped and a ~10-minute total blackout between sessions) — that full model was explicitly **not** adopted here, since it conflicts with this project's continuous, no-grind, always-live design goal. What follows is a lighter, independent addition instead.

**Build:**
- New config block:
  ```yaml
  activity-cycle:
    enabled: true
    active-duration-seconds: 2700   # 45 min — arbitrary default, not from SiegeWar's numbers, tune to taste
    break-duration-seconds: 120     # 2 min — deliberately much shorter than SiegeWar's 10-minute default
  ```
- A scheduled task cycling between `ACTIVE` and `BREAK` states based on the durations above. This state is **ephemeral** (see §3's state categories) — on server boot, always start fresh in `ACTIVE`, don't attempt to persist or resume wherever the cycle left off before a restart.
- **On `ACTIVE → BREAK`:** cancel every in-progress capture session, reusing the exact same session-reset logic 4.4f already needs for server restarts (same underlying behavior, now also triggered on this schedule). The 4.4g point-accrual tick should skip awarding entirely while in `BREAK` — it keeps running, it just does nothing; **no score is reset, reconciled, or otherwise touched**. New capture sessions must be prevented from starting while in `BREAK` (a player standing in the radius simply doesn't start a timer until the state flips back). Broadcast a short server-wide message announcing the break.
- **On `BREAK → ACTIVE`:** broadcast a resuming message; capture sessions and point ticks resume normally.
- **PvP itself is never restricted by this cycle** — only the banner-control/scoring mechanic pauses. Players can keep fighting through a break; they just can't make capture progress during it. This is a deliberate deviation from SiegeWar's real behavior, matching this project's "always-live PvP" priority.
- **Not replicated:** SiegeWar's chat-locking during battle-end (`chatDisabled`/`scheduledGeneralChatRestorationTime` in the source) — unnecessary complexity for what this feature is meant to solve here.
- **Admin override commands:** `/siege admin break [seconds]` (force an early break, optionally with a custom duration) and `/siege admin resume` (end a break early) — gives the project owner a real, on-demand announcement/maintenance window, not just the automatic scheduled one.

---

### 4.4i — Battlefield cleanup (build before the shop)

**Why this order:** an endless siege combined with a shop selling placeable blocks (4.4j) and no round reset would otherwise let the arena degrade permanently. This must exist before 4.4j is usable in practice, even though it doesn't strictly depend on 4.4j's code.

**Revised approach (simpler, per project owner decision): reset in-place, no world swap.** Rather than unloading and replacing the whole world file (which would require relocating every player first), restore just the arena's block region from a saved snapshot while the siege continues running underneath it — no lobby teleport, no downtime, consistent with this project's "always-live, no forced interruption" design throughout.

**Build:**
- Config key: `cleanup.map-reset-interval-hours`, default `6`.
- A scheduled task tracks elapsed time since the last reset and triggers a full reset when the interval elapses. Like the `ACTIVE`/`BREAK` cycle in 4.4h.1, this timer is **ephemeral** — don't persist elapsed time across a restart; just start the 6-hour countdown fresh every time the server boots, consistent with how every other timer in this project is handled.
- **The saved clean copy is the same backup already established in the project's build plan (Stage 3.9 of `../../../Downloads/MAIN_PLAN.md`)** — the finished map, bases and capture point included, backed up before players ever touched it. Restoring from this means the gank bases and banner location come back exactly as originally built, not as some arbitrary "clean" state you'd need to define separately.
- **Warn players before resetting**, similar in spirit to the item-clear plugin's warning but longer, given this is a noticeable disruption — e.g. broadcasts at 5 minutes, 1 minute, and 10 seconds before the reset actually happens.
- **The actual restoration mechanism:** iterate through the arena's saved bounding box and set each block back to its snapshot state — using either Minecraft's native structure/schematic tooling or a WorldEdit-style region copy, your choice, document whichever is used. **Spread this across multiple ticks, not all at once** — restoring a large region in a single tick is real, blocking CPU work and will cause a visible stutter; process it in batches (a few hundred/thousand blocks per tick) instead.
- **Players stay in the world throughout — no relocation needed.** This is a deliberate, accepted tradeoff (project owner decision): because 3.6a's wilderness `build=true`/`destroy=false` setting already prevents players from digging into natural terrain, the only thing a reset ever actually needs to fix is TNT-explosion craters — a genuinely rare occurrence, not routine player digging. A player standing exactly where a block is being restored is an accepted, self-resolving edge case (they may briefly end up inside a restored block; they move, it resolves) — not something to build detection/avoidance logic for.
- **A capture session in progress when the reset fires is treated as an interruption, same as any other** (leaving the radius, a server restart, etc.) — it simply ends; the player would need to start a fresh session afterward. No special-case handling needed here.
- **After the reset, the banner needs to exist correctly again** — this doesn't need new code: it's the same "reconstructed at startup" logic already specified for the banner in §3/4.4f. Just re-trigger that same check after a reset completes, the same as it runs on a normal server boot.
- **Interaction with 4.4i.1 (player-placed blocks):** an in-place reset should also clear the player-placed-block tracking set from 4.4i.1 — any block that existed in that tracked set either gets restored to the snapshot (if it falls inside the reset region) or should simply be dropped from tracking regardless, since the reset is the natural point where "everything placed since last reset" goes away.
- **Unaffected by this reset, worth confirming explicitly rather than assuming:** team scores (4.4g), currency (4.4j), kit loadouts (4.4k), and Towny town/claim data are all stored separately from the physical world files (SQLite or Towny's own data, not the Minecraft world) — a reset touches blocks only, none of that persistent player data.
- **TNT-minecart population control — cooldown, NOT a cap (project owner decision, reverses an earlier draft):**
  - **Do not build a per-player or per-team minecart cap.** An earlier version of this brief specified `minecart-cap.per-player` / `minecart-cap.per-team` config keys — those are explicitly cancelled. No cap of any kind.
  - **Instead, rate-limit placement with a 30-second cooldown per player.** This is the same `player.setCooldown(Material.TNT_MINECART, ...)` mechanism already specified in Stage 4.5 — set it to 30 seconds (600 ticks), from the `cleanup.minecart-placement-cooldown-seconds` config key. **This is not a second, separate system** — it's the existing 4.5 cooldown with a decided value. Don't implement it twice.
  - **Why a cooldown instead of a cap:** a cap creates a frustrating failure mode where a player is blocked because *other people* used up the team's allowance — punishing someone for their teammates' actions. A cooldown limits each player's own rate independently, achieves the same anti-spam/performance goal, and is simpler (no counting live entities at placement time).
  - **Stationary-minecart cleanup, still needed and still separate from the cooldown:** a minecart can be placed and then simply never detonate — rolled somewhere out of the way, or sitting in an unloaded chunk. Every live entity costs tick time, so sweep these periodically: remove any minecart that has been stationary and unattended for longer than `cleanup.minecart-stationary-cleanup-seconds` (**300 seconds / 5 minutes**). This threshold is deliberately generous rather than aggressive — project owner's reasoning: players routinely park a minecart and wait for the right moment to push it, and sweeping too eagerly would break a legitimate tactic.
  - With the cap gone, this cleanup exists purely for **performance**, not to free up cap space — the rationale changed along with the mechanism.
- Keep the admin command to trigger a reset manually on demand (e.g. `/siege admin resetmap`), separate from the automatic 6-hour schedule — useful if the arena gets into a bad state before the timer would naturally fire.

---

### 4.4i.1 — Player-placed blocks stay breakable (new — closes a real gap, not part of the original numbered list)

**The problem this fixes:** Stage 3.6a of `../../../Downloads/MAIN_PLAN.md` sets wilderness `destroy=false` (with `build=true`) in the open battlefield, so natural terrain can't be dug into. But Towny's `destroy` permission is a blanket rule — it can't distinguish natural terrain from something a player placed ten seconds ago. As specified, that means shop-purchased cover (4.4j) becomes permanently indestructible the moment it's placed — not even the person who placed it can undo it, and nobody can ever break through someone else's cover until the next 6-hour map reset. **This step fixes that:** track which blocks were player-placed, and allow breaking only those, while natural terrain stays protected underneath.

**This is not the TTL/expiry system that was cut earlier** — no timer, no expiry, no automatic removal. It's a much simpler permission override: "was this specific block placed by a player? If so, let it be broken."

**Build:**
- A `BlockPlaceEvent` listener records every block placed in the open battlefield (outside the two gank bases, which already have their own separate protection via Towny town permissions from 3.6 and don't need this) into a tracked set — in-memory is fine, e.g. a `Set<Location>` or similar, doesn't need SQLite.
- A `BlockBreakEvent` listener checks whether the block being broken is in that tracked set. If it is, allow the break (override Towny's wilderness `destroy=false` for this specific block); if it isn't, let Towny's existing wilderness protection stand.
- **Event priority matters here and needs empirical verification, not an assumption.** Towny's own permission check almost certainly cancels the break event before your listener would normally see it, depending on what priority Towny registers at (not confirmed — don't guess). Register your listener at a priority that runs *after* Towny's default checks (`HIGH` is a reasonable starting point, `MONITOR` is conventionally for observation-only and shouldn't be modifying event state) and explicitly test that overriding actually works, rather than trusting it compiles and assuming it's correct.
- On a successful break of a tracked block, remove it from the tracked set — mostly just good hygiene to avoid the set growing unbounded over a 6-hour window, since a broken block can't be broken again either way.
- **Known, accepted edge case — flagging rather than solving:** this tracked set is in-memory and doesn't survive a server restart. A block placed and tracked before a restart still physically exists afterward, but the tracking that made it breakable is gone — it becomes effectively permanent (like natural terrain) until the next scheduled map reset clears it away entirely. This is a minor, self-healing inconsistency (the 6-hour reset fixes it eventually), not a bug worth building persistence for.

---

### 4.4j — Currency and shop

**Build:**
- Per-player currency, persisted in SQLite (`player_balances: player_uuid, balance`), earned from kills and from capture-point ticks (coordinate with 4.4g — award currency alongside points, same tick).
- **Kill currency and 4.4h's kill-reward score credit both key off the same `PlayerDeathEvent`, but with genuinely different requirements — do not gate them identically.** 4.4h is a team-level credit and fires on any qualifying death, including environmental ones (no killer needed). This step is different: currency is per-player, so it requires a real, identifiable killer to know whose balance to credit — check the event for a valid killer/damage source, and skip the currency award entirely if there isn't one (an environmental death correctly triggers 4.4h's team credit while correctly not awarding anyone currency here). **(Simplified, per project owner decision) Build one single death handler that does both jobs** — team score credit (4.4h) and killer currency award (here) — rather than two separate handlers. The handler's logic: always attempt the 4.4h team credit (works regardless of killer), then separately check for a valid killer and only if one exists, also award currency. One method, two outcomes gated by different conditions internally, not two independent listeners.
- A shop GUI (Paper's inventory GUI API) selling: building blocks, golden apples, cobwebs, fully enchanted bows and arrows, tridents, rails, TNT minecarts. Use SiegeGame's `ShopGUI`/`shop.yml` (`~/mcserver/reference/SiegeGame/`) as a structural reference for how to lay out a shop GUI and price list — not for game-balance numbers, which should come from this project's own design decisions.

---

### 4.4k — Kit system

**Build:**
- Default kit applied on spawn: full enchanted Mending Netherite armor + sword, experience bottles (for Mending fuel — not currency), Health II/Speed II/Strength II potions, shield, diamond axe, baked potatoes.
- Per-player kit customization, persisted in SQLite (`kit_loadouts`), following SiegeGame's kit-editing pattern as a structural reference (not its exact item list).
- Validate the kit editor against: permitted materials, enchantments, quantities, potion types, slot rules.
- Harden against duplication via shift-click, drag, number-key swap, disconnect-mid-edit, death-mid-edit, and inventory-close-event exploits — test each of these deliberately, they're easy to miss by accident.

---

### 4.4l — Spectate and rejoin commands

**Revised approach (simpler, per project owner decision): a real third Towny town holds spectators, reusing all existing team-membership infrastructure rather than introducing a separate tracking mechanism.**

**Setup — plugin-provisioned, not admin-created (revised again: use the Towny API directly to create a landless town, no lobby-world workaround needed):**
- Unlike `teamRed`/`teamBlue` (manually admin-created before the plugin ever runs, per Stage 3.5), `SpectatorTown` is created **by the plugin itself, programmatically, with zero claimed land at all** — no homeblock, no townblock, nothing to place anywhere. Confirmed directly from Towny's source: `TownyUniverse.getInstance().newTown(name)` creates a registered `Town` with no location parameter whatsoever — a homeblock is genuinely optional at the data-model level (`Town.hasHomeBlock()` is checked conditionally throughout source, never assumed true), this API method just isn't reachable through the normal `/town new` player command, which always claims your current location as part of the flow. Using the API directly sidesteps that entirely.
- **On plugin startup**, as part of the same startup validation already specified in 4.4a: check if `SpectatorTown` (or whatever name is configured) already exists via `TownyUniverse.getInstance().getTown(name)`. If it doesn't, create it with `newTown(name)`, catching `AlreadyRegisteredException` and `InvalidNameException`. This makes `SpectatorTown` self-provisioning — no manual admin step needed to create it at all, unlike the two real teams.
- Still needs an NPC mayor so no real player is structurally load-bearing for it (same reasoning as 3.5a) — set this once via the existing admin command, `/ta set mayor SpectatorTown npc`, after the plugin has created the town on first boot. (Confirm this admin command works correctly on a town with no claimed land — not verified, since every other town this project has created so far had a homeblock; flag rather than assume if it behaves unexpectedly.)
- Add it to config, kept separate from the `teams:` block since it isn't a competitive side:
  ```yaml
  spectator:
    town: "SpectatorTown"
  ```
- This town doesn't need the elaborate build/destroy/switch permission setup the real teams have — spectator *mode* (the Bukkit gamemode) already prevents interaction regardless of Towny permissions, so simple defaults are fine here. It also has no physical location for those permissions to apply to in the first place, since it never claims any land.

**Why this is easier than a `setTown(null)`/"leave town" approach:** it reuses 4.4a's Towny-query pattern and 4.4c/4.4d's `setTown()`-based force-join pattern exactly as-is — no new API surface, no need to figure out what Towny's "resident with no town" state actually looks like (never checked, and avoiding the question entirely is simpler than answering it). Every player is always resident of *some* town at all times — `teamRed`, `teamBlue`, or `SpectatorTown` — one consistent mental model throughout, and this now extends cleanly to the town itself needing no physical presence either.

**Important: this does not change what `getPlayerTeam()` (4.4a) returns for gameplay purposes.** `SpectatorTown` residents still report "no team" from `getPlayerTeam()`'s perspective — they don't count toward RED/BLUE headcount balance (4.4c/4.4d), score attribution (4.4h), or TNT damage scaling (4.5). The spectator town is purely the *mechanism* for tracking who's spectating; it is not a third value that gameplay logic needs to special-case. "No team" can now happen for two real reasons (never assigned yet, or deliberately spectating) — both correctly resolve to the same "no team" state everywhere else in the codebase already handles that case (e.g. 4.4d.1's absolute-color display for no-team viewers).

**Build — `/siege spectate`:**
- Set the player's gamemode to `GameMode.SPECTATOR` — Minecraft's real, built-in vanilla spectator mode (available since 1.8), not a custom-built restricted mode. This is a single API call (`player.setGameMode(GameMode.SPECTATOR)`) and it already provides everything spectating needs for free: invisible to other players, free flight, passes through blocks and entities, and cannot interact with or damage anything. Nothing else needs to be built to achieve those behaviors.
- Force-join them to `SpectatorTown` via the same `setTown()` + `save()` pattern used everywhere else (4.4c/4.4d) — no new method needed, just the same call with a different destination town.

**Build — `/siege spectate`, inventory handling (project owner decision):**
- **Store the player's inventory exactly the same way a siege→lobby exit does (4.4e)** — saved, then cleared — and restore it when they later `/siege rejoin`. Reasoning: a player's inventory most likely reflects their preferred loadout and any shop items they've bought; spectating shouldn't cost them that. This is the same storage mechanism as 4.4e, not a second parallel one.

**Build — `/siege rejoin` (new command):**
- Only usable while the player is currently resident of `SpectatorTown` (reject otherwise, with a clear message).
- **Must switch the player out of spectator gamemode** — set them back to `GameMode.SURVIVAL`. Stating this explicitly because it's the kind of obvious-in-hindsight step that's easy to omit: a player who rejoins but stays in spectator mode is invisible, can fly, and can't be hit, which would be a severe exploit rather than a cosmetic bug.
- **Reuses 4.4c's join-time auto-assign logic directly** — the same "count both teams, assign to whichever has fewer" function used when a brand-new player first connects — not 4.4d's switch command. This is deliberate: rejoining from spectator isn't a "switch" (there's no prior team to compare against, and 4.4d's 15-minute cooldown/2-player-deficit rules are about players actively leaving one side for another, which doesn't apply here). Do not route this through 4.4d's logic or its cooldown.
- On success: exit spectator gamemode, force-join the assigned team's town, teleport to that team's spawn (reusing 4.4d's teleport step), and apply their kit (4.4e/4.4k entry behavior — same as any other entry into active play).

---

## 7. Persistence layer (needed as a prerequisite for 4.4g specifically)

- SQLite, via `org.xerial:sqlite-jdbc` (check Maven Central for current version — don't pin a version from memory).
- Suggested tables, consolidating what's referenced above: `players`, `player_balances`, `kit_loadouts`, `matches`, `score_ledger`. **No `team_memberships` table** — team membership lives in Towny only (§3), not in this plugin's database.
- All database I/O asynchronous, off the server tick thread — capture needed Bukkit state on the main thread first, then hand off.
- Flush any queued writes on plugin disable/shutdown.
- This doesn't need a full enterprise-grade abstraction — a straightforward DAO-per-table pattern is sufficient for this project's scale. Don't over-engineer it.

## 8. After 4.4 is complete

Stage 4.5 (TNT minecart mechanic) builds on top of 4.4g (scores) and 4.4i (cleanup) and is a separate, substantial piece of work — not part of this brief. Don't start it until everything above is verified working. The full project plan (including 4.5 and beyond) exists in `../../../Downloads/MAIN_PLAN.md` in this project's root documentation — consult it for what comes after this brief's scope, but don't build ahead of what's asked here.

---

## 9. Verification Checklist

**This checklist is for the project owner (or a human tester) to run through manually, one step at a time, after each corresponding sub-step is built. The agent does not need to perform these itself — do not attempt to log in as multiple players, wait out real-time timers, or otherwise execute these checks. Building the feature to satisfy the criteria below is the goal; running the criteria is someone else's job.**

This doesn't relax Rule 2 — each sub-step still needs to actually compile and run without errors before moving to the next. It just means the detailed manual/multiplayer verification described below happens separately, afterward, not as something the agent is expected to carry out.

**4.4a:** Deliberately misspell a town name in the live config → server fails to start with a clear log message, not a stack trace. `/siege team` correctly reports "no team" for a player not resident of either configured town, and correctly reports the right team for one who is.

**4.4c:** Two fresh test accounts joining in sequence land on different teams (confirmed via Towny residency directly, e.g. `/town` or `/resident`); a third joins whichever team still has fewer.

**4.4d:** Switching to a team that would end up 2+ ahead is blocked with a clear message; switching to an even/behind team succeeds and Towny residency reflects it immediately; switching while combat-tagged is blocked; attempting a second switch inside 15 minutes of the first is blocked with a message showing remaining time; waiting out the cooldown allows a switch again. Confirm the player's inventory is fully intact after a switch — not cleared, not reset to a base kit.

**4.4d.1:** Two players on the same team see each other in green, in both tab and above-head. Two players on different teams see each other in red. Switching a player's team (4.4d) immediately updates both their own view of everyone else and everyone else's view of them, with no reconnect needed. A no-team/spectator player displays using whatever neutral behavior was decided above. Two teammates hit each other with a direct weapon (melee/arrow) — confirm no damage is dealt. **Two teammates standing in a TNT minecart's blast radius — confirm damage is dealt to both, per 4.5's formula, not blocked.** Two teammates try to walk through each other's position at the capture point — confirm they pass through rather than blocking. After a team switch, confirm both friendly-fire and collision settings apply correctly to the player's *new* team, not leftover from their old one.

**4.4d.2:** Sidebar is visible immediately on join, showing correct current values. Both teams' real points (line 4–5) only ever increase (never reset) except via the admin resetscores command from 4.4g. Session points (line 7–8) reset to zero exactly when a new `ACTIVE` window starts, freeze during `BREAK`, and never affect the real score shown in lines 4–5. Banner control line matches the actual controlling side/count from 4.4f in real time. BAT Time Left counts down correctly through both `ACTIVE` and `BREAK` phases.

**4.4e:** Manually trigger each transition and confirm inventory state is exactly what's expected, not leftover from the previous context.

**4.4f:** A player standing within 16 blocks of the banner (both horizontally and vertically) sees a boss bar counting up over 7 minutes; a player 16+ blocks *above* the banner but horizontally close does NOT start a session (confirming the vertical check works); leaving the radius mid-session resets progress to zero and shows a failure message, and re-entering starts a fresh 7-minute session rather than resuming; completing the session adds them to the controlling set (verify via a debug command or log line, since 4.4g will consume this). Confirm exactly one boss bar per player, not two.

**4.4g:** Two players hold the capture point together, watch the score increase per tick matching SiegeWar's actual formula (not just "points go up"). Restart the server mid-siege — confirm scores are exactly what they were before restart, not reset. Run `/siege admin resetscores` without confirmation — confirm it does nothing but warn; with confirmation — confirm it zeroes correctly.

**4.4h:** Any qualifying death — PvP or environmental (fall, drowning, lava, etc.) — credits the correct point amount to the dying player's opposing team, regardless of where on the map it happened and regardless of whether there was a real killer. A friendly-fire TNT minecart kill still credits the opposing team correctly (based on the dying player's own team, not who caused it). Confirm the asymmetric point values (dying-as-attacker vs. dying-as-defender) are both wired correctly, not sharing one number.

**4.4h.1:** Let a full active window elapse (use a short duration for testing, e.g. 30 seconds) — confirm in-progress sessions are cancelled, new sessions can't start, existing score is unaffected, and PvP damage still works normally during the break. Confirm the cycle resumes correctly afterward. Restart the server mid-break — confirm it comes back in `ACTIVE`, not stuck in `BREAK`.

**4.4i:** Set a short reset interval for testing (e.g. a few minutes instead of 6 hours) — confirm the warning broadcasts fire on schedule, the restoration happens in-place while players remain in the world (no relocation), the region resets to exactly the Stage 3.9 clean backup (bases and banner intact, TNT-explosion craters filled back in), the banner reconstructs correctly afterward, and team scores/currency/kits/Towny claims are completely unaffected. Confirm the restoration is spread across multiple ticks (no visible full-server stutter). Confirm the manual admin reset command works independently of the automatic timer. Confirm the 30-second minecart placement cooldown works (a second placement inside 30s is blocked) and that a stationary minecart is swept after 5 minutes — and confirm there is NO per-player or per-team minecart cap, since that was explicitly cancelled.

**4.4i.1:** A player places a shop-bought block in the open battlefield, then breaks it themselves — confirm it works, where a non-tracked natural terrain block in the same spot would be blocked by Towny's wilderness `destroy=false`. A different player (including an enemy) breaks someone else's tracked placed block — confirm that also works. Confirm the override actually takes effect ahead of Towny's own permission check (this needs real testing, not just code review, given the event-priority uncertainty noted above). Restart the server, then attempt to break a block that was placed and tracked before the restart — confirm it's now treated as unbreakable (the known, accepted limitation), and confirm the next scheduled map reset (4.4i) clears it away regardless.

**4.4j:** A PvP kill with a real killer awards that killer currency; an environmental death (no killer) correctly awards no currency to anyone, even though 4.4h's team score credit still fires for that same death. Capture ticks add currency alongside points. The shop GUI opens, items are purchasable, purchased items appear in inventory and deduct the correct currency amount, insufficient-balance purchases are rejected cleanly.

**4.4k:** A fresh player spawns with the exact default kit; customizing and respawning reflects the saved customization; attempting each duplication exploit listed above fails to duplicate anything.

**4.4l:** On a fresh server with no `SpectatorTown` yet, the plugin creates it automatically on startup with no claimed land — confirm it exists in Towny's registry after boot without any admin command being run. `/siege spectate` enters spectator mode, stores and clears the player's inventory, and moves their Towny residency to `SpectatorTown`; `getPlayerTeam()` correctly reports "no team" for them afterward, and other players cannot see them at all (vanilla spectator invisibility). `/siege rejoin` only works while actually in `SpectatorTown` (rejected otherwise); it assigns to whichever real team currently has fewer players (matching 4.4c's logic exactly, not 4.4d's), **switches them out of spectator gamemode back to survival**, restores their stored inventory, and teleports to the correct spawn — with no cooldown, since this must not be gated by 4.4d's 15-minute switch cooldown. Explicitly confirm a rejoined player is visible, damageable, and cannot fly.
