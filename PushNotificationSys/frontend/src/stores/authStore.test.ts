import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from './authStore'

describe('useAuthStore Permissions & Tenancy', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('super admin has all permissions unconditionally', () => {
    const authStore = useAuthStore()
    authStore.user = {
      id: 'user-1',
      email: 'admin@pushnotify.local',
      fullName: 'Super Admin',
      superAdmin: true,
      organizations: [],
    }

    expect(authStore.hasPermission('org:delete')).toBe(true)
    expect(authStore.hasPermission('anything:custom')).toBe(true)
  })

  it('normal user returns true only for granted permissions in current organization', () => {
    const authStore = useAuthStore()
    authStore.user = {
      id: 'user-2',
      email: 'dev@company.com',
      fullName: 'Developer',
      superAdmin: false,
      organizations: [
        {
          orgId: 'org-acme',
          name: 'Acme',
          slug: 'acme',
          tier: 'FREE',
          roles: ['Developer'],
          permissions: ['member:read', 'notification:create'],
        },
      ],
    }
    authStore.currentOrgId = 'org-acme'

    expect(authStore.hasPermission('member:read')).toBe(true)
    expect(authStore.hasPermission('notification:create')).toBe(true)
    expect(authStore.hasPermission('org:delete')).toBe(false)
    expect(authStore.hasPermission('member:invite')).toBe(false)
  })

  it('returns false if not authenticated or no org selected', () => {
    const authStore = useAuthStore()
    expect(authStore.hasPermission('member:read')).toBe(false)

    authStore.user = {
      id: 'user-3',
      email: 'user@company.com',
      fullName: 'User',
      superAdmin: false,
      organizations: [],
    }
    expect(authStore.hasPermission('member:read')).toBe(false)
  })

  it('switching currentOrg resets currentApp', () => {
    const authStore = useAuthStore()
    authStore.setCurrentApp('app-123')
    expect(authStore.currentAppId).toBe('app-123')

    authStore.setCurrentOrg('org-456')
    expect(authStore.currentOrgId).toBe('org-456')
    expect(authStore.currentAppId).toBe(null)
  })
})
