#!/bin/bash
# Deletes everything added in this copy (uploads, approvals, users) and reloads the
# database file from system/database/private. The public website is not changed.
set -euo pipefail
cd "$(dirname "$0")"
read -r -p "Delete all changes in this copy and start again from the database file? (yes/no) " answer
[ "$answer" = "yes" ] || { echo "Nothing was changed."; exit 0; }
docker compose down -v
exec bash ./start.sh
