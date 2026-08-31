#!/usr/bin/env bash
#
# Rebuilds the plugin jar, deploy-managed base configuration, maps manifest,
# and seeds the five approved clean map templates when missing. Never touches runtime-overrides.yml,
# runtime-map-overrides.yml, potion-storages.yml, siege.db, or generated active
# worlds — those stay VPS-managed.
#
# Requires passwordless SSH key auth to VPS_SSH_TARGET (see the README at the
# top of deploy.local.conf.example for the one-time ssh-copy-id setup) and
# passwordless sudo on the VPS for the `install` command below, since this
# runs non-interactively.
#
# Usage:
#   scripts/deploy.sh              # build + deploy
#   scripts/deploy.sh --dry-run    # show what would change, touch nothing

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONF_FILE="$SCRIPT_DIR/deploy.local.conf"

if [[ ! -f "$CONF_FILE" ]]; then
    echo "Missing $CONF_FILE." >&2
    echo "Copy $SCRIPT_DIR/deploy.local.conf.example to deploy.local.conf and fill in your VPS details." >&2
    exit 1
fi
# shellcheck source=/dev/null
source "$CONF_FILE"

: "${VPS_SSH_TARGET:?Set VPS_SSH_TARGET in deploy.local.conf}"
: "${VPS_SERVER_DIR:?Set VPS_SERVER_DIR in deploy.local.conf}"
: "${VPS_REMOTE_USER:?Set VPS_REMOTE_USER in deploy.local.conf}"

DRY_RUN=false
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        *) echo "Unknown argument: $arg (only --dry-run is supported)" >&2; exit 1 ;;
    esac
done

LOCAL_JAR="$HOME/mcserver/dev/plugins/siegemc-1.0-SNAPSHOT.jar"
LOCAL_CONFIG="$PROJECT_ROOT/src/main/resources/config.yml"
LOCAL_MAPS="$PROJECT_ROOT/src/main/resources/maps.yml"
# This local asset source is intentionally outside the Git repository: the five
# complete world folders are roughly 387 MB and are deployment assets, not
# source files. Override MAP_TEMPLATE_SOURCE in deploy.local.conf if you move
# the downloaded Sieges-master checkout.
MAP_TEMPLATE_SOURCE="${MAP_TEMPLATE_SOURCE:-$HOME/Downloads/Sieges-master}"
MAP_TEMPLATE_IDS=(al_quds iron_mountain1 kansas_city_outpost kazan murmansk)

# mvn usually isn't on PATH on this machine (no system-wide Maven install) —
# fall back to the copy bundled with IntelliJ IDEA if a plain `mvn` isn't found.
IDEA_MAVEN_BIN="/Applications/IntelliJ IDEA CE.app/Contents/plugins/maven/lib/maven3/bin"
if command -v mvn >/dev/null 2>&1; then
    MVN=mvn
elif [[ -x "$IDEA_MAVEN_BIN/mvn" ]]; then
    MVN="$IDEA_MAVEN_BIN/mvn"
else
    echo "Could not find mvn on PATH or at the bundled IntelliJ location." >&2
    echo "Install Maven, or edit IDEA_MAVEN_BIN in this script to point at your IDE's copy." >&2
    exit 1
fi

echo "==> Building a fresh jar (mvn package)"
(cd "$PROJECT_ROOT" && "$MVN" -o -q package)

for f in "$LOCAL_JAR" "$LOCAL_CONFIG" "$LOCAL_MAPS"; do
    if [[ ! -f "$f" ]]; then
        echo "Missing expected local file: $f" >&2
        exit 1
    fi
done
for map_id in "${MAP_TEMPLATE_IDS[@]}"; do
    template_dir="$MAP_TEMPLATE_SOURCE/$map_id"
    if [[ ! -d "$template_dir" || ! -f "$template_dir/level.dat" ]]; then
        echo "Missing clean map template: $template_dir (expected level.dat)" >&2
        exit 1
    fi
done

STAGE="/tmp/siegeplugin-deploy-$$"
RSYNC_FLAGS=(-avz --checksum)
if $DRY_RUN; then
    RSYNC_FLAGS+=(--dry-run)
    echo "==> DRY RUN — nothing on the VPS will actually change"
fi

echo "==> Staging files on the VPS ($STAGE)"
ssh "$VPS_SSH_TARGET" "mkdir -p '$STAGE/templates'"

rsync "${RSYNC_FLAGS[@]}" "$LOCAL_JAR" "$VPS_SSH_TARGET:$STAGE/siegemc-1.0-SNAPSHOT.jar"
rsync "${RSYNC_FLAGS[@]}" "$LOCAL_CONFIG" "$VPS_SSH_TARGET:$STAGE/config.yml"
rsync "${RSYNC_FLAGS[@]}" "$LOCAL_MAPS" "$VPS_SSH_TARGET:$STAGE/maps.yml"
for map_id in "${MAP_TEMPLATE_IDS[@]}"; do
    # session.lock belongs to a running server, never to an immutable template.
    rsync "${RSYNC_FLAGS[@]}" --exclude=session.lock \
        "$MAP_TEMPLATE_SOURCE/$map_id/" \
        "$VPS_SSH_TARGET:$STAGE/templates/$map_id/"
done

if $DRY_RUN; then
    ssh "$VPS_SSH_TARGET" "rm -rf '$STAGE'"
    echo "==> Dry run complete."
    exit 0
fi

echo "==> Moving staged files into place with correct ownership"
# shellcheck disable=SC2087
ssh "$VPS_SSH_TARGET" bash -s <<EOF
set -euo pipefail
DEST_PLUGINS="$VPS_SERVER_DIR/plugins"
DEST_SIEGE="\$DEST_PLUGINS/SiegePlugin"
DEST_TEMPLATE_ROOT="\$DEST_SIEGE/maps/templates"
RUNTIME_OVERRIDES="\$DEST_SIEGE/runtime-overrides.yml"
RUNTIME_MAP_OVERRIDES="\$DEST_SIEGE/runtime-map-overrides.yml"

# Preserve the legacy live default kit exactly once before the source-managed
# base config replaces it. SiegePlugin reads only kit.default-loadout from this
# file, so unrelated legacy settings remain inert rather than becoming hidden
# deployment overrides.
if [ ! -e "\$RUNTIME_OVERRIDES" ] && [ -f "\$DEST_SIEGE/config.yml" ]; then
    sudo install -o "$VPS_REMOTE_USER" -g "$VPS_REMOTE_USER" -m 644 \
        "\$DEST_SIEGE/config.yml" "\$RUNTIME_OVERRIDES"
    echo "==> Migrated the existing live default kit into runtime-overrides.yml"
fi
if [ ! -e "\$RUNTIME_MAP_OVERRIDES" ] && [ -f "\$DEST_SIEGE/maps.yml" ]; then
    sudo install -o "$VPS_REMOTE_USER" -g "$VPS_REMOTE_USER" -m 644 \
        "\$DEST_SIEGE/maps.yml" "\$RUNTIME_MAP_OVERRIDES"
    echo "==> Migrated existing live capture coordinates into runtime-map-overrides.yml"
fi

sudo install -o "$VPS_REMOTE_USER" -g "$VPS_REMOTE_USER" -m 644 \
    "$STAGE/siegemc-1.0-SNAPSHOT.jar" "\$DEST_PLUGINS/siegemc-1.0-SNAPSHOT.jar"
sudo install -o "$VPS_REMOTE_USER" -g "$VPS_REMOTE_USER" -m 644 \
    "$STAGE/config.yml" "\$DEST_SIEGE/config.yml"
sudo install -o "$VPS_REMOTE_USER" -g "$VPS_REMOTE_USER" -m 644 \
    "$STAGE/maps.yml" "\$DEST_SIEGE/maps.yml"

# Seed templates only. Finished in-game calibrations promote their saved world
# into this directory, so an ordinary code deployment must never replace them.
sudo install -d -o "$VPS_REMOTE_USER" -g "$VPS_REMOTE_USER" -m 755 "\$DEST_TEMPLATE_ROOT"
TEMPLATE_BACKUP_STAMP="\$(date +%Y%m%d%H%M%S)"
for MAP_ID in al_quds iron_mountain1 kansas_city_outpost kazan murmansk; do
    STAGED_TEMPLATE="$STAGE/templates/\$MAP_ID"
    TARGET_TEMPLATE="\$DEST_TEMPLATE_ROOT/\$MAP_ID"
    [ -f "\$STAGED_TEMPLATE/level.dat" ] || { echo "Staged template \$MAP_ID has no level.dat" >&2; exit 1; }
    if [ -e "\$TARGET_TEMPLATE" ]; then
        echo "==> Preserving VPS-managed template \$MAP_ID"
        continue
    fi
    TEMP_TEMPLATE="\$DEST_TEMPLATE_ROOT/.\$MAP_ID.install-\$TEMPLATE_BACKUP_STAMP"
    sudo mkdir -p "\$TEMP_TEMPLATE"
    sudo rsync -a --delete "\$STAGED_TEMPLATE/" "\$TEMP_TEMPLATE/"
    sudo chown -R "$VPS_REMOTE_USER:$VPS_REMOTE_USER" "\$TEMP_TEMPLATE"
    sudo mv "\$TEMP_TEMPLATE" "\$TARGET_TEMPLATE"
done
rm -rf "$STAGE"
EOF

echo "==> Deploy complete."
echo "==> The Minecraft server was NOT restarted. Restart it on the VPS when you're ready for this to take effect."
