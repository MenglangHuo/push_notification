import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import NotificationComposerView from './NotificationComposerView.vue'
import PushDevicePreview from '@/components/notifications/PushDevicePreview.vue'
import { useAuthStore } from '@/stores/authStore'
import { useAppStore } from '@/stores/appStore'
import { notificationsApi } from '@/api/notifications'
import { segmentsApi } from '@/api/segments'

const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { appId: 'app-1' },
    query: {},
  }),
  useRouter: () => ({
    push: pushMock,
    back: vi.fn(),
  }),
}))

vi.mock('@/api/notifications', () => ({
  notificationsApi: {
    create: vi.fn(),
  },
}))

vi.mock('@/api/segments', () => ({
  segmentsApi: {
    list: vi.fn(),
  },
}))

describe('NotificationComposerView Component', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    vi.mocked(segmentsApi.list).mockResolvedValue([
      {
        id: 'seg-1',
        appId: 'app-1',
        name: 'VIP Users',
        filterAst: {} as any,
        active: true,
        estimatedCount: 120,
        createdAt: '',
        updatedAt: '',
      },
    ])
    vi.mocked(notificationsApi.create).mockResolvedValue({} as any)
  })

  async function createWrapper() {
    const authStore = useAuthStore()
    const appStore = useAppStore()
    authStore.currentAppId = 'app-1'
    authStore.currentOrgId = 'org-1'
    appStore.apps = [
      { id: 'app-1', organizationId: 'org-1', name: 'NIYEAY-HR', configuredProviders: ['FCM', 'APNS'], createdAt: '', updatedAt: '' },
    ]

    const wrapper = mount(NotificationComposerView, {
      global: {
        stubs: {
          DatePicker: {
            template: '<div class="date-picker-stub"><input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /></div>',
            props: ['modelValue', 'label', 'placeholder'],
          },
          ModernModal: {
            template: '<div v-if="modelValue" class="modal-stub"><slot /><slot name="footer" /></div>',
            props: ['modelValue', 'title', 'subtitle', 'icon'],
          },
        },
      },
    })

    await flushPromises()
    return { wrapper, authStore, appStore }
  }

  it('renders composer with custom cards, audience selectors, and device preview', async () => {
    const { wrapper } = await createWrapper()

    expect(wrapper.text()).toContain('Compose Push Notification')
    expect(wrapper.text()).toContain('NIYEAY-HR')
    expect(wrapper.text()).toContain('1. Target Audience')
    expect(wrapper.text()).toContain('2. Message Content')
    expect(wrapper.text()).toContain('3. Delivery Schedule')
    expect(wrapper.text()).toContain('4. Custom Data (Payload JSON)')
    expect(wrapper.text()).toContain('Device Live Preview')
  })

  it('applies quick campaign templates to inputs and updates live preview', async () => {
    const { wrapper } = await createWrapper()

    const saleButton = wrapper.findAll('button').find((b) => b.text().includes('⚡ Sale'))
    expect(saleButton).toBeDefined()
    await saleButton!.trigger('click')
    await flushPromises()

    expect((wrapper.find('input[placeholder*="Flash Sale"]').element as HTMLInputElement).value).toBe('Flash Sale Alert! ⚡')
    // Live preview should reflect this title
    expect(wrapper.text()).toContain('Flash Sale Alert! ⚡')
  })

  it('switches device preview platforms between Android, iOS, and Web', async () => {
    const { wrapper } = await createWrapper()

    const preview = wrapper.findComponent(PushDevicePreview)
    expect(preview.exists()).toBe(true)

    const iosTab = preview.findAll('button').find((b) => b.text().includes('iOS'))
    expect(iosTab).toBeDefined()
    await iosTab!.trigger('click')
    await flushPromises()

    // iOS Dynamic Island / lockscreen representation
    expect(preview.text()).toContain('Tuesday, November 12')

    const webTab = preview.findAll('button').find((b) => b.text().includes('Web Push'))
    expect(webTab).toBeDefined()
    await webTab!.trigger('click')
    await flushPromises()

    expect(preview.text()).toContain('Chrome Desktop • WebPush')
  })

  it('toggles delivery schedule and renders DatePicker', async () => {
    const { wrapper } = await createWrapper()

    const scheduleTile = wrapper.findAll('button').find((b) => b.text().includes('Schedule for Later'))
    expect(scheduleTile).toBeDefined()
    await scheduleTile!.trigger('click')
    await flushPromises()

    expect(wrapper.find('.date-picker-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('Timezone Delivery Strategy')
  })

  it('supports uploading image banner from device or PC', async () => {
    const { wrapper } = await createWrapper()

    const uploadBtn = wrapper.findAll('button').find((b) => b.text().includes('Upload from Device/PC'))
    expect(uploadBtn).toBeDefined()

    const fileInput = wrapper.find('input[type="file"]')
    expect(fileInput.exists()).toBe(true)

    // Simulate selecting an image file
    const file = new File(['fake image content'], 'promo-banner.png', { type: 'image/png' })
    const changeEvent = new Event('change')
    Object.defineProperty(fileInput.element, 'files', {
      value: [file],
      writable: true,
    })

    await fileInput.trigger('change')
    await flushPromises()
  })
})
