#!/usr/bin/env bash
# ==============================================================================
# Multi-Tenant Push Notification Engine - AWS Transfer & Remote Pipeline
# Transfers Docker images and configs to AWS via rsync/scp or direct SSH streaming.
# ==============================================================================
#
# SAMPLE USAGE & COMMON CASES:
# ------------------------------------------------------------------------------
# CASE 1: STREAM & AUTO-DEPLOY ENTIRE STACK (Recommended - Zero intermediate disk files)
#   $ ./03_transfer_image.sh --stream --run
#   Pipes docker save | gzip directly over SSH into the AWS Docker daemon for both
#   backend and frontend, uploads compose/env configs, and executes 04_deploy.sh.
#
# CASE 2: STREAM & DEPLOY ONLY BACKEND (Zero Downtime for Postgres/Kafka/Redis)
#   $ ./03_transfer_image.sh --stream --services backend --run
#   # Or:
#   $ ./03_transfer_image.sh --stream --backend-only --run
#   Streams only push-backend:latest to AWS and executes `04_deploy.sh backend`.
#   On the server, docker-compose updates only the backend container with --no-deps.
#   PostgreSQL, Redis, and Kafka remain 100% UP with zero dropped connections!
#
# CASE 3: STREAM & DEPLOY ONLY FRONTEND (Vue 3 / Vite / Nginx UI reload)
#   $ ./03_transfer_image.sh --stream --services frontend --run
#   # Or:
#   $ ./03_transfer_image.sh --stream --frontend-only --run
#   Streams only push-frontend:latest to AWS and reloads Nginx.
#
# CASE 4: STREAM INFRASTRUCTURE IMAGES (Postgres, Redis, Kafka)
#   $ ./03_transfer_image.sh --stream --services postgres --run
#   $ ./03_transfer_image.sh --stream --services redis --run
#   $ ./03_transfer_image.sh --stream --services kafka --run
#   $ ./03_transfer_image.sh --stream --infra-only --run
#
# CASE 5: ARCHIVE FILE TRANSFER MODE (Best for slow or unstable internet)
#   $ ./03_transfer_image.sh -i dist-images --run
#   $ ./03_transfer_image.sh -i dist-images --services backend --run
#   Uses rsync/scp to upload .tar.gz archives from dist-images/ to AWS.
#
# CASE 6: OVERRIDE AWS CONNECTION PARAMETERS EXPLICITLY
#   $ ./03_transfer_image.sh -H 44.236.62.255 -u ec2-user -k ~/.ssh/id_rsa \
#                            -d /var/www/html/notify --stream --run
#
# AUTOMATIC .ENV INTEGRATION:
# ------------------------------------------------------------------------------
# The script automatically loads remote parameters from .env if present:
#   REMOTE_USER=ec2-user
#   REMOTE_HOST=44.236.62.255
#   REMOTE_DIR=/var/www/html/notify
#   SSH_KEY=~/.ssh/id_rsa
#   SSH_PORT=22
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

# Load remote transfer defaults from .env if present
ENV_REMOTE_USER=""
ENV_REMOTE_HOST=""
ENV_REMOTE_DIR=""
ENV_SSH_KEY=""
ENV_SSH_PORT=""

if [ -f "${PROJECT_ROOT}/.env" ]; then
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
REMOTE_USER="${ENV_REMOTE_USER:-ubuntu}"
SSH_KEY="${ENV_SSH_KEY:-}"
SSH_PORT="${ENV_SSH_PORT:-22}"
DEST_DIR="${ENV_REMOTE_DIR:-~/push-platform}"
IMAGES_DIR="${PROJECT_ROOT}/dist-images"
BACKEND_TAG="latest"
FRONTEND_TAG="latest"
POSTGRES_IMAGE="postgres:17-alpine"
REDIS_IMAGE="redis:7-alpine"
KAFKA_IMAGE="apache/kafka:3.7.0"

STREAM_MODE=false
AUTO_DEPLOY=false
TRANSFER_BACKEND=false
TRANSFER_FRONTEND=false
TRANSFER_POSTGRES=false
TRANSFER_REDIS=false
TRANSFER_KAFKA=false
DEFAULT_MODE=true
SHOW_LOGS=false
SCRIPTS_ONLY=false

print_usage() {
    cat << EOF
${BOLD}Usage:${NC} $(basename "$0") [OPTIONS] [service1 service2 ...]

${BOLD}Description:${NC}
  Securely transfers Docker images, compose files, and deployment scripts from your
  local / bare-metal build machine to an AWS EC2 instance.
  Supports granular transfer of specific application or infrastructure services.

${BOLD}Arguments:${NC}
  ${CYAN}[service1 service2 ...]${NC}    Target services: backend, frontend, postgres, redis, kafka, infra, all

${BOLD}Required Parameters (or set in .env):${NC}
  ${CYAN}-H, --host <ip/hostname>${NC}   AWS EC2 Public IPv4 or DNS name
  ${CYAN}-k, --key <path>${NC}          Path to SSH private key (.pem)

${BOLD}Options:${NC}
  ${CYAN}-s, --stream${NC}               Direct SSH stream mode (no local/remote tarball files needed)
  ${CYAN}-r, --run, --auto-deploy${NC}   Execute 04_deploy.sh immediately after transfer
  ${CYAN}-l, --logs${NC}                 Stream live logs from remote AWS containers (No SSH login needed!)
  ${CYAN}-u, --user <username>${NC}      SSH user on AWS (default: ${REMOTE_USER}, e.g. 'ubuntu', 'ec2-user')
  ${CYAN}-p, --port <port>${NC}          SSH port (default: ${SSH_PORT})
  ${CYAN}-d, --dest-dir <path>${NC}      Remote deployment directory (default: ${DEST_DIR})
  ${CYAN}-i, --images-dir <path>${NC}    Directory with local .tar.gz archives (default: dist-images)
  ${CYAN}-b, --backend-tag <tag>${NC}   Backend image tag (default: ${BACKEND_TAG})
  ${CYAN}-f, --frontend-tag <tag>${NC}  Frontend image tag (default: ${FRONTEND_TAG})
  ${CYAN}--services <list>${NC}          Comma-separated services: backend,frontend,postgres,redis,kafka,infra,all
  ${CYAN}--backend-only${NC}            Transfer only backend image
  ${CYAN}--frontend-only${NC}           Transfer only frontend image
  ${CYAN}--infra-only${NC}              Transfer only infrastructure images (postgres, redis, kafka)
  ${CYAN}--scripts-only${NC}            Transfer only scripts & configs (.env, compose, deploy, rollback)
  ${CYAN}--all${NC}                     Transfer all 5 images
  ${CYAN}-h, --help${NC}                 Display this help message and exit

${BOLD}Examples:${NC}
  # 1. Stream & Deploy EVERYTHING:
  $ $(basename "$0") --stream --run

  # 2. Stream & Deploy ONLY Backend (Positional argument):
  $ $(basename "$0") --stream --run backend

  # 3. Stream & Deploy ONLY Frontend:
  $ $(basename "$0") --stream --run frontend

  # 4. Stream & Deploy Infrastructure (PostgreSQL / Redis):
  $ $(basename "$0") --stream --run postgres
  $ $(basename "$0") --stream --run postgres redis

  # 5. Stream Live Logs from AWS without logging in:
  $ $(basename "$0") --logs
  $ $(basename "$0") --logs backend
EOF
}

parse_services() {
    local input="$1"
    IFS=',' read -ra ADDR <<< "$input"
    for svc in "${ADDR[@]}"; do
        case "$(echo "$svc" | tr '[:upper:]' '[:lower:]' | xargs)" in
            backend)  TRANSFER_BACKEND=true ;;
            frontend) TRANSFER_FRONTEND=true ;;
            postgres|db) TRANSFER_POSTGRES=true ;;
            redis)    TRANSFER_REDIS=true ;;
            kafka)    TRANSFER_KAFKA=true ;;
            infra)
                TRANSFER_POSTGRES=true
                TRANSFER_REDIS=true
                TRANSFER_KAFKA=true
                ;;
            all)
                TRANSFER_BACKEND=true
                TRANSFER_FRONTEND=true
                TRANSFER_POSTGRES=true
                TRANSFER_REDIS=true
                TRANSFER_KAFKA=true
                ;;
            *)
                echo -e "${RED}Error: Unknown service '$svc'. Available: backend, frontend, postgres, redis, kafka, infra, all${NC}" >&2
                exit 1
                ;;
        esac
    done
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        backend|frontend|postgres|db|redis|kafka|infra|all)
            DEFAULT_MODE=false
            parse_services "$1"
            shift
            ;;
        -l|--logs)
            SHOW_LOGS=true
            shift
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
            DEST_DIR="$2"
            shift 2
            ;;
        -i|--images-dir)
            IMAGES_DIR="$2"
            shift 2
            ;;
        -b|--backend-tag)
            BACKEND_TAG="$2"
            shift 2
            ;;
        -f|--frontend-tag)
            FRONTEND_TAG="$2"
            shift 2
            ;;
        --service|--services)
            DEFAULT_MODE=false
            parse_services "$2"
            shift 2
            ;;
        --backend-only)
            DEFAULT_MODE=false
            TRANSFER_BACKEND=true
            TRANSFER_FRONTEND=false
            shift
            ;;
        --frontend-only)
            DEFAULT_MODE=false
            TRANSFER_BACKEND=false
            TRANSFER_FRONTEND=true
            shift
            ;;
        --infra-only)
            DEFAULT_MODE=false
            TRANSFER_POSTGRES=true
            TRANSFER_REDIS=true
            TRANSFER_KAFKA=true
            shift
            ;;
        --all)
            DEFAULT_MODE=false
            TRANSFER_BACKEND=true
            TRANSFER_FRONTEND=true
            TRANSFER_POSTGRES=true
            TRANSFER_REDIS=true
            TRANSFER_KAFKA=true
            shift
            ;;
        --scripts-only|--configs-only)
            SCRIPTS_ONLY=true
            shift
            ;;
        -s|--stream)
            STREAM_MODE=true
            shift
            ;;
        -r|--auto-deploy|--run)
            AUTO_DEPLOY=true
            shift
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

# Default mode transfers custom application images (backend + frontend)
if [ "${DEFAULT_MODE}" = true ]; then
    TRANSFER_BACKEND=true
    TRANSFER_FRONTEND=true
fi

if [ -z "${REMOTE_HOST}" ]; then
    echo -e "${RED}❌ Error: AWS host (-H/--host) is required.${NC}" >&2
    print_usage
    exit 1
fi

SSH_OPTS=(-p "${SSH_PORT}" -o "StrictHostKeyChecking=accept-new" -o "ConnectTimeout=10")
if [ -n "${SSH_KEY}" ]; then
    SSH_KEY="${SSH_KEY/#\~/$HOME}"
    if [ ! -f "${SSH_KEY}" ]; then
        echo -e "${RED}❌ Error: SSH key not found at: ${SSH_KEY}${NC}" >&2
        exit 1
    fi
    chmod 400 "${SSH_KEY}" 2>/dev/null || true
    SSH_OPTS+=(-i "${SSH_KEY}")
fi

SSH_TARGET="${REMOTE_USER}@${REMOTE_HOST}"

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BOLD}${CYAN}🚀 Shipping to AWS: ${SSH_TARGET}${NC}"
echo -e "${BLUE}==============================================================================${NC}"
echo -e "Remote Destination: ${BOLD}${DEST_DIR}${NC}"
echo -e "Transfer Mode:      ${BOLD}$( [ "${STREAM_MODE}" = true ] && echo "Direct SSH Stream" || echo "Archive File Transfer" )${NC}"
echo -e "Auto-Deploy:        ${BOLD}${AUTO_DEPLOY}${NC}"
echo -e "${BLUE}------------------------------------------------------------------------------${NC}"

# Test SSH connection
echo -e "${YELLOW}🔍 Testing SSH connectivity to ${SSH_TARGET}...${NC}"
if ! ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "echo 'SSH Connection OK'" > /dev/null 2>&1; then
    echo -e "${RED}❌ Failed to connect to ${SSH_TARGET}. Please verify IP, Security Group (port ${SSH_PORT}), and SSH key.${NC}" >&2
    exit 1
fi
echo -e "${GREEN}✅ SSH connection verified.${NC}"

# If --logs flag was requested, stream logs and exit (No SSH login needed!)
if [ "${SHOW_LOGS}" = true ]; then
    LOG_ARGS=""
    if [ "${DEFAULT_MODE}" = false ]; then
        SVCS=()
        [ "${TRANSFER_BACKEND}" = true ] && SVCS+=("backend")
        [ "${TRANSFER_FRONTEND}" = true ] && SVCS+=("frontend")
        [ "${TRANSFER_POSTGRES}" = true ] && SVCS+=("postgres")
        [ "${TRANSFER_REDIS}" = true ] && SVCS+=("redis")
        [ "${TRANSFER_KAFKA}" = true ] && SVCS+=("kafka")
        LOG_ARGS="${SVCS[*]}"
    fi
    echo -e "\n${CYAN}📜 Streaming live container logs from AWS (${SSH_TARGET}) [${LOG_ARGS:-All Services}]... (Ctrl+C to exit)${NC}"
    ssh -t "${SSH_OPTS[@]}" "${SSH_TARGET}" "cd ${DEST_DIR} && (docker compose -f docker-compose.aws.yml logs -f --tail=100 ${LOG_ARGS} 2>/dev/null || docker-compose -f docker-compose.aws.yml logs -f --tail=100 ${LOG_ARGS})"
    exit 0
fi

# Ensure remote deployment directory exists
ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "mkdir -p ${DEST_DIR}"

# ------------------------------------------------------------------------------
# Transfer Deploy Configs (Compose file, environment templates, remote scripts)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}📋 Transferring deployment configs to AWS...${NC}"
FILES_TO_COPY=(
    "${PROJECT_ROOT}/docker-compose.aws.yml"
    "${PROJECT_ROOT}/.env.production.example"
    "${SCRIPT_DIR}/04_deploy.sh"
    "${SCRIPT_DIR}/05_rollback.sh"
    "${SCRIPT_DIR}/06_seed_data.sh"
    "${SCRIPT_DIR}/verify_platform.sh"
)

if [ -f "${PROJECT_ROOT}/.env" ]; then
    FILES_TO_COPY+=("${PROJECT_ROOT}/.env")
fi

if command -v rsync &> /dev/null; then
    rsync -avzP -e "ssh ${SSH_OPTS[*]}" "${FILES_TO_COPY[@]}" "${SSH_TARGET}:${DEST_DIR}/"
else
    scp "${SSH_OPTS[@]}" "${FILES_TO_COPY[@]}" "${SSH_TARGET}:${DEST_DIR}/"
fi
ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "chmod +x ${DEST_DIR}/04_deploy.sh ${DEST_DIR}/05_rollback.sh ${DEST_DIR}/06_seed_data.sh ${DEST_DIR}/verify_platform.sh"
echo -e "${GREEN}✅ Deployment configs transferred.${NC}"

if [ "${SCRIPTS_ONLY}" = true ]; then
    echo -e "${GREEN}✨ Scripts and configuration files successfully transferred to AWS. Exiting (--scripts-only).${NC}"
    exit 0
fi

# ------------------------------------------------------------------------------
# Image Transfer
# ------------------------------------------------------------------------------
if [ "${STREAM_MODE}" = true ]; then
    # Direct streaming mode (pipes docker save | gzip | ssh gunzip | docker load)
    echo -e "\n${CYAN}⚡ Direct Streaming Mode: Piping images directly over SSH...${NC}"

    stream_image() {
        local IMG="$1"
        local LABEL="$2"
        echo -e "${YELLOW}📡 Streaming ${IMG} (${LABEL}) to AWS Docker daemon...${NC}"
        if ! docker image inspect "${IMG}" &> /dev/null; then
            echo -e "${YELLOW}⚠️  Image ${IMG} not found locally. Attempting docker pull...${NC}"
            if ! docker pull "${IMG}"; then
                echo -e "${RED}❌ Error: Failed to find or pull ${IMG}!${NC}" >&2
                exit 1
            fi
        fi
        docker save "${IMG}" | gzip -c | ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "gunzip -c | docker load"
        echo -e "${GREEN}✅ ${LABEL} stream completed.${NC}"
    }

    if [ "${TRANSFER_BACKEND}" = true ]; then
        stream_image "push-backend:${BACKEND_TAG}" "Backend"
    fi
    if [ "${TRANSFER_FRONTEND}" = true ]; then
        stream_image "push-frontend:${FRONTEND_TAG}" "Frontend"
    fi
    if [ "${TRANSFER_POSTGRES}" = true ]; then
        stream_image "${POSTGRES_IMAGE}" "PostgreSQL"
    fi
    if [ "${TRANSFER_REDIS}" = true ]; then
        stream_image "${REDIS_IMAGE}" "Redis"
    fi
    if [ "${TRANSFER_KAFKA}" = true ]; then
        stream_image "${KAFKA_IMAGE}" "Kafka"
    fi

else
    # Archive mode (transfers pre-saved .tar.gz archives)
    echo -e "\n${CYAN}📦 Archive Mode: Transferring compressed tar archives...${NC}"

    ARCHIVES=()
    [ "${TRANSFER_BACKEND}" = true ] && ARCHIVES+=("${IMAGES_DIR}/push-backend-${BACKEND_TAG}.tar.gz")
    [ "${TRANSFER_FRONTEND}" = true ] && ARCHIVES+=("${IMAGES_DIR}/push-frontend-${FRONTEND_TAG}.tar.gz")
    [ "${TRANSFER_POSTGRES}" = true ] && ARCHIVES+=("${IMAGES_DIR}/postgres-17-alpine.tar.gz")
    [ "${TRANSFER_REDIS}" = true ] && ARCHIVES+=("${IMAGES_DIR}/redis-7-alpine.tar.gz")
    [ "${TRANSFER_KAFKA}" = true ] && ARCHIVES+=("${IMAGES_DIR}/kafka-3.7.0.tar.gz")

    for arch in "${ARCHIVES[@]}"; do
        if [ ! -f "${arch}" ]; then
            echo -e "${YELLOW}⚠️  ${arch} not found. Triggering 02_save-images.sh...${NC}"
            SAVE_ARGS=()
            [ "${TRANSFER_BACKEND}" = true ] && SAVE_ARGS+=("backend")
            [ "${TRANSFER_FRONTEND}" = true ] && SAVE_ARGS+=("frontend")
            [ "${TRANSFER_POSTGRES}" = true ] && SAVE_ARGS+=("postgres")
            [ "${TRANSFER_REDIS}" = true ] && SAVE_ARGS+=("redis")
            [ "${TRANSFER_KAFKA}" = true ] && SAVE_ARGS+=("kafka")
            IFS=',' SAVE_CSV="${SAVE_ARGS[*]}"
            "${SCRIPT_DIR}/02_save-images.sh" -b "${BACKEND_TAG}" -f "${FRONTEND_TAG}" -o "${IMAGES_DIR}" --services "${SAVE_CSV}"
            break
        fi
    done

    if command -v rsync &> /dev/null; then
        rsync -avzP -e "ssh ${SSH_OPTS[*]}" "${ARCHIVES[@]}" "${SSH_TARGET}:${DEST_DIR}/"
    else
        scp "${SSH_OPTS[@]}" "${ARCHIVES[@]}" "${SSH_TARGET}:${DEST_DIR}/"
    fi
    echo -e "${GREEN}✅ Image archives successfully uploaded to AWS.${NC}"
fi

# ------------------------------------------------------------------------------
# Remote Deploy Execution
# ------------------------------------------------------------------------------
DEPLOY_ARGS=""
if [ "${DEFAULT_MODE}" = false ]; then
    SVCS=()
    [ "${TRANSFER_BACKEND}" = true ] && SVCS+=("backend")
    [ "${TRANSFER_FRONTEND}" = true ] && SVCS+=("frontend")
    [ "${TRANSFER_POSTGRES}" = true ] && SVCS+=("postgres")
    [ "${TRANSFER_REDIS}" = true ] && SVCS+=("redis")
    [ "${TRANSFER_KAFKA}" = true ] && SVCS+=("kafka")
    DEPLOY_ARGS="${SVCS[*]}"
fi

# if [ "${AUTO_DEPLOY}" = true ]; then
#     echo -e "\n${BLUE}==============================================================================${NC}"
#     echo -e "${BOLD}${CYAN}🚀 Executing Remote Deployment via SSH...${NC}"
#     [ -n "${DEPLOY_ARGS}" ] && echo -e "Target Service(s): ${BOLD}${DEPLOY_ARGS}${NC}"
#     echo -e "${BLUE}==============================================================================${NC}"
    
#     ssh -t "${SSH_OPTS[@]}" "${SSH_TARGET}" "cd ${DEST_DIR} && ./04_deploy.sh ${DEPLOY_ARGS}"
# fi

echo -e "\n${GREEN}✨ Pipeline completed!${NC}"
if [ "${AUTO_DEPLOY}" = false ]; then
    echo -e "To execute the deployment on AWS, run:"
    echo -e "  ${BOLD}ssh ${SSH_TARGET} \"cd ${DEST_DIR} && ./04_deploy.sh ${DEPLOY_ARGS}\"${NC}"
fi
