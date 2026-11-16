import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import NotificationsView from './NotificationsView.vue'
import { useAuthStore } from '@/stores/authStore'
import { notificationsApi } from '@/api/notifications'

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { appId: 'app-1' },
    query: {},
  }),
  useRouter: () => ({
    push: vi.fn(),
  }),
}))

vi.mock('@/api/notifications', () => ({
  notificationsApi: {
    list: vi.fn(),
    listLogs: vi.fn(),
  },
}))

describe('NotificationsView Component', () => {
  const mockNotifications = [
    {
      id: 'notif-1',
      appId: 'app-1',
      title: 'Flash Sale Alert! ⚡',
      body: 'Welcome to the Admin Playground!',
      status: 'COMPLETED',
      targetType: 'DEVICE_IDS',
      targetPayload: {},
      customData: {},
      priority: 'HIGH',
      sentCount: 2,
      deliveredCount: 2,
      failedCount: 0,
      openedCount: 1,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    {
      id: 'notif-2',
      appId: 'app-1',
      title: 'Pending Android Test',
      body: 'Processing push delivery to ANDROID devices.',
      status: 'PROCESSING',
      targetType: 'DEVICE_IDS',
      targetPayload: {},
      customData: {},
      priority: 'NORMAL',
      sentCount: 0,
      deliveredCount: 0,
      failedCount: 0,
      openedCount: 0,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
  ]

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.restoreAllMocks()
    vi.mocked(notificationsApi.list).mockResolvedValue({
      content: [...mockNotifications],
      totalElements: 2,
      totalPages: 1,
      number: 0,
    } as any)
  })

  async function createWrapper() {
    const authStore = useAuthStore()
    authStore.currentAppId = 'app-1'
    authStore.currentOrgId = 'org-1'

    const wrapper = mount(NotificationsView, {
      global: {
        stubs: {
          TablePagination: true,
          ModernModal: true,
        },
      },
    })

    await flushPromises()
    return { wrapper, authStore }
  }

  it('renders notifications with custom table columns and rich badges', async () => {
    const { wrapper } = await createWrapper()

    expect(wrapper.text()).toContain('Push Notification Queue')
    expect(wrapper.text()).toContain('Flash Sale Alert! ⚡')
    expect(wrapper.text()).toContain('Pending Android Test')
    expect(wrapper.text()).toContain('Completed')
    expect(wrapper.text()).toContain('Processing')
    expect(wrapper.text()).toContain('Device Tokens')
  })

  it('filters notifications accurately by status', async () => {
    const { wrapper } = await createWrapper()

    // Initially both items are shown
    expect(wrapper.text()).toContain('Flash Sale Alert! ⚡')
    expect(wrapper.text()).toContain('Pending Android Test')

    // Find the Status select trigger
    const statusSelect = wrapper.findAllComponents({ name: 'CustomSelect' })
    expect(statusSelect.length).toBeGreaterThan(0)

    // Set filter to COMPLETED
    await statusSelect[0].vm.$emit('update:modelValue', 'COMPLETED')
    await flushPromises()

    expect(wrapper.text()).toContain('Flash Sale Alert! ⚡')
    expect(wrapper.text()).not.toContain('Pending Android Test')

    // Set filter to PROCESSING
    await statusSelect[0].vm.$emit('update:modelValue', 'PROCESSING')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Flash Sale Alert! ⚡')
    expect(wrapper.text()).toContain('Pending Android Test')
  })

  it('filters notifications by search query', async () => {
    const { wrapper } = await createWrapper()

    const searchInput = wrapper.find('input[placeholder*="Filter by title"]')
    expect(searchInput.exists()).toBe(true)

    await searchInput.setValue('Flash Sale')
    await flushPromises()

    expect(wrapper.text()).toContain('Flash Sale Alert! ⚡')
    expect(wrapper.text()).not.toContain('Pending Android Test')
  })
})
