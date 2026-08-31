# Double-chest registration investigation

## Symptom

An administrator reported `That block is not part of a double chest` while
aiming at a Minecraft UI-confirmed large chest.

## Code-path evidence

Registration rejects only when the targeted block is not a Bukkit `Chest`, or
when `Chest#getInventory().getHolder()` is not a Bukkit `DoubleChest`. The
existing error did not reveal which condition occurred.

## Instrumentation

The registration error now reports the actual target material, or the chest
inventory size and holder class. This distinguishes an aim/raycast mismatch
from a Paper API representation issue on the live server.

## Status

Awaiting one live reproduction with the enhanced diagnostic message. Maven
tests pass; no behavioral chest-detection change was made without that evidence.
