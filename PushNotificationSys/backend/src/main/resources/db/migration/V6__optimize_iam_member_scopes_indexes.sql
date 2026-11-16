-- Migration: V6__optimize_iam_member_scopes_indexes.sql
-- Optimizes IAM member scoping, RBAC lookups, and active membership resolution at scale.

CREATE INDEX IF NOT EXISTS idx_mra_membership_app 
    ON member_role_assignments (membership_id, app_id);

CREATE INDEX IF NOT EXISTS idx_memberships_user_org_status 
    ON memberships (user_id, organization_id, status);
