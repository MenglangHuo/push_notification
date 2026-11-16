import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import RolesView from './RolesView.vue'
import { useAuthStore } from '@/stores/authStore'
import { rolesApi } from '@/api/roles'

// Mock rolesApi
vi.mock('@/api/roles', () => ({
  rolesApi: {
    listRoles: vi.fn(),
    listPermissions: vi.fn(),
    createCustomRole: vi.fn(),
  },
}))

describe('RolesView Component', () => {
  const mockRoles = [
    {
      id: 'role-1',
      name: 'Owner',
      description: 'Complete control over organization, apps, members and billing',
      system: true,
      permissions: [
        'org:read',
        'org:update',
        'org:delete',
        'notification:create',
        'notification:read',
        'apikey:create',
        'apikey:read',
      ],
    },
    {
      id: 'role-2',
      name: 'Developer',
      description: 'Technical access to apps, API keys, credentials, and test tools',
      system: true,
      permissions: [
        'notification:create',
        'notification:read',
        'apikey:read',
      ],
    },
  ]

  const mockPermissions = [
    { id: 'p-1', code: 'org:read', resource: 'org', action: 'read', description: 'View organization details' },
    { id: 'p-2', code: 'org:update', resource: 'org', action: 'update', description: 'Update organization settings' },
    { id: 'p-3', code: 'org:delete', resource: 'org', action: 'delete', description: 'Delete organization' },
    { id: 'p-4', code: 'notification:create', resource: 'notification', action: 'create', description: 'Compose and dispatch push notifications' },
    { id: 'p-5', code: 'notification:read', resource: 'notification', action: 'read', description: 'View notification history and delivery logs' },
    { id: 'p-6', code: 'apikey:create', resource: 'apikey', action: 'create', description: 'Create server API keys' },
    { id: 'p-7', code: 'apikey:read', resource: 'apikey', action: 'read', description: 'View API key metadata' },
  ]

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    vi.mocked(rolesApi.listRoles).mockResolvedValue([...mockRoles])
    vi.mocked(rolesApi.listPermissions).mockResolvedValue([...mockPermissions])
    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    })
  })

  async function createWrapper() {
    const authStore = useAuthStore()
    authStore.currentOrgId = 'org-1'

    const wrapper = mount(RolesView, {
      global: {
        stubs: {
          BaseButton: {
            template: '<button :disabled="loading" @click="$emit(\'click\', $event)"><slot /></button>',
            props: ['loading', 'variant', 'size', 'icon'],
          },
          BaseInput: true,
          ModernModal: {
            template: `
              <div v-if="modelValue" class="modern-modal-stub">
                <div class="modal-title">{{ title }}</div>
                <div class="modal-subtitle">{{ subtitle }}</div>
                <slot />
                <slot name="footer" />
              </div>
            `,
            props: ['modelValue', 'title', 'subtitle', 'icon', 'initialWidth', 'size'],
          },
        },
      },
    })

    await flushPromises()
    return { wrapper, authStore }
  }

  it('renders roles with clean cards, icons, and module prefix summaries', async () => {
    const { wrapper } = await createWrapper()

    expect(wrapper.text()).toContain('Roles & Permission Matrix')
    expect(wrapper.text()).toContain('Owner')
    expect(wrapper.text()).toContain('Developer')
    expect(wrapper.text()).toContain('System Role')

    // Module prefix badges
    expect(wrapper.text()).toContain('Push Notifications')
    expect(wrapper.text()).toContain('API Keys & Secrets')

    // View Permissions button
    const viewButtons = wrapper.findAll('button').filter((b) => b.text().includes('View Permissions Details'))
    expect(viewButtons.length).toBe(2)
  })

  it('opens details modal and groups permissions by prefix when View Permissions is clicked', async () => {
    const { wrapper } = await createWrapper()

    const devCard = wrapper.findAll('.group').find((c) => c.text().includes('Developer'))
    expect(devCard).toBeDefined()

    const viewBtn = devCard!.findAll('button').find((b) => b.text().includes('View Permissions Details'))
    expect(viewBtn).toBeDefined()
    await viewBtn!.trigger('click')
    await flushPromises()

    // Modal should now be open
    const modal = wrapper.find('.modern-modal-stub')
    expect(modal.exists()).toBe(true)
    expect(modal.text()).toContain('Developer Permissions Matrix')
    expect(modal.text()).toContain('prefix: notification')
    expect(modal.text()).toContain('prefix: apikey')
    expect(modal.text()).toContain('notification:create')
    expect(modal.text()).toContain('Compose and dispatch push notifications')
  })

  it('filters permissions in modal by search query', async () => {
    const { wrapper } = await createWrapper()

    const ownerCard = wrapper.findAll('.group').find((c) => c.text().includes('Owner'))
    const viewBtn = ownerCard!.findAll('button').find((b) => b.text().includes('View Permissions Details'))
    await viewBtn!.trigger('click')
    await flushPromises()

    const searchInput = wrapper.find('input[placeholder*="Filter permissions"]')
    expect(searchInput.exists()).toBe(true)

    await searchInput.setValue('dispatch')
    await flushPromises()

    const modal = wrapper.find('.modern-modal-stub')
    expect(modal.text()).toContain('notification:create')
    expect(modal.text()).not.toContain('org:delete')
  })

  it('filters permissions in modal when clicking prefix tabs', async () => {
    const { wrapper } = await createWrapper()

    const ownerCard = wrapper.findAll('.group').find((c) => c.text().includes('Owner'))
    const viewBtn = ownerCard!.findAll('button').find((b) => b.text().includes('View Permissions Details'))
    await viewBtn!.trigger('click')
    await flushPromises()

    const tabButtons = wrapper.findAll('.modern-modal-stub button').filter((b) => b.text().includes('API Keys & Secrets'))
    expect(tabButtons.length).toBeGreaterThan(0)
    await tabButtons[0].trigger('click')
    await flushPromises()

    const modal = wrapper.find('.modern-modal-stub')
    expect(modal.text()).toContain('apikey:create')
    expect(modal.text()).not.toContain('notification:create')
  })
})
