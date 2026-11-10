export interface UserProfile {
  id: string
  email: string
  fullName: string
  username?: string
  first_name?: string
  last_name?: string
  avatarUrl?: string
  profile?: string
  role?: string
  superAdmin: boolean
  organizations: OrgMembershipSummary[]
}

export interface OrgMembershipSummary {
  orgId: string
  name: string
  slug: string
  tier: string
  roles: string[]
  permissions: string[]
}

export interface LoginResponse {
  accessToken: string
  mfaRequired: boolean
  mfaTicket?: string
  user?: UserProfile
}

export interface Member {
  membershipId: string
  userId: string
  email: string
  fullName: string
  status: string
  roleAssignments: RoleAssignmentInfo[]
}

export interface RoleAssignmentInfo {
  assignmentId: string
  roleId: string
  roleName: string
  appId?: string | null
}

export interface Role {
  id: string
  organizationId?: string | null
  name: string
  description?: string
  system: boolean
  permissions: string[]
}

export interface Permission {
  id: string
  code: string
  resource: string
  action: string
  description: string
}

export interface Invitation {
  id: string
  email: string
  roleId: string
  roleName: string
  appId?: string | null
  expiresAt: string
  acceptedAt?: string | null
  createdAt: string
}

export interface Organization {
  id: string
  name: string
  slug: string
  status: string
  tier: string
  quotas: Record<string, any>
  createdAt: string
}
