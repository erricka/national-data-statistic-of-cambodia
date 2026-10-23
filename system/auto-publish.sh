#!/bin/bash
# Publishes newly approved data to the website automatically: checks the database
# every minute and runs publish.sh when the set of approved uploads changes.
# Started in the background by start.sh, stopped by stop.sh. Log: system/auto-publish.log
cd "$(dirname "$0")"
last=""
failed_at=0
while true; do
  sig=$(docker compose exec -T db psql -U postgres -d nis -tAc \
    "SELECT md5(coalesce(string_agg(upload_id, ',' ORDER BY upload_id), '')) FROM datasource_history WHERE status = '01'" \
    2>/dev/null || true)
  now=$(date +%s)
  # Publish when approvals changed; retry a failed publish after 10 minutes.
  if [ -n "$sig" ] && { [ "$sig" != "$last" ] || { [ "$failed_at" -gt 0 ] && [ $((now - failed_at)) -ge 600 ]; }; }; then
    echo "[$(date -u '+%F %T') UTC] Approved uploads changed; publishing..."
    if bash ./publish.sh; then
      failed_at=0
    else
      failed_at=$now
      echo "[$(date -u '+%F %T') UTC] Publishing failed (see above); trying again in 10 minutes."
    fi
    last=$sig
  fi
  sleep 60
done
