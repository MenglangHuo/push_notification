<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useAppStore } from '@/stores/appStore'
import { useToastStore } from '@/stores/toastStore'
import { notificationsApi } from '@/api/notifications'
import { segmentsApi, type Segment } from '@/api/segments'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import CustomSelect, { type SelectOption } from '@/components/common/CustomSelect.vue'
import DatePicker from '@/components/common/DatePicker.vue'
import ModernModal from '@/components/common/ModernModal.vue'
import PushDevicePreview from '@/components/notifications/PushDevicePreview.vue'
import {
  AlertCircle,
  ArrowLeft,
  BellRing,
  Calendar,
  Check,
  CheckCircle2,
  Clock,
  Code2,
  ExternalLink,
  Filter,
  Globe,
  Image as ImageIcon,
  Layers,
  Plus,
  Rocket,
  Send,
  Smartphone,
  Sparkles,
  Table,
  Trash2,
  Upload,
  Users,
  X,
  Zap,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()
const toastStore = useToastStore()

const appId = computed(() => {
  return (route.params.appId as string) || authStore.currentAppId || ''
})

const currentAppName = computed(() => {
  return appStore.currentApp?.name || 'Push Platform'
})

// Form state
const title = ref('')
const body = ref('')
const imageUrl = ref('')
const imageFileInput = ref<HTMLInputElement | null>(null)
const uploadedImageName = ref<string>('')
const isUploadingImage = ref<boolean>(false)

function triggerImageUpload() {
  imageFileInput.value?.click()
}

function handleImageFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    toastStore.warning('Invalid File', 'Please select a valid image file (PNG, JPG, WEBP, GIF).')
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    toastStore.warning('File Too Large', 'Please select an image smaller than 5MB.')
    return
  }

  isUploadingImage.value = true
  const reader = new FileReader()
  reader.onload = (e) => {
    imageUrl.value = (e.target?.result as string) || ''
    uploadedImageName.value = file.name
    isUploadingImage.value = false
    toastStore.success('Image Loaded', `Loaded "${file.name}" for banner.`)
  }
  reader.onerror = () => {
    isUploadingImage.value = false
    toastStore.error('Read Failed', 'Could not read image file.')
  }
  reader.readAsDataURL(file)
}

function clearUploadedImage() {
  imageUrl.value = ''
  uploadedImageName.value = ''
  if (imageFileInput.value) {
    imageFileInput.value.value = ''
  }
}

const deepLink = ref('')
const priority = ref<'HIGH' | 'NORMAL'>('HIGH')
const targetType = ref<'ALL' | 'SEGMENT' | 'DEVICE_IDS' | 'EXTERNAL_USER_IDS'>('ALL')
const targetInput = ref('')
const selectedSegmentId = ref('')
const availableSegments = ref<Segment[]>([])
const customDataPairs = ref<{ key: string; value: string }[]>([])
const customDataMode = ref<'rows' | 'json'>('rows')
const rawJsonInput = ref('')
const jsonError = ref('')

// Scheduling state
const deliveryOption = ref<'NOW' | 'SCHEDULED'>('NOW')
const scheduledDate = ref('')
const scheduledTime = ref('09:00')
const scheduleType = ref<'FIXED_TIME' | 'USER_TIMEZONE'>('FIXED_TIME')
const userTargetTime = ref('09:00')

// UI & Modal state
const isSubmitting = ref(false)
const errorMessage = ref('')
const showTestModal = ref(false)
const testToken = ref('')
const isSendingTest = ref(false)
const testResult = ref('')

const priorityOptions: SelectOption[] = [
  { label: 'High Priority (Immediate Wakeup)', value: 'HIGH', dotColor: 'bg-rose-500' },
  { label: 'Normal (Battery Optimized)', value: 'NORMAL', dotColor: 'bg-indigo-500' },
]

const timezoneModeOptions: SelectOption[] = [
  { label: 'Universal Fixed Time (UTC / App Timezone)', value: 'FIXED_TIME', dotColor: 'bg-indigo-500' },
  { label: 'Intelligent Delivery (User Local Timezone)', value: 'USER_TIMEZONE', dotColor: 'bg-emerald-500' },
]

const targetTypeOptions = [
  {
    id: 'ALL',
    label: 'Broadcast to All',
    description: 'Dispatch to all active subscribers across iOS, Android & WebPush',
    icon: Globe,
    accent: 'text-indigo-500',
    border: 'border-indigo-500/30',
    bg: 'bg-indigo-50/40 dark:bg-indigo-950/20',
  },
  {
    id: 'SEGMENT',
    label: 'Audience Segment',
    description: 'Target dynamic subscriber groups based on activity and filters',
    icon: Filter,
    accent: 'text-emerald-500',
    border: 'border-emerald-500/30',
    bg: 'bg-emerald-50/40 dark:bg-emerald-950/20',
  },
  {
    id: 'DEVICE_IDS',
    label: 'Device Tokens',
    description: 'Direct push to specific hardware device tokens',
    icon: Smartphone,
    accent: 'text-amber-500',
    border: 'border-amber-500/30',
    bg: 'bg-amber-50/40 dark:bg-amber-950/20',
  },
  {
    id: 'EXTERNAL_USER_IDS',
    label: 'Specific Users',
    description: 'Target by internal application account / user IDs',
    icon: Users,
    accent: 'text-sky-500',
    border: 'border-sky-500/30',
    bg: 'bg-sky-50/40 dark:bg-sky-950/20',
  },
] as const

const segmentSelectOptions = computed<SelectOption[]>(() => {
  return availableSegments.value.map((s) => ({
    label: `${s.name} (~${s.estimatedCount ?? 0} active subscribers)`,
    value: s.id,
  }))
})

const selectedSegmentDetails = computed(() => {
  return availableSegments.value.find((s) => s.id === selectedSegmentId.value)
})

const parsedTokenCount = computed(() => {
  if (!targetInput.value.trim()) return 0
  return targetInput.value.split('\n').map((s) => s.trim()).filter(Boolean).length
})

// Quick Templates
function applyTemplate(type: 'sale' | 'welcome' | 'order' | 'system') {
  if (type === 'sale') {
    title.value = 'Flash Sale Alert! ⚡'
    body.value = 'Exclusive 30% discount on all items today only. Tap to claim before midnight!'
    imageUrl.value = 'https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?w=600&auto=format&fit=crop&q=80'
    deepLink.value = 'myapp://shop/flash-sale'
    priority.value = 'HIGH'
  } else if (type === 'welcome') {
    title.value = 'Welcome to the Platform! 🎉'
    body.value = 'Your workspace is all set up. Explore features and configure your first push campaign.'
    deepLink.value = 'myapp://home'
    priority.value = 'NORMAL'
  } else if (type === 'order') {
    title.value = 'Your Order Has Shipped! 📦'
    body.value = 'Order #89214 is on the way. Tap to track real-time delivery status.'
    imageUrl.value = 'https://images.unsplash.com/photo-1549465220-1a8b9238cd48?w=600&auto=format&fit=crop&q=80'
    deepLink.value = 'myapp://orders/89214'
    priority.value = 'HIGH'
  } else if (type === 'system') {
    title.value = 'Scheduled Maintenance ⚠️'
    body.value = 'Our delivery servers will undergo a brief 10-minute update tonight at 2:00 AM UTC.'
    priority.value = 'NORMAL'
  }
}

function insertEmoji(emoji: string) {
  title.value = (title.value + ' ' + emoji).trim()
}

// Custom Data helpers
function addCustomDataPair() {
  customDataPairs.value.push({ key: '', value: '' })
}

function removeCustomDataPair(index: number) {
  customDataPairs.value.splice(index, 1)
}

function addPresetDataKey(key: string, defaultValue: string = '') {
  const exists = customDataPairs.value.find((p) => p.key === key)
  if (!exists) {
    customDataPairs.value.push({ key, value: defaultValue })
  }
}

function toggleCustomDataMode(mode: 'rows' | 'json') {
  if (mode === 'json') {
    const obj: Record<string, string> = {}
    customDataPairs.value.forEach((p) => {
      if (p.key.trim()) obj[p.key.trim()] = p.value
    })
    rawJsonInput.value = Object.keys(obj).length > 0 ? JSON.stringify(obj, null, 2) : ''
    jsonError.value = ''
  } else {
    if (rawJsonInput.value.trim()) {
      try {
        const parsed = JSON.parse(rawJsonInput.value)
        if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
          customDataPairs.value = Object.entries(parsed).map(([key, val]) => ({
            key,
            value: typeof val === 'object' ? JSON.stringify(val) : String(val),
          }))
          jsonError.value = ''
        } else {
          jsonError.value = 'JSON must be a key-value object'
          return
        }
      } catch (err: any) {
        jsonError.value = 'Invalid JSON: ' + err.message
        return
      }
    }
  }
  customDataMode.value = mode
}

// Schedule Presets
function setSchedulePreset(preset: '15m' | '1h' | 'tomorrow' | 'friday') {
  deliveryOption.value = 'SCHEDULED'
  const now = new Date()
  if (preset === '15m') {
    now.setMinutes(now.getMinutes() + 15)
  } else if (preset === '1h') {
    now.setHours(now.getHours() + 1)
  } else if (preset === 'tomorrow') {
    now.setDate(now.getDate() + 1)
    now.setHours(9, 0, 0, 0)
  } else if (preset === 'friday') {
    const day = now.getDay()
    const diff = (5 - day + 7) % 7 || 7
    now.setDate(now.getDate() + diff)
    now.setHours(17, 0, 0, 0)
  }

  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  scheduledDate.value = `${y}-${m}-${d}`
  scheduledTime.value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

async function loadSegments() {
  if (!appId.value) return
  try {
    const list = await segmentsApi.list(appId.value)
    availableSegments.value = list
    if (route.query.target === 'SEGMENT' && route.query.segmentId) {
      targetType.value = 'SEGMENT'
      selectedSegmentId.value = route.query.segmentId as string
    } else if (list.length > 0 && !selectedSegmentId.value) {
      selectedSegmentId.value = list[0].id
    }
  } catch (err: any) {
    console.error('Failed to load segments:', err)
  }
}

async function handleSendTestPush() {
  if (!appId.value || !testToken.value.trim()) {
    toastStore.error('Token Missing', 'Please enter a test device hardware token.')
    return
  }

  isSendingTest.value = true
  testResult.value = ''
  try {
    await notificationsApi.create(appId.value, {
      title: title.value.trim() || 'Test Push Notification',
      body: body.value.trim() || 'This is a test notification dispatched from the composer.',
      targetType: 'DEVICE_IDS',
      targetPayload: { deviceIds: [testToken.value.trim()] },
      priority: 'HIGH',
      imageUrl: imageUrl.value.trim() || undefined,
      deepLink: deepLink.value.trim() || undefined,
    })
    testResult.value = 'Success! Test push notification successfully dispatched to gateway.'
    toastStore.success('Test Push Sent', 'Delivered to gateway test target.')
  } catch (err: any) {
    testResult.value = err.response?.data?.detail || err.message || 'Failed to dispatch test push'
    toastStore.error('Test Push Failed', testResult.value)
  } finally {
    isSendingTest.value = false
  }
}

async function handleSubmit() {
  if (!appId.value) {
    toastStore.error('Context Missing', 'Please select an application context first.')
    return
  }

  if (!title.value.trim() || !body.value.trim()) {
    errorMessage.value = 'Message Title and Body are required.'
    return
  }

  if (targetType.value === 'SEGMENT' && !selectedSegmentId.value) {
    errorMessage.value = 'Please select a target audience segment.'
    return
  }

  if (deliveryOption.value === 'SCHEDULED' && (!scheduledDate.value || !scheduledTime.value)) {
    errorMessage.value = 'Please select a scheduled launch date and time.'
    return
  }

  isSubmitting.value = true
  errorMessage.value = ''

  try {
    const dataObj: Record<string, string> = {}
    if (customDataMode.value === 'json') {
      if (rawJsonInput.value.trim()) {
        try {
          const parsed = JSON.parse(rawJsonInput.value)
          Object.assign(dataObj, parsed)
        } catch {
          errorMessage.value = 'Invalid JSON in custom data editor.'
          isSubmitting.value = false
          return
        }
      }
    } else {
      customDataPairs.value.forEach((p) => {
        if (p.key.trim()) dataObj[p.key.trim()] = p.value
      })
    }

    const targetPayload: Record<string, unknown> = {}
    if (targetType.value === 'SEGMENT') {
      targetPayload.segmentId = selectedSegmentId.value
    } else if (targetType.value === 'DEVICE_IDS') {
      targetPayload.deviceIds = targetInput.value
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean)
    } else if (targetType.value === 'EXTERNAL_USER_IDS') {
      targetPayload.externalUserIds = targetInput.value
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean)
    }

    const idempotency =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `idemp_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`

    const scheduledAt =
      deliveryOption.value === 'SCHEDULED' && scheduledDate.value && scheduledTime.value
        ? new Date(`${scheduledDate.value}T${scheduledTime.value}:00`).toISOString()
        : null

    const payload = {
      appId: appId.value,
      title: title.value.trim(),
      body: body.value.trim(),
      idempotency,
      idempotencyKey: idempotency,
      imageUrl: imageUrl.value.trim() || undefined,
      deepLink: deepLink.value.trim() || undefined,
      priority: priority.value,
      targetType: targetType.value,
      customData: Object.keys(dataObj).length > 0 ? dataObj : {},
      targetPayload: Object.keys(targetPayload).length > 0 ? targetPayload : {},
      scheduledAt,
      scheduleType: deliveryOption.value === 'SCHEDULED' ? scheduleType.value : null,
      userTargetTime:
        deliveryOption.value === 'SCHEDULED' && scheduleType.value === 'USER_TIMEZONE'
          ? userTargetTime.value
          : null,
    }

    await notificationsApi.create(appId.value, payload, idempotency)

    toastStore.success(
      'Push Dispatched Successfully',
      `Notification "${title.value}" sent to delivery gateway.`,
    )
    router.push(appId.value ? `/apps/${appId.value}/notifications` : '/notifications')
  } catch (err: any) {
    errorMessage.value = err.response?.data?.detail || err.message || 'Failed to dispatch notification.'
    toastStore.error('Dispatch Failed', errorMessage.value)
  } finally {
    isSubmitting.value = false
  }
}

watch(appId, () => {
  loadSegments()
})

onMounted(() => {
  loadSegments()
})
</script>

<template>
  <div class="flex flex-col flex-1 min-h-0 text-left lg:h-[calc(100vh-175px)]">
    <!-- Header -->
    <div class="shrink-0 flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-200/90 dark:border-slate-800">
      <div class="flex items-center gap-3">
        <button
          type="button"
          @click="router.back()"
          class="p-2 rounded-xl text-slate-400 hover:text-slate-700 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition cursor-pointer"
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white">
              Compose Push Notification
            </h1>
            <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
              {{ currentAppName }}
            </span>
          </div>
          <p class="text-xs text-slate-500 dark:text-slate-400">
            Target devices, customize rich media and interactive payloads, with real-time multi-platform preview
          </p>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <BaseButton
          variant="outline"
          size="sm"
          :icon="Smartphone"
          @click="showTestModal = true"
        >
          Send Test
        </BaseButton>

        <BaseButton
          variant="ghost"
          size="sm"
          @click="router.back()"
        >
          Cancel
        </BaseButton>

        <BaseButton
          variant="primary"
          size="sm"
          :icon="Send"
          :loading="isSubmitting"
          @click="handleSubmit"
        >
          Dispatch Push
        </BaseButton>
      </div>
    </div>

    <!-- Error Banner -->
    <div
      v-if="errorMessage"
      class="shrink-0 mt-3 p-3.5 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2.5 animate-in fade-in"
    >
      <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
      <span class="font-medium">{{ errorMessage }}</span>
    </div>

    <!-- 2 Column Composer Grid (2-Side Split) -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 flex-1 min-h-0 pt-4 items-start">
      <!-- Left Column: Form Controls (7 cols, independent scroll-Y) -->
      <div class="lg:col-span-7 flex flex-col min-h-0 h-full">
        <div class="flex-1 overflow-y-auto pr-1.5 space-y-5 custom-scroll pb-6">
          <!-- ========================================================================= -->
          <!-- 1. AUDIENCE & TARGETING CARD                                              -->
          <!-- ========================================================================= -->
          <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200/90 dark:border-slate-800 shadow-xs space-y-4">
            <div class="flex items-center justify-between pb-2.5 border-b border-slate-100 dark:border-slate-800">
              <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-2">
                <Filter class="w-3.5 h-3.5 text-indigo-500" />
                <span>1. Target Audience</span>
              </h3>
              <span class="text-[11px] font-semibold text-indigo-600 dark:text-indigo-400">
                {{ targetType === 'ALL' ? 'Broadcast Mode' : targetType }}
              </span>
            </div>

            <!-- Segmented Target Cards -->
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              <button
                v-for="item in targetTypeOptions"
                :key="item.id"
                type="button"
                @click="targetType = item.id"
                class="p-3 rounded-xl border text-left transition-all duration-150 cursor-pointer flex items-start gap-2.5"
                :class="[
                  targetType === item.id
                    ? 'border-indigo-500 bg-indigo-50/60 dark:bg-indigo-950/40 shadow-2xs ring-1 ring-indigo-500/20'
                    : 'border-slate-200/90 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 bg-white dark:bg-slate-900',
                ]"
              >
                <div
                  class="w-7 h-7 rounded-lg flex items-center justify-center shrink-0 border mt-0.5"
                  :class="[targetType === item.id ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-slate-100 dark:bg-slate-800 text-slate-500 border-slate-200 dark:border-slate-700']"
                >
                  <component :is="item.icon" class="w-3.5 h-3.5" />
                </div>
                <div class="min-w-0 flex-1">
                  <div class="text-xs font-bold text-slate-900 dark:text-white flex items-center justify-between">
                    <span>{{ item.label }}</span>
                    <Check v-if="targetType === item.id" class="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400" />
                  </div>
                  <p class="text-[10px] text-slate-500 dark:text-slate-400 mt-0.5 leading-snug">
                    {{ item.description }}
                  </p>
                </div>
              </button>
            </div>

            <!-- Segment Picker if SEGMENT -->
            <div v-if="targetType === 'SEGMENT'" class="pt-1 animate-in fade-in space-y-2">
              <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                Select Audience Segment
              </label>
              <CustomSelect
                v-if="segmentSelectOptions.length > 0"
                v-model="selectedSegmentId"
                :options="segmentSelectOptions"
                size="md"
              />
              <div v-else class="p-3 rounded-xl bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-800/40 text-xs text-amber-700 dark:text-amber-400">
                No audience segments configured yet. You can build segments in the Segments tab.
              </div>

              <div v-if="selectedSegmentDetails" class="p-2.5 rounded-lg bg-slate-50 dark:bg-slate-950/40 border border-slate-200/80 dark:border-slate-800 text-[11px] text-slate-500 flex items-center justify-between">
                <span>Estimated Audience Reach:</span>
                <span class="font-bold text-slate-900 dark:text-white font-mono">
                  ~{{ selectedSegmentDetails.estimatedCount ?? 0 }} devices
                </span>
              </div>
            </div>

            <!-- Direct Input if Tokens or User IDs -->
            <div v-if="targetType === 'DEVICE_IDS' || targetType === 'EXTERNAL_USER_IDS'" class="pt-1 animate-in fade-in space-y-1.5">
              <div class="flex items-center justify-between">
                <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  {{ targetType === 'DEVICE_IDS' ? 'Device Tokens (one per line)' : 'User Account IDs (one per line)' }}
                </label>
                <span class="text-[10px] font-mono text-indigo-600 dark:text-indigo-400 font-semibold">
                  {{ parsedTokenCount }} entries detected
                </span>
              </div>
              <textarea
                v-model="targetInput"
                rows="3"
                :placeholder="targetType === 'DEVICE_IDS' ? 'fcm_token_xyz123\napns_token_abc456\nwebpush_endpoint_789' : 'user_usr_90218\nuser_usr_10293'"
                class="w-full bg-slate-50/70 dark:bg-slate-950/70 text-slate-900 dark:text-white font-mono p-3 rounded-xl border border-slate-200 dark:border-slate-800 text-xs focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/10 shadow-2xs"
              ></textarea>
            </div>
          </div>

          <!-- ========================================================================= -->
          <!-- 2. MESSAGE CONTENT CARD                                                   -->
          <!-- ========================================================================= -->
          <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200/90 dark:border-slate-800 shadow-xs space-y-4">
            <div class="flex items-center justify-between pb-2.5 border-b border-slate-100 dark:border-slate-800">
              <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-2">
                <BellRing class="w-3.5 h-3.5 text-indigo-500" />
                <span>2. Message Content</span>
              </h3>

              <!-- Quick Template Shortcuts -->
              <div class="flex items-center gap-1.5">
                <span class="text-[10px] text-slate-400 font-medium">Templates:</span>
                <button
                  type="button"
                  @click="applyTemplate('sale')"
                  class="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 text-slate-700 dark:text-slate-300 transition cursor-pointer"
                >
                  ⚡ Sale
                </button>
                <button
                  type="button"
                  @click="applyTemplate('welcome')"
                  class="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 text-slate-700 dark:text-slate-300 transition cursor-pointer"
                >
                  🎉 Welcome
                </button>
                <button
                  type="button"
                  @click="applyTemplate('order')"
                  class="px-2 py-0.5 rounded-md text-[10px] font-semibold bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 text-slate-700 dark:text-slate-300 transition cursor-pointer"
                >
                  📦 Order
                </button>
              </div>
            </div>

            <!-- Notification Title with Character Counter & Emoji Picker -->
            <div class="space-y-1.5">
              <BaseInput
                v-model="title"
                label="Notification Title"
                placeholder="e.g. Flash Sale Alert! ⚡"
                required
                size="md"
              >
                <template #label-right>
                  <div class="flex items-center gap-1.5">
                    <span class="text-[10px] text-slate-400">Quick Emoji:</span>
                    <div class="flex items-center gap-1">
                      <button
                        v-for="e in ['⚡', '🔥', '🚀', '🔔', '🎉', '🎁']"
                        :key="e"
                        type="button"
                        @click="insertEmoji(e)"
                        class="text-xs hover:scale-125 transition-transform cursor-pointer"
                      >
                        {{ e }}
                      </button>
                    </div>
                    <span class="text-[10px] font-mono font-semibold ml-1.5" :class="title.length > 50 ? 'text-rose-500' : 'text-slate-400'">
                      {{ title.length }}/50
                    </span>
                  </div>
                </template>
              </BaseInput>
            </div>

            <!-- Message Body with Character Counter -->
            <div class="space-y-1.5">
              <div class="flex items-center justify-between">
                <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Message Body <span class="text-rose-500 font-bold">*</span>
                </label>
                <span class="text-[10px] font-mono font-semibold" :class="body.length > 150 ? 'text-rose-500' : 'text-slate-400'">
                  {{ body.length }}/150
                </span>
              </div>
              <textarea
                v-model="body"
                rows="3"
                placeholder="Write the notification message content..."
                class="w-full bg-slate-50/70 dark:bg-slate-950/70 hover:bg-white dark:hover:bg-slate-900 focus:bg-white dark:focus:bg-slate-900 text-slate-900 dark:text-white p-3 rounded-xl border border-slate-200 dark:border-slate-800 text-xs focus:outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 shadow-2xs transition-all"
              ></textarea>
            </div>

            <!-- Rich Media: Image Banner (URL or Upload from Device/PC) & Deep Link -->
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1 items-start">
              <!-- Image Banner URL or Local Device Upload -->
              <div class="space-y-1.5">
                <div class="flex items-center justify-between">
                  <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                    Image Banner URL (Optional)
                  </label>
                  <div class="flex items-center gap-1.5">
                    <!-- Hidden File Input for Device/PC upload -->
                    <input
                      ref="imageFileInput"
                      type="file"
                      accept="image/png,image/jpeg,image/jpg,image/webp,image/gif"
                      class="hidden"
                      @change="handleImageFileChange"
                    />
                    <!-- Upload trigger button -->
                    <button
                      type="button"
                      @click="triggerImageUpload"
                      class="text-[11px] font-semibold text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 flex items-center gap-1 cursor-pointer transition"
                    >
                      <Upload class="w-3 h-3" />
                      <span>{{ uploadedImageName ? 'Change File' : 'Upload from Device/PC' }}</span>
                    </button>
                    <!-- Clear button if image exists -->
                    <button
                      v-if="imageUrl"
                      type="button"
                      @click="clearUploadedImage"
                      class="text-[11px] text-slate-400 hover:text-rose-500 flex items-center gap-0.5 cursor-pointer transition ml-1"
                      title="Clear image"
                    >
                      <X class="w-3 h-3" />
                      <span>Clear</span>
                    </button>
                  </div>
                </div>

                <!-- If file uploaded from device/PC, display preview chip -->
                <div
                  v-if="uploadedImageName && imageUrl.startsWith('data:')"
                  class="flex items-center justify-between p-2 rounded-xl bg-indigo-50/80 dark:bg-indigo-950/40 border border-indigo-200 dark:border-indigo-800 text-xs shadow-2xs"
                >
                  <div class="flex items-center gap-2 min-w-0">
                    <img :src="imageUrl" class="w-8 h-8 rounded-lg object-cover border border-indigo-200 dark:border-indigo-700 shadow-2xs shrink-0" />
                    <div class="min-w-0">
                      <div class="font-bold text-[11px] text-indigo-950 dark:text-indigo-200 truncate">
                        {{ uploadedImageName }}
                      </div>
                      <div class="text-[9px] text-emerald-600 dark:text-emerald-400 font-medium">
                        ✓ Loaded from device / PC
                      </div>
                    </div>
                  </div>
                  <button
                    type="button"
                    @click="clearUploadedImage"
                    class="p-1 rounded-lg text-slate-400 hover:text-rose-500 hover:bg-white dark:hover:bg-slate-800 transition cursor-pointer"
                    title="Remove image"
                  >
                    <X class="w-3.5 h-3.5" />
                  </button>
                </div>

                <!-- Standard Input for direct URL -->
                <BaseInput
                  v-else
                  v-model="imageUrl"
                  placeholder="https://example.com/banner.jpg or click Upload above"
                  :icon="ImageIcon"
                  size="sm"
                />
              </div>

              <!-- Deep Link / Action URL -->
              <div class="space-y-1.5">
                <BaseInput
                  v-model="deepLink"
                  label="Deep Link / Action URL"
                  placeholder="myapp://promo/winter"
                  :icon="ExternalLink"
                  size="sm"
                />
              </div>
            </div>

            <!-- Priority Level -->
            <div class="pt-1">
              <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
                Priority Level
              </label>
              <CustomSelect
                v-model="priority"
                :options="priorityOptions"
                size="sm"
              />
            </div>
          </div>

          <!-- ========================================================================= -->
          <!-- 3. DELIVERY SCHEDULE (CUSTOM DATEPICKER & TIMEZONE) - MOVED ABOVE CUSTOM DATA -->
          <!-- ========================================================================= -->
          <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200/90 dark:border-slate-800 shadow-xs space-y-4">
            <div class="flex items-center justify-between pb-2.5 border-b border-slate-100 dark:border-slate-800">
              <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-2">
                <Clock class="w-3.5 h-3.5 text-indigo-500" />
                <span>3. Delivery Schedule</span>
              </h3>
              <span class="text-[11px] font-semibold text-slate-500 dark:text-slate-400">
                {{ deliveryOption === 'NOW' ? 'Instant Delivery' : 'Automated Queue' }}
              </span>
            </div>

            <!-- Delivery Mode Tiles -->
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <button
                type="button"
                @click="deliveryOption = 'NOW'"
                class="p-3.5 rounded-xl border text-left transition-all duration-150 cursor-pointer flex items-start gap-3"
                :class="[
                  deliveryOption === 'NOW'
                    ? 'border-indigo-500 bg-indigo-50/60 dark:bg-indigo-950/40 shadow-2xs ring-1 ring-indigo-500/20'
                    : 'border-slate-200/90 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 bg-white dark:bg-slate-900',
                ]"
              >
                <div
                  class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0 border"
                  :class="deliveryOption === 'NOW' ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-slate-100 dark:bg-slate-800 text-slate-500 border-slate-200 dark:border-slate-700'"
                >
                  <Zap class="w-4 h-4" />
                </div>
                <div class="min-w-0 flex-1">
                  <div class="text-xs font-bold text-slate-900 dark:text-white flex items-center justify-between">
                    <span>Send Immediately</span>
                    <Check v-if="deliveryOption === 'NOW'" class="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400" />
                  </div>
                  <p class="text-[10px] text-slate-500 dark:text-slate-400 mt-0.5 leading-snug">
                    Dispatches immediately via FCM, APNs & Web gateways upon confirmation
                  </p>
                </div>
              </button>

              <button
                type="button"
                @click="deliveryOption = 'SCHEDULED'"
                class="p-3.5 rounded-xl border text-left transition-all duration-150 cursor-pointer flex items-start gap-3"
                :class="[
                  deliveryOption === 'SCHEDULED'
                    ? 'border-indigo-500 bg-indigo-50/60 dark:bg-indigo-950/40 shadow-2xs ring-1 ring-indigo-500/20'
                    : 'border-slate-200/90 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 bg-white dark:bg-slate-900',
                ]"
              >
                <div
                  class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0 border"
                  :class="deliveryOption === 'SCHEDULED' ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-slate-100 dark:bg-slate-800 text-slate-500 border-slate-200 dark:border-slate-700'"
                >
                  <Calendar class="w-4 h-4" />
                </div>
                <div class="min-w-0 flex-1">
                  <div class="text-xs font-bold text-slate-900 dark:text-white flex items-center justify-between">
                    <span>Schedule for Later</span>
                    <Check v-if="deliveryOption === 'SCHEDULED'" class="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400" />
                  </div>
                  <p class="text-[10px] text-slate-500 dark:text-slate-400 mt-0.5 leading-snug">
                    Queue push notification for a specific future date and time
                  </p>
                </div>
              </button>
            </div>

            <!-- Scheduled Options Panel -->
            <div v-if="deliveryOption === 'SCHEDULED'" class="pt-2 animate-in fade-in space-y-4">
              <!-- Quick Preset Buttons -->
              <div class="flex items-center gap-1.5 flex-wrap text-[10px]">
                <span class="text-slate-400">Quick Presets:</span>
                <button
                  type="button"
                  @click="setSchedulePreset('15m')"
                  class="px-2 py-0.5 rounded-md font-semibold bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 text-slate-700 dark:text-slate-300 transition cursor-pointer"
                >
                  In 15 Mins
                </button>
                <button
                  type="button"
                  @click="setSchedulePreset('1h')"
                  class="px-2 py-0.5 rounded-md font-semibold bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 text-slate-700 dark:text-slate-300 transition cursor-pointer"
                >
                  In 1 Hour
                </button>
                <button
                  type="button"
                  @click="setSchedulePreset('tomorrow')"
                  class="px-2 py-0.5 rounded-md font-semibold bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 text-slate-700 dark:text-slate-300 transition cursor-pointer"
                >
                  Tomorrow 9:00 AM
                </button>
                <button
                  type="button"
                  @click="setSchedulePreset('friday')"
                  class="px-2 py-0.5 rounded-md font-semibold bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 text-slate-700 dark:text-slate-300 transition cursor-pointer"
                >
                  Friday 5:00 PM
                </button>
              </div>

              <!-- Custom DatePicker Component & Time Input -->
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 items-end">
                <DatePicker
                  v-model="scheduledDate"
                  label="Launch Date"
                  placeholder="Choose calendar date..."
                />

                <div class="space-y-1.5">
                  <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                    Launch Time (HH:MM)
                  </label>
                  <input
                    v-model="scheduledTime"
                    type="time"
                    class="w-full h-10 bg-slate-50/70 dark:bg-slate-950/70 hover:bg-white dark:hover:bg-slate-900 focus:bg-white dark:focus:bg-slate-900 text-slate-900 dark:text-white px-3.5 rounded-xl border border-slate-200 dark:border-slate-800 text-xs focus:outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 shadow-2xs transition-all"
                  />
                </div>
              </div>

              <!-- Timezone Delivery Strategy -->
              <div class="space-y-1.5 pt-1">
                <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Timezone Delivery Strategy
                </label>
                <CustomSelect
                  v-model="scheduleType"
                  :options="timezoneModeOptions"
                  size="sm"
                />
                <p class="text-[10px] text-slate-400 leading-snug">
                  {{ scheduleType === 'FIXED_TIME' ? 'Delivers universally at the exact same moment across all worldwide subscribers.' : 'Delivers when the clock reaches your target time in each recipient\'s local timezone.' }}
                </p>
              </div>
            </div>
          </div>

          <!-- ========================================================================= -->
          <!-- 4. CUSTOM DATA (PAYLOAD JSON)                                             -->
          <!-- ========================================================================= -->
          <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200/90 dark:border-slate-800 shadow-xs space-y-3.5">
            <div class="flex items-center justify-between pb-2.5 border-b border-slate-100 dark:border-slate-800">
              <div class="flex items-center gap-2">
                <Zap class="w-3.5 h-3.5 text-indigo-500" />
                <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white">
                  4. Custom Data (Payload JSON)
                </h3>
              </div>

              <!-- Mode Switcher -->
              <div class="flex items-center gap-2">
                <div class="flex items-center p-0.5 rounded-lg bg-slate-100 dark:bg-slate-800 text-[10px] font-semibold border border-slate-200/80 dark:border-slate-700/60">
                  <button
                    type="button"
                    @click="toggleCustomDataMode('rows')"
                    class="px-2 py-0.5 rounded transition cursor-pointer"
                    :class="customDataMode === 'rows' ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-300 shadow-2xs' : 'text-slate-500'"
                  >
                    Key-Values
                  </button>
                  <button
                    type="button"
                    @click="toggleCustomDataMode('json')"
                    class="px-2 py-0.5 rounded transition cursor-pointer"
                    :class="customDataMode === 'json' ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-indigo-300 shadow-2xs' : 'text-slate-500'"
                  >
                    Raw JSON
                  </button>
                </div>

                <BaseButton
                  v-if="customDataMode === 'rows'"
                  variant="ghost"
                  size="xs"
                  :icon="Plus"
                  @click="addCustomDataPair"
                >
                  Add Field
                </BaseButton>
              </div>
            </div>

            <!-- Quick Presets -->
            <div class="flex items-center gap-1.5 flex-wrap text-[10px]">
              <span class="text-slate-400">Presets:</span>
              <button
                v-for="preset in ['deep_link', 'category', 'screen', 'order_id', 'tracking_id']"
                :key="preset"
                type="button"
                @click="addPresetDataKey(preset)"
                class="px-2 py-0.5 rounded-md font-mono bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 text-slate-600 dark:text-slate-300 transition cursor-pointer"
              >
                + {{ preset }}
              </button>
            </div>

            <!-- Key-Values Mode -->
            <div v-if="customDataMode === 'rows'" class="space-y-2">
              <div
                v-if="customDataPairs.length === 0"
                class="p-4 rounded-xl border border-dashed border-slate-200 dark:border-slate-800 text-center text-xs text-slate-400 space-y-1"
              >
                <p>No custom payload fields added yet.</p>
                <button
                  type="button"
                  @click="addCustomDataPair"
                  class="text-indigo-600 dark:text-indigo-400 font-semibold hover:underline cursor-pointer"
                >
                  + Add your first key-value parameter
                </button>
              </div>

              <div
                v-for="(pair, idx) in customDataPairs"
                :key="idx"
                class="flex items-center gap-2 animate-in fade-in"
              >
                <BaseInput
                  v-model="pair.key"
                  placeholder="Key (e.g. orderId)"
                  size="sm"
                  class="flex-1"
                />
                <BaseInput
                  v-model="pair.value"
                  placeholder="Value (e.g. 109283)"
                  size="sm"
                  class="flex-1"
                />
                <button
                  type="button"
                  @click="removeCustomDataPair(idx)"
                  class="p-2 text-slate-400 hover:text-rose-500 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 transition cursor-pointer"
                  title="Remove parameter"
                >
                  <Trash2 class="w-4 h-4" />
                </button>
              </div>
            </div>

            <!-- Raw JSON Mode -->
            <div v-else class="space-y-2">
              <textarea
                v-model="rawJsonInput"
                rows="5"
                placeholder='{\n  "orderId": "109283",\n  "category": "promo"\n}'
                class="w-full bg-slate-950 text-indigo-300 font-mono p-3 rounded-xl border border-slate-800 text-xs focus:outline-none focus:border-indigo-500"
              ></textarea>
              <div v-if="jsonError" class="text-xs text-rose-500 font-medium">
                {{ jsonError }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column: Interactive Realtime Device Preview (5 cols, fixed without scrolling) -->
      <div class="lg:col-span-5 flex flex-col min-h-0 h-full overflow-hidden">
        <PushDevicePreview
          :title="title"
          :body="body"
          :imageUrl="imageUrl"
          :deepLink="deepLink"
          :appName="currentAppName"
          :priority="priority"
        />
      </div>
    </div>

    <!-- ========================================================================= -->
    <!-- SEND LIVE TEST PUSH MODAL                                                 -->
    <!-- ========================================================================= -->
    <ModernModal
      v-model="showTestModal"
      title="Send Live Test Push"
      subtitle="Send a real-time push message to verify device registration and gateway credentials"
      :icon="Smartphone"
      :initial-width="580"
      @close="showTestModal = false"
    >
      <div class="p-6 space-y-4 text-left">
        <div
          v-if="testResult"
          class="p-3.5 rounded-xl border text-xs flex items-start gap-2.5"
          :class="testResult.startsWith('Success') ? 'bg-emerald-50 dark:bg-emerald-950/40 border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-300' : 'bg-rose-50 dark:bg-rose-950/40 border-rose-200 dark:border-rose-900 text-rose-800 dark:text-rose-300'"
        >
          <CheckCircle2 v-if="testResult.startsWith('Success')" class="w-4 h-4 shrink-0 text-emerald-600" />
          <AlertCircle v-else class="w-4 h-4 shrink-0 text-rose-600" />
          <span>{{ testResult }}</span>
        </div>

        <div class="space-y-1.5">
          <label class="block text-xs font-semibold text-slate-700 dark:text-slate-300">
            Target Test Device Token <span class="text-rose-500 font-bold">*</span>
          </label>
          <input
            v-model="testToken"
            type="text"
            placeholder="Paste FCM, APNs, or WebPush token..."
            class="w-full h-10 bg-slate-50/70 dark:bg-slate-950/70 font-mono text-xs px-3.5 rounded-xl border border-slate-200 dark:border-slate-800 text-slate-900 dark:text-white focus:outline-none focus:border-indigo-500"
          />
          <p class="text-[10px] text-slate-400">
            You can copy active tokens from the Registered Devices page.
          </p>
        </div>

        <div class="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/50 border border-slate-200/80 dark:border-slate-800 text-xs space-y-1.5">
          <div class="font-semibold text-slate-700 dark:text-slate-300 flex items-center justify-between">
            <span>Message to Send:</span>
            <span class="text-[10px] font-mono text-indigo-600">{{ priority }}</span>
          </div>
          <div class="font-bold text-slate-900 dark:text-white truncate">
            {{ title.trim() || 'Test Push Notification' }}
          </div>
          <div class="text-[11px] text-slate-500 dark:text-slate-400 line-clamp-2">
            {{ body.trim() || 'This is a test notification dispatched from the composer.' }}
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <BaseButton variant="ghost" size="sm" @click="showTestModal = false">
            Close
          </BaseButton>
          <BaseButton
            variant="primary"
            size="sm"
            :icon="Send"
            :loading="isSendingTest"
            @click="handleSendTestPush"
          >
            Dispatch Test
          </BaseButton>
        </div>
      </template>
    </ModernModal>
  </div>
</template>

<style scoped>
.custom-scroll::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}
.custom-scroll::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scroll::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.3);
  border-radius: 9999px;
}
.custom-scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(148, 163, 184, 0.5);
}
</style>
