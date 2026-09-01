# Security and Performance Operations

This document is the operational follow-through for SiegePlugin hardening. It covers the custom plugin and the deployed third-party stack without assuming that third-party JAR source is trustworthy or available.

## Controls now in the plugin

- Siege TNT minecarts have configurable entity budgets: `minecart.max-active-per-player: 2` and `minecart.max-active-arena: 40`. Set either to `0` only for a managed event. `siege.minecart.cooldown.bypass` bypasses both the cooldown and these limits.
- Map resets use immutable templates and generated `siege-active-*` copies. Deletion is restricted to an immediate child of the server world container with that prefix; template folders are never deletion targets.
- Banner-controller currency is committed in one SQLite transaction per scoring interval instead of one transaction per controller. Controller reward chat is consolidated using `currency.capture-reward-notice-seconds: 60`.
- Shop purchases remain conditional database transactions; the cache is never trusted to approve a purchase.

## Release security gate

Before every public update:

1. Run `./tools/audit-server-stack.sh /Users/ericwei/mcserver/dev` and save the output outside the repository. Compare JAR hashes and plugin versions with the prior release.
2. Obtain every changed JAR directly from its official publisher, verify its published checksum/signature when available, and check the publisher's security advisories and Paper compatibility notes.
3. Run the Maven test suite and package the plugin. Do not publish with test failures or unreviewed dependency changes.
4. Use LuckPerms to prove that a normal player lacks all staff nodes, especially `siege.admin`, `siege.minecart.cooldown.bypass`, Towny administration, Vulcan administration, and LuckPerms administration.
5. Confirm `online-mode=true`, `enable-rcon=false`, `enable-query=false`, `white-list=false`, and that the VPS firewall exposes only the Minecraft port and restricted management access. SiegeMC is intentionally public; use permissions, moderation, and backups accordingly.

Never store passwords, webhooks, SSH keys, RCON passwords, or database credentials in this repository or paste them into audit output.

## 25-player performance acceptance test

Use a staging copy of the server, not the live map.

1. Start the profiler: `/spark profiler start --timeout 1200`.
2. Run a 20-minute session with 25 players: banner contention, shop and kit use, normal minecart placement/detonation, and one complete winner-to-map rotation.
3. Stop and save the profiler: `/spark profiler stop`.
4. Accept the build only when average TPS is at least 19.5, main-thread tick p95 is at most 40 ms, p99 is at most 50 ms, no repeating long-task warning occurs, and entities/heap/SQLite work do not grow throughout the soak.
5. Record the spark link, player count, active-cart peak, map copy/load/unload timing, and any configuration deviation beside the release artifact.

## Ongoing third-party review

Installed plugins may include ClearLag, CombatTag, LuckPerms, Towny, TownyChat, Vulcan, Multiverse-Core, PacketEvents, and SiegePlugin. Multiverse-Core is optional operator tooling and does not own SiegePlugin rotation. Treat each JAR as a separate supply-chain component: use vendor release notes and security advisories, update only after staging validation, and remove plugins that are unsupported or no longer needed.

The custom plugin source has permission checks at the command boundary, prepared SQL parameters for player data, and no network/webhook endpoints. Continue to test authorization and resource-abuse paths after every gameplay feature change.
