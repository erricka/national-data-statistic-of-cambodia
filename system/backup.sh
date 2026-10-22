#!/bin/bash
# Saves the whole database of this copy (uploads, approvals, users) to
# system/database/private/nis-backup-<date>.sql.gz. Download it (right-click ->
# Download) to keep it, or to give it to the colleague who publishes next.
# It contains staff accounts: share it only privately, never on GitHub or in a group chat.
set -euo pipefail
cd "$(dirname "$0")"
file="database/private/nis-backup-$(date -u +%Y%m%d-%H%M).sql.gz"
docker compose exec -T db pg_dump -U postgres --no-owner --no-privileges nis | gzip > "$file"
echo "Saved $(du -h "$file" | cut -f1) to system/$file"
