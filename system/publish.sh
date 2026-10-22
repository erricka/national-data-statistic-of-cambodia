#!/bin/bash
# Puts the approved data of this copy on the public website:
#   1. gets the latest website code from GitHub
#   2. checks that nothing published from another copy would be lost
#   3. exports the approved data, saves it to GitHub; the website rebuilds in 1-2 minutes
#   bash system/publish.sh
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f system/.env ] || { echo "Start the system first:  bash system/start.sh"; exit 1; }
set -a; . system/.env; set +a
export DATABASE_URL="postgres://postgres:${DB_PASSWORD}@127.0.0.1:15432/nis"
export NIS_API_URL="http://127.0.0.1:8080/api/nis-portal"

if ! curl -sf -o /dev/null -X POST -H 'Content-Type: application/json' -d '{}' "$NIS_API_URL/CATI004"; then
  echo "The NIS system is not running. Start it first:  bash system/start.sh"; exit 1
fi

branch=$(git rev-parse --abbrev-ref HEAD)
if [ "$branch" != "main" ]; then
  echo "Publishing works from the main branch (you are on '$branch'). Run:  git checkout main"; exit 1
fi
echo "Getting the latest website from GitHub..."
git pull -q --ff-only origin main
if [ ! -d node_modules ] || [ package-lock.json -nt node_modules/.package-lock.json ]; then
  npm ci --no-audit --no-fund --loglevel=error
fi

node system/lib/check-releases.mjs
npm run refresh -- --no-deploy "$@"
