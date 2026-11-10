import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAppStore } from './appStore'
import { useAuthStore } from './authStore'
import { appsApi, type AppSummary } from '../api/apps'

describe('useAppStore App Context & Deduplication', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('selectApp updates active app context in authStore', () => {
    const authStore = useAuthStore()
    const appStore = useAppStore()

    appStore.apps = [
      {
        id: 'app-retail',
        organizationId: 'org-1',
        name: 'Acme Retail',
        configuredProviders: ['FCM', 'WEBPUSH'],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    ]

    appStore.selectApp('app-retail')
    expect(authStore.currentAppId).toBe('app-retail')
    expect(appStore.currentApp?.name).toBe('Acme Retail')
  })

  it('deduplicates concurrent loadApps calls into a single API request', async () => {
    const authStore = useAuthStore()
    const appStore = useAppStore()
    authStore.setCurrentOrg('org-test-123')

    const mockApps: AppSummary[] = [
      {
        id: '11111111-2222-3333-4444-555555555551',
        organizationId: 'org-test-123',
        name: 'App 1',
        configuredProviders: ['WEBPUSH'],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    ]

    const listSpy = vi.spyOn(appsApi, 'list').mockImplementation(async (_orgId: string) => {
      // Simulate network delay
      await new Promise((r) => setTimeout(r, 50))
      return mockApps
    })

    // Dispatch 3 concurrent loadApps calls (simulating Topbar OrgSwitcher + DashboardView mounting simultaneously)
    const [res1, res2, res3] = await Promise.all([
      appStore.loadApps('org-test-123'),
      appStore.loadApps('org-test-123'),
      appStore.loadApps('org-test-123'),
    ])

    // Should only have called appsApi.list ONCE
    expect(listSpy).toHaveBeenCalledTimes(1)
    expect(res1).toEqual(mockApps)
    expect(res2).toEqual(mockApps)
    expect(res3).toEqual(mockApps)
    expect(authStore.currentAppId).toBe('11111111-2222-3333-4444-555555555551')

    // Subsequent call uses cache without dispatching another API call
    const resCached = await appStore.loadApps('org-test-123')
    expect(listSpy).toHaveBeenCalledTimes(1)
    expect(resCached).toEqual(mockApps)
  })
})
