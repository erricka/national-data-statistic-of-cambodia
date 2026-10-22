#!/bin/bash
# First start only: load the newest database file in system/database/private/.
# Accepts plain SQL (.sql / .sql.gz, e.g. from system/backup.sh) and pg_dump
# custom-format backups (.dump).
set -euo pipefail
f=$(ls -t /private/*.sql /private/*.sql.gz /private/*.dump 2>/dev/null | head -n 1 || true)
if [ -z "$f" ]; then
  echo "No database file found in system/database/private/" >&2
  exit 1
fi
echo "Loading $(basename "$f")..."
case "$f" in
  *.dump) pg_restore --no-owner --no-privileges --exit-on-error \
            --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" "$f" ;;
  *.gz)   gunzip -c "$f" | psql -v ON_ERROR_STOP=1 -q --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" >/dev/null ;;
  *)      psql -v ON_ERROR_STOP=1 -q --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$f" >/dev/null ;;
esac
