import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import SegmentsView from './SegmentsView.vue'
import { useAuthStore } from '@/stores/authStore'
import { segmentsApi, type Segment } from '@/api/segments'

// Mock vue-router
const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { appId: 'app-test-123' } }),
  useRouter: () => ({ push: pushMock }),
}))

// Mock segmentsApi
vi.mock('@/api/segments', () => ({
  segmentsApi: {
    list: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('SegmentsView Component', () => {
  const mockSegments: Segment[] = [
    {
      id: 'seg-1',
      appId: 'app-test-123',
      name: 'Inactive Android Users',
      description: 'Target devices that have not opened app in 14 days',
      estimatedCount: 1540,
      active: true,
      filterAst: {
        conjunction: 'AND',
        rules: [
          { field: 'platform', op: 'EQ', value: 'ANDROID' },
          { field: 'last_active_days', op: 'GTE', value: 14 },
        ],
      },
      createdAt: '2026-11-10T10:00:00Z',
      updatedAt: '2026-11-12T08:00:00Z',
    },
    {
      id: 'seg-2',
      appId: 'app-test-123',
      name: 'VIP iOS Shoppers',
      description: 'Target iOS users with VIP tier tag',
      estimatedCount: 420,
      active: true,
      filterAst: {
        conjunction: 'OR',
        rules: [
          { field: 'platform', op: 'EQ', value: 'IOS' },
          { field: 'tag', tagKey: 'tier', op: 'EQ', value: 'vip' },
        ],
      },
      createdAt: '2026-11-08T12:00:00Z',
      updatedAt: '2026-11-08T12:00:00Z',
    },
  ]

  function createWrapper() {
    return mount(SegmentsView, {
      global: {
        stubs: {
          ModernModal: {
            template: `
              <div v-if="modelValue" class="modern-modal-stub">
                <div class="modal-title">{{ title }}</div>
                <div class="modal-subtitle">{{ subtitle }}</div>
                <slot />
                <slot name="footer" />
              </div>
            `,
            props: ['modelValue', 'title', 'subtitle', 'icon', 'iconClass', 'initialWidth', 'size'],
          },
        },
      },
    })
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.restoreAllMocks()
    pushMock.mockReset()
    vi.mocked(segmentsApi.list).mockResolvedValue([...mockSegments])

    const authStore = useAuthStore()
    authStore.currentAppId = 'app-test-123'

    // Mock clipboard
    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    })
  })

  it('renders executive MetricCard KPI strip and segment cards with reachable status and match mode', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    // KPI strip
    expect(wrapper.text()).toContain('Active Segments')
    expect(wrapper.text()).toContain('Reachable Subscribers')
    expect(wrapper.text()).toContain('~1,960')
    expect(wrapper.text()).toContain('Evaluating Filter Rules')

    // Segment cards
    expect(wrapper.text()).toContain('Inactive Android Users')
    expect(wrapper.text()).toContain('~1,540 Reachable')
    expect(wrapper.text()).toContain('Match ALL · 2 rules')

    expect(wrapper.text()).toContain('VIP iOS Shoppers')
    expect(wrapper.text()).toContain('~420 Reachable')
    expect(wrapper.text()).toContain('Match ANY · 2 rules')
  })

  it('renders human-readable criteria pills without raw brackets or uppercase platform dumps', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('Platform')
    expect(text).toContain('Android')
    expect(text).toContain('Inactive for')
    expect(text).toContain('14+ days')
    expect(text).toContain('Apple iOS')
    expect(text).toContain('tier')
    expect(text).toContain('vip')
  })

  it('opens Filter AST Inspector modal and allows copying AST JSON', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    const astButtons = wrapper.findAll('button').filter((b) => b.text().includes('AST JSON'))
    expect(astButtons.length).toBe(2)

    // Open AST modal on first card
    await astButtons[0].trigger('click')
    await flushPromises()

    const modal = wrapper.find('.modern-modal-stub')
    expect(modal.exists()).toBe(true)
    expect(modal.text()).toContain('Inactive Android Users — Filter AST')
    expect(modal.text()).toContain('filter_ast.json')
    expect(modal.text()).toContain('ANDROID')

    const copyBtn = modal.findAll('button').find((b) => b.text().includes('Copy JSON'))
    expect(copyBtn).toBeDefined()
    await copyBtn!.trigger('click')
    expect(navigator.clipboard.writeText).toHaveBeenCalled()
  })

  it('navigates to Edit Rules and notification composer cleanly', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    const editButtons = wrapper.findAll('button').filter((b) => b.text().includes('Edit Rules'))
    expect(editButtons.length).toBe(2)
    await editButtons[0].trigger('click')
    expect(pushMock).toHaveBeenCalledWith('/apps/app-test-123/segments/seg-1/edit')

    const sendPushButtons = wrapper.findAll('button').filter((b) => b.text().includes('Send Push'))
    expect(sendPushButtons.length).toBe(2)
    await sendPushButtons[0].trigger('click')
    expect(pushMock).toHaveBeenCalledWith({
      path: '/apps/app-test-123/notifications/new',
      query: { target: 'SEGMENT', segmentId: 'seg-1' },
    })
  })

  it('filters segments via BaseTabs and toggles between Grid and Table view', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    // Filter using BaseTabs Apple iOS tab
    const iosTab = wrapper.findAll('button').find((b) => b.text().includes('Apple iOS'))
    expect(iosTab).toBeDefined()
    await iosTab!.trigger('click')
    expect(wrapper.text()).toContain('VIP iOS Shoppers')
    expect(wrapper.text()).not.toContain('Inactive Android Users')

    // Switch back to All Cohorts tab
    const allTab = wrapper.findAll('button').find((b) => b.text().includes('All Cohorts'))
    await allTab!.trigger('click')
    expect(wrapper.text()).toContain('Inactive Android Users')

    // Switch to Table View
    const tableBtn = wrapper.find('button[title="Data Table View"]')
    expect(tableBtn.exists()).toBe(true)
    await tableBtn.trigger('click')
    expect(wrapper.text()).toContain('Segment Cohort')
    expect(wrapper.text()).toContain('Targeting Rules')
    expect(wrapper.text()).toContain('Estimated Reach')
  })
})
