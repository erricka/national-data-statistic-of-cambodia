#!/bin/bash
# Shown when a Codespace opens.
cat <<'MSG'

  NIS admin system
  ----------------
  1. Drag the database file into  system/database/private  (Explorer on the left).
  2. Start:    bash system/start.sh      -> prints the admin portal link and login
  3. Publish:  bash system/publish.sh    -> puts approved data on the public website
  Stop: bash system/stop.sh   ·   Start over: bash system/reset.sh   ·   Guide: system/README.md

MSG
