import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import OrgSwitcher from './OrgSwitcher.vue'
import { useAuthStore } from '@/stores/authStore'
import { useAppStore } from '@/stores/appStore'

// Mock vue-router
const pushMock = vi.fn()
let routeMock = {
  params: {} as Record<string, string>,
  query: {} as Record<string, string>,
  path: '/dashboard',
  name: 'Dashboard',
}

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
  }),
  useRoute: () => routeMock,
}))

const mockApps = [
  {
    id: 'app-1',
    organizationId: 'org-1',
    name: 'NIYEAY-HR',
    configuredProviders: ['FCM', 'APNS'],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: 'app-2',
    organizationId: 'org-1',
    name: 'CRM System',
    configuredProviders: ['WEBPUSH'],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
]

vi.mock('@/api/apps', () => ({
  appsApi: {
    list: vi.fn().mockImplementation(() => Promise.resolve([...mockApps])),
    get: vi.fn(),
    create: vi.fn(),
  },
}))

vi.mock('@/api/orgs', () => ({
  orgsApi: {
    create: vi.fn(),
    list: vi.fn(),
  },
}))

describe('OrgSwitcher Component', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.restoreAllMocks()
    routeMock = {
      params: {},
      query: {},
      path: '/dashboard',
      name: 'Dashboard',
    }
  })

  function createTestWrapper() {
    const authStore = useAuthStore()
    const appStore = useAppStore()

    authStore.user = {
      id: 'usr-1',
      email: 'menglang@test.com',
      fullName: 'Menglang Huo',
      superAdmin: true,
      organizations: [
        {
          orgId: 'org-1',
          name: 'Menglang_Org',
          slug: 'menglang-org',
          tier: 'ENTERPRISE',
          roles: ['ORG_ADMIN'],
          permissions: ['*'],
        },
        {
          orgId: 'org-2',
          name: 'Acme Corporation',
          slug: 'acme-corp',
          tier: 'PRO',
          roles: ['ORG_MEMBER'],
          permissions: [],
        },
      ],
    }
    authStore.currentOrgId = 'org-1'

    appStore.apps = [
      {
        id: 'app-1',
        organizationId: 'org-1',
        name: 'NIYEAY-HR',
        configuredProviders: ['FCM', 'APNS'],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
      {
        id: 'app-2',
        organizationId: 'org-1',
        name: 'CRM System',
        configuredProviders: ['WEBPUSH'],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    ]
    authStore.currentAppId = 'app-1'

    const wrapper = mount(OrgSwitcher, {
      global: {
        stubs: {
          ModernModal: true,
          BaseInput: true,
          BaseButton: true,
        },
      },
      attachTo: document.body,
    })

    return { wrapper, authStore, appStore }
  }

  it('renders current org and active app on the trigger button', () => {
    const { wrapper } = createTestWrapper()
    expect(wrapper.text()).toContain('Menglang_Org')
    expect(wrapper.text()).toContain('NIYEAY-HR')
  })

  it('opens popover when trigger button is clicked', async () => {
    const { wrapper } = createTestWrapper()
    const trigger = wrapper.find('button[aria-haspopup="true"]')
    await trigger.trigger('click')

    expect(wrapper.find('input[placeholder="Search apps or workspaces..."]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Apps')
    expect(wrapper.text()).toContain('Workspaces')
    expect(wrapper.text()).toContain('All')
    expect(wrapper.text()).toContain('NIYEAY-HR')
    expect(wrapper.text()).toContain('CRM System')
  })

  it('filters apps in real-time when searching', async () => {
    const { wrapper } = createTestWrapper()
    const trigger = wrapper.find('button[aria-haspopup="true"]')
    await trigger.trigger('click')

    const searchInput = wrapper.find('input[placeholder="Search apps or workspaces..."]')
    await searchInput.setValue('CRM')

    // Popover content checks
    const popover = wrapper.find('.max-h-72')
    expect(popover.text()).toContain('CRM System')
    expect(popover.text()).not.toContain('NIYEAY-HR')
  })

  it('switches between Apps and Workspaces tabs', async () => {
    const { wrapper } = createTestWrapper()
    const trigger = wrapper.find('button[aria-haspopup="true"]')
    await trigger.trigger('click')

    // Find tabs
    const buttons = wrapper.findAll('button')
    const workspacesTab = buttons.find((b) => b.text().includes('Workspaces'))
    expect(workspacesTab).toBeDefined()
    await workspacesTab!.trigger('click')

    // Should now show workspaces list
    expect(wrapper.text()).toContain('Acme Corporation')
    expect(wrapper.text()).toContain('@acme-corp')
  })

  it('navigates to /apps when Manage Apps is clicked', async () => {
    const { wrapper } = createTestWrapper()
    const trigger = wrapper.find('button[aria-haspopup="true"]')
    await trigger.trigger('click')

    const manageBtn = wrapper.findAll('button').find((b) => b.text().includes('Manage Apps'))
    expect(manageBtn).toBeDefined()
    await manageBtn!.trigger('click')

    expect(pushMock).toHaveBeenCalledWith('/apps')
  })

  it('closes dropdown on Escape key', async () => {
    const { wrapper } = createTestWrapper()
    const trigger = wrapper.find('button[aria-haspopup="true"]')
    await trigger.trigger('click')
    expect(wrapper.find('input[placeholder="Search apps or workspaces..."]').exists()).toBe(true)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()

    expect(wrapper.find('input[placeholder="Search apps or workspaces..."]').exists()).toBe(false)
  })

  it('synchronizes route parameters when selecting a different app on an app-scoped route', async () => {
    routeMock.params = { appId: 'app-1' }
    routeMock.name = 'Notifications'
    const { wrapper, authStore } = createTestWrapper()

    const trigger = wrapper.find('button[aria-haspopup="true"]')
    await trigger.trigger('click')

    const appRows = wrapper.findAll('.max-h-72 button')
    const crmBtn = appRows.find((b) => b.text().includes('CRM System'))
    expect(crmBtn).toBeDefined()
    await crmBtn!.trigger('click')

    expect(authStore.currentAppId).toBe('app-2')
    expect(pushMock).toHaveBeenCalledWith({
      name: 'Notifications',
      params: { appId: 'app-2' },
      query: {},
    })
  })
})
