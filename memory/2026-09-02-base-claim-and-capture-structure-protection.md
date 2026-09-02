# Base claims and capture structure protection

## Symptom

Roster fighters could not place blocks in their own base claims, and tagged
fighters could not open their own fence gates. The capture beacon and its iron
base had no proactive immutable-block guard.

## Root cause

`BaseTerrainProtectionListener` denied every block placement and break in every
base claim, without distinguishing authored terrain from runtime tracked player
placements. `CombatTaggedInteractionListener` independently cancelled tagged
fence-gate interactions after `BaseClaimInteractionListener` had allowed the
owning fighter's claim interaction. The beacon is rebuilt periodically but was
not protected from a player break or an explosion in the intervening tick.

## Resolution

* Owning roster fighters may place in their claim and may break only
  `PlacedBlockTracker` entries in it. Authored base blocks and all foreign-claim
  changes remain denied.
* The combat fence-gate rule now exempts the fighter's own claim; the existing
  claim interaction listener still explicitly permits all `Openable` controls.
* `CaptureStructureProtectionListener` protects the banner location, beacon
  directly beneath it, and the complete 3x3 iron base from player breaks and
  entity/block explosions, regardless of player permissions.

## Verification

`mvn test` passed on 2026-09-02: 313 tests, 0 failures/errors/skips.
