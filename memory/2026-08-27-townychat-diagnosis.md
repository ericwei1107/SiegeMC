# TownyChat diagnosis — 2026-08-27

- Symptom: requested concise team-tag chat format did not appear in game.
- Root cause: the intended change was made in TownyChat's server-side configuration, not in the Siege plugin. Building the Siege jar cannot package or deploy `plugins/Towny/settings/ChatConfig.yml` or Towny town-tag data to a different server.
- Evidence: the built Siege jar contains only its own `config.yml`; it contains neither `ChatConfig.yml` nor Towny data. Locally, the default `general` channel has type `GLOBAL`, `modify_chat.enable` is true, and no second chat formatter is installed.
- Resolution: apply the TownyChat configuration and the Red/Blue/Spectator town tags directly on the server being tested, then perform a full restart.
- Status: DONE_WITH_CONCERNS — the external server configuration is not accessible from this workspace, so live chat rendering there still needs verification.
