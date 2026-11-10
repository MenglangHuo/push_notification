-- V1__init_iam_and_audit.sql
-- Baseline schema for IAM, RBAC, Invitations, Audit Logs, and Distributed Locks

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ============================================================================
-- 1. Identity & Organization Tables
-- ============================================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    avatar_url TEXT,
    is_super_admin BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);

CREATE TABLE user_mfa (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    totp_secret_encrypted VARCHAR(255) NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    backup_codes_hash TEXT[],
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_mfa_user_id UNIQUE(user_id)
);

CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    tier VARCHAR(50) NOT NULL DEFAULT 'FREE',
    quotas JSONB NOT NULL DEFAULT '{"max_apps": 5, "max_devices": 100000, "rate_limit_per_min": 1000}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_organizations_slug ON organizations(slug);

CREATE TABLE memberships (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_memberships_user_org UNIQUE(user_id, organization_id)
);

CREATE INDEX idx_memberships_org ON memberships(organization_id);
CREATE INDEX idx_memberships_user ON memberships(user_id);

-- ============================================================================
-- 2. RBAC Tables (Permissions, Roles, Scoped Role Assignments)
-- ============================================================================

CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(100) NOT NULL UNIQUE,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_roles_org_name UNIQUE(organization_id, name)
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE member_role_assignments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    membership_id UUID NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    app_id UUID, -- nullable: NULL means org-wide role, non-null means scoped to a specific app
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_member_role_assignment UNIQUE (membership_id, role_id, app_id)
);

CREATE INDEX idx_mra_membership ON member_role_assignments(membership_id);
CREATE INDEX idx_mra_app ON member_role_assignments(app_id);

CREATE TABLE invitations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    app_id UUID,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    invited_by UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_invitations_org ON invitations(organization_id);
CREATE INDEX idx_invitations_token_hash ON invitations(token_hash);

-- ============================================================================
-- 3. Audit Logs & Shedlock
-- ============================================================================

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    app_id UUID,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_type VARCHAR(50) NOT NULL DEFAULT 'USER',
    action VARCHAR(100) NOT NULL,
    target_resource VARCHAR(100) NOT NULL,
    target_id VARCHAR(100),
    before_state JSONB,
    after_state JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_org_created ON audit_logs(organization_id, created_at DESC);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);

CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

-- ============================================================================
-- 4. Seed Data: Permissions & System Preset Roles
-- ============================================================================

INSERT INTO permissions (id, code, resource, action, description) VALUES
  (uuid_generate_v4(), 'org:read', 'org', 'read', 'View organization profile and settings'),
  (uuid_generate_v4(), 'org:update', 'org', 'update', 'Modify organization details and quotas'),
  (uuid_generate_v4(), 'org:delete', 'org', 'delete', 'Delete organization and all associated resources'),
  (uuid_generate_v4(), 'member:read', 'member', 'read', 'View organization members and roles'),
  (uuid_generate_v4(), 'member:invite', 'member', 'invite', 'Invite new members to the organization'),
  (uuid_generate_v4(), 'member:update', 'member', 'update', 'Update member roles and scopes'),
  (uuid_generate_v4(), 'member:remove', 'member', 'remove', 'Remove members from the organization'),
  (uuid_generate_v4(), 'role:read', 'role', 'read', 'View system and custom roles'),
  (uuid_generate_v4(), 'role:create', 'role', 'create', 'Create custom roles'),
  (uuid_generate_v4(), 'role:update', 'role', 'update', 'Edit custom roles'),
  (uuid_generate_v4(), 'role:delete', 'role', 'delete', 'Delete custom roles'),
  (uuid_generate_v4(), 'app:read', 'app', 'read', 'View apps in the organization'),
  (uuid_generate_v4(), 'app:create', 'app', 'create', 'Create new apps'),
  (uuid_generate_v4(), 'app:update', 'app', 'update', 'Edit app settings'),
  (uuid_generate_v4(), 'app:delete', 'app', 'delete', 'Delete apps'),
  (uuid_generate_v4(), 'apikey:read', 'apikey', 'read', 'View API key metadata'),
  (uuid_generate_v4(), 'apikey:create', 'apikey', 'create', 'Create new server API keys and SDK keys'),
  (uuid_generate_v4(), 'apikey:rotate', 'apikey', 'rotate', 'Rotate API keys'),
  (uuid_generate_v4(), 'apikey:revoke', 'apikey', 'revoke', 'Revoke API keys'),
  (uuid_generate_v4(), 'credential:read', 'credential', 'read', 'View provider credential metadata'),
  (uuid_generate_v4(), 'credential:update', 'credential', 'update', 'Configure provider credentials (FCM/APNs/VAPID)'),
  (uuid_generate_v4(), 'credential:delete', 'credential', 'delete', 'Remove provider credentials'),
  (uuid_generate_v4(), 'notification:read', 'notification', 'read', 'View notification history and details'),
  (uuid_generate_v4(), 'notification:create', 'notification', 'create', 'Compose and schedule push notifications'),
  (uuid_generate_v4(), 'notification:cancel', 'notification', 'cancel', 'Cancel scheduled push notifications'),
  (uuid_generate_v4(), 'notification:delete', 'notification', 'delete', 'Delete notification drafts and logs'),
  (uuid_generate_v4(), 'segment:read', 'segment', 'read', 'View segments and filters'),
  (uuid_generate_v4(), 'segment:create', 'segment', 'create', 'Create custom audience segments'),
  (uuid_generate_v4(), 'segment:update', 'segment', 'update', 'Update audience segments'),
  (uuid_generate_v4(), 'segment:delete', 'segment', 'delete', 'Delete audience segments'),
  (uuid_generate_v4(), 'device:read', 'device', 'read', 'View registered devices'),
  (uuid_generate_v4(), 'device:export', 'device', 'export', 'Export device lists'),
  (uuid_generate_v4(), 'device:delete', 'device', 'delete', 'Deactivate or delete device tokens'),
  (uuid_generate_v4(), 'log:read', 'log', 'read', 'View delivery and API request logs'),
  (uuid_generate_v4(), 'log:export', 'log', 'export', 'Export delivery logs'),
  (uuid_generate_v4(), 'audit:read', 'audit', 'read', 'View audit logs'),
  (uuid_generate_v4(), 'audit:export', 'audit', 'export', 'Export audit logs to CSV'),
  (uuid_generate_v4(), 'analytics:read', 'analytics', 'read', 'View analytics and performance charts');

-- System Roles (organization_id IS NULL denotes system-wide preset roles)
INSERT INTO roles (id, organization_id, name, description, is_system) VALUES
  ('11111111-1111-1111-1111-111111111111', NULL, 'Owner', 'Complete control over organization, apps, members and billing', TRUE),
  ('22222222-2222-2222-2222-222222222222', NULL, 'Admin', 'Administrative access to manage apps, members and campaigns', TRUE),
  ('33333333-3333-3333-3333-333333333333', NULL, 'Developer', 'Technical access to apps, API keys, credentials, and test tools', TRUE),
  ('44444444-4444-4444-4444-444444444444', NULL, 'Marketer', 'Campaign creator access for notifications, segments, and analytics', TRUE),
  ('55555555-5555-5555-5555-555555555555', NULL, 'Viewer', 'Read-only access across apps, notifications, logs, and analytics', TRUE);

-- Map Permissions to Owner (All Permissions)
INSERT INTO role_permissions (role_id, permission_id)
SELECT '11111111-1111-1111-1111-111111111111', id FROM permissions;

-- Map Permissions to Admin (All except org:delete)
INSERT INTO role_permissions (role_id, permission_id)
SELECT '22222222-2222-2222-2222-222222222222', id FROM permissions
WHERE code NOT IN ('org:delete');

-- Map Permissions to Developer
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
);

-- Map Permissions to Marketer
INSERT INTO role_permissions (role_id, permission_id)
SELECT '44444444-4444-4444-4444-444444444444', id FROM permissions
WHERE code IN (
  'org:read', 'member:read', 'app:read',
  'notification:read', 'notification:create', 'notification:cancel',
  'segment:read', 'segment:create', 'segment:update', 'segment:delete',
  'device:read', 'log:read', 'analytics:read'
);

-- Map Permissions to Viewer
INSERT INTO role_permissions (role_id, permission_id)
SELECT '55555555-5555-5555-5555-555555555555', id FROM permissions
WHERE code IN (
  'org:read', 'member:read', 'role:read', 'app:read', 'apikey:read',
  'credential:read', 'notification:read', 'segment:read', 'device:read',
  'log:read', 'analytics:read'
);

-- ============================================================================
-- 5. Seed Platform Admin & Demo Organization
-- Password: Password123!
-- Argon2id: $argon2id$v=19$m=65536,t=3,p=1$XyV4a3FvM2JtOXZyNGg1Zw$v5n4ZqYd5q9zS2b4c8w0L6y8j1K3m5o7q9s1u3w5y7z
-- (Will also be verifiable via Spring Security Argon2PasswordEncoder)
-- ============================================================================

INSERT INTO users (id, email, password_hash, full_name, is_super_admin, status) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'admin@pushnotify.local', '$argon2id$v=19$m=65536,t=3,p=1$xWBnbcjfCRCx5nUMEZMdIA$JO1EhD1j9kNoqCXeMiebhSx0lD1svBzGOfsjVg8cIJk', 'Platform Super Admin', TRUE, 'ACTIVE'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'demo@pushnotify.local', '$argon2id$v=19$m=65536,t=3,p=1$xWBnbcjfCRCx5nUMEZMdIA$JO1EhD1j9kNoqCXeMiebhSx0lD1svBzGOfsjVg8cIJk', 'Demo Owner', FALSE, 'ACTIVE');

INSERT INTO organizations (id, name, slug, status, tier) VALUES
  ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Acme Corporation', 'acme', 'ACTIVE', 'ENTERPRISE');

INSERT INTO memberships (id, user_id, organization_id, status) VALUES
  ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'ACTIVE'),
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'ACTIVE');

-- Assign Owner role to both in Acme Corp
INSERT INTO member_role_assignments (id, membership_id, role_id, app_id) VALUES
  (uuid_generate_v4(), 'dddddddd-dddd-dddd-dddd-dddddddddddd', '11111111-1111-1111-1111-111111111111', NULL),
  (uuid_generate_v4(), 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '11111111-1111-1111-1111-111111111111', NULL);
