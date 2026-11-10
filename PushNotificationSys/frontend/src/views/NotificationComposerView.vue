<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { notificationsApi } from '@/api/notifications'
import { segmentsApi, type Segment } from '@/api/segments'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import CustomSelect, { type SelectOption } from '@/components/common/CustomSelect.vue'
import {
  AlertCircle,
  ArrowLeft,
  BellRing,
  Calendar,
  CheckCircle2,
  Clock,
  ExternalLink,
  Filter,
  Globe,
  Image as ImageIcon,
  Layers,
  Plus,
  Send,
  Smartphone,
  Sparkles,
  Trash2,
  Zap,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()

const appId = computed(() => {
  return (route.params.appId as string) || authStore.currentAppId || ''
})

// Form state
const title = ref('')
const body = ref('')
const imageUrl = ref('')
const deepLink = ref('')
const priority = ref<'HIGH' | 'NORMAL'>('HIGH')
const targetType = ref<'ALL' | 'SEGMENT' | 'DEVICE_IDS' | 'EXTERNAL_USER_IDS'>('ALL')
const targetInput = ref('')
const selectedSegmentId = ref('')
const availableSegments = ref<Segment[]>([])
const customDataPairs = ref<{ key: string; value: string }[]>([])

// Scheduling state
const deliveryOption = ref<'NOW' | 'SCHEDULED'>('NOW')
const scheduledDateTime = ref('')
const scheduleType = ref<'FIXED_TIME' | 'USER_TIMEZONE'>('FIXED_TIME')
const userTargetTime = ref('09:00')

// Preview state
const previewPlatform = ref<'ANDROID' | 'IOS' | 'WEB'>('ANDROID')
const isSubmitting = ref(false)
const errorMessage = ref('')

const priorityOptions: SelectOption[] = [
  { label: 'High Priority (Immediate)', value: 'HIGH', dotColor: 'bg-rose-500' },
  { label: 'Normal (Standard Delivery)', value: 'NORMAL', dotColor: 'bg-indigo-500' },
]

const targetTypeOptions: SelectOption[] = [
  { label: 'Broadcast to All Devices', value: 'ALL', dotColor: 'bg-indigo-500' },
  { label: 'Target by Segment', value: 'SEGMENT', dotColor: 'bg-emerald-500' },
  { label: 'Specific Device Tokens', value: 'DEVICE_IDS', dotColor: 'bg-amber-500' },
  { label: 'Specific User IDs', value: 'EXTERNAL_USER_IDS', dotColor: 'bg-sky-500' },
]

const segmentSelectOptions = computed<SelectOption[]>(() => {
  return availableSegments.value.map((s) => ({
    label: `${s.name} (~${s.estimatedCount ?? 0} users)`,
    value: s.id,
  }))
})

function addCustomDataPair() {
  customDataPairs.value.push({ key: '', value: '' })
}

function removeCustomDataPair(index: number) {
  customDataPairs.value.splice(index, 1)
}

const displayTitle = computed(() => title.value || 'Special Announcement')
const displayBody = computed(
  () => body.value || 'Write your notification message here to see the real-time mobile preview...',
)

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

  isSubmitting.value = true
  errorMessage.value = ''

  try {
    const dataObj: Record<string, string> = {}
    customDataPairs.value.forEach((p) => {
      if (p.key.trim()) dataObj[p.key.trim()] = p.value
    })

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

    const payload = {
      title: title.value.trim(),
      body: body.value.trim(),
      imageUrl: imageUrl.value.trim() || undefined,
      deepLink: deepLink.value.trim() || undefined,
      priority: priority.value,
      targetType: targetType.value,
      customData: Object.keys(dataObj).length > 0 ? dataObj : undefined,
      targetPayload: Object.keys(targetPayload).length > 0 ? targetPayload : undefined,
    }

    await notificationsApi.create(appId.value, payload)

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

onMounted(() => {
  loadSegments()
})
</script>

<template>
  <div class="space-y-6 text-left pb-12">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-1 border-b border-slate-200 dark:border-slate-800">
      <div class="flex items-center gap-3">
        <button
          type="button"
          @click="router.back()"
          class="p-2 rounded-xl text-slate-400 hover:text-slate-700 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition cursor-pointer"
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
        <div>
          <h1 class="text-lg font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
            <span>Compose Push Notification</span>
            <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 dark:bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-500/30">
              Gateway v2
            </span>
          </h1>
          <p class="text-xs text-slate-500 dark:text-slate-400">
            Target devices, customize interactive payloads, and preview real-time rendering
          </p>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <BaseButton
          variant="outline"
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
      class="p-3.5 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2.5 animate-in fade-in"
    >
      <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
      <span class="font-medium">{{ errorMessage }}</span>
    </div>

    <!-- 2 Column Composer Grid -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <!-- Left Column: Form Controls (7 cols) -->
      <div class="lg:col-span-7 space-y-5">
        <!-- 1. Audience & Targeting Card -->
        <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
          <div class="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-2">
              <Filter class="w-3.5 h-3.5 text-indigo-500" />
              <span>1. Target Audience</span>
            </h3>
            <span class="text-[11px] text-slate-400 font-medium">Recipients</span>
          </div>

          <div>
            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1.5">
              Target Mode
            </label>
            <CustomSelect
              v-model="targetType"
              :options="targetTypeOptions"
              size="md"
            />
          </div>

          <!-- Segment Picker if SEGMENT -->
          <div v-if="targetType === 'SEGMENT'" class="animate-in fade-in">
            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1.5">
              Select Audience Segment
            </label>
            <CustomSelect
              v-if="segmentSelectOptions.length > 0"
              v-model="selectedSegmentId"
              :options="segmentSelectOptions"
              size="md"
            />
            <p v-else class="text-xs text-amber-600 dark:text-amber-400">
              No segments configured yet. You can build segments in the Audience tab.
            </p>
          </div>

          <!-- Direct Input if Tokens or User IDs -->
          <div v-if="targetType === 'DEVICE_IDS' || targetType === 'EXTERNAL_USER_IDS'" class="animate-in fade-in space-y-1.5">
            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300">
              {{ targetType === 'DEVICE_IDS' ? 'Enter Device Tokens (one per line)' : 'Enter User IDs (one per line)' }}
            </label>
            <textarea
              v-model="targetInput"
              rows="3"
              placeholder="fcm_token_xyz123&#10;apns_token_abc456"
              class="w-full bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white font-mono p-3 rounded-xl border border-slate-200 dark:border-slate-800 text-xs focus:outline-none focus:border-indigo-500"
            ></textarea>
          </div>
        </div>

        <!-- 2. Message Content Card -->
        <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
          <div class="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-2">
              <BellRing class="w-3.5 h-3.5 text-indigo-500" />
              <span>2. Message Payload</span>
            </h3>
            <span class="text-[11px] text-slate-400 font-medium">Visuals & Text</span>
          </div>

          <BaseInput
            v-model="title"
            label="Notification Title"
            placeholder="e.g. Flash Sale Alert! ⚡"
            required
            size="md"
          />

          <div class="space-y-1.5">
            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300">
              Message Body <span class="text-rose-500">*</span>
            </label>
            <textarea
              v-model="body"
              rows="3"
              placeholder="Write the notification message content..."
              class="w-full bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-white p-3 rounded-xl border border-slate-200 dark:border-slate-800 text-xs focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
            ></textarea>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <BaseInput
              v-model="imageUrl"
              label="Image Banner URL (Optional)"
              placeholder="https://example.com/banner.jpg"
              :icon="ImageIcon"
              size="sm"
            />

            <BaseInput
              v-model="deepLink"
              label="Deep Link / Action URL"
              placeholder="myapp://promo/winter"
              :icon="ExternalLink"
              size="sm"
            />
          </div>

          <div>
            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-1.5">
              Priority Level
            </label>
            <CustomSelect
              v-model="priority"
              :options="priorityOptions"
              size="sm"
            />
          </div>
        </div>

        <!-- 3. Custom Key-Value Data Pairs -->
        <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm space-y-3">
          <div class="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-2">
              <Zap class="w-3.5 h-3.5 text-indigo-500" />
              <span>3. Custom Data (Payload JSON)</span>
            </h3>
            <BaseButton variant="ghost" size="xs" :icon="Plus" @click="addCustomDataPair">
              Add Field
            </BaseButton>
          </div>

          <div v-if="customDataPairs.length === 0" class="text-xs text-slate-400 py-2">
            No custom data pairs added. Click "Add Field" to attach extra JSON key-values to this push.
          </div>

          <div
            v-for="(pair, idx) in customDataPairs"
            :key="idx"
            class="flex items-center gap-2 animate-in fade-in"
          >
            <BaseInput v-model="pair.key" placeholder="Key (e.g. orderId)" size="sm" class="flex-1" />
            <BaseInput v-model="pair.value" placeholder="Value (e.g. 109283)" size="sm" class="flex-1" />
            <button
              type="button"
              @click="removeCustomDataPair(idx)"
              class="p-2 text-slate-400 hover:text-rose-500 transition cursor-pointer"
            >
              <Trash2 class="w-4 h-4" />
            </button>
          </div>
        </div>

        <!-- 4. Schedule Delivery -->
        <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm space-y-3">
          <div class="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-2">
              <Clock class="w-3.5 h-3.5 text-indigo-500" />
              <span>4. Delivery Schedule</span>
            </h3>
            <span class="text-[11px] text-slate-400 font-medium">Timing</span>
          </div>

          <div class="flex items-center gap-3">
            <label class="flex items-center gap-2 text-xs font-semibold cursor-pointer">
              <input type="radio" v-model="deliveryOption" value="NOW" class="text-indigo-600 focus:ring-indigo-500" />
              <span>Send Immediately</span>
            </label>
            <label class="flex items-center gap-2 text-xs font-semibold cursor-pointer">
              <input type="radio" v-model="deliveryOption" value="SCHEDULED" class="text-indigo-600 focus:ring-indigo-500" />
              <span>Schedule for Later</span>
            </label>
          </div>

          <div v-if="deliveryOption === 'SCHEDULED'" class="pt-2 animate-in fade-in">
            <BaseInput
              v-model="scheduledDateTime"
              type="datetime-local"
              label="Scheduled Date & Time"
              size="sm"
              :icon="Calendar"
            />
          </div>
        </div>
      </div>

      <!-- Right Column: Interactive Realtime Device Preview (5 cols) -->
      <div class="lg:col-span-5 space-y-4">
        <div class="bg-white dark:bg-slate-900/90 rounded-2xl p-5 border border-slate-200 dark:border-slate-800 shadow-sm sticky top-20 space-y-4">
          <div class="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
            <h3 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-2">
              <Smartphone class="w-3.5 h-3.5 text-indigo-500" />
              <span>Device Live Preview</span>
            </h3>

            <!-- Platform Switcher -->
            <div class="flex items-center p-0.5 rounded-lg bg-slate-100 dark:bg-slate-800">
              <button
                v-for="p in ['ANDROID', 'IOS', 'WEB'] as const"
                :key="p"
                type="button"
                @click="previewPlatform = p"
                class="px-2 py-0.5 rounded text-[10px] font-bold transition cursor-pointer"
                :class="[
                  previewPlatform === p
                    ? 'bg-white dark:bg-slate-700 text-slate-900 dark:text-white shadow-xs'
                    : 'text-slate-400 hover:text-slate-600 dark:hover:text-slate-200',
                ]"
              >
                {{ p }}
              </button>
            </div>
          </div>

          <!-- Smartphone Frame Mockup -->
          <div class="mx-auto w-full max-w-[320px] bg-slate-950 rounded-[36px] p-3 shadow-2xl border-4 border-slate-800 relative overflow-hidden text-left">
            <!-- Top Speaker & Camera Notch -->
            <div class="w-24 h-4 bg-slate-900 mx-auto rounded-full mb-3 flex items-center justify-center">
              <div class="w-2.5 h-2.5 rounded-full bg-slate-950"></div>
            </div>

            <!-- Lockscreen Screen Background -->
            <div class="bg-gradient-to-br from-indigo-950/80 via-slate-900 to-slate-950 rounded-[28px] p-3.5 min-h-[380px] flex flex-col justify-between text-white relative">
              <!-- Lockscreen Clock -->
              <div class="text-center pt-2">
                <div class="text-3xl font-extralight tracking-tight font-mono">09:41</div>
                <div class="text-[10px] text-slate-400 font-medium">Tuesday, November 10</div>
              </div>

              <!-- Push Notification Card Simulation -->
              <div class="my-auto">
                <div class="bg-white/15 backdrop-blur-xl rounded-2xl p-3 border border-white/20 shadow-lg text-slate-100 space-y-1.5 transition-all">
                  <div class="flex items-center justify-between">
                    <div class="flex items-center gap-1.5">
                      <div class="w-4 h-4 rounded-md bg-indigo-500 flex items-center justify-center">
                        <BellRing class="w-2.5 h-2.5 text-white" />
                      </div>
                      <span class="text-[10px] font-bold uppercase tracking-wider text-slate-200">
                        Push Platform
                      </span>
                    </div>
                    <span class="text-[9px] text-slate-400 font-mono">now</span>
                  </div>

                  <div>
                    <h4 class="text-xs font-bold text-white leading-snug">
                      {{ displayTitle }}
                    </h4>
                    <p class="text-[11px] text-slate-200 leading-relaxed mt-0.5 line-clamp-3">
                      {{ displayBody }}
                    </p>
                  </div>

                  <img
                    v-if="imageUrl"
                    :src="imageUrl"
                    alt="Banner preview"
                    class="w-full h-24 object-cover rounded-xl mt-2 border border-white/10"
                    @error="imageUrl = ''"
                  />
                </div>
              </div>

              <!-- Lockscreen Bottom Action Indicator -->
              <div class="text-center pb-1">
                <div class="w-16 h-1 bg-white/40 rounded-full mx-auto"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
