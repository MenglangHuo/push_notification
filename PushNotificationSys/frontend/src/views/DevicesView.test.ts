import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import DevicesView from './DevicesView.vue'
import { useAuthStore } from '@/stores/authStore'
import { devicesApi, type DeviceSummary, type DeviceStats } from '@/api/devices'
import { appsApi, type AppSummary } from '@/api/apps'

// Mock vue-router
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { appId: 'app-test-123' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

// Mock devicesApi and appsApi
vi.mock('@/api/devices', () => ({
  devicesApi: {
    list: vi.fn(),
    stats: vi.fn(),
    delete: vi.fn(),
    register: vi.fn(),
  },
}))

vi.mock('@/api/apps', () => ({
  appsApi: {
    list: vi.fn(),
  },
}))

describe('DevicesView Component Platform Filtering', () => {
  const mockDevices: DeviceSummary[] = [
    {
      id: 'dev-1',
      appId: 'app-test-123',
      platform: 'ANDROID',
      pushTokenMasked: 'fcm_mock_token_s24_1',
      deviceModel: 'Samsung Galaxy S24 Ultra',
      osVersion: 'Android 14',
      appVersion: '4.2.0',
      language: 'en',
      timezone: 'America/New_York',
      active: true,
      lastActiveAt: '2026-11-12T10:52:00Z',
      createdAt: '2026-11-01T10:00:00Z',
      tags: { tier: 'gold' },
    },
    {
      id: 'dev-2',
      appId: 'app-test-123',
      platform: 'IOS',
      pushTokenMasked: 'apns_mock_token_iphone_2',
      deviceModel: 'iPhone 15 Pro Max',
      osVersion: 'iOS 18.1',
      appVersion: '4.2.0',
      language: 'en',
      timezone: 'Europe/London',
      active: true,
      lastActiveAt: '2026-11-12T10:50:00Z',
      createdAt: '2026-11-02T10:00:00Z',
      tags: { vip: 'true' },
    },
    {
      id: 'dev-3',
      appId: 'app-test-123',
      platform: 'WEB',
      pushTokenMasked: 'https://fcm.googleapis.com/fcm/send/web_3',
      deviceModel: 'Mozilla/5.0 (X11; Linux x86_64)',
      osVersion: 'Linux x86_64',
      appVersion: '1.0.0',
      language: 'en',
      timezone: 'Asia/Tokyo',
      active: true,
      lastActiveAt: '2026-11-12T09:12:00Z',
      createdAt: '2026-11-05T10:00:00Z',
      tags: {},
    },
  ]

  const mockStats: DeviceStats = {
    totalDevices: 3,
    activeDevices: 3,
    androidActive: 1,
    iosActive: 1,
    webActive: 1,
  }

  const mockApps: AppSummary[] = [
    {
      id: 'app-test-123',
      organizationId: 'org-test-456',
      name: 'Bronx Main App',
      configuredProviders: ['FCM', 'APNS', 'WEBPUSH'],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-11-01T00:00:00Z',
    },
  ]

  beforeEach(() => {
    setActivePinia(createPinia())
    const authStore = useAuthStore()
    authStore.currentOrgId = 'org-test-456'
    authStore.currentAppId = 'app-test-123'

    vi.mocked(devicesApi.list).mockResolvedValue({
      content: mockDevices,
      totalElements: 3,
      totalPages: 1,
      number: 0,
    })
    vi.mocked(devicesApi.stats).mockResolvedValue(mockStats)
    vi.mocked(appsApi.list).mockResolvedValue(mockApps)
  })

  it('renders metric cards with platform subscription counts', async () => {
    const wrapper = mount(DevicesView)
    await flushPromises()

    expect(wrapper.text()).toContain('Device Registry')
    expect(wrapper.text()).toContain('Total Subscriptions')
    expect(wrapper.text()).toContain('Active Android')
    expect(wrapper.text()).toContain('Active iOS')
    expect(wrapper.text()).toContain('Active Web')
  })

  it('renders all platform registered devices initially', async () => {
    const wrapper = mount(DevicesView)
    await flushPromises()

    expect(wrapper.text()).toContain('Samsung Galaxy S24 Ultra')
    expect(wrapper.text()).toContain('iPhone 15 Pro Max')
    expect(wrapper.text()).toContain('Mozilla/5.0')
  })

  it('filters by Android when clicking the Android platform tab', async () => {
    const wrapper = mount(DevicesView)
    await flushPromises()

    // Find and click the Android platform tab button
    const buttons = wrapper.findAll('button')
    const androidTab = buttons.find((b) => b.text().includes('Android'))
    expect(androidTab).toBeDefined()
    await androidTab!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Samsung Galaxy S24 Ultra')
    expect(wrapper.text()).not.toContain('iPhone 15 Pro Max')
    expect(wrapper.text()).not.toContain('Mozilla/5.0')
    expect(wrapper.text()).toContain('Platform: Android')
  })

  it('filters by iOS when clicking the iOS metric card', async () => {
    const wrapper = mount(DevicesView)
    await flushPromises()

    // Click Active iOS card
    const iosCard = wrapper.findAll('.cursor-pointer').find((el) => el.text().includes('Active iOS'))
    expect(iosCard).toBeDefined()
    await iosCard!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('iPhone 15 Pro Max')
    expect(wrapper.text()).not.toContain('Samsung Galaxy S24 Ultra')
    expect(wrapper.text()).not.toContain('Mozilla/5.0')
    expect(wrapper.text()).toContain('Platform: iOS')
  })

  it('filters by Web when clicking the Web platform tab', async () => {
    const wrapper = mount(DevicesView)
    await flushPromises()

    const buttons = wrapper.findAll('button')
    const webTab = buttons.find((b) => b.text().includes('Web'))
    expect(webTab).toBeDefined()
    await webTab!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Mozilla/5.0')
    expect(wrapper.text()).not.toContain('Samsung Galaxy S24 Ultra')
    expect(wrapper.text()).not.toContain('iPhone 15 Pro Max')
    expect(wrapper.text()).toContain('Platform: Web')
  })

  it('resets all filters when clicking Reset Filters in the active banner', async () => {
    const wrapper = mount(DevicesView)
    await flushPromises()

    // Click Android tab first
    const buttons = wrapper.findAll('button')
    const androidTab = buttons.find((b) => b.text().includes('Android'))
    await androidTab!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Platform: Android')

    // Click Reset Filters button
    const resetBtn = wrapper.findAll('button').find((b) => b.text().includes('Reset Filters'))
    expect(resetBtn).toBeDefined()
    await resetBtn!.trigger('click')
    await flushPromises()

    // All devices should now be visible again
    expect(wrapper.text()).toContain('Samsung Galaxy S24 Ultra')
    expect(wrapper.text()).toContain('iPhone 15 Pro Max')
    expect(wrapper.text()).toContain('Mozilla/5.0')
  })
})
