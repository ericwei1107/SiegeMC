# Iron Mountain supply validation investigation

## Symptom

After Iron Mountain calibration was finished and a later calibration copy was
aborted, `/siege admin rotation validate iron_mountain1` reported 16 errors of
the form `potion supply <id> expects a chest at <coordinates> but found AIR`.

## Root cause

The later abort did not overwrite persisted calibration data. It only unloads
and deletes its own disposable generated copy. The supply registrations remain
in `potion-storages.yml`, keyed by `iron_mountain1`.

The original calibration finish path checked supply coordinates against the
chosen bounds (`PotionStorageService.findMapProblems`) but omitted the existing
loaded-copy chest check (`PotionStorageService.verifySupplyChests`). Therefore
it allowed a calibration copy containing newly placed chests to finish, then
deleted that copy. The immutable clean template still contains AIR at those
coordinates, so fresh validation correctly fails.

## Resolution

Calibration finish now verifies the physical supply chests, explicitly saves
and unloads the generated world, promotes it into the map's clean-template
location, and retains the previous template beside it as a timestamped backup.
Abort remains discard-only. The deployment script now seeds missing templates
but preserves existing VPS-managed templates, preventing a later jar/config
deployment from undoing in-game map edits.
