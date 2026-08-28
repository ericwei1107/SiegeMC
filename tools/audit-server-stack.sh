#!/usr/bin/env bash
# Read-only deployment inventory for the SiegeMC host. Run from the repository:
#   ./tools/audit-server-stack.sh /path/to/server
set -euo pipefail

SERVER_DIR=${1:?Usage: $0 /path/to/paper-server}
PLUGIN_DIR="$SERVER_DIR/plugins"

if [ ! -d "$PLUGIN_DIR" ]; then
  echo "ERROR: plugins directory not found: $PLUGIN_DIR" >&2
  exit 2
fi

echo "== Server security posture =="
for key in online-mode enable-rcon enable-query white-list enforce-whitelist server-ip max-players view-distance simulation-distance; do
  value=$(awk -F= -v key="$key" '$1 == key { print $2; found=1 } END { if (!found) print "<unset>" }' "$SERVER_DIR/server.properties")
  printf '%s=%s\n' "$key" "$value"
done

echo
echo "== Installed plugin checksums =="
find "$PLUGIN_DIR" -maxdepth 1 -type f -name '*.jar' -print0 | sort -z | while IFS= read -r -d '' jar; do
  shasum -a 256 "$jar"
done

echo
echo "== Plugin descriptors =="
find "$PLUGIN_DIR" -maxdepth 1 -type f -name '*.jar' -print0 | sort -z | while IFS= read -r -d '' jar; do
  echo "-- $(basename "$jar")"
  unzip -p "$jar" plugin.yml 2>/dev/null | awk '/^(name|version|main|api-version|depend|softdepend):/ { print }' || true
done

echo
echo "== Permission/operations review =="
if [ -f "$PLUGIN_DIR/LuckPerms/config.yml" ]; then
  rg -n '^(enable-ops|auto-op|commands-allow-op|apply-bukkit-default-permissions):' "$PLUGIN_DIR/LuckPerms/config.yml" || true
fi
echo "Review player/group grants with: /lp verbose on, reproduce player actions, then /lp verbose off"
echo "Do not print or commit rcon.password, database credentials, Discord webhooks, or other secrets."
