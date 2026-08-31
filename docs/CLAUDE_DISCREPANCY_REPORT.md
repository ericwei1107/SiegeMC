# Claude Discrepancy Report

Full-codebase audit, 2026-08-31. Companion to `docs/FEATURE_INVENTORY.md`. Scope:
`src/main/java` (155 classes), `config.yml`/`maps.yml`/`plugin.yml` (packaged and live),
and every planning document in the repo plus `~/Downloads/MAIN_PLAN.md`. No code was
changed to produce this report — every finding below is report-only, per the approved
audit plan.

**How to read this report:** each finding states what the documents say, what the code
actually does, why it matters in a real game session, a suggested fix, and an explicit
**"Was this intentional?"** line. That last line is the point of the exercise — some of
these will turn out to be decisions you made in a conversation that never made it into
a document, and the fix there is to write it down, not to revert the code. Others are
genuine drift. This report does not presume which is which.

## Severity legend

- **HIGH** — a real, current, player-visible discrepancy from an explicit prior decision, or a bug with a concrete failure path.
- **MEDIUM** — a real defect or drift with a narrower blast radius, or one that's self-recoverable.
- **LOW** — maintainability, hygiene, or a latent issue that needs your confirmation before it's worth fixing.
- **INFORMATIONAL** — not a defect; recorded so a documented-but-silent reversal has a paper trail.

## Category legend

| Code | Meaning |
|---|---|
| **U** | Unapproved / unintended feature |
| **D** | Silent reversal of an explicit prior decision (a newer document or the code itself moved the goalposts without saying so) |
| **X** | Cross-module bug — neither module is wrong in isolation |
| **B** | Bug, module-local |
| **E** | Inefficiency / duplication / dead code |
| **C** | Configuration drift (packaged vs. live, or code vs. any config) |

## Summary table

| ID | Category | Module | Severity | Description |
|---|---|---|---:|---|
| U-01 | U | economy | **HIGH** | Shop sells 12 items (pickaxes, steak, ender pearls, a knockback sword) with no trace in any planning document |
| D-01 | D | minecart | **HIGH** | Per-player/per-arena minecart caps exist and are enforced, directly contradicting the Stage 4.4 brief's explicit "explicitly cancelled... do not build" instruction |
| C-01 | C | economy, minecart | **HIGH** | Both U-01 and D-01 are invisible in the live server's own `config.yml` — they only exist via Java code defaults |
| X-01 | X | round + map + state | **MEDIUM** | Round-completion/recovery evacuation silently wipes the inventory of an admin mid-map-calibration |
| B-01 | B | round | **MEDIUM** | Misleading "Preparing next map: unavailable — it will be loaded shortly" broadcast immediately before entering RECOVERY |
| E-01 | E | config | **MEDIUM** | `config.yml`'s kit-editor section is ~700 lines of 27 near-identical, fully copy-pasted slot blocks |
| B-02 | B | round | LOW | `ActiveRoundProvider.transition()` has a no-op ternary that always evaluates the same regardless of target phase |
| E-02 | E | SiegePlugin wiring | LOW | `MinecartSettings`/`CaptureSettings`/`ScoringSettings`.fromConfig() are each re-parsed 2–3 times from the same config |
| E-03 | E | capture | LOW | `CaptureService.suspendForReset()`/`resumeAfterReset()` are dead code with zero callers |
| E-04 | E | (package) | LOW | `woo.siegePlugin.cycle` is an empty leftover package from the retired ACTIVE/BREAK cycle |
| E-05 | E | SiegePlugin.java | LOW | `LobbySettings` is imported twice |
| D-02 | D | score/death | INFORMATIONAL | Kill-reward credit now requires a real opposing killer, reversing the Stage 4.4 brief's explicit "no killer check" rule — correctly matches the *newer* 1.1 validation doc |
| D-03 | D | docs only | INFORMATIONAL | The 1.1 Expansion document itself disagrees with itself about whether the score cutoff key is `scoring.winning-score` or `rotation.winning-score`; code and `AGENTS.md` agree on `scoring.winning-score` |

---

## U-01 — Shop sells 12 items nobody documented (HIGH)

**Evidence:**
`src/main/java/woo/siegePlugin/economy/ShopBundle.java` lines 24–34 define:
`ENDER_PEARLS`, `STEAK`, `EXPERIENCE_BOTTLES`, `GOLDEN_CARROTS`, `KNOCKBACK_SWORD`,
`DIAMOND_PICKAXE_I` through `_V` (five separate enchant tiers), and
`NETHERITE_PICKAXE_V`. Every one has a nonzero price in the packaged
`src/main/resources/config.yml`.

**Intended:** Stage_4.4_Agent_Build_Brief.md §4.4j: *"A shop GUI... selling: building
blocks, golden apples, cobwebs, fully enchanted bows and arrows, tridents, rails, TNT
minecarts."* Seven items. `SiegePlugin 1.1 Expansion.md` never mentions the shop at all
— its "To do" and "Recommended priority" sections only cover map rotation, potion
storage, kit editing, and border walls.

**Actual:** The shop has grown to 19 items. Twelve — five diamond pickaxe enchant
tiers, a netherite pickaxe, a "Knockback II Diamond Sword," ender pearls, steak,
bottles o' enchanting, and golden carrots — appear nowhere in any planning document,
memory file, or the Stage 4.4 brief's own discrepancy audit (§10). The one memory
record that touches these items (`memory/2026-08-27-shop-price-fallbacks.md`) diagnoses
a *pricing* bug for items it treats as already-existing fact; it does not explain when
or why they were added.

**Why it matters:** the project's stated design goal is explicitly "no town-building,
no economy grind, no progression — players log in, get equipped instantly, and fight."
Diamond and Netherite pickaxes bought with siege currency don't serve that goal on
their face — there's no mining or building objective in the game as designed. This is
exactly the shape of "features that come as a surprise to me and other players" you
described: they work correctly, they're just not something you asked for.

**Confirmed live, not just in the repo:** the live server's own `config.yml` at
`~/mcserver/dev/plugins/SiegePlugin/config.yml` has only the original 8 `shop.prices`
keys — none of these 12 items appear there. But because `ShopBundle.defaultPrice()`
supplies a price whenever a config key is absent, all 12 are purchasable on the live
server *right now*, with no trace in the file you'd actually think to check. See C-01.

**Suggested fix:** decide, per item, whether it stays. If some are wanted, add them to
the shop's documentation (the natural home is a revision note in the 1.1 Expansion doc
or a new memory record) so a future audit doesn't re-flag them. If some aren't wanted,
remove the `ShopBundle` enum constants and their config keys — `ShopListener`/`ShopMenu`
iterate the enum, so removing entries needs no other code change.

**Was this intentional?** — this is the central question for you to answer. Nothing in
the available record says yes or no.

---

## D-01 — Minecart caps exist despite an explicit "do not build" instruction (HIGH)

**Evidence:**
- `src/main/java/woo/siegePlugin/minecart/MinecartSettings.java`:
  `MAX_ACTIVE_PER_PLAYER_PATH = "minecart.max-active-per-player"` (default 2),
  `MAX_ACTIVE_ARENA_PATH = "minecart.max-active-arena"` (default 40).
- `src/main/java/woo/siegePlugin/minecart/MinecartPlacementLimits.java` enforces both
  caps and is wired into `MinecartPlacementListener.java:70-81`.
- `src/test/java/woo/siegePlugin/config/CanonicalConfigTest.java:48-49` asserts these
  exact default values against the packaged config — this was built and tested
  deliberately, not accidentally introduced.

**Intended:** `Stage_4.4_Agent_Build_Brief.md` §4.4i, verbatim: *"Do not build a
per-player or per-team minecart cap. An earlier version of this brief specified
`minecart-cap.per-player` / `minecart-cap.per-team` config keys — those are explicitly
cancelled. No cap of any kind... Instead, rate-limit placement with a 30-second cooldown
per player... Why a cooldown instead of a cap: a cap creates a frustrating failure mode
where a player is blocked because other people used up the team's allowance."* This is
about as explicit an owner decision as this project's documents contain.

**Actual:** `docs/SECURITY_AND_PERFORMANCE_OPERATIONS.md` (a newer document) lists the
caps as an intentional "Control now in the plugin," framed as a security/performance
measure: *"Siege TNT minecarts have configurable entity budgets:
`minecart.max-active-per-player: 2` and `minecart.max-active-arena: 40`."* Under the
newest-wins doc-precedence rule this audit used, that document should govern — but
nowhere does it, or any other document, acknowledge that this reverses the earlier
explicit cancellation. It reads as if the cap had always been the plan.

**Why it matters:** this is a live gameplay rule players will feel directly — exactly
the scenario the Stage 4.4 brief warned about ("a player is blocked because other
people used up the team's allowance"). The cooldown-only design was chosen for a
specific, stated reason; the cap reintroduces the exact frustration that reasoning was
meant to avoid.

**Confirmed live:** the live server's `config.yml` has no `max-active-per-player` /
`max-active-arena` keys at all — meaning the caps are active today via Java defaults,
invisible to anyone reading the live config. See C-01.

**Suggested fix:** if the caps are wanted (e.g. genuinely needed for server performance
at higher player counts), write that decision down explicitly — a line in
`SECURITY_AND_PERFORMANCE_OPERATIONS.md` saying "this supersedes Stage 4.4 brief §4.4i's
no-cap decision, because—" would have prevented this from reading as silent drift. If
the caps aren't wanted, delete `MinecartPlacementLimits`'s enforcement and the two
config keys; the cooldown-only design remains intact underneath.

**Was this intentional?** — plausibly yes (it's tested and documented as a deliberate
security control), but the *reversal itself* was never surfaced to you as a decision to
make. That's the part worth confirming.

---

## C-01 — Both U-01 and D-01 are invisible in the file you'd actually check (HIGH)

**Evidence:** diff of `~/mcserver/dev/plugins/SiegePlugin/config.yml` (live) against
`src/main/resources/config.yml` (packaged, in this repo):

- Live `shop.prices` has exactly 8 keys (the original documented set). None of U-01's
  12 extra items appear.
- Live `minecart:` block contains only `damage: {balanced-coefficient, full-damage-deficit}`
  — no `max-active-per-player` / `max-active-arena` keys at all.
- Live `kit.editor` is entirely absent (the live kit system predates the customizable
  editor; only `kit.default-loadout` exists live).
- Live `maps.yml` predates the `calibration-spawn` field and the in-game calibration
  workflow's documentation comment — a normal deployment-lag artifact, not a defect,
  since `scripts/deploy.sh` is designed to preserve VPS-managed templates rather than
  overwrite them (per `memory/2026-08-30-iron-mountain-supply-validation.md`).

**Why it matters:** `config.getInt(path, default)` / `config.getLong(path, default)`
apply the Java-side default whenever a key is simply absent — there is no
"key doesn't exist, so the feature is disabled" behavior anywhere in this codebase's
settings classes. That means every one of U-01's items and D-01's caps is live on the
production server right now, with literally no line in the file an operator would
open to review configuration. The only way to discover them is to read the Java source
or open the in-game shop.

**Suggested fix:** none needed beyond resolving U-01 and D-01 themselves — but this is
worth knowing as a general pattern: a future addition that should ship "off by default"
needs a config key with a genuinely inert default (0, empty list, `enabled: false`),
not just an assumption that the owner will notice a new default before it goes live.

**Was this intentional?** — not applicable; this finding just explains why U-01 and
D-01 were able to go unnoticed rather than being resolved as a decision itself.

---

## X-01 — Round evacuation silently wipes a calibrating admin's inventory (MEDIUM)

**Evidence:**
- `round/RotationCoordinator.java`, `evacuateEveryone()`: `audience.onlinePlayers()
  .forEach(audience::sendToLobby)` — called on every INTERMISSION/RECOVERY-entering
  transition (`bootstrapFirstRotation`, `resumeWithoutMatch`, `transferRosterAndPrepare`
  via `abortAndPrepare`, `onMatchCompleted`'s failure path, `discardPreparedAndRecover`).
  It sweeps **every online player**, not just queued/rostered round participants.
- `round/BukkitRoundAudience.java`, `sendToLobby()`: delegates to
  `PlayerStateTransitionService.forceRoundLobby(player)`.
- `state/PlayerStateTransitionService.java` lines 247–258, `forceRoundLobby()`:
  unconditionally closes the inventory, calls `PlayerInventorySnapshot.clear(player
  .getInventory())` (no save first — by design, since round inventory is meant to be
  discarded), sets `GameMode.ADVENTURE`, and teleports to the lobby spawn.
- `map/MapCalibrationService.java`: an entirely separate, independent per-admin session
  tracker (`activeFor(Player)`) for the `/siege admin map calibrate` workflow. It has
  no registration with, or exemption from, `PlayerStateTransitionService`'s
  LOBBY/SIEGE/SPECTATOR context model.

**Failure scenario:** an admin runs `/siege admin map calibrate <map>` (a documented,
normal workflow — see the 1.1 Expansion doc and `docs/STAGE_4_4_5_PLAYTEST_GUIDE.md`)
and is teleported into a disposable calibration copy while holding whatever items they
were carrying. Independently, the live production round completes, gets aborted, or
exhausts every fallback candidate — any of several code paths that call
`evacuateEveryone()`. The admin is swept up as "just another online player": their
current inventory is silently cleared with no warning, and they're teleported to the
main lobby in Adventure mode. Their calibration session is left dangling — `/siege
admin map return` can still teleport them back into the still-loaded calibration copy,
but whatever they were holding is gone.

**Why it matters:** this isn't a contrived edge case — calibration is described at
length as a normal admin task, and round completion/recovery run on independent timers
that have no reason to ever coordinate with it. It's a real footgun for exactly the
person operating the server.

**Suggested fix:** give `PlayerStateTransitionService` (or a small shared registry) a
notion of "this player is in an admin session that must never be force-evacuated," and
have `evacuateEveryone()` skip those UUIDs — or have `MapCalibrationService` register
against the same context map so `forceRoundLobby` can detect and defer them.

**Was this intentional?** — almost certainly not; this looks like two systems built at
different times (the pre-rotation calibration workflow and the later rotation
lifecycle) that were never made aware of each other.

---

## B-01 — Misleading "loading shortly" broadcast right before RECOVERY (MEDIUM)

**Evidence:** `round/RotationCoordinator.java`, `beginPreparation(List<Component>
ceremony, String announcement)`, roughly lines 483–518:

```java
String nextMap = candidates.isEmpty() ? "unavailable" : candidates.getFirst().displayName();
if (ceremony != null) {
    ceremony.forEach(audience::broadcast);
    audience.broadcast(Component.text(
            "Preparing next map: " + nextMap + " — it will be loaded shortly.",
            NamedTextColor.YELLOW
    ));
    audience.broadcast(lobbyButton());
} else if (!candidates.isEmpty()) {
    audience.broadcast(Component.text(
            "Preparing next map: " + nextMap + " — it will be loaded shortly.",
            NamedTextColor.YELLOW
    ));
    audience.broadcast(lobbyButton());
}
if (candidates.isEmpty()) {
    enterRecovery("No enabled map passed validation", null);
    return;
}
```

**The bug:** the `else if (!candidates.isEmpty())` branch (taken when a round is being
re-prepared with no ceremony, e.g. after an abort) correctly guards on candidates being
non-empty before announcing. The `if (ceremony != null)` branch (taken right after a
match completes and a results card is shown) has **no such guard** — it unconditionally
announces "Preparing next map: unavailable — it will be loaded shortly" and posts a
live `[Go to Lobby]` button, then a few lines later immediately enters `RECOVERY`
because `candidates` turned out to be empty.

**Concrete failure:** complete a match at a moment when every `maps.yml` entry has been
broken (a bad template edit, a validation regression, etc.) — not a contrived scenario,
since map templates are edited by hand. Every player sees the match results, then
"Preparing next map: unavailable — it will be loaded shortly," then immediately
"Siege rotation needs administrator recovery." The middle message actively contradicts
the one right after it.

**Also flagging as duplication (E):** the "Preparing next map..." + `lobbyButton()`
broadcast pair is copy-pasted verbatim into both branches; the only real difference
between them is whether the ceremony card is shown first. One guarded block would fix
the bug and remove the duplication in the same edit.

**Suggested fix:**
```java
if (ceremony != null) {
    ceremony.forEach(audience::broadcast);
}
if (!candidates.isEmpty()) {
    audience.broadcast(Component.text(
            "Preparing next map: " + nextMap + " — it will be loaded shortly.",
            NamedTextColor.YELLOW
    ));
    audience.broadcast(lobbyButton());
}
```

**Was this intentional?** — no; this reads as an omitted guard, not a design choice.

---

## E-01 — `config.yml`'s kit editor is ~700 lines of copy-pasted blocks (MEDIUM)

**Evidence:** `src/main/resources/config.yml`, `kit.editor.slots` — 27 slot entries
(one per configurable inventory slot), each repeating an identical six-choice menu
(`default`, `instant_health_ii`, `speed_ii`, `strength_ii`, `cobblestone`,
`diamond_pickaxe`) verbatim. `grep -c "display-name: \"Supply Slot"` returns 27;
`grep -n "material:"` shows `DIAMOND_PICKAXE` and `COBBLESTONE` each appearing exactly
27 times, once per slot.

**Why it matters:** roughly 700 of the file's 1,001 lines are this one repeated block.
Any future change to what a slot offers (a new potion type, a price tweak, fixing a
typo in a display name) has to be made 27 times by hand, and it's easy to update 26 of
them and miss one. `KitChoiceCatalog.load()` reads this structure per-slot from
`FileConfiguration` — there's no code obstacle to sharing one named choice-set across
multiple slots; the duplication is purely in the YAML.

**Note:** this also means every one of the 27 slots offers a Diamond Pickaxe as a kit
customization choice, the same pattern noted in U-01 for the shop. Worth resolving both
together if the pickaxe theme turns out to be unwanted.

**Suggested fix:** either template-generate this block with a small script that reads
one canonical choice-set definition, or — if the config format itself should support
it — add a lightweight "choice-set reference" concept to `KitChoiceCatalog` so
`slots: {9: {choice-set: standard}, 10: {choice-set: standard}, ...}` replaces the
27-way copy-paste. This is a config-authoring/maintainability issue, not a runtime bug.

**Was this intentional?** — plausibly just how the file grew slot-by-slot as it was
authored; worth a cleanup pass regardless of intent.

---

## B-02 — `ActiveRoundProvider.transition()` has a no-op ternary (LOW)

**Evidence:** `round/ActiveRoundProvider.java`:
```java
public boolean transition(RoundPhase expected, RoundPhase next) {
    while (true) {
        State before = state.get();
        if (before.phase() != expected) {
            return false;
        }
        ActiveRoundContext context = next == RoundPhase.ACTIVE ? before.context() : before.context();
        if (state.compareAndSet(before, new State(next, context))) {
            return true;
        }
    }
}
```
Both branches of the ternary are identical — `next == RoundPhase.ACTIVE` is checked but
changes nothing. The method always just keeps `before.context()` regardless of which
phase is being transitioned to.

**Why it matters — needs verification, not assumed severity:** this is a strong
"refactor leftover" signature: the condition suggests an author once intended different
behavior per target phase (most plausibly: clearing `context` when leaving `ACTIVE` for
somewhere context shouldn't linger) but both branches collapsed to the same value,
without the now-pointless conditional being removed. In the current call sites this
appears harmless — `onMatchCompleted` calls `transition(ACTIVE, COMPLETING)` and
deliberately wants the context to survive into `COMPLETING` (for the ceremony), and
`activatePrepared` calls `transition(INTERMISSION, ACTIVATING)` where `before.context()`
is already `null`. But `ActiveRoundProvider.current()` and `isActive()` are read from
several places (including `SiegePlugin.trackedGeneratedWorldNames()`, which calls
`activeRounds.current()` with no phase check at all) — a future call to `transition()`
with a genuine intent to clear context on a specific phase change would silently not
work, because the ternary can never produce a different result no matter what's
written on the "true" side.

**Suggested fix:** either remove the ternary entirely (`before.context()`) to make the
current behavior explicit and stop it looking like unfinished logic, or — if a phase
transition really should null out context — fix the condition to actually do that and
add a test asserting it.

**Was this intentional?** — unclear; flagging as **PLAUSIBLE** rather than confirmed
harmful, since no current call site appears to depend on the missing behavior. Worth a
quick look rather than an urgent fix.

---

## E-02 — Settings objects are re-parsed from config 2–3 times each (LOW)

**Evidence:** in `SiegePlugin.java`:
- `MinecartSettings.fromConfig(getConfig())` is called at lines 218, 486, and 512.
- `CaptureSettings.fromConfig(getConfig())` is called at lines 196 and 495.
- `ScoringSettings.fromConfig(getConfig())` is called at lines 216 and 546.

Each call re-walks the same `FileConfiguration` and constructs an independent,
separately-validated record — there is no sharing between the copies. This isn't
expensive at boot (config parsing is cheap and this only runs once per enable), but it
is wasted work, and more importantly a real drift hazard: if one call site is ever
updated to pass a different config section, or a code change adds a parameter to one
`fromConfig()` overload, the others silently keep using stale settings without any
compiler signal that they've diverged.

**Suggested fix:** construct each settings record once in `onEnable()` and pass the
single instance to every consumer, the same way `CaptureSettings settings` is already
a field elsewhere in the codebase. Low priority — purely a maintainability tidy-up.

**Was this intentional?** — no; straightforward incremental-growth duplication.

---

## E-03 — Dead code: `CaptureService.suspendForReset()` / `resumeAfterReset()` (LOW)

**Evidence:** `grep -rn "suspendForReset\|resumeAfterReset"` across `src/main` and
`src/test` returns only the method definitions and their own Javadoc — zero callers.
These existed to pause capture during the old in-place six-hour arena reset
(`ArenaResetService`), which the 1.1 Expansion doc explicitly retired ("Retire snapshot
and activity-cycle systems... Remove arena snapshot capture/restore services"). No
`ArenaResetService`/`ArenaSnapshotService`/`ArenaMaintenanceCoordinator` class exists
anywhere in the current `src/main` tree — the retirement was done correctly everywhere
except these two orphaned methods in `CaptureService`.

**Suggested fix:** delete both methods (and the `suspended` boolean field they toggle,
and the corresponding check inside `tick()`) — pure removal, no behavior change, since
nothing calls them.

**Was this intentional?** — no; a straightforward leftover from the retirement.

---

## E-04 — Empty leftover package: `woo.siegePlugin.cycle` (LOW)

**Evidence:** `src/main/java/woo/siegePlugin/cycle/` exists as an empty directory with
no `.java` files in it. This was the package for the ACTIVE/BREAK activity cycle
(`SiegePhaseStatus`, referenced in the Stage 4.4 brief's D-04 discrepancy and later
fully retired in favor of round rotation's `RoundActivityStatus`). The directory itself
was never cleaned up after its contents were deleted.

**Suggested fix:** `rmdir` — it holds nothing and Maven doesn't need it.

**Was this intentional?** — no; harmless filesystem litter.

---

## E-05 — Duplicate import in `SiegePlugin.java` (LOW)

**Evidence:** `woo.siegePlugin.state.LobbySettings` is imported twice — once at line 62
and again at line 87. Compiles fine (Java tolerates a repeated identical import) but is
pure noise.

**Suggested fix:** delete one of the two `import` lines.

**Was this intentional?** — no; a trivial oversight from incremental edits.

---

## D-02 — Kill-reward now requires a real opposing killer (INFORMATIONAL — correctly resolved, not a defect)

**Evidence:** `death/SiegeDeathListener.java` lines 71–80: a death only credits the
opposing team's score when `killer != null && !killer.equals(victim) && killerTeam ==
victimTeam.opponent() && eligibility.isEligibleFighter(killer)`. Every other qualifying
death (environmental, self, friendly-fire) broadcasts a `+0` message and changes
nothing.

**What changed:** `Stage_4.4_Agent_Build_Brief.md` §4.4h is explicit and detailed on
this point — it directly corrects an *even earlier* draft that required a killer, and
insists: *"any qualifying death credits the dead player's opposing team, full stop —
the cause doesn't matter... There is no killer check at all."* The 1.1 Expansion
document's "CLAUDE VALIDATION PLAN" section reverses this again: *"Every eligible siege
death receives exactly one Siege message. A kill whose score write is rejected displays
Battle Points +0..."* and the live acceptance checklist states plainly: *"Self,
environment, and friendly deaths announce Battle Points +0 and do not change team score
or kill MVP."*

**Why this is informational, not a bug:** under this audit's doc-precedence rule
(newest wins), the 1.1 document governs, and the current code matches it exactly. This
entry exists purely so the reversal has a paper trail — the Stage 4.4 brief spent two
full paragraphs insisting "no killer check, full stop," and a reader of that document
alone would reasonably flag the current code as wrong. It isn't; the newer document
simply changed the rule and this is the one place in the whole audit where that
changed-and-documented pattern is genuinely clean.

**Was this intentional?** — yes, and unlike D-01/U-01, this reversal *is* written down
where a careful reader would find it.

---

## D-03 — The 1.1 Expansion document disagrees with itself about the score-cutoff config key (INFORMATIONAL)

**Evidence:** within `SiegePlugin 1.1 Expansion.md` itself: the "Fit for SiegePlugin"
section (under "Map rotation via clean world copies") says *"The score cutoff is
configured as `scoring.winning-score` (default 10000), grouped with the other scoring
keys."* The later "CLAUDE FOLLOW ALONG PLAN" section's "Durable score cutoff"
subsection says *"Configure `rotation.winning-score: 10000`."* These are two different
config paths in the same document.

**Actual:** the code (`score/ScoringSettings.java`, `WINNING_SCORE_PATH =
"scoring.winning-score"`), the test suite (`CanonicalConfigTest`), and `AGENTS.md`'s
live-migration note ("`scoring.winning-score`... added") all agree on
`scoring.winning-score`. There is no ambiguity in what actually ships.

**Why this is informational:** this is a documentation-only inconsistency within a
single planning document, already resolved correctly and consistently everywhere it
matters (code, tests, and the most current operational note). No action needed beyond
awareness, should anyone re-read the 1.1 doc's earlier section and get confused.

**Was this intentional?** — not applicable; purely a drafting slip in a planning
document, harmless because the implementation converged on one answer.

---

## Also noticed, out of scope for this audit (per the approved plan)

- **`helloween/`** — a complete Minecraft world (region files, `level.dat`, etc.)
  committed into this plugin repository. Unrelated to the plugin's code; a repo-hygiene
  question for you, not flagged as a plugin defect.
- **`## src/`** — a stray directory at the repository root (`ls` shows it literally
  named `## src`), containing a partial duplicate of `src/main/java/.../arena/`. Almost
  certainly the result of a shell command that had `## ` typed into a path by mistake.
  Not on the Maven source path, so it doesn't affect the build — just clutter worth
  deleting.

---

## Verification notes

- Baseline: `mvn clean test` (via the IntelliJ-bundled Maven at
  `/Applications/IntelliJ IDEA CE.app/Contents/plugins/maven/lib/maven3/bin/mvn`) —
  **273 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS.** No source files were
  modified during this audit; `git status` should show only the two new files this
  report and its companion inventory add under `docs/`.
- `target/surefire-reports/` held 79 stale XML files (for test classes that no longer
  exist, e.g. `ArenaCleanupSettingsTest`, `KitItemsTest`) before the clean rebuild —
  a trap for anyone reading that directory without rebuilding first. Mentioned here so
  it isn't mistaken for a live discrepancy.
- Every `file:line` citation above was re-checked against the current source after
  writing this report.
- See `docs/FEATURE_INVENTORY.md`'s "Coverage ledger" for exactly which packages got a
  full line-by-line read versus a structure-confirmed pass during this audit — 11 of 20
  packages were read completely; the remainder had every class located and its public
  surface examined, cross-referenced against callers and tests, but not all
  individually transcribed.
