#!/usr/bin/env bash
# ==============================================================================
# Multi-Tenant Push Notification Engine - Docker Image Builder
# Supports custom tags, build args for backend & frontend, and optional auto-save.
# ==============================================================================
#
# SAMPLE USAGE & COMMON CASES:
# ------------------------------------------------------------------------------
# CASE 1: BUILD BOTH BACKEND AND FRONTEND (Default latest tags)
#   $ ./01_build.sh
#   Compiles Spring Boot JAR with Java 25 Corretto, builds push-backend:latest
#   and builds push-frontend:latest.
#
# CASE 2: BUILD ONLY BACKEND (Fast API hotfix)
#   $ ./01_build.sh --backend-only
#   # Or:
#   $ ./01_build.sh -s backend
#
# CASE 3: BUILD ONLY FRONTEND WITH CUSTOM API ROUTING
#   $ ./01_build.sh --frontend-only -a /api
#   # Or:
#   $ ./01_build.sh -s frontend -a https://api.push.yourdomain.com
#
# CASE 4: RELEASE BUILD WITH CUSTOM TAGS & AUTO-SAVE
#   $ ./01_build.sh -b v1.2.0 -f v1.2.0 --save
#   Builds push-backend:v1.2.0 and push-frontend:v1.2.0 and triggers 02_save-images.sh.
#
# CASE 5: FAST BUILD (SKIP MAVEN PACKAGE IF JAR ALREADY COMPILED)
#   $ ./01_build.sh --skip-maven --no-cache
# ==============================================================================
set -euo pipefail

# ANSI color codes for readable terminal feedback
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# Resolve repository paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${PROJECT_ROOT}/backend"
FRONTEND_DIR="${PROJECT_ROOT}/frontend"

# Default configurations
BACKEND_TAG="latest"
FRONTEND_TAG="latest"
API_BASE_URL="/api"
ENABLE_MOCK_DATA="false"
SKIP_TESTS=true
SKIP_MAVEN=false
NO_CACHE=false
BUILD_BACKEND=true
BUILD_FRONTEND=true
AUTO_SAVE=false
OUTPUT_DIR="${PROJECT_ROOT}/dist-images"

# Load platform from .env if present (default to linux/arm64 for AWS Graviton)
ENV_PLATFORM=""
if [ -f "${PROJECT_ROOT}/.env" ]; then
    ENV_PLATFORM=$(grep -E '^DOCKER_DEFAULT_PLATFORM=' "${PROJECT_ROOT}/.env" | cut -d '=' -f2 | tr -d '\r" ' || true)
fi
PLATFORM="${ENV_PLATFORM:-linux/arm64}"
POSITIONAL_SERVICES=()

# Help message
print_usage() {
    cat << EOF
${BOLD}Usage:${NC} $(basename "$0") [OPTIONS] [service1 service2 ...]

${BOLD}Description:${NC}
  Builds optimized, minimal Docker images for both the Push Notification Backend
  (Spring Boot / Java 25 Alpine) and Frontend (Vue 3 / Vite / Nginx Alpine).
  Supports targeted multi-arch cross-compilation (e.g. linux/arm64 for AWS Graviton).

${BOLD}Arguments:${NC}
  ${CYAN}[service1 service2 ...]${NC}    Services to build: backend, frontend, all (default: all)

${BOLD}Options:${NC}
  ${CYAN}--platform <arch>${NC}          Target architecture (default: ${PLATFORM}, e.g. 'linux/arm64', 'linux/amd64')
  ${CYAN}-b, --backend-tag <tag>${NC}   Tag for backend image (default: ${BACKEND_TAG})
  ${CYAN}-f, --frontend-tag <tag>${NC}  Tag for frontend image (default: ${FRONTEND_TAG})
  ${CYAN}-a, --api-base-url <url>${NC}  VITE_API_BASE_URL build arg for frontend (default: ${API_BASE_URL})
  ${CYAN}-m, --mock-data <bool>${NC}    VITE_ENABLE_MOCK_DATA build arg (default: ${ENABLE_MOCK_DATA})
  ${CYAN}--backend-only${NC}             Build only backend image
  ${CYAN}--frontend-only${NC}            Build only frontend image
  ${CYAN}--skip-maven${NC}               Skip 'mvn package' (assumes target/*.jar exists)
  ${CYAN}--run-tests${NC}                Run Maven tests during JAR compilation
  ${CYAN}--no-cache${NC}                 Build Docker images with --no-cache
  ${CYAN}--save${NC}                     Automatically export images to compressed tarballs after build
  ${CYAN}-o, --output-dir <path>${NC}    Directory for saved images (default: dist-images)
  ${CYAN}-h, --help${NC}                 Display this help message and exit

${BOLD}Examples:${NC}
  # Build both backend & frontend for AWS (ARM64 by default):
  $ $(basename "$0")

  # Build ONLY backend using simple positional argument:
  $ $(basename "$0") backend

  # Build ONLY frontend:
  $ $(basename "$0") frontend

  # Build both explicitly:
  $ $(basename "$0") backend frontend

  # Build for standard x86_64:
  $ $(basename "$0") --platform linux/amd64
EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --platform)
            PLATFORM="$2"
            shift 2
            ;;
        backend|frontend|all)
            POSITIONAL_SERVICES+=("$(echo "$1" | tr '[:upper:]' '[:lower:]')")
            shift
            ;;
        -s|--service|--services)
            IFS=',' read -ra ADDR <<< "$2"
            for s in "${ADDR[@]}"; do
                POSITIONAL_SERVICES+=("$(echo "$s" | tr '[:upper:]' '[:lower:]' | xargs)")
            done
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
        -a|--api-base-url)
            API_BASE_URL="$2"
            shift 2
            ;;
        -m|--mock-data)
            ENABLE_MOCK_DATA="$2"
            shift 2
            ;;
        --backend-only)
            POSITIONAL_SERVICES+=("backend")
            shift
            ;;
        --frontend-only)
            POSITIONAL_SERVICES+=("frontend")
            shift
            ;;
        --skip-maven)
            SKIP_MAVEN=true
            shift
            ;;
        --run-tests)
            SKIP_TESTS=false
            shift
            ;;
        --no-cache)
            NO_CACHE=true
            shift
            ;;
        --save)
            AUTO_SAVE=true
            shift
            ;;
        -o|--output-dir)
            OUTPUT_DIR="$2"
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

# If positional services were specified, select only those services
if [ ${#POSITIONAL_SERVICES[@]} -gt 0 ]; then
    BUILD_BACKEND=false
    BUILD_FRONTEND=false
    for s in "${POSITIONAL_SERVICES[@]}"; do
        case "$s" in
            backend)  BUILD_BACKEND=true ;;
            frontend) BUILD_FRONTEND=true ;;
            all)
                BUILD_BACKEND=true
                BUILD_FRONTEND=true
                ;;
        esac
    done
fi

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BOLD}${CYAN}🚀 Multi-Tenant Push Notification Engine - Build Pipeline${NC}"
echo -e "${BLUE}==============================================================================${NC}"
echo -e "Platform Target:  ${BOLD}${PLATFORM}${NC}"
echo -e "Build Backend:    ${BOLD}${BUILD_BACKEND}${NC} (Tag: push-backend:${BACKEND_TAG})"
echo -e "Build Frontend:   ${BOLD}${BUILD_FRONTEND}${NC} (Tag: push-frontend:${FRONTEND_TAG})"
echo -e "API Base URL:     ${BOLD}${API_BASE_URL}${NC}"
echo -e "Mock Data:        ${BOLD}${ENABLE_MOCK_DATA}${NC}"
echo -e "No Cache:         ${BOLD}${NO_CACHE}${NC}"
echo -e "Auto-Save Tar:    ${BOLD}${AUTO_SAVE}${NC}"
echo -e "${BLUE}------------------------------------------------------------------------------${NC}"

# Verification of pre-requisites
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Error: Docker is not installed or not in PATH.${NC}" >&2
    exit 1
fi

DOCKER_BUILD_FLAGS=()
if [ "${NO_CACHE}" = true ]; then
    DOCKER_BUILD_FLAGS+=("--no-cache")
fi

BUILD_CMD="docker build --platform ${PLATFORM}"
if docker buildx version &> /dev/null; then
    BUILD_CMD="docker buildx build --platform ${PLATFORM} --load"
fi

# ------------------------------------------------------------------------------
# 1. Backend Build
# ------------------------------------------------------------------------------
if [ "${BUILD_BACKEND}" = true ]; then
    echo -e "\n${CYAN}📦 [1/2] Building Backend (push-backend:${BACKEND_TAG}) for ${PLATFORM}...${NC}"

    JAR_PATH="${BACKEND_DIR}/target/push-platform-backend-1.0.0-SNAPSHOT.jar"

    if [ "${SKIP_MAVEN}" = false ]; then
        echo -e "${YELLOW}⚙️  Compiling backend JAR via Maven (Java 25)...${NC}"
        if ! command -v mvn &> /dev/null; then
            echo -e "${RED}❌ Error: Maven ('mvn') is required but not installed.${NC}" >&2
            exit 1
        fi

        MVN_ARGS=("clean" "package")
        if [ "${SKIP_TESTS}" = true ]; then
            MVN_ARGS+=("-DskipTests")
        fi

        (cd "${BACKEND_DIR}" && mvn "${MVN_ARGS[@]}")
        echo -e "${GREEN}✅ Maven packaging complete.${NC}"
    else
        if [ ! -f "${JAR_PATH}" ]; then
            echo -e "${RED}❌ Error: --skip-maven was specified but ${JAR_PATH} does not exist!${NC}" >&2
            exit 1
        fi
        echo -e "${YELLOW}ℹ️  Using existing artifact: ${JAR_PATH}${NC}"
    fi

    echo -e "${YELLOW}🐳 Building Docker image for backend (${PLATFORM})...${NC}"
    ${BUILD_CMD} "${DOCKER_BUILD_FLAGS[@]}" \
        --build-arg JAR_FILE="target/push-platform-backend-1.0.0-SNAPSHOT.jar" \
        -t "push-backend:${BACKEND_TAG}" \
        "${BACKEND_DIR}"

    echo -e "${GREEN}✅ Successfully built push-backend:${BACKEND_TAG} (${PLATFORM})${NC}"
fi

# ------------------------------------------------------------------------------
# 2. Frontend Build
# ------------------------------------------------------------------------------
if [ "${BUILD_FRONTEND}" = true ]; then
    echo -e "\n${CYAN}🎨 [2/2] Building Frontend (push-frontend:${FRONTEND_TAG}) for ${PLATFORM}...${NC}"
    echo -e "${YELLOW}🐳 Building multi-stage Vue 3 + Nginx Docker image...${NC}"
    echo -e "   → Build Arg VITE_API_BASE_URL: ${API_BASE_URL}"
    echo -e "   → Build Arg VITE_ENABLE_MOCK_DATA: ${ENABLE_MOCK_DATA}"

    ${BUILD_CMD} "${DOCKER_BUILD_FLAGS[@]}" \
        --build-arg VITE_API_BASE_URL="${API_BASE_URL}" \
        --build-arg VITE_ENABLE_MOCK_DATA="${ENABLE_MOCK_DATA}" \
        -t "push-frontend:${FRONTEND_TAG}" \
        "${FRONTEND_DIR}"

    echo -e "${GREEN}✅ Successfully built push-frontend:${FRONTEND_TAG} (${PLATFORM})${NC}"
fi

# ------------------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${GREEN}${BOLD}🎉 Build Summary${NC}"
echo -e "${BLUE}==============================================================================${NC}"
if [ "${BUILD_BACKEND}" = true ]; then
    BACKEND_SIZE=$(docker image inspect "push-backend:${BACKEND_TAG}" --format='{{.Size}}' 2>/dev/null || echo "0")
    BACKEND_SIZE_MB=$((BACKEND_SIZE / 1024 / 1024))
    echo -e "Backend Image:  ${BOLD}push-backend:${BACKEND_TAG}${NC} (~${BACKEND_SIZE_MB} MB)"
fi
if [ "${BUILD_FRONTEND}" = true ]; then
    FRONTEND_SIZE=$(docker image inspect "push-frontend:${FRONTEND_TAG}" --format='{{.Size}}' 2>/dev/null || echo "0")
    FRONTEND_SIZE_MB=$((FRONTEND_SIZE / 1024 / 1024))
    echo -e "Frontend Image: ${BOLD}push-frontend:${FRONTEND_TAG}${NC} (~${FRONTEND_SIZE_MB} MB)"
fi
echo -e "${BLUE}==============================================================================${NC}"

# Auto-save triggers 02_save-images.sh if requested
if [ "${AUTO_SAVE}" = true ]; then
    echo -e "\n${YELLOW}📦 Auto-save triggered: Calling 02_save-images.sh...${NC}"
    SAVE_ARGS=("-o" "${OUTPUT_DIR}")
    if [ "${BUILD_BACKEND}" = true ]; then
        SAVE_ARGS+=("-b" "${BACKEND_TAG}")
    else
        SAVE_ARGS+=("--frontend-only")
    fi
    if [ "${BUILD_FRONTEND}" = true ]; then
        SAVE_ARGS+=("-f" "${FRONTEND_TAG}")
    else
        SAVE_ARGS+=("--backend-only")
    fi

    "${SCRIPT_DIR}/02_save-images.sh" "${SAVE_ARGS[@]}"
fi

echo -e "\n${GREEN}✨ Done! Ready for transfer and deployment.${NC}"
