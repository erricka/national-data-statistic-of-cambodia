#!/bin/bash
# Publishes newly approved data to the website automatically: checks the database
# every 30 seconds and runs publish.sh once the approved uploads have changed and then
# stayed the same for one more check (the server finishes an approval in steps).
# Started in the background by start.sh, stopped by stop.sh. Log: system/auto-publish.log
cd "$(dirname "$0")"
published=""
seen=""
failed_at=0
while true; do
  sig=$(docker compose exec -T db psql -U postgres -d nis -tAc \
    "SELECT md5(coalesce(string_agg(concat_ws('|', upload_id, archived_at, last_change_date, last_change_time),
                                    ',' ORDER BY upload_id), ''))
     FROM datasource_history WHERE status = '01'" 2>/dev/null || true)
  now=$(date +%s)
  if [ -n "$sig" ] && [ "$sig" = "$seen" ] && \
     { [ "$sig" != "$published" ] || { [ "$failed_at" -gt 0 ] && [ $((now - failed_at)) -ge 600 ]; }; }; then
    echo "[$(date -u '+%F %T') UTC] Approved uploads changed; publishing..."
    if bash ./publish.sh; then
      failed_at=0
    else
      failed_at=$now
      echo "[$(date -u '+%F %T') UTC] Publishing failed (see above); trying again in 10 minutes."
    fi
    published=$sig
  fi
  seen=$sig
  sleep 30
done
