#!/bin/bash
# Shown when a Codespace opens.
cd "$(dirname "$0")"
if [ -f .env ]; then
  . ./.env
  if [ -n "${CODESPACE_NAME:-}" ]; then
    url="https://${CODESPACE_NAME}-8080.${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}/login"
  else
    url="http://localhost:8080/login"
  fi
  cat <<MSG

  NIS admin system (starts by itself; give it a minute after the Codespace opens)
  -------------------------------------------------------------------------------
  Admin portal:  $url
  Username:      $ADMIN_USER
  Password:      $ADMIN_PASSWORD

  Approved data goes to the public website automatically (log: system/auto-publish.log).
  Not working?  bash system/start.sh   ·   Backup: bash system/backup.sh   ·   Guide: system/README.md

MSG
else
  cat <<'MSG'

  NIS admin system: first-time setup
  ----------------------------------
  1. Drag the database file into  system/database/private  (Explorer on the left).
  2. Run:  bash system/start.sh     -> prints the admin portal link and login
  After that, the system starts by itself whenever this Codespace opens, and approved
  data goes to the public website automatically. Guide: system/README.md

MSG
fi
