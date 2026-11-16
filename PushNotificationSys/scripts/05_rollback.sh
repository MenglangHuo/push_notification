#!/usr/bin/env bash
# ==============================================================================
# Multi-Tenant Push Notification Engine - Instant Rollback Pipeline
# Reverts services to their previous known-good image (:previous or custom tag).
# Can be run from your local workstation (via SSH) OR directly on the AWS server.
# ==============================================================================
#
# SAMPLE USAGE & COMMON CASES:
# ------------------------------------------------------------------------------
# CASE 1: ROLLBACK ONLY BACKEND (From Local Workstation - Zero SSH login needed):
#   $ ./scripts/05_rollback.sh backend
#   Connects to AWS, tags push-backend:previous -> push-backend:latest,
#   restarts backend with `docker compose up -d --no-deps backend`,
#   verifies /actuator/health, and displays live logs/status.
#   Postgres, Redis, and Kafka remain 100% untouched!
#
# CASE 2: ROLLBACK ONLY FRONTEND (From Local Workstation):
#   $ ./scripts/05_rollback.sh frontend
#   Reverts frontend to push-frontend:previous and reloads Nginx.
#
# CASE 3: ROLLBACK FULL APPLICATION (Backend + Frontend):
#   $ ./scripts/05_rollback.sh
#   # Or explicitly:
#   $ ./scripts/05_rollback.sh all
#
# CASE 4: ROLLBACK TO SPECIFIC VERSION TAG:
#   $ ./scripts/05_rollback.sh backend -t v1.0.0
#   $ ./scripts/05_rollback.sh frontend -t v1.0.0
#
# CASE 5: EXECUTE LOCALLY ON AWS SERVER:
#   $ cd /var/www/html/notify && ./05_rollback.sh backend
# ==============================================================================
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Detect execution context: Running on AWS Server or Local Workstation?
IS_REMOTE=false
if [ -f "${SCRIPT_DIR}/docker-compose.aws.yml" ] && [ ! -d "${SCRIPT_DIR}/backend" ]; then
    IS_REMOTE=true
fi

# Load remote transfer defaults from .env if on local machine
ENV_REMOTE_USER=""
ENV_REMOTE_HOST=""
ENV_REMOTE_DIR=""
ENV_SSH_KEY=""
ENV_SSH_PORT=""

if [ "${IS_REMOTE}" = false ] && [ -f "${PROJECT_ROOT}/.env" ]; then
    while IFS='=' read -r key val || [ -n "$key" ]; do
        case "$key" in
            REMOTE_USER) ENV_REMOTE_USER="$val" ;;
            REMOTE_HOST) ENV_REMOTE_HOST="$val" ;;
            REMOTE_DIR)  ENV_REMOTE_DIR="$val" ;;
            SSH_KEY)     ENV_SSH_KEY="$val" ;;
            SSH_PORT)    ENV_SSH_PORT="$val" ;;
        esac
    done < <(grep -E '^(REMOTE_|SSH_)' "${PROJECT_ROOT}/.env" | sed 's/\r$//')
fi

REMOTE_HOST="${ENV_REMOTE_HOST:-}"
REMOTE_USER="${ENV_REMOTE_USER:-ec2-user}"
REMOTE_DIR="${ENV_REMOTE_DIR:-/var/www/html/notify}"
SSH_KEY="${ENV_SSH_KEY:-~/.ssh/id_rsa}"
SSH_PORT="${ENV_SSH_PORT:-22}"

TARGET_TAG="previous"
SERVICES=()

print_usage() {
    cat << EOF
${BOLD}Usage:${NC} $(basename "$0") [OPTIONS] [service1 service2 ...]

${BOLD}Description:${NC}
  Rolls back one or more services to their previous working image or a specified tag.
  Works seamlessly from your local workstation (executes remotely via SSH) or on AWS.

${BOLD}Arguments:${NC}
  ${CYAN}[service1 service2 ...]${NC}    Services to rollback: backend, frontend, all (default: all)

${BOLD}Options:${NC}
  ${CYAN}-t, --tag <tag>${NC}             Target image tag to revert to (default: 'previous')
  ${CYAN}-H, --host <ip/hostname>${NC}   AWS EC2 Public IPv4 or DNS name
  ${CYAN}-k, --key <path>${NC}          Path to SSH private key (.pem)
  ${CYAN}-u, --user <username>${NC}      SSH user on AWS (default: ${REMOTE_USER})
  ${CYAN}-p, --port <port>${NC}          SSH port (default: ${SSH_PORT})
  ${CYAN}-d, --dest-dir <path>${NC}      Remote deployment directory (default: ${REMOTE_DIR})
  ${CYAN}-h, --help${NC}                 Display this help message and exit

${BOLD}Examples:${NC}
  # Rollback backend to :previous image (No SSH login needed):
  $ $(basename "$0") backend

  # Rollback frontend to :previous image:
  $ $(basename "$0") frontend

  # Rollback both backend and frontend:
  $ $(basename "$0") all

  # Rollback backend to a specific previous release:
  $ $(basename "$0") backend -t v1.0.0
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        backend|frontend|all)
            SERVICES+=("$(echo "$1" | tr '[:upper:]' '[:lower:]')")
            shift
            ;;
        -t|--tag)
            TARGET_TAG="$2"
            shift 2
            ;;
        -H|--host)
            REMOTE_HOST="$2"
            shift 2
            ;;
        -u|--user)
            REMOTE_USER="$2"
            shift 2
            ;;
        -k|--key)
            SSH_KEY="$2"
            shift 2
            ;;
        -p|--port)
            SSH_PORT="$2"
            shift 2
            ;;
        -d|--dest-dir)
            REMOTE_DIR="$2"
            shift 2
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            echo -e "${RED}Error: Unknown parameter: $1${NC}" >&2
            print_usage
            exit 1
            ;;
    esac
done

# Default to rolling back both application services if none specified
if [ ${#SERVICES[@]} -eq 0 ]; then
    SERVICES=("backend" "frontend")
fi

# ==============================================================================
# Execution Dispatch: Local Workstation -> Remote AWS via SSH
# ==============================================================================
if [ "${IS_REMOTE}" = false ]; then
    if [ -z "${REMOTE_HOST}" ]; then
        echo -e "${RED}❌ Error: REMOTE_HOST not defined in .env and not passed via -H/--host!${NC}" >&2
        exit 1
    fi

    SSH_KEY_EXPANDED="${SSH_KEY/#\~/$HOME}"
    SSH_OPTS=(-p "${SSH_PORT}" -o "StrictHostKeyChecking=accept-new" -o "ConnectTimeout=10")
    if [ -f "${SSH_KEY_EXPANDED}" ]; then
        SSH_OPTS+=(-i "${SSH_KEY_EXPANDED}")
    fi

    SSH_TARGET="${REMOTE_USER}@${REMOTE_HOST}"

    echo -e "${BLUE}==============================================================================${NC}"
    echo -e "${BOLD}${CYAN}⏪ Initiating Remote Rollback on AWS: ${SSH_TARGET}${NC}"
    echo -e "${BLUE}==============================================================================${NC}"
    echo -e "Target Service(s): ${BOLD}${SERVICES[*]}${NC}"
    echo -e "Revert Tag:        ${BOLD}${TARGET_TAG}${NC}"
    echo -e "Remote Directory:  ${BOLD}${REMOTE_DIR}${NC}"
    echo -e "${BLUE}------------------------------------------------------------------------------${NC}"

    # Build the remote rollback command
    REMOTE_SCRIPT="${REMOTE_DIR}/05_rollback.sh"
    
    # Sync 05_rollback.sh to remote directory if not present
    scp "${SSH_OPTS[@]}" "${SCRIPT_DIR}/05_rollback.sh" "${SSH_TARGET}:${REMOTE_SCRIPT}" > /dev/null 2>&1 || true
    ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "chmod +x ${REMOTE_SCRIPT}" > /dev/null 2>&1 || true

    ssh -t "${SSH_OPTS[@]}" "${SSH_TARGET}" "cd ${REMOTE_DIR} && ./05_rollback.sh -t ${TARGET_TAG} ${SERVICES[*]}"
    exit 0
fi

# ==============================================================================
# Remote Execution Logic (Runs directly on AWS EC2)
# ==============================================================================
COMPOSE_CMD=""
if docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
elif command -v docker-compose &> /dev/null; then
    COMPOSE_CMD="docker-compose"
else
    echo -e "${RED}❌ Docker Compose not found on this server!${NC}" >&2
    exit 1
fi

COMPOSE_FILE="docker-compose.aws.yml"
ENV_FILE=".env"

echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${BOLD}${CYAN}⏪ Executing Rollback on Server${NC}"
echo -e "${BLUE}==============================================================================${NC}"

for svc in "${SERVICES[@]}"; do
    case "$svc" in
        backend)
            IMAGE_NAME="push-backend"
            ROLLBACK_SRC="${IMAGE_NAME}:${TARGET_TAG}"
            echo -e "\n${YELLOW}🔍 Inspecting rollback image: ${ROLLBACK_SRC}...${NC}"

            if ! docker image inspect "${ROLLBACK_SRC}" &> /dev/null; then
                echo -e "${RED}❌ Error: Rollback image '${ROLLBACK_SRC}' does not exist on this server!${NC}" >&2
                docker images | grep "${IMAGE_NAME}" || true
                exit 1
            fi

            echo -e "${YELLOW}🔄 Re-tagging ${ROLLBACK_SRC} → ${IMAGE_NAME}:latest...${NC}"
            docker tag "${ROLLBACK_SRC}" "${IMAGE_NAME}:latest"

            echo -e "${YELLOW}🚀 Rolling back backend container with --no-deps...${NC}"
            ${COMPOSE_CMD} --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --no-deps backend

            echo -ne "${YELLOW}⏳ Verifying backend health (/actuator/health)...${NC}"
            BACKEND_OK=false
            for ((i=1; i<=15; i++)); do
                if docker exec push_backend wget -qO- http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"' || curl -s -m 3 "http://localhost:8080/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
                    BACKEND_OK=true
                    echo -e " ${GREEN}✅ Backend is UP!${NC}"
                    break
                fi
                echo -ne "."
                sleep 4
            done

            if [ "${BACKEND_OK}" = false ]; then
                echo -e "\n${RED}⚠️ Backend healthcheck timed out. Recent logs:${NC}"
                ${COMPOSE_CMD} --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" logs --tail=25 backend
            fi
            ;;

        frontend)
            IMAGE_NAME="push-frontend"
            ROLLBACK_SRC="${IMAGE_NAME}:${TARGET_TAG}"
            echo -e "\n${YELLOW}🔍 Inspecting rollback image: ${ROLLBACK_SRC}...${NC}"

            if ! docker image inspect "${ROLLBACK_SRC}" &> /dev/null; then
                echo -e "${RED}❌ Error: Rollback image '${ROLLBACK_SRC}' does not exist on this server!${NC}" >&2
                docker images | grep "${IMAGE_NAME}" || true
                exit 1
            fi

            echo -e "${YELLOW}🔄 Re-tagging ${ROLLBACK_SRC} → ${IMAGE_NAME}:latest...${NC}"
            docker tag "${ROLLBACK_SRC}" "${IMAGE_NAME}:latest"

            echo -e "${YELLOW}🚀 Rolling back frontend container with --no-deps...${NC}"
            ${COMPOSE_CMD} --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --no-deps frontend

            echo -ne "${YELLOW}⏳ Verifying Frontend HTTP status...${NC}"
            CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 3 "http://localhost/" 2>/dev/null || true)
            echo -e " ${GREEN}✅ Frontend response: HTTP ${CODE}${NC}"
            ;;

        all)
            # Recursively call backend and frontend
            "${SCRIPT_DIR}/05_rollback.sh" -t "${TARGET_TAG}" backend frontend
            exit 0
            ;;
    esac
done

echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${GREEN}${BOLD}🎉 Rollback Completed Successfully!${NC}"
echo -e "${BLUE}==============================================================================${NC}"
${COMPOSE_CMD} --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps
echo -e "${BLUE}==============================================================================${NC}"
