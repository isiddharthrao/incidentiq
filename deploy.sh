#!/usr/bin/env bash
# =============================================================================
# IncidentIQ — one-click deployment script.
#
# What it does:
#   1. Verifies Docker + Docker Compose are installed
#   2. Creates .env from .env.example on first run (and pauses for the API key)
#   3. Builds and starts the stack (MySQL + app) in detached mode
#   4. Waits up to 3 minutes for the app to respond on :8080
#   5. Prints the URL + default credentials when ready
#
# Re-running this script is safe and idempotent.
# =============================================================================

set -euo pipefail

cd "$(dirname "$0")"

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
RESET='\033[0m'

ok()    { printf "${GREEN}✓${RESET} %s\n" "$1"; }
warn()  { printf "${YELLOW}!${RESET} %s\n" "$1"; }
fail()  { printf "${RED}✗${RESET} %s\n" "$1"; exit 1; }
info()  { printf "  %s\n" "$1"; }

echo "═══════════════════════════════════════════"
echo "  IncidentIQ — one-click deployment"
echo "═══════════════════════════════════════════"
echo

# ---- Pre-flight ----
command -v docker >/dev/null 2>&1 || fail "Docker not found. Install Docker Desktop: https://docs.docker.com/desktop/"
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 not found. Update Docker Desktop."
docker info >/dev/null 2>&1 || fail "Docker daemon is not running. Start Docker Desktop and try again."
ok "Docker is running"

# ---- .env handling ----
if [ ! -f .env ]; then
    if [ ! -f .env.example ]; then
        fail ".env.example is missing — repo seems incomplete."
    fi
    cp .env.example .env
    ok "Created .env from .env.example"
    echo
    warn "Edit .env now and set APP_GEMINI_API_KEY (get one at https://aistudio.google.com/app/apikey)"
    warn "If you skip this, the app will still run but AI features will return errors."
    echo
    read -r -p "Press Enter once you've saved .env (or Ctrl-C to cancel)..."
fi

# Soft warn if key is unset
if ! grep -qE '^APP_GEMINI_API_KEY=AIza[A-Za-z0-9_-]{20,}' .env; then
    warn "APP_GEMINI_API_KEY in .env doesn't look like a Google API key (should start with AIza)."
    warn "App will still boot; AI features will return graceful errors."
    echo
fi

# ---- Build + start ----
echo "Building and starting containers (first run takes ~2-3 min for Maven dependencies)..."
docker compose up -d --build

echo
echo "Waiting for the app to become reachable on http://localhost:8080..."

# Poll for readiness — up to 3 minutes
DEADLINE=$(($(date +%s) + 180))
while [ $(date +%s) -lt $DEADLINE ]; do
    code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/login || true)
    if [ "$code" = "200" ]; then
        echo
        ok "IncidentIQ is up."
        echo
        echo "  URL:      http://localhost:8080"
        echo "  Login:    admin / admin123"
        echo
        echo "  Demo accounts (created on first boot only):"
        echo "    alice / alice123      (ENGINEER)"
        echo "    bob / bob123          (ENGINEER)"
        echo "    charlie / charlie123  (REPORTER)"
        echo "    diana / diana123      (REPORTER)"
        echo
        echo "  Manage the stack:"
        echo "    docker compose logs -f app    # tail app logs"
        echo "    docker compose down           # stop, KEEP data"
        echo "    docker compose down -v        # stop + WIPE data"
        echo "    docker compose up -d          # start again"
        echo
        exit 0
    fi
    printf "."
    sleep 3
done

echo
fail "App didn't respond within 3 minutes. Check logs:  docker compose logs app"
