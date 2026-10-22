#!/bin/bash
# Stops the NIS admin system. Your data is kept for the next start.
cd "$(dirname "$0")" && docker compose stop && echo "Stopped. Start again with:  bash system/start.sh"
