-- V2__init_apps_and_credentials.sql
-- Apps, Encrypted Provider Credentials Vault, and REST & SDK API Keys

-- ============================================================================
-- 1. Applications Table
-- ============================================================================

CREATE TABLE apps (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_apps_org_name UNIQUE(organization_id, name)
);

CREATE INDEX idx_apps_organization ON apps(organization_id);

-- ============================================================================
-- 2. Provider Credentials Vault (Write-only, Encrypted at Rest)
-- ============================================================================

CREATE TABLE provider_credentials (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    provider_type VARCHAR(30) NOT NULL,
    credential_data_encrypted TEXT NOT NULL,
    metadata_masked JSONB NOT NULL DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_credentials_app_provider UNIQUE(app_id, provider_type)
);

CREATE INDEX idx_credentials_app ON provider_credentials(app_id);

-- ============================================================================
-- 3. API Keys Table (REST & SDK Public Keys)
-- ============================================================================

CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    key_type VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(100) NOT NULL UNIQUE,
    scopes TEXT[] NOT NULL DEFAULT '{}',
    allowed_origins TEXT[] NOT NULL DEFAULT '{}',
    allowed_bundle_ids TEXT[] NOT NULL DEFAULT '{}',
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_keys_app ON api_keys(app_id);
CREATE INDEX idx_api_keys_hash ON api_keys(key_hash);
CREATE INDEX idx_api_keys_prefix ON api_keys(key_prefix);

-- ============================================================================
-- 4. Seed Demo Apps for Acme Corporation
-- ============================================================================

INSERT INTO apps (id, organization_id, name, description) VALUES
  ('11111111-2222-3333-4444-555555555551', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'Acme Retail Store', 'Primary customer-facing e-commerce shopping app (iOS, Android, Web)'),
  ('11111111-2222-3333-4444-555555555552', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'Acme Driver Delivery', 'Logistics delivery partner application');

-- Seed a Demo REST API Key for Acme Retail Store
-- Raw secret: os_live_demo1234567890abcdef1234567890
-- SHA-256 hash: c3b477b21f2401a9d5984a85a08d93f0a4b9828fddb617626d3dc078b55ca0ec
-- Prefix: os_live_demo
INSERT INTO api_keys (id, app_id, key_type, name, key_prefix, key_hash, scopes) VALUES
  ('aaaaaaaa-1111-2222-3333-444444444441', '11111111-2222-3333-4444-555555555551', 'SERVER_REST', 'Server Production Key', 'os_live_demo', 'c3b477b21f2401a9d5984a85a08d93f0a4b9828fddb617626d3dc078b55ca0ec', ARRAY['notification:create', 'notification:read', 'device:read', 'segment:read']);

-- Seed a Demo Public SDK Key for Acme Retail Store
-- Raw secret: os_pub_demo9876543210abcdef9876543210
-- SHA-256 hash: 8690233766a189019b80bc3a4f3adb0c9ce44a6da3c689328b23168102664513
-- Prefix: os_pub_demo
INSERT INTO api_keys (id, app_id, key_type, name, key_prefix, key_hash, scopes, allowed_origins) VALUES
  ('aaaaaaaa-1111-2222-3333-444444444442', '11111111-2222-3333-4444-555555555551', 'SDK_PUBLIC', 'Web Client SDK Key', 'os_pub_demo', '8690233766a189019b80bc3a4f3adb0c9ce44a6da3c689328b23168102664513', ARRAY['device:create', 'device:update', 'event:track'], ARRAY['http://localhost:5173', 'https://retail.acme.com']);
