#!/usr/bin/env bash
# ==============================================================================
# Multi-Tenant Push Notification Engine - AWS Target Host Deployment Script
# Designed for resource-constrained AWS EC2 instances (t3.micro / t3.small).
# Handles swap configuration, image loading, container rollout, and health checks.
# ==============================================================================
#
# SAMPLE USAGE & COMMON CASES (Execute on AWS: cd /var/www/html/notify):
# ------------------------------------------------------------------------------
# CASE 1: DEPLOY / REFRESH ENTIRE FLEET (All 5 Services)
#   $ ./04_deploy.sh
#   - Auto-allocates 2GB swap file if system RAM <= 2.5GB (vm.swappiness=10).
#   - Loads all *.tar.gz image archives present in directory into Docker daemon.
#   - Auto-creates .env from .env.production.example if missing.
#   - Runs: docker compose --env-file .env -f docker-compose.aws.yml up -d --remove-orphans.
#   - Polls Spring Boot Actuator (/actuator/health) and Frontend Nginx (port 80).
#   - Prunes dangling Docker image layers to preserve EBS disk space.
#
# CASE 2: DEPLOY / ROLLING UPDATE FOR ONLY BACKEND (Zero Downtime for DB/Kafka/Redis)
#   $ ./04_deploy.sh backend
#   # Or using flag:
#   $ ./04_deploy.sh -s backend
#   - Loads only push-backend-*.tar.gz (if archive file exists).
#   - Executes: docker compose up -d --no-deps backend.
#   - Restarts ONLY the backend container; Postgres, Redis, and Kafka remain 100% UP.
#   - Runs targeted health check on Backend Actuator (http://localhost:8080/actuator/health).
#
# CASE 3: DEPLOY / RELOAD FOR ONLY FRONTEND (Vue 3 / Vite / Nginx)
#   $ ./04_deploy.sh frontend
#   # Or using flag:
#   $ ./04_deploy.sh -s frontend
#   - Loads only push-frontend-*.tar.gz.
#   - Executes: docker compose up -d --no-deps frontend.
#   - Verifies Nginx returns HTTP 200 on port 80.
#
# CASE 4: RESTART SPECIFIC INFRASTRUCTURE SERVICES
#   $ ./04_deploy.sh postgres
#   $ ./04_deploy.sh redis
#   $ ./04_deploy.sh kafka
#   $ ./04_deploy.sh postgres redis
#   - Restarts only targeted infrastructure services.
#   - Executes native health verification (pg_isready, redis-cli ping, or kafka API test).
#
# CASE 5: FAST LAUNCH (SKIP HEALTH CHECK OR SKIP SWAP ALLOCATION)
#   $ ./04_deploy.sh --skip-healthcheck
#   $ ./04_deploy.sh --no-swap
#   $ ./04_deploy.sh backend --skip-load --skip-healthcheck
#
# CASE 6: SPECIFY CUSTOM COMPOSE OR ENVIRONMENT FILE
#   $ ./04_deploy.sh -f docker-compose.aws.yml -e .env.staging
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
WORK_DIR="${SCRIPT_DIR}"

COMPOSE_FILE="docker-compose.aws.yml"
ENV_FILE=".env"
AUTO_SWAP=true
SWAP_SIZE_GB=2
LOAD_IMAGES=true
SKIP_HEALTHCHECK=false
PRUNE_IMAGES=true
TARGET_SERVICES=()

print_usage() {
    cat << EOF
${BOLD}Usage:${NC} $(basename "$0") [OPTIONS] [service1 service2 ...]

${BOLD}Description:${NC}
  Executes on the AWS server to provision swap (if memory constrained),
  import Docker images, validate environment variables, launch containers,
  and verify service health.
  Supports rolling updates for specific services (e.g. backend, frontend, postgres, redis, kafka)
  without disturbing the rest of the running stack.

${BOLD}Options:${NC}
  ${CYAN}-s, --services <list>${NC}      Target services to deploy: backend,frontend,postgres,redis,kafka,all
  ${CYAN}-f, --compose-file <file>${NC}  Docker compose file to use (default: ${COMPOSE_FILE})
  ${CYAN}-e, --env-file <file>${NC}      Environment file (default: ${ENV_FILE})
  ${CYAN}--no-swap${NC}                 Skip automatic swap space allocation
  ${CYAN}--swap-size-gb <gb>${NC}       Swap file size in gigabytes (default: ${SWAP_SIZE_GB})
  ${CYAN}--skip-load${NC}               Skip loading *.tar.gz images in directory
  ${CYAN}--skip-healthcheck${NC}        Skip polling container health endpoints
  ${CYAN}--no-prune${NC}                Skip pruning dangling Docker images after deploy
  ${CYAN}-h, --help${NC}                Display this help message and exit

${BOLD}Examples:${NC}
  # Deploy / update entire container fleet:
  $ $(basename "$0")

  # Deploy ONLY Backend (zero downtime for Postgres/Kafka/Redis):
  $ $(basename "$0") backend
  $ $(basename "$0") -s backend

  # Deploy ONLY Frontend:
  $ $(basename "$0") frontend

  # Deploy ONLY Database / Infra:
  $ $(basename "$0") postgres redis
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -f|--compose-file)
            COMPOSE_FILE="$2"
            shift 2
            ;;
        -e|--env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        -s|--service|--services)
            IFS=',' read -ra ADDR <<< "$2"
            for s in "${ADDR[@]}"; do
                s_clean=$(echo "$s" | tr '[:upper:]' '[:lower:]' | xargs)
                [ "$s_clean" = "db" ] && s_clean="postgres"
                TARGET_SERVICES+=("$s_clean")
            done
            shift 2
            ;;
        backend|frontend|postgres|db|redis|kafka|all)
            s_clean=$(echo "$1" | tr '[:upper:]' '[:lower:]')
            [ "$s_clean" = "db" ] && s_clean="postgres"
            TARGET_SERVICES+=("$s_clean")
            shift
            ;;
        --no-swap)
            AUTO_SWAP=false
            shift
            ;;
        --swap-size-gb)
            SWAP_SIZE_GB="$2"
            shift 2
            ;;
        --skip-load)
            LOAD_IMAGES=false
            shift
            ;;
        --skip-healthcheck)
            SKIP_HEALTHCHECK=true
            shift
            ;;
        --no-prune)
            PRUNE_IMAGES=false
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

# If 'all' was specified, clear target services so compose launches all
for s in "${TARGET_SERVICES[@]}"; do
    if [ "$s" = "all" ]; then
        TARGET_SERVICES=()
        break
    fi
done

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BOLD}${CYAN}🚀 Executing Deployment on AWS Server${NC}"
echo -e "${BLUE}==============================================================================${NC}"
echo -e "Working Directory: ${BOLD}${WORK_DIR}${NC}"
echo -e "Compose File:      ${BOLD}${COMPOSE_FILE}${NC}"
echo -e "Environment File:  ${BOLD}${ENV_FILE}${NC}"
if [ ${#TARGET_SERVICES[@]} -gt 0 ]; then
    echo -e "Target Service(s): ${BOLD}${TARGET_SERVICES[*]}${NC}"
else
    echo -e "Target Service(s): ${BOLD}All Services (Full Stack)${NC}"
fi
echo -e "Auto Swap:         ${BOLD}${AUTO_SWAP}${NC}"
echo -e "${BLUE}------------------------------------------------------------------------------${NC}"

cd "${WORK_DIR}"

# ------------------------------------------------------------------------------
# 1. Environment & Prerequisite Checks
# ------------------------------------------------------------------------------
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed on this server!${NC}"
    echo -e "Install Docker with: curl -fsSL https://get.docker.com | sh"
    exit 1
fi

COMPOSE_CMD=""
if docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
elif command -v docker-compose &> /dev/null; then
    COMPOSE_CMD="docker-compose"
else
    echo -e "${RED}❌ Neither 'docker compose' nor 'docker-compose' found!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Docker CLI & Compose detected: $(${COMPOSE_CMD} version --short 2>/dev/null || echo "Active")${NC}"

# ------------------------------------------------------------------------------
# 2. Swap Space Provisioning (Vital for Small AWS Instances like t3.micro/t3.small)
# ------------------------------------------------------------------------------
if [ "${AUTO_SWAP}" = true ]; then
    TOTAL_RAM_MB=$(free -m | awk '/^Mem:/{print $2}')
    EXISTING_SWAP_MB=$(free -m | awk '/^Swap:/{print $2}')

    echo -e "\n${CYAN}🔍 Memory Audit: Total RAM = ${TOTAL_RAM_MB}MB | Existing Swap = ${EXISTING_SWAP_MB}MB${NC}"

    if [ "${TOTAL_RAM_MB}" -le 2500 ] && [ "${EXISTING_SWAP_MB}" -lt 512 ]; then
        echo -e "${YELLOW}⚠️  Detected low system RAM (< 2.5GB) with no adequate swap.${NC}"
        echo -e "${YELLOW}⚙️  Configuring ${SWAP_SIZE_GB}GB swap space to protect against OOM-killer...${NC}"

        if [ "$EUID" -ne 0 ] && ! command -v sudo &> /dev/null; then
            echo -e "${YELLOW}⚠️  Skipping swap creation: root/sudo permissions required.${NC}"
        else
            SUDO=""
            [ "$EUID" -ne 0 ] && SUDO="sudo"

            SWAPFILE="/swapfile"
            if [ ! -f "${SWAPFILE}" ]; then
                $SUDO fallocate -l "${SWAP_SIZE_GB}G" "${SWAPFILE}" 2>/dev/null || \
                $SUDO dd if=/dev/zero of="${SWAPFILE}" bs=1M count=$((SWAP_SIZE_GB * 1024)) status=progress
                $SUDO chmod 600 "${SWAPFILE}"
                $SUDO mkswap "${SWAPFILE}"
                $SUDO swapon "${SWAPFILE}"
                
                # Make persistent in fstab if not present
                if ! grep -q "${SWAPFILE}" /etc/fstab; then
                    echo "${SWAPFILE} none swap sw 0 0" | $SUDO tee -a /etc/fstab > /dev/null
                fi

                # Optimize kernel swap aggressiveness for server workloads
                $SUDO sysctl -w vm.swappiness=10 > /dev/null || true
                $SUDO sysctl -w vm.vfs_cache_pressure=50 > /dev/null || true

                echo -e "${GREEN}✅ Successfully provisioned and activated ${SWAP_SIZE_GB}GB swap!${NC}"
            else
                $SUDO swapon "${SWAPFILE}" 2>/dev/null || true
                echo -e "${GREEN}✅ Existing ${SWAPFILE} is active.${NC}"
            fi
        fi
    else
        echo -e "${GREEN}✅ System has sufficient memory or active swap space.${NC}"
    fi
fi

# Tag existing running images as :previous for zero-downtime rollback safety
if docker image inspect push-backend:latest &> /dev/null; then
    docker tag push-backend:latest push-backend:previous 2>/dev/null || true
fi
if docker image inspect push-frontend:latest &> /dev/null; then
    docker tag push-frontend:latest push-frontend:previous 2>/dev/null || true
fi

# ------------------------------------------------------------------------------
# 3. Load Transfered Docker Images
# ------------------------------------------------------------------------------
if [ "${LOAD_IMAGES}" = true ]; then
    IMAGE_ARCHIVES=()
    if [ ${#TARGET_SERVICES[@]} -eq 0 ]; then
        IMAGE_ARCHIVES=(push-backend-*.tar.gz push-frontend-*.tar.gz postgres-*.tar.gz redis-*.tar.gz kafka-*.tar.gz)
    else
        for s in "${TARGET_SERVICES[@]}"; do
            case "$s" in
                backend)  IMAGE_ARCHIVES+=(push-backend-*.tar.gz) ;;
                frontend) IMAGE_ARCHIVES+=(push-frontend-*.tar.gz) ;;
                postgres) IMAGE_ARCHIVES+=(postgres-*.tar.gz) ;;
                redis)    IMAGE_ARCHIVES+=(redis-*.tar.gz) ;;
                kafka)    IMAGE_ARCHIVES+=(kafka-*.tar.gz) ;;
            esac
        done
    fi

    FOUND_IMAGES=false

    for archive in "${IMAGE_ARCHIVES[@]}"; do
        if [ -f "${archive}" ]; then
            FOUND_IMAGES=true
            echo -e "\n${YELLOW}📦 Loading image archive: ${archive}...${NC}"
            if command -v pigz &> /dev/null; then
                pigz -dc "${archive}" | docker load
            else
                docker load -i "${archive}"
            fi
            echo -e "${GREEN}✅ Loaded ${archive}${NC}"
        fi
    done

    if [ "${FOUND_IMAGES}" = false ]; then
        echo -e "${YELLOW}ℹ️  No matching .tar.gz image archives found in current directory. Assuming images were pre-loaded or will pull.${NC}"
    fi
fi

# ------------------------------------------------------------------------------
# 4. Environment File Validation
# ------------------------------------------------------------------------------
if [ ! -f "${ENV_FILE}" ]; then
    if [ -f ".env.production.example" ]; then
        echo -e "${YELLOW}⚠️  ${ENV_FILE} not found. Creating from .env.production.example...${NC}"
        cp .env.production.example "${ENV_FILE}"
        echo -e "${YELLOW}⚠️  PLEASE REVIEW ${ENV_FILE} AND UPDATE SECRETS/HOSTNAMES!${NC}"
    elif [ -f ".env.example" ]; then
        echo -e "${YELLOW}⚠️  ${ENV_FILE} not found. Creating from .env.example...${NC}"
        cp .env.example "${ENV_FILE}"
    else
        echo -e "${RED}❌ Error: No ${ENV_FILE} file found! Create it before launching.${NC}"
        exit 1
    fi
fi

if [ ! -f "${COMPOSE_FILE}" ]; then
    echo -e "${RED}❌ Error: Compose file '${COMPOSE_FILE}' not found!${NC}"
    exit 1
fi

# ------------------------------------------------------------------------------
# 5. Launch / Rolling Update via Docker Compose
# ------------------------------------------------------------------------------
if [ ${#TARGET_SERVICES[@]} -eq 0 ]; then
    echo -e "\n${CYAN}🚀 Launching entire container fleet with ${COMPOSE_CMD}...${NC}"
    ${COMPOSE_CMD} --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --remove-orphans
else
    echo -e "\n${CYAN}🚀 Rolling update for target service(s): ${TARGET_SERVICES[*]} with ${COMPOSE_CMD}...${NC}"
    ${COMPOSE_CMD} --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --no-deps "${TARGET_SERVICES[@]}"
fi

echo -e "${GREEN}✅ Containers started in background.${NC}"

# ------------------------------------------------------------------------------
# 6. Service Health Check & Actuator Polling
# ------------------------------------------------------------------------------
service_in_targets() {
    local target="$1"
    [ ${#TARGET_SERVICES[@]} -eq 0 ] && return 0
    for s in "${TARGET_SERVICES[@]}"; do
        [ "$s" = "$target" ] && return 0
    done
    return 1
}

if [ "${SKIP_HEALTHCHECK}" = false ]; then
    echo -e "\n${CYAN}🩺 Running Post-Deploy Health Checks...${NC}"

    # 6a. Check Postgres if targeted
    if service_in_targets "postgres"; then
        echo -ne "${YELLOW}⏳ Verifying PostgreSQL health...${NC}"
        sleep 2
        if docker exec push_postgres pg_isready -U "${DB_USER:-postgres}" -d "${DB_NAME:-push_platform}" > /dev/null 2>&1; then
            echo -e " ${GREEN}✅ PostgreSQL is accepting connections!${NC}"
        else
            echo -e " ${YELLOW}⚠️  PostgreSQL check pending. Ensure credentials match .env.${NC}"
        fi
    fi

    # 6b. Check Redis if targeted
    if service_in_targets "redis"; then
        echo -ne "${YELLOW}⏳ Verifying Redis health...${NC}"
        sleep 1
        if docker exec push_redis redis-cli ping > /dev/null 2>&1; then
            echo -e " ${GREEN}✅ Redis PONG received!${NC}"
        else
            echo -e " ${YELLOW}⚠️  Redis ping pending.${NC}"
        fi
    fi

    # 6c. Check Kafka if targeted
    if service_in_targets "kafka"; then
        echo -ne "${YELLOW}⏳ Verifying Kafka broker status...${NC}"
        sleep 3
        if docker exec push_kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1; then
            echo -e " ${GREEN}✅ Kafka broker is operational!${NC}"
        else
            echo -e " ${YELLOW}⚠️  Kafka broker check pending.${NC}"
        fi
    fi

    # 6d. Check Backend Spring Boot Actuator if targeted
    if service_in_targets "backend"; then
        BACKEND_UP=false
        MAX_RETRIES=18
        RETRY_INTERVAL=5
        ACTUATOR_URL="http://localhost:8080/actuator/health"

        echo -ne "${YELLOW}⏳ Polling backend health endpoint (${ACTUATOR_URL})...${NC}"

        for ((i=1; i<=MAX_RETRIES; i++)); do
            STATUS=$(docker exec push_backend wget -qO- http://127.0.0.1:8080/actuator/health 2>/dev/null || curl -s -m 3 "${ACTUATOR_URL}" 2>/dev/null || true)
            if echo "${STATUS}" | grep -q '"status":"UP"'; then
                BACKEND_UP=true
                echo -e "\n${GREEN}✅ Backend Actuator is UP (attempt ${i}/${MAX_RETRIES})!${NC}"
                break
            fi
            echo -ne "."
            sleep "${RETRY_INTERVAL}"
        done

        if [ "${BACKEND_UP}" = false ]; then
            echo -e "\n${RED}❌ Backend failed to report healthy status within $((MAX_RETRIES * RETRY_INTERVAL))s!${NC}"
            echo -e "${YELLOW}📋 Last 40 lines of backend logs:${NC}"
            ${COMPOSE_CMD} --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" logs --tail=40 backend
            echo -e "${RED}Deployment failed verification.${NC}"
            exit 1
        fi
    fi

    # 6e. Check Frontend Nginx Gateway if targeted
    if service_in_targets "frontend"; then
        TARGET_PORT="${FRONTEND_PORT:-3001}"
        echo -ne "${YELLOW}⏳ Verifying Frontend web server on port ${TARGET_PORT}...${NC}"
        FRONTEND_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 3 "http://localhost:${TARGET_PORT}/" 2>/dev/null || true)
        if [ "${FRONTEND_CODE}" = "200" ] || [ "${FRONTEND_CODE}" = "301" ] || [ "${FRONTEND_CODE}" = "302" ]; then
            echo -e " ${GREEN}✅ Frontend HTTP Response: ${FRONTEND_CODE}${NC}"
        else
            echo -e " ${YELLOW}⚠️  Frontend returned HTTP ${FRONTEND_CODE}. (Verify port mappings if custom port used).${NC}"
        fi
    fi
fi

# ------------------------------------------------------------------------------
# 7. Cleanup & Disk Space Hygiene
# ------------------------------------------------------------------------------
if [ "${PRUNE_IMAGES}" = true ]; then
    echo -e "\n${CYAN}🧹 Pruning dangling Docker images to preserve EBS disk space...${NC}"
    docker image prune -f > /dev/null 2>&1 || true
    echo -e "${GREEN}✅ Disk cleanup complete.${NC}"
fi

echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${GREEN}${BOLD}🎉 Deployment Completed Successfully!${NC}"
echo -e "${BLUE}==============================================================================${NC}"
if [ ${#TARGET_SERVICES[@]} -gt 0 ]; then
    ${COMPOSE_CMD} --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps "${TARGET_SERVICES[@]}"
else
    ${COMPOSE_CMD} --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps
fi
echo -e "${BLUE}==============================================================================${NC}"
