#!/bin/bash
# Stops the NIS admin system. Your data is kept for the next start.
cd "$(dirname "$0")"
[ -f .auto-publish.pid ] && kill "$(cat .auto-publish.pid)" 2>/dev/null; rm -f .auto-publish.pid
docker compose stop && echo "Stopped. Start again with:  bash system/start.sh"
