#!/bin/bash
# Starts the NIS admin system: database, NIS server and admin portal.
#   bash system/start.sh
set -euo pipefail
cd "$(dirname "$0")"

# Docker may still be starting right after the Codespace opens.
for i in $(seq 1 30); do docker info >/dev/null 2>&1 && break; sleep 2; done

shopt -s nullglob
db_files=(database/private/*.sql database/private/*.sql.gz database/private/*.dump)
if [ ${#db_files[@]} -eq 0 ] && ! docker volume inspect nis-system_db-data >/dev/null 2>&1; then
  cat <<'MSG'

  The database file is missing.

  Drag the database file (for example 01-nis-data.sql.gz from the practice-system
  zip) into the folder  system/database/private  in the Explorer on the left,
  then run this command again:   bash system/start.sh

MSG
  exit 1
fi

# Secrets for this copy only, created once and never committed.
rand() { LC_ALL=C tr -dc "$1" </dev/urandom | head -c "$2" || true; }
policy_password() { echo "$1-$(rand 'A-Z' 2)$(rand 'a-z' 4)$(rand '0-9' 3)$(rand 'A-Za-z0-9' 3)"; }
if [ ! -f .env ]; then
  {
    echo "ADMIN_USER=nis-admin"
    echo "ADMIN_PASSWORD=$(policy_password Nis)"
    echo "DB_PASSWORD=$(rand 'A-Za-z0-9' 24)"
    echo "RESET_PASSWORD=$(policy_password Reset)"
    echo "APPROVAL_INTERNAL_SECRET=$(rand 'A-Za-z0-9' 32)"
  } > .env
  chmod 600 .env
fi
set -a; . ./.env; set +a

# The database container reads the file as its own user.
[ ${#db_files[@]} -eq 0 ] || chmod a+r "${db_files[@]}"

echo "Starting the NIS system (the first start builds everything and takes 5-10 minutes)..."
docker compose up -d --build

printf "Waiting for the NIS server"
for i in $(seq 1 180); do
  if curl -sf -o /dev/null -X POST -H 'Content-Type: application/json' -d '{}' \
       http://127.0.0.1:8080/api/nis-portal/CATI004; then
    echo " ready."
    break
  fi
  if [ "$i" -eq 180 ]; then
    echo; echo "The server did not start. See what went wrong with:  docker compose -f system/docker-compose.yml logs"
    exit 1
  fi
  printf "."; sleep 5
done

# Publish approved data to the website automatically (AUTO_PUBLISH=no in system/.env turns it off).
if [ "${AUTO_PUBLISH:-yes}" = "yes" ] && \
   ! { [ -f .auto-publish.pid ] && kill -0 "$(cat .auto-publish.pid)" 2>/dev/null; }; then
  nohup bash ./auto-publish.sh >> auto-publish.log 2>&1 &
  echo $! > .auto-publish.pid
fi

shared=""
if [ -n "${CODESPACE_NAME:-}" ]; then
  url="https://${CODESPACE_NAME}-8080.${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}/login"
  # Let staff open the admin portal without a GitHub account (SHARE_LINK=no in system/.env turns it off).
  if [ "${SHARE_LINK:-yes}" = "yes" ]; then
    if gh codespace ports visibility 8080:public -c "$CODESPACE_NAME" >/dev/null 2>&1; then
      shared=yes
    else
      shared=no
    fi
  fi
else
  url="http://localhost:8080/login"
fi
echo
echo "  Admin portal:  $url"
if [ "$shared" = "yes" ]; then
  echo "                 Staff can open this link while this Codespace is running, and sign in"
  echo "                 with their usual NIS account."
elif [ "$shared" = "no" ]; then
  echo "                 To let staff open it: Ports tab (next to Terminal) -> right-click port 8080"
  echo "                 -> Port Visibility -> Public."
fi
admin_ok=$(docker compose exec -T db psql -U postgres -d nis -tAc \
  "SELECT 1 FROM users WHERE user_id = '$ADMIN_USER'" 2>/dev/null || true)
if [ "$admin_ok" = "1" ]; then
  echo "  Your login:    $ADMIN_USER  /  $ADMIN_PASSWORD"
else
  echo "  The database was not set up completely. Its log ends with:"
  docker compose logs --no-log-prefix db 2>&1 | grep -iE "error|fatal|loading|running" | tail -n 5 | sed 's/^/    /'
  echo "  Fix the cause, then start again from the database file with:  bash system/reset.sh"
fi
echo
echo "  Approved data goes to the public website automatically within a few minutes."
echo "  (Progress: system/auto-publish.log. To publish right away: bash system/publish.sh)"
echo
