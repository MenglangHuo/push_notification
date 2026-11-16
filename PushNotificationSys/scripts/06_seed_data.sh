#!/usr/bin/env bash
# ==============================================================================
# Multi-Tenant Push Notification Engine - Zero-JVM Database Seeder
# ==============================================================================
# Standardized, secure, idempotent database bootstrap script for initial
# platform provisioning and disaster recovery.
#
# ARCHITECTURAL RATIONALE:
# - Production best practice: Java application startup (JVM) must NEVER execute
#   destructive, slow, or redundant database seeding (DatabaseSeeder.java removed).
# - Seeding is strictly an external, audited, one-time infrastructure operation.
# - Multi-environment capable: Runs seamlessly directly on the target AWS server,
#   in local Docker environments, or remotely from your workstation via SSH.
#
# SAMPLE USAGE:
# ------------------------------------------------------------------------------
# CASE 1: EXECUTE DIRECTLY ON SERVER (Default / Automatic Container Detection)
#   $ cd /var/www/html/notify
#   $ ./06_seed_data.sh
#   - Auto-detects push_postgres container and applies idempotent seed SQL.
#
# CASE 2: EXECUTE REMOTELY FROM LOCAL WORKSTATION (Zero-SSH Manual Login)
#   $ ./PushNotificationSys/scripts/06_seed_data.sh --remote
#   - Uses SSH connection details from .env (ec2-user@44.236.62.255)
#   - Streams seed SQL directly into remote PostgreSQL without requiring manual login.
#
# CASE 3: LOCAL DEVELOPMENT / LOCAL DOCKER
#   $ ./PushNotificationSys/scripts/06_seed_data.sh --local
#   - Targets local Docker container push_postgres or local port 54327.
#
# CASE 4: FORCE RE-SEED / OVERWRITE EXISTING DEMO ENTITIES
#   $ ./06_seed_data.sh --force
#   $ ./06_seed_data.sh --remote --force
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

# Search for .env
ENV_FILE=""
if [ -f "${SCRIPT_DIR}/.env" ]; then
    ENV_FILE="${SCRIPT_DIR}/.env"
elif [ -f "${PROJECT_ROOT}/.env" ]; then
    ENV_FILE="${PROJECT_ROOT}/.env"
elif [ -f "./.env" ]; then
    ENV_FILE="./.env"
fi

if [ -n "${ENV_FILE}" ]; then
    while IFS='=' read -r key val || [ -n "$key" ]; do
        case "$key" in
            DB_USER)       ENV_DB_USER="$val" ;;
            DB_NAME)       ENV_DB_NAME="$val" ;;
            DB_PASSWORD)   ENV_DB_PASSWORD="$val" ;;
            POSTGRES_PORT) ENV_POSTGRES_PORT="$val" ;;
            REMOTE_USER)   ENV_REMOTE_USER="$val" ;;
            REMOTE_HOST)   ENV_REMOTE_HOST="$val" ;;
            REMOTE_DIR)    ENV_REMOTE_DIR="$val" ;;
            SSH_KEY)       ENV_SSH_KEY="$val" ;;
            SSH_PORT)      ENV_SSH_PORT="$val" ;;
        esac
    done < <(grep -E '^(DB_|POSTGRES_|REMOTE_|SSH_)' "${ENV_FILE}" | sed 's/\r$//')
fi

DB_USER="${ENV_DB_USER:-postgres}"
DB_NAME="${ENV_DB_NAME:-push_platform}"
DB_PASSWORD="${ENV_DB_PASSWORD:-postgres_password}"
POSTGRES_PORT="${ENV_POSTGRES_PORT:-54327}"

REMOTE_USER="${ENV_REMOTE_USER:-ec2-user}"
REMOTE_HOST="${ENV_REMOTE_HOST:-}"
REMOTE_DIR="${ENV_REMOTE_DIR:-/var/www/html/notify}"
SSH_KEY="${ENV_SSH_KEY:-~/.ssh/id_rsa}"
SSH_PORT="${ENV_SSH_PORT:-22}"

MODE="auto"
FORCE_SEED=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --remote|-r)
            MODE="remote"
            shift
            ;;
        --local|-l)
            MODE="local"
            shift
            ;;
        --force|-f)
            FORCE_SEED=true
            shift
            ;;
        -h|--help)
            echo -e "${BOLD}Usage:${NC} $(basename "$0") [options]"
            echo ""
            echo "Options:"
            echo "  --remote, -r     Seed remote database via SSH from local machine"
            echo "  --local, -l      Seed local docker container (push_postgres)"
            echo "  --force, -f      Force update seed records even if already present"
            echo "  -h, --help       Show this help message"
            exit 0
            ;;
        *)
            shift
            ;;
    esac
done

# If mode is auto, decide based on environment
if [ "${MODE}" = "auto" ]; then
    if docker ps --format '{{.Names}}' 2>/dev/null | grep -qE '^push_postgres$|^push-platform-postgres'; then
        MODE="local"
    elif [ -n "${REMOTE_HOST}" ]; then
        MODE="remote"
    else
        MODE="local"
    fi
fi

echo -e "${BLUE}======================================================================${NC}"
echo -e "${BOLD}🌱 Multi-Tenant Push Notification Engine - Database Seeder${NC}"
echo -e "${BLUE}======================================================================${NC}"
echo -e "Target Mode : ${CYAN}${MODE}${NC}"
echo -e "Database    : ${CYAN}${DB_NAME}${NC} (User: ${DB_USER})"
echo -e "Force Mode  : ${CYAN}${FORCE_SEED}${NC}"

if [ "${MODE}" = "remote" ]; then
    if [ -z "${REMOTE_HOST}" ]; then
        echo -e "${RED}Error: REMOTE_HOST not configured in .env or environment!${NC}" >&2
        exit 1
    fi
    echo -e "\n${CYAN}📡 Delegating execution to remote AWS server via SSH...${NC}"
    SSH_OPTS=(-p "${SSH_PORT}" -o StrictHostKeyChecking=no -o ConnectTimeout=15)
    [ -n "${SSH_KEY}" ] && SSH_OPTS+=(-i "${SSH_KEY/#\~/$HOME}")
    SSH_TARGET="${REMOTE_USER}@${REMOTE_HOST}"

    FORCE_FLAG=""
    [ "${FORCE_SEED}" = true ] && FORCE_FLAG="--force"

    # Ensure remote script exists and is executable
    ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "if [ ! -f ${REMOTE_DIR}/06_seed_data.sh ]; then exit 12; fi" 2>/dev/null || {
        echo -e "${YELLOW}⚠️  06_seed_data.sh not found on remote. Uploading...${NC}"
        scp "${SSH_OPTS[@]}" "${BASH_SOURCE[0]}" "${SSH_TARGET}:${REMOTE_DIR}/06_seed_data.sh"
        ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "chmod +x ${REMOTE_DIR}/06_seed_data.sh"
    }

    ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "cd ${REMOTE_DIR} && ./06_seed_data.sh --local ${FORCE_FLAG}"
    exit $?
fi

# Define the Master Idempotent Seed SQL
SEED_SQL=$(cat <<'EOF'
-- ============================================================================
-- Push Notification Platform - Master Idempotent Bootstrap Seed
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ----------------------------------------------------------------------------
-- 1. IAM Permissions Catalog (38 Granular System Permissions)
-- ----------------------------------------------------------------------------
INSERT INTO permissions (id, code, resource, action, description, created_at) VALUES
  (uuid_generate_v4(), 'org:read', 'org', 'read', 'View organization profile and settings', NOW()),
  (uuid_generate_v4(), 'org:update', 'org', 'update', 'Modify organization details and quotas', NOW()),
  (uuid_generate_v4(), 'org:delete', 'org', 'delete', 'Delete organization and all associated resources', NOW()),
  (uuid_generate_v4(), 'member:read', 'member', 'read', 'View organization members and roles', NOW()),
  (uuid_generate_v4(), 'member:invite', 'member', 'invite', 'Invite new members to the organization', NOW()),
  (uuid_generate_v4(), 'member:update', 'member', 'update', 'Update member roles and scopes', NOW()),
  (uuid_generate_v4(), 'member:remove', 'member', 'remove', 'Remove members from the organization', NOW()),
  (uuid_generate_v4(), 'role:read', 'role', 'read', 'View system and custom roles', NOW()),
  (uuid_generate_v4(), 'role:create', 'role', 'create', 'Create custom roles', NOW()),
  (uuid_generate_v4(), 'role:update', 'role', 'update', 'Edit custom roles', NOW()),
  (uuid_generate_v4(), 'role:delete', 'role', 'delete', 'Delete custom roles', NOW()),
  (uuid_generate_v4(), 'app:read', 'app', 'read', 'View apps in the organization', NOW()),
  (uuid_generate_v4(), 'app:create', 'app', 'create', 'Create new apps', NOW()),
  (uuid_generate_v4(), 'app:update', 'app', 'update', 'Edit app settings', NOW()),
  (uuid_generate_v4(), 'app:delete', 'app', 'delete', 'Delete apps', NOW()),
  (uuid_generate_v4(), 'apikey:read', 'apikey', 'read', 'View API key metadata', NOW()),
  (uuid_generate_v4(), 'apikey:create', 'apikey', 'create', 'Create new server API keys and SDK keys', NOW()),
  (uuid_generate_v4(), 'apikey:rotate', 'apikey', 'rotate', 'Rotate API keys', NOW()),
  (uuid_generate_v4(), 'apikey:revoke', 'apikey', 'revoke', 'Revoke API keys', NOW()),
  (uuid_generate_v4(), 'credential:read', 'credential', 'read', 'View provider credential metadata', NOW()),
  (uuid_generate_v4(), 'credential:update', 'credential', 'update', 'Configure provider credentials (FCM/APNs/VAPID)', NOW()),
  (uuid_generate_v4(), 'credential:delete', 'credential', 'delete', 'Remove provider credentials', NOW()),
  (uuid_generate_v4(), 'notification:read', 'notification', 'read', 'View notification history and details', NOW()),
  (uuid_generate_v4(), 'notification:create', 'notification', 'create', 'Compose and schedule push notifications', NOW()),
  (uuid_generate_v4(), 'notification:cancel', 'notification', 'cancel', 'Cancel scheduled push notifications', NOW()),
  (uuid_generate_v4(), 'notification:delete', 'notification', 'delete', 'Delete notification drafts and logs', NOW()),
  (uuid_generate_v4(), 'segment:read', 'segment', 'read', 'View segments and filters', NOW()),
  (uuid_generate_v4(), 'segment:create', 'segment', 'create', 'Create custom audience segments', NOW()),
  (uuid_generate_v4(), 'segment:update', 'segment', 'update', 'Update audience segments', NOW()),
  (uuid_generate_v4(), 'segment:delete', 'segment', 'delete', 'Delete audience segments', NOW()),
  (uuid_generate_v4(), 'device:read', 'device', 'read', 'View registered devices', NOW()),
  (uuid_generate_v4(), 'device:export', 'device', 'export', 'Export device lists', NOW()),
  (uuid_generate_v4(), 'device:delete', 'device', 'delete', 'Deactivate or delete device tokens', NOW()),
  (uuid_generate_v4(), 'log:read', 'log', 'read', 'View delivery and API request logs', NOW()),
  (uuid_generate_v4(), 'log:export', 'log', 'export', 'Export delivery logs', NOW()),
  (uuid_generate_v4(), 'audit:read', 'audit', 'read', 'View audit logs', NOW()),
  (uuid_generate_v4(), 'audit:export', 'audit', 'export', 'Export audit logs to CSV', NOW()),
  (uuid_generate_v4(), 'analytics:read', 'analytics', 'read', 'View analytics and performance charts', NOW())
ON CONFLICT (code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. System Preset Roles
-- ----------------------------------------------------------------------------
INSERT INTO roles (id, organization_id, name, description, is_system, created_at, updated_at) VALUES
  ('11111111-1111-1111-1111-111111111111', NULL, 'Owner', 'Complete control over organization, apps, members and billing', TRUE, NOW(), NOW()),
  ('22222222-2222-2222-2222-222222222222', NULL, 'Admin', 'Administrative access to manage apps, members and campaigns', TRUE, NOW(), NOW()),
  ('33333333-3333-3333-3333-333333333333', NULL, 'Developer', 'Technical access to apps, API keys, credentials, and test tools', TRUE, NOW(), NOW()),
  ('44444444-4444-4444-4444-444444444444', NULL, 'Marketer', 'Campaign creator access for notifications, segments, and analytics', TRUE, NOW(), NOW()),
  ('55555555-5555-5555-5555-555555555555', NULL, 'Viewer', 'Read-only access across apps, notifications, logs, and analytics', TRUE, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  is_system = EXCLUDED.is_system;

-- Map Role Permissions (Owner: All)
INSERT INTO role_permissions (role_id, permission_id)
SELECT '11111111-1111-1111-1111-111111111111', id FROM permissions
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Admin: All except org:delete
INSERT INTO role_permissions (role_id, permission_id)
SELECT '22222222-2222-2222-2222-222222222222', id FROM permissions
WHERE code NOT IN ('org:delete')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Developer
INSERT INTO role_permissions (role_id, permission_id)
SELECT '33333333-3333-3333-3333-333333333333', id FROM permissions
WHERE code IN (
  'org:read', 'member:read', 'role:read',
  'app:read', 'app:create', 'app:update',
  'apikey:read', 'apikey:create', 'apikey:rotate', 'apikey:revoke',
  'credential:read', 'credential:update', 'credential:delete',
  'notification:read', 'notification:create', 'notification:cancel',
  'segment:read', 'segment:create', 'segment:update',
  'device:read', 'device:export', 'device:delete',
  'log:read', 'log:export', 'analytics:read'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Marketer
INSERT INTO role_permissions (role_id, permission_id)
SELECT '44444444-4444-4444-4444-444444444444', id FROM permissions
WHERE code IN (
  'org:read', 'member:read', 'app:read',
  'notification:read', 'notification:create', 'notification:cancel',
  'segment:read', 'segment:create', 'segment:update', 'segment:delete',
  'device:read', 'log:read', 'analytics:read'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Viewer
INSERT INTO role_permissions (role_id, permission_id)
SELECT '55555555-5555-5555-5555-555555555555', id FROM permissions
WHERE code IN (
  'org:read', 'member:read', 'role:read', 'app:read', 'apikey:read',
  'credential:read', 'notification:read', 'segment:read', 'device:read',
  'log:read', 'analytics:read'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3. Users (Super Admin & Demo Owner)
-- Password for both: Password123!
-- ----------------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, full_name, is_super_admin, status, created_at, updated_at) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'admin@pushnotify.local', '$argon2id$v=19$m=65536,t=3,p=1$xWBnbcjfCRCx5nUMEZMdIA$JO1EhD1j9kNoqCXeMiebhSx0lD1svBzGOfsjVg8cIJk', 'Platform Super Admin', TRUE, 'ACTIVE', NOW(), NOW()),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'demo@pushnotify.local', '$argon2id$v=19$m=65536,t=3,p=1$xWBnbcjfCRCx5nUMEZMdIA$JO1EhD1j9kNoqCXeMiebhSx0lD1svBzGOfsjVg8cIJk', 'Demo Owner', FALSE, 'ACTIVE', NOW(), NOW())
ON CONFLICT (email) DO UPDATE SET
  password_hash = EXCLUDED.password_hash,
  full_name = EXCLUDED.full_name,
  is_super_admin = EXCLUDED.is_super_admin,
  status = EXCLUDED.status,
  updated_at = NOW();

-- ----------------------------------------------------------------------------
-- 4. Demo Organization (Acme Corporation)
-- ----------------------------------------------------------------------------
INSERT INTO organizations (id, name, slug, status, tier, quotas, created_at, updated_at) VALUES
  ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Acme Corporation', 'acme', 'ACTIVE', 'ENTERPRISE', '{"max_apps": 5, "max_devices": 100000, "rate_limit_per_min": 1000}'::jsonb, NOW(), NOW())
ON CONFLICT (slug) DO UPDATE SET
  name = EXCLUDED.name,
  status = EXCLUDED.status,
  tier = EXCLUDED.tier,
  quotas = EXCLUDED.quotas,
  updated_at = NOW();

-- ----------------------------------------------------------------------------
-- 5. Memberships & Owner Role Assignments
-- ----------------------------------------------------------------------------
INSERT INTO memberships (id, user_id, organization_id, status, created_at, updated_at) VALUES
  ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'ACTIVE', NOW(), NOW()),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'ACTIVE', NOW(), NOW())
ON CONFLICT (user_id, organization_id) DO UPDATE SET
  status = EXCLUDED.status,
  updated_at = NOW();

INSERT INTO member_role_assignments (id, membership_id, role_id, app_id, created_at)
SELECT uuid_generate_v4(), 'dddddddd-dddd-dddd-dddd-dddddddddddd', '11111111-1111-1111-1111-111111111111', NULL, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM member_role_assignments
  WHERE membership_id = 'dddddddd-dddd-dddd-dddd-dddddddddddd'
    AND role_id = '11111111-1111-1111-1111-111111111111'
    AND app_id IS NULL
);

INSERT INTO member_role_assignments (id, membership_id, role_id, app_id, created_at)
SELECT uuid_generate_v4(), 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '11111111-1111-1111-1111-111111111111', NULL, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM member_role_assignments
  WHERE membership_id = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'
    AND role_id = '11111111-1111-1111-1111-111111111111'
    AND app_id IS NULL
);

-- ----------------------------------------------------------------------------
-- 6. Applications (Acme Retail Store & Driver Delivery)
-- ----------------------------------------------------------------------------
INSERT INTO apps (id, organization_id, name, description, created_at, updated_at) VALUES
  ('11111111-2222-3333-4444-555555555551', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'Acme Retail Store', 'Primary customer-facing e-commerce shopping app (iOS, Android, Web)', NOW(), NOW()),
  ('11111111-2222-3333-4444-555555555552', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'Acme Driver Delivery', 'Logistics delivery partner application', NOW(), NOW())
ON CONFLICT (organization_id, name) DO UPDATE SET
  description = EXCLUDED.description,
  updated_at = NOW();

-- ----------------------------------------------------------------------------
-- 7. API Keys (Server REST Key & Public SDK Key for Acme Retail Store)
-- ----------------------------------------------------------------------------
INSERT INTO api_keys (id, app_id, key_type, name, key_prefix, key_hash, scopes, allowed_origins, is_revoked, created_at) VALUES
  ('aaaaaaaa-1111-2222-3333-444444444441', '11111111-2222-3333-4444-555555555551', 'SERVER_REST', 'Server Production Key', 'os_live_demo', 'c3b477b21f2401a9d5984a85a08d93f0a4b9828fddb617626d3dc078b55ca0ec', ARRAY['notification:create', 'notification:read', 'device:read', 'segment:read'], '{}', FALSE, NOW()),
  ('aaaaaaaa-1111-2222-3333-444444444442', '11111111-2222-3333-4444-555555555551', 'SDK_PUBLIC', 'Web Client SDK Key', 'os_pub_demo', '8690233766a189019b80bc3a4f3adb0c9ce44a6da3c689328b23168102664513', ARRAY['device:create', 'device:update', 'event:track'], ARRAY['http://localhost:5173', 'https://retail.acme.com', 'https://notify.bronxtechnology.site'], FALSE, NOW())
ON CONFLICT (key_hash) DO UPDATE SET
  name = EXCLUDED.name,
  scopes = EXCLUDED.scopes,
  allowed_origins = EXCLUDED.allowed_origins,
  is_revoked = EXCLUDED.is_revoked;

-- ----------------------------------------------------------------------------
-- 8. Target Audience Segments (Acme Retail Store)
-- ----------------------------------------------------------------------------
INSERT INTO segments (id, app_id, name, description, filter_ast, is_active, created_at, updated_at) VALUES
  ('bbbbbbbb-1111-2222-3333-444444444441', '11111111-2222-3333-4444-555555555551', 'VIP Customers', 'Users flagged with tier VIP tag', '{"conjunction":"AND","rules":[{"field":"tag","tagKey":"tier","op":"EQ","value":"VIP"}]}'::jsonb, TRUE, NOW(), NOW()),
  ('bbbbbbbb-1111-2222-3333-444444444442', '11111111-2222-3333-4444-555555555551', 'Active iOS Users', 'Active Apple iPhone and iPad users', '{"conjunction":"AND","rules":[{"field":"platform","op":"EQ","value":"IOS"},{"field":"is_active","op":"EQ","value":"true"}]}'::jsonb, TRUE, NOW(), NOW()),
  ('bbbbbbbb-1111-2222-3333-444444444443', '11111111-2222-3333-4444-555555555551', 'Android Electronics Shoppers', 'Android users interested in consumer electronics', '{"conjunction":"AND","rules":[{"field":"platform","op":"EQ","value":"ANDROID"},{"field":"tag","tagKey":"category","op":"EQ","value":"electronics"}]}'::jsonb, TRUE, NOW(), NOW())
ON CONFLICT (app_id, name) DO UPDATE SET
  description = EXCLUDED.description,
  filter_ast = EXCLUDED.filter_ast,
  is_active = EXCLUDED.is_active,
  updated_at = NOW();
EOF
)

execute_sql() {
    local sql_content="$1"
    if [ "${MODE}" = "remote" ]; then
        if [ -z "${REMOTE_HOST}" ]; then
            echo -e "${RED}Error: REMOTE_HOST not configured in .env or environment!${NC}" >&2
            exit 1
        fi
        local SSH_OPTS=(-p "${SSH_PORT}" -o StrictHostKeyChecking=no -o ConnectTimeout=10)
        [ -n "${SSH_KEY}" ] && SSH_OPTS+=(-i "${SSH_KEY/#\~/$HOME}")
        local SSH_TARGET="${REMOTE_USER}@${REMOTE_HOST}"

        ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "docker exec -i push_postgres psql -U ${DB_USER} -d ${DB_NAME}" <<< "${sql_content}"
    else
        # Local execution: prefer docker exec push_postgres
        if docker ps --format '{{.Names}}' 2>/dev/null | grep -qE '^push_postgres$|^push-platform-postgres'; then
            local CONTAINER_NAME
            CONTAINER_NAME=$(docker ps --format '{{.Names}}' | grep -E '^push_postgres$|^push-platform-postgres' | head -n 1)
            docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" <<< "${sql_content}"
        elif command -v psql &>/dev/null; then
            PGPASSWORD="${DB_PASSWORD}" psql -h localhost -p "${POSTGRES_PORT}" -U "${DB_USER}" -d "${DB_NAME}" <<< "${sql_content}"
        else
            echo -e "${RED}Error: Neither push_postgres container nor psql client found locally.${NC}" >&2
            exit 1
        fi
    fi
}

query_sql() {
    local query="$1"
    if [ "${MODE}" = "remote" ]; then
        local SSH_OPTS=(-p "${SSH_PORT}" -o StrictHostKeyChecking=no -o ConnectTimeout=10)
        [ -n "${SSH_KEY}" ] && SSH_OPTS+=(-i "${SSH_KEY/#\~/$HOME}")
        local SSH_TARGET="${REMOTE_USER}@${REMOTE_HOST}"
        ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "docker exec -i push_postgres psql -U ${DB_USER} -d ${DB_NAME} -t -c \"${query}\"" 2>/dev/null | tr -d ' \r\n'
    else
        if docker ps --format '{{.Names}}' 2>/dev/null | grep -qE '^push_postgres$|^push-platform-postgres'; then
            local CONTAINER_NAME
            CONTAINER_NAME=$(docker ps --format '{{.Names}}' | grep -E '^push_postgres$|^push-platform-postgres' | head -n 1)
            docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" -t -c "${query}" 2>/dev/null | tr -d ' \r\n'
        elif command -v psql &>/dev/null; then
            PGPASSWORD="${DB_PASSWORD}" psql -h localhost -p "${POSTGRES_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t -c "${query}" 2>/dev/null | tr -d ' \r\n'
        else
            echo "0"
        fi
    fi
}

echo -e "\n${CYAN}🔍 Inspecting database state...${NC}"
EXISTING_ADMINS=$(query_sql "SELECT COUNT(*) FROM users WHERE email='admin@pushnotify.local';" || echo "0")
EXISTING_PERMS=$(query_sql "SELECT COUNT(*) FROM permissions;" || echo "0")

if [ "${EXISTING_ADMINS}" != "0" ] && [ "${EXISTING_PERMS}" != "0" ] && [ "${FORCE_SEED}" != true ]; then
    echo -e "${YELLOW}⚠️  Database already contains seeded platform admin (${EXISTING_ADMINS} admin user, ${EXISTING_PERMS} permissions).${NC}"
    echo -e "${GREEN}✨ Database is in a valid seeded state. Skipping execution.${NC}"
    echo -e "Tip: Pass ${BOLD}--force${NC} to update/re-apply seed data."
else
    echo -e "${CYAN}🚀 Applying master seed SQL...${NC}"
    execute_sql "${SEED_SQL}"
    echo -e "${GREEN}✅ Master seed SQL applied successfully.${NC}"
fi

echo -e "\n${CYAN}📊 Verifying seeded records in database:${NC}"
PERM_CNT=$(query_sql "SELECT COUNT(*) FROM permissions;")
ROLE_CNT=$(query_sql "SELECT COUNT(*) FROM roles;")
USER_CNT=$(query_sql "SELECT COUNT(*) FROM users;")
ORG_CNT=$(query_sql "SELECT COUNT(*) FROM organizations;")
APP_CNT=$(query_sql "SELECT COUNT(*) FROM apps;")
KEY_CNT=$(query_sql "SELECT COUNT(*) FROM api_keys;")
SEG_CNT=$(query_sql "SELECT COUNT(*) FROM segments;")

echo -e "  - Permissions : ${GREEN}${PERM_CNT}${NC} (Expected: 38)"
echo -e "  - System Roles: ${GREEN}${ROLE_CNT}${NC} (Expected: 5)"
echo -e "  - Users       : ${GREEN}${USER_CNT}${NC} (admin@pushnotify.local, demo@pushnotify.local)"
echo -e "  - Orgs        : ${GREEN}${ORG_CNT}${NC} (Acme Corporation)"
echo -e "  - Apps        : ${GREEN}${APP_CNT}${NC} (Acme Retail Store, Acme Driver Delivery)"
echo -e "  - API Keys    : ${GREEN}${KEY_CNT}${NC} (Server REST & SDK Public keys)"
echo -e "  - Segments    : ${GREEN}${SEG_CNT}${NC} (VIP, iOS, Android electronics)"

echo -e "\n${GREEN}🎉 Database bootstrap seeding complete!${NC}"
echo -e "Super Admin credentials: ${BOLD}admin@pushnotify.local${NC} / ${BOLD}Password123!${NC}"
