# Active map restart failed with Paper's relative world container

## Symptom

After `iron_mountain1` was validated and activated successfully, restarting the
VPS left `/siege join` reporting that the siege was changing over. The admin
`rotation force` command reported that rotation was not recoverable or the map
was unknown.

## Root cause

Paper returned its world container as the relative path `.`.
`NativeMapWorldLoader.resume()` resolved the durable generated-world name and
called `normalize()`, producing a one-segment relative path whose `getParent()`
was null. The direct-child containment check dereferenced that null parent.

The exception escaped the restart callback while the in-memory coordinator was
still `ACTIVATING`. That phase deliberately blocks player joins, and
`RotationCoordinator.retry()` only accepts `INTERMISSION` or `RECOVERY`, which
explains both user-facing messages.

VPS evidence from `logs/latest.log`:

```text
java.lang.NullPointerException: Cannot invoke "java.nio.file.Path.equals(Object)"
because the return value of "java.nio.file.Path.getParent()" is null
  at woo.siegePlugin.map.NativeMapWorldLoader.resume(NativeMapWorldLoader.java:183)
  at woo.siegePlugin.round.RotationCoordinator.resumeActiveMatch(RotationCoordinator.java:251)
```

The VPS runtime override still had `iron_mountain1.setup.enabled: true`, its
generated world folder remained on disk, and SQLite still recorded generation
2 / `rotation-2` / `iron_mountain1` as `ACTIVE`. This was a lifecycle hydration
failure, not loss of the calibrated template or map configuration.

## Fix

- Normalize the template root and Paper world container to absolute paths when
  constructing `NativeMapWorldLoader`.
- Resolve recovered runtime folders beneath that absolute container and compare
  the parent null-safely.
- Retain the direct-child containment guard.

## Regression coverage

`NativeMapWorldLoaderTest` covers the exact `Path.of(".")` world-container case
and confirms nested runtime names remain rejected. `RotationRestartTest` still
covers active-match and interrupted-activation hydration.

## Verification

- Focused suite: 11 tests passed (`NativeMapWorldLoaderTest`,
  `RotationRestartTest`).
- Full Maven suite: 268 tests passed with zero failures or errors.
- A post-deploy VPS restart is still required for live acceptance.

## Status

DONE_WITH_CONCERNS until the corrected jar is deployed and the VPS is restarted
once to verify Iron Mountain reopens and `/siege join` enters the active match.
