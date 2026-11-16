import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from './authStore'
import { authApi } from '../api/auth'

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

  it('logout immediately clears authentication state before backend request completes', async () => {
    let resolveLogout!: () => void
    const logoutSpy = vi.spyOn(authApi, 'logout').mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveLogout = resolve
        }),
    )

    const authStore = useAuthStore()
    authStore.setAccessToken('jwt-token-123')
    authStore.user = {
      id: 'user-1',
      email: 'admin@pushnotify.local',
      fullName: 'Super Admin',
      superAdmin: true,
      organizations: [],
    }
    localStorage.setItem('bronx_user', JSON.stringify(authStore.user))

    expect(authStore.isAuthenticated).toBe(true)

    const logoutPromise = authStore.logout()

    // Synchronously cleared before the API promise settles so router guestOnly guards allow /login
    expect(authStore.isAuthenticated).toBe(false)
    expect(authStore.accessToken).toBe(null)
    expect(authStore.user).toBe(null)
    expect(localStorage.getItem('bronx_access_token')).toBe(null)
    expect(localStorage.getItem('bronx_user')).toBe(null)
    expect(authStore.currentOrgId).toBe(null)
    expect(authStore.currentAppId).toBe(null)
    expect(localStorage.getItem('push_current_org_id')).toBe(null)
    expect(localStorage.getItem('push_current_app_id')).toBe(null)

    resolveLogout()
    await logoutPromise
    expect(logoutSpy).toHaveBeenCalledTimes(1)
    logoutSpy.mockRestore()
  })

  it('login clears stale currentAppId from previous session and validates currentOrgId', async () => {
    const authStore = useAuthStore()
    authStore.setCurrentOrg('old-org-not-in-new-user')
    authStore.setCurrentApp('stale-unaccessible-app-id')

    const loginSpy = vi.spyOn(authApi, 'login').mockResolvedValue({
      accessToken: 'new-jwt',
      mfaRequired: false,
      user: {
        id: 'user-scoped',
        email: 'scoped@company.com',
        fullName: 'Scoped User',
        superAdmin: false,
        organizations: [
          {
            orgId: 'org-allowed',
            name: 'Allowed Org',
            slug: 'allowed',
            tier: 'FREE',
            roles: ['Owner'],
            permissions: ['analytics:read'],
          },
        ],
      },
    } as any)

    const ok = await authStore.login('scoped@company.com', 'Password123!')
    expect(ok).toBe(true)
    expect(authStore.currentOrgId).toBe('org-allowed')
    expect(authStore.currentAppId).toBe(null)

    loginSpy.mockRestore()
  })
})
