#!/usr/bin/env bash
# ==============================================================================
# Multi-Tenant Push Notification Engine - Docker Image Exporter
# Saves Docker images to compressed archives (.tar.gz) with sha256 checksums.
# ==============================================================================
#
# SAMPLE USAGE & COMMON CASES:
# ------------------------------------------------------------------------------
# CASE 1: SAVE APPLICATION IMAGES (Backend + Frontend with default tags)
#   $ ./02_save-images.sh
#   Creates dist-images/push-backend-latest.tar.gz, dist-images/push-frontend-latest.tar.gz
#   and dist-images/checksums.sha256.
#
# CASE 2: SAVE ONLY BACKEND
#   $ ./02_save-images.sh --backend-only
#   # Or:
#   $ ./02_save-images.sh -s backend
#
# CASE 3: SAVE ONLY FRONTEND
#   $ ./02_save-images.sh --frontend-only
#   # Or:
#   $ ./02_save-images.sh -s frontend
#
# CASE 4: SAVE INFRASTRUCTURE IMAGES (Postgres, Redis, Kafka)
#   $ ./02_save-images.sh --infra-only
#   # Or save only Postgres:
#   $ ./02_save-images.sh -s postgres
#
# CASE 5: SAVE ALL 5 IMAGES (Full Fleet Archive for Air-Gapped Transfer)
#   $ ./02_save-images.sh --all -o /tmp/deploy-pkg
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

BACKEND_TAG="latest"
FRONTEND_TAG="latest"
POSTGRES_IMAGE="postgres:17-alpine"
REDIS_IMAGE="redis:7-alpine"
KAFKA_IMAGE="apache/kafka:3.7.0"
OUTPUT_DIR="${PROJECT_ROOT}/dist-images"

SAVE_BACKEND=false
SAVE_FRONTEND=false
SAVE_POSTGRES=false
SAVE_REDIS=false
SAVE_KAFKA=false
DEFAULT_MODE=true

print_usage() {
    cat << EOF
${BOLD}Usage:${NC} $(basename "$0") [OPTIONS] [service1 service2 ...]

${BOLD}Description:${NC}
  Exports Docker images into gzip-compressed tar archives (.tar.gz) suitable for
  transferring to AWS EC2 or offline bare-metal servers.
  Supports custom application images (backend, frontend) and infrastructure images (postgres, redis, kafka).

${BOLD}Arguments:${NC}
  ${CYAN}[service1 service2 ...]${NC}    Services to save: backend, frontend, postgres, redis, kafka, infra, all

${BOLD}Options:${NC}
  ${CYAN}-b, --backend-tag <tag>${NC}  Backend image tag to save (default: ${BACKEND_TAG})
  ${CYAN}-f, --frontend-tag <tag>${NC} Frontend image tag to save (default: ${FRONTEND_TAG})
  ${CYAN}-o, --output-dir <path>${NC}  Destination directory (default: dist-images)
  ${CYAN}--backend-only${NC}            Save only backend (push-backend)
  ${CYAN}--frontend-only${NC}           Save only frontend (push-frontend)
  ${CYAN}--infra-only${NC}              Save only infrastructure (postgres, redis, kafka)
  ${CYAN}--all${NC}                     Save all 5 images (backend, frontend, postgres, redis, kafka)
  ${CYAN}-h, --help${NC}                Display this help message and exit

${BOLD}Examples:${NC}
  # Save application images (backend + frontend) by default:
  $ $(basename "$0")

  # Save ONLY backend with simple positional argument:
  $ $(basename "$0") backend

  # Save ONLY frontend:
  $ $(basename "$0") frontend

  # Save database & cache images:
  $ $(basename "$0") postgres redis

  # Save all 5 images:
  $ $(basename "$0") all
EOF
}

parse_services() {
    local input="$1"
    IFS=',' read -ra ADDR <<< "$input"
    for svc in "${ADDR[@]}"; do
        case "$(echo "$svc" | tr '[:upper:]' '[:lower:]' | xargs)" in
            backend)  SAVE_BACKEND=true ;;
            frontend) SAVE_FRONTEND=true ;;
            postgres|db) SAVE_POSTGRES=true ;;
            redis)    SAVE_REDIS=true ;;
            kafka)    SAVE_KAFKA=true ;;
            infra)
                SAVE_POSTGRES=true
                SAVE_REDIS=true
                SAVE_KAFKA=true
                ;;
            all)
                SAVE_BACKEND=true
                SAVE_FRONTEND=true
                SAVE_POSTGRES=true
                SAVE_REDIS=true
                SAVE_KAFKA=true
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
        -s|--service|--services)
            DEFAULT_MODE=false
            parse_services "$2"
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
        -o|--output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --backend-only)
            DEFAULT_MODE=false
            SAVE_BACKEND=true
            SAVE_FRONTEND=false
            shift
            ;;
        --frontend-only)
            DEFAULT_MODE=false
            SAVE_BACKEND=false
            SAVE_FRONTEND=true
            shift
            ;;
        --infra-only)
            DEFAULT_MODE=false
            SAVE_POSTGRES=true
            SAVE_REDIS=true
            SAVE_KAFKA=true
            shift
            ;;
        --all)
            DEFAULT_MODE=false
            SAVE_BACKEND=true
            SAVE_FRONTEND=true
            SAVE_POSTGRES=true
            SAVE_REDIS=true
            SAVE_KAFKA=true
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

# Default mode exports backend and frontend
if [ "${DEFAULT_MODE}" = true ]; then
    SAVE_BACKEND=true
    SAVE_FRONTEND=true
fi

mkdir -p "${OUTPUT_DIR}"

# Select compressor (pigz for multi-core, fallback to gzip)
COMPRESSOR="gzip -c"
if command -v pigz &> /dev/null; then
    COMPRESSOR="pigz -c"
    echo -e "${CYAN}⚡ Using pigz for multi-threaded compression.${NC}"
fi

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BOLD}${CYAN}💾 Exporting Docker Images to: ${OUTPUT_DIR}${NC}"
echo -e "${BLUE}==============================================================================${NC}"

CHECKSUM_FILE="${OUTPUT_DIR}/checksums.sha256"
> "${CHECKSUM_FILE}"

save_single_image() {
    local IMAGE="$1"
    local ARCHIVE="${OUTPUT_DIR}/$2"
    echo -e "\n${YELLOW}📦 Exporting ${IMAGE} → $(basename "${ARCHIVE}")...${NC}"

    if ! docker image inspect "${IMAGE}" &> /dev/null; then
        echo -e "${YELLOW}⚠️  Image ${IMAGE} not found locally. Attempting docker pull...${NC}"
        if ! docker pull "${IMAGE}"; then
            echo -e "${RED}❌ Error: Failed to pull/find image ${IMAGE}!${NC}" >&2
            exit 1
        fi
    fi

    docker save "${IMAGE}" | ${COMPRESSOR} > "${ARCHIVE}"
    ARCHIVE_SIZE=$(ls -lh "${ARCHIVE}" | awk '{print $5}')
    echo -e "${GREEN}✅ Saved: ${ARCHIVE} (${ARCHIVE_SIZE})${NC}"

    (cd "${OUTPUT_DIR}" && sha256sum "$(basename "${ARCHIVE}")" >> checksums.sha256)
}

if [ "${SAVE_BACKEND}" = true ]; then
    save_single_image "push-backend:${BACKEND_TAG}" "push-backend-${BACKEND_TAG}.tar.gz"
fi

if [ "${SAVE_FRONTEND}" = true ]; then
    save_single_image "push-frontend:${FRONTEND_TAG}" "push-frontend-${FRONTEND_TAG}.tar.gz"
fi

if [ "${SAVE_POSTGRES}" = true ]; then
    save_single_image "${POSTGRES_IMAGE}" "postgres-17-alpine.tar.gz"
fi

if [ "${SAVE_REDIS}" = true ]; then
    save_single_image "${REDIS_IMAGE}" "redis-7-alpine.tar.gz"
fi

if [ "${SAVE_KAFKA}" = true ]; then
    save_single_image "${KAFKA_IMAGE}" "kafka-3.7.0.tar.gz"
fi

echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${GREEN}${BOLD}📋 Checksums Generated:${NC}"
cat "${CHECKSUM_FILE}"
echo -e "${BLUE}==============================================================================${NC}"
echo -e "${GREEN}✨ Image export complete. Ready to ship to AWS!${NC}"
